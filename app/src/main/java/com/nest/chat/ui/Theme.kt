package com.nest.chat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
class NestColors(
    val dark: Boolean,
    val bgTop: Color,
    val bgBottom: Color,
    val blobA: Color,
    val blobB: Color,
    val glass: Color,
    val glassStrong: Color,
    val strokeHi: Color,
    val strokeLo: Color,
    val text: Color,
    val textSoft: Color,
    val accent: Color,
    val onAccent: Color,
    val mine: Color,
    val onMine: Color,
    val danger: Color,
    val badge: Color
)

private val LightColors = NestColors(
    dark = false,
    bgTop = Color(0xFFF8F9FD),
    bgBottom = Color(0xFFE8ECF6),
    blobA = Color(0x408AA4FF),
    blobB = Color(0x40FFB5D8),
    glass = Color(0xB3FFFFFF),
    glassStrong = Color(0xE6FFFFFF),
    strokeHi = Color(0xFFFFFFFF),
    strokeLo = Color(0x1A000000),
    text = Color(0xFF0E0F13),
    textSoft = Color(0x990E0F13),
    accent = Color(0xFF15161A),
    onAccent = Color(0xFFFFFFFF),
    mine = Color(0xFF15161A),
    onMine = Color(0xFFFFFFFF),
    danger = Color(0xFFE5484D),
    badge = Color(0xFF3B82F6)
)

private val DarkColors = NestColors(
    dark = true,
    bgTop = Color(0xFF0A0B0F),
    bgBottom = Color(0xFF151720),
    blobA = Color(0x404F6BFF),
    blobB = Color(0x40B455FF),
    glass = Color(0x14FFFFFF),
    glassStrong = Color(0x26FFFFFF),
    strokeHi = Color(0x47FFFFFF),
    strokeLo = Color(0x14FFFFFF),
    text = Color(0xFFF5F6F8),
    textSoft = Color(0x99F5F6F8),
    accent = Color(0xFFF5F6F8),
    onAccent = Color(0xFF0A0B0F),
    mine = Color(0xFFF2F3F5),
    onMine = Color(0xFF0A0B0F),
    danger = Color(0xFFFF6369),
    badge = Color(0xFF5B9BFF)
)

private val LocalNest = staticCompositionLocalOf { LightColors }

object Nest {
    val colors: NestColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNest.current
}

@Composable
fun NestTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalNest provides colors) {
        MaterialTheme(
            colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
            content = content
        )
    }
}
