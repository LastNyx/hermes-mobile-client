package io.github.lastnyx.hermesclient.data.chat

const val TOOL_DETAIL_LIMIT = 4000

// Room kept for the "\n... N more characters" line so the whole result stays within the limit.
private const val MARKER_ROOM = 40

fun capToolDetail(detail: String, limit: Int = TOOL_DETAIL_LIMIT): String {
    if (detail.length <= limit) return detail
    var keep = limit - MARKER_ROOM
    if (keep > 0 && detail[keep - 1].isHighSurrogate()) keep--
    keep = keep.coerceAtLeast(0)
    return detail.substring(0, keep) + "\n... ${detail.length - keep} more characters"
}
