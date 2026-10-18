package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.chat.CheckpointsController
import io.github.lastnyx.hermesclient.data.chat.CheckpointsState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckpointsSheet(
    state: CheckpointsState,
    controller: CheckpointsController,
    running: Boolean,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf(false) }
    val canRestore = state.diffFor != null && !running && !state.busy

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Restore this checkpoint?") },
            text = { Text("This resets your files to the checkpoint and removes the chat turns after it. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    state.diffFor?.let { hash -> scope.launch { controller.restore(hash) } }
                }) { Text("Restore", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = { BottomSheetDefaults.DragHandle() }) {
        LazyColumn(Modifier.padding(horizontal = SCREEN_EDGE.dp), verticalArrangement = Arrangement.spacedBy(LIST_GAP.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Checkpoints", Modifier.weight(1f), style = roles.title)
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
            }
            if (state.enabled == false) {
                item { Text("Checkpoints are off on this gateway", style = roles.body) }
            }
            items(state.checkpoints, key = { it.hash }) { cp ->
                val selected = cp.hash == state.diffFor
                Surface(
                    onClick = { scope.launch { controller.showDiff(cp.hash) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CARD_CORNER.dp),
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                ) {
                    Column(Modifier.padding(ROW_PADDING.dp)) {
                        Text(cp.timestamp, style = roles.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(cp.message, style = roles.body)
                        Text(cp.hash.take(8), style = roles.code)
                    }
                }
            }
            state.diffText?.let { text ->
                item {
                    SelectionContainer { Text(text, style = roles.code) }
                    TextButton(onClick = { confirming = true }, enabled = canRestore) { Text("Restore") }
                }
            }
            state.message?.let { item { Text(it, style = roles.body) } }
            item { Spacer(Modifier.size(SCREEN_EDGE.dp)) }
        }
    }
}
