package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.chat.ANSI
import io.github.lastnyx.hermesclient.data.chat.BackgroundProcess
import io.github.lastnyx.hermesclient.data.chat.ProcessState
import kotlinx.coroutines.launch

private const val MAX_VISIBLE_ROWS = 4
private const val PROCESS_ROW_HEIGHT = 40

internal fun processSummary(processes: List<BackgroundProcess>): String {
    val parts = listOf(
        ProcessState.Running to "running",
        ProcessState.Failed to "failed",
        ProcessState.Done to "done",
    ).mapNotNull { (state, word) ->
        val n = processes.count { it.state == state }
        if (n == 0) null else "$n $word"
    }
    return if (parts.isEmpty()) "Background" else "Background · ${parts.joinToString(", ")}"
}

internal fun outputTail(output: String?): String? =
    output?.let { ANSI.replace(it, "").replace("\r\n", "\n") }
        ?.takeIf { it.isNotBlank() }?.trimEnd()?.split('\n')
        ?.map { it.substringAfterLast('\r') }
        ?.takeLast(COMMAND_OUTPUT_MAX_LINES)?.joinToString("\n")

internal fun processOutputText(p: BackgroundProcess): String {
    val body = outputTail(p.output)
    val exit = if (p.state == ProcessState.Failed) p.exitCode?.let { "Exit code $it" } else null
    return when {
        body != null && exit != null -> "$body\n$exit"
        body != null -> body
        exit != null -> exit
        p.state == ProcessState.Running -> "No output yet"
        else -> "No output"
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(SESSION_DOT_SIZE.dp).background(color, CircleShape))
}

/** Background processes of the session. Shows nothing when there are none. */
@Composable
fun ProcessPanel(
    processes: List<BackgroundProcess>,
    onStop: suspend (String) -> Unit,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (processes.isEmpty()) return
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shownOutput = remember { mutableStateListOf<String>() }
    val stopping = remember { mutableStateListOf<String>() }
    val scheme = MaterialTheme.colorScheme
    val anyRunning = processes.any { it.state == ProcessState.Running }
    val anyFailed = processes.any { it.state == ProcessState.Failed }
    val headerDot = when {
        anyRunning -> scheme.primary
        anyFailed -> scheme.error
        else -> scheme.onSurfaceVariant
    }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(CARD_CORNER.dp), color = scheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = CARD_PADDING_H.dp, vertical = CARD_PADDING_V.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = PROCESS_ROW_HEIGHT.dp).clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(headerDot)
                Text(processSummary(processes), Modifier.padding(start = ROW_PADDING.dp), style = roles.label)
            }
            if (expanded) {
                Column(Modifier.heightIn(max = (PROCESS_ROW_HEIGHT * MAX_VISIBLE_ROWS).dp).verticalScroll(rememberScrollState())) {
                    processes.forEach { p ->
                        val running = p.state == ProcessState.Running
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = PROCESS_ROW_HEIGHT.dp)
                                .clickable { if (!shownOutput.remove(p.id)) shownOutput.add(p.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatusDot(
                                when (p.state) {
                                    ProcessState.Running -> scheme.primary
                                    ProcessState.Failed -> scheme.error
                                    ProcessState.Done -> scheme.onSurfaceVariant
                                },
                            )
                            Text(
                                p.title,
                                Modifier.weight(1f).padding(start = ROW_PADDING.dp),
                                style = roles.code,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (running) {
                                TextButton(
                                    enabled = p.id !in stopping,
                                    onClick = {
                                        stopping.add(p.id)
                                        scope.launch { try { onStop(p.id) } finally { stopping.remove(p.id) } }
                                    },
                                ) { Text("Stop", style = roles.label) }
                            } else {
                                TextButton(onClick = { onDismiss(p.id) }) { Text("Dismiss", style = roles.label) }
                            }
                        }
                        if (p.id in shownOutput) {
                            SelectionContainer {
                                Text(processOutputText(p), Modifier.padding(bottom = LIST_GAP.dp), style = roles.code)
                            }
                        }
                    }
                }
            }
        }
    }
}
