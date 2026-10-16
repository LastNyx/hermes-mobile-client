package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.insights.INSIGHT_DAYS
import io.github.lastnyx.hermesclient.data.insights.InsightsModel
import io.github.lastnyx.hermesclient.data.insights.UsageInsights
import io.github.lastnyx.hermesclient.data.insights.dayLines
import io.github.lastnyx.hermesclient.data.insights.modelLines
import io.github.lastnyx.hermesclient.data.insights.skillLines
import io.github.lastnyx.hermesclient.data.insights.summaryLines
import io.github.lastnyx.hermesclient.data.insights.toolLines
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
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(LIST_GAP.dp)) {
            INSIGHT_DAYS.forEach { days ->
                val selected = days == state.days
                TextButton(
                    onClick = { scope.launch { model.setDays(days) } },
                    modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = { scope.launch { model.setDays(days) } }),
                ) {
                    Text("$days days", fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        val data = state.data
        when {
            state.loading -> Text("Loading...", style = roles.secondary)
            state.error != null -> {
                Text(state.error.orEmpty(), style = roles.body, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { scope.launch { model.load() } }) { Text("Retry") }
            }
            data == null -> Unit
            data.isEmpty -> Text("Nothing used yet", style = roles.body)
            else -> UsageLines(data)
        }
    }
}

@Composable
private fun UsageLines(u: UsageInsights) {
    summaryLines(u).forEach { Text(it, style = roles.body) }
    listOf("Models" to modelLines(u), "Tools" to toolLines(u), "Skills" to skillLines(u), "Days" to dayLines(u))
        .filter { it.second.isNotEmpty() }
        .forEach { (title, lines) ->
            Text(title, style = roles.title)
            lines.forEach { Text(it, style = roles.secondary) }
        }
}
