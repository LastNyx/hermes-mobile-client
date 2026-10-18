package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.live.RpcError
import io.github.lastnyx.hermesclient.data.sessions.CANT_REACH_GATEWAY
import java.io.IOException

enum class AttachmentKind { Image, File }

/** Ready to upload: an image is already a scaled JPEG, or an unchanged GIF. */
class PreparedAttachment(val name: String, val kind: AttachmentKind, val bytes: ByteArray, val mime: String)

object AttachLimits {
    const val MAX_COUNT = 6
    const val IMAGE_MAX_BYTES = 25L * 1024 * 1024
    const val FILE_MAX_BYTES = 10L * 1024 * 1024
    const val IMAGE_LONG_SIDE = 2048
    const val JPEG_QUALITY = 85
    const val IMAGE_ATTACH_TIMEOUT_MS = 90_000L
    const val FILE_ATTACH_TIMEOUT_MS = 120_000L
    const val IMAGE_DETACH_TIMEOUT_MS = 120_000L
}

private val IMAGE_MIMES = setOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp", "image/heic", "image/heif")
private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")

fun kindFor(mime: String?, name: String): AttachmentKind {
    val m = mime?.trim()?.lowercase()
    if (m.isNullOrEmpty() || m == "application/octet-stream") {
        val ext = name.substringAfterLast('.', "").lowercase()
        return if (ext in IMAGE_EXTENSIONS) AttachmentKind.Image else AttachmentKind.File
    }
    return if (m in IMAGE_MIMES) AttachmentKind.Image else AttachmentKind.File
}

fun scaledSize(width: Int, height: Int, maxLong: Int = AttachLimits.IMAGE_LONG_SIDE): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return width to height
    val long = maxOf(width, height)
    if (long <= maxLong) return width to height
    fun scale(side: Int) = Math.round(side.toDouble() * maxLong / long).toInt().coerceAtLeast(1)
    return if (width >= height) maxLong to scale(height) else scale(width) to maxLong
}

fun sampleSizeFor(width: Int, height: Int, target: Int): Int {
    if (width <= 0 || height <= 0 || target <= 0) return 1
    val long = maxOf(width, height)
    var s = 1
    while (long / (s * 2) >= target) s *= 2
    return s
}

fun jpegName(name: String): String {
    if (name.isBlank()) return "image.jpg"
    val dot = name.lastIndexOf('.')
    val stem = if (dot > 0) name.substring(0, dot) else name
    return "$stem.jpg"
}

fun checkSize(name: String, kind: AttachmentKind, byteSize: Long): String? = when {
    kind == AttachmentKind.Image && byteSize > AttachLimits.IMAGE_MAX_BYTES -> "$name is larger than 25 MB."
    kind == AttachmentKind.File && byteSize > AttachLimits.FILE_MAX_BYTES -> "$name is larger than 10 MB."
    else -> null
}

data class PickDecision(val accepted: Int, val message: String?)

fun acceptPick(current: Int, picked: Int): PickDecision {
    val room = (AttachLimits.MAX_COUNT - current).coerceAtLeast(0)
    val accepted = minOf(room, picked)
    return PickDecision(accepted, if (accepted < picked) "Up to ${AttachLimits.MAX_COUNT} attachments per message." else null)
}

fun attachmentLine(kind: AttachmentKind, name: String): String =
    if (kind == AttachmentKind.Image) "[image]" else "📎 $name"

fun bubbleText(typed: String, attachments: List<PreparedAttachment>): String =
    (listOf(typed.trim()).filter { it.isNotEmpty() } + attachments.map { attachmentLine(it.kind, it.name) }).joinToString("\n")

fun buildPrompt(typed: String, refTexts: List<String>, imageCount: Int): String {
    val refs = refTexts.map { it.trim() }.filter { it.isNotEmpty() }
    val text = typed.trim()
    if (text.isEmpty() && refs.isEmpty()) return if (imageCount > 0) "What do you see in this image?" else ""
    return listOf(refs.joinToString("\n"), text).filter { it.isNotEmpty() }.joinToString("\n\n")
}

fun attachErrorText(name: String, error: Throwable): String = when {
    error is IOException -> CANT_REACH_GATEWAY
    error is RpcError && error.code == 4018 -> "$name is too large for the gateway."
    else -> "Couldn't attach $name."
}
