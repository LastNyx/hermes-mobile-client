package io.github.lastnyx.hermesclient.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.chat.ChatController
import io.github.lastnyx.hermesclient.data.chat.ChatItem
import io.github.lastnyx.hermesclient.data.chat.ModelsController
import io.github.lastnyx.hermesclient.data.chat.ModelsState
import io.github.lastnyx.hermesclient.data.chat.Prompt
import io.github.lastnyx.hermesclient.data.chat.REASONING_LEVELS
import io.github.lastnyx.hermesclient.data.chat.SlashCommands
import io.github.lastnyx.hermesclient.data.chat.ToolStatus
import io.github.lastnyx.hermesclient.data.chat.parseSlashLine
import io.github.lastnyx.hermesclient.data.live.LinkState
import io.github.lastnyx.hermesclient.data.live.LiveLink
import io.github.lastnyx.hermesclient.data.sessions.SessionsRepository
import io.github.lastnyx.hermesclient.data.watch.WatchHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Builds the controller for one chat; opens it once the live link is connected. */
@Composable
fun ChatHost(
    storedId: String?,
    link: LiveLink,
    repo: SessionsRepository,
    modifier: Modifier = Modifier,
    title: String,
    onBack: (() -> Unit)? = null,
) {
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
    ChatScreen(controller, models, slash, modifier, onSend, title, onBack)
}

