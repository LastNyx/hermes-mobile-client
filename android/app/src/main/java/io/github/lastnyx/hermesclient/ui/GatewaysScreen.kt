package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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

@Composable
fun GatewaysScreen(
    registry: GatewayRegistry,
    client: GatewayClient,
    signIn: SignInController,
    onOpenSessions: () -> Unit = {},
) {
    val signStates by signIn.states.collectAsState()
    var gateways by remember { mutableStateOf(registry.list()) }
    var activeId by remember { mutableStateOf(registry.active()?.id) }
    var editing by remember { mutableStateOf<Gateway?>(null) }
    var adding by remember { mutableStateOf(gateways.isEmpty()) }
    val testResults = remember { mutableStateMapOf<String, String>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(gateways) { gateways.forEach { signIn.refreshName(it.id, it.url) } }

    fun refresh() { gateways = registry.list(); activeId = registry.active()?.id }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Gateways", style = MaterialTheme.typography.headlineMedium)
        val activeState = activeId?.let { signStates[it] ?: signIn.stateOf(it) }
        if (activeState is SignInState.SignedIn) {
            Button(onClick = onOpenSessions) { Text("Open sessions") }
        }
        if (adding || editing != null) {
            GatewayForm(
                initial = editing,
                onSubmit = { name, url ->
                    val result = editing?.let { registry.update(it.id, name, url) } ?: registry.add(name, url)
                    result.onSuccess { adding = false; editing = null; refresh() }
                        .exceptionOrNull()?.message
                },
                onCancel = { adding = false; editing = null },
            )
        } else {
            Button(onClick = { adding = true }) { Text("Add gateway") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(gateways, key = { it.id }) { gateway ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row {
                            RadioButton(selected = gateway.id == activeId, onClick = { registry.setActive(gateway.id); refresh() })
                            Column {
                                Text(gateway.name, style = MaterialTheme.typography.titleMedium)
                                Text(gateway.url, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        testResults[gateway.id]?.let { Text(it) }
                        val state = signStates[gateway.id] ?: signIn.stateOf(gateway.id)
                        Text(
                            when (state) {
                                is SignInState.SignedIn -> if (state.name == "Signed in") state.name else "Signed in as ${state.name}"
                                SignInState.SigningIn -> "Signing in…"
                                is SignInState.SignedOut -> state.message?.let { "Signed out: $it" } ?: "Signed out"
                            },
                        )
                        Row {
                            if (state is SignInState.SignedIn) {
                                TextButton(onClick = { signIn.signOut(gateway.id) }) { Text("Sign out") }
                            } else {
                                TextButton(
                                    enabled = state != SignInState.SigningIn,
                                    onClick = { scope.launch { signIn.signIn(gateway.id, gateway.url) } },
                                ) { Text("Sign in") }
                            }
                            TextButton(onClick = {
                                testResults[gateway.id] = "Testing…"
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { client.status(gateway.url) }
                                    testResults[gateway.id] = result.fold(
                                        { "Online, Hermes ${it.version}" },
                                        { "Unreachable: ${it.message ?: "error"}" },
                                    )
                                }
                            }) { Text("Test") }
                            TextButton(onClick = { editing = gateway }) { Text("Edit") }
                            TextButton(onClick = { registry.remove(gateway.id); refresh() }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }
}

/** [onSubmit] returns an error message to show, or null when it saved. */
@Composable
private fun GatewayForm(initial: Gateway?, onSubmit: (String, String) -> String?, onCancel: () -> Unit) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember(initial) { mutableStateOf(initial?.url.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(url, { url = it }, label = { Text("URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (GatewayUrl.warnsCleartext(url)) {
            Text(
                "This address uses http://, so your credentials would cross the network unencrypted.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row {
            Button(onClick = { error = onSubmit(name, url) }) { Text("Save") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
