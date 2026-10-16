package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.cron.CronJob
import io.github.lastnyx.hermesclient.data.cron.CronListModel
import io.github.lastnyx.hermesclient.data.cron.CronRun
import io.github.lastnyx.hermesclient.data.cron.formatCronTime
import io.github.lastnyx.hermesclient.data.cron.formatRunTime
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun CronScreen(model: CronListModel, onEdit: (CronEditTarget) -> Unit, onBack: () -> Unit) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<CronJob?>(null) }
    deleting?.let { job ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this job?") },
            text = { Text(job.name) },
            confirmButton = {
                TextButton(onClick = { deleting = null; scope.launch { model.delete(job.id) } }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    LaunchedEffect(Unit) { model.load() }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = SCREEN_EDGE.dp),
        verticalArrangement = Arrangement.spacedBy(ROW_PADDING.dp),
    ) {
        ScreenTopBar("Scheduled jobs", onBack)
        TextButton(onClick = { onEdit(CronEditTarget.New) }) { Text("New job") }
        state.error?.let { Text(it, style = roles.body, color = MaterialTheme.colorScheme.error) }
        state.notice?.let { Text(it, style = roles.secondary) }
        cronEmptyText(state.jobs.size, state.loading, state.error != null)?.let {
            EmptyStateText(it, Modifier.fillMaxWidth().heightIn(min = EMPTY_STATE_MIN_HEIGHT.dp))
        }
        state.jobs.forEach { job ->
            JobCard(
                job,
                busy = job.id in state.busy,
                runs = if (state.openRuns == job.id) state.runs[job.id] ?: emptyList() else null,
                onRunNow = { scope.launch { model.runNow(job.id) } },
                onPause = { scope.launch { model.pause(job.id) } },
                onResume = { scope.launch { model.resume(job.id) } },
                onRuns = { scope.launch { model.toggleRuns(job.id) } },
                onEdit = { onEdit(CronEditTarget.Existing(job)) },
                onDelete = { deleting = job },
            )
        }
    }
}

@Composable
private fun JobCard(
    job: CronJob,
    busy: Boolean,
    runs: List<CronRun>?,
    onRunNow: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRuns: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    Section(job.name) {
        Text(listOf(job.schedule, job.state).filter { it.isNotEmpty() }.joinToString(" · "), style = roles.secondary)
        formatCronTime(job.nextRunAt, zone)?.let { Text("Next: $it", style = roles.secondary) }
        formatCronTime(job.lastRunAt, zone)?.let {
            Text("Last: $it ${job.lastStatus.orEmpty()}".trim(), style = roles.secondary)
        }
        job.lastError?.let { Text(it, style = roles.secondary, color = MaterialTheme.colorScheme.error) }
        Row {
            if (job.state != "completed") TextButton(enabled = !busy, onClick = onRunNow) { Text("Run now") }
            if (job.state == "paused") {
                TextButton(enabled = !busy, onClick = onResume) { Text("Resume") }
            } else if (job.state != "completed") {
                TextButton(enabled = !busy, onClick = onPause) { Text("Pause") }
            }
            TextButton(enabled = !busy, onClick = onRuns) { Text("Runs") }
            TextButton(enabled = !busy, onClick = onEdit) { Text("Edit") }
            TextButton(enabled = !busy, onClick = onDelete) { Text("Delete") }
        }
        if (runs != null) {
            if (runs.isEmpty()) Text("No runs yet.", style = roles.secondary)
            runs.take(10).forEach { run ->
                Column {
                    Text("${formatRunTime(run.startedAt, zone)} ${run.title}".trim(), style = roles.secondary)
                    if (run.preview.isNotEmpty()) {
                        Text(run.preview, style = roles.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
