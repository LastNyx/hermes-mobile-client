package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.sessions.SessionGrouping
import io.github.lastnyx.hermesclient.data.sessions.SessionOrdering
import io.github.lastnyx.hermesclient.data.sessions.SessionStatus

@Composable
fun SessionViewMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    ordering: SessionOrdering,
    grouping: SessionGrouping,
    statuses: Set<SessionStatus>,
    onOrdering: (SessionOrdering) -> Unit,
    onGrouping: (SessionGrouping) -> Unit,
    onStatuses: (Set<SessionStatus>) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        SessionOrdering.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text((if (option == ordering) "✓ " else "") + option.label) },
                onClick = { onOrdering(option) },
            )
        }
        HorizontalDivider()
        Text("Group by", style = roles.caption, modifier = Modifier.padding(horizontal = ROW_PADDING.dp))
        SessionGrouping.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text((if (option == grouping) "✓ " else "") + option.label) },
                onClick = { onGrouping(option) },
            )
        }
        HorizontalDivider()
        Text("Show", style = roles.caption, modifier = Modifier.padding(horizontal = ROW_PADDING.dp))
        SessionStatus.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text((if (option in statuses) "☑ " else "☐ ") + option.label) },
                onClick = { onStatuses(if (option in statuses) statuses - option else statuses + option) },
            )
        }
    }
}
