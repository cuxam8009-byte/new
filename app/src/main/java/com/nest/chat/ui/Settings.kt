package com.nest.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nest.chat.AppViewModel
import com.nest.chat.data.Backend
import com.nest.chat.data.Route
import com.nest.chat.data.UserProfile
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: AppViewModel, me: UserProfile) {
    val c = Nest.colors
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Cài đặt")
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Glass(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                RoundedCornerShape(22.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.push(Route.Profile) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(me, me.handle, 56.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Label(me.name, size = 17, weight = FontWeight.Bold, maxLines = 1)
                        Label("@${me.handle}", size = 13, color = c.textSoft, maxLines = 1)
                    }
                    Label("Sửa", size = 14, weight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingRow(Icons.Rounded.Palette, "Giao diện", { vm.push(Route.SettingsAppearance) }, subtitle = "Chế độ sáng tối, nút điều hướng")
            SettingRow(Icons.Rounded.Visibility, "Quyền riêng tư", { vm.push(Route.SettingsPrivacy) }, subtitle = "Tìm kiếm, trạng thái đã xem")
            SettingRow(Icons.Rounded.Lock, "Bảo mật", { vm.push(Route.SettingsSecurity) }, subtitle = "Khóa PIN, chặn chụp màn hình, mật khẩu")
            SettingRow(Icons.Rounded.Block, "Người đã chặn", { vm.push(Route.BlockedList) }, subtitle = "Quản lý danh sách chặn")
            SettingRow(Icons.Rounded.Flag, "Tài khoản và vi phạm", { vm.push(Route.SettingsAccount) }, subtitle = "Tình trạng tài khoản, quy tắc cộng đồng")
            SettingRow(Icons.Rounded.Info, "Phiên bản", { }, subtitle = "Nest 1.0.0", chevron = false)
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
fun AppearanceScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Giao diện", onBack = { vm.back() })
        SectionTitle("Chế độ màu")
        Column(Modifier.padding(horizontal = 16.dp)) {
            Segmented(listOf("Hệ thống", "Sáng", "Tối"), vm.themeMode, { vm.setTheme(it) })
        }
        SectionTitle("Nút điều hướng")
        SettingRow(
            Icons.Rounded.Settings,
            "Đặt lại vị trí nút tròn",
            {
                vm.resetBubble()
                vm.toast("Đã đưa nút về góc phải")
            },
            subtitle = "Kéo nút tròn đến bất kỳ đâu trên màn hình",
            chevron = false
        )
    }
}

@Composable
fun PrivacyScreen(vm: AppViewModel, me: UserProfile) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Quyền riêng tư", onBack = { vm.back() })
        SectionTitle("Người khác thấy gì")
        SettingSwitch(
            Icons.Rounded.Search,
            "Cho phép tìm bằng ID",
            "Tắt để chỉ kết bạn qua mã QR",
            me.discoverable
        ) { Backend.setFlag("discoverable", it) }
        SettingSwitch(
            Icons.Rounded.Visibility,
            "Hiện trạng thái đã xem",
            "Bạn chỉ thấy người khác đã xem khi cũng bật mục này",
            me.readReceipts
        ) { Backend.setFlag("readReceipts", it) }
        SettingSwitch(
            Icons.Rounded.VisibilityOff,
            "Ẩn trạng thái hoạt động",
            "Ẩn giờ truy cập cuối và trạng thái đang nhập của bạn, bạn cũng không thấy của người khác",
            me.hideOnline
        ) { Backend.setFlag("hideOnline", it) }
        SettingSwitch(
            Icons.Rounded.PersonAdd,
            "Cho phép nhận lời mời kết bạn",
            "Tắt để không ai gửi được lời mời kết bạn cho bạn",
            me.allowRequests
        ) { Backend.setFlag("allowRequests", it) }
        Spacer(Modifier.height(12.dp))
        Banner(
            "Tin nhắn được lưu trên máy chủ Firebase và chỉ thành viên của cuộc trò chuyện đọc được. Tin nhắn chưa được mã hóa đầu cuối.",
            danger = false
        )
    }
}

