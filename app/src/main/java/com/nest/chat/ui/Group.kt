package com.nest.chat.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nest.chat.AppViewModel
import com.nest.chat.data.Backend
import com.nest.chat.data.ImageUtil
import com.nest.chat.data.Moderation
import com.nest.chat.data.Route
import com.nest.chat.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GroupInfoScreen(vm: AppViewModel, me: UserProfile, chatId: String) {
    val c = Nest.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val chats by vm.chats.collectAsStateWithLifecycle()
    val friendIds by vm.friendIds.collectAsStateWithLifecycle()
    val nicknames by vm.nicknames.collectAsStateWithLifecycle()
    val chat = chats.firstOrNull { it.id == chatId }
    var newName by remember(chat?.name) { mutableStateOf(chat?.name.orEmpty()) }
    var newDesc by remember(chat?.description) { mutableStateOf(chat?.description.orEmpty()) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDisband by remember { mutableStateOf(false) }
    var confirmKick by remember { mutableStateOf<String?>(null) }
    var confirmTransfer by remember { mutableStateOf<String?>(null) }
    var memberMenu by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }

    fun act(okMessage: String, failMessage: String, block: suspend () -> Boolean) {
        scope.launch {
            val ok = block()
            vm.toast(if (ok) okMessage else failMessage)
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val prepared = withContext(Dispatchers.IO) { ImageUtil.prepare(context, uri, 320, 45_000) }
                if (prepared == null) {
                    vm.toast("Không đọc được ảnh")
                } else {
                    val ok = Backend.updateChat(chatId, mapOf("avatar" to prepared.b64))
                    vm.toast(if (ok) "Đã đổi ảnh nhóm" else "Không đổi được ảnh nhóm")
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Thông tin nhóm", onBack = { vm.back() })
        if (chat == null) {
            Loading()
        } else {
            val isOwner = chat.createdBy == me.uid
            val isAdmin = chat.isAdmin(me.uid)
            val candidates = remember(friendIds, chat.members) { friendIds.filter { it !in chat.members }.toSet() }
            val candidateProfiles by rememberFriendProfiles(candidates)

            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                item(key = "head") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GroupAvatar(
                            88.dp,
                            chat.avatar,
                            Modifier.clickable(enabled = isAdmin && !chat.disbanded) {
                                avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                        Label(chat.name, size = 20, weight = FontWeight.Bold, align = TextAlign.Center)
                        Label("${chat.members.size} thành viên", size = 14, color = c.textSoft)
                        if (chat.description.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Label(chat.description, size = 14, align = TextAlign.Center)
                        }
                        if (chat.disbanded) {
                            Spacer(Modifier.height(8.dp))
                            Label("Nhóm đã giải tán", size = 13, color = c.danger)
                        } else if (isAdmin) {
                            Spacer(Modifier.height(6.dp))
                            Label("Chạm vào ảnh để đổi ảnh nhóm", size = 12, color = c.textSoft)
                        }
                    }
                }
                if (isAdmin && !chat.disbanded) {
                    item(key = "edit") {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            GlassField(
                                value = newName,
                                onValueChange = { newName = it.take(40) },
                                placeholder = "Tên nhóm"
                            )
                            Spacer(Modifier.height(8.dp))
                            GlassField(
                                value = newDesc,
                                onValueChange = { newDesc = it.take(200) },
                                placeholder = "Mô tả nhóm",
                                singleLine = false,
                                maxLines = 4
                            )
                            Spacer(Modifier.height(8.dp))
                            NestButton(
                                text = "Lưu thông tin nhóm",
                                onClick = {
                                    val title = newName.trim()
                                    val desc = newDesc.trim()
                                    if (Moderation.scanText(title) != null || Moderation.scanText(desc) != null) {
                                        vm.toast("Nội dung chứa ngôn từ không phù hợp")
                                    } else {
                                        act("Đã lưu thông tin nhóm", "Không lưu được thông tin nhóm") {
                                            Backend.updateChat(chatId, mapOf("name" to title, "description" to desc))
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                primary = false,
                                enabled = newName.trim().isNotEmpty() &&
                                    (newName.trim() != chat.name || newDesc.trim() != chat.description)
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    item(key = "only_admins") {
                        SettingSwitch(
                            Icons.Rounded.Lock,
                            "Chỉ quản trị viên được nhắn",
                            "Thành viên thường chỉ đọc tin nhắn",
                            chat.onlyAdminsPost
                        ) { value ->
                            act("Đã cập nhật quyền nhắn tin", "Không cập nhật được") {
                                Backend.updateChat(chatId, mapOf("onlyAdminsPost" to value))
                            }
                        }
                    }
                    item(key = "invite") {
                        Glass(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            RoundedCornerShape(20.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Label("Mã mời vào nhóm", weight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                if (chat.inviteCode.isNotEmpty()) {
                                    Label(chat.inviteCode, size = 24, weight = FontWeight.Bold)
                                    Label("Bạn bè nhập mã này ở mục Tham gia nhóm bằng mã.", size = 12, color = c.textSoft)
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SmallButton("Sao chép", true) {
                                            clipboard.setText(AnnotatedString(chat.inviteCode))
                                            vm.toast("Đã sao chép mã mời")
                                        }
                                        SmallButton("Tạo mã mới", false) {
                                            scope.launch {
                                                val code = Backend.createInvite(chatId, chat.inviteCode)
                                                vm.toast(if (code != null) "Đã tạo mã mới" else "Không tạo được mã")
                                            }
                                        }
                                        SmallButton("Tắt mã", false) {
                                            act("Đã tắt mã mời", "Không tắt được mã") {
                                                Backend.revokeInvite(chatId, chat.inviteCode)
                                            }
                                        }
                                    }
                                } else {
                                    Label("Chưa có mã mời. Tạo mã để bạn bè tự tham gia nhóm.", size = 13, color = c.textSoft)
                                    Spacer(Modifier.height(10.dp))
                                    SmallButton("Tạo mã mời", true) {
                                        scope.launch {
                                            val code = Backend.createInvite(chatId, "")
                                            vm.toast(if (code != null) "Đã tạo mã mời" else "Không tạo được mã")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "members_title") { SectionTitle("Thành viên") }
                items(chat.members, key = { "m_$it" }) { uid ->
                    val user by rememberUser(uid)
                    val role = when {
                        uid == chat.createdBy -> "Trưởng nhóm"
                        uid in chat.admins -> "Phó nhóm"
                        else -> ""
                    }
                    val canManage = isAdmin && !chat.disbanded && uid != me.uid && uid != chat.createdBy &&
                        (isOwner || uid !in chat.admins)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = uid != me.uid) { vm.push(Route.User(uid)) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(user, "?", 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Label(
                                if (uid == me.uid) "Bạn" else displayName(user, nicknames[uid]),
                                weight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Label("@${user?.handle.orEmpty()}", size = 13, color = c.textSoft, maxLines = 1)
                        }
                        if (role.isNotEmpty()) Label(role, size = 12, color = c.textSoft)
                        if (canManage) {
                            IconBtn(Icons.Rounded.MoreVert, { memberMenu = uid }, tint = c.textSoft, size = 36.dp)
                        }
                    }
                }
                if (isAdmin && !chat.disbanded) {
                    item(key = "add") {
                        NestButton(
                            "Thêm thành viên",
                            {
                                selected = emptySet()
                                adding = true
                            },
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            primary = false
                        )
                    }
                }
                if (!isOwner) {
                    item(key = "leave") {
                        NestButton(
                            "Rời nhóm",
                            { confirmLeave = true },
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            danger = true
                        )
                    }
                } else if (!chat.disbanded) {
                    item(key = "owner_hint") {
                        Label(
                            "Bạn là trưởng nhóm. Hãy chuyển quyền trưởng nhóm trước khi rời nhóm.",
                            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            size = 12,
                            color = c.textSoft
                        )
                    }
                    item(key = "disband") {
                        NestButton(
                            "Giải tán nhóm",
                            { confirmDisband = true },
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            danger = true
                        )
                    }
                }
            }

            memberMenu?.let { target ->
                val targetUser = com.nest.chat.data.UserCache.peek(target)
                val targetName = displayName(targetUser, nicknames[target])
                GlassDialog(title = targetName, onDismiss = { memberMenu = null }) {
                    ActionRow("Xem hồ sơ", {
                        memberMenu = null
                        vm.push(Route.User(target))
                    })
                    if (isOwner) {
                        if (target in chat.admins) {
                            ActionRow("Gỡ quyền phó nhóm", {
                                memberMenu = null
                                act("Đã gỡ quyền phó nhóm", "Không cập nhật được") { Backend.setAdmin(chatId, target, false) }
                            })
                        } else {
                            ActionRow("Đặt làm phó nhóm", {
                                memberMenu = null
                                act("Đã đặt làm phó nhóm", "Không cập nhật được") { Backend.setAdmin(chatId, target, true) }
                            })
                        }
                        ActionRow("Chuyển quyền trưởng nhóm", {
                            memberMenu = null
                            confirmTransfer = target
                        })
                    }
                    ActionRow("Xóa khỏi nhóm", {
                        memberMenu = null
                        confirmKick = target
                    }, danger = true)
                }
            }

            confirmKick?.let { target ->
                GlassDialog(
                    title = "Xóa khỏi nhóm?",
                    onDismiss = { confirmKick = null },
                    confirmText = "Xóa",
                    dismissText = "Hủy",
                    danger = true,
                    onConfirm = {
                        confirmKick = null
                        act("Đã xóa thành viên", "Không xóa được thành viên") { Backend.kickMember(chatId, target) }
                    }
                ) {
                    Label("Người này sẽ không còn nhận được tin nhắn của nhóm.", color = c.textSoft, size = 14)
                }
            }

            confirmTransfer?.let { target ->
                GlassDialog(
                    title = "Chuyển quyền trưởng nhóm?",
                    onDismiss = { confirmTransfer = null },
                    confirmText = "Chuyển quyền",
                    dismissText = "Hủy",
                    onConfirm = {
                        confirmTransfer = null
                        act("Đã chuyển quyền trưởng nhóm", "Không chuyển quyền được") { Backend.transferOwner(chatId, target) }
                    }
                ) {
                    Label("Bạn vẫn là phó nhóm sau khi chuyển quyền.", color = c.textSoft, size = 14)
                }
            }

            if (adding) {
                GlassDialog(
                    title = "Thêm thành viên",
                    onDismiss = { adding = false },
                    confirmText = "Thêm",
                    dismissText = "Hủy",
                    onConfirm = {
                        val ids = selected.toList()
                        adding = false
                        if (ids.isNotEmpty()) {
                            act("Đã thêm thành viên", "Không thêm được thành viên") { Backend.addMembers(chatId, ids) }
                        }
                    }
                ) {
                    if (candidateProfiles.isEmpty()) {
                        Label("Không còn bạn bè nào để thêm.", color = c.textSoft, size = 14)
                    } else {
                        Column(
                            Modifier
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (person in candidateProfiles) {
                                val checked = person.uid in selected
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selected = if (checked) selected - person.uid else selected + person.uid
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Avatar(person, person.handle, 38.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Label(displayName(person, nicknames[person.uid]), Modifier.weight(1f), maxLines = 1)
                                    CheckDot(checked)
                                }
                            }
                        }
                    }
                }
            }

            if (confirmLeave) {
                GlassDialog(
                    title = "Rời nhóm?",
                    onDismiss = { confirmLeave = false },
                    confirmText = "Rời nhóm",
                    dismissText = "Hủy",
                    danger = true,
                    onConfirm = {
                        confirmLeave = false
                        scope.launch {
                            val ok = Backend.leaveGroup(chatId, me.uid)
                            if (ok) vm.resetTo(Route.Chats) else vm.toast("Không rời được nhóm, thử lại sau")
                        }
                    }
                ) {
                    Label("Bạn sẽ không nhận được tin nhắn của nhóm này nữa.", color = c.textSoft, size = 14)
                }
            }

            if (confirmDisband) {
                GlassDialog(
                    title = "Giải tán nhóm?",
                    onDismiss = { confirmDisband = false },
                    confirmText = "Giải tán",
                    dismissText = "Hủy",
                    danger = true,
                    onConfirm = {
                        confirmDisband = false
                        act("Nhóm đã giải tán", "Không giải tán được nhóm") { Backend.disbandGroup(chatId) }
                    }
                ) {
                    Label("Không ai có thể nhắn thêm trong nhóm. Hành động này không hoàn tác được.", color = c.textSoft, size = 14)
                }
            }
        }
    }
}
