package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.lastnyx.hermesclient.data.watch.WatchHub
import io.github.lastnyx.hermesclient.data.live.AndroidNetworkMonitor
import io.github.lastnyx.hermesclient.data.live.LinkState
import io.github.lastnyx.hermesclient.data.live.LiveLink
import androidx.compose.ui.platform.LocalContext
import io.github.lastnyx.hermesclient.data.auth.AuthState
import io.github.lastnyx.hermesclient.data.auth.SignInController
import io.github.lastnyx.hermesclient.data.gateway.Gateway
import io.github.lastnyx.hermesclient.data.sessions.CANT_REACH_GATEWAY
import io.github.lastnyx.hermesclient.data.sessions.Session
import io.github.lastnyx.hermesclient.data.sessions.SessionListModel
import io.github.lastnyx.hermesclient.data.sessions.SessionsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SessionsScreen(
    model: SessionListModel,
    expired: Boolean,
    onOpen: (String?) -> Unit,
    onGateways: () -> Unit,
    modifier: Modifier = Modifier,
    offline: Boolean = false,
) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Session?>(null) }
    var deleting by remember { mutableStateOf<Session?>(null) }

    LaunchedEffect(Unit) { model.load() }
    LaunchedEffect(query) {
        if (query.isNotEmpty()) delay(300)
        if (query != model.state.value.query) model.search(query)
    }

    Column(modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Sessions", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onGateways) { Text("Gateways") }
        }
        if (expired) {
            Text("Signed out, sign in again", color = MaterialTheme.colorScheme.error)
            Button(onClick = onGateways) { Text("Go to Gateways") }
        }
        Button(onClick = { onOpen(null) }) { Text("New chat") }
        OutlinedTextField(
            query, { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let {
            if (offline && it == CANT_REACH_GATEWAY) {
                Text("Offline", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = { scope.launch { model.refresh() } },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.sessions, key = { it.id }) { session ->
                    Column(
                        Modifier.fillMaxWidth()
                            .combinedClickable(onClick = { onOpen(session.id) }, onLongClick = { renaming = session })
                            .padding(vertical = 8.dp),
                    ) {
                        Row {
                            Text(
                                session.title.ifBlank { "Untitled" },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(relativeTime(session.lastActive), style = MaterialTheme.typography.bodySmall)
                        }
                        if (session.preview.isNotBlank()) {
                            Text(session.preview, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
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
            dismissButton = {
                Row {
                    TextButton(onClick = { renaming = null; deleting = session }) { Text("Delete") }
                    TextButton(onClick = { renaming = null }) { Text("Cancel") }
                }
            },
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

private fun relativeTime(epochSeconds: Double, nowMs: Long = System.currentTimeMillis()): String {
    if (epochSeconds <= 0) return ""
    val seconds = (nowMs / 1000 - epochSeconds.toLong()).coerceAtLeast(0)
    return when {
        seconds < 60 -> "now"
        seconds < 3600 -> "${seconds / 60} min ago"
        seconds < 86400 -> "${seconds / 3600} h ago"
        else -> "${seconds / 86400} d ago"
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
) {
    val authed = remember(gateway.id, gateway.url) { signIn.authed(gateway.id, gateway.url) }
    val model = remember(authed) { SessionListModel(SessionsRepository(authed, gateway.url), Dispatchers.IO) }
    val auth by authed.state.collectAsState()
    var chatOpen by remember(gateway.id) { mutableStateOf(false) }
    var chatId by remember(gateway.id) { mutableStateOf<String?>(null) }
    val wide = LocalConfiguration.current.screenWidthDp >= 720
    var chatKey by remember(gateway.id) { mutableStateOf(0) }
    val open = { id: String? -> chatId = id; chatKey++; chatOpen = true }
    BackHandler(enabled = chatOpen && !wide) { chatOpen = false }
    LaunchedEffect(openSessionId) { if (openSessionId != null) open(openSessionId) }

    val linkScope = rememberCoroutineScope()
    val context = LocalContext.current
    val network = remember { AndroidNetworkMonitor(context) }
    DisposableEffect(network) { onDispose { network.close() } }
    val link = remember(authed) { LiveLink(http, authed, gateway.url, linkScope, network) }
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
    LaunchedEffect(auth) { if (auth == AuthState.Expired) link.stop() }

    Column(Modifier.fillMaxSize()) {
        LiveLinkIndicator(link, Modifier.padding(horizontal = 12.dp))
        val linkState by link.state.collectAsState()
        val offline = showsOfflineLine(linkState)
        val repo = remember(authed) { SessionsRepository(authed, gateway.url) }
        val listState by model.state.collectAsState()
        val chat: @Composable (Modifier) -> Unit = { m ->
            key(chatId, chatKey) {
                ChatHost(
                    chatId, link, repo, m,
                    title = chatTitle(listState.sessions, chatId),
                    onBack = if (wide) null else ({ chatOpen = false }),
                )
            }
        }
        Box(Modifier.weight(1f)) { SessionsBody(wide, chatOpen, auth, model, open, onGateways, offline, chat) }
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
    onGateways: () -> Unit,
    offline: Boolean,
    chat: @Composable (Modifier) -> Unit,
) {
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            SessionsScreen(model, auth == AuthState.Expired, open, onGateways, Modifier.width(360.dp).fillMaxHeight(), offline)
            if (chatOpen) chat(Modifier.weight(1f)) else ChatPlaceholder(Modifier.weight(1f))
        }
    } else if (chatOpen) {
        chat(Modifier.fillMaxSize())
    } else {
        SessionsScreen(model, auth == AuthState.Expired, open, onGateways, Modifier.fillMaxSize(), offline)
    }
}

/** Shown beside the list on wide screens until a chat is opened. */
@Composable
fun ChatPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp)) {
        Text("Select or start a chat", style = MaterialTheme.typography.titleMedium)
    }
}
