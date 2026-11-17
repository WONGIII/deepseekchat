package com.wongiii.chat.domain.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.wongiii.chat.data.model.FileAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ImageCompressor {
    suspend fun compress(attachment: FileAttachment): FileAttachment = withContext(Dispatchers.IO) {
        val path = attachment.localPath ?: return@withContext attachment
        val file = File(path)
        if (!file.exists()) return@withContext attachment
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return@withContext attachment.copy(extraction = "error", extractionError = "图片无法解码")
        }
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (maxDim / sample > 2048) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(path, options)
            ?: return@withContext attachment.copy(extraction = "error", extractionError = "图片无法解码")
        val oriented = applyOrientation(decoded, readOrientation(path))
        val scaled = scaleToFit(oriented, 2048)
        val jpeg = compressToLimit(scaled)
        file.writeBytes(jpeg)
        val finalBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, finalBounds)
        attachment.copy(
            size = file.length(),
            mimeType = "image/jpeg",
            width = finalBounds.outWidth,
            height = finalBounds.outHeight,
            extraction = "done",
            extractionError = null,
        )
    }

    private fun readOrientation(path: String): Int = runCatching {
        when (ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    private fun applyOrientation(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleToFit(bitmap: Bitmap, max: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= max) return bitmap
        val scale = max.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun compressToLimit(bitmap: Bitmap): ByteArray {
        val limit = 4 * 1024 * 1024
        var quality = 86
        var bytes: ByteArray
        do {
            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 8
        } while (bytes.size > limit && quality >= 45)
        return bytes
    }
}
