package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.chat.AttachLimits
import io.github.lastnyx.hermesclient.data.chat.AttachmentKind
import io.github.lastnyx.hermesclient.data.chat.PreparedAttachment
import io.github.lastnyx.hermesclient.data.chat.checkSize
import io.github.lastnyx.hermesclient.data.chat.jpegName
import io.github.lastnyx.hermesclient.data.chat.kindFor
import io.github.lastnyx.hermesclient.data.chat.scaledSize
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

private const val PERCENT = 100f

/** Same limits, names and messages as the Android reader; every limit decision lives in Attachments.kt. */
fun readPickedFile(file: File): Result<PreparedAttachment> {
    val name = file.name
    val failure = Result.failure<PreparedAttachment>(Exception("Couldn't read $name."))
    return try {
        val mime = Files.probeContentType(file.toPath())?.takeIf { it.isNotBlank() }
        val kind = kindFor(mime, name)
        checkSize(name, kind, file.length())?.let { return Result.failure(Exception(it)) }
        val raw = file.readBytes()
        val isGif = mime == "image/gif" || (mime == null && name.endsWith(".gif", ignoreCase = true))
        if (kind == AttachmentKind.File || isGif) {
            Result.success(PreparedAttachment(name, kind, raw, mime ?: "application/octet-stream"))
        } else {
            val jpeg = toJpeg(raw) ?: return failure
            Result.success(PreparedAttachment(jpegName(name), kind, jpeg, "image/jpeg"))
        }
    } catch (e: Exception) {
        failure
    }
}

private fun toJpeg(raw: ByteArray): ByteArray? {
    val source = ImageIO.read(ByteArrayInputStream(raw)) ?: return null
    val (w, h) = scaledSize(source.width, source.height)
    // JPEG has no alpha: draw onto an RGB canvas.
    val canvas = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    canvas.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        drawImage(source, 0, 0, w, h, null)
        dispose()
    }
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    val params = writer.defaultWriteParam.apply {
        compressionMode = ImageWriteParam.MODE_EXPLICIT
        compressionQuality = AttachLimits.JPEG_QUALITY / PERCENT
    }
    val out = ByteArrayOutputStream()
    try {
        ImageIO.createImageOutputStream(out).use {
            writer.output = it
            writer.write(null, IIOImage(canvas, null, null), params)
        }
    } finally {
        writer.dispose()
    }
    return out.toByteArray()
}
