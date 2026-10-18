package io.github.lastnyx.hermesclient.data.share

const val SHARE_DRAFT_MAX_CHARS = 100_000

/** One-shot signal: the id makes sharing identical text twice still count as a new request. */
data class ShareRequest(val id: Long, val draft: String)

fun draftFromShare(subject: String?, text: String?): String? {
    val body = text?.trim().orEmpty()
    val title = subject?.trim().orEmpty()
    val bareLink = body.none { it.isWhitespace() } &&
        (body.startsWith("http://", ignoreCase = true) || body.startsWith("https://", ignoreCase = true))
    val draft = when {
        body.isEmpty() && title.isEmpty() -> return null
        body.isEmpty() -> title
        bareLink && title.isNotEmpty() && title != body -> "$title\n$body"
        else -> body
    }
    val cut = draft.take(SHARE_DRAFT_MAX_CHARS)
    return if (cut.length < draft.length && cut.last().isHighSurrogate()) cut.dropLast(1) else cut
}
