package com.wongiii.chat.feature

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardVoice
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wongiii.chat.data.local.sessionDateLabel
import com.wongiii.chat.data.model.ChatMessage
import com.wongiii.chat.data.model.ChatSession
import com.wongiii.chat.data.model.FileAttachment
import com.wongiii.chat.data.model.SessionSummary
import com.wongiii.chat.data.model.TextCitation
import com.wongiii.chat.data.model.path
import com.wongiii.chat.design.BrandMark
import com.wongiii.chat.design.BrandLockup
import com.wongiii.chat.design.CardRadius
import com.wongiii.chat.design.CircleIconButton
import com.wongiii.chat.design.EmptyDots
import com.wongiii.chat.design.PageHorizontalPadding
import com.wongiii.chat.design.PressableScale
import com.wongiii.chat.design.TopBar
import com.wongiii.chat.design.chatColors
import com.wongiii.chat.design.chatTypography
import com.wongiii.chat.design.WebIcons
import com.wongiii.chat.design.ExtraIcons
import com.wongiii.chat.design.ComposerRadius
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.util.Locale

@Composable
fun WelcomeScreen(
    settings: com.wongiii.chat.data.local.AppSettings,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    onToggleMute: () -> Unit,
    composer: @Composable () -> Unit,
) {
    val colors = chatColors()
    Box(Modifier.fillMaxSize()) {
    ChatBackgroundLayer(settings)
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = null,
            onMenu = onMenu,
            onNewChat = onNewChat,
            muted = settings.readAloudMuted,
            onToggleMute = onToggleMute,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-48).dp)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BrandMark(size = 46.dp, color = colors.accent)
                Spacer(Modifier.height(18.dp))
                Text(
                    text = remember { WelcomeGreetings.random() },
                    style = chatTypography().greeting.copy(fontSize = 24.sp, lineHeight = 32.sp),
                    color = colors.textPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                composer()
            }
        }
    }
    }
}

