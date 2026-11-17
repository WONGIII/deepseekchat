package com.wongiii.chat.feature

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
import kotlin.math.abs
import com.wongiii.chat.data.model.ChatMessage
import com.wongiii.chat.design.EmptyDots
import com.wongiii.chat.design.ExtraIcons
import com.wongiii.chat.design.WebIcons
import com.wongiii.chat.design.chatColors
import com.wongiii.chat.design.chatTypography

@Composable
fun AssistantMessageRow(
    message: ChatMessage,
    streaming: Boolean,
    reading: Boolean = false,
    readingStatus: String? = null,
    open: Boolean = true,
    onOpenChange: (Boolean) -> Unit = {},
    branchIndex: Int = 0,
    branchCount: Int = 1,
    onCopy: (String) -> Unit,
    onRegenerate: (String) -> Unit,
    onContinue: (String) -> Unit,
    onPrevBranch: () -> Unit,
    onNextBranch: () -> Unit,
    onReadAloud: (String, String) -> Unit,
    onOpenSearch: (String, List<SearchSource>) -> Unit,
    onOpenPreview: (String, String) -> Unit,
    searchSummaryModifier: Modifier = Modifier,
    searchCitationsModifier: Modifier = Modifier,
) {
    val colors = chatColors()
    val openSummary: (List<SearchSource>) -> Unit = { sources ->
        onOpenSearch("search:${message.id}:summary", sources)
    }
    val openCitations: (List<SearchSource>) -> Unit = { sources ->
        onOpenSearch("search:${message.id}:citations", sources)
    }
    val thinkingBlocks = message.blocks.filter { it.type == "thinking" && it.thinking.orEmpty().isNotBlank() }
    val textBlocks = message.blocks.filter { it.type == "text" && it.text.orEmpty().isNotBlank() && !it.isTemplate }
    val templateBlocks = message.blocks.filter { it.type == "text" && it.isTemplate }
    val searchSteps = remember(message.blocks) { message.searchSteps() }
    val sources = remember(message.blocks) { message.searchSources() }
    val answer = (textBlocks + templateBlocks).joinToString("\n\n") { it.text.orEmpty() }
    val showTitle = thinkingBlocks.isNotEmpty() || (message.thinking == true && streaming)
    val elapsed = thinkingBlocks.mapNotNull { it.elapsedSeconds }.sum()
    val title = when {
        streaming && answer.isBlank() -> "正在思考"
        elapsed > 0 -> "已思考（用时 $elapsed 秒）"
        else -> "已思考"
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        if (showTitle || searchSteps.isNotEmpty()) {
            if (showTitle) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenChange(!open) }
                        .padding(vertical = 5.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        WebIcons.Think16,
                        null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    val active = streaming && answer.isBlank()
                    val shimmer = androidx.compose.animation.core.rememberInfiniteTransition(label = "thinkTitle")
                    val titleAlpha by shimmer.animateFloat(
                        initialValue = if (active) 0.55f else 1f,
                        targetValue = 1f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(900, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "thinkTitleAlpha",
                    )
                    Text(
                        text = title,
                        style = chatTypography().markdown.copy(fontSize = 16.sp, lineHeight = 24.sp),
                        color = colors.textSecondary.copy(alpha = titleAlpha),
                    )
                    Spacer(Modifier.width(6.dp))
                    val chevronAngle by animateFloatAsState(
                        targetValue = if (open) 0f else -90f,
                        animationSpec = tween(160, easing = com.wongiii.chat.design.Motion.OutSoft),
                        label = "chevron",
                    )
                    Icon(
                        WebIcons.ChevronDown14,
                        null,
                        tint = colors.textTertiary,
                        modifier = Modifier
                            .size(14.dp)
                            .rotate(chevronAngle),
                    )
                }
            }
            AnimatedVisibility(
                visible = !showTitle || open,
                enter = fadeIn(tween(240)) + expandVertically(
                    animationSpec = tween(340, easing = com.wongiii.chat.design.Motion.OutSoft),
                    expandFrom = Alignment.Top,
                ),
                exit = fadeOut(tween(160)) + shrinkVertically(
                    animationSpec = tween(260, easing = com.wongiii.chat.design.Motion.InOutSoft),
                    shrinkTowards = Alignment.Top,
                ),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = if (searchSteps.isEmpty()) 10.dp else 2.dp),
                ) {
                    searchSteps.forEach { step ->
                        SearchStepRow(
                            step,
                            streaming,
                            message.status,
                            openSummary,
                            modifier = Modifier.padding(start = 22.dp),
                        )
                    }
                    thinkingBlocks.forEachIndexed { index, block ->
                        ThinkingBlockRow(active = streaming) {
                            val fadeChars = rememberStreamingFade(
                                key = message.id + "-think-" + index,
                                text = block.thinking.orEmpty(),
                                streaming = streaming && index == thinkingBlocks.lastIndex,
                            )
                            MarkdownView(
                                text = block.thinking.orEmpty(),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                compact = true,
                                fadeChars = fadeChars,
                                onOpenHtml = null,
                            )
                        }
                    }
                }
            }
        }
        textBlocks.forEachIndexed { index, block ->
            val fadeChars = rememberStreamingFade(
                key = message.id + "-text-" + index,
                text = block.text.orEmpty(),
                streaming = streaming && index == textBlocks.lastIndex,
            )
            MarkdownView(
                text = block.text.orEmpty(),
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                onOpenHtml = onOpenPreview,
                fadeChars = fadeChars,
            )
            if (block.citations.isNotEmpty()) {
                CitationChips(block.citations, sources, openCitations, searchCitationsModifier)
            }
        }
        if (sources.isNotEmpty() && !streaming) {
            SearchSummaryChip(sources, openSummary, searchSummaryModifier)
        }
        if (templateBlocks.isNotEmpty()) {
            MarkdownView(
                text = templateBlocks.joinToString("\n\n") { it.text.orEmpty() },
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            )
        }
        if (answer.isBlank() && streaming) {
            Box(Modifier.padding(vertical = 12.dp)) { EmptyDots() }
        }
        message.error?.let { error ->
            Text(
                text = error.message,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp),
                color = colors.danger,
                style = chatTypography().label,
            )
        }
        if (!streaming) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (branchCount > 1) {
                    BranchSwitcher(branchIndex, branchCount, onPrevBranch, onNextBranch)
                    Spacer(Modifier.width(6.dp))
                }
                MessageAction(ExtraIcons.Copy16, "复制", onClick = { onCopy(answer) })
                if (answer.isNotBlank()) {
                    IconButton(
                        onClick = { onReadAloud(message.id, answer) },
                        modifier = Modifier.size(34.dp),
                    ) {
                        if (reading) {
                            ReadingBars(loading = readingStatus == "loading")
                        } else {
                            Icon(ExtraIcons.Play16, "朗读", tint = colors.textTertiary, modifier = Modifier.size(17.dp))
                        }
                    }
                }
                MessageAction(ExtraIcons.Refresh16, "重新生成", onClick = { onRegenerate(message.id) })
                Spacer(Modifier.weight(1f))
                if (message.status == "stopped" || message.stopReason == "max_tokens") {
                    TextButton(onClick = { onContinue(message.id) }) {
                        Text("继续生成", style = chatTypography().caption)
                    }
                }
            }
        }
    }
}

