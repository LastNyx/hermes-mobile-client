package io.github.lastnyx.hermesclient.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import io.github.lastnyx.hermesclient.data.chat.ChatState
import io.github.lastnyx.hermesclient.data.chat.PreparedAttachment
import io.github.lastnyx.hermesclient.data.live.LiveLink
import io.github.lastnyx.hermesclient.data.live.NetworkMonitor
import io.github.lastnyx.hermesclient.data.watch.WatchReply
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

/** A desktop has no way to tell that the network is gone, so the link just retries. */
object AlwaysOnline : NetworkMonitor {
    override val online: StateFlow<Boolean> = MutableStateFlow(true)
}

private fun pickFiles(images: Boolean): List<File> {
    val dialog = FileDialog(null as Frame?, "Attach", FileDialog.LOAD).apply {
        isMultipleMode = true
        if (images) setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
        isVisible = true
    }
    return dialog.files.toList()
}

private class DesktopPicker(
    private val scope: CoroutineScope,
    private val onPicked: (List<Result<PreparedAttachment>>) -> Unit,
) : AttachmentPicker {
    private fun pick(images: Boolean) {
        val files = pickFiles(images)
        if (files.isEmpty()) return
        scope.launch { onPicked(withContext(Dispatchers.IO) { files.map(::readPickedFile) }) }
    }

    override fun photos() = pick(images = true)
    override fun files() = pick(images = false)
}

private class DesktopMediaActions(private val scope: CoroutineScope) : MediaActions {
    override var error by mutableStateOf<String?>(null)
    override val canShare = false

    override fun save(name: String, bytes: ByteArray) {
        error = null
        val dialog = FileDialog(null as Frame?, "Save", FileDialog.SAVE).apply {
            file = safeName(name)
            isVisible = true
        }
        val chosen = dialog.file?.let { File(dialog.directory, it) } ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    chosen.writeBytes(bytes)
                    true
                } catch (e: Exception) {
                    false
                }
            }
            if (!ok) error = "Couldn't save the ${mediaWhat(name)}."
        }
    }

    // Not offered on desktop: the Share item is hidden.
    override fun share(name: String, bytes: ByteArray) = Unit
}

/** Desktop actuals of [ChatPlatform]: file dialogs, no watch service, always in front. */
class DesktopChatPlatform : ChatPlatform {
    @Composable
    override fun rememberAttachmentPicker(onPicked: (List<Result<PreparedAttachment>>) -> Unit): AttachmentPicker {
        val scope = rememberCoroutineScope()
        val latest by rememberUpdatedState(onPicked)
        return remember(scope) { DesktopPicker(scope) { latest(it) } }
    }

    @Composable
    override fun rememberMediaActions(): MediaActions {
        val scope = rememberCoroutineScope()
        return remember(scope) { DesktopMediaActions(scope) }
    }

    @Composable
    override fun rememberNotificationAsker(): () -> Unit = {}

    override fun turnStarted(sessionId: String, state: StateFlow<ChatState>, link: LiveLink, answer: (WatchReply) -> Unit) = Unit
    override fun chatClosed(sessionId: String) = Unit

    @Composable
    override fun ObserveForeground(changed: (Boolean) -> Unit) {
        LaunchedEffect(changed) { changed(true) }
    }

    override val watchActive = false
}
