package io.github.lastnyx.hermesclient.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.chat.ChatController
import io.github.lastnyx.hermesclient.data.chat.ChatItem
import io.github.lastnyx.hermesclient.data.chat.ModelsController
import io.github.lastnyx.hermesclient.data.chat.ModelsState
import io.github.lastnyx.hermesclient.data.chat.Prompt
import io.github.lastnyx.hermesclient.data.chat.REASONING_LEVELS
import io.github.lastnyx.hermesclient.data.chat.SlashCommands
import io.github.lastnyx.hermesclient.data.chat.SlashSuggestion
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
import kotlinx.coroutines.delay
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

@OptIn(ExperimentalFoundationApi::class)
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
    val scope = rememberCoroutineScope()
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val draft = field.text
    fun setDraft(text: String) { field = TextFieldValue(text, TextRange(text.length)) }
    var suggestions by remember { mutableStateOf(emptyList<SlashSuggestion>()) }
    LaunchedEffect(draft) {
        if (!asksForSuggestions(draft)) {
            suggestions = emptyList()
        } else {
            if (draft != "/") delay(150)
            suggestions = slash.suggestions(draft)
        }
    }
    var picking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val slackPx = with(LocalDensity.current) { 24.dp.roundToPx() }
    var following by remember { mutableStateOf(true) }
    // Only user drags and flings reach this; programmatic scrolls do not, so content growth never unsets it.
    val userScrollWatcher = remember(listState, slackPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                following = listState.isNearBottom(slackPx)
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            info.totalItemsCount to info.visibleItemsInfo.lastOrNull()?.size
        }.collect { if (following) listState.scrollToEnd() }
    }
    if (picking) ModelPicker(modelState, models, onDismiss = { picking = false })

    Column(modifier.fillMaxSize().padding(horizontal = SCREEN_EDGE.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
            Text(
                title,
                Modifier.weight(1f).padding(start = if (onBack == null) 4.dp else 0.dp),
                style = roles.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = { picking = true }) {
                Text(
                    modelChipLabel(modelState.model, modelState.reasoningEffort),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = roles.label,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize().nestedScroll(userScrollWatcher), state = listState) {
                val rows = groupTools(state.items)
                val actions = lastActions(state.items, state.running)
                itemsIndexed(rows, key = { _, it -> it.key }) { index, row ->
                    Box(Modifier.padding(top = itemGapBefore(rows.getOrNull(index - 1)?.last, row.first).dp)) {
                        if (row is ChatRow.ToolGroup) {
                            ToolGroupRow(row)
                        } else {
                            when (val item = (row as ChatRow.Single).item) {
                                is ChatItem.Message -> MessageBubble(
                                    item,
                                    actions,
                                    onRetry = { scope.launch { slash.runLine("/retry") } },
                                    onEdit = { scope.launch { slash.runLine("/undo")?.let { setDraft(it) } } },
                                )
                                is ChatItem.Reasoning -> ReasoningRow(item)
                                is ChatItem.Tool -> ToolRow(item)
                                is ChatItem.CommandOutput -> CommandOutputCard(item)
                                is ChatItem.Note -> Text(
                                    item.text,
                                    Modifier.fillMaxWidth(),
                                    style = roles.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
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
            if (!following) {
                FilledTonalButton(
                    onClick = {
                        following = true
                        scope.launch { listState.scrollToEnd() }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = ROW_PADDING.dp),
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, Modifier.size(18.dp))
                    Text("Jump to latest")
                }
            }
        }
        state.error?.let { Text(it, style = roles.body, color = MaterialTheme.colorScheme.error) }
        if (suggestions.isNotEmpty() && asksForSuggestions(draft)) {
            SlashSuggestionList(suggestions, { setDraft(composerTextFor(it)) })
        }
        QueuePanel(
            state.queue,
            onSendNow = { id -> scope.launch { controller.sendNow(id) } },
            onEdit = { id -> controller.takeQueued(id)?.let { setDraft(it) } },
            onRemove = { controller.removeQueued(it) },
        )
        Row(Modifier.fillMaxWidth().padding(vertical = COMPOSER_VERTICAL_PADDING.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                field, { field = it }, modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                shape = RoundedCornerShape(24.dp),
                maxLines = 6,
                placeholder = { Text(if (state.running) "Redirect the reply" else "Message") },
            )
            val sendable = draft.isNotBlank()
            val icon = if (!sendable && state.running) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send
            val label = if (sendable) (if (state.running) "Redirect" else "Send") else if (state.running) "Stop" else "Send"
            var queueMenu by remember { mutableStateOf(false) }
            Box {
            Surface(
                shape = CircleShape,
                color = if (sendable || state.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(start = ROW_PADDING.dp).size(40.dp).combinedClickable(
                    enabled = sendable || state.running,
                    onLongClick = { if (sendable && state.running) queueMenu = true },
                    onClick = {
                    if (sendable) {
                        val text = draft
                        setDraft("")
                        following = true
                        if (parseSlashLine(text) != null) {
                            scope.launch { slash.runLine(text)?.let { setDraft(it) } }
                        } else {
                            scope.launch { onSend(text) }
                        }
                    }
                    else if (state.running) scope.launch { controller.stop() }
                    },
                ),
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label) }
            }
            DropdownMenu(expanded = queueMenu, onDismissRequest = { queueMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Queue for later") },
                    onClick = {
                        queueMenu = false
                        controller.queue(draft)
                        setDraft("")
                    },
                )
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ModelPicker(state: ModelsState, models: ModelsController, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = { BottomSheetDefaults.DragHandle() }) {
        LazyColumn(Modifier.padding(horizontal = SCREEN_EDGE.dp), verticalArrangement = Arrangement.spacedBy(LIST_GAP.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Model", Modifier.weight(1f), style = roles.title)
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
            }
            val notice = state.confirmMessage
            if (notice != null || state.error != null) {
                item {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.padding(PROMPT_CARD_PADDING.dp)) {
                            if (notice != null) {
                                Text(notice, style = roles.body, color = MaterialTheme.colorScheme.onErrorContainer)
                                Row(Modifier.padding(top = LIST_GAP.dp)) {
                                    Button(onClick = { scope.launch { models.confirmPending() } }) { Text("Confirm") }
                                    OutlinedButton(onClick = { models.cancelConfirm() }, modifier = Modifier.padding(start = LIST_GAP.dp)) { Text("Cancel") }
                                }
                            }
                            state.error?.let { Text(it, style = roles.body, color = MaterialTheme.colorScheme.onErrorContainer) }
                        }
                    }
                }
            }
            item {
                Text("Reasoning", style = roles.label, modifier = Modifier.padding(top = ROW_PADDING.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ROW_PADDING.dp)) {
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
                        Modifier.padding(top = LIST_GAP.dp),
                        style = roles.secondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.groups.forEach { group ->
                item {
                    Text(
                        group.name,
                        Modifier.padding(top = ROW_PADDING.dp),
                        style = roles.label,
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
                        Row(Modifier.padding(horizontal = ROW_PADDING.dp, vertical = ROW_PADDING.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(model, Modifier.weight(1f), style = roles.body)
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
        Column(Modifier.padding(PROMPT_CARD_PADDING.dp), verticalArrangement = Arrangement.spacedBy(PROMPT_CARD_SPACING.dp)) {
            SelectionContainer(Modifier.heightIn(max = promptBodyMaxHeight()).verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PROMPT_CARD_SPACING.dp)) {
                    Text(prompt.toolName.ifBlank { "Approval needed" }, style = roles.title)
                    if (prompt.description.isNotBlank()) Text(prompt.description, style = roles.body)
                    Text(prompt.command, style = roles.code)
                }
            }
            val labels = listOf("once" to "Allow once", "session" to "Allow for session", "always" to "Always allow", "deny" to "Deny")
            labels.filter { it.first in prompt.choices }.forEach { (choice, label) ->
                if (choice == "deny") OutlinedButton(onClick = { onChoose(choice) }, Modifier.fillMaxWidth()) { WrappedLabel(label) }
                else Button(onClick = { onChoose(choice) }, Modifier.fillMaxWidth()) { WrappedLabel(label) }
            }
        }
    }
}

internal fun promptBodyMaxHeight(screenHeight: Dp): Dp = (screenHeight.value * 3 / 5).dp

@Composable
private fun promptBodyMaxHeight(): Dp = promptBodyMaxHeight(LocalConfiguration.current.screenHeightDp.dp)

@Composable
private fun WrappedLabel(text: String) = Text(text, Modifier.fillMaxWidth(), textAlign = TextAlign.Start)

@Composable
private fun ClarifyCard(prompt: Prompt.Clarify, onAnswer: (Map<String, String>) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(PROMPT_CARD_PADDING.dp), verticalArrangement = Arrangement.spacedBy(PROMPT_CARD_SPACING.dp)) {
            // §5.1 documents one string per question id, so multi-select is answered as a single value too.
            var answers by remember(prompt.id) { mutableStateOf(mapOf<String, String>()) }
            Column(
                Modifier.heightIn(max = promptBodyMaxHeight()).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PROMPT_CARD_SPACING.dp),
            ) {
                prompt.questions.forEach { q ->
                    SelectionContainer { Text(q.question, style = roles.body) }
                    q.choices.forEach { choice ->
                        val picked = answers[q.qid] == choice
                        val pick = { answers = answers + (q.qid to choice) }
                        if (picked) Button(onClick = pick, Modifier.fillMaxWidth()) { WrappedLabel(choice) }
                        else OutlinedButton(onClick = pick, Modifier.fillMaxWidth()) { WrappedLabel(choice) }
                    }
                    OutlinedTextField(
                        answers[q.qid].orEmpty().takeIf { it !in q.choices }.orEmpty(),
                        { answers = answers + (q.qid to it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Your answer") },
                    )
                }
            }
            val ready = prompt.questions.all { !answers[it.qid].isNullOrBlank() }
            Button(onClick = { onAnswer(answers.mapValues { it.value.trim() }) }, enabled = ready) { Text("Answer") }
        }
    }
}

// A turn is a prompt plus the reply's parts: air after the prompt and before the next
// prompt, tight between the reply's own parts.
internal fun itemGapBefore(previous: ChatItem?, item: ChatItem): Int = when {
    previous == null -> 0
    item is ChatItem.Message && item.role == "user" -> PROMPT_TO_REPLY_GAP
    previous is ChatItem.Message && previous.role == "user" -> PROMPT_TO_REPLY_GAP
    else -> INSIDE_TURN_GAP
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(item: ChatItem.Message, actions: LastActions, onRetry: () -> Unit, onEdit: () -> Unit) {
    val mine = item.role == "user"
    if (mine) {
        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
        var menu by remember { mutableStateOf(false) }
        val editable = actions.editKey == item.key
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Surface(
                Modifier.widthIn(max = maxWidth * 0.85f).combinedClickable(
                    enabled = editable,
                    onClick = {},
                    onLongClick = { menu = true },
                ),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(item.text, Modifier.padding(horizontal = CARD_PADDING_H.dp, vertical = CARD_PADDING_V.dp), style = roles.body)
            }
            DropdownMenu(expanded = menu && editable, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit and resend") }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(
                    text = { Text("Copy") },
                    onClick = {
                        menu = false
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(item.text))
                    },
                )
            }
        }
    } else if (item.text.isNotEmpty()) {
        Column(Modifier.fillMaxWidth()) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                MarkdownText(item.text, Modifier.padding(horizontal = CARD_PADDING_H.dp, vertical = CARD_PADDING_V.dp))
            }
            if (!item.streaming) {
                CopyReplyButton(replyForCopy(item.text)) {
                    if (actions.retryKey == item.key) TextButton(onClick = onRetry) { Text("Retry", style = roles.label) }
                }
            }
        }
    }
}

private const val COMMAND_OUTPUT_MAX_LINES = 12

@Composable
private fun CommandOutputCard(item: ChatItem.CommandOutput) {
    var expanded by rememberSaveable(item.key) { mutableStateOf(false) }
    val long = item.text.lineSequence().count() > COMMAND_OUTPUT_MAX_LINES
    val container = if (item.failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (item.failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
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
private fun ReasoningRow(item: ChatItem.Reasoning) {
    // Saveable so the expanded state survives the row leaving the lazy list.
    var open by rememberSaveable(item.key) { mutableStateOf(false) }
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(Modifier.clickable { open = !open }.padding(horizontal = CARD_PADDING_H.dp, vertical = REASONING_VERTICAL_PADDING.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.streaming) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    if (item.streaming) "Thinking…" else "Thought",
                    Modifier.padding(start = if (item.streaming) ROW_PADDING.dp else 0.dp),
                    style = roles.label,
                )
                val preview = if (open) "" else reasoningPreview(item.text, item.streaming)
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
                    modifier = Modifier.size(20.dp),
                )
            }
            if (open) Text(item.text, style = roles.body)
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
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = CARD_PADDING_H.dp, vertical = TOOL_HEADER_VERTICAL_PADDING.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ROW_PADDING.dp),
            ) {
                if (item.status == ToolStatus.Running) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Check, contentDescription = "Done", modifier = Modifier.size(16.dp))
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
                        .padding(horizontal = CARD_PADDING_H.dp, vertical = TOOL_DETAIL_VERTICAL_PADDING.dp),
                    style = roles.code,
                )
                CopyButton(toolDetailPreview(item.detail, 4000))
            }
        }
    }
}

@Composable
private fun ToolGroupRow(group: ChatRow.ToolGroup) {
    var open by rememberSaveable(group.key) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(INSIDE_TURN_GAP.dp)) {
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = CARD_PADDING_H.dp, vertical = TOOL_HEADER_VERTICAL_PADDING.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ROW_PADDING.dp),
            ) {
                if (group.running) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Check, contentDescription = "Done", modifier = Modifier.size(16.dp))
                Text("Used ${group.tools.size} tools", Modifier.weight(1f), style = roles.label)
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (open) group.tools.forEach { ToolRow(it) }
    }
}
