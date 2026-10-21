package com.nest.chat.ui

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nest.chat.AppViewModel
import com.nest.chat.data.Backend
import com.nest.chat.data.Chat
import com.nest.chat.data.ChatPref
import com.nest.chat.data.Moderation
import com.nest.chat.data.Route
import com.nest.chat.data.UserCache
import com.nest.chat.data.UserProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

private val userReportReasons = listOf(
    "Quấy rối hoặc đe dọa",
    "Spam hoặc lừa đảo",
    "Nội dung tục tĩu hoặc nhạy cảm",
    "Giả mạo người khác",
    "Lý do khác"
)

fun displayName(profile: UserProfile?, nickname: String?): String {
    val nick = nickname.orEmpty()
    if (nick.isNotEmpty()) return nick
    val name = profile?.name.orEmpty()
    if (name.isNotBlank()) return name
    return profile?.handle.orEmpty().ifEmpty { "..." }
}

fun chatTitle(chat: Chat, myUid: String, nicknames: Map<String, String>): String {
    if (chat.isGroup) return chat.name
    val other = chat.members.firstOrNull { it != myUid }.orEmpty()
    return displayName(UserCache.peek(other), nicknames[other])
}

@Composable
fun rememberFriendProfiles(ids: Set<String>): State<List<UserProfile>> =
    produceState<List<UserProfile>>(initialValue = emptyList(), key1 = ids) {
        value = ids.map { id -> async { UserCache.get(id) } }.awaitAll().filterNotNull().sortedBy { it.name.lowercase() }
    }

@Composable
fun SmallButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val c = Nest.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) c.accent else c.glassStrong)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Label(text, size = 13, weight = FontWeight.SemiBold, color = if (primary) c.onAccent else c.text)
    }
}

@Composable
fun PersonRow(
    profile: UserProfile,
    nickname: String?,
    onClick: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val c = Nest.colors
    val hasNick = !nickname.isNullOrEmpty()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(profile, profile.handle, 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Label(displayName(profile, nickname), weight = FontWeight.Medium, maxLines = 1)
            Label(
                if (hasNick) "${profile.name} · @${profile.handle}" else "@${profile.handle}",
                size = 13,
                color = c.textSoft,
                maxLines = 1
            )
        }
        trailing()
    }
}

@Composable
fun EmptyCard(title: String, body: String, action: String?, onAction: () -> Unit) {
    val c = Nest.colors
    Glass(Modifier.fillMaxWidth().padding(20.dp), RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Label(title, size = 17, weight = FontWeight.SemiBold, align = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Label(body, color = c.textSoft, align = TextAlign.Center, size = 14)
            if (action != null) {
                Spacer(Modifier.height(16.dp))
                NestButton(action, onAction)
            }
        }
    }
}

