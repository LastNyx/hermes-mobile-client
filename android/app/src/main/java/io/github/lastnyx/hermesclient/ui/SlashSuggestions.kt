package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.chat.SlashKind
import io.github.lastnyx.hermesclient.data.chat.SlashSuggestion
import io.github.lastnyx.hermesclient.data.chat.parseSlashLine

sealed interface SuggestionRow {
    data class Header(val title: String) : SuggestionRow
    data class Item(val suggestion: SlashSuggestion) : SuggestionRow
}

fun suggestionRows(suggestions: List<SlashSuggestion>): List<SuggestionRow> {
    val rows = ArrayList<SuggestionRow>()
    var group: String? = null
    for (s in suggestions) {
        val g = s.group
        if (g != null && g != group) rows += SuggestionRow.Header(g)
        group = g
        rows += SuggestionRow.Item(s)
    }
    return rows
}

/** The one rule for showing the list: a single-line draft that is `/` or starts a slash command (not a path). */
internal fun asksForSuggestions(draft: String): Boolean =
    '\n' !in draft && (draft == "/" || parseSlashLine(draft) != null)

/** What the composer holds after a tap: commands and skills get a space for the argument. */
internal fun composerTextFor(s: SlashSuggestion): String = if (s.kind == SlashKind.Option) s.text else s.text + " "

@Composable
fun SlashSuggestionList(suggestions: List<SlashSuggestion>, onPick: (SlashSuggestion) -> Unit, modifier: Modifier = Modifier) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.4f).dp
    Surface(
        modifier.fillMaxWidth().heightIn(max = maxHeight),
        shape = RoundedCornerShape(CARD_CORNER.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        LazyColumn {
            items(suggestionRows(suggestions)) { row ->
                when (row) {
                    is SuggestionRow.Header -> Text(
                        row.title,
                        Modifier.padding(start = CARD_PADDING_H.dp, end = CARD_PADDING_H.dp, top = ROW_PADDING.dp),
                        style = roles.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is SuggestionRow.Item -> {
                        val s = row.suggestion
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { onPick(s) }.padding(horizontal = CARD_PADDING_H.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(s.label, style = roles.code)
                            if (s.kind != SlashKind.Option) {
                                Text(
                                    s.description,
                                    Modifier.weight(1f).padding(start = ROW_PADDING.dp),
                                    style = roles.secondary,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (s.kind == SlashKind.Skill) {
                                Text(
                                    "skill",
                                    Modifier.padding(start = ROW_PADDING.dp),
                                    style = roles.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
