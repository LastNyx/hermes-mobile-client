package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.insights.InsightsModel
import kotlinx.coroutines.launch

@Composable
fun InsightsScreen(model: InsightsModel, onBack: () -> Unit) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { model.load() }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = SCREEN_EDGE.dp),
        verticalArrangement = Arrangement.spacedBy(ROW_PADDING.dp),
    ) {
        ScreenTopBar("Insights", onBack)
        RangeChips(state.days) { days -> scope.launch { model.setDays(days) } }
        val data = state.data
        val centered = Modifier.fillMaxWidth().heightIn(min = EMPTY_STATE_MIN_HEIGHT.dp)
        when {
            state.loading -> OpeningSpinner(centered)
            state.error != null -> {
                Text(state.error.orEmpty(), style = roles.body, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { scope.launch { model.load() } }) { Text("Retry") }
            }
            data == null -> Unit
            data.isEmpty -> EmptyStateText("Nothing used yet", centered)
            else -> UsageCards(data)
        }
    }
}
