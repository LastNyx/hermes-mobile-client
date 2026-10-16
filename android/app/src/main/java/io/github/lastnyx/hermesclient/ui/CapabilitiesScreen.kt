package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.capabilities.CapabilitiesModel
import io.github.lastnyx.hermesclient.data.capabilities.McpTestResult
import kotlinx.coroutines.launch

private const val TEST_NAMES_SHOWN = 5

internal fun mcpResultLine(r: McpTestResult): String =
    if (r.ok) "OK, ${r.tools.size} tools: ${r.tools.take(TEST_NAMES_SHOWN).joinToString(", ")}" else r.error ?: "Test failed"

@Composable
fun CapabilitiesScreen(model: CapabilitiesModel, onBack: () -> Unit) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { model.load() }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = SCREEN_EDGE.dp),
        verticalArrangement = Arrangement.spacedBy(ROW_PADDING.dp),
    ) {
        ScreenTopBar("Capabilities", onBack)
        Text("Changes apply from the next chat.", style = roles.secondary)
        state.error?.let {
            Text(it, style = roles.body, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { scope.launch { model.load() } }) { Text("Retry") }
        }
        if (state.loading) Text("Loading...", style = roles.secondary)
        Section("Skills") {
            if (state.skills.isEmpty()) Text("None", style = roles.secondary)
            state.skills.forEach { s ->
                SwitchRow(s.name, s.description, s.enabled, true) { scope.launch { model.toggleSkill(s.name, it) } }
            }
        }
        Section("Toolsets") {
            if (state.toolsets.isEmpty()) Text("None", style = roles.secondary)
            state.toolsets.forEach { t ->
                val detail = if (t.configured) t.description else "Needs setup"
                SwitchRow(t.label, detail, t.enabled, true) { scope.launch { model.toggleToolset(t.name, it) } }
            }
        }
        Section("MCP servers") {
            if (state.servers.isEmpty()) Text("None", style = roles.secondary)
            state.servers.forEach { m ->
                SwitchRow(m.name, null, m.enabled, !m.locked, {
                    TextButton(enabled = state.testing == null, onClick = { scope.launch { model.testMcp(m.name) } }) { Text("Test") }
                }) { scope.launch { model.toggleMcp(m.name, it) } }
                if (state.testing == m.name) Text("Testing...", style = roles.secondary)
                else state.testResults[m.name]?.let { Text(mcpResultLine(it), style = roles.secondary) }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    detail: String?,
    checked: Boolean,
    enabled: Boolean,
    extra: @Composable () -> Unit = {},
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = roles.body)
            if (!detail.isNullOrBlank()) Text(detail, style = roles.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        extra()
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
