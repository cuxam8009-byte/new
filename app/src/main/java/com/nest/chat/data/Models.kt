package com.nest.chat.data

import androidx.compose.runtime.Immutable

@Immutable
data class UserProfile(
    val uid: String,
    val handle: String,
    val name: String,
    val bio: String,
    val avatar: String,
    val status: String,
    val birthday: String,
    val discoverable: Boolean,
    val readReceipts: Boolean,
    val hideOnline: Boolean,
    val allowRequests: Boolean,
    val profileDone: Boolean,
    val strikes: Int,
    val bannedUntil: Long,
    val suspended: Boolean,
    val lastSeen: Long,
    val admin: Boolean
)

@Immutable
data class Chat(
    val id: String,
    val isGroup: Boolean,
    val name: String,
    val description: String,
    val avatar: String,
    val members: List<String>,
    val admins: List<String>,
    val createdBy: String,
    val onlyAdminsPost: Boolean,
    val inviteCode: String,
    val disbanded: Boolean,
    val pinnedId: String,
    val pinnedText: String,
    val pinnedBy: String,
    val lastText: String,
    val lastKind: String,
    val lastSender: String,
    val lastTime: Long,
    val unread: Map<String, Int>
) {
    fun unreadFor(uid: String): Int = unread[uid] ?: 0

    fun isAdmin(uid: String): Boolean = uid == createdBy || uid in admins
}

@Immutable
data class Message(
    val id: String,
    val sender: String,
    val text: String,
    val image: String,
    val width: Int,
    val height: Int,
    val time: Long,
    val flagged: Boolean,
    val recalled: Boolean,
    val edited: Boolean,
    val pending: Boolean,
    val replyTo: String,
    val replyText: String,
    val replySender: String,
    val fwd: Boolean,
    val mentions: List<String>,
    val reactions: Map<String, String>
)

@Immutable
data class ChatPref(
    val pinned: Boolean,
    val pinnedAt: Long,
    val muted: Boolean,
    val archived: Boolean,
    val markedUnread: Boolean,
    val clearedAt: Long,
    val hidden: Set<String>
)

@Immutable
data class FriendRequest(
    val id: String,
    val from: String,
    val to: String
)

class ReplyRef(val id: String, val text: String, val sender: String)

sealed interface Session {
    data object Loading : Session
    data object SignedOut : Session
    data class SignedIn(val uid: String) : Session
}

sealed interface Route {
    data object Chats : Route
    data object Archived : Route
    data object Friends : Route
    data object AddFriend : Route
    data object QrScan : Route
    data object NewGroup : Route
    data object Profile : Route
    data object Settings : Route
    data object SettingsAppearance : Route
    data object SettingsPrivacy : Route
    data object SettingsSecurity : Route
    data object SettingsAccount : Route
    data object BlockedList : Route
    data class Chat(val id: String) : Route
    data class GroupInfo(val id: String) : Route
    data class User(val uid: String) : Route
}
