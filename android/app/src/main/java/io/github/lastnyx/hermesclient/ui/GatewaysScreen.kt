package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.auth.SignInController
import io.github.lastnyx.hermesclient.data.auth.SignInState
import io.github.lastnyx.hermesclient.data.gateway.Gateway
import io.github.lastnyx.hermesclient.data.gateway.GatewayClient
import io.github.lastnyx.hermesclient.data.gateway.GatewayRegistry
import io.github.lastnyx.hermesclient.data.gateway.GatewayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatewaysScreen(
    registry: GatewayRegistry,
    client: GatewayClient,
    signIn: SignInController,
    onOpenSessions: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    updateStatus: String = "",
) {
    val signStates by signIn.states.collectAsState()
    var gateways by remember { mutableStateOf(registry.list()) }
    var activeId by remember { mutableStateOf(registry.active()?.id) }
    var editing by remember { mutableStateOf<Gateway?>(null) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Gateway?>(null) }
    val testResults = remember { mutableStateMapOf<String, String>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(gateways) { gateways.forEach { signIn.refreshName(it.id, it.url) } }

    fun refresh() { gateways = registry.list(); activeId = registry.active()?.id }

    val activeState = activeId?.let { signStates[it] ?: signIn.stateOf(it) }
    val canOpenSessions = activeState is SignInState.SignedIn
    val showSheet = adding || editing != null || gateways.isEmpty()

    Column(Modifier.padding(horizontal = SCREEN_EDGE.dp), verticalArrangement = Arrangement.spacedBy(ROW_PADDING.dp)) {
        ScreenTopBar("Gateways", onBack = if (canOpenSessions) onOpenSessions else null) {
            IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, contentDescription = "Add") }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
        }
        if (updateStatus.startsWith("Update available")) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(updateStatus, Modifier.padding(ROW_PADDING.dp), style = roles.body)
            }
        }
        if (gateways.isEmpty()) {
            Text("Add a gateway to get started", Modifier.fillMaxWidth().padding(ROW_PADDING.dp), style = roles.body, textAlign = TextAlign.Center)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(LIST_GAP.dp)) {
            items(gateways, key = { it.id }) { gateway ->
                val state = signStates[gateway.id] ?: signIn.stateOf(gateway.id)
                GatewayCard(
                    gateway = gateway,
                    active = gateway.id == activeId,
                    state = state,
                    testResult = testResults[gateway.id],
                    onClick = {
                        if (gateway.id != activeId) { registry.setActive(gateway.id); refresh() }
                        if (state is SignInState.SignedIn) onOpenSessions()
                    },
                    onSignIn = { scope.launch { signIn.signIn(gateway.id, gateway.url) } },
                    onSignOut = { signIn.signOut(gateway.id) },
                    onTest = {
                        testResults[gateway.id] = "Testing…"
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { client.status(gateway.url) }
                            testResults[gateway.id] = result.fold(
                                { "Online, Hermes ${it.version}" },
                                { "Unreachable: ${it.message ?: "error"}" },
                            )
                        }
                    },
                    onEdit = { editing = gateway },
                    onRemove = { removing = gateway },
                )
            }
        }
    }

    removing?.let { gateway ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${gateway.name}?") },
            confirmButton = {
                TextButton(onClick = { registry.remove(gateway.id); removing = null; refresh() }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { adding = false; editing = null }) {
            GatewayForm(
                initial = editing,
                onSubmit = { name, url ->
                    val result = editing?.let { registry.update(it.id, name, url) } ?: registry.add(name, url)
                    result.onSuccess { adding = false; editing = null; refresh() }
                        .exceptionOrNull()?.message
                },
                onCancel = { adding = false; editing = null },
            )
        }
    }
}

/** [onSubmit] returns an error message to show, or null when it saved. */
@Composable
private fun GatewayForm(initial: Gateway?, onSubmit: (String, String) -> String?, onCancel: () -> Unit) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember(initial) { mutableStateOf(initial?.url.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(SCREEN_EDGE.dp), verticalArrangement = Arrangement.spacedBy(ROW_PADDING.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(url, { url = it }, label = { Text("URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (GatewayUrl.warnsCleartext(url)) {
            Text(
                "This address uses http://, so your credentials would cross the network unencrypted.",
                style = roles.body,
                color = MaterialTheme.colorScheme.error,
            )
        }
        error?.let { Text(it, style = roles.body, color = MaterialTheme.colorScheme.error) }
        Row {
            Button(onClick = { error = onSubmit(name, url) }) { Text("Save") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