@Composable
fun ChatScreen(
    session: ChatSession,
    streaming: Boolean,
    readingMessageId: String?,
    readingStatus: String?,
    autoCollapseThinking: Boolean,
    thinkingOpen: Map<String, Boolean>,
    onSetThinkingOpen: (String, Boolean) -> Unit,
    settings: com.wongiii.chat.data.local.AppSettings,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    onToggleMute: () -> Unit,
    onCopy: (String) -> Unit,
    onEdit: (ChatMessage, String) -> Unit,
    onRegenerate: (String) -> Unit,
    onContinue: (String) -> Unit,
    onSwitchBranch: (String, Int) -> Unit,
    onReadAloud: (String, String) -> Unit,
    onOpenSearch: (String, List<SearchSource>) -> Unit,
    onOpenPreview: (String, String) -> Unit,
    onOpenImage: (String) -> Unit,
    searchChipModifier: @Composable (String) -> Modifier = { Modifier },
    chatListState: LazyListState,
    chatListReady: Boolean,
    onChatListReady: () -> Unit,
    entranceSeen: MutableSet<String>,
    animateInitialMessages: Boolean,
    composer: @Composable () -> Unit,
) {
    val colors = chatColors()
    val path = remember(session.messages, session.rootMessageId) { session.path() }
    remember(session.id, entranceSeen, animateInitialMessages) {
        if (!animateInitialMessages) {
            entranceSeen.addAll(path.map { it.id })
        }
    }
    val listState = chatListState
    var autoFollow by remember(session.id) { mutableStateOf(!listState.canScrollForward) }
    var pendingGrowth by remember(session.id) { mutableStateOf(0f) }
    var measuredLastHeight by remember(session.id) {
        mutableStateOf(
            listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == path.lastIndex }
                ?.size ?: 0
        )
    }
    var scrollReady by remember(session.id) { mutableStateOf(chatListReady) }
    var lastPathSize by remember(session.id) {
        mutableStateOf(if (chatListReady) path.size else 0)
    }
    val isDragged by listState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(session.id, path.size) {
        if (path.isEmpty()) {
            lastPathSize = 0
            return@LaunchedEffect
        }
        val grew = path.size > lastPathSize
        lastPathSize = path.size
        if (!scrollReady || grew) {
            delay(40)
            listState.scrollToItem(path.lastIndex)
            listState.scrollBy(100000f)
            measuredLastHeight = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == path.lastIndex }
                ?.size ?: 0
            scrollReady = true
            onChatListReady()
        }
    }
    LaunchedEffect(isDragged) {
        if (isDragged) {
            autoFollow = false
        } else if (!listState.canScrollForward) {
            autoFollow = true
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { !listState.canScrollForward }.collect { atBottom ->
            if (atBottom) autoFollow = true
        }
    }
    LaunchedEffect(session.id, streaming) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == path.lastIndex }
                ?.size ?: 0
        }.collect { height ->
            if (height <= 0 || !scrollReady) return@collect
            val growth = height - measuredLastHeight
            val hadHeight = measuredLastHeight > 0
            measuredLastHeight = height
            if (hadHeight && growth > 0 && streaming && autoFollow) {
                pendingGrowth += growth.toFloat()
            }
        }
    }
    LaunchedEffect(autoFollow, streaming) {
        if (!autoFollow || !streaming) {
            pendingGrowth = 0f
            return@LaunchedEffect
        }
        while (autoFollow && streaming) {
            if (pendingGrowth > 0.5f) {
                val delta = pendingGrowth
                pendingGrowth = 0f
                listState.animateScrollBy(
                    delta,
                    tween(190, easing = com.wongiii.chat.design.Motion.OutSoft),
                )
            } else {
                delay(16)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    ChatBackgroundLayer(settings)
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = session.title.ifBlank { "新对话" },
            onMenu = onMenu,
            onNewChat = onNewChat,
            muted = settings.readAloudMuted,
            onToggleMute = onToggleMute,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 10.dp,
                    bottom = 18.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(path, key = { it.id }) { message ->
                    val animateEntrance = entranceSeen.add(message.id)
                    MessageEntrance(message.id, animateEntrance) {
                    if (message.isUser) {
                        UserMessageRow(
                            message = message,
                            onCopy = onCopy,
                            onEdit = onEdit,
                            onOpenImage = onOpenImage,
                        )
                    } else {
                        val siblings = session.messages.filter { it.parentId == message.parentId }
                        val branchIndex = siblings.indexOfFirst { it.id == message.id }.coerceAtLeast(0)
                        val isStreamingMessage = streaming && path.lastOrNull()?.id == message.id
                        val defaultOpen = isStreamingMessage || !autoCollapseThinking
                        val summaryModifier = searchChipModifier("search:${message.id}:summary")
                        val citationsModifier = searchChipModifier("search:${message.id}:citations")
                        AssistantMessageRow(
                            message = message,
                            streaming = isStreamingMessage,
                            reading = readingMessageId == message.id,
                            readingStatus = readingStatus,
                            open = thinkingOpen[message.id] ?: defaultOpen,
                            onOpenChange = { open ->
                                onSetThinkingOpen(message.id, open)
                            },
                            branchIndex = branchIndex,
                            branchCount = siblings.size.coerceAtLeast(1),
                            onCopy = onCopy,
                            onRegenerate = onRegenerate,
                            onContinue = onContinue,
                            onPrevBranch = { onSwitchBranch(message.id, -1) },
                            onNextBranch = { onSwitchBranch(message.id, 1) },
                            onReadAloud = onReadAloud,
                            onOpenSearch = onOpenSearch,
                            onOpenPreview = onOpenPreview,
                            searchSummaryModifier = summaryModifier,
                            searchCitationsModifier = citationsModifier,
                        )
                    }
                    }
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, colors.page.copy(alpha = 0.92f), colors.page)
                    )
                )
                .padding(horizontal = 12.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            composer()
        }
    }
    }
}

