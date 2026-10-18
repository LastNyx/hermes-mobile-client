package io.github.lastnyx.hermesclient.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.lastnyx.hermesclient.data.chat.AttachLimits
import io.github.lastnyx.hermesclient.data.chat.AttachmentReader
import io.github.lastnyx.hermesclient.data.chat.ChatState
import io.github.lastnyx.hermesclient.data.chat.PreparedAttachment
import io.github.lastnyx.hermesclient.data.live.AndroidNetworkMonitor
import io.github.lastnyx.hermesclient.data.live.LiveLink
import io.github.lastnyx.hermesclient.data.settings.DraftStore
import io.github.lastnyx.hermesclient.data.settings.PrefsDraftStorage
import io.github.lastnyx.hermesclient.data.settings.PrefsSessionViewStorage
import io.github.lastnyx.hermesclient.data.settings.SessionViewStore
import io.github.lastnyx.hermesclient.data.watch.WatchHub
import io.github.lastnyx.hermesclient.data.watch.WatchReply
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Wraps the phone's pickers, share sheet, permission prompt, Watch service and lifecycle. */
class AndroidChatPlatform(private val context: Context) : ChatPlatform {
    @Composable
    override fun rememberAttachmentPicker(onPicked: (List<Result<PreparedAttachment>>) -> Unit): AttachmentPicker {
        val scope = rememberCoroutineScope()
        val resolver = LocalContext.current.contentResolver
        val latest by rememberUpdatedState(onPicked)
        fun read(uris: List<Uri>) {
            if (uris.isEmpty()) return
            scope.launch {
                val reader = AttachmentReader(resolver)
                latest(withContext(Dispatchers.IO) { uris.map { reader.read(it) } })
            }
        }
        val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(AttachLimits.MAX_COUNT)) { read(it) }
        val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { read(it) }
        return remember(pickPhotos, pickFiles) {
            object : AttachmentPicker {
                override fun photos() = pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                override fun files() = pickFiles.launch(arrayOf("*/*"))
            }
        }
    }

    @Composable
    override fun rememberMediaActions(): MediaActions {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val holder = remember { arrayOfNulls<AndroidMediaActions>(1) }
        // "*/*": the contract's type is fixed at creation, the suggested name carries the extension.
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { holder[0]?.saved(it) }
        return remember(context, scope) { AndroidMediaActions(context, scope) { launcher.launch(it) }.also { holder[0] = it } }
    }

    @Composable
    override fun rememberNotificationAsker(): () -> Unit {
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        return {
            if (Build.VERSION.SDK_INT >= 33 && !WatchHub.askedForNotifications) {
                WatchHub.askedForNotifications = true
                ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun turnStarted(sessionId: String, state: StateFlow<ChatState>, link: LiveLink, answer: (WatchReply) -> Unit) =
        WatchHub.track(context, sessionId, state, link, answer)

    override fun chatClosed(sessionId: String) = WatchHub.chatClosed(context, sessionId)

    @Composable
    override fun ObserveForeground(changed: (Boolean) -> Unit) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(changed, lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    WatchHub.foreground = true
                    changed(true)
                } else if (event == Lifecycle.Event.ON_STOP) {
                    WatchHub.foreground = false
                    changed(false)
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
    }

    override val watchActive: Boolean get() = WatchHub.active
}

/** Save (system file picker) and Share (chooser) for bytes already in memory; no permission needed. */
private class AndroidMediaActions(
    private val context: Context,
    private val scope: CoroutineScope,
    private val pick: (String) -> Unit,
) : MediaActions {
    override var error by mutableStateOf<String?>(null)
    override val canShare = true
    private var pending: ByteArray? = null
    private var pendingWhat = "file"

    override fun save(name: String, bytes: ByteArray) {
        error = null
        pending = bytes
        pendingWhat = mediaWhat(name)
        pick(safeName(name))
    }

    fun saved(uri: Uri?) {
        val bytes = pending ?: return
        pending = null
        if (uri == null) return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
                } catch (e: Exception) {
                    false
                }
            }
            if (!ok) error = "Couldn't save the $pendingWhat."
        }
    }

    override fun share(name: String, bytes: ByteArray) {
        error = null
        scope.launch {
            val what = mediaWhat(name)
            try {
                val uri = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "media").apply { mkdirs() }
                    val file = File(dir, safeName(name)).apply { writeBytes(bytes) }
                    FileProvider.getUriForFile(context, "${context.packageName}.media", file)
                }
                val send = Intent(Intent.ACTION_SEND).setType(mimeFor(name))
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                error = "Couldn't share the $what."
            }
        }
    }
}

/** The phone's objects for SessionsHost; the network monitor is closed when the host leaves. */
@Composable
fun rememberAndroidPlatform(): HostPlatform {
    val context = LocalContext.current
    val network = remember { AndroidNetworkMonitor(context) }
    DisposableEffect(network) { onDispose { network.close() } }
    return remember(network) {
        val app = context.applicationContext
        HostPlatform(
            network,
            SessionViewStore(PrefsSessionViewStorage(app)),
            DraftStore(PrefsDraftStorage(app)),
            AndroidChatPlatform(app),
        )
    }
}
