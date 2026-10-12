package io.github.lastnyx.hermesclient.data.watch

import io.github.lastnyx.hermesclient.data.chat.ChatState
import io.github.lastnyx.hermesclient.data.chat.Prompt

/** What to show for a watched chat. Carries only the session id and a tool name, never command text or answers. */
sealed interface WatchAction {
    val sessionId: String

    data class Done(override val sessionId: String) : WatchAction
    data class Failed(override val sessionId: String) : WatchAction
    data class NeedsApproval(override val sessionId: String, val toolName: String) : WatchAction
    data class NeedsAnswer(override val sessionId: String) : WatchAction
    data class Clear(override val sessionId: String) : WatchAction
}

/**
 * Decides, from chat state, when the Watch service must run and which notifications to post.
 * Only turns started on the phone are watched; Desktop turns never start it.
 */
class WatchPolicy {
    private class Watched(var seenRequests: Set<String> = emptySet(), var started: Boolean = false)

    private val watched = mutableMapOf<String, Watched>()

    val shouldRun: Boolean @Synchronized get() = watched.isNotEmpty()

    @Synchronized
    fun watching(sessionId: String): Boolean = sessionId in watched

    @Synchronized
    fun turnStarted(sessionId: String) {
        watched.getOrPut(sessionId) { Watched() }
    }

    @Synchronized
    fun onState(sessionId: String, state: ChatState): List<WatchAction> {
        val entry = watched[sessionId] ?: return emptyList()
        val actions = mutableListOf<WatchAction>()
        val prompts = state.prompts
        val now = prompts.map { it.id }.toSet()
        for (p in prompts) {
            if (p.id in entry.seenRequests) continue
            actions += when (p) {
                is Prompt.Approval -> WatchAction.NeedsApproval(sessionId, p.toolName)
                is Prompt.Clarify -> WatchAction.NeedsAnswer(sessionId)
            }
        }
        if (entry.seenRequests.any { it !in now }) actions += WatchAction.Clear(sessionId)
        entry.seenRequests = now
        if (state.running || state.error != null) entry.started = true
        // The state seen right after turnStarted may predate the send flipping `running`.
        if (entry.started && !state.running && prompts.isEmpty()) {
            actions += if (state.error != null) WatchAction.Failed(sessionId) else WatchAction.Done(sessionId)
            watched.remove(sessionId)
        }
        return actions
    }
}
