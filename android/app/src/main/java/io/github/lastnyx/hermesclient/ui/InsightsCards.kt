package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.insights.INSIGHT_DAYS
import io.github.lastnyx.hermesclient.data.insights.InsightRow
import io.github.lastnyx.hermesclient.data.insights.UsageInsights
import io.github.lastnyx.hermesclient.data.insights.dayRows
import io.github.lastnyx.hermesclient.data.insights.modelRows
import io.github.lastnyx.hermesclient.data.insights.skillRows
import io.github.lastnyx.hermesclient.data.insights.summaryStats
import io.github.lastnyx.hermesclient.data.insights.toolRows

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RangeChips(selectedDays: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(LIST_GAP.dp)) {
        INSIGHT_DAYS.forEach { days ->
            val selected = days == selectedDays
            FilterChip(
                selected = selected,
                onClick = { onSelect(days) },
                label = { Text("$days days") },
                modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(days) }),
            )
        }
    }
}

@Composable
internal fun InsightRows(rows: List<InsightRow>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LIST_GAP.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ROW_PADDING.dp)) {
                Text(row.name, Modifier.weight(1f), style = roles.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.value, style = roles.secondary, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
internal fun UsageCards(u: UsageInsights) {
    Section("Totals") { InsightRows(summaryStats(u)) }
    listOf("Models" to modelRows(u), "Tools" to toolRows(u), "Skills" to skillRows(u), "Days" to dayRows(u))
        .filter { it.second.isNotEmpty() }
        .forEach { (title, rows) -> Section(title) { InsightRows(rows) } }
}
