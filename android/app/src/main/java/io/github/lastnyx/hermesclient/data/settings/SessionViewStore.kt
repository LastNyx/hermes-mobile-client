package io.github.lastnyx.hermesclient.data.settings

import io.github.lastnyx.hermesclient.data.sessions.SessionGrouping
import io.github.lastnyx.hermesclient.data.sessions.SessionOrdering

class SessionViewStore(private val storage: SessionViewStorage) {
    fun grouping(): SessionGrouping = SessionGrouping.fromKey(storage.load("session_grouping"))
    fun ordering(): SessionOrdering = SessionOrdering.fromKey(storage.load("session_ordering"))
    fun setGrouping(value: SessionGrouping) = storage.save("session_grouping", value.key)
    fun setOrdering(value: SessionOrdering) = storage.save("session_ordering", value.key)
}