@Composable
fun ChatListScreen(vm: AppViewModel, me: UserProfile, archivedOnly: Boolean = false) {
    val c = Nest.colors
    val scope = rememberCoroutineScope()
    val chats by vm.chats.collectAsStateWithLifecycle()
    val prefs by vm.chatPrefs.collectAsStateWithLifecycle()
    val nicknames by vm.nicknames.collectAsStateWithLifecycle()
    val now by rememberNow(me.bannedUntil)
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<Chat?>(null) }
    var confirmClear by remember { mutableStateOf<Chat?>(null) }
    var joinDialog by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }
    var joining by remember { mutableStateOf(false) }

    val base = remember(chats, prefs) {
        chats.filter { chat ->
            chat.lastSender.isNotEmpty() && chat.lastTime > (prefs[chat.id]?.clearedAt ?: 0L)
        }
    }
    val archivedCount = remember(base, prefs) { base.count { prefs[it.id]?.archived == true } }
    val visible = remember(base, prefs, query, archivedOnly, nicknames) {
        val selected = base.filter { (prefs[it.id]?.archived == true) == archivedOnly }
        val filtered = if (query.isBlank()) {
            selected
        } else {
            val needle = foldVi(query.trim())
            selected.filter { foldVi(chatTitle(it, me.uid, nicknames)).contains(needle) }
        }
        filtered.sortedWith(
            compareByDescending<Chat> { prefs[it.id]?.pinned == true }
                .thenByDescending { prefs[it.id]?.pinnedAt ?: 0L }
                .thenByDescending { it.lastTime }
        )
    }

    Column(Modifier.fillMaxSize()) {
        if (searching) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Tìm cuộc trò chuyện",
                    modifier = Modifier.weight(1f),
                    leading = { Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = c.textSoft) }
                )
                IconBtn(Icons.Rounded.Close, {
                    searching = false
                    query = ""
                })
            }
        } else {
            ScreenHeader(
                if (archivedOnly) "Đã lưu trữ" else "Tin nhắn",
                onBack = if (archivedOnly) ({ vm.back() }) else null,
                actions = {
                    if (!archivedOnly) {
                        IconBtn(Icons.Rounded.Search, { searching = true })
                        IconBtn(Icons.Rounded.Link, {
                            joinCode = ""
                            joinDialog = true
                        })
                    }
                }
            )
        }
        if (!archivedOnly && me.bannedUntil > now) {
            Banner("Bạn đang bị cấm chat đến ${Fmt.full(me.bannedUntil)}.")
        }
        if (visible.isEmpty() && (archivedOnly || archivedCount == 0)) {
            EmptyCard(
                if (query.isNotBlank()) "Không tìm thấy kết quả" else if (archivedOnly) "Chưa lưu trữ cuộc trò chuyện nào" else "Chưa có cuộc trò chuyện nào",
                if (query.isNotBlank()) "Thử một từ khóa khác." else if (archivedOnly) "Giữ lâu một cuộc trò chuyện để lưu trữ." else "Thêm bạn bằng ID hoặc mã QR để bắt đầu nhắn tin.",
                if (query.isBlank() && !archivedOnly) "Thêm bạn" else null
            ) { vm.resetTo(Route.AddFriend) }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                if (!archivedOnly && archivedCount > 0 && query.isBlank()) {
                    item(key = "archived_entry") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.push(Route.Archived) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Label("Đã lưu trữ ($archivedCount)", Modifier.weight(1f), color = c.textSoft, weight = FontWeight.Medium)
                        }
                    }
                }
                items(visible, key = { it.id }) { chat ->
                    ChatRow(vm, me, chat, prefs[chat.id], nicknames) { menuFor = chat }
                }
            }
        }
    }

    menuFor?.let { chat ->
        val pref = prefs[chat.id]
        GlassDialog(title = chatTitle(chat, me.uid, nicknames), onDismiss = { menuFor = null }) {
            ActionRow(if (pref?.pinned == true) "Bỏ ghim" else "Ghim lên đầu", {
                vm.setPref(chat.id, mapOf("pinned" to (pref?.pinned != true), "pinnedAt" to System.currentTimeMillis()))
                menuFor = null
            })
            ActionRow(if (pref?.muted == true) "Bật thông báo" else "Tắt thông báo", {
                vm.setPref(chat.id, mapOf("muted" to (pref?.muted != true)))
                menuFor = null
            })
            ActionRow(if (pref?.archived == true) "Bỏ lưu trữ" else "Lưu trữ", {
                vm.setPref(chat.id, mapOf("archived" to (pref?.archived != true)))
                menuFor = null
            })
            ActionRow(if (pref?.markedUnread == true) "Đánh dấu đã đọc" else "Đánh dấu chưa đọc", {
                vm.setPref(chat.id, mapOf("markedUnread" to (pref?.markedUnread != true)))
                menuFor = null
            })
            ActionRow("Xóa cuộc trò chuyện", {
                confirmClear = chat
                menuFor = null
            }, danger = true)
        }
    }

    confirmClear?.let { chat ->
        GlassDialog(
            title = "Xóa cuộc trò chuyện?",
            onDismiss = { confirmClear = null },
            confirmText = "Xóa",
            dismissText = "Hủy",
            danger = true,
            onConfirm = {
                vm.setPref(
                    chat.id,
                    mapOf("clearedAt" to maxOf(System.currentTimeMillis(), chat.lastTime), "pinned" to false, "archived" to false)
                )
                vm.setDraft(chat.id, "")
                confirmClear = null
            }
        ) {
            Label("Tin nhắn cũ sẽ bị ẩn khỏi tài khoản của bạn. Người khác vẫn giữ nguyên.", color = c.textSoft, size = 14)
        }
    }

    if (joinDialog) {
        GlassDialog(
            title = "Tham gia nhóm bằng mã",
            onDismiss = { joinDialog = false },
            confirmText = if (joining) "Đang vào..." else "Tham gia",
            dismissText = "Hủy",
            onConfirm = {
                if (!joining && joinCode.trim().length >= 6) {
                    joining = true
                    scope.launch {
                        val id = Backend.joinByCode(me.uid, joinCode)
                        joining = false
                        if (id == null) {
                            vm.toast("Mã không hợp lệ hoặc không thể tham gia nhóm")
                        } else {
                            joinDialog = false
                            vm.push(Route.Chat(id))
                        }
                    }
                }
            }
        ) {
            GlassField(
                value = joinCode,
                onValueChange = { joinCode = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(12) },
                placeholder = "Nhập mã mời"
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    vm: AppViewModel,
    me: UserProfile,
    chat: Chat,
    pref: ChatPref?,
    nicknames: Map<String, String>,
    onLong: () -> Unit
) {
    val c = Nest.colors
    val otherId = if (chat.isGroup) "" else chat.members.firstOrNull { it != me.uid }.orEmpty()
    val other by rememberUser(otherId)
    val title = if (chat.isGroup) chat.name else displayName(other, nicknames[otherId])
    val unreadCount = chat.unreadFor(me.uid)
    val marked = pref?.markedUnread == true
    val muted = pref?.muted == true
    val draft = vm.drafts[chat.id].orEmpty()
    val prefix = if (chat.lastSender == me.uid) "Bạn: " else ""
    val preview = when {
        draft.isNotEmpty() -> "Bản nháp: $draft"
        chat.lastKind == "image" -> "${prefix}[Hình ảnh]"
        else -> prefix + chat.lastText
    }
    val bold = unreadCount > 0 || marked

    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { vm.push(Route.Chat(chat.id)) }, onLongClick = onLong)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (chat.isGroup) GroupAvatar(52.dp, chat.avatar) else Avatar(other, "?", 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(
                    title,
                    Modifier.weight(1f, fill = false),
                    weight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1
                )
                if (pref?.pinned == true) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.PushPin, null, Modifier.size(14.dp), tint = c.textSoft)
                }
                if (muted) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.NotificationsOff, null, Modifier.size(14.dp), tint = c.textSoft)
                }
            }
            Spacer(Modifier.height(2.dp))
            Label(
                preview,
                size = 14,
                color = if (draft.isNotEmpty()) c.danger else if (bold) c.text else c.textSoft,
                weight = if (bold) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Label(Fmt.chatTime(chat.lastTime), size = 12, color = c.textSoft)
            Spacer(Modifier.height(6.dp))
            if (unreadCount > 0) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (muted) c.textSoft else c.badge)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Label(if (unreadCount > 99) "99+" else unreadCount.toString(), size = 11, weight = FontWeight.Bold, color = Color.White)
                }
            } else if (marked) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(c.badge)
                )
            } else {
                Spacer(Modifier.size(10.dp))
            }
        }
    }
}

