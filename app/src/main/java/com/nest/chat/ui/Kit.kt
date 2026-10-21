package com.nest.chat.ui

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nest.chat.data.ImageUtil
import com.nest.chat.data.UserCache
import com.nest.chat.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ImageCache {
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, value: ImageBitmap) {
        cache.put(key, value)
    }
}

object Fmt {
    private val hm = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dm = SimpleDateFormat("dd/MM", Locale.getDefault())
    private val fullFmt = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())

    fun clock(ms: Long): String = hm.format(Date(ms))

    fun full(ms: Long): String = fullFmt.format(Date(ms))

    private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun dayKey(ms: Long): Long = (ms + TimeZone.getDefault().getOffset(ms)) / 86_400_000L

    fun dayLabel(ms: Long): String {
        val gap = dayKey(System.currentTimeMillis()) - dayKey(ms)
        return when (gap) {
            0L -> "Hôm nay"
            1L -> "Hôm qua"
            else -> dateFmt.format(Date(ms))
        }
    }

    fun chatTime(ms: Long): String {
        if (ms <= 0L) return ""
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = ms }
        val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) hm.format(Date(ms)) else dm.format(Date(ms))
    }
}

@Composable
fun rememberB64Image(b64: String, maxSide: Int): State<ImageBitmap?> {
    val key = "${b64.length}_${b64.hashCode()}_$maxSide"
    return produceState<ImageBitmap?>(initialValue = ImageCache.get(key), key1 = key) {
        if (value == null && b64.isNotEmpty()) {
            val decoded = withContext(Dispatchers.Default) { ImageUtil.decodeImage(b64, maxSide) }
            if (decoded != null) {
                ImageCache.put(key, decoded)
                value = decoded
            }
        }
    }
}

@Composable
fun rememberUser(uid: String): State<UserProfile?> =
    produceState<UserProfile?>(initialValue = UserCache.peek(uid), key1 = uid) {
        if (uid.isNotEmpty()) {
            val fresh = UserCache.get(uid)
            if (fresh != null) value = fresh
        }
    }

@Composable
fun rememberNow(until: Long): State<Long> =
    produceState(initialValue = System.currentTimeMillis(), key1 = until) {
        while (value < until) {
            delay(minOf(until - value, 30_000L) + 300L)
            value = System.currentTimeMillis()
        }
    }

@Composable
fun NestBackground(content: @Composable BoxScope.() -> Unit) {
    val c = Nest.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.bgTop, c.bgBottom)))
            .drawBehind {
                val radius = size.width * 0.85f
                val centerA = Offset(size.width * 0.12f, size.height * 0.10f)
                val centerB = Offset(size.width * 0.95f, size.height * 0.85f)
                drawCircle(
                    brush = Brush.radialGradient(listOf(c.blobA, Color.Transparent), center = centerA, radius = radius),
                    radius = radius,
                    center = centerA
                )
                drawCircle(
                    brush = Brush.radialGradient(listOf(c.blobB, Color.Transparent), center = centerB, radius = radius),
                    radius = radius,
                    center = centerB
                )
            },
        content = content
    )
}

@Composable
fun Glass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    strong: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val c = Nest.colors
    Box(
        modifier
            .clip(shape)
            .background(if (strong) c.glassStrong else c.glass)
            .border(1.dp, Brush.linearGradient(listOf(c.strokeHi, c.strokeLo)), shape),
        content = content
    )
}

@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 15,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Nest.colors.text,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = (size * 1.32f).sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align
    )
}

@Composable
fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    maxLines: Int = if (singleLine) 1 else 6,
    leading: @Composable (() -> Unit)? = null
) {
    val c = Nest.colors
    Glass(modifier, RoundedCornerShape(16.dp)) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(10.dp))
            }
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Label(placeholder, color = c.textSoft, size = 16)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = singleLine,
                    maxLines = maxLines,
                    textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                    cursorBrush = SolidColor(c.text),
                    visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() })
                )
            }
        }
    }
}

@Composable
fun NestButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    loading: Boolean = false,
    danger: Boolean = false
) {
    val c = Nest.colors
    val shape = RoundedCornerShape(16.dp)
    val bg = when {
        !primary -> c.glassStrong
        danger -> c.danger
        else -> c.accent
    }
    val fg = when {
        !primary -> c.text
        danger -> Color.White
        else -> c.onAccent
    }
    Box(
        modifier
            .heightIn(min = 50.dp)
            .clip(shape)
            .background(bg.copy(alpha = if (enabled) 1f else 0.4f))
            .then(if (!primary) Modifier.border(1.dp, c.strokeLo, shape) else Modifier)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = fg, strokeWidth = 2.dp)
        } else {
            Label(text, weight = FontWeight.SemiBold, color = fg, align = TextAlign.Center)
        }
    }
}