@Composable
private fun MessageEntrance(id: String, animate: Boolean, content: @Composable () -> Unit) {
    var visible by remember(id) { mutableStateOf(!animate) }
    LaunchedEffect(id, animate) {
        if (animate) visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(260, easing = com.wongiii.chat.design.Motion.OutSoft)) +
            slideInVertically(
                animationSpec = tween(320, easing = com.wongiii.chat.design.Motion.OutSoft),
                initialOffsetY = { it / 5 },
            ) +
            scaleIn(
                initialScale = 0.99f,
                animationSpec = tween(320, easing = com.wongiii.chat.design.Motion.OutSoft),
            ),
    ) {
        content()
    }
}

@Composable
private fun chatBackground(settings: com.wongiii.chat.data.local.AppSettings): Brush {
    val colors = chatColors()
    return when (settings.backgroundStyle) {
        com.wongiii.chat.data.model.BackgroundStyle.GRADIENT -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to colors.accentSoft,
                0.38f to colors.accentSoft.copy(alpha = 0.55f),
                0.72f to colors.page,
                1.0f to colors.page,
            ),
        )
        else -> androidx.compose.ui.graphics.SolidColor(colors.page)
    }
}

@Composable
private fun ChatBackgroundLayer(settings: com.wongiii.chat.data.local.AppSettings) {
    val colors = chatColors()
    Box(Modifier.fillMaxSize().background(chatBackground(settings))) {
        if (settings.backgroundStyle == com.wongiii.chat.data.model.BackgroundStyle.CUSTOM) {
            val path = settings.backgroundImagePath
            if (!path.isNullOrBlank()) {
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            if (colors.dark) Color.Black.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.18f)
                        ),
                )
            }
        }
    }
}

@Composable
fun UserMessageRow(
    message: ChatMessage,
    onCopy: (String) -> Unit,
    onEdit: (ChatMessage, String) -> Unit,
    onOpenImage: (String) -> Unit,
) {
    val colors = chatColors()
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(message.content) }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        if (message.files.isNotEmpty()) {
            Row(
                Modifier.padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                message.files.take(4).forEach { file ->
                    if (file.isImage && file.localPath != null) {
                        AsyncImage(
                            model = File(file.localPath),
                            contentDescription = file.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(0.5.dp, colors.border, RoundedCornerShape(14.dp))
                                .clickable { onOpenImage(file.localPath) },
                        )
                    } else {
                        FileChip(file)
                    }
                }
            }
        }
        if (editing) {
            Surface(
                color = colors.input,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.accent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(10.dp)) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        textStyle = chatTypography().body.copy(color = colors.textPrimary),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp, max = 160.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        TextButton(onClick = {
                            editing = false
                            draft = message.content
                        }) { Text("取消") }
                        TextButton(onClick = {
                            editing = false
                            onEdit(message, draft)
                        }) { Text("保存并重新生成") }
                    }
                }
            }
        } else {
            Surface(
                color = colors.bubble,
                shape = RoundedCornerShape(com.wongiii.chat.design.BubbleRadius),
                border = if (colors.bubbleBorder != Color.Transparent) {
                    androidx.compose.foundation.BorderStroke(1.dp, colors.bubbleBorder)
                } else null,
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = chatTypography().body,
                    color = colors.textPrimary,
                )
            }
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onCopy(message.content) }, modifier = Modifier.size(30.dp)) {
                    Icon(ExtraIcons.Copy16, "复制", tint = colors.textTertiary, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { editing = true }, modifier = Modifier.size(30.dp)) {
                    Icon(ExtraIcons.Edit16, "编辑", tint = colors.textTertiary, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(9.dp))
    }
}

