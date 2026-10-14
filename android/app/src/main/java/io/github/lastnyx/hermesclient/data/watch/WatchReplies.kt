package io.github.lastnyx.hermesclient.data.watch

/** An answer given from a notification. Ticket 50 adds a text answer. */
sealed interface WatchReply {
    data class Approve(val promptId: String, val choice: String) : WatchReply
}

/** Only these two choices are offered on the notification; the rest need the chat. */
fun watchAnswer(promptId: String, choice: String): WatchReply? =
    if (choice == "once" || choice == "deny") WatchReply.Approve(promptId, choice) else null

/** Per-session callbacks into the live chat controller. */
class WatchReplies {
    private val callbacks = mutableMapOf<String, (WatchReply) -> Unit>()

    @Synchronized
    fun register(sessionId: String, answer: (WatchReply) -> Unit) {
        callbacks[sessionId] = answer
    }

    @Synchronized
    fun forget(sessionId: String) {
        callbacks.remove(sessionId)
    }

    /** False when nobody is listening (watch ended or app restarted). */
    fun reply(sessionId: String, reply: WatchReply): Boolean {
        val cb = synchronized(this) { callbacks[sessionId] } ?: return false
        cb(reply)
        return true
    }
}
