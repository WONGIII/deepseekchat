package com.wongiii.chat.data.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.wongiii.chat.data.model.FileAttachment
import com.wongiii.chat.data.model.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileStore(private val context: Context) {
    private val root = File(context.filesDir, "attachments").apply { mkdirs() }

    fun pathFor(id: String): File = File(root, id)

    suspend fun copyFromUri(uri: Uri, nameFallback: String = "file"): Pair<File, String> =
        withContext(Dispatchers.IO) {
            val id = newId()
            val file = File(root, id)
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: error("无法读取文件")
            val name = displayName(uri) ?: nameFallback
            file to name
        }

    fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    fun displaySize(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

    suspend fun saveAt(id: String, bytes: ByteArray): File = withContext(Dispatchers.IO) {
        val file = File(root, id)
        file.writeBytes(bytes)
        file
    }

    suspend fun copyBytes(bytes: ByteArray, name: String, mime: String, isImage: Boolean): FileAttachment =
        withContext(Dispatchers.IO) {
            val id = newId()
            val file = File(root, id)
            file.writeBytes(bytes)
            FileAttachment(
                id = id,
                name = name,
                size = file.length(),
                mimeType = mime,
                isImage = isImage,
                localPath = file.absolutePath,
            )
        }

    fun exists(path: String?): Boolean = path != null && File(path).exists()

    fun read(path: String?): ByteArray? = path?.let { runCatching { File(it).readBytes() }.getOrNull() }

    fun delete(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }

    fun clearAll() {
        root.listFiles()?.forEach { it.delete() }
    }
}
