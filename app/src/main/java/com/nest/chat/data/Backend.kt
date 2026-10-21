package com.nest.chat.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.security.SecureRandom
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

enum class ReportResult { CONFIRMED, QUEUED, FAILED }

object Backend {

    private const val EMAIL_DOMAIN = "nestchat.app"
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    val uid: String? get() = auth.currentUser?.uid

    private fun emailOf(handle: String) = "$handle@$EMAIL_DOMAIN"

    private fun authError(e: Exception): String = when {
        e is FirebaseNetworkException -> "Không có kết nối mạng"
        e is FirebaseAuthException -> when (e.errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> "ID này đã có người dùng"
            "ERROR_WEAK_PASSWORD" -> "Mật khẩu quá yếu, tối thiểu 6 ký tự"
            "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_INVALID_EMAIL" -> "Sai ID hoặc mật khẩu"
            "ERROR_TOO_MANY_REQUESTS" -> "Thử quá nhiều lần, vui lòng đợi một lúc"
            "ERROR_USER_DISABLED" -> "Tài khoản đã bị vô hiệu hóa"
            "ERROR_OPERATION_NOT_ALLOWED" -> "Chưa bật đăng nhập Email/Password trong Firebase"
            else -> "Lỗi đăng nhập (${e.errorCode})"
        }
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Máy chủ từ chối, hãy kiểm tra Firestore Rules"
        else -> "Có lỗi xảy ra, vui lòng thử lại"
    }

    fun authFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun login(handle: String, password: String): String? = try {
        auth.signInWithEmailAndPassword(emailOf(handle), password).await()
        try {
            ensureProfile(handle)
        } catch (e: Exception) {
        }
        null
    } catch (e: Exception) {
        authError(e)
    }

    suspend fun register(handle: String, password: String): String? = try {
        val result = auth.createUserWithEmailAndPassword(emailOf(handle), password).await()
        val id = result.user?.uid ?: throw IllegalStateException()
        withTimeout(15_000) {
            db.collection("users").document(id).set(newUserMap(handle)).await()
        }
        null
    } catch (e: Exception) {
        authError(e)
    }

    private suspend fun ensureProfile(handle: String) {
        val id = uid ?: return
        val snap = db.collection("users").document(id).get().await()
        if (!snap.exists()) {
            db.collection("users").document(id).set(newUserMap(handle)).await()
        }
    }

    private fun newUserMap(handle: String): Map<String, Any> = mapOf(
        "handle" to handle,
        "name" to handle,
        "bio" to "",
        "avatar" to "",
        "status" to "",
        "birthday" to "",
        "discoverable" to true,
        "readReceipts" to true,
        "hideOnline" to false,
        "allowRequests" to true,
        "profileDone" to false,
        "strikes" to 0L,
        "bannedUntil" to 0L,
        "suspended" to false,
        "createdAt" to FieldValue.serverTimestamp()
    )

    fun signOut() {
        auth.signOut()
    }

    suspend fun changePassword(oldPassword: String, newPassword: String): String? {
        val user = auth.currentUser ?: return "Chưa đăng nhập"
        val email = user.email ?: return "Không xác định được tài khoản"
        return try {
            user.reauthenticate(EmailAuthProvider.getCredential(email, oldPassword)).await()
            user.updatePassword(newPassword).await()
            null
        } catch (e: Exception) {
            authError(e)
        }
    }

    fun profileFlow(id: String): Flow<UserProfile?> = callbackFlow {
        val registration = db.collection("users").document(id).addSnapshotListener { snap, error ->
            if (error != null) {
                close()
                return@addSnapshotListener
            }
            trySend(if (snap != null && snap.exists()) snap.toProfile() else null)
        }
        awaitClose { registration.remove() }
    }

    suspend fun fetchUser(id: String): UserProfile? = try {
        val snap = db.collection("users").document(id).get().await()
        if (snap.exists()) snap.toProfile() else null
    } catch (e: Exception) {
        null
    }

    suspend fun findByHandle(handle: String): UserProfile? = try {
        val snap = db.collection("users")
            .whereEqualTo("handle", handle.lowercase())
            .whereEqualTo("discoverable", true)
            .limit(1)
            .get()
            .await()
        snap.documents.firstOrNull()?.toProfile()
    } catch (e: Exception) {
        null
    }

