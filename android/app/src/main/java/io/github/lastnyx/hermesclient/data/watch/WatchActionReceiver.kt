package io.github.lastnyx.hermesclient.data.watch

import android.app.NotificationManager
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

const val EXTRA_PROMPT_ID = "hermes.prompt_id"
const val EXTRA_QID = "hermes.qid"
const val EXTRA_CHOICE = "hermes.choice"
const val REMOTE_INPUT_ANSWER = "answer"

/** Thin glue: routes a notification button to [WatchHub]. If nobody listens it only cancels the notification. */
class WatchActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        val promptId = intent.getStringExtra(EXTRA_PROMPT_ID)
        val choice = intent.getStringExtra(EXTRA_CHOICE)
        val qid = intent.getStringExtra(EXTRA_QID)
        if (promptId != null && qid != null) {
            val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(REMOTE_INPUT_ANSWER)?.toString().orEmpty()
            // Blank: keep the notification so the user can try again.
            val reply = watchText(promptId, qid, text) ?: return
            WatchHub.reply(sessionId, reply)
        } else if (promptId != null && choice != null) {
            watchAnswer(promptId, choice)?.let { WatchHub.reply(sessionId, it) }
        }
        context.getSystemService(NotificationManager::class.java).cancel(sessionId.hashCode())
    }
}
