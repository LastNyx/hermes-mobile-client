package io.github.lastnyx.hermesclient.ui

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
import io.github.lastnyx.hermesclient.data.media.MediaRef
import io.github.lastnyx.hermesclient.data.media.isImagePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "bmp" -> "image/bmp"
    else -> "application/octet-stream"
}

// The name comes from agent text: it must never climb out of the folder it is written to.
fun safeName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|]"), "_").takeIf { it.isNotBlank() && it.any { c -> c != '.' } } ?: "file"

fun mediaWhat(name: String): String = if (isImagePath(name)) "picture" else "file"

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
            if (actions.canShare) DropdownMenuItem(text = { Text("Share") }, onClick = { fetch { actions.share(ref.name, it) } })
        }
        if (downloading) Text("Downloading…", style = roles.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        (failure ?: actions.error)?.let { Text(it, style = roles.caption, color = MaterialTheme.colorScheme.error) }
    }
}
