package com.wongiii.chat.data.remote

import com.wongiii.chat.data.model.TtsEmotion
import com.wongiii.chat.data.model.TtsVoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

data class TtsHandle(
    val response: Response,
    val sampleRate: Int,
) {
    val body get() = response.body
    fun close() = response.close()
}

class SpeechApi(private val factory: HttpClientFactory) {
    suspend fun tts(
        baseUrl: String,
        apiKey: String,
        text: String,
        voice: TtsVoice,
        emotion: TtsEmotion,
    ): TtsHandle = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("text", text)
            put("voice_id", voice.api)
            put("format", "pcm")
            put("filter_markdown", true)
            put("emotion", emotion.api)
        }
        val request = Request.Builder()
            .url(endpoint(baseUrl, "tts"))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(jsonBody(body))
            .build()
        val response = factory.client.newCall(request).execute()
        if (!response.isSuccessful) {
            val textBody = runCatching { response.body?.string() }.getOrNull()
            val code = response.code
            response.close()
            throw HttpError(code, readErrorMessage(textBody, code))
        }
        val rate = response.header("X-TTS-Sample-Rate")?.toIntOrNull()
            ?.takeIf { it in 8000..96000 } ?: 24000
        TtsHandle(response, rate)
    }

    suspend fun asr(baseUrl: String, apiKey: String, wav: ByteArray): String =
        withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "recording.wav",
                    wav.toRequestBody("audio/wav".toMediaType())
                )
                .build()
            val request = Request.Builder()
                .url(endpoint(baseUrl, "asr"))
                .header("Authorization", "Bearer $apiKey")
                .post(body)
                .build()
            val response = factory.client.newCall(request).execute()
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpError(response.code, readErrorMessage(text, response.code))
            val json = JSONObject(text)
            json.optString("text").trim().ifEmpty { throw HttpError(0, "没有识别到语音") }
        }
}
