package com.wongiii.chat.domain.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import com.wongiii.chat.data.local.AppSettings
import com.wongiii.chat.data.remote.Endpoints
import com.wongiii.chat.data.remote.SpeechApi
import com.wongiii.chat.data.remote.TtsHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.max

class ReadAloudPlayer(
    private val context: Context,
    private val speechApi: SpeechApi,
    private val scope: CoroutineScope,
    private val onError: (Throwable) -> Unit,
    private val onState: (String?, String?) -> Unit,
) {
    private var job: Job? = null
    private var track: AudioTrack? = null
    private var activeHandle: TtsHandle? = null
    private var focus: AudioFocusRequest? = null
    private var currentId: String? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val readingId: String? get() = currentId

    fun toggle(messageId: String, text: String, settings: AppSettings) {
        if (currentId == messageId) {
            stop()
            return
        }
        stop()
        currentId = messageId
        onState(messageId, "loading")
        job = scope.launch {
            try {
                requestFocus()
                val handle = speechApi.tts(
                    Endpoints.speech(settings.network),
                    Endpoints.speechKey(settings.network),
                    text,
                    settings.ttsVoice,
                    settings.ttsEmotion,
                )
                activeHandle = handle
                if (!isActive) {
                    activeHandle = null
                    handle.close()
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    try {
                        val body = handle.body ?: error("语音服务没有返回音频")
                        val sampleRate = handle.sampleRate
                        Log.d("ReadAloud", "tts response rate=$sampleRate")
                        val minBuffer = AudioTrack.getMinBufferSize(
                            sampleRate,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                        )
                        val bufferSize = max(minBuffer, sampleRate * 2 / 4)
                        val audio = AudioTrack.Builder()
                            .setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                            .setAudioFormat(
                                AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(sampleRate)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                    .build()
                            )
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .setBufferSizeInBytes(bufferSize)
                            .build()
                        track = audio
                        audio.play()
                        val writtenFrames = body.byteStream().use { input ->
                            streamPcm(input, audio, messageId)
                        }
                        val deadline = SystemClock.elapsedRealtime() + 3000
                        while (
                            job?.isActive == true &&
                            audio.playbackHeadPosition < writtenFrames &&
                            SystemClock.elapsedRealtime() < deadline
                        ) {
                            delay(20)
                        }
                        Log.d("ReadAloud", "stream complete, frames=$writtenFrames")
                        audio.stop()
                        audio.release()
                        track = null
                    } finally {
                        if (activeHandle === handle) activeHandle = null
                        handle.close()
                    }
                }
            } catch (error: Throwable) {
                if (error !is CancellationException && coroutineContext[Job]?.isActive == true) {
                    Log.e("ReadAloud", "tts failed", error)
                    onError(error)
                }
            } finally {
                releaseFocus()
                if (currentId == messageId) {
                    currentId = null
                    onState(null, null)
                }
            }
        }
    }

    private suspend fun streamPcm(input: InputStream, audio: AudioTrack, messageId: String): Int {
        val bytes = ByteArray(8192)
        var carry = 0
        var carryByte: Byte? = null
        var written = 0
        var playingReported = false
        while (job?.isActive == true) {
            val read = input.read(bytes)
            if (read <= 0) break
            val available = read + carry
            val samples = ShortArray(available / 2)
            var index = 0
            var position = 0
            if (carry == 1 && carryByte != null) {
                val low = carryByte!!.toInt() and 0xff
                val high = bytes[0].toInt() and 0xff
                samples[index++] = (low or (high shl 8)).toShort()
                position = 1
            }
            while (position + 1 < read) {
                val low = bytes[position].toInt() and 0xff
                val high = bytes[position + 1].toInt() and 0xff
                samples[index++] = (low or (high shl 8)).toShort()
                position += 2
            }
            carry = if ((read - position) == 1) {
                carryByte = bytes[position]
                1
            } else {
                0
            }
            if (index > 0) {
                val result = audio.write(samples, 0, index, AudioTrack.WRITE_BLOCKING)
                if (result > 0) {
                    written += result
                    if (!playingReported) {
                        playingReported = true
                        Log.d("ReadAloud", "first audio chunk written")
                        onState(messageId, "playing")
                    }
                }
            }
        }
        return written
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { activeHandle?.close() }
        activeHandle = null
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        runCatching { track?.release() }
        track = null
        currentId = null
        releaseFocus()
        onState(null, null)
    }

    private fun requestFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
        focus = request
        audioManager.requestAudioFocus(request)
    }

    private fun releaseFocus() {
        focus?.let { audioManager.abandonAudioFocusRequest(it) }
        focus = null
    }
}