@Composable
fun ChatScreen(
    controller: ChatController,
    models: ModelsController,
    slash: SlashCommands,
    modifier: Modifier = Modifier,
    onSend: suspend (String) -> Unit = { controller.send(it) },
    title: String,
    onBack: (() -> Unit)? = null,
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
            Text(
                title,
                Modifier.weight(1f).padding(start = if (onBack == null) 4.dp else 0.dp),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = { picking = true }) {
                Text(
                    modelChipLabel(modelState.model, modelState.reasoningEffort),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
            itemsIndexed(state.items, key = { _, it -> it.key }) { index, item ->
                Box(Modifier.padding(top = itemGapBefore(state.items.getOrNull(index - 1), item).dp)) {
                    when (item) {
                        is ChatItem.Message -> MessageBubble(item)
                        is ChatItem.Reasoning -> ReasoningRow(item)
                        is ChatItem.Tool -> ToolRow(item)
                        is ChatItem.CommandOutput -> CommandOutputCard(item)
                        is ChatItem.Note -> Text(
                            item.text,
                            Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            items(state.prompts, key = { it.id }) { prompt ->
                Box(Modifier.padding(top = INSIDE_TURN_GAP.dp)) {
                    when (prompt) {
                        is Prompt.Approval -> ApprovalCard(prompt) { controller.answerApproval(prompt.id, it) }
                        is Prompt.Clarify -> ClarifyCard(prompt) { controller.answerClarify(prompt.id, it) }
                    }
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
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                draft, { draft = it }, modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                shape = RoundedCornerShape(24.dp),
                maxLines = 6,
                placeholder = { Text(if (state.running) "Steer the reply" else "Message") },
            )
            val sendable = draft.isNotBlank()
            val icon = if (!sendable && state.running) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send
            val label = if (sendable) (if (state.running) "Steer" else "Send") else if (state.running) "Stop" else "Send"
            FilledIconButton(
                onClick = {
                    if (sendable) {
                        val text = draft
                        draft = ""
                        if (parseSlashLine(text) != null) {
                            scope.launch { slash.runLine(text)?.let { draft = it } }
                        } else {
                            scope.launch { onSend(text) }
                        }
                    }
                    else if (state.running) scope.launch { controller.stop() }
                },
                enabled = sendable || state.running,
                modifier = Modifier.padding(start = 8.dp).size(40.dp),
            ) { Icon(icon, contentDescription = label) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ModelPicker(state: ModelsState, models: ModelsController, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = { BottomSheetDefaults.DragHandle() }) {
        LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Model", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
            }
            val notice = state.confirmMessage
            if (notice != null || state.error != null) {
                item {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.padding(12.dp)) {
                            if (notice != null) {
                                Text(notice, color = MaterialTheme.colorScheme.onErrorContainer)
                                Row(Modifier.padding(top = 8.dp)) {
                                    Button(onClick = { scope.launch { models.confirmPending() } }) { Text("Confirm") }
                                    OutlinedButton(onClick = { models.cancelConfirm() }, modifier = Modifier.padding(start = 8.dp)) { Text("Cancel") }
                                }
                            }
                            state.error?.let { Text(it, color = MaterialTheme.colorScheme.onErrorContainer) }
                        }
                    }
                }
            }
            item {
                Text("Reasoning", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    REASONING_LEVELS.forEach { level ->
                        val selected = level == state.reasoningEffort
                        FilterChip(
                            selected = selected,
                            onClick = { scope.launch { models.selectReasoning(level) } },
                            label = { Text(level) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp)) }
                            } else null,
                        )
                    }
                }
                if (state.reasoningEffort !in REASONING_LEVELS && state.reasoningDisplay.isNotBlank()) {
                    Text(
                        "Current: ${state.reasoningDisplay}",
                        Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.groups.forEach { group ->
                item {
                    Text(
                        group.name,
                        Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(group.models) { model ->
                    val current = model == state.model && group.slug == state.provider
                    Surface(
                        onClick = { scope.launch { models.selectModel(group.slug, model) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(model, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            if (current) Icon(Icons.Filled.Check, contentDescription = "Current model")
                        }
                    }
                }
            }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }
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

internal const val INSIDE_TURN_GAP = 4
internal const val REASONING_VERTICAL_PADDING = 10
internal const val TOOL_HEADER_VERTICAL_PADDING = 6
internal const val TOOL_DETAIL_VERTICAL_PADDING = 8
private const val PROMPT_TO_REPLY_GAP = 12

// A turn is a prompt plus the reply's parts: air after the prompt and before the next
// prompt, tight between the reply's own parts.
internal fun itemGapBefore(previous: ChatItem?, item: ChatItem): Int = when {
    previous == null -> 0
    item is ChatItem.Message && item.role == "user" -> PROMPT_TO_REPLY_GAP
    previous is ChatItem.Message && previous.role == "user" -> PROMPT_TO_REPLY_GAP
    else -> INSIDE_TURN_GAP
}

@Composable
private fun MessageBubble(item: ChatItem.Message) {
    val mine = item.role == "user"
    if (mine) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Surface(
                Modifier.widthIn(max = maxWidth * 0.85f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(item.text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else if (item.text.isNotEmpty()) {
        Column(Modifier.fillMaxWidth()) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                MarkdownText(item.text, Modifier.padding(10.dp))
            }
            if (!item.streaming) CopyReplyButton(replyForCopy(item.text))
        }
    }
}

private const val COMMAND_OUTPUT_MAX_LINES = 12

@Composable
private fun CommandOutputCard(item: ChatItem.CommandOutput) {
    var expanded by rememberSaveable(item.key) { mutableStateOf(false) }
    val long = item.text.lineSequence().count() > COMMAND_OUTPUT_MAX_LINES
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (item.failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (item.failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(item.command, style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.7f))
                SelectionContainer {
                    Text(
                        item.text,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (long && !expanded) COMMAND_OUTPUT_MAX_LINES else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (long) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Show more") }
            }
        }
    }
}

@Composable
private fun ReasoningRow(item: ChatItem.Reasoning) {
    // Saveable so the expanded state survives the row leaving the lazy list.
    var open by rememberSaveable(item.key) { mutableStateOf(false) }
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(Modifier.clickable { open = !open }.padding(horizontal = 12.dp, vertical = REASONING_VERTICAL_PADDING.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (item.streaming) "Thinking…" else "Thought",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                )
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                )
            }
            if (open) Text(item.text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ToolRow(item: ChatItem.Tool) {
    var open by remember(item.key) { mutableStateOf(false) }
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 12.dp, vertical = TOOL_HEADER_VERTICAL_PADDING.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (item.status == ToolStatus.Running) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Check, contentDescription = "Done", modifier = Modifier.size(16.dp))
                Text(item.name.ifBlank { "tool" }, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                )
            }
            if (open && item.detail.isNotBlank()) {
                Text(
                    toolDetailPreview(item.detail, 4000),
                    Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = TOOL_DETAIL_VERTICAL_PADDING.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
