package io.github.lastnyx.hermesclient.data.chat

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Thin Android adapter: every limit and name decision lives in Attachments.kt. */
class AttachmentReader(private val resolver: ContentResolver) {
    fun read(uri: Uri): Result<PreparedAttachment> {
        val (shownName, size) = describe(uri)
        val name = shownName ?: uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "attachment"
        val failure = Result.failure<PreparedAttachment>(Exception("Couldn't read $name."))
        return try {
            val mime = resolver.getType(uri)
            val kind = kindFor(mime, name)
            checkSize(name, kind, size ?: 0)?.let { return Result.failure(Exception(it)) }
            val limit = if (kind == AttachmentKind.Image) AttachLimits.IMAGE_MAX_BYTES else AttachLimits.FILE_MAX_BYTES
            val raw = resolver.openInputStream(uri)?.use { readCapped(it, limit) } ?: return failure
            if (raw.size > limit) return Result.failure(Exception(checkSize(name, kind, raw.size.toLong()) ?: "Couldn't read $name."))
            val isGif = mime == "image/gif" || (mime.isNullOrBlank() && name.endsWith(".gif", ignoreCase = true))
            if (kind == AttachmentKind.File || isGif) {
                Result.success(PreparedAttachment(name, kind, raw, mime?.takeIf { it.isNotBlank() } ?: "application/octet-stream"))
            } else {
                val jpeg = toJpeg(raw) ?: return failure
                Result.success(PreparedAttachment(jpegName(name), kind, jpeg, "image/jpeg"))
            }
        } catch (e: Exception) {
            failure
        }
    }

    private fun describe(uri: Uri): Pair<String?, Long?> =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null to null
            val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { c.getString(it) }?.takeIf { it.isNotBlank() }
            val s = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) }
            n to s
        } ?: (null to null)

    /** Stops at [limit] + 1 bytes so an oversize stream is never held whole. */
    private fun readCapped(input: InputStream, limit: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (total <= limit) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), limit + 1 - total).toInt())
            if (n < 0) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    private fun toJpeg(raw: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, AttachLimits.IMAGE_LONG_SIDE)
        }
        var bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size, opts) ?: return null
        val turn = when (ExifInterface(raw.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (turn != 0f) bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(turn) }, true)
        val (w, h) = scaledSize(bitmap.width, bitmap.height)
        if (w != bitmap.width || h != bitmap.height) bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, AttachLimits.JPEG_QUALITY, it) }.toByteArray()
    }
}
