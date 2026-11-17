package com.wongiii.chat.feature

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wongiii.chat.data.model.TextCitation
import com.wongiii.chat.design.CardRadius
import com.wongiii.chat.design.chatColors
import com.wongiii.chat.design.chatTypography
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.ThematicBreak
import org.commonmark.node.Text as MdText
import org.commonmark.parser.Parser
import kotlin.math.roundToInt

private val markdownParser: Parser = Parser.builder()
    .extensions(listOf(TablesExtension.create(), StrikethroughExtensionHolder.create(), TaskListItemsExtension.create()))
    .build()

/** commonmark 扩展工厂需要单独引用，避免长链调用散落在组合里。 */
private object StrikethroughExtensionHolder {
    fun create() = org.commonmark.ext.gfm.strikethrough.StrikethroughExtension.create()
}

/** 已完成消息的 AST 缓存，避免列表滚动回收后重复解析长 Markdown。 */
private object MarkdownDocumentCache {
    private const val MAX_ENTRIES = 80
    private val entries = object : LinkedHashMap<String, Node>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Node>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(text: String): Node =
        entries[text] ?: markdownParser.parse(normalizePipeTables(text)).also { entries[text] = it }
}

private val tableDelimiterRow = Regex(
    """^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$"""
)

/**
 * 模型经常把表格紧贴在上一段文字后面，或者用全角竖线输出。
 * 这里补上表格前后的空行并统一竖线，保证 commonmark 能识别成 TableBlock。
 */
internal fun normalizePipeTables(text: String): String {
    if (!text.contains('|') && !text.contains('｜')) return text
    val source = text.replace('｜', '|')
    val lines = source.lines()
    val result = mutableListOf<String>()
    var inFence = false
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            inFence = !inFence
            result += line
            index++
            continue
        }
        if (inFence || line.startsWith("    ")) {
            result += line
            index++
            continue
        }
        if (index + 1 < lines.size && isTableRow(line) && tableDelimiterRow.matches(lines[index + 1])) {
            if (result.isNotEmpty() && result.last().isNotBlank()) result += ""
            result += line
            result += lines[index + 1]
            index += 2
            while (index < lines.size && isTableRow(lines[index])) {
                result += lines[index]
                index++
            }
            if (index < lines.size && lines[index].isNotBlank()) result += ""
            continue
        }
        result += line
        index++
    }
    return result.joinToString("\n")
}

private fun isTableRow(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.contains('|') && !tableDelimiterRow.matches(line)
}

@Composable
fun MarkdownView(
    text: String,
    modifier: Modifier = Modifier,
    citations: List<TextCitation> = emptyList(),
    onOpenHtml: ((code: String, title: String) -> Unit)? = null,
    onCitation: ((TextCitation) -> Unit)? = null,
    compact: Boolean = false,
    fadeChars: Int = 0,
) {
    val colors = chatColors()
    val typography = chatTypography()
    val fadeProgress = remember { Animatable(1f) }
    LaunchedEffect(text, fadeChars) {
        if (fadeChars > 0) {
            fadeProgress.snapTo(0f)
            fadeProgress.animateTo(1f, tween(320, easing = LinearEasing))
        } else {
            fadeProgress.snapTo(1f)
        }
    }
    val body = if (compact) {
        typography.copy(
            markdown = typography.markdown.copy(fontSize = 14.sp, lineHeight = 24.sp),
            body = typography.body.copy(fontSize = 14.sp, lineHeight = 24.sp),
        )
    } else {
        typography
    }
    val document = remember(text, fadeChars) {
        if (fadeChars == 0) {
            MarkdownDocumentCache.get(text)
        } else {
            markdownParser.parse(normalizePipeTables(text))
        }
    }
    Column(modifier) {
        MarkdownNode(
            node = document,
            typography = body,
            onOpenHtml = onOpenHtml,
            fadeChars = fadeChars,
            fadeProgress = { fadeProgress.value },
        )
        if (citations.isNotEmpty() && onCitation != null) {
            CitationRow(citations, onCitation)
        }
    }
}

