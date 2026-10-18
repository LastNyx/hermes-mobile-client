package io.github.lastnyx.hermesclient.data.media

import java.util.Base64

enum class MediaKind { Image, File }

data class MediaRef(val kind: MediaKind, val path: String, val name: String)

data class ReplyMedia(val text: String, val refs: List<MediaRef>)

// Android cannot decode svg or ico, so they are plain files here.
private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
private val MEDIA_LINE = Regex("^MEDIA:\\s*(.*)$")
private val MARKDOWN_IMAGE = Regex("!\\[[^\\]]*]\\(([^)\\s]+)\\)")
private val BLANK_RUNS = Regex("\\n{3,}")

/** A Windows drive path such as `C:\a` or `C:/a`. */
val WINDOWS_PATH = Regex("^[A-Za-z]:[\\\\/].*")

/** The file name: the last segment after `/` or `\`. */
fun pathName(path: String): String = path.substringAfterLast('/').substringAfterLast('\\')

/** True when [path] names a picture Android can decode. */
fun isImagePath(path: String): Boolean = pathName(path).substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

private fun refFor(path: String): MediaRef =
    MediaRef(if (isImagePath(path)) MediaKind.Image else MediaKind.File, path, pathName(path))

private fun unwrap(raw: String): String {
    val s = raw.trim()
    if (s.length >= 2 && s.first() == s.last() && s.first() in "`\"'") return s.substring(1, s.length - 1).trim()
    return s
}

private fun localPath(target: String): String? = when {
    target.startsWith("file://") -> target.removePrefix("file://").replace("%20", " ")
    target.startsWith("/") || WINDOWS_PATH.matches(target) -> target.replace("%20", " ")
    else -> null
}

fun extractReplyMedia(text: String): ReplyMedia {
    val refs = LinkedHashMap<String, MediaRef>()
    val kept = mutableListOf<String>()
    var fenced = false
    for (line in text.split("\n")) {
        if (line.trimStart().startsWith("```")) {
            fenced = !fenced
            kept += line
            continue
        }
        if (fenced) {
            kept += line
            continue
        }
        val path = MEDIA_LINE.find(line.trim())?.let { unwrap(it.groupValues[1]) }
        if (!path.isNullOrEmpty()) {
            refs.putIfAbsent(path, refFor(path))
            continue
        }
        var removed = false
        val rest = MARKDOWN_IMAGE.replace(line) { m ->
            val local = localPath(m.groupValues[1]) ?: return@replace m.value
            refs.putIfAbsent(local, refFor(local))
            removed = true
            ""
        }
        if (removed && rest.isBlank()) continue
        kept += if (removed) rest.trimEnd() else line
    }
    if (refs.isEmpty()) return ReplyMedia(text, emptyList())
    return ReplyMedia(kept.joinToString("\n").replace(BLANK_RUNS, "\n\n").trim(), refs.values.toList())
}

/** Bytes of a `data:<mime>;base64,<payload>` URL; null for anything else. An empty payload gives an empty array. */
fun parseDataUrl(dataUrl: String): ByteArray? {
    if (!dataUrl.startsWith("data:")) return null
    val marker = dataUrl.indexOf(";base64,")
    if (marker < 0) return null
    return try {
        Base64.getDecoder().decode(dataUrl.substring(marker + ";base64,".length))
    } catch (e: IllegalArgumentException) {
        null
    }
}

class ByteBudgetLru<K : Any, V : Any>(private val maxBytes: Long, private val sizeOf: (V) -> Long) {
    private val map = LinkedHashMap<K, V>(16, 0.75f, true)
    private var total = 0L

    val bytes: Long get() = synchronized(this) { total }
    val size: Int get() = synchronized(this) { map.size }

    fun get(key: K): V? = synchronized(this) { map[key] }

    fun put(key: K, value: V) {
        synchronized(this) {
            map.remove(key)?.let { total -= sizeOf(it) }
            val cost = sizeOf(value)
            if (cost > maxBytes) return
            val oldest = map.entries.iterator()
            while (total + cost > maxBytes && oldest.hasNext()) {
                total -= sizeOf(oldest.next().value)
                oldest.remove()
            }
            map[key] = value
            total += cost
        }
    }

    fun clear() = synchronized(this) {
        map.clear()
        total = 0
    }
}

fun clampScale(scale: Float): Float = scale.coerceIn(1f, 6f)

fun clampOffset(offset: Float, scale: Float, viewPx: Float): Float {
    val limit = (viewPx * (scale - 1) / 2).coerceAtLeast(0f)
    return offset.coerceIn(-limit, limit)
}
