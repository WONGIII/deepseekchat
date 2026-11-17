package com.wongiii.chat.domain.haptics

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.wongiii.chat.data.model.HapticMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * 流式输出的触感引擎。
 *
 * rikkahub 的做法是每个消息片段触发一次系统 HapticFeedback，力度固定、间隔明显。
 * 这里改为直接驱动 Vibrator，把字符到达的密度转成连续能量，再用 10ms 级、低幅、
 * 带缓慢随机漂移的振幅包络拼成流动的触感：文字持续输出时像细流，暂停时自然收敛。
 */
object HapticLevels {
    const val MIN = 1
    const val MAX = 5

    fun label(level: Int): String = when (level.coerceIn(MIN, MAX)) {
        1 -> "极轻"
        2 -> "轻柔"
        3 -> "适中"
        4 -> "明显"
        else -> "强劲"
    }

    fun factor(level: Int): Float = when (level.coerceIn(MIN, MAX)) {
        1 -> 0.28f
        2 -> 0.42f
        3 -> 0.58f
        4 -> 0.78f
        else -> 1f
    }
}

private data class Step(val millis: Long, val amplitude: Float)

class StreamingHaptics(context: Context) {
    private val vibrator: Vibrator? = resolveVibrator(context.applicationContext)
    private val amplitudeControl: Boolean = vibrator?.hasAmplitudeControl() == true
    private var job: Job? = null
    private var lastPeak = 0f
    private var lastEmitAt = 0L

    val available: Boolean
        get() = vibrator != null && vibrator.hasVibrator()

    val fineControl: Boolean
        get() = amplitudeControl

    /**
     * 开始跟随流式字符的触感。
     *
     * [chars] 返回当前已显示字符总数；引擎自己计算增长速度和停顿，不需要每次
     * token 更新都调用一次震动 API。
     */
    fun playStreaming(
        scope: CoroutineScope,
        mode: HapticMode,
        level: Int,
        chars: () -> Int,
    ) {
        // 只替换自己的流式任务，不取消正在播放的发送确认/上一段波形，
        // 否则新消息刚发出时的那一下会被立刻掐断。
        job?.cancel()
        job = null
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        val maxAmplitude = 210f * HapticLevels.factor(level)
        val random = Random(System.nanoTime())
        job = scope.launch {
            val startedAt = SystemClock.uptimeMillis()
            var previousChars = chars()
            var presence = 0f
            var nextFlow = startedAt
            var nextRipple = startedAt
            var nextHeartbeat = startedAt
            var nextTap = startedAt
            var nextDrop = startedAt
            var flowPhase = random.nextFloat() * (2f * PI.toFloat())
            var breathPhase = -PI.toFloat() / 2f
            var flowDrift = 0f

            while (isActive) {
                delay(10)
                val now = SystemClock.uptimeMillis()

                val total = chars()
                val delta = (total - previousChars).coerceAtLeast(0)
                previousChars = total

                // 文字到达时快速响应，停顿后缓慢收敛，避免突然起振或硬停。
                val target = if (delta > 0) 1f else 0f
                presence = if (target > presence) {
                    presence + (1f - presence) * 0.36f
                } else {
                    presence * 0.86f
                }
                if (presence < 0.015f) presence = 0f

                when (mode) {
                    HapticMode.FLOW -> {
                        if (presence > 0.025f && now >= nextFlow) {
                            // 一次生成约 126ms 的连续振动，每 107-115ms 重铺一段并
                            // 与上一段重叠。振幅由两个缓慢正弦叠加随机漂移得到，
                            // 一直在游走，不会形成固定节拍。
                            flowDrift = (flowDrift * 0.78f +
                                (random.nextFloat() - 0.5f) * 0.12f).coerceIn(-0.07f, 0.07f)
                            val peak = maxAmplitude * presence
                            val steps = ArrayList<Step>(9)
                            var phase = flowPhase
                            repeat(9) {
                                val shimmer = 0.52f +
                                    0.20f * (0.5f + 0.5f * sin(phase)) +
                                    0.12f * sin(phase * 3.1f + 0.8f) +
                                    0.08f * sin(phase * 7.3f + 2.1f)
                                val jitter = (random.nextFloat() - 0.5f) * 0.04f
                                val amplitude = peak * (shimmer + flowDrift + jitter)
                                    .coerceIn(0.32f, 1f)
                                steps += Step(14, amplitude)
                                phase += 0.119f
                            }
                            flowPhase = phase
                            waveform(steps)
                            nextFlow = now + 107L + random.nextInt(0, 9)
                        }
                    }

                    HapticMode.RIPPLE -> {
                        if (presence > 0.04f && now >= nextRipple) {
                            val peak = maxAmplitude * presence
                            // 一个柔软的水波：快速涨起、缓慢退去，然后留一小段空白。
                            waveform(
                                listOf(
                                    Step(15, peak * 0.34f),
                                    Step(19, peak * 0.72f),
                                    Step(21, peak * 0.52f),
                                    Step(26, peak * 0.20f),
                                    Step(38, 0f),
                                )
                            )
                            nextRipple = now + 150L + random.nextInt(0, 55)
                        }
                    }

                    HapticMode.BREATH -> {
                        if (presence > 0.03f && now >= nextFlow) {
                            // 128ms 的连续振幅包络，缓慢起伏；相邻段重叠。
                            val peak = maxAmplitude * presence
                            val steps = ArrayList<Step>(8)
                            repeat(8) {
                                val swell = 0.18f + 0.82f * (0.5f + 0.5f * sin(breathPhase))
                                steps += Step(16, peak * swell)
                                breathPhase += 0.0346f
                            }
                            waveform(steps)
                            nextFlow = now + 112L + random.nextInt(0, 7)
                        }
                    }

                    HapticMode.HEARTBEAT -> {
                        if (presence > 0.04f && now >= nextHeartbeat) {
                            val peak = maxAmplitude * presence
                            waveform(
                                listOf(
                                    Step(46, peak * 0.92f),
                                    Step(70, 0f),
                                    Step(36, peak * 0.58f),
                                )
                            )
                            nextHeartbeat = now + 930L + random.nextInt(0, 110)
                        }
                    }

                    HapticMode.TYPEWRITER -> {
                        if (delta > 0 && presence > 0.04f && now >= nextTap) {
                            val accent = (delta / 6f).coerceIn(0.35f, 1f)
                            val amplitude = maxAmplitude * presence *
                                (0.44f + 0.42f * accent) * (0.9f + random.nextFloat() * 0.16f)
                            pulse(
                                durationMs = 8L + (delta.coerceAtMost(8) * 2L),
                                amplitude = amplitude,
                            )
                            // 比 rikkahub 的 50ms 更松弛，字与字之间留一点呼吸。
                            nextTap = now + 54L + random.nextInt(0, 46)
                        }
                    }

                    HapticMode.RAIN -> {
                        if (presence > 0.04f && now >= nextDrop) {
                            val amplitude = maxAmplitude * presence *
                                (0.32f + random.nextFloat() * 0.62f)
                            pulse(
                                durationMs = if (amplitudeControl) {
                                    14L + random.nextInt(0, 18)
                                } else {
                                    8L + random.nextInt(0, 8)
                                },
                                amplitude = amplitude,
                            )
                            nextDrop = now + 62L + random.nextInt(0, 132)
                        }
                    }
                }
            }
            v.cancel()
        }
    }

