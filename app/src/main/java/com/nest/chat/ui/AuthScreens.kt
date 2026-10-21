package com.nest.chat.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nest.chat.AppViewModel
import com.nest.chat.data.Backend
import com.nest.chat.data.ImageUtil
import com.nest.chat.data.Moderation
import com.nest.chat.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val birthdayPattern = Regex("^(0[1-9]|[12][0-9]|3[01])/(0[1-9]|1[0-2])$")

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Nest.colors
    Glass(modifier.fillMaxWidth(), RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(4.dp)) {
            options.forEachIndexed { index, option ->
                val active = index == selected
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) c.accent else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Label(
                        option,
                        size = 14,
                        weight = FontWeight.SemiBold,
                        color = if (active) c.onAccent else c.text
                    )
                }
            }
        }
    }
}

@Composable
fun AuthScreen() {
    val c = Nest.colors
    val scope = rememberCoroutineScope()
    var registerMode by remember { mutableStateOf(false) }
    var handle by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val handlePattern = remember { Regex("^[a-z0-9_]{4,20}$") }

    fun submit() {
        if (loading) return
        if (!handlePattern.matches(handle)) {
            error = "ID gồm 4-20 ký tự: chữ thường, số hoặc dấu gạch dưới"
            return
        }
        if (password.length < 6) {
            error = "Mật khẩu tối thiểu 6 ký tự"
            return
        }
        if (registerMode && password != confirm) {
            error = "Mật khẩu nhập lại không khớp"
            return
        }
        loading = true
        error = null
        scope.launch {
            val result = if (registerMode) Backend.register(handle, password) else Backend.login(handle, password)
            loading = false
            error = result
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))
        Glass(Modifier.size(84.dp), CircleShape, strong = true) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ChatBubble, null, Modifier.size(38.dp), tint = c.text)
            }
        }
        Spacer(Modifier.height(20.dp))
        Label("Nest", size = 34, weight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Label("Nơi trò chuyện riêng tư của hội bạn", color = c.textSoft, align = TextAlign.Center)
        Spacer(Modifier.height(32.dp))

        Segmented(
            listOf("Đăng nhập", "Tạo tài khoản"),
            if (registerMode) 1 else 0,
            { registerMode = it == 1; error = null }
        )
        Spacer(Modifier.height(16.dp))

        GlassField(
            value = handle,
            onValueChange = { input ->
                handle = input.lowercase().filter { ch -> ch in 'a'..'z' || ch in '0'..'9' || ch == '_' }.take(20)
            },
            placeholder = "ID đăng nhập",
            leading = { Icon(Icons.Rounded.Person, null, Modifier.size(20.dp), tint = c.textSoft) }
        )
        Spacer(Modifier.height(10.dp))
        GlassField(
            value = password,
            onValueChange = { password = it },
            placeholder = "Mật khẩu",
            password = true,
            keyboardType = KeyboardType.Password,
            imeAction = if (registerMode) ImeAction.Next else ImeAction.Done,
            onImeAction = { if (!registerMode) submit() },
            leading = { Icon(Icons.Rounded.Lock, null, Modifier.size(20.dp), tint = c.textSoft) }
        )
        if (registerMode) {
            Spacer(Modifier.height(10.dp))
            GlassField(
                value = confirm,
                onValueChange = { confirm = it },
                placeholder = "Nhập lại mật khẩu",
                password = true,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                onImeAction = { submit() },
                leading = { Icon(Icons.Rounded.Lock, null, Modifier.size(20.dp), tint = c.textSoft) }
            )
            Spacer(Modifier.height(8.dp))
            Label(
                "Tài khoản không gắn với email nên không thể khôi phục mật khẩu. Hãy ghi nhớ mật khẩu của bạn.",
                size = 12,
                color = c.textSoft
            )
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Label(it, color = c.danger, size = 13, align = TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))
        NestButton(
            text = if (registerMode) "Tạo tài khoản" else "Đăng nhập",
            onClick = { submit() },
            modifier = Modifier.fillMaxWidth(),
            loading = loading
        )
        Spacer(Modifier.height(20.dp))
        Label(
            "Khi tiếp tục, bạn đồng ý với quy tắc cộng đồng: không dùng ngôn từ tục tĩu, không gửi hình ảnh nhạy cảm.",
            size = 12,
            color = c.textSoft,
            align = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun ProfileEditor(
    vm: AppViewModel,
    initial: UserProfile,
    saveLabel: String,
    onSaved: () -> Unit,
    onSkip: (() -> Unit)? = null
) {
    val c = Nest.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var bio by remember { mutableStateOf(initial.bio) }
    var status by remember { mutableStateOf(initial.status) }
    var birthday by remember { mutableStateOf(initial.birthday) }
    var avatar by remember { mutableStateOf(initial.avatar) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val prepared = withContext(Dispatchers.IO) { ImageUtil.prepare(context, uri, 320, 45_000) }
                if (prepared == null) {
                    error = "Không đọc được ảnh"
                } else {
                    avatar = prepared.b64
                    error = null
                }
            }
        }
    }

    fun save() {
        val cleanName = name.trim()
        val cleanBio = bio.trim()
        if (cleanName.isEmpty()) {
            error = "Tên hiển thị không được để trống"
            return
        }
        val cleanStatus = status.trim()
        val cleanBirthday = birthday.trim()
        if (Moderation.scanText(cleanName) != null || Moderation.scanText(cleanBio) != null || Moderation.scanText(cleanStatus) != null) {
            error = "Tên, giới thiệu hoặc trạng thái chứa ngôn từ không phù hợp"
            return
        }
        if (cleanBirthday.isNotEmpty() && !birthdayPattern.matches(cleanBirthday)) {
            error = "Sinh nhật cần theo dạng ngày/tháng, ví dụ 25/12"
            return
        }
        if (saving) return
        saving = true
        error = null
        scope.launch {
            val ok = Backend.updateProfile(
                mapOf(
                    "name" to cleanName,
                    "bio" to cleanBio,
                    "status" to cleanStatus,
                    "birthday" to cleanBirthday,
                    "avatar" to avatar,
                    "profileDone" to true
                )
            )
            saving = false
            if (ok) onSaved() else error = "Không lưu được, hãy kiểm tra kết nối"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Avatar(
            initial.copy(avatar = avatar, name = name),
            initial.handle,
            104.dp,
            Modifier.clickable {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Label(
                "Đổi ảnh",
                Modifier
                    .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    .padding(6.dp),
                weight = FontWeight.SemiBold
            )
            if (avatar.isNotEmpty()) {
                Label("Xóa ảnh", Modifier.clickable { avatar = "" }.padding(6.dp), color = c.textSoft)
            }
        }
        Spacer(Modifier.height(4.dp))
        Label("@${initial.handle}", color = c.textSoft, size = 14)
        Spacer(Modifier.height(20.dp))
        GlassField(
            value = name,
            onValueChange = { name = it.take(30) },
            placeholder = "Tên hiển thị",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        GlassField(
            value = status,
            onValueChange = { status = it.take(60) },
            placeholder = "Trạng thái (ví dụ: đang bận học)",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        GlassField(
            value = bio,
            onValueChange = { bio = it.take(120) },
            placeholder = "Giới thiệu ngắn",
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 4
        )
        Spacer(Modifier.height(10.dp))
        GlassField(
            value = birthday,
            onValueChange = { birthday = it.filter { ch -> ch.isDigit() || ch == '/' }.take(5) },
            placeholder = "Sinh nhật dd/mm (không bắt buộc)",
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Number
        )
        error?.let {
            Spacer(Modifier.height(12.dp))
            Label(it, color = c.danger, size = 13, align = TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))
        NestButton(
            text = saveLabel,
            onClick = { save() },
            modifier = Modifier.fillMaxWidth(),
            loading = saving
        )
        if (onSkip != null) {
            Spacer(Modifier.height(10.dp))
            NestButton("Bỏ qua", onSkip, Modifier.fillMaxWidth(), primary = false)
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
fun ProfileSetupScreen(vm: AppViewModel, me: UserProfile) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Hoàn thiện hồ sơ")
        Label(
            "Thêm ảnh và tên hiển thị để bạn bè nhận ra bạn. Bạn có thể bỏ qua và chỉnh sau.",
            Modifier.padding(horizontal = 20.dp),
            size = 14,
            color = Nest.colors.textSoft
        )
        ProfileEditor(
            vm = vm,
            initial = me,
            saveLabel = "Lưu và tiếp tục",
            onSaved = { },
            onSkip = { scope.launch { Backend.updateProfile(mapOf("profileDone" to true)) } }
        )
    }
}

@Composable
fun ProfileScreen(vm: AppViewModel, me: UserProfile) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Hồ sơ")
        ProfileEditor(
            vm = vm,
            initial = me,
            saveLabel = "Lưu thay đổi",
            onSaved = {
                vm.toast("Đã lưu hồ sơ")
                vm.resetTo(com.nest.chat.data.Route.Chats)
            }
        )
    }
}

@Composable
fun LockScreen(vm: AppViewModel) {
    val c = Nest.colors
    val length = vm.prefs.pinLength
    var entered by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    fun press(digit: String) {
        if (entered.length >= length) return
        entered += digit
        wrong = false
        if (entered.length == length && !vm.unlock(entered)) {
            entered = ""
            wrong = true
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.Lock, null, Modifier.size(36.dp), tint = c.text)
        Spacer(Modifier.height(14.dp))
        Label("Nhập mã PIN để mở khóa", size = 18, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for (i in 0 until length) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (i < entered.length) c.text else c.glassStrong)
                        .border(1.dp, c.strokeLo, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Label(if (wrong) "Sai mã PIN, thử lại" else " ", color = c.danger, size = 13)
        Spacer(Modifier.height(16.dp))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫")
        )
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 9.dp)) {
                for (key in row) {
                    if (key.isEmpty()) {
                        Spacer(Modifier.size(72.dp))
                    } else {
                        Glass(Modifier.size(72.dp), CircleShape, strong = true) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        if (key == "⌫") {
                                            entered = entered.dropLast(1)
                                        } else {
                                            press(key)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Label(key, size = 24, weight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Label(
            "Quên mã PIN? Đăng xuất",
            Modifier
                .clickable { vm.logout() }
                .padding(10.dp),
            color = c.textSoft,
            size = 14
        )
    }
}

@Composable
fun SuspendedScreen(vm: AppViewModel, me: UserProfile) {
    val c = Nest.colors
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))
        Icon(Icons.Rounded.Shield, null, Modifier.size(52.dp), tint = c.danger)
        Spacer(Modifier.height(16.dp))
        Label("Tài khoản bị đình chỉ tạm thời", size = 22, weight = FontWeight.Bold, align = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Label(
            "Hệ thống phát hiện nội dung vi phạm quy tắc cộng đồng. Nếu bạn cho rằng đây là nhầm lẫn, hãy gửi kháng cáo để được xem xét.",
            color = c.textSoft,
            align = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Label("Số lần vi phạm đã ghi nhận: ${me.strikes}", size = 13, color = c.textSoft)
        Spacer(Modifier.height(24.dp))
        if (sent) {
            Banner("Đã gửi kháng cáo. Vui lòng chờ xem xét.", danger = false)
        } else {
            GlassField(
                value = message,
                onValueChange = { message = it.take(500) },
                placeholder = "Giải thích vì sao bạn kháng cáo",
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 6
            )
            Spacer(Modifier.height(14.dp))
            NestButton(
                text = "Gửi kháng cáo",
                onClick = {
                    sending = true
                    scope.launch {
                        val ok = Backend.submitAppeal(message.trim())
                        sending = false
                        if (ok) sent = true else vm.toast("Không gửi được, thử lại sau")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = message.trim().length >= 10,
                loading = sending
            )
        }
        Spacer(Modifier.height(12.dp))
        NestButton("Đăng xuất", { vm.logout() }, Modifier.fillMaxWidth(), primary = false)
        Spacer(Modifier.height(32.dp))
    }
}