@Composable
private fun CitationChips(
    citations: List<com.wongiii.chat.data.model.TextCitation>,
    sources: List<SearchSource>,
    onOpen: (List<SearchSource>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = chatColors()
    Row(
        modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        citations.forEach { citation ->
            val index = sources.indexOfFirst { it.url == citation.url }
            if (index < 0) return@forEach
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onOpen(listOf(sources[index])) },
                color = colors.tagBg,
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    "${index + 1}",
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    color = colors.accent,
                    style = chatTypography().caption.copy(fontSize = 11.sp),
                )
            }
        }
    }
}

@Composable
private fun rememberStreamingFade(key: String, text: String, streaming: Boolean): Int {
    val previous = rememberSaveable(key) { mutableStateOf("") }
    val fadeChars = remember(text, streaming) {
        if (streaming) {
            (text.length - commonPrefix(previous.value, text)).coerceAtLeast(0)
        } else {
            0
        }
    }
    LaunchedEffect(text) { previous.value = text }
    return fadeChars
}

private fun commonPrefix(a: String, b: String): Int {
    var i = 0
    while (i < a.length && i < b.length && a[i] == b[i]) i++
    return i
}

@Composable
private fun ThinkingBlockRow(
    active: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = chatColors()
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = colors.textCaption.copy(alpha = 0.45f),
                    start = Offset(7.5.dp.toPx(), 10.dp.toPx()),
                    end = Offset(7.5.dp.toPx(), size.height - 6.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 22.dp)) {
            content()
        }
        PulsingDot(
            active = active,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 5.dp, y = 4.dp),
        )
    }
}

@Composable
private fun PulsingDot(active: Boolean, modifier: Modifier = Modifier) {
    val colors = chatColors()
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "thinkDot")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (active) 1.3f else 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotScale",
    )
    Box(
        modifier
            .size(5.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (active) colors.accent else colors.textTertiary),
    )
}

@Composable
private fun ReadingBars(loading: Boolean) {
    Crossfade(
        targetState = loading,
        animationSpec = tween(150, easing = com.wongiii.chat.design.Motion.OutSoft),
        label = "ttsBars",
    ) { isLoading ->
        if (isLoading) LoadingBars() else PlayingBars()
    }
}

/** 与网页一致的加载态：四根静态灰条，一道暗色扫光从左往右掠过。 */
@Composable
private fun LoadingBars() {
    val colors = chatColors()
    val transition = rememberInfiniteTransition(label = "ttsLoading")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ttsSweep",
    )
    Canvas(Modifier.size(16.dp)) {
        val unit = size.width / 16f
        val barXs = listOf(1f, 5f, 9f, 13f)
        val barHeight = 4f * unit
        val top = (16f * unit - barHeight) / 2f
        val radius = CornerRadius(unit)
        val sweepX = (-7f + sweep * 23f) * unit
        val base = colors.textTertiary
        barXs.forEach { x ->
            val centerX = (x + 1f) * unit
            val influence = (1f - abs(centerX - sweepX) / (7f * unit)).coerceIn(0f, 1f)
            drawRoundRect(
                color = lerp(base, Color.Black, influence * 0.42f),
                topLeft = Offset(x * unit, top),
                size = Size(2f * unit, barHeight),
                cornerRadius = radius,
            )
        }
    }
}

