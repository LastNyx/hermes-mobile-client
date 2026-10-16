package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.cron.CronJob
import io.github.lastnyx.hermesclient.data.cron.CronListModel
import kotlinx.coroutines.launch

sealed interface CronEditTarget {
    data object New : CronEditTarget
    data class Existing(val job: CronJob) : CronEditTarget
}

private const val PROMPT_MAX_LINES = 6

@Composable
fun CronEditScreen(target: CronEditTarget, model: CronListModel, onClose: () -> Unit) {
    val job = (target as? CronEditTarget.Existing)?.job
    var name by remember(target) { mutableStateOf(job?.name.orEmpty()) }
    var prompt by remember(target) { mutableStateOf(job?.prompt.orEmpty()) }
    var schedule by remember(target) { mutableStateOf(job?.schedule.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    DisposableEffect(target) {
        model.clearFormError()
        onDispose { model.clearFormError() }
    }
    Column(
        Modifier.imePadding().verticalScroll(rememberScrollState()).padding(horizontal = SCREEN_EDGE.dp),
        verticalArrangement = Arrangement.spacedBy(ROW_PADDING.dp),
    ) {
        ScreenTopBar(if (job == null) "New job" else "Edit job", onBack = onClose)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
        OutlinedTextField(
            prompt, { prompt = it },
            Modifier.fillMaxWidth(),
            label = { Text("Prompt") },
            minLines = 2,
            maxLines = PROMPT_MAX_LINES,
        )
        OutlinedTextField(
            schedule, { schedule = it },
            Modifier.fillMaxWidth(),
            label = { Text("Schedule") },
            supportingText = { Text("every 30m, every 2h, 0 9 * * *, in 2h") },
            singleLine = true,
        )
        state.formError?.let { Text(it, style = roles.body, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                saving = true
                scope.launch {
                    val ok = if (job == null) model.create(name, prompt, schedule) else model.edit(job, name, prompt, schedule)
                    saving = false
                    if (ok) onClose()
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
    }
}
