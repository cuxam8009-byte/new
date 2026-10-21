package com.nest.chat

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nest.chat.data.Route
import com.nest.chat.data.Session
import com.nest.chat.data.UserProfile
import com.nest.chat.ui.AccountScreen
import com.nest.chat.ui.AddFriendScreen
import com.nest.chat.ui.AppearanceScreen
import com.nest.chat.ui.AuthScreen
import com.nest.chat.ui.BlockedListScreen
import com.nest.chat.ui.BubbleItem
import com.nest.chat.ui.ChatListScreen
import com.nest.chat.ui.ChatScreen
import com.nest.chat.ui.FriendsScreen
import com.nest.chat.ui.GroupInfoScreen
import com.nest.chat.ui.LockScreen
import com.nest.chat.ui.Loading
import com.nest.chat.ui.NavBubble
import com.nest.chat.ui.NestBackground
import com.nest.chat.ui.NestButton
import com.nest.chat.ui.NestTheme
import com.nest.chat.ui.NewGroupScreen
import com.nest.chat.ui.PrivacyScreen
import com.nest.chat.ui.QrScanScreen
import com.nest.chat.ui.ProfileScreen
import com.nest.chat.ui.ProfileSetupScreen
import com.nest.chat.ui.SecurityScreen
import com.nest.chat.ui.SettingsScreen
import com.nest.chat.ui.SuspendedScreen
import com.nest.chat.ui.UserScreen
import kotlinx.coroutines.delay

@Composable
fun NestRoot(vm: AppViewModel, activity: ComponentActivity) {
    val dark = when (vm.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    DisposableEffect(dark) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
        )
        onDispose { }
    }

    DisposableEffect(vm.blockShots) {
        if (vm.blockShots) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { }
    }

    val session by vm.session.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()

    NestTheme(dark) {
        NestBackground {
            when (session) {
                Session.Loading -> Loading()
                Session.SignedOut -> Box(Modifier.fillMaxSize().safeDrawingPadding()) { AuthScreen() }
                is Session.SignedIn -> {
                    val profile = me
                    when {
                        profile == null -> ProfileLoading(vm)
                        vm.locked -> LockScreen(vm)
                        profile.suspended -> Box(Modifier.fillMaxSize().safeDrawingPadding()) { SuspendedScreen(vm, profile) }
                        !profile.profileDone -> Box(Modifier.fillMaxSize().safeDrawingPadding()) { ProfileSetupScreen(vm, profile) }
                        else -> MainShell(vm, profile)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileLoading(vm: AppViewModel) {
    val stuck by produceState(false) {
        delay(8_000)
        value = true
    }
    Box(Modifier.fillMaxSize()) {
        Loading()
        if (stuck) {
            Box(Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(24.dp)) {
                NestButton("Đăng xuất", { vm.logout() }, primary = false)
            }
        }
    }
}

@Composable
private fun MainShell(vm: AppViewModel, me: UserProfile) {
    BackHandler(enabled = vm.stack.size > 1) { vm.back() }

    val chats by vm.chats.collectAsStateWithLifecycle()
    val prefs by vm.chatPrefs.collectAsStateWithLifecycle()
    val incoming by vm.incoming.collectAsStateWithLifecycle()
    val unread = chats.any { chat ->
        val pref = prefs[chat.id]
        (chat.unreadFor(me.uid) > 0 || pref?.markedUnread == true) && pref?.muted != true && pref?.archived != true
    }
    val pending = incoming.isNotEmpty()

    val items = listOf(
        BubbleItem(Icons.Rounded.ChatBubble, "Tin nhắn", unread) { vm.resetTo(Route.Chats) },
        BubbleItem(Icons.Rounded.People, "Bạn bè", pending) { vm.resetTo(Route.Friends) },
        BubbleItem(Icons.Rounded.PersonAdd, "Thêm bạn", false) { vm.resetTo(Route.AddFriend) },
        BubbleItem(Icons.Rounded.GroupAdd, "Tạo nhóm", false) { vm.resetTo(Route.NewGroup) },
        BubbleItem(Icons.Rounded.Person, "Hồ sơ", false) { vm.resetTo(Route.Profile) },
        BubbleItem(Icons.Rounded.Settings, "Cài đặt", false) { vm.resetTo(Route.Settings) }
    )

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Crossfade(targetState = vm.current, animationSpec = tween(140), label = "route") { route ->
                when (route) {
                    Route.Chats -> ChatListScreen(vm, me)
                    Route.Archived -> ChatListScreen(vm, me, archivedOnly = true)
                    Route.QrScan -> QrScanScreen(vm)
                    Route.BlockedList -> BlockedListScreen(vm, me)
                    Route.Friends -> FriendsScreen(vm, me)
                    Route.AddFriend -> AddFriendScreen(vm, me)
                    Route.NewGroup -> NewGroupScreen(vm, me)
                    Route.Profile -> ProfileScreen(vm, me)
                    Route.Settings -> SettingsScreen(vm, me)
                    Route.SettingsAppearance -> AppearanceScreen(vm)
                    Route.SettingsPrivacy -> PrivacyScreen(vm, me)
                    Route.SettingsSecurity -> SecurityScreen(vm)
                    Route.SettingsAccount -> AccountScreen(vm, me)
                    is Route.Chat -> ChatScreen(vm, me, route.id)
                    is Route.GroupInfo -> GroupInfoScreen(vm, me, route.id)
                    is Route.User -> UserScreen(vm, me, route.uid)
                }
            }
        }
        NavBubble(
            items = items,
            showBadge = unread || pending,
            initialX = vm.prefs.bubbleX,
            initialY = vm.prefs.bubbleY,
            onMoved = { x, y -> vm.saveBubble(x, y) },
            resetKey = vm.bubbleReset,
            modifier = Modifier.systemBarsPadding()
        )
    }
}
