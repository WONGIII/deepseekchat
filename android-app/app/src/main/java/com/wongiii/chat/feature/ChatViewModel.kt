package com.wongiii.chat.feature

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wongiii.chat.data.local.AppPrefs
import com.wongiii.chat.data.local.AppSettings
import com.wongiii.chat.data.local.FileStore
import com.wongiii.chat.data.local.LocalSessionStore
import com.wongiii.chat.data.local.normalizeBaseUrl
import com.wongiii.chat.data.local.normalizeServerUrl
import com.wongiii.chat.data.model.AppJson
import com.wongiii.chat.data.model.BackgroundStyle
import com.wongiii.chat.data.model.BubbleStyle
import com.wongiii.chat.data.model.ChatMessage
import com.wongiii.chat.data.model.ChatSession
import com.wongiii.chat.data.model.FileAttachment
import com.wongiii.chat.data.model.FontSize
import com.wongiii.chat.data.model.MessageError
import com.wongiii.chat.data.model.NetworkSettings
import com.wongiii.chat.data.model.ReplyBlock
import com.wongiii.chat.data.model.SessionSummary
import com.wongiii.chat.data.model.ThemeMode
import com.wongiii.chat.data.model.TtsEmotion
import com.wongiii.chat.data.model.TtsVoice
import com.wongiii.chat.data.model.newId
import com.wongiii.chat.data.model.path
import com.wongiii.chat.data.model.summary
import com.wongiii.chat.data.remote.AccountApi
import com.wongiii.chat.data.remote.AppVersion
import com.wongiii.chat.data.remote.Endpoints
import com.wongiii.chat.data.remote.HttpClientFactory
import com.wongiii.chat.data.remote.HttpError
import com.wongiii.chat.data.remote.LlmApi
import com.wongiii.chat.data.remote.SearchApi
import com.wongiii.chat.data.remote.SessionApi
import com.wongiii.chat.data.remote.SpeechApi
import com.wongiii.chat.data.repo.CloudRepository
import com.wongiii.chat.data.repo.LocalRepository
import com.wongiii.chat.data.repo.SessionRepository
import com.wongiii.chat.domain.archive.HistoryArchive
import com.wongiii.chat.domain.chat.ChatEngine
import com.wongiii.chat.domain.files.FileExtractor
import com.wongiii.chat.domain.files.ImageCompressor
import com.wongiii.chat.domain.files.fileExtension
import com.wongiii.chat.domain.files.inferMime
import com.wongiii.chat.domain.files.isSupportedAttachment
import com.wongiii.chat.domain.speech.ReadAloudPlayer
import com.wongiii.chat.domain.speech.VoiceRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class DataMode { LOCAL, CLOUD }

enum class VoicePhase { IDLE, RECORDING, TRANSCRIBING }

data class MigrationPrompt(
    val localCount: Int,
    val remoteCount: Int,
)

