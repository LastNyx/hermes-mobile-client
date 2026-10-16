package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The phone layout (back arrow shown) is too narrow for title and model chip side by side. */
internal fun headerStacked(onBack: Boolean): Boolean = onBack

@Composable
internal fun RowScope.ChatHeaderTitle(title: String, chip: String, onChip: () -> Unit, stacked: Boolean) {
    if (stacked) {
        Column(Modifier.weight(1f)) {
            Text(title, style = roles.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onChip, contentPadding = PaddingValues(0.dp)) {
                Text(chip, maxLines = 1, overflow = TextOverflow.Ellipsis, style = roles.caption)
            }
        }
    } else {
        Text(
            title,
            Modifier.weight(1f).padding(start = 4.dp),
            style = roles.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onChip) {
            Text(chip, maxLines = 1, overflow = TextOverflow.Ellipsis, style = roles.label)
        }
    }
}
