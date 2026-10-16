package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.lastnyx.hermesclient.data.media.FileText
import io.github.lastnyx.hermesclient.data.media.numberLines
import io.github.lastnyx.hermesclient.data.media.pathName
import io.github.lastnyx.hermesclient.data.media.viewerNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-screen plain text view of a gateway file: monospace, line numbers, Save. */
@Composable
fun FileTextViewer(path: String, onDismiss: () -> Unit) {
    val access = LocalMedia.current ?: return
    val actions = rememberMediaActions()
    val scope = rememberCoroutineScope()
    var file by remember(path) { mutableStateOf<FileText?>(null) }
    var failure by remember(path) { mutableStateOf<String?>(null) }
    var saveFailure by remember(path) { mutableStateOf<String?>(null) }
    val name = pathName(path)
    LaunchedEffect(path) {
        withContext(Dispatchers.IO) { access.repo.readText(path) }
            .onSuccess { file = it }
            .onFailure { failure = it.message }
    }

    fun save() {
        saveFailure = null
        scope.launch {
            withContext(Dispatchers.IO) { access.repo.download(path, access.sessionId()) }
                .onSuccess { actions.save(name, it) }
                .onFailure { saveFailure = it.message }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                Column(Modifier.weight(1f)) {
                    Text(name, style = roles.label, maxLines = 1)
                    file?.language?.let { Text(it, style = roles.caption, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                TextButton(onClick = ::save) { Text("Save") }
            }
            (failure ?: saveFailure ?: actions.error)?.let {
                Text(it, Modifier.padding(horizontal = SCREEN_EDGE.dp), style = roles.caption, color = MaterialTheme.colorScheme.error)
            }
            val shown = file
            if (shown == null) {
                if (failure == null) Text("Loading…", Modifier.padding(SCREEN_EDGE.dp), style = roles.caption)
            } else {
                val notice = viewerNotice(shown)
                if (notice != null) Text(notice, Modifier.padding(SCREEN_EDGE.dp), style = roles.caption)
                if (!shown.binary) {
                    val lines = remember(shown) { numberLines(shown.text) }
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = SCREEN_EDGE.dp).horizontalScroll(rememberScrollState())) {
                        items(lines) { Text(it, style = roles.code, softWrap = false) }
                    }
                }
            }
        }
    }
}
