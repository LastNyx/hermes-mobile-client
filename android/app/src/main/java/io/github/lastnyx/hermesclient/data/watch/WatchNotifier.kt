package io.github.lastnyx.hermesclient.data.watch

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import io.github.lastnyx.hermesclient.MainActivity

const val EXTRA_SESSION_ID = "hermes.session_id"
const val WORKING_NOTIFICATION_ID = 1

private const val CHANNEL_WORKING = "working"
private const val CHANNEL_NEEDS_YOU = "needs_you"

/** Android-only: channels and notification building. All wording is fixed; nothing from the chat is shown. */
class WatchNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    private fun ensureChannels() {
        manager.createNotificationChannel(NotificationChannel(CHANNEL_WORKING, "Working", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_NEEDS_YOU, "Needs you", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun canPost() =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun open(sessionId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (sessionId != null) intent.putExtra(EXTRA_SESSION_ID, sessionId)
        return PendingIntent.getActivity(
            context, sessionId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun working(): Notification {
        ensureChannels()
        return Notification.Builder(context, CHANNEL_WORKING)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Hermes is working…")
            .setOngoing(true)
            .setContentIntent(open(null))
            .build()
    }

    fun post(action: WatchAction) {
        if (action is WatchAction.Clear) {
            manager.cancel(action.sessionId.hashCode())
            return
        }
        if (!canPost()) return
        ensureChannels()
        val (title, text) = when (action) {
            is WatchAction.Done -> "Hermes is done" to "Tap to open the chat"
            is WatchAction.Failed -> "Hermes hit a problem" to "Tap to open the chat"
            is WatchAction.NeedsApproval -> "Hermes needs approval" to action.toolName.ifBlank { "A tool" } + " is waiting"
            is WatchAction.NeedsAnswer -> "Hermes has a question" to "Tap to answer"
            is WatchAction.Clear -> return
        }
        val builder = Notification.Builder(context, CHANNEL_NEEDS_YOU)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open(action.sessionId))
        if (action is WatchAction.NeedsApproval) {
            builder.addAction(choiceAction(action, "Allow once", "once"))
            builder.addAction(choiceAction(action, "Deny", "deny"))
        }
        if (action is WatchAction.NeedsAnswer && action.qid != null) builder.addAction(replyAction(action, action.qid))
        manager.notify(action.sessionId.hashCode(), builder.build())
    }

    private fun actionIntent(sessionId: String, promptId: String?) =
        Intent(context, WatchActionReceiver::class.java)
            .putExtra(EXTRA_SESSION_ID, sessionId)
            .putExtra(EXTRA_PROMPT_ID, promptId)

    private fun replyAction(action: WatchAction.NeedsAnswer, qid: String): Notification.Action {
        val intent = actionIntent(action.sessionId, action.promptId).putExtra(EXTRA_QID, qid)
        // Mutable so the system can attach the typed text to the intent.
        val pending = PendingIntent.getBroadcast(
            context, (action.sessionId + "answer").hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val input = RemoteInput.Builder(REMOTE_INPUT_ANSWER).setLabel("Your answer").build()
        return Notification.Action.Builder(null, "Reply", pending).addRemoteInput(input).build()
    }

    private fun choiceAction(action: WatchAction.NeedsApproval, label: String, choice: String): Notification.Action {
        val intent = actionIntent(action.sessionId, action.promptId).putExtra(EXTRA_CHOICE, choice)
        val pending = PendingIntent.getBroadcast(
            context, (action.sessionId + choice).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(null, label, pending).build()
    }
}
