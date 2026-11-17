package com.wongiii.chat.feature

import android.content.Intent
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.wongiii.chat.data.model.SessionSummary
import com.wongiii.chat.data.model.TextCitation
import com.wongiii.chat.design.BrandMark
import com.wongiii.chat.design.ChatTheme
import com.wongiii.chat.design.ToastBanner
import com.wongiii.chat.design.chatColors
import com.wongiii.chat.design.Motion
import com.wongiii.chat.design.chatTypography
import com.wongiii.chat.data.model.Brand
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private data class SearchOverlayState(
    val sources: List<SearchSource>,
    val key: String,
)

private sealed interface AppScreen {
    data object Main : AppScreen
    data object SettingsRoot : AppScreen
    data object Auth : AppScreen
    data class Preview(val title: String, val html: String) : AppScreen
    data class Image(val path: String) : AppScreen
}

private fun settingsKey(page: SettingsPage): String = when (page) {
    SettingsPage.ACCOUNT -> "account"
    SettingsPage.DATA -> "data"
    SettingsPage.LANGUAGE -> "language"
    SettingsPage.APPEARANCE -> "appearance"
    SettingsPage.FONT_SIZE -> "font"
    SettingsPage.PERSONALIZATION -> "personalization"
    SettingsPage.LOCATION -> "location"
    SettingsPage.SERVICES -> "services"
    SettingsPage.NETWORK -> "network"
    SettingsPage.ASR_LANGUAGE -> "asr"
    SettingsPage.TTS -> "tts"
    SettingsPage.UPDATE -> "update"
    SettingsPage.TERMS -> "terms"
    SettingsPage.HELP -> "help"
    SettingsPage.LICENSES -> "licenses"
    SettingsPage.ROOT -> "root"
    SettingsPage.MODEL -> "model"
}

@OptIn(ExperimentalSharedTransitionApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatRoot(vm: ChatViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val stack = remember { mutableStateListOf<AppScreen>(AppScreen.Main) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var renameId by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var migrationDialog by remember { mutableStateOf(true) }
    var attachSheet by remember { mutableStateOf(false) }
    var settingsDetail by remember { mutableStateOf<SettingsPage?>(null) }
    var searchOverlay by remember { mutableStateOf<SearchOverlayState?>(null) }
    val chatListState = remember(state.activeId) { LazyListState() }
    var chatListReady by remember(state.activeId) { mutableStateOf(false) }
    val entranceSeen = remember(state.activeId) { mutableSetOf<String>() }
    var recentImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedRecent by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var mediaRefresh by remember { mutableIntStateOf(0) }
    val focusManager = LocalFocusManager.current
    val settingsScroll = rememberScrollState()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addFiles(uris)
    }
    val recordPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startVoiceInput() else vm.toast("没有麦克风权限，无法语音输入")
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        attachSheet = false
        vm.addFiles(uris)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        attachSheet = false
        if (bitmap != null) vm.addBitmap(bitmap)
    }
    val mediaPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) mediaRefresh++
    }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.setBackgroundImageFromUri(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportArchiveTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importArchiveFrom(uri)
    }

    ChatTheme(
        themeMode = state.settings.themeMode,
        accent = state.settings.accent,
        bubbleStyle = state.settings.bubbleStyle,
        fontSize = state.settings.fontSize,
    ) {
        val colors = chatColors()
        LaunchedEffect(state.authRequired) {
            if (state.authRequired) {
                if (stack.lastOrNull() != AppScreen.Auth) stack.add(AppScreen.Auth)
            } else if (stack.lastOrNull() == AppScreen.Auth) {
                stack.removeAt(stack.lastIndex)
            }
        }
        LaunchedEffect(state.activeId) {
            when (stack.lastOrNull()) {
                is AppScreen.Preview, is AppScreen.Image -> {
                    while (stack.size > 1 && stack.lastOrNull() != AppScreen.SettingsRoot) {
                        stack.removeAt(stack.lastIndex)
                        if (stack.lastOrNull() == AppScreen.Main) break
                    }
                }
                else -> Unit
            }
        }

        LaunchedEffect(attachSheet, mediaRefresh) {
            if (!attachSheet) return@LaunchedEffect
            val permission = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES else android.Manifest.permission.READ_EXTERNAL_STORAGE
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return@LaunchedEffect
            }
            recentImages = withContext(Dispatchers.IO) {
                val items = mutableListOf<Uri>()
                runCatching {
                    context.contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Images.Media._ID),
                        null,
                        null,
                        "${MediaStore.Images.Media.DATE_ADDED} DESC",
                    )?.use { cursor ->
                        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        while (cursor.moveToNext() && items.size < 12) {
                            items += ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                cursor.getLong(idIndex),
                            )
                        }
                    }
                }
                items
            }
        }

        BackHandler(enabled = true) {
            when {
                attachSheet -> attachSheet = false
                searchOverlay != null -> searchOverlay = null
                settingsDetail != null -> settingsDetail = null
                stack.lastOrNull() == AppScreen.Auth -> {
                    vm.cancelCloudSetup()
                    if (stack.size > 1) stack.removeAt(stack.lastIndex)
                }
                drawerOpen -> drawerOpen = false
                stack.size > 1 -> stack.removeAt(stack.lastIndex)
                else -> (context as? android.app.Activity)?.finish()
            }
        }

        Box(Modifier.fillMaxSize().background(colors.page)) {
            if (!state.ready) {
                SplashView()
            } else {
                SharedTransitionLayout {
                    AnimatedContent(
                        targetState = stack.last(),
                        label = "app-screen",
                        transitionSpec = {
                            when {
                                targetState == AppScreen.SettingsRoot && initialState == AppScreen.Main -> {
                                    slideInHorizontally(
                                        animationSpec = tween(360, easing = Motion.OutSoft),
                                        initialOffsetX = { it },
                                    ) togetherWith androidx.compose.animation.ExitTransition.None
                                }
                                initialState == AppScreen.SettingsRoot && targetState == AppScreen.Main -> {
                                    (androidx.compose.animation.EnterTransition.None togetherWith slideOutHorizontally(
                                        animationSpec = tween(340, easing = Motion.OutSoft),
                                        targetOffsetX = { it },
                                    )).apply { targetContentZIndex = -1f }
                                }
                                targetState is AppScreen.Auth || initialState is AppScreen.Auth -> {
                                    fadeIn(tween(280)) togetherWith fadeOut(tween(200))
                                }
                                targetState is AppScreen.Preview || targetState is AppScreen.Image -> {
                                    fadeIn(tween(240)) togetherWith fadeOut(tween(180))
                                }
                                else -> fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                            }
                        },
                    ) { screen ->
                        when (screen) {
                            AppScreen.Main -> {
                                AnimatedContent(
                                    targetState = searchOverlay,
                                    modifier = Modifier.fillMaxSize(),
                                    transitionSpec = {
                                        if (targetState != null) {
                                            slideInHorizontally(
                                                animationSpec = tween(360, easing = Motion.OutSoft),
                                                initialOffsetX = { it },
                                            ) togetherWith androidx.compose.animation.ExitTransition.None
                                        } else {
                                            (androidx.compose.animation.EnterTransition.None togetherWith slideOutHorizontally(
                                                animationSpec = tween(340, easing = Motion.OutSoft),
                                                targetOffsetX = { it },
                                            )).apply { targetContentZIndex = -1f }
                                        }
                                    },
                                    label = "search-overlay",
                                ) { overlay ->
                                    val screenScope = this
                                    if (overlay == null) {
                                val searchChipModifier: @Composable (String) -> Modifier = { key ->
                                    with(this@SharedTransitionLayout) {
                                        Modifier.sharedBounds(
                                            rememberSharedContentState(key),
                                            screenScope,
                                            boundsTransform = { _, _ -> tween(340, easing = Motion.OutSoft) },
                                            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                        )
                                    }
                                }
                                MainShell(
                                    state = state,
                                    vm = vm,
                                    drawerOpen = drawerOpen,
                                    onDrawerOpen = { drawerOpen = true },
                                    onDrawerClose = { drawerOpen = false },
                                    onOpenSettings = {
                                        drawerOpen = false
                                        settingsDetail = null
                                        if (stack.lastOrNull() == AppScreen.Main) stack.add(AppScreen.SettingsRoot)
                                    },
                                    onOpenImage = { path ->
                                        if (stack.lastOrNull() == AppScreen.Main) stack.add(AppScreen.Image(path))
                                    },
                                    onOpenSearch = { key, citations ->
                                        searchOverlay = SearchOverlayState(citations, key)
                                    },
                                    searchChipModifier = searchChipModifier,
                                    chatListState = chatListState,
                                    chatListReady = chatListReady,
                                    onChatListReady = { chatListReady = true },
                                    entranceSeen = entranceSeen,
                                    animateInitialMessages = vm.isFreshSession(state.activeId.orEmpty()),
                                    onOpenPreview = { title, html ->
                                        if (stack.lastOrNull() == AppScreen.Main) stack.add(AppScreen.Preview(title, html))
                                    },
                                    onAttach = {
                                        focusManager.clearFocus()
                                        attachSheet = true
                                        val permission = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES else android.Manifest.permission.READ_EXTERNAL_STORAGE
                                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                            mediaPermission.launch(permission)
                                        }
                                    },
                                    attachSheetOpen = attachSheet,
                                    onToggleAttachSheet = { attachSheet = !attachSheet },
                                    recentImages = recentImages,
                                    selectedRecent = selectedRecent,
                                    onToggleRecent = { uri ->
                                        if (uri !in selectedRecent) {
                                            selectedRecent = selectedRecent + uri
                                            vm.addFiles(listOf(uri))
                                        } else {
                                            selectedRecent = selectedRecent - uri
                                        }
                                    },
                                    onCamera = { cameraLauncher.launch(null) },
                                    onGallery = {
                                        attachSheet = false
                                        imagePicker.launch("image/*")
                                    },
                                    onFile = {
                                        attachSheet = false
                                        filePicker.launch(arrayOf("*/*"))
                                    },
                                    onVoiceStart = {
                                        if (androidx.core.content.ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.RECORD_AUDIO,
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        ) {
                                            vm.startVoiceInput()
                                        } else {
                                            recordPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    onRenameRequest = { renameId = it },
                                    onDeleteRequest = { deleteId = it },
                                    onExport = { exportLauncher.launch("chat_data-${java.time.LocalDate.now()}.zip") },
                                    onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                                    onDeleteAll = { vm.deleteAllCurrent() },
                                )
                                    } else {
                                        val titleModifier = with(this@SharedTransitionLayout) {
                                            Modifier.sharedBounds(
                                                rememberSharedContentState(overlay.key),
                                                screenScope,
                                                boundsTransform = { _, _ -> tween(340, easing = Motion.OutSoft) },
                                                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                            )
                                        }
                                        SearchResultsScreen(
                                            sources = overlay.sources,
                                            titleModifier = titleModifier,
                                            onBack = { searchOverlay = null },
                                        )
                                    }
                                }
                            }
                            AppScreen.SettingsRoot -> {
                                AnimatedContent(
                                    targetState = settingsDetail,
                                    modifier = Modifier.fillMaxSize(),
                                    transitionSpec = {
                                        if (targetState != null) {
                                            slideInHorizontally(
                                                animationSpec = tween(360, easing = Motion.OutSoft),
                                                initialOffsetX = { it },
                                            ) togetherWith androidx.compose.animation.ExitTransition.None
                                        } else {
                                            (androidx.compose.animation.EnterTransition.None togetherWith slideOutHorizontally(
                                                animationSpec = tween(340, easing = Motion.OutSoft),
                                                targetOffsetX = { it },
                                            )).apply { targetContentZIndex = -1f }
                                        }
                                    },
                                    label = "settings-stack",
                                ) { page ->
                                    val screenScope = this
                                    val rowModifiers = with(this@SharedTransitionLayout) {
                                        listOf(
                                            "account", "data", "language", "appearance", "font",
                                            "personalization", "location", "auto_collapse", "services", "network", "asr", "tts", "update", "terms", "help", "licenses",
                                        ).associateWith { key ->
                                            Modifier.sharedBounds(
                                                rememberSharedContentState("settings-row:$key"),
                                                screenScope,
                                                boundsTransform = { _, _ -> tween(340, easing = Motion.OutSoft) },
                                                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                            )
                                        }
                                    }
                                    if (page == null) {
                                        SettingsRoot(
                                            state = state,
                                            vm = vm,
                                            onBack = {
                                                settingsDetail = null
                                                if (stack.size > 1) stack.removeAt(stack.lastIndex)
                                            },
                                            onOpen = { next -> settingsDetail = next },
                                            onOpenAuth = { stack.add(AppScreen.Auth) },
                                            onLeaveCloud = { vm.logout() },
                                            rowModifier = { key -> rowModifiers[key] ?: Modifier },
                                            scrollState = settingsScroll,
                                        )
                                    } else {
                                        val headerModifier = with(this@SharedTransitionLayout) {
                                            Modifier.sharedBounds(
                                                rememberSharedContentState("settings-row:${settingsKey(page)}"),
                                                screenScope,
                                                boundsTransform = { _, _ -> tween(340, easing = Motion.OutSoft) },
                                                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                            )
                                        }
                                        SettingsDetail(
                                            page = page,
                                            state = state,
                                            vm = vm,
                                            onBack = { settingsDetail = null },
                                            onOpen = { next -> settingsDetail = next },
                                            onOpenAuth = { stack.add(AppScreen.Auth) },
                                            onExport = { exportLauncher.launch("chat_data-${java.time.LocalDate.now()}.zip") },
                                            onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                                            onDeleteAll = { vm.deleteAllCurrent() },
                                            onPickBackground = { backgroundPicker.launch(arrayOf("image/*")) },
                                            headerModifier = headerModifier,
                                        )
                                    }
                                }
                            }
                            AppScreen.Auth -> AuthScreen(
                                state = state,
                                vm = vm,
                                onBack = {
                                    vm.cancelCloudSetup()
                                    if (stack.size > 1) stack.removeAt(stack.lastIndex)
                                },
                            )
                            is AppScreen.Preview -> HtmlPreviewScreen(
                                title = screen.title,
                                html = screen.html,
                                onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                            )
                            is AppScreen.Image -> ImageViewerScreen(
                                path = screen.path,
                                onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                            )
                        }
                    }
                }
            }

            state.toast?.let { message ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 54.dp, start = 12.dp, end = 12.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    ToastBanner(message) { vm.clearToast() }
                }
            }

            val prompt = state.migration
            if (prompt != null && migrationDialog) {
                AlertDialog(
                    onDismissRequest = { migrationDialog = false },
                    title = { Text("迁移本机会话") },
                    text = {
                        Text(
                            "检测到本机会话 ${prompt.localCount} 个、云端会话 ${prompt.remoteCount} 个。要把本机会话合并到云端，还是用本地替换云端？"
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.resolveMigration("merge")
                            migrationDialog = false
                        }) { Text("合并") }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = {
                                vm.resolveMigration("replace")
                                migrationDialog = false
                            }) { Text("本地替换云端", color = chatColors().danger) }
                            TextButton(onClick = {
                                vm.resolveMigration("keep")
                                migrationDialog = false
                            }) { Text("保留云端") }
                        }
                    },
                )
            }
        }

        if (renameId != null) {
            val summary = state.sessions.firstOrNull { it.id == renameId }
            RenameDialog(
                initial = summary?.title.orEmpty(),
                onDismiss = { renameId = null },
                onConfirm = { title ->
                    renameId?.let { vm.renameSession(it, title) }
                    renameId = null
                },
            )
        }
        if (deleteId != null) {
            val summary = state.sessions.firstOrNull { it.id == deleteId }
            DeleteDialog(
                title = summary?.title.orEmpty(),
                onDismiss = { deleteId = null },
                onConfirm = {
                    deleteId?.let { vm.deleteSession(it) }
                    deleteId = null
                },
            )
        }
    }
}

