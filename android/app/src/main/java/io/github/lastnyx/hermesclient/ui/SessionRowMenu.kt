package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import io.github.lastnyx.hermesclient.data.sessions.Session
import io.github.lastnyx.hermesclient.data.sessions.SessionListModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun SessionRowMenu(
    session: Session,
    archivedPage: Boolean,
    model: SessionListModel,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(session.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                if (archivedPage) {
                    TextButton(onClick = { onDismiss(); scope.launch { model.setArchived(session.id, false) } }) { Text("Unarchive") }
                } else {
                    TextButton(onClick = { onDismiss(); scope.launch { model.setPinned(session.id, !session.pinned) } }) {
                        Text(if (session.pinned) "Unpin" else "Pin")
                    }
                    TextButton(onClick = {
                        onDismiss()
                        scope.launch {
                            model.setArchived(session.id, true)
                            if (model.state.value.error == null) {
                                val result = snackbar.showSnackbar("Archived", actionLabel = "Undo", duration = SnackbarDuration.Short)
                                if (result == SnackbarResult.ActionPerformed) model.setArchived(session.id, false)
                            }
                        }
                    }) { Text("Archive") }
                    TextButton(onClick = { onDismiss(); onRename() }) { Text("Rename") }
                }
                TextButton(onClick = { onDismiss(); onDelete() }) { Text("Delete") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
