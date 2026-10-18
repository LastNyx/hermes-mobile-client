package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.chat.AttachmentKind
import io.github.lastnyx.hermesclient.data.chat.PreparedAttachment

private const val THUMB_TARGET_PX = 168

@Composable
fun AttachTrayRow(tray: AttachTrayState, onRemove: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = LIST_GAP.dp),
        horizontalArrangement = Arrangement.spacedBy(ROW_PADDING.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tray.items.forEachIndexed { index, a -> AttachChip(a) { onRemove(index) } }
    }
}

@Composable
private fun AttachChip(a: PreparedAttachment, onRemove: () -> Unit) {
    val thumb = remember(a) { if (a.kind == AttachmentKind.Image) decodeImage(a.bytes, THUMB_TARGET_PX) else null }
    val remove = "Remove ${a.name}"
    if (thumb != null) {
        Box(Modifier.size(56.dp)) {
            Image(thumb, contentDescription = a.name, Modifier.size(56.dp), contentScale = ContentScale.Crop)
            IconButton(onClick = onRemove, Modifier.align(Alignment.TopEnd).size(24.dp)) {
                Icon(Icons.Filled.Close, contentDescription = remove, Modifier.size(16.dp))
            }
        }
    } else {
        Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Row(Modifier.padding(start = CARD_PADDING_H.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📎 ${chipLabel(a)}", style = roles.label, maxLines = 1)
                IconButton(onClick = onRemove, Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = remove, Modifier.size(16.dp))
                }
            }
        }
    }
}
