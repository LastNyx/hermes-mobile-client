package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.chat.QueueEntry

private const val MAX_VISIBLE_ROWS = 3
private const val QUEUE_ROW_HEIGHT = 40

/** Drafts waiting for the running reply. Shows nothing when the queue is empty. */
@Composable
fun QueuePanel(
    entries: List<QueueEntry>,
    onSendNow: (String) -> Unit,
    onEdit: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = CARD_PADDING_H.dp, vertical = CARD_PADDING_V.dp)) {
            Text("Queued · ${entries.size}", style = roles.label)
            Column(Modifier.heightIn(max = (QUEUE_ROW_HEIGHT * MAX_VISIBLE_ROWS).dp).verticalScroll(rememberScrollState())) {
                entries.forEach { entry ->
                    Row(Modifier.fillMaxWidth().heightIn(min = QUEUE_ROW_HEIGHT.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            entry.text,
                            Modifier.weight(1f),
                            style = roles.body,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = { onSendNow(entry.id) }) { Text("Send now", style = roles.label) }
                        TextButton(onClick = { onEdit(entry.id) }) { Text("Edit", style = roles.label) }
                        TextButton(onClick = { onRemove(entry.id) }) { Text("Remove", style = roles.label) }
                    }
                }
            }
        }
    }
}
