package com.wongiii.chat

import com.wongiii.chat.feature.normalizePipeTables
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTableNormalizeTest {
    @Test
    fun tableWithoutBlankLinesIsNormalized() {
        val parser = Parser.builder().extensions(listOf(TablesExtension.create())).build()
        val raw = "下面是表格：\n| 项目 | 说明 |\n| ---- | ---- |\n| A | 1 |\n表格结束。"
        val document = parser.parse(normalizePipeTables(raw))
        var child: Node? = document.firstChild
        var found = false
        while (child != null) {
            if (child is TableBlock) found = true
            child = child.next
        }
        assertTrue(found)
    }
}
