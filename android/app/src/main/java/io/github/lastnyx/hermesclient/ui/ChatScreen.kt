package io.github.lastnyx.hermesclient.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import io.github.lastnyx.hermesclient.data.watch.WatchHub
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import io.github.lastnyx.hermesclient.data.chat.ModelsController
import io.github.lastnyx.hermesclient.data.chat.ModelsState
import io.github.lastnyx.hermesclient.data.chat.REASONING_LEVELS
import io.github.lastnyx.hermesclient.data.chat.SlashCommands
import androidx.compose.material3.OutlinedButton
import io.github.lastnyx.hermesclient.data.chat.Prompt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.chat.ChatController
import io.github.lastnyx.hermesclient.data.chat.ChatItem
import io.github.lastnyx.hermesclient.data.chat.ToolStatus
import io.github.lastnyx.hermesclient.data.live.LinkState
import io.github.lastnyx.hermesclient.data.live.LiveLink
import io.github.lastnyx.hermesclient.data.sessions.SessionsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Builds the controller for one chat; opens it once the live link is connected. */
@Composable
fun ChatHost(storedId: String?, link: LiveLink, repo: SessionsRepository, modifier: Modifier = Modifier) {
    val scope = remember(storedId) { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    DisposableEffect(scope) { onDispose { scope.cancel() } }
    val controller = remember(scope) { ChatController(link, repo, storedId, scope) }
    val models = remember(controller) { ModelsController(controller) }
    val slash = remember(controller) { SlashCommands(controller) }
    LaunchedEffect(controller) {
        link.state.first { it == LinkState.Connected }
        controller.open()
        models.load()
        slash.load()
    }
    val context = LocalContext.current
    DisposableEffect(controller) {
        onDispose { controller.state.value.let { s -> (s.storedId ?: s.sessionId)?.let { WatchHub.chatClosed(context, it) } } }
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val onSend: suspend (String) -> Unit = { text ->
        val current = controller.state.value
        if (!current.running) {
            // Denied permission only silences notifications; the service and chat still work.
            if (Build.VERSION.SDK_INT >= 33 && !WatchHub.askedForNotifications) {
                WatchHub.askedForNotifications = true
                askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            (current.storedId ?: current.sessionId)?.let { WatchHub.track(context, it, controller.state, link) }
        }
        controller.send(text)
    }
    ChatScreen(controller, models, slash, modifier, onSend)
}

@Composable
fun ChatScreen(
    controller: ChatController,
    models: ModelsController,
    slash: SlashCommands,
    modifier: Modifier = Modifier,
    onSend: suspend (String) -> Unit = { controller.send(it) },
) {
    val state by controller.state.collectAsState()
    val modelState by models.state.collectAsState()
    val commands by slash.commands.collectAsState()
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.items.size, state.items.lastOrNull()) {
        if (state.items.isNotEmpty()) listState.scrollToItem(state.items.size - 1)
    }
    if (picking) ModelPicker(modelState, models, onDismiss = { picking = false })

    Column(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        TextButton(onClick = { picking = true }) { Text(modelState.model.ifBlank { "Model" }) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.items, key = { it.key }) { item ->
                when (item) {
                    is ChatItem.Message -> MessageBubble(item)
                    is ChatItem.Tool -> ToolRow(item)
                    is ChatItem.Note -> Text(item.text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.prompts, key = { it.id }) { prompt ->
                when (prompt) {
                    is Prompt.Approval -> ApprovalCard(prompt) { controller.answerApproval(prompt.id, it) }
                    is Prompt.Clarify -> ClarifyCard(prompt) { controller.answerClarify(prompt.id, it) }
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (draft.startsWith("/") && ' ' !in draft) {
            slash.filter(draft).take(6).forEach { cmd ->
                Text(
                    "/${cmd.name}  ${cmd.description}",
                    Modifier.fillMaxWidth().clickable { draft = ""; scope.launch { slash.run(cmd.name) } }.padding(vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                draft, { draft = it }, modifier = Modifier.weight(1f),
                placeholder = { Text(if (state.running) "Steer the reply" else "Message") },
            )
            if (draft.isNotBlank()) {
                Button(onClick = { val text = draft; draft = ""; scope.launch { onSend(text) } }, modifier = Modifier.padding(start = 8.dp)) {
                    Text(if (state.running) "Steer" else "Send")
                }
            } else if (state.running) {
                Button(onClick = { scope.launch { controller.stop() } }, modifier = Modifier.padding(start = 8.dp)) { Text("Stop") }
            } else {
                Button(onClick = {}, enabled = false, modifier = Modifier.padding(start = 8.dp)) { Text("Send") }
            }
        }
    }
}

@Composable
private fun ModelPicker(state: ModelsState, models: ModelsController, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Model") },
        text = {
            LazyColumn {
                item {
                    Text("Reasoning", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        REASONING_LEVELS.forEach { level ->
                            TextButton(onClick = { scope.launch { models.selectReasoning(level) } }) {
                                Text(if (level == state.reasoningEffort) "[$level]" else level)
                            }
                        }
                    }
                }
                state.groups.forEach { group ->
                    item { Text(group.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
                    items(group.models) { model ->
                        val current = model == state.model && group.slug == state.provider
                        Text(
                            if (current) "• $model" else model,
                            Modifier.fillMaxWidth().clickable { scope.launch { models.selectModel(group.slug, model) } }.padding(vertical = 6.dp),
                        )
                    }
                }
                state.confirmMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            }
        },
    )
}

@Composable
private fun ApprovalCard(prompt: Prompt.Approval, onChoose: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(prompt.toolName.ifBlank { "Approval needed" }, style = MaterialTheme.typography.titleSmall)
            if (prompt.description.isNotBlank()) Text(prompt.description, style = MaterialTheme.typography.bodyMedium)
            Text(prompt.command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            val labels = listOf("once" to "Allow once", "session" to "Allow for session", "always" to "Always allow", "deny" to "Deny")
            labels.filter { it.first in prompt.choices }.forEach { (choice, label) ->
                if (choice == "deny") OutlinedButton(onClick = { onChoose(choice) }) { Text(label) }
                else Button(onClick = { onChoose(choice) }) { Text(label) }
            }
        }
    }
}

@Composable
private fun ClarifyCard(prompt: Prompt.Clarify, onAnswer: (Map<String, String>) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // §5.1 documents one string per question id, so multi-select is answered as a single value too.
            var answers by remember(prompt.id) { mutableStateOf(mapOf<String, String>()) }
            prompt.questions.forEach { q ->
                Text(q.question, style = MaterialTheme.typography.bodyMedium)
                q.choices.forEach { choice ->
                    val picked = answers[q.qid] == choice
                    val pick = { answers = answers + (q.qid to choice) }
                    if (picked) Button(onClick = pick) { Text(choice) } else OutlinedButton(onClick = pick) { Text(choice) }
                }
                OutlinedTextField(
                    answers[q.qid].orEmpty().takeIf { it !in q.choices }.orEmpty(),
                    { answers = answers + (q.qid to it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Your answer") },
                )
            }
            val ready = prompt.questions.all { !answers[it.qid].isNullOrBlank() }
            Button(onClick = { onAnswer(answers.mapValues { it.value.trim() }) }, enabled = ready) { Text("Answer") }
        }
    }
}

@Composable
private fun MessageBubble(item: ChatItem.Message) {
    val mine = item.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Column(Modifier.padding(vertical = 2.dp)) {
            splitCode(item.text).forEach { (code, text) ->
                if (code) {
                    Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
                } else if (text.isNotEmpty()) {
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ToolRow(item: ChatItem.Tool) {
    var open by remember(item.key) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 4.dp)) {
        Text(
            "${item.name.ifBlank { "tool" }} · ${if (item.status == ToolStatus.Running) "running" else "done"}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (open && item.detail.isNotBlank()) {
            Text(item.detail, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Basic Markdown: fenced ``` blocks become monospace; everything else stays plain text. */
private fun splitCode(text: String): List<Pair<Boolean, String>> =
    text.split("```").mapIndexed { i, part -> (i % 2 == 1) to (if (i % 2 == 1) part.substringAfter('\n', part).trimEnd() else part.trim('\n')) }
