package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.update.UpdateController
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    updates: UpdateController,
    versionName: String,
    textScale: TextScale,
    onTextScale: (TextScale) -> Unit,
    onBack: () -> Unit,
) {
    val ui by updates.state.collectAsState()
    val scope = rememberCoroutineScope()
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = SCREEN_EDGE.dp),
        verticalArrangement = Arrangement.spacedBy(ROW_PADDING.dp),
    ) {
        ScreenTopBar("Settings", onBack)
        Section("Text size") {
            Column(Modifier.selectableGroup()) {
                TextScale.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = option == textScale,
                            role = Role.RadioButton,
                            onClick = { onTextScale(option) },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == textScale, onClick = null)
                        Column(Modifier.padding(start = ROW_PADDING.dp, top = LIST_GAP.dp, bottom = LIST_GAP.dp)) {
                            Text(option.label, style = roles.body)
                            Text("The quick brown fox", style = roles.secondary)
                        }
                    }
                }
            }
        }
        Section("About") {
            Text("Version $versionName", style = roles.body)
            Button(enabled = !ui.busy, onClick = { scope.launch { updates.check(silent = false) } }) {
                Text("Check for updates")
            }
            if (ui.available != null) {
                Button(enabled = !ui.busy, onClick = { scope.launch { updates.installAvailable() } }) {
                    Text("Install ${ui.available?.version}")
                }
            }
            if (ui.status.isNotEmpty()) Text(ui.status, style = roles.body)
        }
    }
}

@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(CARD_CORNER.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(ROW_PADDING.dp),
        verticalArrangement = Arrangement.spacedBy(ROW_PADDING.dp),
    ) {
        Text(title, style = roles.title)
        content()
    }
}
