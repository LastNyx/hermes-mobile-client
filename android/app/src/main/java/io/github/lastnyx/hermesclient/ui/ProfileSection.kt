package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.github.lastnyx.hermesclient.data.profiles.ProfilesModel
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun ProfileSection(model: ProfilesModel) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(model) { model.load() }
    Section("Profile") {
        Text("Chats and new chats use the selected profile on this gateway.", style = roles.secondary)
        state.error?.let { error ->
            Text(error, style = roles.body)
            Button(enabled = !state.loading, onClick = { scope.launch { model.load() } }) { Text("Retry") }
        }
        Column(Modifier.selectableGroup()) {
            state.profiles.forEach { item ->
                // No stored choice means the gateway's default profile.
                val selected = if (state.selected == null) item.isDefault else item.name == state.selected
                Row(
                    Modifier.fillMaxWidth().selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { model.select(item) },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Column(Modifier.padding(start = ROW_PADDING.dp, top = LIST_GAP.dp, bottom = LIST_GAP.dp)) {
                        Text(item.label, style = roles.body)
                        val detail = listOfNotNull(
                            item.model,
                            item.provider,
                            "gateway profile".takeIf { item.name == state.current },
                        ).joinToString(" · ")
                        if (detail.isNotEmpty()) Text(detail, style = roles.secondary)
                    }
                }
            }
        }
    }
}