@Composable
private fun FileChip(file: FileAttachment) {
    val colors = chatColors()
    Surface(
        color = colors.card,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.border),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (file.name.lowercase().endsWith("pdf")) Icons.Rounded.PictureAsPdf else Icons.Rounded.Description,
                null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = file.name,
                style = chatTypography().caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 130.dp),
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Composer(
    settings: com.wongiii.chat.data.local.AppSettings,
    draftFiles: List<FileAttachment>,
    streaming: Boolean,
    text: String,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onToggleThinking: () -> Unit,
    onToggleSearch: () -> Unit,
    onAttach: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceEnd: (Boolean) -> Unit,
    onVoiceCancelArmed: (Boolean) -> Unit,
    sheetOpen: Boolean,
    onToggleSheet: () -> Unit,
    onRemoveFile: (String) -> Unit,
    onRetryFile: (String) -> Unit,
) {
    val colors = chatColors()
    val canSend = text.isNotBlank() || draftFiles.isNotEmpty()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var voiceMode by rememberSaveable { mutableStateOf(false) }
    var pendingKeyboardFocus by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    BackHandler(enabled = focused && imeVisible) {
        keyboard?.hide()
        focusManager.clearFocus()
    }
    var hadIme by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            hadIme = true
        } else if (hadIme) {
            hadIme = false
            if (focused) focusManager.clearFocus()
        }
    }
    LaunchedEffect(voiceMode, pendingKeyboardFocus) {
        if (!voiceMode && pendingKeyboardFocus) {
            pendingKeyboardFocus = false
            // 等输入框重新进入组合后再请求焦点，避免 FocusRequester 尚未挂载导致崩溃。
            delay(60)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.input,
        shape = RoundedCornerShape(ComposerRadius),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            if (colors.dark) Color(0x1AFFFFFF) else Color(0x1A000000),
        ),
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(12.dp)) {
            if (draftFiles.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    draftFiles.forEach { file ->
                        DraftFile(file, onRemove = { onRemoveFile(file.id) }, onRetry = { onRetryFile(file.id) })
                    }
                }
            }
            if (voiceMode) {
                HoldToTalkArea(
                    onStart = {
                        keyboard?.hide()
                        onVoiceStart()
                    },
                    onEnd = onVoiceEnd,
                    onCancelArmed = onVoiceCancelArmed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 28.dp, max = 180.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused },
                    textStyle = chatTypography().body.copy(color = colors.textPrimary),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text(
                                text = "发消息或按住说话",
                                style = chatTypography().body,
                                color = colors.textCaption,
                            )
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TogglePill("深度思考", settings.thinking, WebIcons.Think14, onToggleThinking)
                    TogglePill("智能搜索", settings.search, WebIcons.Globe14, onToggleSearch)
                }
                IconButton(
                    onClick = if (sheetOpen) onToggleSheet else onAttach,
                    modifier = Modifier.size(34.dp),
                ) {
                    if (sheetOpen) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .border(1.2.dp, colors.textPrimary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Close, "关闭附件面板", tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Icon(Icons.Outlined.AddCircleOutline, "添加图片或文件", tint = colors.textPrimary, modifier = Modifier.size(24.dp))
                    }
                }
                if (streaming) {
                    Surface(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onStop),
                        color = colors.accent,
                        shape = CircleShape,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(ExtraIcons.Stop16, "停止", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                } else if (voiceMode) {
                    IconButton(
                        onClick = {
                            voiceMode = false
                            pendingKeyboardFocus = true
                        },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(Icons.Outlined.Keyboard, "切换到键盘输入", tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                    }
                } else if (text.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { onSend(text) },
                        color = colors.accent,
                        shape = CircleShape,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(WebIcons.Send16, "发送", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                } else {
                    IconButton(
                        onClick = {
                            voiceMode = true
                            keyboard?.hide()
                            focusManager.clearFocus()
                        },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(WebIcons.Mic16, "语音输入", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TogglePill(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val colors = chatColors()
    Surface(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = if (selected) colors.accentSoft else Color.Transparent,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) colors.accentBorder else colors.border,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) colors.accent else colors.textTertiary,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                style = chatTypography().caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                color = if (selected) colors.accent else colors.textSecondary,
            )
        }
    }
}

@Composable
private fun DraftFile(
    file: FileAttachment,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = chatColors()
    Box(
        Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.inputFilled)
            .border(0.5.dp, if (file.extraction == "error") colors.danger else colors.border, RoundedCornerShape(14.dp)),
    ) {
        if (file.isImage && file.localPath != null) {
            AsyncImage(
                model = File(file.localPath),
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(6.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.Description, null, tint = colors.accent, modifier = Modifier.size(22.dp))
                Text(
                    text = file.name,
                    style = chatTypography().caption.copy(fontSize = 9.sp),
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        if (file.extraction == "pending") {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            }
        }
        if (file.extraction == "error") {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.danger.copy(alpha = 0.16f))
                    .clickable(onClick = onRetry),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Refresh, "重试", tint = colors.danger, modifier = Modifier.size(20.dp))
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(17.dp)
                .clip(CircleShape)
                .background(colors.toastBg)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, "移除", tint = Color.White, modifier = Modifier.size(11.dp))
        }
    }
}

