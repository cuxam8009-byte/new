package com.nest.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

class BubbleItem(val icon: ImageVector, val label: String, val badge: Boolean, val onClick: () -> Unit)

@Composable
fun NavBubble(
    items: List<BubbleItem>,
    showBadge: Boolean,
    initialX: Float,
    initialY: Float,
    onMoved: (Float, Float) -> Unit,
    resetKey: Int,
    modifier: Modifier = Modifier
) {
    val c = Nest.colors
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        val bubble = with(density) { 56.dp.toPx() }
        val margin = with(density) { 8.dp.toPx() }
        val panelW = with(density) { 224.dp.toPx() }
        val panelH = with(density) { (items.size * 52 + 16).dp.toPx() }
        val rangeX = (maxW - bubble).coerceAtLeast(1f)
        val rangeY = (maxH - bubble).coerceAtLeast(1f)

        var pos by remember(maxW, resetKey) {
            mutableStateOf(Offset(initialX.coerceIn(0f, 1f) * rangeX, initialY.coerceIn(0f, 1f) * rangeY))
        }
        var open by remember { mutableStateOf(false) }

        if (open) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { open = false } }
            )
        }

        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(120)) + scaleIn(tween(140), initialScale = 0.92f),
            exit = fadeOut(tween(90)),
            modifier = Modifier.offset {
                val onLeft = pos.x + bubble / 2f < maxW / 2f
                val x = if (onLeft) pos.x else pos.x + bubble - panelW
                val below = pos.y + bubble + margin + panelH <= maxH
                val y = if (below) pos.y + bubble + margin else pos.y - panelH - margin
                IntOffset(
                    x.coerceIn(margin, (maxW - panelW - margin).coerceAtLeast(margin)).roundToInt(),
                    y.coerceIn(margin, (maxH - panelH - margin).coerceAtLeast(margin)).roundToInt()
                )
            }
        ) {
            Glass(Modifier.width(224.dp), RoundedCornerShape(24.dp), strong = true) {
                Column(Modifier.padding(8.dp)) {
                    for (item in items) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    open = false
                                    item.onClick()
                                }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(item.icon, null, Modifier.size(22.dp), tint = c.text)
                            Spacer(Modifier.width(14.dp))
                            Label(item.label, Modifier.weight(1f), weight = FontWeight.Medium, maxLines = 1)
                            if (item.badge) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(c.badge)
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .offset { IntOffset(pos.x.roundToInt(), pos.y.coerceAtMost(maxH - bubble).roundToInt()) }
                .size(56.dp)
                .clip(CircleShape)
                .background(c.glassStrong)
                .border(1.dp, c.strokeHi.copy(alpha = if (c.dark) 0.3f else 0.9f), CircleShape)
                .pointerInput(maxW, maxH) {
                    detectDragGestures(
                        onDragStart = { open = false },
                        onDragEnd = { onMoved(pos.x / rangeX, pos.y / rangeY) }
                    ) { change, drag ->
                        change.consume()
                        pos = Offset(
                            (pos.x + drag.x).coerceIn(0f, maxW - bubble),
                            (pos.y + drag.y).coerceIn(0f, maxH - bubble)
                        )
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onTap = { open = !open }) },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Apps, null, Modifier.size(26.dp), tint = c.text)
            if (showBadge && !open) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(c.badge)
                )
            }
        }
    }
}