/** 朗读态：完全照网页的 16×16 四竖条，相位 0/.25/.5/.75，周期 1.6s。 */
@Composable
private fun PlayingBars() {
    val colors = chatColors()
    val transition = rememberInfiniteTransition(label = "ttsPlaying")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ttsProgress",
    )
    val ease = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    Canvas(Modifier.size(16.dp)) {
        val unit = size.width / 16f
        val barXs = listOf(1f, 5f, 9f, 13f)
        val phases = listOf(0f, 0.25f, 0.5f, 0.75f)
        barXs.forEachIndexed { index, x ->
            val t = (progress + phases[index]) % 1f
            val wave = abs(2f * t - 1f)
            val height = (4f + 10f * ease.transform(wave)) * unit
            drawRoundRect(
                color = colors.accent,
                topLeft = Offset(x * unit, (16f * unit - height) / 2f),
                size = Size(2f * unit, height),
                cornerRadius = CornerRadius(unit),
            )
        }
    }
}

@Composable
private fun SearchStepRow(
    step: SearchStep,
    streaming: Boolean,
    status: String,
    onOpen: (List<SearchSource>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = chatColors()
    val wip = step.status == "wip"
    val failed = step.status == "failed"
    val count = step.results.size
    val clickable = !wip && count > 0
    var queryIndex by remember(step.key) { mutableIntStateOf(0) }
    LaunchedEffect(step.key, wip, step.queries) {
        if (wip && step.queries.size > 1) {
            while (true) {
                delay(2000)
                queryIndex = (queryIndex + 1) % step.queries.size
            }
        }
    }
    val label = when {
        wip && step.queries.isNotEmpty() -> "搜索 ${step.queries[queryIndex % step.queries.size]}"
        wip -> "搜索中..."
        failed && streaming && status == "stopped" -> "搜索已停止"
        failed -> "联网搜索暂不可用"
        count == 0 -> "未搜索到合适内容"
        else -> "已阅读 $count 个网页"
    }
    val wipTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "searchWip")
    val wipAlpha by wipTransition.animateFloat(
        initialValue = if (wip) 0.55f else 1f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "searchWipAlpha",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .then(if (clickable) Modifier.clickable { onOpen(step.results) } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            WebIcons.Search16,
            null,
            tint = if (failed) colors.danger else colors.textTertiary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Crossfade(targetState = label, animationSpec = tween(180), label = "searchLabel") { text ->
            Text(
                text,
                style = chatTypography().markdown.copy(fontSize = 14.sp, lineHeight = 22.sp),
                color = (if (failed) colors.danger else colors.textSecondary).copy(alpha = wipAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (clickable) {
            Spacer(Modifier.width(8.dp))
            SiteThumbnails(step.results)
        }
    }
}

@Composable
private fun SearchSummaryChip(
    sources: List<SearchSource>,
    onOpen: (List<SearchSource>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = chatColors()
    Surface(
        modifier = modifier
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable { onOpen(sources) },
        color = colors.card,
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.borderStrong),
    ) {
        Row(
            Modifier.padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SiteThumbnails(sources, size = 20.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (sources.size == 1) "1 个网页" else "${sources.size} 个网页",
                style = chatTypography().caption,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
fun SiteThumbnails(sources: List<SearchSource>, size: androidx.compose.ui.unit.Dp = 16.dp) {
    val colors = chatColors()
    Row(horizontalArrangement = Arrangement.spacedBy(-4.dp)) {
        sources.take(4).forEach { source ->
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(colors.tagBg)
                    .border(1.dp, colors.card, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (!source.siteIcon.isNullOrBlank()) {
                    AsyncImage(
                        model = source.siteIcon,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(colors.tagBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (source.siteName ?: source.title).take(1).uppercase(),
                            style = chatTypography().caption.copy(fontSize = 9.sp),
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = chatColors().textTertiary,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun BranchSwitcher(
    index: Int,
    count: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = chatColors()
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev, enabled = index > 0, modifier = Modifier.size(26.dp)) {
            Icon(
                Icons.Rounded.ChevronLeft,
                "上一条版本",
                tint = if (index > 0) colors.textSecondary else colors.textCaption,
                modifier = Modifier.size(16.dp),
            )
        }
        Text("${index + 1} / $count", style = chatTypography().caption, color = colors.textTertiary)
        IconButton(onClick = onNext, enabled = index < count - 1, modifier = Modifier.size(26.dp)) {
            Icon(
                Icons.Rounded.ChevronRight,
                "下一条版本",
                tint = if (index < count - 1) colors.textSecondary else colors.textCaption,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