data class UiState(
    val ready: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val mode: DataMode = DataMode.LOCAL,
    val cloudUser: String? = null,
    val authRequired: Boolean = false,
    val serverUrl: String? = null,
    val sessions: List<SessionSummary> = emptyList(),
    val activeSession: ChatSession? = null,
    val activeId: String? = null,
    val loadingSession: Boolean = false,
    val savingError: String? = null,
    val toast: String? = null,
    val draftFiles: List<FileAttachment> = emptyList(),
    val streamingSessionId: String? = null,
    val readingMessageId: String? = null,
    val authBusy: Boolean = false,
    val authError: String? = null,
    val migration: MigrationPrompt? = null,
    val searchQuery: String = "",
    val searchHits: List<SessionSummary>? = null,
    val versionInfo: AppVersion? = null,
    val updateStatus: String? = null,
    val composerText: String = "",
    val voicePhase: VoicePhase = VoicePhase.IDLE,
    val voiceSeconds: Int = 0,
    val voiceLevels: List<Float> = emptyList(),
    val readingStatus: String? = null,
    val thinkingOpen: Map<String, Boolean> = emptyMap(),
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = AppPrefs(application)
    private val factory = HttpClientFactory(prefs)
    private val fileStore = FileStore(application)
    private val localStore = LocalSessionStore(application.filesDir)
    private val localRepo = LocalRepository(localStore)
    private val llmApi = LlmApi(factory)
    private val searchApi = SearchApi(factory)
    private val speechApi = SpeechApi(factory)
    private val voiceRecorder = VoiceRecorder(application)
    private val readPlayer = ReadAloudPlayer(
        context = application,
        speechApi = speechApi,
        scope = viewModelScope,
        onError = { error -> toast(readErrorText(error)) },
        onState = { id, status ->
            _state.value = _state.value.copy(readingMessageId = id, readingStatus = status)
        },
    )
    private val extractor = FileExtractor(application)
    private val compressor = ImageCompressor()
    private val engine = ChatEngine(llmApi, searchApi, fileStore)

    private val _state = MutableStateFlow(UiState(settings = prefs.load()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val cache = mutableMapOf<String, ChatSession>()
    private val saveJobs = mutableMapOf<String, Job>()
    private val streamJobs = mutableMapOf<String, Job>()
    private val freshSessionIds = mutableSetOf<String>()
    private var cloudRepo: CloudRepository? = null
    private var toastJob: Job? = null
    private var voiceJob: Job? = null

    init {
        val settings = prefs.load()
        _state.value = _state.value.copy(
            settings = settings,
            serverUrl = settings.serverUrl,
            cloudUser = settings.username,
            mode = if (settings.cloudConfigured) DataMode.CLOUD else DataMode.LOCAL,
        )
        viewModelScope.launch {
            if (settings.cloudConfigured) {
                if (restoreCloud(settings)) return@launch
            }
            loadLocalSessions()
        }
    }

    private suspend fun restoreCloud(settings: AppSettings): Boolean {
        val server = settings.serverUrl ?: return false
        val accountApi = AccountApi(factory, server)
        val user = runCatching { accountApi.me() }.getOrNull()
        if (user == null) {
            _state.value = _state.value.copy(
                mode = DataMode.CLOUD,
                authRequired = true,
                ready = true,
                cloudUser = settings.username,
            )
            return true
        }
        setCloudRepo(server)
        val remote = runCatching { cloudRepo?.list() ?: emptyList() }.getOrElse {
            toast("无法加载云端会话：${it.message}")
            emptyList()
        }
        val network = runCatching { SessionApi(factory, server).networkSettings() }.getOrNull()
        val nextSettings = if (network != null) {
            settings.copy(network = network)
        } else {
            val localNetwork = settings.network
            if (!localNetwork.isEmpty()) {
                runCatching { SessionApi(factory, server).saveNetworkSettings(localNetwork) }
            }
            settings
        }
        prefs.saveSettings(nextSettings)
        _state.value = _state.value.copy(
            settings = nextSettings,
            ready = true,
            mode = DataMode.CLOUD,
            cloudUser = user.username,
            authRequired = false,
            sessions = remote,
        )
        return true
    }

    private suspend fun loadLocalSessions() {
        val settings = prefs.load()
        _state.value = _state.value.copy(
            ready = false,
            mode = DataMode.LOCAL,
            authRequired = false,
            settings = settings,
        )
        val list = runCatching { localRepo.list() }.getOrElse {
            toast("读取本机会话失败：${it.message}")
            emptyList()
        }
        _state.value = _state.value.copy(
            ready = true,
            sessions = list,
            activeSession = null,
            activeId = null,
        )
    }

    private fun setCloudRepo(server: String) {
        cloudRepo = CloudRepository(
            SessionApi(factory, server),
            LocalSessionStore(getApplication<Application>().filesDir.resolve("cloud_cache").also { it.mkdirs() }),
        )
    }

    private fun repo(): SessionRepository =
        if (_state.value.mode == DataMode.CLOUD) cloudRepo ?: localRepo else localRepo

    fun newChat() {
        readPlayer.stop()
        cancelVoiceRecording(keepText = true)
        cache.clear()
        _state.value = _state.value.copy(activeSession = null, activeId = null, draftFiles = emptyList())
    }

    fun selectSession(id: String?) {
        if (id == null) {
            newChat()
            return
        }
        if (id == _state.value.activeId) return
        val cached = cache[id]
        if (cached != null) {
            readPlayer.stop()
            _state.value = _state.value.copy(activeSession = cached, activeId = id, draftFiles = emptyList())
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingSession = true)
            val session = runCatching { repo().load(id) }.getOrElse {
                toast("加载会话失败：${it.message}")
                null
            }
            if (session != null) cache[id] = session
            _state.value = _state.value.copy(
                loadingSession = false,
                activeSession = session,
                activeId = id,
                draftFiles = emptyList(),
            )
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        val files = _state.value.draftFiles
        if (trimmed.isEmpty() && files.isEmpty()) return
        if (files.any { !it.isImage && it.extraction == "pending" }) {
            toast("附件还在解析中")
            return
        }
        if (files.any { !it.isImage && it.extraction == "error" }) {
            toast("有附件解析失败，请移除或重试")
            return
        }
        val settings = _state.value.settings
        if (settings.network.llmApiKey.isNullOrBlank()) {
            toast("请先在设置中填写 API Key")
            return
        }
        val now = System.currentTimeMillis()
        val isNewSession = _state.value.activeSession == null
        var session = _state.value.activeSession ?: ChatSession(id = newId(), createdAt = now, updatedAt = now)
        val currentPath = session.path()
        val parent = currentPath.lastOrNull()
        val user = ChatMessage(
            id = newId(),
            role = "user",
            content = trimmed,
            files = files,
            createdAt = now,
            parentId = parent?.id,
        )
        val assistant = ChatMessage(
            id = newId(),
            role = "assistant",
            createdAt = now,
            parentId = user.id,
            model = settings.model,
            thinking = settings.thinking,
            search = settings.search,
            status = "streaming",
            latestReminder = null,
        )
        val updatedMessages = session.messages
            .map { if (it.id == parent?.id) it.copy(selectedChildId = user.id) else it }
            .plus(user)
            .plus(assistant)
        val updatedUser = user.copy(selectedChildId = assistant.id)
        session = session.copy(
            title = if (session.title.isBlank()) fallbackTitle(user).take(120) else session.title,
            messages = updatedMessages.map { if (it.id == user.id) updatedUser else it },
            rootMessageId = session.rootMessageId ?: user.id,
            updatedAt = now,
        )
        if (isNewSession) freshSessionIds += session.id
        cache[session.id] = session
        readPlayer.stop()
        cancelVoiceRecording(keepText = true)
        _state.value = _state.value.copy(
            activeSession = session,
            activeId = session.id,
            draftFiles = emptyList(),
            streamingSessionId = session.id,
            composerText = "",
        )
        viewModelScope.launch {
            runCatching { repo().save(session) }.onFailure { toast("保存会话失败：${it.message}") }
            refreshSummaries()
            startStream(session.id, user.id, assistant.id, settings)
        }
    }

    private fun startStream(sessionId: String, questionId: String, assistantId: String, settings: AppSettings) {
        streamJobs[sessionId]?.cancel()
        val session = cache[sessionId] ?: return
        val question = session.messages.firstOrNull { it.id == questionId } ?: return
        val assistant = session.messages.firstOrNull { it.id == assistantId } ?: return
        val titleEnabled = session.title.isBlank() || session.title.startsWith("新对话 ·")
        streamJobs[sessionId] = viewModelScope.launch {
            var finalSession: ChatSession? = null
            try {
                finalSession = engine.stream(
                    session = session,
                    assistant = assistant,
                    question = question,
                    settings = settings,
                    titleEnabled = titleEnabled,
                    onUpdate = { updated ->
                        cache[sessionId] = updated
                        if (_state.value.activeId == sessionId) {
                            _state.value = _state.value.copy(activeSession = updated)
                        }
                        scheduleSave(updated)
                    },
                    onServerTitle = { title ->
                        val current = cache[sessionId] ?: session
                        if (current.title.isBlank() || current.title.startsWith("新对话 ·")) {
                            val titled = current.copy(title = title.take(120), updatedAt = System.currentTimeMillis())
                            cache[sessionId] = titled
                            if (_state.value.activeId == sessionId) {
                                _state.value = _state.value.copy(activeSession = titled)
                            }
                            scheduleSave(titled)
                        }
                    },
                )
            } catch (error: Throwable) {
                val current = cache[sessionId] ?: session
                val failed = current.copy(
                    messages = current.messages.map { message ->
                        if (message.id == assistantId) {
                            message.copy(
                                status = "error",
                                error = MessageError("network", error.message ?: "生成失败"),
                            )
                        } else message
                    },
                )
                cache[sessionId] = failed
                if (_state.value.activeId == sessionId) {
                    _state.value = _state.value.copy(activeSession = failed)
                }
                finalSession = failed
            }
            val finished = finalSession ?: cache[sessionId] ?: session
            var titled = finished
            if (titled.title.isBlank()) {
                titled = titled.copy(title = fallbackTitle(question).take(120))
            }
            cache[sessionId] = titled
            withContext(NonCancellable) {
                runCatching { repo().save(titled) }.onFailure { toast("保存会话失败：${it.message}") }
                refreshSummaries()
            }
            if (_state.value.activeId == sessionId) {
                _state.value = _state.value.copy(activeSession = titled)
            }
            val finalReply = titled.messages.firstOrNull { it.id == assistantId }
            val speechText = finalReply?.blocks
                ?.filter { it.type == "text" && !it.isTemplate }
                ?.joinToString(System.lineSeparator() + System.lineSeparator()) { it.text.orEmpty() }
                .orEmpty()
            if (!settings.readAloudMuted && speechText.isNotBlank() && finalReply?.refusal != true) {
                readPlayer.toggle(assistantId, speechText, settings)
            }
            streamJobs.remove(sessionId)
            if (_state.value.streamingSessionId == sessionId) {
                _state.value = _state.value.copy(streamingSessionId = null)
            }
        }
    }

    fun stopStreaming() {
        val id = _state.value.streamingSessionId ?: _state.value.activeId ?: return
        streamJobs[id]?.cancel()
    }

    fun regenerate(messageId: String) {
        val session = _state.value.activeSession ?: return
        val old = session.messages.firstOrNull { it.id == messageId } ?: return
        if (old.isUser) return
        val parentId = old.parentId ?: return
        val settings = _state.value.settings
        val assistant = old.copy(
            id = newId(),
            createdAt = System.currentTimeMillis(),
            selectedChildId = null,
            status = "streaming",
            blocks = emptyList(),
            error = null,
            stopReason = null,
            refusal = false,
            prefix = false,
        )
        val updated = session.copy(
            messages = session.messages
                .map { if (it.id == parentId) it.copy(selectedChildId = assistant.id) else it }
                .plus(assistant),
            updatedAt = System.currentTimeMillis(),
        )
        cache[session.id] = updated
        _state.value = _state.value.copy(activeSession = updated, streamingSessionId = session.id)
        viewModelScope.launch {
            runCatching { repo().save(updated) }
            refreshSummaries()
            startStream(session.id, parentId, assistant.id, settings)
        }
    }

    fun editUser(messageId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val session = _state.value.activeSession ?: return
        val original = session.messages.firstOrNull { it.id == messageId } ?: return
        if (!original.isUser) return
        val settings = _state.value.settings
        val parent = original.parentId?.let { id -> session.messages.firstOrNull { it.id == id } }
        val user = original.copy(
            id = newId(),
            content = trimmed,
            createdAt = System.currentTimeMillis(),
            selectedChildId = null,
        )
        val assistant = ChatMessage(
            id = newId(),
            role = "assistant",
            createdAt = System.currentTimeMillis(),
            parentId = user.id,
            model = settings.model,
            thinking = settings.thinking,
            search = settings.search,
            status = "streaming",
        )
        val newUser = user.copy(selectedChildId = assistant.id)
        var messages = session.messages
            .map { if (it.id == parent?.id) it.copy(selectedChildId = newUser.id) else it }
            .plus(newUser)
            .plus(assistant)
        val updated = session.copy(
            messages = messages,
            rootMessageId = if (parent == null) newUser.id else session.rootMessageId,
            updatedAt = System.currentTimeMillis(),
        )
        cache[session.id] = updated
        _state.value = _state.value.copy(activeSession = updated, streamingSessionId = session.id)
        viewModelScope.launch {
            runCatching { repo().save(updated) }
            refreshSummaries()
            startStream(session.id, newUser.id, assistant.id, settings)
        }
    }

    fun continueReply(messageId: String) {
        val session = _state.value.activeSession ?: return
        val message = session.messages.firstOrNull { it.id == messageId } ?: return
        if (message.isUser) return
        val parentId = message.parentId ?: return
        val question = session.messages.firstOrNull { it.id == parentId } ?: return
        val updatedMessage = message.copy(
            prefix = true,
            status = "streaming",
            error = null,
            stopReason = null,
        )
        val updated = session.copy(
            messages = session.messages.map { if (it.id == messageId) updatedMessage else it },
            updatedAt = System.currentTimeMillis(),
        )
        cache[session.id] = updated
        _state.value = _state.value.copy(activeSession = updated, streamingSessionId = session.id)
        startStream(session.id, question.id, messageId, _state.value.settings)
    }

    fun switchBranch(messageId: String, delta: Int) {
        val session = _state.value.activeSession ?: return
        val message = session.messages.firstOrNull { it.id == messageId } ?: return
        val siblings = session.messages.filter { it.parentId == message.parentId }
        if (siblings.size < 2) return
        val index = siblings.indexOfFirst { it.id == message.id }
        if (index < 0) return
        val target = siblings[(index + delta + siblings.size) % siblings.size]
        val parentId = message.parentId
        val updated = session.copy(
            messages = session.messages.map { candidate ->
                when {
                    parentId == null -> candidate
                    candidate.id == parentId -> candidate.copy(selectedChildId = target.id)
                    else -> candidate
                }
            },
            rootMessageId = if (parentId == null) target.id else session.rootMessageId,
            updatedAt = System.currentTimeMillis(),
        )
        cache[session.id] = updated
        _state.value = _state.value.copy(activeSession = updated)
        scheduleSave(updated)
    }

    fun renameSession(id: String, title: String) {
        val value = title.trim().take(120)
        if (value.isEmpty()) return
        viewModelScope.launch {
            runCatching { repo().rename(id, value) }.onFailure { toast("重命名失败：${it.message}") }
            cache[id]?.let { cache[id] = it.copy(title = value) }
            if (_state.value.activeId == id) {
                _state.value = _state.value.copy(activeSession = _state.value.activeSession?.copy(title = value))
            }
            refreshSummaries()
        }
    }

    fun deleteSession(id: String) {
        streamJobs[id]?.cancel()
        viewModelScope.launch {
            runCatching { repo().delete(id) }.onSuccess {
                cache.remove(id)
                if (_state.value.activeId == id) {
                    _state.value = _state.value.copy(activeSession = null, activeId = null)
                }
                refreshSummaries()
            }.onFailure { toast("删除失败：${it.message}") }
        }
    }

    private suspend fun refreshSummaries() {
        val list = runCatching { repo().list() }.getOrElse { return }
        _state.value = _state.value.copy(sessions = list)
    }

    private fun scheduleSave(session: ChatSession) {
        saveJobs[session.id]?.cancel()
        saveJobs[session.id] = viewModelScope.launch {
            delay(450)
            runCatching { repo().save(session) }
                .onFailure {
                    if (it is HttpError && it.status == 401) {
                        _state.value = _state.value.copy(authRequired = true)
                    }
                    _state.value = _state.value.copy(savingError = "保存失败：${it.message}")
                }
                .onSuccess { _state.value = _state.value.copy(savingError = null) }
        }
    }

    fun retrySave() {
        val current = _state.value.activeSession ?: return
        viewModelScope.launch {
            runCatching { repo().save(current) }
                .onSuccess { _state.value = _state.value.copy(savingError = null) }
                .onFailure { toast("重试保存失败：${it.message}") }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value.settings)
        prefs.saveSettings(next)
        _state.value = _state.value.copy(settings = next)
        if (_state.value.mode == DataMode.CLOUD) {
            val server = next.serverUrl
            if (server != null && next.username != null) {
                viewModelScope.launch {
                    runCatching { SessionApi(factory, server).saveNetworkSettings(next.network) }
                }
            }
        }
    }

    fun setTheme(mode: ThemeMode) = updateSettings { it.copy(themeMode = mode) }
    fun setAccent(color: com.wongiii.chat.data.model.AccentColor) = updateSettings { it.copy(accent = color) }
    fun setBubble(style: BubbleStyle) = updateSettings { it.copy(bubbleStyle = style) }
    fun setBackground(style: BackgroundStyle) = updateSettings { it.copy(backgroundStyle = style) }
    fun setBackgroundImage(path: String?) = updateSettings { it.copy(backgroundImagePath = path, backgroundStyle = if (path != null) BackgroundStyle.CUSTOM else BackgroundStyle.PLAIN) }
    fun setFontSize(size: FontSize) = updateSettings { it.copy(fontSize = size) }
    fun setLocale(locale: String) = updateSettings { it.copy(locale = locale) }
    fun setThinking(value: Boolean) = updateSettings { it.copy(thinking = value) }
    fun setSearch(value: Boolean) = updateSettings { it.copy(search = value) }
    fun setTtsVoice(value: TtsVoice) = updateSettings { it.copy(ttsVoice = value) }
    fun setTtsEmotion(value: TtsEmotion) = updateSettings { it.copy(ttsEmotion = value) }
    fun setAsrLanguage(value: String) = updateSettings { it.copy(asrLanguage = value) }
    fun setLocation(value: String?) = updateSettings { it.copy(location = value?.trim()?.takeIf { it.isNotEmpty() }) }
    fun setReadAloudMuted(value: Boolean) = updateSettings { it.copy(readAloudMuted = value) }
    fun setAutoCollapseThinking(value: Boolean) = updateSettings { it.copy(autoCollapseThinking = value) }

    fun setThinkingOpen(messageId: String, open: Boolean) {
        _state.value = _state.value.copy(
            thinkingOpen = _state.value.thinkingOpen + (messageId to open),
        )
    }
    fun setModel(value: String) = updateSettings { it.copy(model = value.trim()) }
    fun setNetwork(value: NetworkSettings) = updateSettings { it.copy(network = value) }

    fun addModel(model: String) {
        val value = model.trim()
        if (value.isEmpty()) return
        updateSettings {
            it.copy(
                model = value,
                models = (it.models + value).distinct(),
            )
        }
    }

    fun fetchModels() {
        val settings = _state.value.settings
        val base = Endpoints.llm(settings.network)
        val key = settings.network.llmApiKey.orEmpty()
        if (key.isBlank()) {
            toast("请先填写 API Key")
            return
        }
        viewModelScope.launch {
            runCatching { llmApi.fetchModels(base, key) }
                .onSuccess { models ->
                    if (models.isEmpty()) {
                        toast("没有获取到模型")
                    } else {
                        val merged = (settings.models + models).distinct()
                        updateSettings { it.copy(models = merged) }
                        toast("已获取 ${models.size} 个模型")
                    }
                }
                .onFailure { toast("获取模型失败：${it.message}") }
        }
    }

    fun toast(message: String) {
        toastJob?.cancel()
        _state.value = _state.value.copy(toast = message)
        toastJob = viewModelScope.launch {
            delay(3000)
            _state.value = _state.value.copy(toast = null)
        }
    }

    fun clearToast() {
        toastJob?.cancel()
        _state.value = _state.value.copy(toast = null)
    }

    fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val current = _state.value.draftFiles
        if (current.size + uris.size > 50) {
            toast("最多添加 50 个文件")
            return
        }
        viewModelScope.launch {
            for (uri in uris) {
                val name = fileStore.displayName(uri) ?: "文件"
                if (!isSupportedAttachment(name)) {
                    toast("不支持的文件格式：$name")
                    continue
                }
                val size = fileStore.displaySize(uri) ?: 0
                if (size > 64L * 1024 * 1024) {
                    toast("文件超过 64MB：$name")
                    continue
                }
                val copyResult = runCatching { fileStore.copyFromUri(uri) }
                val copied = copyResult.getOrNull()
                if (copied == null) {
                    toast("读取文件失败：${copyResult.exceptionOrNull()?.message.orEmpty()}")
                    continue
                }
                val (file, actualName) = copied
                val mime = getApplication<Application>().contentResolver.getType(uri).orEmpty()
                    .ifBlank { inferMime(actualName) }
                val isImage = mime.startsWith("image/") || fileExtension(actualName) in setOf("png", "jpg", "jpeg", "webp", "gif", "heic", "heif")
                var attachment = FileAttachment(
                    id = newId(),
                    name = actualName,
                    size = file.length(),
                    mimeType = mime,
                    isImage = isImage,
                    localPath = file.absolutePath,
                    extraction = if (isImage) "pending" else "pending",
                )
                _state.value = _state.value.copy(draftFiles = _state.value.draftFiles + attachment)
                if (isImage) {
                    attachment = runCatching { compressor.compress(attachment) }.getOrElse {
                        attachment.copy(extraction = "error", extractionError = "图片处理失败")
                    }
                } else {
                    attachment = runCatching { extractor.extract(attachment) }.getOrElse {
                        attachment.copy(extraction = "error", extractionError = it.message ?: "解析失败")
                    }
                }
                _state.value = _state.value.copy(
                    draftFiles = _state.value.draftFiles.map {
                        if (it.id == attachment.id) attachment else it
                    },
                )
            }
        }
    }

    fun addBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            val bytes = java.io.ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.toByteArray()
            }
            val attachment = runCatching {
                fileStore.copyBytes(bytes, "camera-${System.currentTimeMillis()}.jpg", "image/jpeg", true)
            }.getOrNull()
            if (attachment == null) {
                toast("保存拍照图片失败")
                return@launch
            }
            _state.value = _state.value.copy(draftFiles = _state.value.draftFiles + attachment)
            val compressed = runCatching { compressor.compress(attachment) }.getOrDefault(attachment)
            _state.value = _state.value.copy(
                draftFiles = _state.value.draftFiles.map {
                    if (it.id == attachment.id) compressed else it
                },
            )
        }
    }

    fun retryExtraction(id: String) {
        val attachment = _state.value.draftFiles.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                draftFiles = _state.value.draftFiles.map {
                    if (it.id == id) it.copy(extraction = "pending", extractionError = null) else it
                },
            )
            val updated = if (attachment.isImage) {
                runCatching { compressor.compress(attachment) }.getOrElse {
                    attachment.copy(extraction = "error", extractionError = it.message ?: "图片处理失败")
                }
            } else {
                runCatching { extractor.extract(attachment) }.getOrElse {
                    attachment.copy(extraction = "error", extractionError = it.message ?: "解析失败")
                }
            }
            _state.value = _state.value.copy(
                draftFiles = _state.value.draftFiles.map { if (it.id == id) updated else it },
            )
        }
    }

    fun removeFile(id: String) {
        val file = _state.value.draftFiles.firstOrNull { it.id == id }
        file?.localPath?.let { fileStore.delete(it) }
        _state.value = _state.value.copy(draftFiles = _state.value.draftFiles.filterNot { it.id == id })
    }

    fun searchSessions(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        if (query.trim().isEmpty()) {
            _state.value = _state.value.copy(searchHits = null)
            return
        }
        viewModelScope.launch {
            val hits = runCatching { repo().search(query.trim()) }.getOrElse {
                toast("搜索失败：${it.message}")
                emptyList()
            }
            _state.value = _state.value.copy(searchHits = hits)
        }
    }

    fun prepareCloud(server: String, onResult: (Boolean) -> Unit) {
        val normalized = normalizeServerUrl(server)
        if (normalized == null) {
            toast("服务器地址无效")
            onResult(false)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(authBusy = true, authError = null)
            val ok = AccountApi(factory, normalized).health()
            _state.value = _state.value.copy(authBusy = false)
            if (!ok) {
                _state.value = _state.value.copy(authError = "无法连接服务器")
                onResult(false)
            } else {
                prefs.saveServer(normalized, null)
                _state.value = _state.value.copy(
                    serverUrl = normalized,
                    mode = DataMode.CLOUD,
                    authRequired = true,
                    cloudUser = null,
                )
                onResult(true)
            }
        }
    }

    fun login(username: String, password: String, register: Boolean) {
        val server = _state.value.serverUrl ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(authBusy = true, authError = null)
            runCatching {
                val api = AccountApi(factory, server)
                if (register) api.register(username.trim(), password) else api.login(username.trim(), password)
            }.onSuccess { user ->
                prefs.saveServer(server, user.username)
                val settings = prefs.load()
                _state.value = _state.value.copy(
                    settings = settings,
                    cloudUser = user.username,
                    authRequired = false,
                    authBusy = false,
                    authError = null,
                )
                setCloudRepo(server)
                afterLogin(server, user.username)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    authBusy = false,
                    authError = error.message ?: "登录失败",
                )
            }
        }
    }

    private suspend fun afterLogin(server: String, username: String) {
        val remote = runCatching { cloudRepo?.list() ?: emptyList() }.getOrElse { emptyList() }
        val remoteSettings = runCatching { SessionApi(factory, server).networkSettings() }.getOrNull()
        val local = runCatching { localRepo.list() }.getOrElse { emptyList() }
        val localSessions = runCatching { localRepo.exportAll() }.getOrElse { emptyList() }
        val settings = prefs.load().let { current ->
            if (remoteSettings != null) current.copy(network = remoteSettings) else current
        }
        prefs.saveSettings(settings)
        _state.value = _state.value.copy(
            settings = settings,
            sessions = remote,
            mode = DataMode.CLOUD,
            cloudUser = username,
            ready = true,
        )
        if (remoteSettings == null && !settings.network.isEmpty()) {
            runCatching { SessionApi(factory, server).saveNetworkSettings(settings.network) }
        }
        if (localSessions.isNotEmpty() || remote.isNotEmpty()) {
            _state.value = _state.value.copy(
                migration = MigrationPrompt(local.count(), remote.size),
            )
        }
        refreshSummaries()
    }

    fun resolveMigration(mode: String) {
        val prompt = _state.value.migration ?: return
        _state.value = _state.value.copy(migration = null)
        viewModelScope.launch {
            val localSessions = runCatching { localRepo.exportAll() }.getOrElse { emptyList() }
            val remote = runCatching { cloudRepo?.exportAll() ?: emptyList() }.getOrElse { emptyList() }
            val target = when (mode) {
                "merge" -> {
                    val byId = remote.associateBy { it.id }.toMutableMap()
                    localSessions.forEach { local ->
                        val existing = byId[local.id]
                        if (existing == null || local.updatedAt > existing.updatedAt) byId[local.id] = local
                    }
                    byId.values.toList()
                }
                "replace" -> localSessions
                else -> remote
            }
            runCatching { cloudRepo?.replaceAll(target) }
                .onSuccess {
                    cache.clear()
                    refreshSummaries()
                    toast(
                        when (mode) {
                            "merge" -> "已合并本机会话"
                            "replace" -> "已用本机会话替换云端"
                            else -> "保留云端会话"
                        }
                    )
                }
                .onFailure { toast("迁移失败：${it.message}") }
        }
    }

    fun logout() {
        val server = _state.value.serverUrl
        viewModelScope.launch {
            if (server != null) runCatching { AccountApi(factory, server).logout() }
            prefs.saveServer(server, null)
            prefs.saveNetwork(NetworkSettings())
            prefs.saveNetworkOwner(null)
            val settings = prefs.load()
            cache.clear()
            setCloudRepoOrNull()
            _state.value = _state.value.copy(
                settings = settings,
                mode = DataMode.LOCAL,
                cloudUser = null,
                authRequired = false,
                activeSession = null,
                activeId = null,
            )
            loadLocalSessions()
        }
    }

    private fun setCloudRepoOrNull() {
        cloudRepo = null
    }

    fun checkUpdate() {
        val server = _state.value.serverUrl
        if (server == null) {
            _state.value = _state.value.copy(updateStatus = "当前版本 ${com.wongiii.chat.data.model.Brand.VERSION}")
            return
        }
        viewModelScope.launch {
            val version = runCatching { AccountApi(factory, server).version() }.getOrNull()
            _state.value = _state.value.copy(
                versionInfo = version,
                updateStatus = when {
                    version == null -> "检查更新失败"
                    version.version == com.wongiii.chat.data.model.Brand.VERSION -> "已是最新版本"
                    else -> "发现新版本 ${version.version}"
                },
            )
        }
    }

    fun isFreshSession(id: String): Boolean = id in freshSessionIds

    fun openServerForLogin() {
        _state.value = _state.value.copy(authRequired = true)
    }

    fun cancelCloudSetup() {
        val settings = prefs.load()
        setCloudRepoOrNull()
        cache.clear()
        _state.value = _state.value.copy(
            mode = DataMode.LOCAL,
            authRequired = false,
            cloudUser = settings.username,
            serverUrl = settings.serverUrl,
            activeSession = null,
            activeId = null,
        )
        viewModelScope.launch { loadLocalSessions() }
    }

    private fun fallbackTitle(question: ChatMessage): String {
        val text = question.content.ifBlank { question.files.firstOrNull()?.name.orEmpty() }
            .replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return "新对话"
        val segment = text.split('。', '！', '？', '!', '?', '\n').firstOrNull { it.isNotBlank() } ?: text
        val cjk = segment.any { it.code in 0x4E00..0x9FFF }
        val length = if (cjk) 15 else 28
        val title = if (segment.length > length) segment.take(length) + "…" else segment
        return "新对话 · $title"
    }

    fun deleteAllLocal() {
        viewModelScope.launch {
            runCatching { localRepo.replaceAll(emptyList()) }.onSuccess {
                localStore.let { }
                fileStore.clearAll()
                cache.clear()
                _state.value = _state.value.copy(activeSession = null, activeId = null)
                loadLocalSessions()
                toast("本机会话已清空")
            }.onFailure { toast("清空失败：${it.message}") }
        }
    }


    fun setBackgroundImageFromUri(uri: Uri) {
        viewModelScope.launch {
            val copied = runCatching { fileStore.copyFromUri(uri) }.getOrNull()
            if (copied == null) {
                toast("读取背景图片失败")
                return@launch
            }
            val attachment = FileAttachment(
                id = newId(),
                name = copied.second,
                size = copied.first.length(),
                mimeType = "image/jpeg",
                isImage = true,
                localPath = copied.first.absolutePath,
                extraction = "done",
            )
            val compressed = runCatching { compressor.compress(attachment) }.getOrDefault(attachment)
            setBackgroundImage(compressed.localPath)
            toast("聊天背景已更新")
        }
    }

    fun exportArchiveTo(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val sessions = repo().exportAll()
                val output = getApplication<Application>().contentResolver.openOutputStream(uri)
                    ?: error("无法写入文件")
                output.use { stream -> HistoryArchive.export(sessions, fileStore, stream) }
            }.onSuccess { toast("已导出历史会话") }
                .onFailure { toast("导出失败：${it.message}") }
        }
    }

    fun importArchiveFrom(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val input = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?: error("无法读取文件")
                input.use { stream ->
                    val sessions = HistoryArchive.import(stream, fileStore)
                    repo().replaceAll(sessions)
                    sessions
                }
            }.onSuccess { sessions ->
                cache.clear()
                _state.value = _state.value.copy(activeSession = null, activeId = null)
                refreshSummaries()
                toast("已导入 ${sessions.size} 个会话")
            }.onFailure { toast("导入失败：${it.message}") }
        }
    }

    fun deleteAllCurrent() {
        viewModelScope.launch {
            runCatching {
                repo().replaceAll(emptyList())
                fileStore.clearAll()
            }.onSuccess {
                cache.clear()
                _state.value = _state.value.copy(activeSession = null, activeId = null)
                refreshSummaries()
                toast("当前数据已清空")
            }.onFailure { toast("清空失败：${it.message}") }
        }
    }


    fun setComposerText(value: String) {
        _state.value = _state.value.copy(composerText = value)
    }

    fun readAloud(messageId: String, text: String) {
        if (text.isBlank()) return
        readPlayer.toggle(messageId, text, _state.value.settings)
    }

    fun stopReadAloud() {
        readPlayer.stop()
    }

    fun startVoiceInput() {
        if (!voiceRecorder.hasPermission()) {
            toast("请先授予麦克风权限")
            return
        }
        if (_state.value.voicePhase != VoicePhase.IDLE) {
            return
        }
        if (!voiceRecorder.start()) {
            toast("无法启动麦克风")
            return
        }
        _state.value = _state.value.copy(
            voicePhase = VoicePhase.RECORDING,
            voiceSeconds = 0,
            voiceLevels = emptyList(),
        )
        voiceJob = viewModelScope.launch {
            val levels = ArrayDeque<Float>()
            while (true) {
                val chunk = voiceRecorder.read()
                if (chunk != null && chunk.isNotEmpty()) {
                    levels.addLast(voiceRecorder.amplitude(chunk))
                    while (levels.size > 48) levels.removeFirst()
                    _state.value = _state.value.copy(
                        voiceSeconds = voiceRecorder.seconds(),
                        voiceLevels = levels.toList(),
                    )
                }
                if (voiceRecorder.limitReached()) {
                    stopVoiceInput()
                    break
                }
                delay(35)
            }
        }
    }

    fun stopVoiceInput(sendAfterTranscription: Boolean = true) {
        if (_state.value.voicePhase != VoicePhase.RECORDING) {
            if (_state.value.voicePhase == VoicePhase.TRANSCRIBING) cancelVoiceRecording(keepText = true)
            return
        }
        voiceJob?.cancel()
        voiceJob = null
        _state.value = _state.value.copy(voicePhase = VoicePhase.TRANSCRIBING)
        viewModelScope.launch {
            val wav = runCatching { voiceRecorder.stop() }.getOrNull()
            if (wav == null || wav.size < 44) {
                _state.value = _state.value.copy(voicePhase = VoicePhase.IDLE, voiceLevels = emptyList())
                toast("没有录到声音")
                return@launch
            }
            runCatching {
                speechApi.asr(
                    Endpoints.speech(_state.value.settings.network),
                    Endpoints.speechKey(_state.value.settings.network),
                    wav,
                )
            }.onSuccess { text ->
                val current = _state.value.composerText
                val next = when {
                    text.isBlank() -> current
                    current.isBlank() -> text
                    else -> current.trimEnd() + " " + text
                }
                if (sendAfterTranscription && next.isNotBlank()) {
                    _state.value = _state.value.copy(
                        voicePhase = VoicePhase.IDLE,
                        voiceLevels = emptyList(),
                        composerText = "",
                    )
                    send(next)
                } else {
                    _state.value = _state.value.copy(
                        composerText = next,
                        voicePhase = VoicePhase.IDLE,
                        voiceLevels = emptyList(),
                    )
                }
            }.onFailure { error ->
                _state.value = _state.value.copy(voicePhase = VoicePhase.IDLE, voiceLevels = emptyList())
                toast(readErrorText(error))
            }
        }
    }

    fun cancelVoiceRecording(keepText: Boolean) {
        voiceJob?.cancel()
        voiceJob = null
        viewModelScope.launch {
            runCatching { voiceRecorder.cancel() }
            _state.value = _state.value.copy(
                voicePhase = VoicePhase.IDLE,
                voiceLevels = emptyList(),
                voiceSeconds = 0,
            )
        }
    }

    private fun readErrorText(error: Throwable): String = when {
        error is HttpError && error.status == 401 -> "语音服务鉴权失败，请检查 API Key"
        error is HttpError && error.status == 402 -> "语音服务余额不足"
        error is HttpError && error.status == 429 -> "语音请求过于频繁，请稍后再试"
        error is HttpError && error.status >= 500 -> "语音服务暂时不可用"
        else -> error.message?.take(120).orEmpty().ifBlank { "语音服务请求失败" }
    }

    fun currentSettings(): AppSettings = _state.value.settings
}
