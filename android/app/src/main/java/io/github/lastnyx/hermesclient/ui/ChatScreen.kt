package io.github.lastnyx.hermesclient.ui

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.chat.AttachLimits
import io.github.lastnyx.hermesclient.data.chat.AttachSendResult
import io.github.lastnyx.hermesclient.data.chat.AttachmentReader
import io.github.lastnyx.hermesclient.data.chat.ChatController
import io.github.lastnyx.hermesclient.data.chat.ChatItem
import io.github.lastnyx.hermesclient.data.chat.CheckpointsController
import io.github.lastnyx.hermesclient.data.chat.ModelsController
import io.github.lastnyx.hermesclient.data.chat.NoticeLevel
import io.github.lastnyx.hermesclient.data.chat.Prompt
import io.github.lastnyx.hermesclient.data.chat.SlashCommands
import io.github.lastnyx.hermesclient.data.chat.SlashSuggestion
import io.github.lastnyx.hermesclient.data.chat.parseSlashLine
import io.github.lastnyx.hermesclient.data.live.LinkState
import io.github.lastnyx.hermesclient.data.live.LiveLink
import io.github.lastnyx.hermesclient.data.sessions.SessionsRepository
import io.github.lastnyx.hermesclient.data.settings.DraftStore
import io.github.lastnyx.hermesclient.data.settings.DraftTracker
import io.github.lastnyx.hermesclient.data.settings.draftKey
import io.github.lastnyx.hermesclient.data.settings.flush
import io.github.lastnyx.hermesclient.data.watch.WatchHub
import io.github.lastnyx.hermesclient.data.watch.WatchReply
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Builds the controller for one chat; opens it once the live link is connected. */
@Composable
fun ChatHost(
    storedId: String?,
    link: LiveLink,
    repo: SessionsRepository,
    gatewayId: String,
    drafts: DraftStore,
    modifier: Modifier = Modifier,
    title: String,
    onBack: (() -> Unit)? = null,
    seed: String? = null,
    onBranched: (String) -> Unit = {},
    newChatCwd: String? = null,
) {
    val key = draftKey(gatewayId, storedId)
    val scope = remember(storedId) { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    DisposableEffect(scope) { onDispose { scope.cancel() } }
    val controller = remember(scope) { ChatController(link, repo, storedId, scope, newChatCwd = newChatCwd) }
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
    val beforeSend: suspend () -> Unit = {
        val current = controller.state.value
        if (!current.running) {
            // Denied permission only silences notifications; the service and chat still work.
            if (Build.VERSION.SDK_INT >= 33 && !WatchHub.askedForNotifications) {
                WatchHub.askedForNotifications = true
                askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            (current.storedId ?: current.sessionId)?.let {
                WatchHub.track(context, it, controller.state, link) { reply ->
                    when (reply) {
                        is WatchReply.Approve -> controller.answerApproval(reply.promptId, reply.choice)
                        is WatchReply.Answer -> controller.answerClarify(reply.promptId, mapOf(reply.qid to reply.text))
                    }
                }
            }
        }
    }
    val onSend: suspend (String) -> Boolean = { text ->
        beforeSend()
        controller.send(text)
    }
    val linkState by link.state.collectAsState()
    ChatScreen(controller, models, slash, key, drafts, modifier, onSend, title, onBack, promptActionsEnabled(linkState), seed, onBranched, beforeSend)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    controller: ChatController,
    models: ModelsController,
    slash: SlashCommands,
    key: String,
    drafts: DraftStore,
    modifier: Modifier = Modifier,
    onSend: suspend (String) -> Boolean = { controller.send(it) },
    title: String,
    onBack: (() -> Unit)? = null,
    promptsEnabled: Boolean = true,
    seed: String? = null,
    onBranched: (String) -> Unit = {},
    beforeSend: suspend () -> Unit = {},
) {
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var tray by remember { mutableStateOf(AttachTrayState()) }
    var attachError by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var attachMenu by remember { mutableStateOf(false) }
    fun addPicked(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            val reader = AttachmentReader(context.contentResolver)
            val read = withContext(Dispatchers.IO) { uris.map { reader.read(it) } }
            val failure = read.firstNotNullOfOrNull { it.exceptionOrNull()?.message }
            val (next, limit) = tray.add(read.mapNotNull { it.getOrNull() })
            tray = next
            attachError = failure ?: limit
        }
    }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(AttachLimits.MAX_COUNT)) { addPicked(it) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { addPicked(it) }
    val modelState by models.state.collectAsState()
    var field by remember(key) {
        val saved = seed ?: drafts.get(key)
        mutableStateOf(TextFieldValue(saved, TextRange(saved.length)))
    }
    val draft = field.text
    val tracker = remember { DraftTracker(key, draft) }
    LaunchedEffect(key, draft) {
        delay(300)
        drafts.set(key, draft)
    }
    // A quick Back must not lose the last keystrokes the debounce has not saved yet. The tracker
    // is updated in a SideEffect, which runs after the old key's onDispose, so that save still
    // sees the outgoing chat's key and text.
    DisposableEffect(key) { onDispose { drafts.flush(tracker) } }
    SideEffect { tracker.track(key, draft) }
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
    val slackPx = with(LocalDensity.current) { NEAR_BOTTOM_SLACK.dp.roundToPx() }
    var following by rememberSaveable { mutableStateOf(true) }
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
    // Not while following: the list sits at the end on first open, before it has scrolled there.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            if (index <= 3 && !following && controller.state.value.hasOlder) controller.loadOlder()
        }
    }
    if (picking) ModelPicker(modelState, models, onDismiss = { picking = false })
    var checkpointsOpen by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    val checkpoints = remember(controller) { CheckpointsController(controller) }
    val checkpointsState by checkpoints.state.collectAsState()
    if (checkpointsOpen) {
        LaunchedEffect(Unit) { checkpoints.load() }
        CheckpointsSheet(checkpointsState, checkpoints, state.running, onDismiss = { checkpointsOpen = false })
    }

    Column(modifier.fillMaxSize().padding(horizontal = SCREEN_EDGE.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
            ChatHeaderTitle(
                title,
                modelChipLabel(modelState.model, modelState.reasoningEffort),
                onChip = { picking = true },
                stacked = headerStacked(onBack != null),
            )
            Box {
                IconButton(onClick = { overflow = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                    DropdownMenuItem(text = { Text("Checkpoints") }, onClick = { overflow = false; checkpointsOpen = true })
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            var showEarlier by rememberSaveable(turnKey(state.items)) { mutableStateOf(false) }
            val view = remember(state.items, state.running, showEarlier) { chatRows(state.items, state.running, showEarlier) }
            val rows = view.rows
            val actions = remember(state.items, state.running) { messageActions(state.items, state.running) }
            val placeholder = chatPlaceholder(state)
            when (placeholder) {
                ChatPlaceholder.Opening -> OpeningSpinner()
                ChatPlaceholder.Empty -> EmptyStateText("Say something to start.")
                ChatPlaceholder.None -> Unit
            }
            LazyColumn(Modifier.fillMaxSize().nestedScroll(userScrollWatcher), state = listState) {
                itemsIndexed(rows, key = { _, it -> it.key }) { index, row ->
                    Column(Modifier.padding(top = itemGapBefore(rows.getOrNull(index - 1)?.last, row.first).dp)) {
                        if (row.first.key == view.earlierBeforeKey) {
                            Text(
                                earlierStepsText(view.earlierSteps),
                                Modifier.fillMaxWidth().clickable { showEarlier = true },
                                style = roles.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (row is ChatRow.ToolGroup) {
                            ToolGroupRow(row)
                        } else {
                            when (val item = (row as ChatRow.Single).item) {
                                is ChatItem.Message -> MessageBubble(
                                    item,
                                    actions,
                                    canBranch = !state.running,
                                    onBranch = { scope.launch { controller.branch(item.key)?.let(onBranched) } },
                                    onRetry = { scope.launch { slash.runLine("/retry") } },
                                    onEdit = { rowId, text -> scope.launch { controller.rewind(rowId, text) } },
                                    onRegenerate = {
                                        val prompt = promptBefore(state.items, item.key)
                                        val rowId = prompt?.rowId
                                        if (prompt != null && rowId != null) scope.launch { controller.rewind(rowId, prompt.text) }
                                    },
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
                    val guard = remember(prompt.id) { TapGuard({ SystemClock.elapsedRealtime() }) }
                    Box(Modifier.padding(top = INSIDE_TURN_GAP.dp)) {
                        when (prompt) {
                            is Prompt.Approval -> ApprovalCard(prompt, promptsEnabled, guard) { controller.answerApproval(prompt.id, it) }
                            is Prompt.Clarify -> ClarifyCard(prompt, promptsEnabled, guard) { controller.answerClarify(prompt.id, it) }
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
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, Modifier.size(SMALL_ICON.dp))
                    Text("Jump to latest")
                }
            }
        }
        state.error?.let { Text(it, style = roles.body, color = MaterialTheme.colorScheme.error) }
        attachError?.let { Text(it, style = roles.body, color = MaterialTheme.colorScheme.error) }
        if (suggestions.isNotEmpty() && asksForSuggestions(draft)) {
            SlashSuggestionList(suggestions, { setDraft(composerTextFor(it)) })
        }
        QueuePanel(
            state.queue,
            onSendNow = { id -> scope.launch { controller.sendNow(id) } },
            onEdit = { id -> controller.takeQueued(id)?.let { setDraft(it) } },
            onRemove = { controller.removeQueued(it) },
        )
        SubagentPanel(state.subagents, onStop = { controller.interruptSubagent(it) })
        ProcessPanel(state.processes, onStop = { controller.killProcess(it) }, onDismiss = { controller.dismissProcess(it) })
        state.notices.lastOrNull()?.let { notice ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    notice.text,
                    Modifier.weight(1f),
                    style = roles.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = noticeColor(notice.level),
                )
                TextButton(onClick = { controller.dismissNotice(notice.uid) }) { Text("Dismiss", style = roles.label) }
            }
        }
        if (!tray.isEmpty) AttachTrayRow(tray, onRemove = { tray = tray.remove(it); attachError = null })
        Row(Modifier.fillMaxWidth().padding(vertical = COMPOSER_VERTICAL_PADDING.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                IconButton(onClick = { attachMenu = true }) { Icon(Icons.Filled.AttachFile, contentDescription = "Attach") }
                DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Photos") },
                        onClick = {
                            attachMenu = false
                            attachError = null
                            pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Files") },
                        onClick = {
                            attachMenu = false
                            attachError = null
                            pickFiles.launch(arrayOf("*/*"))
                        },
                    )
                }
            }
            OutlinedTextField(
                field, { field = it }, modifier = Modifier.weight(1f).heightIn(min = COMPOSER_MIN_HEIGHT.dp),
                shape = RoundedCornerShape(COMPOSER_CORNER.dp),
                maxLines = 6,
                placeholder = { Text(if (state.running) "Redirect the reply" else "Message") },
            )
            val sendable = canSend(draft, tray, state.running)
            val icon = if (!sendable && state.running) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send
            val label = if (sendable) (if (state.running) "Redirect" else "Send") else if (state.running) "Stop" else "Send"
            var queueMenu by remember { mutableStateOf(false) }
            Box {
                Surface(
                    shape = CircleShape,
                    color = if (sendable || state.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(start = ROW_PADDING.dp).size(SEND_BUTTON.dp).combinedClickable(
                        enabled = sendable || state.running,
                        role = Role.Button,
                        onLongClick = { if (sendable && state.running) queueMenu = true },
                        onClick = {
                            val text = draft
                            if (sendable && !tray.isEmpty && parseSlashLine(text) == null) {
                                if (!sending) {
                                    sending = true
                                    attachError = null
                                    following = true
                                    scope.launch {
                                        try {
                                            beforeSend()
                                            when (val r = controller.sendWithAttachments(text, tray.items)) {
                                                AttachSendResult.Sent -> { tray = tray.clear(); setDraft("") }
                                                is AttachSendResult.Failed -> { setDraft(r.text); attachError = r.message }
                                            }
                                        } finally {
                                            sending = false
                                        }
                                    }
                                }
                            } else if (sendable) {
                                setDraft("")
                                following = true
                                if (parseSlashLine(text) != null) {
                                    scope.launch { slash.runLine(text)?.let { setDraft(it) } }
                                } else {
                                    scope.launch { if (!onSend(text) && field.text.isEmpty()) setDraft(text) }
                                }
                            } else if (state.running) scope.launch { controller.stop() }
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

@Composable
private fun noticeColor(level: NoticeLevel): Color = when (level) {
    NoticeLevel.Error -> MaterialTheme.colorScheme.error
    NoticeLevel.Warn -> MaterialTheme.colorScheme.tertiary
    NoticeLevel.Info, NoticeLevel.Success -> MaterialTheme.colorScheme.onSurfaceVariant
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
private fun MessageBubble(
    item: ChatItem.Message,
    actions: MessageActions,
    canBranch: Boolean,
    onBranch: () -> Unit,
    onRetry: () -> Unit,
    onEdit: (Long, String) -> Unit,
    onRegenerate: () -> Unit,
) {
    val mine = item.role == "user"
    if (mine) {
        val clipboard = LocalClipboardManager.current
        var menu by remember { mutableStateOf(false) }
        var editing by remember { mutableStateOf(false) }
        val rowId = item.rowId
        val editable = rowId != null && item.key in actions.edit
        if (editing && rowId != null) {
            var text by remember { mutableStateOf(item.text) }
            AlertDialog(
                onDismissRequest = { editing = false },
                title = { Text("Edit message") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(PROMPT_CARD_SPACING.dp)) {
                        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth())
                        Text("Later messages are removed", style = roles.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    TextButton(enabled = text.isNotBlank(), onClick = { editing = false; onEdit(rowId, text) }) { Text("Resend") }
                },
                dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Surface(
                Modifier.widthIn(max = maxWidth * 0.85f).combinedClickable(
                    onClick = {},
                    onLongClick = { menu = true },
                ),
                shape = RoundedCornerShape(BUBBLE_CORNER.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(item.text, Modifier.padding(horizontal = CARD_PADDING_H.dp, vertical = CARD_PADDING_V.dp), style = roles.body)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (editable) DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; editing = true })
                DropdownMenuItem(
                    text = { Text("Copy") },
                    onClick = {
                        menu = false
                        clipboard.setText(AnnotatedString(item.text))
                    },
                )
                if (canBranch) DropdownMenuItem(text = { Text("Branch from here") }, onClick = { menu = false; onBranch() })
            }
        }
    } else if (item.text.isNotEmpty()) {
        val shown = remember(item.text, item.streaming) { if (item.streaming) item.text else clipForDisplay(item.text) }
        Column(Modifier.fillMaxWidth()) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(BUBBLE_CORNER.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                MarkdownText(shown, Modifier.padding(horizontal = CARD_PADDING_H.dp, vertical = CARD_PADDING_V.dp))
            }
            if (!item.streaming) {
                CopyReplyButton(replyForCopy(item.text)) {
                    item.usage?.let { Text(it, style = roles.caption, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (actions.retry == item.key) TextButton(onClick = onRetry) { Text("Retry", style = roles.label) }
                    if (actions.regenerate == item.key) TextButton(onClick = onRegenerate) { Text("Regenerate", style = roles.label) }
                }
            }
        }
    }
}