@Composable
fun FriendsScreen(vm: AppViewModel, me: UserProfile) {
    val c = Nest.colors
    val scope = rememberCoroutineScope()
    val friendIds by vm.friendIds.collectAsStateWithLifecycle()
    val nicknames by vm.nicknames.collectAsStateWithLifecycle()
    val incoming by vm.incoming.collectAsStateWithLifecycle()
    val people by rememberFriendProfiles(friendIds)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Bạn bè", actions = {
            IconBtn(Icons.Rounded.PersonAdd, { vm.push(Route.AddFriend) })
        })
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            if (incoming.isNotEmpty()) {
                item(key = "requests_title") { SectionTitle("Lời mời kết bạn (${incoming.size})") }
                items(incoming, key = { "req_" + it.id }) { request ->
                    val user by rememberUser(request.from)
                    val profile = user
                    if (profile != null) {
                        PersonRow(profile, null, { vm.push(Route.User(profile.uid)) }) {
                            SmallButton("Chấp nhận", true) {
                                scope.launch {
                                    val ok = Backend.acceptRequest(me.uid, request.from)
                                    vm.toast(if (ok) "Đã trở thành bạn bè" else "Có lỗi, vui lòng thử lại")
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            SmallButton("Từ chối", false) { Backend.deleteRequest(request.from, me.uid) }
                        }
                    }
                }
            }
            item(key = "friends_title") { SectionTitle("Bạn bè (${friendIds.size})") }
            if (friendIds.isEmpty()) {
                item(key = "friends_empty") {
                    EmptyCard(
                        "Chưa có bạn bè",
                        "Tìm bạn bằng ID hoặc quét mã QR của họ.",
                        "Thêm bạn"
                    ) { vm.push(Route.AddFriend) }
                }
            } else {
                items(people, key = { it.uid }) { person ->
                    PersonRow(person, nicknames[person.uid], { vm.openDm(person.uid) }) {
                        IconBtn(Icons.Rounded.Info, { vm.push(Route.User(person.uid)) }, tint = c.textSoft)
                    }
                }
            }
        }
    }
}

