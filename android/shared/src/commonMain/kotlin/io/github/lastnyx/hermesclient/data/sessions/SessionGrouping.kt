package io.github.lastnyx.hermesclient.data.sessions

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class SessionGrouping(val key: String, val label: String) {
    Date("date", "Date"),
    Status("status", "Status"),
    Project("project", "Project");

    companion object {
        fun fromKey(key: String?): SessionGrouping = entries.firstOrNull { it.key == key } ?: Date
    }
}

/** Loudest first. "Needs input" is not here: a stored row cannot tell. */
enum class SessionStatus(val key: String, val label: String) {
    Working("working", "Working"),
    Unread("unread", "Unread"),
    Draft("draft", "Draft"),
    Idle("idle", "Idle");

    companion object {
        fun fromKey(key: String): SessionStatus? = entries.firstOrNull { it.key == key }
    }
}

fun sessionStatus(session: Session): SessionStatus = when {
    session.active -> SessionStatus.Working
    session.unread -> SessionStatus.Unread
    session.messageCount == 0 -> SessionStatus.Draft
    else -> SessionStatus.Idle
}

enum class SessionOrdering(val key: String, val label: String) {
    Updated("updated", "Last activity"),
    Created("created", "Created");

    companion object {
        fun fromKey(key: String?): SessionOrdering = entries.firstOrNull { it.key == key } ?: Updated
    }
}

data class SessionGroup(val title: String, val sessions: List<Session>)

const val NO_PROJECT = "No project"

/**
 * [now] is epoch seconds; day boundaries are local midnight in [zone].
 * [statuses] empty means all; filtering happens after grouping and drops empty groups.
 */
fun groupSessions(
    sessions: List<Session>,
    now: Long,
    grouping: SessionGrouping,
    ordering: SessionOrdering,
    zone: ZoneId,
    statuses: Set<SessionStatus> = emptySet(),
    projects: List<Project>? = null,
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
    val grouped = buildList {
        if (pinned.isNotEmpty()) add(SessionGroup("Pinned", pinned))
        when (grouping) {
            SessionGrouping.Date -> {
                val byTitle = rest.groupBy(::title)
                for (t in listOf("Today", "Yesterday", "Last week", "Older")) {
                    byTitle[t]?.let { add(SessionGroup(t, it)) }
                }
            }
            SessionGrouping.Status -> {
                val byStatus = rest.groupBy(::sessionStatus)
                for (st in SessionStatus.entries) {
                    byStatus[st]?.let { add(SessionGroup(st.label, it)) }
                }
            }
            SessionGrouping.Project -> {
                val owner = projects?.let { list ->
                    buildMap { list.forEach { p -> p.sessionIds.forEach { putIfAbsent(it, p) } } }
                }
                val byProject = rest.groupBy { s ->
                    val p = owner?.get(s.id)
                    when {
                        p == null -> projectName(s.cwd)
                        p.isNoProject -> NO_PROJECT
                        else -> p.label.ifBlank { NO_PROJECT }
                    }
                }
                // Each list is already newest first, so the first element is the group's newest.
                val named = byProject.filterKeys { it != NO_PROJECT }.entries
                    .sortedByDescending { stamp(it.value.first()) }
                named.forEach { add(SessionGroup(it.key, it.value)) }
                byProject[NO_PROJECT]?.let { add(SessionGroup(NO_PROJECT, it)) }
            }
        }
    }
    if (statuses.isEmpty()) return grouped
    return grouped.mapNotNull { g ->
        g.sessions.filter { sessionStatus(it) in statuses }.takeIf { it.isNotEmpty() }?.let { SessionGroup(g.title, it) }
    }
}

private fun projectName(cwd: String?): String =
    cwd?.trimEnd('/', '\\')?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() } ?: NO_PROJECT