@Composable
private fun MarkdownNode(
    node: Node,
    typography: com.wongiii.chat.design.ChatTypography,
    onOpenHtml: ((String, String) -> Unit)?,
    fadeChars: Int = 0,
    fadeProgress: () -> Float = { 1f },
) {
    val colors = chatColors()
    var child = node.firstChild
    while (child != null) {
        when (child) {
            is Paragraph -> {
                InlineMarkdown(
                    node = child,
                    style = typography.markdown,
                    fadeChars = if (child.next == null) fadeChars else 0,
                    fadeProgress = fadeProgress,
                )
                Spacer(Modifier.height(12.dp))
            }
            is Heading -> {
                val level = child.level
                val size = when (level) {
                    1 -> 24.sp
                    2 -> 22.sp
                    3 -> 20.sp
                    else -> 16.sp
                }
                InlineMarkdown(
                    child,
                    typography.markdown.copy(
                        fontSize = size,
                        lineHeight = (size.value + 10).sp,
                        fontWeight = if (level <= 3) FontWeight.Bold else FontWeight.SemiBold,
                    ),
                    topPadding = if (level <= 3) 20.dp else 12.dp,
                    fadeChars = if (child.next == null) fadeChars else 0,
                    fadeProgress = fadeProgress,
                )
                Spacer(Modifier.height(if (level <= 3) 8.dp else 4.dp))
            }
            is BulletList -> LastBlockFade(child.next == null && fadeChars > 0, fadeProgress) {
                ListBlock(child, ordered = false, typography = typography)
            }
            is OrderedList -> LastBlockFade(child.next == null && fadeChars > 0, fadeProgress) {
                ListBlock(child, ordered = true, typography = typography)
            }
            is BlockQuote -> LastBlockFade(child.next == null && fadeChars > 0, fadeProgress) {
                Row(Modifier.padding(vertical = 8.dp)) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .background(colors.textCaption, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        MarkdownNode(child, typography, onOpenHtml)
                    }
                }
            }
            is FencedCodeBlock -> {
                val block = child
                LastBlockFade(block.next == null && fadeChars > 0, fadeProgress) {
                    CodeBlock(
                        code = block.literal.trimEnd(),
                        language = block.info?.trim()?.substringBefore(' ').orEmpty(),
                        onOpenHtml = onOpenHtml,
                    )
                }
            }
            is IndentedCodeBlock -> {
                val block = child
                LastBlockFade(block.next == null && fadeChars > 0, fadeProgress) {
                    CodeBlock(block.literal.trimEnd(), "", onOpenHtml)
                }
            }
            is ThematicBreak -> LastBlockFade(child.next == null && fadeChars > 0, fadeProgress) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp)
                        .height(0.5.dp)
                        .background(colors.divider)
                )
            }
            is TableBlock -> {
                val block = child
                LastBlockFade(block.next == null && fadeChars > 0, fadeProgress) {
                    TableBlockView(block, typography)
                }
            }
            else -> if (child is Paragraph || child is Heading) Unit
        }
        child = child.next
    }
}

@Composable
private fun LastBlockFade(
    active: Boolean,
    progress: () -> Float,
    content: @Composable () -> Unit,
) {
    if (active) {
        Box(Modifier.graphicsLayer(alpha = 0.15f + 0.85f * progress())) {
            content()
        }
    } else {
        content()
    }
}

@Composable
private fun InlineMarkdown(
    node: Node,
    style: TextStyle,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    fadeChars: Int = 0,
    fadeProgress: () -> Float = { 1f },
) {
    val colors = chatColors()
    val annotated = buildInline(node, colors.tagBg, colors.textPrimary, colors.accent)
    val display = if (fadeChars > 0) {
        buildAnnotatedString {
            append(annotated)
            val progress = fadeProgress()
            val start = (length - fadeChars).coerceAtLeast(0)
            for (index in start until length) {
                val offset = index - start
                val delay = (offset * 8).coerceAtMost(96) / 320f
                val local = ((progress - delay) / 0.6875f).coerceIn(0f, 1f)
                addStyle(
                    SpanStyle(color = colors.textPrimary.copy(alpha = 0.15f + 0.85f * local)),
                    start = index,
                    end = index + 1,
                )
            }
        }
    } else {
        annotated
    }
    if (topPadding > androidx.compose.ui.unit.Dp(0f)) Spacer(Modifier.height(topPadding))
    BasicText(
        text = display,
        modifier = Modifier.fillMaxWidth(),
        style = style.copy(color = colors.textPrimary),
    )
}

private fun buildInline(
    node: Node,
    codeBackground: Color,
    codeText: Color,
    linkColor: Color,
): AnnotatedString = buildAnnotatedString {
    fun walk(parent: Node) {
        var child = parent.firstChild
        while (child != null) {
            when (child) {
                is MdText -> append(child.literal)
                is SoftLineBreak -> append('\n')
                is HardLineBreak -> append('\n')
                is StrongEmphasis -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    walk(child)
                    pop()
                }
                is Emphasis -> {
                    pushStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                    walk(child)
                    pop()
                }
                is Strikethrough -> {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    walk(child)
                    pop()
                }
                is Code -> {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = codeText,
                            background = codeBackground,
                        )
                    )
                    append(child.literal)
                    pop()
                }
                is Link -> {
                    pushLink(
                        LinkAnnotation.Url(
                            child.destination,
                            TextLinkStyles(
                                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                            ),
                        )
                    )
                    walk(child)
                    pop()
                }
                is Image -> {
                    append("[图片]")
                }
                else -> walk(child)
            }
            child = child.next
        }
    }
    walk(node)
}

