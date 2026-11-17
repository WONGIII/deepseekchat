package com.wongiii.chat.feature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.wongiii.chat.data.model.HapticMode
import com.wongiii.chat.domain.haptics.StreamingHaptics

/**
 * 把一条正在流式输出的消息接到触感引擎上。
 *
 * [chars] 只需要传当前已收到的字符数；字符增长本身就是触感的驱动信号，不需要
 * 在为每个 token 单独调用震动 API。
 */
@Composable
fun StreamingHapticEffect(
    active: Boolean,
    enabled: Boolean,
    mode: HapticMode,
    level: Int,
    chars: Int,
    key: Any,
) {
    val context = LocalContext.current
    val haptics = remember(context) { StreamingHaptics(context) }
    val currentChars = rememberUpdatedState(chars)

    DisposableEffect(haptics) {
        onDispose { haptics.stop() }
    }

    LaunchedEffect(key, active, enabled, mode, level) {
        if (!active) {
            haptics.fadeOut()
            return@LaunchedEffect
        }
        if (!enabled || !haptics.available) {
            haptics.stop()
            return@LaunchedEffect
        }
        haptics.playStreaming(this, mode, level) { currentChars.value }
    }
}
