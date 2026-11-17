package com.wongiii.chat.domain.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

class VoiceRecorder(private val context: Context) {
    private var record: AudioRecord? = null
    private var pcm = ByteArrayOutputStream()
    private var totalSamples = 0
    var active = false
        private set
    var startedAt = 0L
        private set
    private val maxSamples = SAMPLE_RATE * MAX_SECONDS

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun start(): Boolean {
        if (!hasPermission()) return false
        if (active) return true
        val min = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(BUFFER_SAMPLES * 2)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            min,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }
        pcm = ByteArrayOutputStream()
        totalSamples = 0
        recorder.startRecording()
        record = recorder
        active = true
        startedAt = System.currentTimeMillis()
        return true
    }

    suspend fun read(): ShortArray? = withContext(Dispatchers.IO) {
        val recorder = record ?: return@withContext null
        val buffer = ShortArray(BUFFER_SAMPLES)
        val count = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
        if (count <= 0) return@withContext null
        if (totalSamples + count > maxSamples) {
            val allowed = (maxSamples - totalSamples).coerceAtLeast(0)
            if (allowed > 0) {
                append(buffer, allowed)
            }
            return@withContext buffer.copyOf(allowed)
        }
        append(buffer, count)
        buffer.copyOf(count)
    }

    fun amplitude(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        samples.forEach { value ->
            val normalized = value / 32768.0
            sum += normalized * normalized
        }
        return (sqrt(sum / samples.size) * 8.0).coerceIn(0.0, 1.0).toFloat()
    }

    fun limitReached(): Boolean = totalSamples >= maxSamples

    fun seconds(): Int = ((System.currentTimeMillis() - startedAt) / 1000).toInt()

    suspend fun stop(): ByteArray? = withContext(Dispatchers.IO) {
        val recorder = record
        active = false
        runCatching { recorder?.stop() }
        recorder?.release()
        record = null
        if (totalSamples <= 0) null else encodeWav()
    }

    suspend fun cancel() = withContext(Dispatchers.IO) {
        active = false
        runCatching { record?.stop() }
        record?.release()
        record = null
        pcm = ByteArrayOutputStream()
        totalSamples = 0
    }

    private fun append(buffer: ShortArray, count: Int) {
        for (i in 0 until count) {
            val value = buffer[i].toInt()
            pcm.write(value and 0xff)
            pcm.write((value shr 8) and 0xff)
        }
        totalSamples += count
    }

    private fun encodeWav(): ByteArray {
        val data = pcm.toByteArray()
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        writeInt(header, 4, 36 + data.size)
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        writeInt(header, 16, 16)
        header[20] = 1
        header[22] = 1
        writeInt(header, 24, SAMPLE_RATE)
        writeInt(header, 28, SAMPLE_RATE * 2)
        header[32] = 2
        header[34] = 16
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        writeInt(header, 40, data.size)
        return header + data
    }

    private fun writeInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xff).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xff).toByte()
        bytes[offset + 3] = ((value shr 24) and 0xff).toByte()
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val MAX_SECONDS = 120
        private const val BUFFER_SAMPLES = 1024
    }
}
