package io.github.lastnyx.hermesclient.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.lastnyx.hermesclient.data.chat.ChatState
import io.github.lastnyx.hermesclient.data.chat.PreparedAttachment
import io.github.lastnyx.hermesclient.data.live.LiveLink
import io.github.lastnyx.hermesclient.data.live.NetworkMonitor
import io.github.lastnyx.hermesclient.data.settings.DraftStore
import io.github.lastnyx.hermesclient.data.settings.SessionViewStore
import io.github.lastnyx.hermesclient.data.watch.WatchReply
import kotlinx.coroutines.flow.StateFlow

interface AttachmentPicker {
    fun photos()
    fun files()
}

/** Save and Share for bytes already in memory. */
interface MediaActions {
    val error: String?

    /** False where the platform has no share sheet: the Share item is hidden. */
    val canShare: Boolean
    fun save(name: String, bytes: ByteArray)
    fun share(name: String, bytes: ByteArray)
}

/** What the chat needs from the operating system; each app passes its own. */
interface ChatPlatform {
    /** [onPicked] gets one result per picked item, already read and limited like an upload. */
    @Composable
    fun rememberAttachmentPicker(onPicked: (List<Result<PreparedAttachment>>) -> Unit): AttachmentPicker

    @Composable
    fun rememberMediaActions(): MediaActions

    /** The returned function asks for notification permission at most once per process. */
    @Composable
    fun rememberNotificationAsker(): () -> Unit

    fun turnStarted(sessionId: String, state: StateFlow<ChatState>, link: LiveLink, answer: (WatchReply) -> Unit)
    fun chatClosed(sessionId: String)

    /** Calls [changed] with true when the app is in front and false when it leaves. */
    @Composable
    fun ObserveForeground(changed: (Boolean) -> Unit)

    /** True while a background watch needs the live link to stay open. */
    val watchActive: Boolean
}

/** Everything SessionsHost takes from the platform. */
class HostPlatform(
    val network: NetworkMonitor,
    val viewStore: SessionViewStore,
    val drafts: DraftStore,
    val chat: ChatPlatform,
)

val LocalChatPlatform = staticCompositionLocalOf<ChatPlatform> { error("ChatPlatform not provided") }

@Composable
fun rememberMediaActions(): MediaActions = LocalChatPlatform.current.rememberMediaActions()
