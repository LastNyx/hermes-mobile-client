package io.github.lastnyx.hermesclient.ui

// The gateway cuts previews at a fixed length, so the closing backquote may be missing.
private val ATTACHMENT_TOKEN = Regex("@\\w+:(\"[^\"]*\"|`[^`]*`?|\\S+)")
private val CONTEXT_MARKER = Regex("--- (?:Context Warnings|Attached Context) ---")
private val NOISE_ONLY = Regex("[\\s.\\u2026()\\[\\]{}]*")
private val BARE_URL = Regex("https?://([^/\\s?#`]+)\\S*")
private val WHITESPACE = Regex("\\s+")

internal fun cleanPreview(raw: String): String =
    raw.substringBefore(CONTEXT_MARKER.find(raw)?.value ?: "\u0000")
        .replace(ATTACHMENT_TOKEN, " ")
        .replace(BARE_URL) { it.groupValues[1] }
        .replace(WHITESPACE, " ")
        .trim()
        .let { if (NOISE_ONLY.matches(it)) "" else it }

internal fun groupHeaderTitle(title: String, count: Int): String = "$title · $count"
