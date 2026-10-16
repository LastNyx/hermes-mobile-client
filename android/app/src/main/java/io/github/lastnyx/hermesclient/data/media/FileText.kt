package io.github.lastnyx.hermesclient.data.media

import java.util.Locale
import org.json.JSONObject

data class FileText(val text: String, val byteSize: Long, val truncated: Boolean, val binary: Boolean, val language: String?)

fun parseFileText(body: JSONObject): FileText = FileText(
    // optString on JSON null would return the text "null".
    text = if (body.isNull("text")) "" else body.optString("text"),
    byteSize = body.optLong("byteSize"),
    truncated = body.optBoolean("truncated"),
    binary = body.optBoolean("binary"),
    language = if (body.isNull("language")) null else body.optString("language").takeIf { it.isNotBlank() },
)

fun numberLines(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val lines = text.split("\r\n", "\n").let { if (it.last().isEmpty()) it.dropLast(1) else it }
    val width = lines.size.toString().length
    return lines.mapIndexed { i, line -> "${(i + 1).toString().padStart(width)} $line" }
}

fun sizeText(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
}

fun viewerNotice(f: FileText): String? = when {
    f.binary -> "This file isn't text. Save it to open it elsewhere."
    f.truncated -> "Showing the first 512 KB of ${sizeText(f.byteSize)}."
    else -> null
}
