package io.github.lastnyx.hermesclient.data.watch

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

const val EXTRA_PROMPT_ID = "hermes.prompt_id"
const val EXTRA_CHOICE = "hermes.choice"

/** Thin glue: routes a notification button to [WatchHub]. If nobody listens it only cancels the notification. */
class WatchActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        val promptId = intent.getStringExtra(EXTRA_PROMPT_ID)
        val choice = intent.getStringExtra(EXTRA_CHOICE)
        if (promptId != null && choice != null) {
            watchAnswer(promptId, choice)?.let { WatchHub.reply(sessionId, it) }
        }
        context.getSystemService(NotificationManager::class.java).cancel(sessionId.hashCode())
    }
}
