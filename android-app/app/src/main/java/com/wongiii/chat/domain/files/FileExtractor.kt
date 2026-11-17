package com.wongiii.chat.domain.files

import android.content.Context
import android.util.Xml
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.wongiii.chat.data.model.FileAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

private const val MAX_TEXT = 150_000

class FileExtractor(private val context: Context) {
    private var pdfReady = false

    suspend fun extract(attachment: FileAttachment): FileAttachment = withContext(Dispatchers.IO) {
        val path = attachment.localPath
        if (path == null || !File(path).exists()) {
            return@withContext attachment.copy(extraction = "error", extractionError = "本地文件不存在")
        }
        val file = File(path)
        val ext = fileExtension(attachment.name)
        if (ext in setOf("doc", "ppt", "xls")) {
            return@withContext attachment.copy(
                extraction = "error",
                extractionError = "旧版 Office 格式不支持，请另存为 docx/pptx/xlsx",
            )
        }
        val text = runCatching {
            when (ext) {
                "pdf" -> pdfText(file)
                "docx" -> ooxmlText(file, docx = true)
                "pptx" -> ooxmlText(file, pptx = true)
                "xlsx" -> ooxmlText(file, xlsx = true)
                "txt", "md", "markdown", "csv", "json", "xml", "html", "htm",
                "js", "ts", "tsx", "jsx", "py", "java", "kt", "kts", "c", "cc", "cpp",
                "h", "hpp", "cs", "go", "rs", "rb", "php", "swift", "sql", "sh", "bat",
                "yaml", "yml", "toml", "ini", "log", "tex" -> file.readText(Charsets.UTF_8)
                else -> null
            }
        }.getOrElse { error ->
            return@withContext attachment.copy(
                extraction = "error",
                extractionError = error.message?.take(80) ?: "解析失败",
            )
        }
        if (text.isNullOrBlank()) {
            return@withContext attachment.copy(
                extraction = "error",
                extractionError = "未能提取文字，可能是扫描件或空文档",
            )
        }
        val normalized = text.replace("\u0000", "").trim()
        val truncated = normalized.length > MAX_TEXT
        attachment.copy(
            extraction = "done",
            extractionError = null,
            textContent = normalized.take(MAX_TEXT),
            truncated = truncated,
            size = file.length(),
        )
    }

    private fun pdfText(file: File): String {
        if (!pdfReady) {
            PDFBoxResourceLoader.init(context.applicationContext)
            pdfReady = true
        }
        PDDocument.load(file).use { document ->
            return PDFTextStripper().getText(document)
        }
    }

    private fun ooxmlText(file: File, docx: Boolean = false, pptx: Boolean = false, xlsx: Boolean = false): String {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(file.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                val wanted = when {
                    docx -> name == "word/document.xml"
                    pptx -> name.startsWith("ppt/slides/slide") && name.endsWith(".xml")
                    xlsx -> name == "xl/sharedStrings.xml" || (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml"))
                    else -> false
                }
                if (wanted) entries[name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return when {
            docx -> entries["word/document.xml"]?.let { docxText(it) }.orEmpty()
            pptx -> entries.entries.sortedBy { slideNumber(it.key) }.joinToString("\n") { (_, bytes) ->
                xmlText(bytes, "t", paragraphTag = null)
            }
            xlsx -> xlsxText(entries)
            else -> ""
        }
    }

    private fun docxText(bytes: ByteArray): String {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        return buildString {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> if (parser.name == "t") append(parser.nextText())
                    XmlPullParser.END_TAG -> if (parser.name == "p") append('\n')
                }
                event = parser.next()
            }
        }
    }

    private fun xmlText(bytes: ByteArray, textTag: String, paragraphTag: String?): String {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        return buildString {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> if (parser.name == textTag) append(parser.nextText()).append('\n')
                    XmlPullParser.END_TAG -> if (paragraphTag != null && parser.name == paragraphTag) append('\n')
                }
                event = parser.next()
            }
        }
    }

    private fun xlsxText(entries: Map<String, ByteArray>): String {
        val shared = entries["xl/sharedStrings.xml"]?.let { bytes ->
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(bytes), null)
            val values = mutableListOf<String>()
            var event = parser.eventType
            var current = StringBuilder()
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> if (parser.name == "t") current.append(parser.nextText())
                    XmlPullParser.END_TAG -> if (parser.name == "si") {
                        values += current.toString()
                        current = StringBuilder()
                    }
                }
                event = parser.next()
            }
            values
        }.orEmpty()
        val sheets = entries.filterKeys { it.startsWith("xl/worksheets/sheet") }.toSortedMap()
        return buildString {
            sheets.forEach { (_, bytes) ->
                val parser = Xml.newPullParser()
                parser.setInput(ByteArrayInputStream(bytes), null)
                var event = parser.eventType
                var cellType: String? = null
                var lastRow = ""
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> when (parser.name) {
                            "c" -> cellType = parser.getAttributeValue(null, "t")
                            "v" -> {
                                val raw = parser.nextText()
                                val value = if (cellType == "s") shared.getOrNull(raw.toIntOrNull() ?: -1) else raw
                                if (!value.isNullOrBlank()) append(value).append('\t')
                            }
                        }
                        XmlPullParser.END_TAG -> when (parser.name) {
                            "row" -> {
                                append('\n')
                                lastRow = ""
                            }
                            "c" -> cellType = null
                        }
                    }
                    event = parser.next()
                }
                append('\n')
            }
        }
    }

    private fun slideNumber(name: String): Int =
        Regex("slide(\\d+)\\.xml").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
}

fun fileExtension(name: String): String {
    val dot = name.lastIndexOf('.')
    return if (dot > 0) name.substring(dot + 1).lowercase() else ""
}

fun inferMime(name: String): String = when (fileExtension(name)) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "heic", "heif" -> "image/heic"
    "pdf" -> "application/pdf"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    "txt" -> "text/plain"
    "md" -> "text/markdown"
    "csv" -> "text/csv"
    "json" -> "application/json"
    "html", "htm" -> "text/html"
    else -> "application/octet-stream"
}

fun isSupportedAttachment(name: String): Boolean {
    val ext = fileExtension(name)
    return ext in setOf(
        "png", "jpg", "jpeg", "webp", "gif", "heic", "heif",
        "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx",
        "txt", "md", "markdown", "csv", "json", "xml", "html", "htm",
        "js", "ts", "tsx", "jsx", "py", "java", "kt", "kts", "c", "cc", "cpp",
        "h", "hpp", "cs", "go", "rs", "rb", "php", "swift", "sql", "sh", "bat",
        "yaml", "yml", "toml", "ini", "log", "tex",
    )
}