    suspend fun updateProfile(fields: Map<String, Any>): Boolean {
        val id = uid ?: return false
        return try {
            withTimeout(12_000) {
                db.collection("users").document(id).update(fields).await()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun setFlag(field: String, value: Boolean) {
        val id = uid ?: return
        db.collection("users").document(id).update(field, value)
    }

    fun touchPresence() {
        val id = uid ?: return
        db.collection("users").document(id).update("lastSeen", FieldValue.serverTimestamp())
    }

    fun chatsFlow(id: String): Flow<List<Chat>> = callbackFlow {
        val registration = db.collection("chats").whereArrayContains("members", id)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close()
                    return@addSnapshotListener
                }
                val list = snap?.documents.orEmpty().map { it.toChat() }.sortedByDescending { it.lastTime }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    fun friendsFlow(id: String): Flow<Map<String, String>> = callbackFlow {
        val registration = db.collection("users").document(id).collection("friends")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close()
                    return@addSnapshotListener
                }
                val map = HashMap<String, String>()
                for (doc in snap?.documents.orEmpty()) map[doc.id] = doc.getString("nickname").orEmpty()
                trySend(map)
            }
        awaitClose { registration.remove() }
    }

    fun blockedFlow(id: String): Flow<Set<String>> = callbackFlow {
        val registration = db.collection("users").document(id).collection("blocked")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close()
                    return@addSnapshotListener
                }
                trySend(snap?.documents.orEmpty().map { it.id }.toSet())
            }
        awaitClose { registration.remove() }
    }

    fun chatPrefsFlow(id: String): Flow<Map<String, ChatPref>> = callbackFlow {
        val registration = db.collection("users").document(id).collection("chatPrefs")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close()
                    return@addSnapshotListener
                }
                val map = HashMap<String, ChatPref>()
                for (doc in snap?.documents.orEmpty()) {
                    map[doc.id] = ChatPref(
                        pinned = doc.getBoolean("pinned") ?: false,
                        pinnedAt = doc.getLong("pinnedAt") ?: 0L,
                        muted = doc.getBoolean("muted") ?: false,
                        archived = doc.getBoolean("archived") ?: false,
                        markedUnread = doc.getBoolean("markedUnread") ?: false,
                        clearedAt = doc.getLong("clearedAt") ?: 0L,
                        hidden = (doc.get("hidden") as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
                    )
                }
                trySend(map)
            }
        awaitClose { registration.remove() }
    }

    fun requestsFlow(field: String, id: String): Flow<List<FriendRequest>> = callbackFlow {
        val registration = db.collection("requests").whereEqualTo(field, id)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close()
                    return@addSnapshotListener
                }
                trySend(snap?.documents.orEmpty().map {
                    FriendRequest(it.id, it.getString("from").orEmpty(), it.getString("to").orEmpty())
                })
            }
        awaitClose { registration.remove() }
    }

    suspend fun sendRequest(me: String, to: String): Boolean = try {
        withTimeout(10_000) {
            db.collection("requests").document("${me}_$to").set(
                mapOf("from" to me, "to" to to, "createdAt" to FieldValue.serverTimestamp())
            ).await()
        }
        true
    } catch (e: Exception) {
        false
    }

    suspend fun acceptRequest(me: String, from: String): Boolean = try {
        val batch = db.batch()
        batch.set(
            db.collection("users").document(me).collection("friends").document(from),
            mapOf("since" to FieldValue.serverTimestamp(), "nickname" to "")
        )
        batch.set(
            db.collection("users").document(from).collection("friends").document(me),
            mapOf("since" to FieldValue.serverTimestamp(), "nickname" to "")
        )
        batch.delete(db.collection("requests").document("${from}_$me"))
        withTimeout(12_000) { batch.commit().await() }
        true
    } catch (e: Exception) {
        false
    }

    fun deleteRequest(from: String, to: String) {
        db.collection("requests").document("${from}_$to").delete()
    }

    suspend fun removeFriend(me: String, other: String): Boolean = try {
        val batch = db.batch()
        batch.delete(db.collection("users").document(me).collection("friends").document(other))
        batch.delete(db.collection("users").document(other).collection("friends").document(me))
        withTimeout(12_000) { batch.commit().await() }
        true
    } catch (e: Exception) {
        false
    }

    suspend fun setNickname(me: String, friend: String, nickname: String): Boolean = try {
        withTimeout(10_000) {
            db.collection("users").document(me).collection("friends").document(friend)
                .update("nickname", nickname.trim().take(30)).await()
        }
        true
    } catch (e: Exception) {
        false
    }

    suspend fun block(me: String, other: String): Boolean = try {
        val batch = db.batch()
        batch.set(
            db.collection("users").document(me).collection("blocked").document(other),
            mapOf("since" to FieldValue.serverTimestamp())
        )
        batch.delete(db.collection("users").document(me).collection("friends").document(other))
        batch.delete(db.collection("users").document(other).collection("friends").document(me))
        withTimeout(12_000) { batch.commit().await() }
        deleteRequest(me, other)
        deleteRequest(other, me)
        true
    } catch (e: Exception) {
        false
    }

    fun unblock(me: String, other: String) {
        db.collection("users").document(me).collection("blocked").document(other).delete()
    }

    suspend fun reportUser(target: String, reason: String): Boolean {
        val me = uid ?: return false
        return try {
            withTimeout(10_000) {
                db.collection("reports").document().set(
                    mapOf(
                        "reporter" to me,
                        "target" to target,
                        "chat" to "",
                        "msg" to "",
                        "reason" to reason,
                        "status" to "pending",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun setChatPref(chatId: String, fields: Map<String, Any>) {
        val me = uid ?: return
        db.collection("users").document(me).collection("chatPrefs").document(chatId)
            .set(fields, SetOptions.merge())
    }

    fun hideMessageForMe(chatId: String, messageId: String) {
        val me = uid ?: return
        db.collection("users").document(me).collection("chatPrefs").document(chatId)
            .set(mapOf("hidden" to FieldValue.arrayUnion(messageId)), SetOptions.merge())
    }

    suspend fun openDm(me: String, other: String): String? = try {
        val members = listOf(me, other).sorted()
        val id = members.joinToString("_")
        val ref = db.collection("chats").document(id)
        withTimeout(12_000) {
            val snap = ref.get().await()
            if (!snap.exists()) {
                ref.set(
                    mapOf(
                        "type" to "dm",
                        "members" to members,
                        "name" to "",
                        "createdBy" to me,
                        "lastText" to "",
                        "lastKind" to "text",
                        "lastSender" to "",
                        "lastTime" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
        id
    } catch (e: Exception) {
        null
    }

    suspend fun createGroup(me: String, name: String, others: List<String>): String? = try {
        val ref = db.collection("chats").document()
        val members = (listOf(me) + others).distinct()
        withTimeout(12_000) {
            ref.set(
                mapOf(
                    "type" to "group",
                    "members" to members,
                    "name" to name,
                    "description" to "",
                    "avatar" to "",
                    "admins" to listOf(me),
                    "onlyAdminsPost" to false,
                    "inviteCode" to "",
                    "disbanded" to false,
                    "createdBy" to me,
                    "lastText" to "Nhóm đã được tạo",
                    "lastKind" to "text",
                    "lastSender" to me,
                    "lastTime" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        ref.id
    } catch (e: Exception) {
        null
    }

    suspend fun updateChat(chatId: String, fields: Map<String, Any>): Boolean = try {
        withTimeout(10_000) { db.collection("chats").document(chatId).update(fields).await() }
        true
    } catch (e: Exception) {
        false
    }

    suspend fun addMembers(chatId: String, ids: List<String>): Boolean =
        updateChat(chatId, mapOf("members" to FieldValue.arrayUnion(*ids.toTypedArray())))

    suspend fun kickMember(chatId: String, id: String): Boolean =
        updateChat(chatId, mapOf("members" to FieldValue.arrayRemove(id)))

    suspend fun leaveGroup(chatId: String, me: String): Boolean =
        updateChat(chatId, mapOf("members" to FieldValue.arrayRemove(me)))

    suspend fun setAdmin(chatId: String, id: String, admin: Boolean): Boolean =
        updateChat(chatId, mapOf("admins" to if (admin) FieldValue.arrayUnion(id) else FieldValue.arrayRemove(id)))

    suspend fun transferOwner(chatId: String, id: String): Boolean =
        updateChat(chatId, mapOf("createdBy" to id, "admins" to FieldValue.arrayUnion(id)))

    suspend fun disbandGroup(chatId: String): Boolean =
        updateChat(chatId, mapOf("disbanded" to true))

    private fun randomCode(length: Int): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        val sb = StringBuilder()
        repeat(length) { sb.append(alphabet[random.nextInt(alphabet.length)]) }
        return sb.toString()
    }

    suspend fun createInvite(chatId: String, oldCode: String): String? {
        val me = uid ?: return null
        return try {
            val code = randomCode(10)
            val batch = db.batch()
            batch.set(
                db.collection("invites").document(code),
                mapOf("chat" to chatId, "by" to me, "createdAt" to FieldValue.serverTimestamp())
            )
            batch.update(db.collection("chats").document(chatId), "inviteCode", code)
            if (oldCode.isNotEmpty()) batch.delete(db.collection("invites").document(oldCode))
            withTimeout(12_000) { batch.commit().await() }
            code
        } catch (e: Exception) {
            null
        }
    }

    suspend fun revokeInvite(chatId: String, code: String): Boolean = try {
        val batch = db.batch()
        batch.update(db.collection("chats").document(chatId), "inviteCode", "")
        if (code.isNotEmpty()) batch.delete(db.collection("invites").document(code))
        withTimeout(12_000) { batch.commit().await() }
        true
    } catch (e: Exception) {
        false
    }

    suspend fun joinByCode(me: String, code: String): String? = try {
        val invite = withTimeout(10_000) { db.collection("invites").document(code.trim().uppercase()).get().await() }
        val chatId = invite.getString("chat")
        if (!invite.exists() || chatId.isNullOrEmpty()) {
            null
        } else {
            val batch = db.batch()
            batch.set(
                db.collection("joins").document("${chatId}_$me"),
                mapOf("code" to code.trim().uppercase(), "chat" to chatId, "uid" to me)
            )
            batch.update(db.collection("chats").document(chatId), "members", FieldValue.arrayUnion(me))
            withTimeout(12_000) { batch.commit().await() }
            chatId
        }
    } catch (e: Exception) {
        null
    }

    fun setPinned(chatId: String, messageId: String, text: String, by: String) {
        db.collection("chats").document(chatId).update(
            mapOf("pinnedId" to messageId, "pinnedText" to text.take(120), "pinnedBy" to by)
        )
    }

    fun messagesFlow(chatId: String, limit: Int, sinceMs: Long): Flow<List<Message>> = callbackFlow {
        var query: Query = db.collection("chats").document(chatId).collection("messages")
        if (sinceMs > 0L) query = query.whereGreaterThan("time", Timestamp(Date(sinceMs)))
        query = query.orderBy("time", Query.Direction.DESCENDING).limit(limit.toLong())
        val registration = query.addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            if (error != null) {
                close()
                return@addSnapshotListener
            }
            trySend(snap?.documents.orEmpty().map { it.toMessage() })
        }
        awaitClose { registration.remove() }
    }

    fun typingFlow(chatId: String, me: String): Flow<Map<String, Long>> = callbackFlow {
        val registration = db.collection("chats").document(chatId).collection("typing")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close()
                    return@addSnapshotListener
                }
                val map = HashMap<String, Long>()
                for (doc in snap?.documents.orEmpty()) {
                    if (doc.id != me) {
                        map[doc.id] = doc.getTimestamp("ts", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L
                    }
                }
                trySend(map)
            }
        awaitClose { registration.remove() }
    }

    fun sendTyping(chatId: String, me: String) {
        db.collection("chats").document(chatId).collection("typing").document(me)
            .set(mapOf("ts" to FieldValue.serverTimestamp()))
    }

    fun readsFlow(chatId: String, other: String): Flow<Long> = callbackFlow {
        val registration = db.collection("chats").document(chatId).collection("reads").document(other)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close()
                    return@addSnapshotListener
                }
                val time = snap?.getTimestamp("time", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L
                trySend(time)
            }
        awaitClose { registration.remove() }
    }

    fun markRead(chatId: String, me: String) {
        db.collection("chats").document(chatId).collection("reads").document(me)
            .set(mapOf("time" to FieldValue.serverTimestamp()))
    }

    fun resetUnread(chatId: String, me: String) {
        db.collection("chats").document(chatId).update("unread.$me", 0L)
    }

    fun sendMessage(
        chat: Chat,
        me: String,
        text: String,
        image: String,
        width: Int,
        height: Int,
        reply: ReplyRef?,
        forwarded: Boolean,
        mentions: List<String>,
        onFail: () -> Unit
    ) {
        val chatRef = db.collection("chats").document(chat.id)
        val messageRef = chatRef.collection("messages").document()
        val kind = if (image.isEmpty()) "text" else "image"

        val message = HashMap<String, Any>()
        message["sender"] = me
        message["text"] = text
        message["image"] = image
        message["kind"] = kind
        message["w"] = width.toLong()
        message["h"] = height.toLong()
        message["time"] = FieldValue.serverTimestamp()
        message["flagged"] = false
        message["replyTo"] = reply?.id.orEmpty()
        message["replyText"] = reply?.text.orEmpty().take(120)
        message["replySender"] = reply?.sender.orEmpty()
        message["fwd"] = forwarded
        message["mentions"] = mentions.take(20)

        val update = HashMap<String, Any>()
        update["lastText"] = text.take(120)
        update["lastKind"] = kind
        update["lastSender"] = me
        update["lastTime"] = FieldValue.serverTimestamp()
        for (member in chat.members) {
            if (member != me) update["unread.$member"] = FieldValue.increment(1)
        }

        val batch = db.batch()
        batch.set(messageRef, message)
        batch.update(chatRef, update)
        batch.commit().addOnFailureListener { onFail() }
    }

    fun editMessage(chatId: String, messageId: String, text: String, onFail: () -> Unit) {
        db.collection("chats").document(chatId).collection("messages").document(messageId)
            .update(mapOf("text" to text, "edited" to true))
            .addOnFailureListener { onFail() }
    }

    fun recallMessage(chatId: String, messageId: String, onFail: () -> Unit) {
        db.collection("chats").document(chatId).collection("messages").document(messageId)
            .update(mapOf("text" to "", "image" to "", "recalled" to true))
            .addOnFailureListener { onFail() }
    }

    fun react(chatId: String, messageId: String, me: String, emoji: String?) {
        val ref = db.collection("chats").document(chatId).collection("messages").document(messageId)
        if (emoji == null) {
            ref.update("reactions.$me", FieldValue.delete())
        } else {
            ref.update("reactions.$me", emoji)
        }
    }

    suspend fun selfViolation(me: UserProfile, severe: Boolean): Penalty {
        val penalty = Moderation.penaltyFor(me, severe, false, System.currentTimeMillis())
        try {
            withTimeout(10_000) {
                db.collection("users").document(me.uid).update(
                    mapOf(
                        "strikes" to penalty.strikes.toLong(),
                        "bannedUntil" to penalty.bannedUntil,
                        "suspended" to penalty.suspended,
                        "lastViolation" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: Exception) {
        }
        return penalty
    }

    suspend fun reportMessage(chatId: String, message: Message, reason: String): ReportResult {
        val me = uid ?: return ReportResult.FAILED
        return try {
            var finding: Finding? = Moderation.scanText(message.text)?.let { Finding(it, false) }
            if (finding == null && message.image.isNotEmpty()) {
                val bitmap = ImageUtil.decode(message.image, 256)
                if (bitmap != null) {
                    val verdict = ImageScanner.scan(bitmap)
                    if (verdict != ImageVerdict.SAFE) {
                        finding = Finding("Hình ảnh nhạy cảm", verdict == ImageVerdict.VIOLATION)
                    }
                }
            }

            val reportRef = db.collection("reports").document()
            val report = mapOf(
                "reporter" to me,
                "target" to message.sender,
                "chat" to chatId,
                "msg" to message.id,
                "reason" to reason,
                "status" to if (finding != null) "confirmed" else "pending",
                "createdAt" to FieldValue.serverTimestamp()
            )

            if (finding == null) {
                withTimeout(12_000) { reportRef.set(report).await() }
                return ReportResult.QUEUED
            }

            val targetRef = db.collection("users").document(message.sender)
            val target = withTimeout(12_000) { targetRef.get(Source.SERVER).await() }.toProfile()
            val penalty = Moderation.penaltyFor(target, finding.severe, true, System.currentTimeMillis())
            val messageRef = db.collection("chats").document(chatId).collection("messages").document(message.id)

            val batch = db.batch()
            batch.set(reportRef, report)
            batch.update(messageRef, mapOf("flagged" to true))
            batch.set(
                db.collection("violations").document("${message.sender}_${penalty.strikes}"),
                mapOf(
                    "by" to me,
                    "target" to message.sender,
                    "chat" to chatId,
                    "msg" to message.id,
                    "kind" to finding.reason,
                    "time" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                targetRef,
                mapOf(
                    "strikes" to penalty.strikes.toLong(),
                    "bannedUntil" to penalty.bannedUntil,
                    "suspended" to penalty.suspended,
                    "lastViolation" to FieldValue.serverTimestamp()
                )
            )
            withTimeout(15_000) { batch.commit().await() }
            ReportResult.CONFIRMED
        } catch (e: Exception) {
            ReportResult.FAILED
        }
    }

    suspend fun submitAppeal(text: String): Boolean {
        val id = uid ?: return false
        return try {
            withTimeout(12_000) {
                db.collection("appeals").document(id).set(
                    mapOf(
                        "message" to text.take(500),
                        "status" to "pending",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

object UserCache {

    private class Entry(val profile: UserProfile, val at: Long)

    private val map = ConcurrentHashMap<String, Entry>()

    fun peek(id: String): UserProfile? = map[id]?.profile

    fun put(profile: UserProfile) {
        map[profile.uid] = Entry(profile, System.currentTimeMillis())
    }

    suspend fun get(id: String, maxAgeMs: Long = 300_000L): UserProfile? {
        val entry = map[id]
        if (entry != null && System.currentTimeMillis() - entry.at < maxAgeMs) return entry.profile
        val fresh = Backend.fetchUser(id)
        if (fresh != null) put(fresh)
        return fresh ?: entry?.profile
    }

    fun clear() {
        map.clear()
    }
}

private fun DocumentSnapshot.millis(field: String): Long =
    getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L

fun DocumentSnapshot.toProfile(): UserProfile = UserProfile(
    uid = id,
    handle = getString("handle").orEmpty(),
    name = getString("name").orEmpty(),
    bio = getString("bio").orEmpty(),
    avatar = getString("avatar").orEmpty(),
    status = getString("status").orEmpty(),
    birthday = getString("birthday").orEmpty(),
    discoverable = getBoolean("discoverable") ?: true,
    readReceipts = getBoolean("readReceipts") ?: true,
    hideOnline = getBoolean("hideOnline") ?: false,
    allowRequests = getBoolean("allowRequests") ?: true,
    profileDone = getBoolean("profileDone") ?: false,
    strikes = (getLong("strikes") ?: 0L).toInt(),
    bannedUntil = getLong("bannedUntil") ?: 0L,
    suspended = getBoolean("suspended") ?: false,
    lastSeen = millis("lastSeen"),
    admin = getBoolean("admin") ?: false
)

fun DocumentSnapshot.toChat(): Chat {
    val owner = getString("createdBy").orEmpty()
    val adminList = (get("admins") as? List<*>)?.filterIsInstance<String>().orEmpty()
    val unreadMap = HashMap<String, Int>()
    val rawUnread = get("unread") as? Map<*, *>
    if (rawUnread != null) {
        for ((key, value) in rawUnread) {
            if (key is String && value is Number) unreadMap[key] = value.toInt()
        }
    }
    return Chat(
        id = id,
        isGroup = getString("type") == "group",
        name = getString("name").orEmpty(),
        description = getString("description").orEmpty(),
        avatar = getString("avatar").orEmpty(),
        members = (get("members") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        admins = if (adminList.isEmpty()) listOf(owner) else adminList,
        createdBy = owner,
        onlyAdminsPost = getBoolean("onlyAdminsPost") ?: false,
        inviteCode = getString("inviteCode").orEmpty(),
        disbanded = getBoolean("disbanded") ?: false,
        pinnedId = getString("pinnedId").orEmpty(),
        pinnedText = getString("pinnedText").orEmpty(),
        pinnedBy = getString("pinnedBy").orEmpty(),
        lastText = getString("lastText").orEmpty(),
        lastKind = getString("lastKind").orEmpty(),
        lastSender = getString("lastSender").orEmpty(),
        lastTime = millis("lastTime"),
        unread = unreadMap
    )
}

fun DocumentSnapshot.toMessage(): Message {
    val reactionMap = HashMap<String, String>()
    val rawReactions = get("reactions") as? Map<*, *>
    if (rawReactions != null) {
        for ((key, value) in rawReactions) {
            if (key is String && value is String) reactionMap[key] = value
        }
    }
    val time = getTimestamp("time", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
        ?: System.currentTimeMillis()
    return Message(
        id = id,
        sender = getString("sender").orEmpty(),
        text = getString("text").orEmpty(),
        image = getString("image").orEmpty(),
        width = (getLong("w") ?: 0L).toInt(),
        height = (getLong("h") ?: 0L).toInt(),
        time = time,
        flagged = getBoolean("flagged") ?: false,
        recalled = getBoolean("recalled") ?: false,
        edited = getBoolean("edited") ?: false,
        pending = metadata.hasPendingWrites(),
        replyTo = getString("replyTo").orEmpty(),
        replyText = getString("replyText").orEmpty(),
        replySender = getString("replySender").orEmpty(),
        fwd = getBoolean("fwd") ?: false,
        mentions = (get("mentions") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        reactions = reactionMap
    )
}