@Composable
fun PersonCard(vm: AppViewModel, me: UserProfile, person: UserProfile) {
    val c = Nest.colors
    val scope = rememberCoroutineScope()
    val friendIds by vm.friendIds.collectAsStateWithLifecycle()
    val incoming by vm.incoming.collectAsStateWithLifecycle()
    val outgoing by vm.outgoing.collectAsStateWithLifecycle()
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    val nicknames by vm.nicknames.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var nickDialog by remember { mutableStateOf(false) }
    var reportDialog by remember { mutableStateOf(false) }
    var viewAvatar by remember { mutableStateOf(false) }

    val isMe = person.uid == me.uid
    val isFriend = person.uid in friendIds
    val isBlocked = person.uid in blocked
    val nickname = nicknames[person.uid].orEmpty()
    var nickInput by remember(nickname) { mutableStateOf(nickname) }
    val incomingRequest = incoming.firstOrNull { it.from == person.uid }
    val outgoingRequest = outgoing.firstOrNull { it.to == person.uid }

    Glass(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), RoundedCornerShape(26.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(
                person,
                person.handle,
                92.dp,
                Modifier.clickable(enabled = person.avatar.isNotEmpty()) { viewAvatar = true }
            )
            Spacer(Modifier.height(12.dp))
            Label(displayName(person, nickname), size = 20, weight = FontWeight.Bold, align = TextAlign.Center)
            if (nickname.isNotEmpty()) Label(person.name, size = 13, color = c.textSoft)
            Label("@${person.handle}", size = 14, color = c.textSoft)
            if (person.status.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Label(person.status, size = 14, align = TextAlign.Center, weight = FontWeight.Medium)
            }
            if (person.birthday.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Label("Sinh nhật: ${person.birthday}", size = 13, color = c.textSoft)
            }
            if (person.bio.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Label(person.bio, size = 14, align = TextAlign.Center)
            }
            Spacer(Modifier.height(18.dp))
            when {
                isMe -> Label("Đây là bạn", color = c.textSoft)
                isBlocked -> NestButton(
                    "Bỏ chặn",
                    { Backend.unblock(me.uid, person.uid) },
                    Modifier.fillMaxWidth(),
                    primary = false
                )
                isFriend -> {
                    NestButton("Nhắn tin", { vm.openDm(person.uid) }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    NestButton("Đặt biệt danh", { nickDialog = true }, Modifier.fillMaxWidth(), primary = false)
                    Spacer(Modifier.height(8.dp))
                    NestButton("Hủy kết bạn", { confirmRemove = true }, Modifier.fillMaxWidth(), primary = false)
                }
                incomingRequest != null -> NestButton(
                    "Chấp nhận lời mời",
                    {
                        busy = true
                        scope.launch {
                            val ok = Backend.acceptRequest(me.uid, person.uid)
                            busy = false
                            vm.toast(if (ok) "Đã trở thành bạn bè" else "Có lỗi, vui lòng thử lại")
                        }
                    },
                    Modifier.fillMaxWidth(),
                    loading = busy
                )
                outgoingRequest != null -> NestButton(
                    "Hủy lời mời đã gửi",
                    { Backend.deleteRequest(me.uid, person.uid) },
                    Modifier.fillMaxWidth(),
                    primary = false
                )
                !person.allowRequests -> Label(
                    "Người này không nhận lời mời kết bạn.",
                    color = c.textSoft,
                    size = 14,
                    align = TextAlign.Center
                )
                else -> NestButton(
                    "Kết bạn",
                    {
                        busy = true
                        scope.launch {
                            val ok = Backend.sendRequest(me.uid, person.uid)
                            busy = false
                            vm.toast(if (ok) "Đã gửi lời mời kết bạn" else "Không gửi được lời mời")
                        }
                    },
                    Modifier.fillMaxWidth(),
                    loading = busy
                )
            }
            if (!isMe) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Label(
                        "Báo cáo",
                        Modifier.clickable { reportDialog = true }.padding(6.dp),
                        size = 13,
                        color = c.textSoft
                    )
                    if (!isBlocked) {
                        Label(
                            "Chặn",
                            Modifier.clickable { confirmBlock = true }.padding(6.dp),
                            size = 13,
                            color = c.danger
                        )
                    }
                }
            }
        }
    }

    if (viewAvatar) ImageViewer(person.avatar) { viewAvatar = false }

    if (confirmRemove) {
        GlassDialog(
            title = "Hủy kết bạn?",
            onDismiss = { confirmRemove = false },
            confirmText = "Hủy kết bạn",
            danger = true,
            onConfirm = {
                confirmRemove = false
                scope.launch {
                    val ok = Backend.removeFriend(me.uid, person.uid)
                    vm.toast(if (ok) "Đã hủy kết bạn" else "Có lỗi, vui lòng thử lại")
                }
            }
        ) {
            Label("${person.name} sẽ không còn trong danh sách bạn bè của bạn.", color = c.textSoft, size = 14)
        }
    }

    if (confirmBlock) {
        GlassDialog(
            title = "Chặn ${person.name}?",
            onDismiss = { confirmBlock = false },
            confirmText = "Chặn",
            dismissText = "Hủy",
            danger = true,
            onConfirm = {
                confirmBlock = false
                scope.launch {
                    val ok = Backend.block(me.uid, person.uid)
                    vm.toast(if (ok) "Đã chặn người dùng" else "Có lỗi, vui lòng thử lại")
                }
            }
        ) {
            Label("Hai bên sẽ không còn là bạn bè và không thể nhắn tin cho nhau.", color = c.textSoft, size = 14)
        }
    }

    if (nickDialog) {
        GlassDialog(
            title = "Đặt biệt danh",
            onDismiss = { nickDialog = false },
            confirmText = "Lưu",
            dismissText = "Hủy",
            onConfirm = {
                nickDialog = false
                scope.launch {
                    val ok = Backend.setNickname(me.uid, person.uid, nickInput)
                    if (!ok) vm.toast("Không lưu được biệt danh")
                }
            }
        ) {
            GlassField(
                value = nickInput,
                onValueChange = { nickInput = it.take(30) },
                placeholder = "Biệt danh (để trống để xóa)"
            )
        }
    }

    if (reportDialog) {
        GlassDialog(title = "Báo cáo ${person.name}", onDismiss = { reportDialog = false }, dismissText = "Hủy") {
            for (reason in userReportReasons) {
                ActionRow(reason, {
                    reportDialog = false
                    scope.launch {
                        val ok = Backend.reportUser(person.uid, reason)
                        vm.toast(if (ok) "Đã gửi báo cáo, cảm ơn bạn" else "Không gửi được báo cáo")
                    }
                })
            }
        }
    }
}

