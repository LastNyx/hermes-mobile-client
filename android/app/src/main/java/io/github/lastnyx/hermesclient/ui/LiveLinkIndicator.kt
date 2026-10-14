package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.live.LinkState
import io.github.lastnyx.hermesclient.data.live.LiveLink

private fun linkLabel(state: LinkState): Pair<String, Boolean> = when (state) {
    LinkState.Connected -> "Connected" to false
    LinkState.Connecting -> "Connecting" to false
    LinkState.Offline -> "Offline" to false
    LinkState.SignedOut -> "Signed out" to true
    is LinkState.Error -> "Offline: ${state.message}" to true
}

@Composable
fun LiveLinkIndicator(link: LiveLink, modifier: Modifier = Modifier) {
    val state by link.state.collectAsState()
    val (label, error) = linkLabel(state)
    Column(modifier) {
        Text(
            "● $label",
            style = MaterialTheme.typography.labelSmall,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun LiveLinkDot(link: LiveLink, modifier: Modifier = Modifier) {
    val state by link.state.collectAsState()
    val (label, error) = linkLabel(state)
    val color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier.size(SESSION_DOT_SIZE.dp).background(color, CircleShape)
            .semantics { contentDescription = "Live link: $label" },
    )
}
