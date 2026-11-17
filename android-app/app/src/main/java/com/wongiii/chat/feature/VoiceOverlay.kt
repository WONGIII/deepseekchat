package com.wongiii.chat.feature

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wongiii.chat.design.chatColors
import com.wongiii.chat.design.chatTypography

/** 按住说话的全屏录音层：底部渐变、松手发送、上滑取消。 */
@Composable
fun VoiceHoldOverlay(
    phase: VoicePhase,
    seconds: Int,
    levels: List<Float>,
    cancelArmed: Boolean,
) {
    val cancel by animateFloatAsState(
        targetValue = if (cancelArmed) 1f else 0f,
        animationSpec = tween(180),
        label = "voiceCancel",
    )
    val blueMid = Color(0xFF74AEFF)
    val blueBottom = Color(0xFF2F7BF7)
    val redMid = Color(0xFFF6B9B9)
    val redBottom = Color(0xFFE96565)
    val pageBase = chatColors().page
    val brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to Color.Transparent,
            0.42f to pageBase.copy(alpha = 0.96f),
            0.66f to lerp(blueMid, redMid, cancel).copy(alpha = 0.72f),
            1.0f to lerp(blueBottom, redBottom, cancel),
        ),
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(brush),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 96.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (phase == VoicePhase.TRANSCRIBING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "正在识别…",
                    color = Color.White,
                    style = chatTypography().label.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                )
            } else {
                Text(
                    text = if (cancelArmed) "松手取消" else "松手发送，上滑取消",
                    color = lerp(Color.White, Color(0xFFE64C4C), cancel),
                    style = chatTypography().label.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                VoiceWaveform(levels, cancel, Modifier.fillMaxWidth().height(24.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "%02d:%02d".format(seconds / 60, seconds % 60),
                    color = Color.White.copy(alpha = 0.75f),
                    style = chatTypography().caption.copy(fontSize = 12.sp),
                )
            }
        }
    }
}

@Composable
private fun VoiceWaveform(
    levels: List<Float>,
    cancel: Float,
    modifier: Modifier = Modifier,
) {
    val color = lerp(Color.White, Color(0xFFB32323), cancel)
    Canvas(modifier) {
        val count = 64
        val gap = 2.5.dp.toPx()
        val barWidth = (size.width - gap * (count - 1)) / count
        val centerY = size.height / 2
        for (index in 0 until count) {
            val level = levels.getOrNull(levels.size - count + index) ?: 0.05f
            val height = (3.dp.toPx() + level * (size.height - 5.dp.toPx()))
                .coerceAtLeast(3.dp.toPx())
            drawRoundRect(
                color = color.copy(alpha = 0.92f),
                topLeft = Offset(index * (barWidth + gap), centerY - height / 2),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}
