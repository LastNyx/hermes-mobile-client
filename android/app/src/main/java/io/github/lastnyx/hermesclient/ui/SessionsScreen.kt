package io.github.lastnyx.hermesclient.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.lastnyx.hermesclient.data.auth.AuthState
import io.github.lastnyx.hermesclient.data.auth.SignInController
import io.github.lastnyx.hermesclient.data.gateway.Gateway
import io.github.lastnyx.hermesclient.data.live.AndroidNetworkMonitor
import io.github.lastnyx.hermesclient.data.live.LinkState
import io.github.lastnyx.hermesclient.data.live.LiveLink
import io.github.lastnyx.hermesclient.data.sessions.CANT_REACH_GATEWAY
import io.github.lastnyx.hermesclient.data.sessions.Session
import io.github.lastnyx.hermesclient.data.sessions.SessionListModel
import io.github.lastnyx.hermesclient.data.sessions.SessionsRepository
import io.github.lastnyx.hermesclient.data.sessions.groupSessions
import io.github.lastnyx.hermesclient.data.sessions.newChatChoices
import io.github.lastnyx.hermesclient.data.settings.DraftStore
import io.github.lastnyx.hermesclient.data.settings.PrefsDraftStorage
import io.github.lastnyx.hermesclient.data.settings.PrefsSessionViewStorage
import io.github.lastnyx.hermesclient.data.settings.SessionViewStore
import io.github.lastnyx.hermesclient.data.settings.draftKey
import io.github.lastnyx.hermesclient.data.share.ShareRequest
import io.github.lastnyx.hermesclient.data.watch.WatchHub
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SessionsScreen(
    model: SessionListModel,
    expired: Boolean,
    onOpen: (String?) -> Unit,
    onGateways: () -> Unit,
    viewStore: SessionViewStore,
    modifier: Modifier = Modifier,
    offline: Boolean = false,
    statusDot: @Composable () -> Unit = {},
    onNewChat: (cwd: String?) -> Unit = { onOpen(null) },
) {
    val state by model.state.collectAsState()
    var newChatMenu by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var projectError by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    var grouping by remember { mutableStateOf(viewStore.grouping()) }
    var statuses by remember { mutableStateOf(viewStore.statusFilter()) }
    var ordering by remember { mutableStateOf(viewStore.ordering()) }
    var viewMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Session?>(null) }
    var deleting by remember { mutableStateOf<Session?>(null) }

    var menuFor by remember { mutableStateOf<Session?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { model.load() }
    LaunchedEffect(query) {
        if (query.isNotEmpty()) delay(300)
        if (query != model.state.value.query) model.search(query)
    }
    BackHandler(enabled = searching && !state.archivedPage) { query = ""; searching = false }
    BackHandler(enabled = state.archivedPage) { scope.launch { model.closeArchived() } }

    Box(modifier) {
        Column(Modifier.padding(horizontal = SCREEN_EDGE.dp), verticalArrangement = Arrangement.spacedBy(ROW_PADDING.dp)) {
            if (state.archivedPage) {
                ScreenTopBar("Archived", onBack = { scope.launch { model.closeArchived() } })
            } else if (searching) {
                SearchField(query, { query = it }, { query = ""; searching = false })
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Chats",
                        Modifier.padding(start = 4.dp),
                        style = screenTitleStyle(MaterialTheme.typography),
                        maxLines = 1,
                    )
                    Box(Modifier.padding(start = LIST_GAP.dp * 2)) { statusDot() }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                    Box {
                        IconButton(onClick = { viewMenu = true }) { Icon(Icons.Filled.FilterList, contentDescription = "View") }
                        SessionViewMenu(
                            expanded = viewMenu,
                            onDismiss = { viewMenu = false },
                            ordering = ordering,
                            grouping = grouping,
                            statuses = statuses,
                            onOrdering = { viewMenu = false; ordering = it; viewStore.setOrdering(it) },
                            onGrouping = { viewMenu = false; grouping = it; viewStore.setGrouping(it) },
                            onStatuses = { statuses = it; viewStore.setStatusFilter(it) },
                        )
                    }
                    Box {
                        IconButton(onClick = {
                            if (newChatChoices(state.projects).isEmpty()) onNewChat(null) else newChatMenu = true
                        }) { Icon(Icons.Filled.Add, contentDescription = "New chat") }
                        DropdownMenu(expanded = newChatMenu, onDismissRequest = { newChatMenu = false }) {
                            newChatChoices(state.projects).forEach { choice ->
                                DropdownMenuItem(
                                    text = { Text(choice.label) },
                                    onClick = { newChatMenu = false; onNewChat(choice.cwd) },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { overflow = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                            DropdownMenuItem(
                                text = { Text("New project") },
                                onClick = { overflow = false; projectError = null; creating = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Archived") },
                                onClick = { overflow = false; scope.launch { query = ""; searching = false; model.openArchived() } },
                            )
                            DropdownMenuItem(text = { Text("Gateways") }, onClick = { overflow = false; onGateways() })
                        }
                    }
                }
            }
            if (expired) {
                Text("Signed out, sign in again", style = roles.body, color = MaterialTheme.colorScheme.error)
                Button(onClick = onGateways) { Text("Go to Gateways") }
            }
            state.error?.let {
                if (offline && it == CANT_REACH_GATEWAY) {
                    Text("Offline", style = roles.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(it, style = roles.body, color = MaterialTheme.colorScheme.error)
                }
            }
            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = { scope.launch { model.refresh() } },
                modifier = Modifier.fillMaxSize(),
            ) {
                val flat = state.query.isNotEmpty() || state.archivedPage
                val groups = if (flat) emptyList() else groupSessions(
                    state.sessions, System.currentTimeMillis() / 1000, grouping, ordering, ZoneId.systemDefault(), statuses,
                    projects = state.projects,
                )
                val emptyText = listEmptyText(state, filtered = !flat && state.sessions.isNotEmpty() && groups.isEmpty())
                LazyColumn(Modifier.fillMaxSize()) {
                    if (emptyText != null) {
                        item(key = "empty") { EmptyStateText(emptyText, Modifier.fillParentMaxSize()) }
                    }
                    if (flat) {
                        itemsIndexed(state.sessions, key = { _, it -> it.id }) { i, session ->
                            SessionRow(session, onOpen, { menuFor = session }, divider = i < state.sessions.lastIndex)
                        }
                    } else {
                        groups.forEach { group ->
                            stickyHeader(key = "group-${group.title}") {
                                Text(
                                    groupHeaderTitle(group.title, group.sessions.size),
                                    Modifier.fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(vertical = SESSION_HEADER_PADDING_V.dp),
                                    style = roles.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            itemsIndexed(group.sessions, key = { _, it -> it.id }) { i, session ->
                                SessionRow(session, onOpen, { menuFor = session }, divider = i < group.sessions.lastIndex)
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    menuFor?.let { session ->
        SessionRowMenu(
            session, state.archivedPage, model, snackbar, scope,
            onDismiss = { menuFor = null },
            onRename = { renaming = session },
            onDelete = { deleting = session },
        )
    }
    renaming?.let { session ->
        var title by remember(session.id) { mutableStateOf(session.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(title, { title = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { renaming = null; scope.launch { model.rename(session.id, title.trim()) } }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        var folder by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(ROW_PADDING.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(folder, { folder = it }, label = { Text("Folder on the gateway, optional") }, singleLine = true)
                    projectError?.let { Text(it, style = roles.body, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        projectError = model.createProject(name, folder)
                        if (projectError == null) creating = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
        )
    }
    deleting?.let { session ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete chat?") },
            text = { Text(session.title.ifBlank { "Untitled" }) },
            confirmButton = {
                TextButton(onClick = { deleting = null; scope.launch { model.delete(session.id) } }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** Active gateway's list; beside the chat placeholder on screens 720 dp wide or more. */
@Composable
fun SessionsHost(
    gateway: Gateway,
    http: OkHttpClient,
    signIn: SignInController,
    onGateways: () -> Unit,
    openSessionId: String? = null,
    share: ShareRequest? = null,
) {
    val authed = remember(gateway.id, gateway.url) { signIn.authed(gateway.id, gateway.url) }
    val linkScope = rememberCoroutineScope()
    val context = LocalContext.current
    val network = remember { AndroidNetworkMonitor(context) }
    val link = remember(authed) { LiveLink(http, authed, gateway.url, linkScope, network) }
    val model = remember(authed, link) {
        SessionListModel(SessionsRepository(authed, gateway.url), Dispatchers.IO) { method, params ->
            link.call(method, params, 10_000)
        }
    }
    val auth by authed.state.collectAsState()
    var chatOpen by rememberSaveable(gateway.id) { mutableStateOf(false) }
    var chatId by rememberSaveable(gateway.id) { mutableStateOf<String?>(null) }
    val wide = LocalConfiguration.current.screenWidthDp >= 720
    var chatKey by rememberSaveable(gateway.id) { mutableStateOf(0) }
    // Shared text for the new chat; the old new-chat screen saves its own text on the way out, so the
    // fresh composer takes this directly instead of reading the store.
    var sharedDraft by remember { mutableStateOf<String?>(null) }
    var chatCwd by remember(gateway.id) { mutableStateOf<String?>(null) }
    val open = { id: String? -> sharedDraft = null; chatCwd = null; chatId = id; chatKey++; chatOpen = true }
    val openNew = { cwd: String? -> sharedDraft = null; chatId = null; chatCwd = cwd; chatKey++; chatOpen = true }
    BackHandler(enabled = chatOpen && !wide) { chatOpen = false }
    LaunchedEffect(openSessionId) { if (openSessionId != null) open(openSessionId) }

    val viewStore = remember { SessionViewStore(PrefsSessionViewStorage(context.applicationContext)) }
    val drafts = remember { DraftStore(PrefsDraftStorage(context.applicationContext)) }
    LaunchedEffect(share) {
        if (share != null) {
            drafts.set(draftKey(gateway.id, null), share.draft)
            open(null)
            sharedDraft = share.draft
        }
    }
    DisposableEffect(network) { onDispose { network.close() } }
    LaunchedEffect(link, model) {
        link.state.collect { model.onLinkState(it) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(link, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                WatchHub.foreground = true
                link.start()
            } else if (event == Lifecycle.Event.ON_STOP) {
                WatchHub.foreground = false
                // The Watch service keeps the link open while a phone-started turn is running or waiting.
                if (!WatchHub.active) link.stop()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); link.stop() }
    }
    LaunchedEffect(auth) { if (auth == AuthState.Expired || auth == AuthState.Unreadable) link.stop() }

    Column(Modifier.fillMaxSize()) {
        val linkState by link.state.collectAsState()
        val offline = showsOfflineLine(linkState)
        val networkOnline by network.online.collectAsState()
        val repo = remember(authed) { SessionsRepository(authed, gateway.url) }
        val listState by model.state.collectAsState()
        val chat: @Composable (Modifier) -> Unit = { m ->
            key(chatId, chatKey) {
                Column(m) {
                    // The list screen shows only the dot; the text line belongs to the chat.
                    LiveLinkIndicator(link, auth, networkOnline, Modifier.padding(horizontal = SCREEN_EDGE.dp))
                    ChatHost(
                        chatId, link, repo, gateway.id, drafts, Modifier.weight(1f),
                        title = chatTitle(listState.sessions, chatId),
                        onBack = if (wide) null else ({ chatOpen = false }),
                        seed = sharedDraft,
                        newChatCwd = chatCwd,
                        onBranched = { id -> open(id); linkScope.launch { model.refresh() } },
                    )
                }
            }
        }
        val dot: @Composable () -> Unit = { LiveLinkDot(link, auth, networkOnline) }
        Box(Modifier.weight(1f)) { SessionsBody(wide, chatOpen, auth, model, open, openNew, onGateways, offline, viewStore, dot, chat) }
    }
}

// Connecting must keep the red error: a failed connect attempt is a genuine "can't reach" report.
internal fun showsOfflineLine(state: LinkState): Boolean = state == LinkState.Offline

@Composable
private fun SessionsBody(
    wide: Boolean,
    chatOpen: Boolean,
    auth: AuthState,
    model: SessionListModel,
    open: (String?) -> Unit,
    openNew: (String?) -> Unit,
    onGateways: () -> Unit,
    offline: Boolean,
    viewStore: SessionViewStore,
    statusDot: @Composable () -> Unit,
    chat: @Composable (Modifier) -> Unit,
) {
    val expired = auth == AuthState.Expired || auth == AuthState.Unreadable
    val plan = paneLayout(wide, chatOpen)
    // One chat call site for every layout: Compose keys state by call site, so a rotation must not move it.
    Row(Modifier.fillMaxSize()) {
        if (plan.showList) {
            val listModifier = if (plan.listFixedWidth) Modifier.width(360.dp).fillMaxHeight() else Modifier.weight(1f).fillMaxHeight()
            SessionsScreen(model, expired, open, onGateways, viewStore, listModifier, offline, statusDot, openNew)
        }
        if (plan.showChat) chat(Modifier.weight(1f)) else if (plan.showPlaceholder) ChatPlaceholder(Modifier.weight(1f))
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().height(SESSION_SEARCH_HEIGHT.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(24.dp))
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            query, onQuery, singleLine = true,
            textStyle = roles.body.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f).focusRequester(focus),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("Search chats", style = roles.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    inner()
                }
            },
        )
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close search") }
    }
}

/** Shown beside the list on wide screens until a chat is opened. */
@Composable
fun ChatPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier.padding(SCREEN_EDGE.dp)) {
        Text("Select or start a chat", style = roles.title)
    }
}
