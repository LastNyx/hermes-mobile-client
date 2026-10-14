package io.github.lastnyx.hermesclient.data.watch

import android.content.Context
import android.content.Intent
import io.github.lastnyx.hermesclient.data.chat.ChatState
import io.github.lastnyx.hermesclient.data.live.LiveLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Process-wide owner shared by the UI and [WatchService]; the decisions live in [WatchPolicy]. */
object WatchHub {
    private val policy = WatchPolicy()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobs = mutableMapOf<String, Job>()
    private val replies = WatchReplies()

    @Volatile var active = false
        private set

    /** Set by the screen: true between ON_START and ON_STOP. */
    @Volatile var foreground = false

    @Volatile private var link: LiveLink? = null

    @Volatile var askedForNotifications = false

    /** Call while the app is in the foreground, as a phone-started turn begins. */
    fun track(context: Context, sessionId: String, state: StateFlow<ChatState>, link: LiveLink, answer: (WatchReply) -> Unit) {
        val app = context.applicationContext
        val notifier = WatchNotifier(app)
        this.link = link
        replies.register(sessionId, answer)
        val alreadyWatched = policy.watching(sessionId)
        policy.turnStarted(sessionId)
        if (!active) {
            active = true
            app.startForegroundService(Intent(app, WatchService::class.java))
        }
        if (alreadyWatched) return
        jobs[sessionId] = scope.launch {
            state.first { s ->
                policy.onState(sessionId, s).forEach(notifier::post)
                !policy.watching(sessionId)
            }
            jobs.remove(sessionId)
            replies.forget(sessionId)
            stopIfIdle(app)
        }
    }

    fun reply(sessionId: String, reply: WatchReply): Boolean = replies.reply(sessionId, reply)

    /** Call when the chat screen goes away: its state flow no longer updates, so the watch ends here. */
    fun chatClosed(context: Context, sessionId: String) {
        val app = context.applicationContext
        replies.forget(sessionId)
        val actions = policy.chatClosed(sessionId)
        if (actions.isEmpty()) return
        jobs.remove(sessionId)?.cancel()
        val notifier = WatchNotifier(app)
        actions.forEach(notifier::post)
        stopIfIdle(app)
    }

    private fun stopIfIdle(app: Context) {
        if (policy.shouldRun || !active) return
        active = false
        app.stopService(Intent(app, WatchService::class.java))
        // Back to the foreground-only rule for the live link, and do not pin it in the process-wide hub.
        if (!foreground) link?.stop()
        link = null
    }
}
