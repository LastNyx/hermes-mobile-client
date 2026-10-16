package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.chat.ChatState
import io.github.lastnyx.hermesclient.data.sessions.SessionListState

/** [filtered] is true when the list has sessions but the status filter hid every group. */
internal fun listEmptyText(state: SessionListState, filtered: Boolean): String? = when {
    state.loading || state.error != null -> null
    state.query.isNotEmpty() -> if (state.sessions.isEmpty()) "No chats match your search." else null
    state.archivedPage -> if (state.sessions.isEmpty()) "Nothing archived." else null
    filtered -> "No chats match this filter."
    state.sessions.isEmpty() -> "No chats yet. Tap + to start one."
    else -> null
}

internal enum class ChatPlaceholder { Opening, Empty, None }

// A stored chat has no sessionId until create/resume returned, so null + no error means still opening.
internal fun chatPlaceholder(state: ChatState): ChatPlaceholder = when {
    state.items.isNotEmpty() || state.error != null -> ChatPlaceholder.None
    state.sessionId == null -> ChatPlaceholder.Opening
    !state.running -> ChatPlaceholder.Empty
    else -> ChatPlaceholder.None
}

@Composable
internal fun EmptyStateText(text: String, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text,
            Modifier.padding(SCREEN_EDGE.dp),
            style = roles.secondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun OpeningSpinner(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(OPENING_SPINNER_SIZE.dp), strokeWidth = OPENING_SPINNER_STROKE.dp)
    }
}
