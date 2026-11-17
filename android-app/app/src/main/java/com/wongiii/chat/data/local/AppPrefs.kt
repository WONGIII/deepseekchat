package com.wongiii.chat.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.wongiii.chat.data.model.AccentColor
import com.wongiii.chat.data.model.AppJson
import com.wongiii.chat.data.model.BackgroundStyle
import com.wongiii.chat.data.model.BubbleStyle
import com.wongiii.chat.data.model.FontSize
import com.wongiii.chat.data.model.HapticMode
import com.wongiii.chat.data.model.NetworkSettings
import com.wongiii.chat.data.model.ThemeMode
import com.wongiii.chat.data.model.TtsEmotion
import com.wongiii.chat.data.model.TtsVoice
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AppSettings(
    val serverUrl: String? = null,
    val username: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.BLUE,
    val bubbleStyle: BubbleStyle = BubbleStyle.TINTED,
    val backgroundStyle: BackgroundStyle = BackgroundStyle.PLAIN,
    val backgroundImagePath: String? = null,
    val fontSize: FontSize = FontSize.NORMAL,
    val locale: String = "zh-CN",
    val thinking: Boolean = true,
    val search: Boolean = true,
    val ttsVoice: TtsVoice = TtsVoice.STELLA,
    val ttsEmotion: TtsEmotion = TtsEmotion.AUTO,
    val model: String = "deepseek-flash",
    val models: List<String> = listOf("deepseek-flash", "deepseek-v4-pro"),
    val readAloudMuted: Boolean = true,
    val autoCollapseThinking: Boolean = true,
    val hapticEnabled: Boolean = true,
    val hapticMode: HapticMode = HapticMode.FLOW,
    val hapticLevel: Int = 2,
    val asrLanguage: String = "auto",
    val location: String? = null,
    val network: NetworkSettings = NetworkSettings(),
) {
    val cloudConfigured: Boolean get() = !serverUrl.isNullOrBlank() && !username.isNullOrBlank()
}