@Composable
fun SecurityScreen(vm: AppViewModel) {
    val c = Nest.colors
    val scope = rememberCoroutineScope()
    var pinDialog by remember { mutableStateOf(false) }
    var passDialog by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var passError by remember { mutableStateOf<String?>(null) }
    var passBusy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Bảo mật", onBack = { vm.back() })
        SectionTitle("Bảo vệ ứng dụng")
        SettingSwitch(
            Icons.Rounded.Lock,
            "Khóa bằng mã PIN",
            "Tự khóa sau 30 giây khi rời ứng dụng",
            vm.pinEnabled
        ) { enabled ->
            if (enabled) {
                pin = ""
                pinConfirm = ""
                pinError = null
                pinDialog = true
            } else {
                vm.disablePin()
            }
        }
        SettingSwitch(
            Icons.Rounded.PhotoLibrary,
            "Chặn chụp màn hình",
            "Ẩn nội dung khi chụp hoặc xem ở đa nhiệm",
            vm.blockShots
        ) { vm.setBlockShots(it) }
        SectionTitle("Tài khoản")
        SettingRow(
            Icons.Rounded.Shield,
            "Đổi mật khẩu",
            {
                oldPass = ""
                newPass = ""
                passError = null
                passDialog = true
            }
        )
    }

    if (pinDialog) {
        GlassDialog(
            title = "Đặt mã PIN",
            onDismiss = { pinDialog = false },
            confirmText = "Lưu",
            dismissText = "Hủy",
            onConfirm = {
                when {
                    pin.length !in 4..6 -> pinError = "Mã PIN gồm 4 đến 6 chữ số"
                    pin != pinConfirm -> pinError = "Hai mã PIN không khớp"
                    else -> {
                        vm.enablePin(pin)
                        pinDialog = false
                        vm.toast("Đã bật khóa PIN")
                    }
                }
            }
        ) {
            GlassField(
                value = pin,
                onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(6) },
                placeholder = "Mã PIN",
                password = true,
                keyboardType = KeyboardType.NumberPassword
            )
            Spacer(Modifier.height(8.dp))
            GlassField(
                value = pinConfirm,
                onValueChange = { pinConfirm = it.filter { ch -> ch.isDigit() }.take(6) },
                placeholder = "Nhập lại mã PIN",
                password = true,
                keyboardType = KeyboardType.NumberPassword
            )
            pinError?.let {
                Spacer(Modifier.height(8.dp))
                Label(it, size = 13, color = c.danger)
            }
        }
    }

    if (passDialog) {
        GlassDialog(
            title = "Đổi mật khẩu",
            onDismiss = { passDialog = false },
            confirmText = if (passBusy) "Đang lưu..." else "Đổi mật khẩu",
            dismissText = "Hủy",
            onConfirm = {
                if (newPass.length < 6) {
                    passError = "Mật khẩu mới tối thiểu 6 ký tự"
                } else if (!passBusy) {
                    passBusy = true
                    passError = null
                    scope.launch {
                        val error = Backend.changePassword(oldPass, newPass)
                        passBusy = false
                        if (error == null) {
                            passDialog = false
                            vm.toast("Đã đổi mật khẩu")
                        } else {
                            passError = error
                        }
                    }
                }
            }
        ) {
            GlassField(
                value = oldPass,
                onValueChange = { oldPass = it },
                placeholder = "Mật khẩu hiện tại",
                password = true,
                keyboardType = KeyboardType.Password
            )
            Spacer(Modifier.height(8.dp))
            GlassField(
                value = newPass,
                onValueChange = { newPass = it },
                placeholder = "Mật khẩu mới",
                password = true,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            )
            passError?.let {
                Spacer(Modifier.height(8.dp))
                Label(it, size = 13, color = c.danger)
            }
        }
    }
}

@Composable
fun AccountScreen(vm: AppViewModel, me: UserProfile) {
    val c = Nest.colors
    val now by rememberNow(me.bannedUntil)
    var confirmLogout by remember { mutableStateOf(false) }
    val banned = me.bannedUntil > now

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Tài khoản và vi phạm", onBack = { vm.back() })
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Glass(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Label("Tình trạng tài khoản", weight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Label(
                        if (banned) "Đang bị cấm chat đến ${Fmt.full(me.bannedUntil)}" else "Hoạt động bình thường",
                        color = if (banned) c.danger else c.text
                    )
                    Spacer(Modifier.height(4.dp))
                    Label("Số lần vi phạm: ${me.strikes}", size = 14, color = c.textSoft)
                }
            }
            SectionTitle("Quy tắc cộng đồng")
            Glass(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Label("Không dùng ngôn từ tục tĩu hoặc xúc phạm trong tin nhắn.", size = 14)
                    Spacer(Modifier.height(8.dp))
                    Label("Không gửi hình ảnh nhạy cảm.", size = 14)
                    Spacer(Modifier.height(8.dp))
                    Label(
                        "Vi phạm lần 1: cấm chat 24 giờ. Lần 2: 3 ngày. Lần 3: 7 ngày. Lần 4: 30 ngày. Từ lần 5: tài khoản bị đình chỉ.",
                        size = 14,
                        color = c.textSoft
                    )
                    Spacer(Modifier.height(8.dp))
                    Label(
                        "Hình ảnh nhạy cảm bị phát hiện sẽ dẫn đến đình chỉ tài khoản tạm thời. Bạn có thể kháng cáo.",
                        size = 14,
                        color = c.textSoft
                    )
                    Spacer(Modifier.height(8.dp))
                    Label("Bạn bè có thể báo cáo tin nhắn vi phạm, hệ thống sẽ kiểm tra và xử lý.", size = 14, color = c.textSoft)
                }
            }
            Spacer(Modifier.height(16.dp))
            SettingRow(Icons.Rounded.PowerSettingsNew, "Đăng xuất", { confirmLogout = true }, danger = true, chevron = false)
            Spacer(Modifier.height(96.dp))
        }
    }

    if (confirmLogout) {
        GlassDialog(
            title = "Đăng xuất?",
            onDismiss = { confirmLogout = false },
            confirmText = "Đăng xuất",
            dismissText = "Hủy",
            danger = true,
            onConfirm = {
                confirmLogout = false
                vm.logout()
            }
        ) {
            Label("Mã PIN khóa ứng dụng trên máy này cũng sẽ bị xóa.", size = 14, color = c.textSoft)
        }
    }
}
