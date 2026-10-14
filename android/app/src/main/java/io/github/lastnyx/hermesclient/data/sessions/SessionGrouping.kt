package io.github.lastnyx.hermesclient.data.sessions

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class SessionGrouping(val key: String) {
    Date("date");

    companion object {
        fun fromKey(key: String?): SessionGrouping = entries.firstOrNull { it.key == key } ?: Date
    }
}

enum class SessionOrdering(val key: String, val label: String) {
    Updated("updated", "Last activity"),
    Created("created", "Created");

    companion object {
        fun fromKey(key: String?): SessionOrdering = entries.firstOrNull { it.key == key } ?: Updated
    }
}

data class SessionGroup(val title: String, val sessions: List<Session>)

/** [now] is epoch seconds; day boundaries are local midnight in [zone]. */
fun groupSessions(
    sessions: List<Session>,
    now: Long,
    grouping: SessionGrouping,
    ordering: SessionOrdering,
    zone: ZoneId,
): List<SessionGroup> {
    fun stamp(s: Session) = if (ordering == SessionOrdering.Created) s.startedAt else s.lastActive
    val newestFirst = sessions.sortedByDescending(::stamp)
    val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    fun title(s: Session): String {
        val day = Instant.ofEpochSecond(stamp(s).toLong()).atZone(zone).toLocalDate()
        return when (ChronoUnit.DAYS.between(day, today)) {
            in Long.MIN_VALUE..0L -> "Today"
            1L -> "Yesterday"
            in 2L..8L -> "Last week"
            else -> "Older"
        }
    }
    val (pinned, rest) = newestFirst.partition { it.pinned }
    val byTitle = rest.groupBy(::title)
    return buildList {
        if (pinned.isNotEmpty()) add(SessionGroup("Pinned", pinned))
        for (t in listOf("Today", "Yesterday", "Last week", "Older")) {
            byTitle[t]?.let { add(SessionGroup(t, it)) }
        }
    }
}
