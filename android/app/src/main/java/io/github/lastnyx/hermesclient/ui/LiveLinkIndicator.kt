package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.lastnyx.hermesclient.data.live.LinkState
import io.github.lastnyx.hermesclient.data.live.LiveLink

@Composable
fun LiveLinkIndicator(link: LiveLink, modifier: Modifier = Modifier) {
    val state by link.state.collectAsState()
    val (label, error) = when (val s = state) {
        LinkState.Connected -> "Connected" to false
        LinkState.Connecting -> "Connecting" to false
        LinkState.Offline -> "Offline" to false
        LinkState.SignedOut -> "Signed out" to true
        is LinkState.Error -> "Offline: ${s.message}" to true
    }
    Column(modifier) {
        Text(
            "● $label",
            style = MaterialTheme.typography.labelSmall,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
