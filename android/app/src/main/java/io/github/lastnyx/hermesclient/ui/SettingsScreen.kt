package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.update.UpdateController
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(updates: UpdateController, versionName: String, onBack: () -> Unit) {
    val ui by updates.state.collectAsState()
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Version $versionName")
        Button(enabled = !ui.busy, onClick = { scope.launch { updates.check(silent = false) } }) {
            Text("Check for updates")
        }
        if (ui.available != null) {
            Button(enabled = !ui.busy, onClick = { scope.launch { updates.installAvailable() } }) {
                Text("Install ${ui.available?.version}")
            }
        }
        if (ui.status.isNotEmpty()) Text(ui.status)
        TextButton(onClick = onBack) { Text("Back") }
    }
}
