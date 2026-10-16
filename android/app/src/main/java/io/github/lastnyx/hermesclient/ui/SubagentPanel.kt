package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.chat.Subagent
import io.github.lastnyx.hermesclient.data.chat.SubagentStatus
import io.github.lastnyx.hermesclient.data.chat.groupSubagents
import io.github.lastnyx.hermesclient.data.chat.subagentMeta
import kotlinx.coroutines.launch

private const val SUBAGENT_MAX_HEIGHT = 200
private const val MAX_INDENT_STEPS = 6
private const val INDENT_STEP = 12

internal fun subagentSummary(all: List<Subagent>): String {
    val running = all.count { it.status == SubagentStatus.Running || it.status == SubagentStatus.Queued }
    val done = all.size - running
    val parts = listOfNotNull(
        if (running > 0) "$running running" else null,
        if (done > 0) "$done done" else null,
    )
    return if (parts.isEmpty()) "Subagents" else "Subagents · ${parts.joinToString(", ")}"
}

/** Subagents (delegate_task children) of the session as plain rows. Shows nothing when there are none. */
@Composable
fun SubagentPanel(subagents: List<Subagent>, onStop: suspend (String) -> Unit, modifier: Modifier = Modifier) {
    if (subagents.isEmpty()) return
    val scope = rememberCoroutineScope()
    val open = remember { mutableStateListOf<String>() }
    val stopping = remember { mutableStateListOf<String>() }
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Text(subagentSummary(subagents), style = roles.label)
        Column(Modifier.heightIn(max = SUBAGENT_MAX_HEIGHT.dp).verticalScroll(rememberScrollState())) {
            groupSubagents(subagents).forEach { group ->
                group.forEach { s ->
                    val failed = s.status == SubagentStatus.Failed || s.status == SubagentStatus.Error || s.status == SubagentStatus.Timeout
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { if (!open.remove(s.key)) open.add(s.key) }
                            .padding(start = (minOf(s.depth ?: 0, MAX_INDENT_STEPS) * INDENT_STEP).dp, top = ROW_PADDING.dp),
                    ) {
                        Text(s.goal, style = roles.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            subagentMeta(s),
                            style = roles.caption,
                            color = if (failed) scheme.error else scheme.onSurfaceVariant,
                        )
                        val id = s.id
                        if (id != null && s.status == SubagentStatus.Running) {
                            TextButton(
                                enabled = id !in stopping,
                                onClick = {
                                    stopping.add(id)
                                    scope.launch { try { onStop(id) } finally { stopping.remove(id) } }
                                },
                            ) { Text("Stop", style = roles.label) }
                        }
                        if (s.key in open) {
                            SelectionContainer {
                                Text(s.activity.joinToString("\n").ifEmpty { "No activity yet" }, Modifier.padding(bottom = LIST_GAP.dp), style = roles.code)
                            }
                        }
                    }
                }
            }
        }
    }
}
