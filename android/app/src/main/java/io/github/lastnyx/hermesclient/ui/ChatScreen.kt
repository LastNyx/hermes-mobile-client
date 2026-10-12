package io.github.lastnyx.hermesclient.ui

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
import androidx.compose.material3.Button
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
    LaunchedEffect(controller) {
        link.state.first { it == LinkState.Connected }
        controller.open()
    }
    ChatScreen(controller, modifier)
}

@Composable
fun ChatScreen(controller: ChatController, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.items.size, state.items.lastOrNull()) {
        if (state.items.isNotEmpty()) listState.scrollToItem(state.items.size - 1)
    }

    Column(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.items, key = { it.key }) { item ->
                when (item) {
                    is ChatItem.Message -> MessageBubble(item)
                    is ChatItem.Tool -> ToolRow(item)
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                draft, { draft = it }, modifier = Modifier.weight(1f),
                placeholder = { Text(if (state.running) "Steer the reply" else "Message") },
            )
            if (draft.isNotBlank()) {
                Button(onClick = { val text = draft; draft = ""; scope.launch { controller.send(text) } }, modifier = Modifier.padding(start = 8.dp)) {
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
