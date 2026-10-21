package com.nest.chat

import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nest.chat.data.Backend
import com.nest.chat.data.Chat
import com.nest.chat.data.ChatPref
import com.nest.chat.data.FriendRequest
import com.nest.chat.data.LocalPrefs
import com.nest.chat.data.Route
import com.nest.chat.data.Session
import com.nest.chat.data.UserCache
import com.nest.chat.data.UserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = LocalPrefs(app)

    val session = MutableStateFlow<Session>(Session.Loading)
    val me = MutableStateFlow<UserProfile?>(null)
    val chats = MutableStateFlow<List<Chat>>(emptyList())
    val friendIds = MutableStateFlow<Set<String>>(emptySet())
    val nicknames = MutableStateFlow<Map<String, String>>(emptyMap())
    val blocked = MutableStateFlow<Set<String>>(emptySet())
    val chatPrefs = MutableStateFlow<Map<String, ChatPref>>(emptyMap())
    val incoming = MutableStateFlow<List<FriendRequest>>(emptyList())
    val outgoing = MutableStateFlow<List<FriendRequest>>(emptyList())

    val stack = mutableStateListOf<Route>(Route.Chats)
    val drafts = mutableStateMapOf<String, String>()

    var locked by mutableStateOf(prefs.hasPin)
        private set
    var themeMode by mutableIntStateOf(prefs.themeMode)
        private set
    private var blockShotsState by mutableStateOf(prefs.blockScreenshots)
    var bubbleReset by mutableIntStateOf(0)
        private set
    var pinEnabled by mutableStateOf(prefs.hasPin)
        private set
    var scannedUid by mutableStateOf<String?>(null)

    private var jobs: List<Job> = emptyList()
    private var activeUid: String? = null
    private var stoppedAt = 0L
    private var foreground = false

    val blockShots: Boolean
        get() = blockShotsState

    val current: Route
        get() = stack.last()

    init {
        drafts.putAll(prefs.allDrafts())
        viewModelScope.launch {
            Backend.authFlow().catch { }.collect { user ->
                if (user == null) {
                    stopData()
                    session.value = Session.SignedOut
                } else {
                    startData(user.uid)
                    session.value = Session.SignedIn(user.uid)
                }
            }
        }
    }

    private fun startData(uid: String) {
        if (activeUid == uid) return
        stopData()
        activeUid = uid
        jobs = listOf(
            viewModelScope.launch {
                Backend.profileFlow(uid).catch { }.collect { profile ->
                    me.value = profile
                    if (profile != null) UserCache.put(profile)
                }
            },
            viewModelScope.launch { Backend.chatsFlow(uid).catch { }.collect { chats.value = it } },
            viewModelScope.launch {
                Backend.friendsFlow(uid).catch { }.collect { map ->
                    nicknames.value = map
                    friendIds.value = map.keys.toSet()
                }
            },
            viewModelScope.launch { Backend.blockedFlow(uid).catch { }.collect { blocked.value = it } },
            viewModelScope.launch { Backend.chatPrefsFlow(uid).catch { }.collect { chatPrefs.value = it } },
            viewModelScope.launch { Backend.requestsFlow("to", uid).catch { }.collect { incoming.value = it } },
            viewModelScope.launch { Backend.requestsFlow("from", uid).catch { }.collect { outgoing.value = it } },
            viewModelScope.launch {
                while (true) {
                    if (foreground && me.value?.hideOnline == false) Backend.touchPresence()
                    delay(150_000L)
                }
            }
        )
    }

    private fun stopData() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        activeUid = null
        me.value = null
        chats.value = emptyList()
        friendIds.value = emptySet()
        nicknames.value = emptyMap()
        blocked.value = emptySet()
        chatPrefs.value = emptyMap()
        incoming.value = emptyList()
        outgoing.value = emptyList()
        UserCache.clear()
        stack.clear()
        stack.add(Route.Chats)
    }

    fun push(route: Route) {
        if (stack.lastOrNull() != route) stack.add(route)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun resetTo(route: Route) {
        stack.clear()
        stack.add(Route.Chats)
        if (route != Route.Chats) stack.add(route)
    }

    fun replaceTop(route: Route) {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        stack.add(route)
    }

    fun toast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
    }

    fun openDm(other: String) {
        val mine = Backend.uid ?: return
        viewModelScope.launch {
            val id = Backend.openDm(mine, other)
            if (id == null) toast("Không mở được cuộc trò chuyện, hãy kiểm tra kết nối") else push(Route.Chat(id))
        }
    }

    fun setPref(chatId: String, fields: Map<String, Any>) {
        Backend.setChatPref(chatId, fields)
    }

    fun setDraft(chatId: String, text: String) {
        if (text.isBlank()) drafts.remove(chatId) else drafts[chatId] = text
        prefs.setDraft(chatId, text)
    }

    fun setTheme(mode: Int) {
        prefs.themeMode = mode
        themeMode = mode
    }

    fun setBlockShots(enabled: Boolean) {
        prefs.blockScreenshots = enabled
        blockShotsState = enabled
    }

    fun saveBubble(x: Float, y: Float) {
        prefs.bubbleX = x
        prefs.bubbleY = y
    }

    fun resetBubble() {
        prefs.bubbleX = 1f
        prefs.bubbleY = 0.72f
        bubbleReset++
    }

    fun unlock(pin: String): Boolean {
        val ok = prefs.checkPin(pin)
        if (ok) locked = false
        return ok
    }

    fun enablePin(pin: String) {
        prefs.setPin(pin)
        pinEnabled = true
    }

    fun disablePin() {
        prefs.clearPin()
        pinEnabled = false
        locked = false
    }

    fun onForeground() {
        foreground = true
        if (prefs.hasPin && stoppedAt > 0L && System.currentTimeMillis() - stoppedAt > 30_000L) locked = true
        if (me.value?.hideOnline == false) Backend.touchPresence()
    }

    fun onBackground() {
        foreground = false
        stoppedAt = System.currentTimeMillis()
    }

    fun logout() {
        prefs.clearPin()
        pinEnabled = false
        locked = false
        Backend.signOut()
    }
}