@Composable
fun IconBtn(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Nest.colors.text,
    size: Dp = 40.dp
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
    }
}

@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconBtn(Icons.AutoMirrored.Filled.ArrowBack, onBack)
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Label(
            title,
            Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            size = 22,
            weight = FontWeight.Bold,
            maxLines = 1
        )
        actions()
    }
}

@Composable
fun Avatar(profile: UserProfile?, fallbackName: String, size: Dp, modifier: Modifier = Modifier) {
    val c = Nest.colors
    val image by rememberB64Image(profile?.avatar.orEmpty(), 192)
    val shown = profile?.name?.takeIf { it.isNotBlank() } ?: fallbackName
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(c.glassStrong)
            .border(1.dp, c.strokeLo, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Label(
                shown.trim().firstOrNull()?.uppercase() ?: "?",
                size = (size.value * 0.4f).toInt().coerceAtLeast(12),
                weight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun GroupAvatar(size: Dp, avatar: String = "", modifier: Modifier = Modifier) {
    val c = Nest.colors
    val image by rememberB64Image(avatar, 192)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(c.glassStrong)
            .border(1.dp, c.strokeLo, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.Group, null, Modifier.size(size * 0.5f), tint = c.text)
        }
    }
}

@Composable
fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    onConfirm: () -> Unit = {},
    dismissText: String = "Đóng",
    danger: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = Nest.colors
    val shape = RoundedCornerShape(26.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (c.dark) Color(0xFF1A1C23) else Color.White)
                .border(1.dp, c.strokeLo, shape)
                .padding(20.dp)
        ) {
            Label(title, size = 18, weight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NestButton(dismissText, onDismiss, Modifier.weight(1f), primary = false)
                if (confirmText != null) {
                    NestButton(confirmText, onConfirm, Modifier.weight(1f), danger = danger)
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Label(
        text,
        Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp),
        size = 13,
        weight = FontWeight.SemiBold,
        color = Nest.colors.textSoft
    )
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    danger: Boolean = false,
    chevron: Boolean = true
) {
    val c = Nest.colors
    val tint = if (danger) c.danger else c.text
    Glass(modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(), RoundedCornerShape(18.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = tint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Label(title, color = tint, weight = FontWeight.Medium)
                if (subtitle != null) Label(subtitle, size = 12, color = c.textSoft)
            }
            if (chevron) Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = c.textSoft)
        }
    }
}

@Composable
fun SettingSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val c = Nest.colors
    Glass(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(), RoundedCornerShape(18.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = c.text)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Label(title, weight = FontWeight.Medium)
                if (subtitle != null) Label(subtitle, size = 12, color = c.textSoft)
            }
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.onAccent,
                    checkedTrackColor = c.accent,
                    uncheckedThumbColor = c.textSoft,
                    uncheckedTrackColor = c.glassStrong,
                    uncheckedBorderColor = c.strokeLo
                )
            )
        }
    }
}

@Composable
fun Banner(text: String, modifier: Modifier = Modifier, danger: Boolean = true) {
    val c = Nest.colors
    val tone = if (danger) c.danger else c.badge
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tone.copy(alpha = 0.14f))
            .border(1.dp, tone.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Label(text, size = 13, color = tone)
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Nest.colors.text, strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
    }
}

private val markRegex = Regex("\\p{Mn}+")

fun foldVi(text: String): String =
    markRegex.replace(Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD), "").replace('đ', 'd')

fun presenceText(profile: UserProfile?, meHidden: Boolean, now: Long): String {
    if (profile == null) return ""
    if (meHidden || profile.hideOnline || profile.lastSeen <= 0L) return "@${profile.handle}"
    val diff = now - profile.lastSeen
    return when {
        diff < 4 * 60_000L -> "Đang hoạt động"
        diff < 60 * 60_000L -> "Hoạt động ${diff / 60_000L} phút trước"
        diff < 24 * 3_600_000L -> "Hoạt động ${diff / 3_600_000L} giờ trước"
        else -> "@${profile.handle}"
    }
}

@Composable
fun rememberMinuteTicker(): State<Long> = produceState(initialValue = System.currentTimeMillis()) {
    while (true) {
        delay(60_000L)
        value = System.currentTimeMillis()
    }
}

@Composable
fun ActionRow(text: String, onClick: () -> Unit, danger: Boolean = false) {
    val c = Nest.colors
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp)
    ) {
        Label(text, color = if (danger) c.danger else c.text, weight = FontWeight.Medium)
    }
}

@Composable
fun ImageViewer(b64: String, onClose: () -> Unit) {
    val image by rememberB64Image(b64, 1600)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = image
            if (bitmap != null) {
                Image(bitmap, null, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
            } else {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
        }
    }
}
