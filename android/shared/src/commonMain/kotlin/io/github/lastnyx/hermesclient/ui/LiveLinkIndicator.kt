package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.auth.AuthState
import io.github.lastnyx.hermesclient.data.live.LinkState
import io.github.lastnyx.hermesclient.data.live.LiveLink

@Composable
private fun rememberLinkStatus(link: LiveLink, auth: AuthState, networkOnline: Boolean): LinkStatus {
    val state by link.state.collectAsState()
    var hasConnected by remember(link) { mutableStateOf(false) }
    LaunchedEffect(state) { if (state == LinkState.Connected) hasConnected = true }
    return linkStatus(state, auth, networkOnline, hasConnected)
}

@Composable
fun LiveLinkIndicator(link: LiveLink, auth: AuthState, networkOnline: Boolean, modifier: Modifier = Modifier) {
    val status = rememberLinkStatus(link, auth, networkOnline)
    Column(modifier) {
        Text(
            "● ${status.text}",
            style = MaterialTheme.typography.labelSmall,
            color = if (status.problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun LiveLinkDot(link: LiveLink, auth: AuthState, networkOnline: Boolean, modifier: Modifier = Modifier) {
    val status = rememberLinkStatus(link, auth, networkOnline)
    val color = if (status.problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier.size(SESSION_DOT_SIZE.dp).background(color, CircleShape)
            .semantics { contentDescription = "Live link: ${status.text}" },
    )
}