    /** 用户消息真正发出时的一下确认触感，力度跟随当前挡位。 */
    fun confirm(level: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val peak = 210f * HapticLevels.factor(level) * 0.82f
        // 一个很短的“落点”：轻起、收住，不拖尾，避免和流式触感混在一起。
        waveform(
            listOf(
                Step(9, peak * 0.48f),
                Step(11, peak * 0.9f),
                Step(17, peak * 0.52f),
            )
        )
    }

    /** 设置页试听：模拟约 2.2 秒的文字流入，然后自然收尾。 */
    fun preview(scope: CoroutineScope, mode: HapticMode, level: Int) {
        val startedAt = SystemClock.uptimeMillis()
        playStreaming(scope, mode, level) {
            val elapsed = (SystemClock.uptimeMillis() - startedAt).coerceAtMost(2200L)
            (elapsed / 42L).toInt()
        }
        val running = job
        scope.launch {
            delay(2750)
            if (job === running) fadeOut()
        }
    }

    /** 流式结束时让最后一小段触感柔和收尾，而不是硬切。 */
    fun fadeOut() {
        job?.cancel()
        job = null
        val v = vibrator ?: return
        val peak = lastPeak
        val now = SystemClock.uptimeMillis()
        if (amplitudeControl && peak > 3f && now - lastEmitAt < 280) {
            val steps = ArrayList<Step>(7)
            repeat(7) { index ->
                val remaining = 1f - index / 7f
                steps += Step(11, peak * remaining * remaining)
            }
            waveform(steps, recordPeak = false)
        } else {
            v.cancel()
        }
        lastPeak = 0f
    }

    fun stop() {
        job?.cancel()
        job = null
        lastPeak = 0f
        runCatching { vibrator?.cancel() }
    }

    private fun pulse(durationMs: Long, amplitude: Float) {
        val v = vibrator ?: return
        if (amplitude <= 1.5f) return
        if (amplitudeControl) {
            val amp = amplitude.roundToInt().coerceIn(1, 255)
            v.vibrate(VibrationEffect.createOneShot(durationMs.coerceAtLeast(1L), amp))
            lastPeak = amplitude
            lastEmitAt = SystemClock.uptimeMillis()
        } else if (amplitude >= 34f) {
            // 没有振幅控制的转子马达只能靠通断占空比表达强弱。
            v.vibrate(
                VibrationEffect.createOneShot(
                    durationMs.coerceAtLeast(1L),
                    VibrationEffect.DEFAULT_AMPLITUDE,
                )
            )
            lastPeak = amplitude
            lastEmitAt = SystemClock.uptimeMillis()
        }
    }

    private fun waveform(steps: List<Step>, recordPeak: Boolean = true) {
        val v = vibrator ?: return
        val peak = steps.maxOf { it.amplitude }
        if (peak <= 1.5f) return
        if (amplitudeControl) {
            val timings = steps.map { it.millis.coerceAtLeast(1L) }.toLongArray()
            val amplitudes = steps.map { it.amplitude.roundToInt().coerceIn(0, 255) }.toIntArray()
            v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            if (peak < 34f) return
            val timings = steps.map { it.millis.coerceAtLeast(1L) }.toLongArray()
            val amplitudes = steps.map {
                if (it.amplitude >= 34f) VibrationEffect.DEFAULT_AMPLITUDE else 0
            }.toIntArray()
            v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
        if (recordPeak) {
            lastPeak = peak
            lastEmitAt = SystemClock.uptimeMillis()
        }
    }

    private companion object {
        fun resolveVibrator(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                    ?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
    }
}
