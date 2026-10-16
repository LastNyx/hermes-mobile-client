package io.github.lastnyx.hermesclient.data.settings

import io.github.lastnyx.hermesclient.data.sessions.SessionGrouping
import io.github.lastnyx.hermesclient.data.sessions.SessionOrdering
import io.github.lastnyx.hermesclient.data.sessions.SessionStatus

class SessionViewStore(private val storage: SessionViewStorage) {
    fun grouping(): SessionGrouping = SessionGrouping.fromKey(storage.load("session_grouping"))
    fun ordering(): SessionOrdering = SessionOrdering.fromKey(storage.load("session_ordering"))
    fun setGrouping(value: SessionGrouping) = storage.save("session_grouping", value.key)
    fun setOrdering(value: SessionOrdering) = storage.save("session_ordering", value.key)

    /** Empty means all statuses. */
    fun statusFilter(): Set<SessionStatus> =
        storage.load("session_status_filter").orEmpty().split(',').mapNotNull { SessionStatus.fromKey(it) }.toSet()

    fun setStatusFilter(value: Set<SessionStatus>) =
        storage.save("session_status_filter", value.joinToString(",") { it.key })
}
