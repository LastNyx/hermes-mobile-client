package io.github.lastnyx.hermesclient.ui

internal const val DISPLAY_MAX_CHARS = 24_000
internal const val DISPLAY_MAX_LINE = 1_000

// Our own markers: recognised so clipping already-clipped text changes nothing.
private val LINE_MARKER = Regex(" \\.\\.\\. \\d+ more characters$")
private val TOTAL_MARKER = Regex("\n\\.\\.\\. \\d+ more characters$")

internal fun clipForDisplay(
    text: String,
    maxChars: Int = DISPLAY_MAX_CHARS,
    maxLine: Int = DISPLAY_MAX_LINE,
): String {
    val body = TOTAL_MARKER.find(text, (text.length - 40).coerceAtLeast(0))
        ?.let { text.substring(0, it.range.first) } ?: text
    if (body.length <= maxChars && !hasLongLine(body, maxLine)) return text
    val lined = text.split('\n').joinToString("\n") { line ->
        if (!isLong(line, maxLine)) line
        else {
            val kept = safeEnd(line, maxLine)
            line.substring(0, kept) + " ... ${line.length - kept} more characters"
        }
    }
    if (lined.length <= maxChars) return lined
    val kept = safeEnd(lined, maxChars)
    return lined.substring(0, kept) + "\n... ${lined.length - kept} more characters"
}

private fun isLong(line: String, maxLine: Int): Boolean {
    if (line.length <= maxLine) return false
    val m = LINE_MARKER.find(line, (line.length - 40).coerceAtLeast(0)) ?: return true
    return m.range.first > maxLine
}

private fun hasLongLine(text: String, maxLine: Int): Boolean {
    var start = 0
    while (start <= text.length) {
        val nl = text.indexOf('\n', start).let { if (it < 0) text.length else it }
        if (nl - start > maxLine && isLong(text.substring(start, nl), maxLine)) return true
        start = nl + 1
    }
    return false
}

// Never end on a lone high surrogate: it would draw as a broken glyph.
private fun safeEnd(s: String, end: Int): Int =
    if (end > 0 && Character.isHighSurrogate(s[end - 1])) end - 1 else end
