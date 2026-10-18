package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.sessions.Session
import io.github.lastnyx.hermesclient.data.sessions.SessionStatus
import io.github.lastnyx.hermesclient.data.sessions.sessionStatus

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionRow(session: Session, onOpen: (String?) -> Unit, onMenu: () -> Unit, divider: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = { onOpen(session.id) }, onLongClick = onMenu)
                .padding(vertical = ROW_PADDING.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(SESSION_ROW_SLOT.dp), contentAlignment = Alignment.CenterStart) {
                    val dot = when (sessionStatus(session)) {
                        SessionStatus.Working -> MaterialTheme.colorScheme.tertiary
                        SessionStatus.Unread -> MaterialTheme.colorScheme.primary
                        else -> null
                    }
                    if (session.pinned) {
                        Icon(
                            Icons.Filled.PushPin, contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(SESSION_PIN_SIZE.dp),
                        )
                    } else if (dot != null) {
                        Box(Modifier.size(SESSION_DOT_SIZE.dp).background(dot, CircleShape))
                    }
                }
                Text(
                    session.title.ifBlank { "Untitled" },
                    style = roles.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(relativeTime(session.lastActive), style = roles.caption)
            }
            val preview = cleanPreview(session.preview)
            if (preview.isNotBlank()) {
                Text(
                    preview,
                    style = roles.secondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = SESSION_ROW_SLOT.dp),
                )
            }
        }
        if (divider) {
            HorizontalDivider(
                Modifier.padding(start = SESSION_ROW_SLOT.dp).alpha(0.5f),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

fun relativeTime(epochSeconds: Double, nowMs: Long = System.currentTimeMillis()): String {
    if (epochSeconds <= 0) return ""
    val seconds = (nowMs / 1000 - epochSeconds.toLong()).coerceAtLeast(0)
    return when {
        seconds < 60 -> "now"
        seconds < 3600 -> "${seconds / 60} min ago"
        seconds < 86400 -> "${seconds / 3600} h ago"
        else -> "${seconds / 86400} d ago"
    }
}