class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("chat_secure", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun put(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val body = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(iv + body, Base64.NO_WRAP)
        prefs.edit().putString(key, payload).apply()
    }

    fun get(key: String): String? {
        val payload = prefs.getString(key, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(payload, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, 12)
            val body = bytes.copyOfRange(12, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrNull()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val ALIAS = "chat_secure_v1"
    }
}

class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
    private val secure = SecureStore(context)

    fun load(): AppSettings {
        val network = runCatching {
            secure.get("network")?.let { AppJson.decodeFromString(NetworkSettings.serializer(), it) }
        }.getOrNull() ?: NetworkSettings()
        val models: List<String> = runCatching {
            prefs.getString("models", null)?.let {
                AppJson.decodeFromString(ListSerializer(String.serializer()), it)
            }
        }.getOrNull()?.filter { it.isNotBlank() }?.distinct().orEmpty()
        val model = prefs.getString("model", null)?.trim().takeUnless { it.isNullOrBlank() }
            ?: models.firstOrNull() ?: "deepseek-flash"
        val finalModels = (if (models.isEmpty()) listOf(model, "deepseek-flash", "deepseek-v4-pro") else models)
            .toMutableList()
        if (model !in finalModels) finalModels.add(0, model)
        return AppSettings(
            serverUrl = prefs.getString("server_url", null)?.takeIf { it.isNotBlank() },
            username = prefs.getString("username", null)?.takeIf { it.isNotBlank() },
            themeMode = enumOr(prefs.getString("theme", null), ThemeMode.SYSTEM),
            accent = enumOr(prefs.getString("accent", null), AccentColor.BLUE),
            bubbleStyle = enumOr(prefs.getString("bubble", null), BubbleStyle.TINTED),
            backgroundStyle = enumOr(prefs.getString("background", null), BackgroundStyle.PLAIN),
            backgroundImagePath = prefs.getString("background_image", null),
            fontSize = enumOr(prefs.getString("font_size", null), FontSize.NORMAL),
            locale = if (prefs.getString("locale", null) == "en-US") "en-US" else "zh-CN",
            thinking = prefs.getBoolean("thinking", true),
            search = prefs.getBoolean("search", true),
            ttsVoice = enumOr(prefs.getString("tts_voice", null), TtsVoice.STELLA),
            ttsEmotion = enumOr(prefs.getString("tts_emotion", null), TtsEmotion.AUTO),
            model = model,
            models = finalModels,
            readAloudMuted = prefs.getBoolean("read_aloud_muted", true),
            autoCollapseThinking = prefs.getBoolean("auto_collapse_thinking", true),
            hapticEnabled = prefs.getBoolean("haptic_enabled", true),
            hapticMode = enumOr(prefs.getString("haptic_mode", null), HapticMode.FLOW),
            hapticLevel = prefs.getInt("haptic_level", 2).coerceIn(1, 5),
            asrLanguage = prefs.getString("asr_language", null) ?: "auto",
            location = prefs.getString("location", null),
            network = network,
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putString("server_url", settings.serverUrl)
            .putString("username", settings.username)
            .putString("theme", settings.themeMode.name)
            .putString("accent", settings.accent.name)
            .putString("bubble", settings.bubbleStyle.name)
            .putString("background", settings.backgroundStyle.name)
            .putString("background_image", settings.backgroundImagePath)
            .putString("font_size", settings.fontSize.name)
            .putString("locale", settings.locale)
            .putBoolean("thinking", settings.thinking)
            .putBoolean("search", settings.search)
            .putString("tts_voice", settings.ttsVoice.name)
            .putString("tts_emotion", settings.ttsEmotion.name)
            .putString("model", settings.model)
            .putString("models", AppJson.encodeToString(
                ListSerializer(String.serializer()), settings.models
            ))
            .putBoolean("read_aloud_muted", settings.readAloudMuted)
            .putBoolean("auto_collapse_thinking", settings.autoCollapseThinking)
            .putBoolean("haptic_enabled", settings.hapticEnabled)
            .putString("haptic_mode", settings.hapticMode.name)
            .putInt("haptic_level", settings.hapticLevel.coerceIn(1, 5))
            .putString("asr_language", settings.asrLanguage)
            .putString("location", settings.location)
            .apply()
        secure.put("network", AppJson.encodeToString(NetworkSettings.serializer(), settings.network))
    }

    fun saveNetwork(network: NetworkSettings) {
        secure.put("network", AppJson.encodeToString(NetworkSettings.serializer(), network))
    }

    fun saveServer(serverUrl: String?, username: String?) {
        prefs.edit().putString("server_url", serverUrl).putString("username", username).apply()
    }

    fun cookie(host: String): String? = secure.get("cookie:$host")

    fun saveCookie(host: String, cookie: String?) = secure.put("cookie:$host", cookie)

    fun networkOwner(): String? = secure.get("network_owner")

    fun saveNetworkOwner(owner: String?) = secure.put("network_owner", owner)

    fun clearCloudSession() {
        prefs.edit().remove("username").apply()
    }

    fun clearEverything() {
        prefs.edit().clear().apply()
        secure.clear()
    }

    private inline fun <reified T : Enum<T>> enumOr(value: String?, fallback: T): T =
        runCatching { value?.let { enumValueOf<T>(it) } }.getOrNull() ?: fallback
}

fun normalizeBaseUrl(value: String?): String? {
    val trimmed = value?.trim().orEmpty().trimEnd('/')
    if (trimmed.isEmpty()) return null
    return runCatching {
        val url = java.net.URI(trimmed)
        val valid = url.scheme in listOf("http", "https") &&
            url.host != null &&
            url.userInfo == null &&
            url.query == null &&
            url.fragment == null
        if (valid) trimmed else null
    }.getOrNull()
}

fun normalizeServerUrl(value: String?): String? = normalizeBaseUrl(value)
