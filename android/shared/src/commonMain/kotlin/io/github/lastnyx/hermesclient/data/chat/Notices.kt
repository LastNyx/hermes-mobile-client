package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.live.LinkEvent
import org.json.JSONObject

enum class NoticeLevel { Info, Warn, Error, Success }

data class Notice(val uid: Long, val key: String?, val text: String, val level: NoticeLevel, val expiresAt: Long?)

private const val DEFAULT_TTL_MS = 5000L
private const val MAX_NOTICES = 5

/** Gateway JSON often carries explicit nulls, which `optString` would turn into the text "null". */
internal fun JSONObject.stringOrNull(name: String): String? = if (has(name) && !isNull(name)) optString(name) else null

data class NoticeStore(val notices: List<Notice> = emptyList(), val next: Long = 1) {
    fun add(event: LinkEvent, now: Long): NoticeStore {
        val p = event.payload
        if (event.type != "notification.show" || p == null) return this
        val text = p.stringOrNull("text")?.takeIf { it.isNotBlank() } ?: return this
        val level = when (p.stringOrNull("level")) {
            "warn" -> NoticeLevel.Warn
            "error" -> NoticeLevel.Error
            "success" -> NoticeLevel.Success
            else -> NoticeLevel.Info
        }
        val expiresAt = if (p.stringOrNull("kind") == "ttl") {
            val ttl = (p.opt("ttl_ms") as? Number)?.toLong()?.takeIf { it > 0 } ?: DEFAULT_TTL_MS
            now + ttl
        } else null
        val key = p.stringOrNull("key") ?: p.stringOrNull("id")
        val notice = Notice(next, key, text, level, expiresAt)
        val at = if (key == null) -1 else notices.indexOfFirst { it.key == key }
        val list = if (at >= 0) notices.toMutableList().also { it[at] = notice } else notices + notice
        return NoticeStore(list.takeLast(MAX_NOTICES), next + 1)
    }

    fun clear(key: String) = copy(notices = notices.filterNot { it.key == key })

    fun expire(now: Long) = copy(notices = notices.filterNot { it.expiresAt != null && it.expiresAt <= now })

    fun dismiss(uid: Long) = copy(notices = notices.filterNot { it.uid == uid })
}