@Composable
private fun ListBlock(
    node: Node,
    ordered: Boolean,
    typography: com.wongiii.chat.design.ChatTypography,
) {
    var index = 1
    var child = node.firstChild
    while (child != null) {
        if (child is ListItem) {
            Row(Modifier.padding(start = 4.dp, top = 4.dp)) {
                Text(
                    text = if (ordered) "${index}. " else "• ",
                    style = typography.markdown,
                    color = chatColors().textSecondary,
                    modifier = Modifier.width(if (ordered) 22.dp else 16.dp),
                )
                Column(Modifier.weight(1f)) {
                    val itemChildren = child
                    MarkdownNode(itemChildren, typography, null)
                }
            }
            index++
        }
        child = child.next
    }
}

@Composable
private fun CodeBlock(
    code: String,
    language: String,
    onOpenHtml: ((String, String) -> Unit)?,
) {
    val colors = chatColors()
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1400)
            copied = false
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clip(RoundedCornerShape(CardRadius)),
        color = colors.codeBg,
        shape = RoundedCornerShape(CardRadius),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.border),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.codeBanner)
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = language.ifBlank { "code" },
                    style = chatTypography().caption.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textTertiary,
                    modifier = Modifier.weight(1f),
                )
                if (language.lowercase() in setOf("html", "htm")) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "运行 HTML",
                        tint = colors.textSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickableNoRipple { onOpenHtml?.invoke(code, extractHtmlTitle(code)) }
                            .padding(5.dp)
                            .size(18.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                }
                Icon(
                    if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = "复制",
                    tint = if (copied) colors.success else colors.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickableNoRipple {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                            copied = true
                        }
                        .padding(5.dp)
                        .size(18.dp),
                )
            }
            Text(
                text = code,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp),
                style = chatTypography().code,
                color = colors.textPrimary,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun TableBlockView(
    node: TableBlock,
    typography: com.wongiii.chat.design.ChatTypography,
) {
    val colors = chatColors()
    val rows = mutableListOf<TableRow>()
    var child = node.firstChild
    while (child != null) {
        when (child) {
            is TableHead, is TableBody -> {
                var row = child.firstChild
                while (row != null) {
                    if (row is TableRow) rows += row
                    row = row.next
                }
            }
            is TableRow -> rows += child
        }
        child = child.next
    }
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.card)
            .padding(1.dp),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(Modifier.background(if (rowIndex == 0) colors.codeBanner else colors.card)) {
                var cell = row.firstChild
                while (cell != null) {
                    if (cell is TableCell) {
                        val text = buildInline(cell, colors.tagBg, colors.textPrimary, colors.accent)
                        Text(
                            text = text,
                            modifier = Modifier
                                .width(150.dp)
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            style = typography.body.copy(
                                fontSize = 14.sp,
                                fontWeight = if (rowIndex == 0) FontWeight.Medium else FontWeight.Normal,
                            ),
                            color = colors.textPrimary,
                        )
                    }
                    cell = cell.next
                }
            }
            if (rowIndex < rows.lastIndex) {
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.divider))
            }
        }
    }
}

@Composable
private fun CitationRow(citations: List<TextCitation>, onClick: (TextCitation) -> Unit) {
    val colors = chatColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        citations.forEachIndexed { index, citation ->
            Surface(
                color = colors.tagBg,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickableNoRipple { onClick(citation) },
            ) {
                Text(
                    text = "${index + 1}",
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    color = colors.accent,
                    style = chatTypography().caption,
                )
            }
        }
    }
}

private fun extractHtmlTitle(code: String): String {
    val match = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE).find(code)
    val title = match?.groupValues?.getOrNull(1)?.trim().orEmpty()
    if (title.isNotEmpty()) return title
    val h1 = Regex("<h1[^>]*>(.*?)</h1>", RegexOption.IGNORE_CASE).find(code)?.groupValues?.getOrNull(1)
    return h1?.replace(Regex("<[^>]+>"), "")?.trim().orEmpty().ifBlank { "HTML 预览" }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.then(
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        )
    )
}

@Composable
private fun Modifier.clickableNoRippleFix(onClick: () -> Unit): Modifier = this

private fun Int.toDp(): androidx.compose.ui.unit.Dp = this.dp
