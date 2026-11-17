package com.wongiii.chat.feature

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wongiii.chat.design.WebIcons
import com.wongiii.chat.design.chatColors

@Composable
fun HoldMicButton(
    onStart: () -> Unit,
    onEnd: (cancelled: Boolean) -> Unit,
    onCancelArmed: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = chatColors()
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    Box(
        modifier = modifier
            .size(34.dp)
            .pointerInput(onStart, onEnd, onCancelArmed, threshold) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onStart()
                    var armed = false
                    var active = true
                    while (active) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val dy = change.position.y - down.position.y
                        val nextArmed = dy < -threshold
                        if (nextArmed != armed) {
                            armed = nextArmed
                            onCancelArmed(armed)
                        }
                        if (!change.pressed) {
                            change.consume()
                            onEnd(armed)
                            active = false
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            WebIcons.Mic16,
            contentDescription = "按住说话",
            tint = colors.textPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}


@Composable
fun HoldToTalkArea(
    onStart: () -> Unit,
    onEnd: (cancelled: Boolean) -> Unit,
    onCancelArmed: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = chatColors()
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    Box(
        modifier = modifier.pointerInput(onStart, onEnd, onCancelArmed, threshold) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                onStart()
                var armed = false
                var active = true
                while (active) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val dy = change.position.y - down.position.y
                    val nextArmed = dy < -threshold
                    if (nextArmed != armed) {
                        armed = nextArmed
                        onCancelArmed(armed)
                    }
                    if (!change.pressed) {
                        change.consume()
                        onEnd(armed)
                        active = false
                    }
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            "按住说话",
            color = colors.textPrimary,
            style = com.wongiii.chat.design.chatTypography().body.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            ),
        )
    }
}