@Composable
private fun SplashView() {
    val colors = chatColors()
    Box(Modifier.fillMaxSize().background(colors.page), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(size = 52.dp)
            Spacer(Modifier.height(14.dp))
            Text(Brand.NAME, style = chatTypography().title, color = colors.textPrimary)
            Spacer(Modifier.height(8.dp))
            Text("正在准备对话…", style = chatTypography().caption, color = colors.textTertiary)
        }
    }
}

@Composable
private fun MainShell(
    state: UiState,
    vm: ChatViewModel,
    drawerOpen: Boolean,
    onDrawerOpen: () -> Unit,
    onDrawerClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenSearch: (String, List<SearchSource>) -> Unit,
    onOpenPreview: (String, String) -> Unit,
    searchChipModifier: @Composable (String) -> Modifier,
    chatListState: LazyListState,
    chatListReady: Boolean,
    onChatListReady: () -> Unit,
    entranceSeen: MutableSet<String>,
    animateInitialMessages: Boolean,
    onAttach: () -> Unit,
    attachSheetOpen: Boolean,
    onToggleAttachSheet: () -> Unit,
    recentImages: List<Uri>,
    selectedRecent: Set<Uri>,
    onToggleRecent: (Uri) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onFile: () -> Unit,
    onVoiceStart: () -> Unit,
    onRenameRequest: (String) -> Unit,
    onDeleteRequest: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    val colors = chatColors()
    var voiceCancelArmed by remember { mutableStateOf(false) }
    var lastVoicePhase by remember { mutableStateOf(VoicePhase.RECORDING) }
    LaunchedEffect(state.voicePhase) {
        if (state.voicePhase != VoicePhase.IDLE) lastVoicePhase = state.voicePhase
    }
    val focusManager = LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val openDrawer = {
        focusManager.clearFocus()
        keyboard?.hide()
        onDrawerOpen()
    }
    Box(Modifier.fillMaxSize()) {
        val active = state.activeSession
        val toggleMute = {
            val muted = !state.settings.readAloudMuted
            vm.setReadAloudMuted(muted)
            if (muted) vm.stopReadAloud()
        }
        val composer: @Composable () -> Unit = {
            val bottomGap by animateDpAsState(
                targetValue = if (attachSheetOpen) 0.dp else 32.dp,
                animationSpec = tween(300, easing = Motion.OutSoft),
                label = "composerBottomGap",
            )
            Column {
                Composer(
                    settings = state.settings,
                    draftFiles = state.draftFiles,
                    streaming = state.streamingSessionId == active?.id && active != null,
                    text = state.composerText,
                    onTextChange = { vm.setComposerText(it) },
                    onSend = { vm.send(it) },
                    onStop = { vm.stopStreaming() },
                    onToggleThinking = { vm.setThinking(!state.settings.thinking) },
                    onToggleSearch = { vm.setSearch(!state.settings.search) },
                    onAttach = onAttach,
                    onVoiceStart = {
                        voiceCancelArmed = false
                        onVoiceStart()
                    },
                    onVoiceEnd = { cancelled ->
                        if (cancelled) vm.cancelVoiceRecording(true) else vm.stopVoiceInput(true)
                        voiceCancelArmed = false
                    },
                    onVoiceCancelArmed = { voiceCancelArmed = it },
                    sheetOpen = attachSheetOpen,
                    onToggleSheet = onToggleAttachSheet,
                    onRemoveFile = { vm.removeFile(it) },
                    onRetryFile = { vm.retryExtraction(it) },
                )
                Spacer(Modifier.height(bottomGap))
                AnimatedVisibility(
                    visible = attachSheetOpen,
                    enter = fadeIn(tween(180)) + expandVertically(
                        animationSpec = tween(320, easing = Motion.OutSoft),
                        expandFrom = Alignment.Top,
                    ),
                    exit = fadeOut(tween(140)) + shrinkVertically(
                        animationSpec = tween(260, easing = Motion.InOutSoft),
                        shrinkTowards = Alignment.Top,
                    ),
                ) {
                    AttachmentPanel(
                        recentImages = recentImages,
                        selectedImages = selectedRecent,
                        onToggleRecent = onToggleRecent,
                        onCamera = onCamera,
                        onGallery = onGallery,
                        onFile = onFile,
                    )
                }
            }
        }
        if (active == null) {
            WelcomeScreen(
                settings = state.settings,
                onMenu = openDrawer,
                onNewChat = { vm.newChat() },
                onToggleMute = toggleMute,
                composer = composer,
            )
        } else {
            ChatScreen(
                session = active,
                streaming = state.streamingSessionId == active.id,
                readingMessageId = state.readingMessageId,
                readingStatus = state.readingStatus,
                autoCollapseThinking = state.settings.autoCollapseThinking,
                thinkingOpen = state.thinkingOpen,
                onSetThinkingOpen = { id, open -> vm.setThinkingOpen(id, open) },
                settings = state.settings,
                onMenu = openDrawer,
                onNewChat = { vm.newChat() },
                onToggleMute = toggleMute,
                onCopy = { text ->
                    val clipboard = vm.getApplication<android.app.Application>()
                        .getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("message", text))
                    vm.toast("已复制")
                },
                onEdit = { message, text -> vm.editUser(message.id, text) },
                onRegenerate = { vm.regenerate(it) },
                onContinue = { vm.continueReply(it) },
                onSwitchBranch = { id, delta -> vm.switchBranch(id, delta) },
                onReadAloud = { id, text -> vm.readAloud(id, text) },
                onOpenSearch = onOpenSearch,
                onOpenPreview = onOpenPreview,
                onOpenImage = onOpenImage,
                searchChipModifier = searchChipModifier,
                chatListState = chatListState,
                chatListReady = chatListReady,
                onChatListReady = onChatListReady,
                entranceSeen = entranceSeen,
                animateInitialMessages = animateInitialMessages,
                composer = composer,
            )
        }
        if (state.savingError != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 58.dp, start = 12.dp, end = 12.dp),
            ) {
                Surface(
                    color = colors.dangerSoft,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            state.savingError,
                            modifier = Modifier.weight(1f),
                            color = colors.danger,
                            style = chatTypography().caption,
                        )
                        TextButton(onClick = { vm.retrySave() }) { Text("重试") }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = state.voicePhase != VoicePhase.IDLE,
            enter = fadeIn(tween(260, easing = Motion.OutSoft)),
            exit = fadeOut(tween(180, easing = Motion.InOutSoft)),
        ) {
            VoiceHoldOverlay(
                phase = if (state.voicePhase == VoicePhase.IDLE) lastVoicePhase else state.voicePhase,
                seconds = state.voiceSeconds,
                levels = state.voiceLevels,
                cancelArmed = voiceCancelArmed,
            )
        }
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = drawerOpen,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colors.scrim)
                        .clickableNoRipple(onDrawerClose),
                )
            }
            AnimatedVisibility(
                visible = drawerOpen,
                enter = slideInHorizontally(
                    animationSpec = tween(340, easing = Motion.OutSoft),
                    initialOffsetX = { -it },
                ) + fadeIn(tween(160)),
                exit = slideOutHorizontally(
                    animationSpec = tween(340, easing = Motion.OutSoft),
                    targetOffsetX = { -it },
                ) + fadeOut(tween(160)),
            ) {
                Box(Modifier.fillMaxSize()) {
                    DrawerPanel(
                        sessions = state.sessions,
                        searchHits = state.searchHits,
                        searchQuery = state.searchQuery,
                        activeId = state.activeId,
                        mode = state.mode,
                        username = state.cloudUser,
                        onSearch = { vm.searchSessions(it) },
                        onSelect = { id ->
                            vm.selectSession(id)
                            onDrawerClose()
                        },
                        onRename = onRenameRequest,
                        onDelete = onDeleteRequest,
                        onSettings = onOpenSettings,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsScreen(
    sources: List<SearchSource>,
    onBack: () -> Unit,
    titleModifier: Modifier = Modifier,
) {
    val colors = chatColors()
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxSize().background(colors.page)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(58.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = colors.textPrimary)
            }
            Text(
                "搜索结果",
                modifier = titleModifier,
                style = chatTypography().title,
                color = colors.textPrimary,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sources.forEach { source ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { runCatching { uriHandler.openUri(source.url) } },
                    color = colors.card,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.border),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SiteLogo(source, 18.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                source.siteName ?: source.url,
                                style = chatTypography().caption,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            source.pageAge?.takeIf { it.isNotBlank() }?.let { date ->
                                Spacer(Modifier.width(6.dp))
                                Text("·", color = colors.textCaption, style = chatTypography().caption)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    date.take(16),
                                    style = chatTypography().caption,
                                    color = colors.textTertiary,
                                    maxLines = 1,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            source.title,
                            style = chatTypography().body.copy(fontSize = 16.sp, lineHeight = 24.sp),
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (source.snippet.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                source.snippet,
                                style = chatTypography().caption.copy(fontSize = 13.sp, lineHeight = 20.sp),
                                color = colors.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SiteLogo(source: SearchSource, size: androidx.compose.ui.unit.Dp) {
    val colors = chatColors()
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(colors.tagBg),
        contentAlignment = Alignment.Center,
    ) {
        if (!source.siteIcon.isNullOrBlank()) {
            AsyncImage(
                model = source.siteIcon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                (source.siteName ?: source.title).take(1).uppercase(),
                style = chatTypography().caption.copy(fontSize = 10.sp),
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun HtmlPreviewScreen(title: String, html: String, onBack: () -> Unit) {
    val colors = chatColors()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .background(colors.page)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = colors.textPrimary)
            }
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = chatTypography().title,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/html"
                    putExtra(Intent.EXTRA_TEXT, html)
                    putExtra(Intent.EXTRA_SUBJECT, title)
                }
                context.startActivity(Intent.createChooser(send, "分享 HTML"))
            }) {
                Icon(Icons.Rounded.Share, "分享", tint = colors.textPrimary)
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = WebViewClient()
                    loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                }
            },
        )
    }
}

@Composable
private fun ImageViewerScreen(path: String, onBack: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                )
                .transformable(
                    state = rememberTransformableState { zoomChange, panChange, _ ->
                        scale = (scale * zoomChange).coerceIn(1f, 5f)
                        offsetX += panChange.x
                        offsetY += panChange.y
                    }
                )
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .align(Alignment.TopStart)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
        ) {
            Icon(Icons.Rounded.Close, "关闭", tint = Color.White)
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    return this.then(
        Modifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    )
}
