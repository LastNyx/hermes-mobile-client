package io.github.lastnyx.hermesclient.data.chat

import java.util.concurrent.ConcurrentHashMap

/**
 * Dismissed background process ids per session, kept for the app process so a re-opened chat
 * does not bring back rows the user already cleared. Memory only, by design.
 */
object DismissedProcesses {
    private val bySession = ConcurrentHashMap<String, MutableSet<String>>()

    fun dismiss(sessionKey: String, processId: String) {
        bySession.compute(sessionKey) { _, set -> (set ?: ConcurrentHashMap.newKeySet()).also { it.add(processId) } }
    }

    fun ids(sessionKey: String): Set<String> = bySession[sessionKey]?.toSet().orEmpty()

    /** Forgets dismissed ids the server no longer lists, so the store does not grow forever. */
    fun prune(sessionKey: String, listed: Set<String>) {
        bySession.computeIfPresent(sessionKey) { _, set ->
            set.retainAll(listed)
            if (set.isEmpty()) null else set
        }
    }

    internal fun reset() = bySession.clear()
}
