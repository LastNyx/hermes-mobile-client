package io.github.lastnyx.hermesclient.ui

private val ATTACHMENT_TOKEN = Regex("@\\w+:(`[^`]*`|\\S+)")
private val BARE_URL = Regex("https?://([^/\\s?#`]+)\\S*")
private val WHITESPACE = Regex("\\s+")

internal fun cleanPreview(raw: String): String =
    raw.replace(ATTACHMENT_TOKEN, " ")
        .replace(BARE_URL) { it.groupValues[1] }
        .replace(WHITESPACE, " ")
        .trim()

internal fun groupHeaderTitle(title: String, count: Int): String = "$title · $count"
