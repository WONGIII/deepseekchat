package com.wongiii.chat

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTableTest {
    @Test
    fun gfmTableIsParsed() {
        val parser = Parser.builder().extensions(
            listOf(
                TablesExtension.create(),
                org.commonmark.ext.gfm.strikethrough.StrikethroughExtension.create(),
                org.commonmark.ext.task.list.items.TaskListItemsExtension.create(),
            )
        ).build()
        val document = parser.parse("| 项目 | 说明 |\n| --- | --- |\n| A | 1 |")
        val table = document.firstChild as TableBlock
        assertTrue(table.firstChild is TableHead)
        val header = (table.firstChild as TableHead).firstChild as TableRow
        var cell = header.firstChild
        var count = 0
        while (cell != null) {
            if (cell is TableCell) count++
            cell = cell.next
        }
        assertEquals(2, count)
    }
}
