package io.github.lastnyx.hermesclient.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import io.github.lastnyx.hermesclient.data.media.MediaRef
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "bmp" -> "image/bmp"
    else -> "application/octet-stream"
}

// The name comes from agent text: it must never climb out of cacheDir/media.
private fun safeName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|]"), "_").takeIf { it.isNotBlank() && it.any { c -> c != '.' } } ?: "file"

/** Save (system file picker) and Share (chooser) for bytes already in memory; no permission needed. */
class MediaActions internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val pick: (String) -> Unit,
) {
    var error by mutableStateOf<String?>(null)
    private var pending: ByteArray? = null
    private var pendingWhat = "file"

    private fun whatFor(name: String) = if (mimeFor(name).startsWith("image/")) "picture" else "file"

    fun save(name: String, bytes: ByteArray) {
        error = null
        pending = bytes
        pendingWhat = whatFor(name)
        pick(safeName(name))
    }

    internal fun saved(uri: Uri?) {
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

    fun share(name: String, bytes: ByteArray) {
        error = null
        scope.launch {
            val what = whatFor(name)
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

@Composable
fun rememberMediaActions(): MediaActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val holder = remember { arrayOfNulls<MediaActions>(1) }
    // "*/*": the contract's type is fixed at creation, the suggested name carries the extension.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { holder[0]?.saved(it) }
    return remember(context, scope) { MediaActions(context, scope) { launcher.launch(it) }.also { holder[0] = it } }
}

/** A plain `file: name` line; tap offers Save and Share after downloading the file. */
@Composable
fun MediaFileLine(ref: MediaRef) {
    val access = LocalMedia.current ?: return
    val actions = rememberMediaActions()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    fun fetch(then: (ByteArray) -> Unit) {
        menu = false
        failure = null
        downloading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { access.repo.download(ref.path, access.sessionId()) }
            downloading = false
            result.onSuccess(then).onFailure { failure = it.message }
        }
    }

    Column {
        Text("file: ${ref.name}", Modifier.clickable(enabled = !downloading) { menu = true }, style = roles.body)
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Save") }, onClick = { fetch { actions.save(ref.name, it) } })
            DropdownMenuItem(text = { Text("Share") }, onClick = { fetch { actions.share(ref.name, it) } })
        }
        if (downloading) Text("Downloading…", style = roles.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        (failure ?: actions.error)?.let { Text(it, style = roles.caption, color = MaterialTheme.colorScheme.error) }
    }
}