@Composable
fun DrawerPanel(
    sessions: List<SessionSummary>,
    searchHits: List<SessionSummary>?,
    searchQuery: String,
    activeId: String?,
    mode: DataMode,
    username: String?,
    onSearch: (String) -> Unit,
    onSelect: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = chatColors()
    val shown = searchHits ?: sessions
    Surface(
        modifier = modifier
            .width(320.dp)
            .fillMaxHeight(),
        color = colors.drawer,
        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        shadowElevation = 14.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .height(40.dp),
                color = colors.inputFilled,
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Icon(Icons.Rounded.Search, null, tint = colors.textTertiary, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearch,
                        modifier = Modifier.weight(1f),
                        textStyle = chatTypography().label.copy(color = colors.textPrimary),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text("搜索对话内容…", style = chatTypography().label, color = colors.textCaption)
                            }
                            inner()
                        },
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearch("") }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Rounded.Close, "清空", tint = colors.textTertiary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp),
            ) {
                val groups = shown.groupBy { sessionDateLabel(it.updatedAt) }
                groups.forEach { (label, rows) ->
                    item(key = "label-$label") {
                        Text(
                            text = label,
                            modifier = Modifier.padding(start = 22.dp, top = 12.dp, bottom = 4.dp),
                            color = colors.textTertiary,
                            style = chatTypography().caption,
                        )
                    }
                    items(rows, key = { it.id }) { summary ->
                        DrawerSessionRow(
                            summary = summary,
                            selected = summary.id == activeId,
                            onSelect = { onSelect(summary.id) },
                            onRename = { onRename(summary.id) },
                            onDelete = { onDelete(summary.id) },
                        )
                    }
                }
            }
            HorizontalDivider(color = colors.divider)
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .clickable(onClick = onSettings)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (username ?: "本").take(1).uppercase(Locale.getDefault()),
                        color = colors.accent,
                        style = chatTypography().label.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = username ?: "本机使用",
                        style = chatTypography().label,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = if (mode == DataMode.CLOUD) "已连接服务器" else "数据保存在本机",
                        style = chatTypography().caption,
                        color = colors.textTertiary,
                    )
                }
                Icon(Icons.Rounded.MoreHoriz, "账户", tint = colors.textTertiary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun DrawerSessionRow(
    summary: SessionSummary,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = chatColors()
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) colors.accentSoft else Color.Transparent)
                .clickable(onClick = onSelect)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = summary.title.ifBlank { "新对话" },
                modifier = Modifier.weight(1f),
                style = chatTypography().body,
                color = if (selected) colors.accent else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.MoreVert, "会话菜单", tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp)) },
                        onClick = { menu = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = colors.danger, modifier = Modifier.size(18.dp)) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名会话") },
        text = {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                textStyle = chatTypography().body,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(chatColors().inputFilled, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun DeleteDialog(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除会话") },
        text = { Text("确定删除“$title”吗？此操作无法撤销。") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("删除", color = chatColors().danger) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}


object WelcomeGreetings {
    val all = listOf(
        "嗨！今天想聊些什么？",
        "你好，今天也要照顾好自己哦",
        "今天想从哪里开始？",
        "很高兴见到你，想聊点什么？",
        "今天有什么新鲜事吗？",
        "我在这儿，随时可以开始。",
        "想聊聊今天的什么？",
        "新的一天，从一句话开始。",
        "你好呀，今天过得怎么样？",
        "有什么想法，我们一起理一理。",
    )
    fun random(): String = all.random()
}