@Composable
fun AddFriendScreen(vm: AppViewModel, me: UserProfile) {
    val c = Nest.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<UserProfile?>(null) }
    var showMyQr by remember { mutableStateOf(false) }
    val myQrBitmap = remember(me.uid) { QrCodes.bitmap(QrCodes.PREFIX + me.uid, 768) }
    val myQr = remember(myQrBitmap) { myQrBitmap.asImageBitmap() }

    val scanned = vm.scannedUid
    LaunchedEffect(scanned) {
        if (scanned != null) {
            vm.scannedUid = null
            val found = Backend.fetchUser(scanned)
            if (found == null) {
                vm.toast("Mã QR không hợp lệ")
            } else {
                result = found
                searched = true
            }
        }
    }

    fun search() {
        val handle = query.trim().lowercase().removePrefix("@")
        if (handle.length < 4 || searching) return
        searching = true
        scope.launch {
            result = Backend.findByHandle(handle)
            searched = true
            searching = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("Thêm bạn")
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassField(
                value = query,
                onValueChange = { input ->
                    query = input.lowercase().filter { ch -> ch in 'a'..'z' || ch in '0'..'9' || ch == '_' || ch == '@' }.take(21)
                },
                placeholder = "Nhập ID của bạn bè",
                modifier = Modifier.weight(1f),
                imeAction = ImeAction.Search,
                onImeAction = { search() },
                leading = { Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = c.textSoft) }
            )
            Spacer(Modifier.width(10.dp))
            NestButton("Tìm", { search() }, enabled = query.trim().length >= 4, loading = searching)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Glass(Modifier.weight(1f), RoundedCornerShape(16.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.push(Route.QrScan) }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(20.dp), tint = c.text)
                    Spacer(Modifier.width(8.dp))
                    Label("Quét mã QR", weight = FontWeight.Medium)
                }
            }
            Glass(Modifier.weight(1f), RoundedCornerShape(16.dp), strong = showMyQr) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showMyQr = !showMyQr }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.QrCode2, null, Modifier.size(20.dp), tint = c.text)
                    Spacer(Modifier.width(8.dp))
                    Label("Mã của tôi", weight = FontWeight.Medium)
                }
            }
        }

        if (showMyQr) {
            Spacer(Modifier.height(14.dp))
            Glass(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(26.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .padding(12.dp)
                    ) {
                        Image(myQr, null, Modifier.size(216.dp), contentScale = ContentScale.Fit)
                    }
                    Spacer(Modifier.height(12.dp))
                    Label(me.name, weight = FontWeight.Bold, size = 17)
                    Label("@${me.handle}", color = c.textSoft, size = 14)
                    Spacer(Modifier.height(4.dp))
                    Label("Đưa mã này cho bạn bè để họ quét và kết bạn.", size = 12, color = c.textSoft, align = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            NestButton(
                                "Lưu ảnh",
                                {
                                    val ok = QrCodes.save(context, myQrBitmap)
                                    vm.toast(if (ok) "Đã lưu vào thư viện ảnh" else "Không lưu được ảnh")
                                },
                                Modifier.weight(1f),
                                primary = false
                            )
                        }
                        NestButton(
                            "Chia sẻ",
                            { QrCodes.share(context, myQrBitmap, "Kết bạn với mình trên Nest: @${me.handle}") },
                            Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        val found = result
        if (found != null) {
            PersonCard(vm, me, found)
        } else if (searched) {
            Label(
                "Không tìm thấy người dùng. ID có thể sai hoặc người đó đã ẩn khỏi tìm kiếm. Hãy thử quét mã QR.",
                Modifier.padding(horizontal = 24.dp),
                color = c.textSoft,
                size = 14,
                align = TextAlign.Center
            )
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
fun UserScreen(vm: AppViewModel, me: UserProfile, uid: String) {
    val user by rememberUser(uid)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("Hồ sơ", onBack = { vm.back() })
        val profile = user
        if (profile == null) Loading(Modifier.height(240.dp)) else PersonCard(vm, me, profile)
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
fun BlockedListScreen(vm: AppViewModel, me: UserProfile) {
    val c = Nest.colors
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    val people by rememberFriendProfiles(blocked)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Người đã chặn", onBack = { vm.back() })
        if (blocked.isEmpty()) {
            Label(
                "Bạn chưa chặn ai.",
                Modifier.padding(24.dp),
                color = c.textSoft,
                align = TextAlign.Center
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(people, key = { it.uid }) { person ->
                    PersonRow(person, null, { vm.push(Route.User(person.uid)) }) {
                        SmallButton("Bỏ chặn", false) { Backend.unblock(me.uid, person.uid) }
                    }
                }
            }
        }
    }
}

@Composable
fun CheckDot(checked: Boolean) {
    val c = Nest.colors
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (checked) c.accent else Color.Transparent)
            .border(1.5.dp, if (checked) c.accent else c.textSoft, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = c.onAccent)
    }
}

@Composable
fun NewGroupScreen(vm: AppViewModel, me: UserProfile) {
    val scope = rememberCoroutineScope()
    val friendIds by vm.friendIds.collectAsStateWithLifecycle()
    val nicknames by vm.nicknames.collectAsStateWithLifecycle()
    val people by rememberFriendProfiles(friendIds)
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun create() {
        val title = name.trim()
        if (Moderation.scanText(title) != null) {
            error = "Tên nhóm chứa ngôn từ không phù hợp"
            return
        }
        creating = true
        error = null
        scope.launch {
            val id = Backend.createGroup(me.uid, title, selected.toList())
            creating = false
            if (id == null) error = "Không tạo được nhóm, hãy kiểm tra kết nối" else vm.replaceTop(Route.Chat(id))
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Tạo nhóm")
        GlassField(
            value = name,
            onValueChange = { name = it.take(40) },
            placeholder = "Tên nhóm",
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        SectionTitle("Chọn thành viên (${selected.size})")
        if (people.isEmpty()) {
            EmptyCard("Bạn chưa có bạn bè nào", "Hãy kết bạn trước khi tạo nhóm.", "Thêm bạn") { vm.push(Route.AddFriend) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(people, key = { it.uid }) { person ->
                val checked = person.uid in selected
                PersonRow(person, nicknames[person.uid], {
                    selected = if (checked) selected - person.uid else selected + person.uid
                }) { CheckDot(checked) }
            }
        }
        error?.let { Banner(it) }
        NestButton(
            text = "Tạo nhóm",
            onClick = { create() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 84.dp),
            enabled = name.trim().isNotEmpty() && selected.isNotEmpty(),
            loading = creating
        )
    }
}
