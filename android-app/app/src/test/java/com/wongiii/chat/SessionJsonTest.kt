package com.wongiii.chat

import com.wongiii.chat.data.model.AppJson
import com.wongiii.chat.data.model.ChatSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class SessionJsonTest {
    @Test
    fun injectedSessionDecodes() {
        val text = File("D:/zcodeprojectxianmu/deepseekchat/android-app/screenshots/demo-table3.json").readText()
        val session = AppJson.decodeFromString(ChatSession.serializer(), text)
        assertEquals("表格渲染测试", session.title)
    }
}
