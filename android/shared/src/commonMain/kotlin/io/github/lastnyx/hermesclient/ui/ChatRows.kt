package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.chat.ChatItem
import io.github.lastnyx.hermesclient.data.chat.TOOL_DETAIL_LIMIT
import io.github.lastnyx.hermesclient.data.chat.ToolStatus

const val COMMAND_OUTPUT_MAX_LINES = 12

@Composable
fun CommandOutputCard(item: ChatItem.CommandOutput) {
    var expanded by rememberSaveable(item.key) { mutableStateOf(false) }
    val long = item.text.lineSequence().count() > COMMAND_OUTPUT_MAX_LINES
    val container = if (item.failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (item.failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER.dp),
        color = container,
        contentColor = content,
    ) {
        Column(Modifier.padding(horizontal = CARD_PADDING_H.dp, vertical = CARD_PADDING_V.dp)) {
            Text(
                item.command,
                style = roles.code,
                color = content.copy(alpha = 0.7f),
            )
            SelectionContainer {
                Text(
                    item.text,
                    Modifier.padding(top = LIST_GAP.dp),
                    style = roles.body,
                    maxLines = if (long && !expanded) COMMAND_OUTPUT_MAX_LINES else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (long) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Show more") }
        }
    }
}

@Composable
fun ReasoningRow(item: ChatItem.Reasoning) {
    // Saveable so the expanded state survives the row leaving the lazy list.
    var open by rememberSaveable(item.key) { mutableStateOf(false) }
    val shown = remember(item.text, item.streaming) { if (item.streaming) item.text else clipForDisplay(item.text) }
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(Modifier.clickable { open = !open }.padding(horizontal = CARD_PADDING_H.dp, vertical = REASONING_VERTICAL_PADDING.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.streaming) CircularProgressIndicator(Modifier.size(PROGRESS_SIZE.dp), strokeWidth = PROGRESS_STROKE.dp)
                Text(
                    if (item.streaming) "Thinking…" else "Thought",
                    Modifier.padding(start = if (item.streaming) ROW_PADDING.dp else 0.dp),
                    style = roles.label,
                )
                val preview = remember(item.text, item.streaming, open) { if (open) "" else reasoningPreview(item.text, item.streaming) }
                Text(
                    preview,
                    Modifier.weight(1f).padding(start = ROW_PADDING.dp),
                    style = roles.secondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Collapse" else "Expand",
                    modifier = Modifier.size(CHEVRON_SIZE.dp),
                )
            }
            if (open) Text(shown, style = roles.body)
        }
    }
}

@Composable
fun ToolRow(item: ChatItem.Tool) {
    // Keyed on status so a finished tool collapses to its final state.
    var open by remember(item.key, item.status) { mutableStateOf(false) }
    val body = toolExpandedText(item.status, item.detail)
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().then(if (body != null) Modifier.clickable { open = !open } else Modifier).padding(horizontal = CARD_PADDING_H.dp, vertical = TOOL_HEADER_VERTICAL_PADDING.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ROW_PADDING.dp),
            ) {
                if (item.status == ToolStatus.Running) CircularProgressIndicator(Modifier.size(PROGRESS_SIZE.dp), strokeWidth = PROGRESS_STROKE.dp)
                else Icon(Icons.Filled.Check, contentDescription = "Done", modifier = Modifier.size(PROGRESS_SIZE.dp))
                Text(toolLabel(item.name), style = roles.label, maxLines = 1)
                Text(
                    item.preview,
                    Modifier.weight(1f),
                    style = roles.code,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.durationS?.let { Text(durationText(it), style = roles.caption) }
                if (body != null) Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Collapse" else "Expand",
                    modifier = Modifier.size(CHEVRON_SIZE.dp),
                )
            }
            if (open && body != null) {
                val shown = remember(body) { toolDetailPreview(body, TOOL_DETAIL_LIMIT) }
                Text(
                    shown,
                    Modifier.fillMaxWidth().heightIn(max = TOOL_DETAIL_MAX_HEIGHT.dp).verticalScroll(rememberScrollState())
                        .padding(horizontal = CARD_PADDING_H.dp, vertical = TOOL_DETAIL_VERTICAL_PADDING.dp),
                    style = roles.code,
                )
                if (item.detail.isNotBlank()) CopyButton(shown)
            }
        }
    }
}

@Composable
fun ToolGroupRow(group: ChatRow.ToolGroup) {
    var open by rememberSaveable(group.key) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(INSIDE_TURN_GAP.dp)) {
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CARD_CORNER.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = CARD_PADDING_H.dp, vertical = TOOL_HEADER_VERTICAL_PADDING.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ROW_PADDING.dp),
            ) {
                if (group.running) CircularProgressIndicator(Modifier.size(PROGRESS_SIZE.dp), strokeWidth = PROGRESS_STROKE.dp)
                else Icon(Icons.Filled.Check, contentDescription = "Done", modifier = Modifier.size(PROGRESS_SIZE.dp))
                Text("Used ${group.tools.size} tools", Modifier.weight(1f), style = roles.label)
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Collapse" else "Expand",
                    modifier = Modifier.size(CHEVRON_SIZE.dp),
                )
            }
        }
        if (open) group.tools.forEach { ToolRow(it) }
    }
}
