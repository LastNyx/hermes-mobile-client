package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.chat.ModelsController
import io.github.lastnyx.hermesclient.data.chat.ModelsState
import io.github.lastnyx.hermesclient.data.chat.REASONING_LEVELS
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ModelPicker(state: ModelsState, models: ModelsController, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = { BottomSheetDefaults.DragHandle() }) {
        LazyColumn(Modifier.padding(horizontal = SCREEN_EDGE.dp), verticalArrangement = Arrangement.spacedBy(LIST_GAP.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Model", Modifier.weight(1f), style = roles.title)
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
            }
            val notice = state.confirmMessage
            if (notice != null || state.error != null) {
                item {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(CARD_CORNER.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.padding(PROMPT_CARD_PADDING.dp)) {
                            if (notice != null) {
                                Text(notice, style = roles.body, color = MaterialTheme.colorScheme.onErrorContainer)
                                Row(Modifier.padding(top = LIST_GAP.dp)) {
                                    Button(onClick = { scope.launch { models.confirmPending() } }) { Text("Confirm") }
                                    OutlinedButton(onClick = { models.cancelConfirm() }, modifier = Modifier.padding(start = LIST_GAP.dp)) { Text("Cancel") }
                                }
                            }
                            state.error?.let { Text(it, style = roles.body, color = MaterialTheme.colorScheme.onErrorContainer) }
                        }
                    }
                }
            }
            item {
                Text("Reasoning", style = roles.label, modifier = Modifier.padding(top = ROW_PADDING.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ROW_PADDING.dp)) {
                    REASONING_LEVELS.forEach { level ->
                        val selected = level == state.reasoningEffort
                        FilterChip(
                            selected = selected,
                            onClick = { scope.launch { models.selectReasoning(level) } },
                            label = { Text(level) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(SMALL_ICON.dp)) }
                            } else null,
                        )
                    }
                }
                if (state.reasoningEffort !in REASONING_LEVELS && state.reasoningDisplay.isNotBlank()) {
                    Text(
                        "Current: ${state.reasoningDisplay}",
                        Modifier.padding(top = LIST_GAP.dp),
                        style = roles.secondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.groups.forEach { group ->
                item {
                    Text(
                        group.name,
                        Modifier.padding(top = ROW_PADDING.dp),
                        style = roles.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(group.models) { model ->
                    val current = model == state.model && group.slug == state.provider
                    Surface(
                        onClick = { scope.launch { models.selectModel(group.slug, model) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(CARD_CORNER.dp),
                        color = if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    ) {
                        Row(Modifier.padding(horizontal = ROW_PADDING.dp, vertical = ROW_PADDING.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(model, Modifier.weight(1f), style = roles.body)
                            if (current) Icon(Icons.Filled.Check, contentDescription = "Current model")
                        }
                    }
                }
            }
            item { Spacer(Modifier.size(LIST_END_SPACE.dp)) }
        }
    }
}
