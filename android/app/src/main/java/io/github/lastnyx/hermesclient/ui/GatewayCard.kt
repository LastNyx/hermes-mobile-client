package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.auth.SignInState
import io.github.lastnyx.hermesclient.data.gateway.Gateway

@Composable
fun GatewayCard(
    gateway: Gateway,
    active: Boolean,
    state: SignInState,
    testResult: String?,
    onClick: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val status = gatewayStatusLine(state, testResult)
    val scheme = MaterialTheme.colorScheme
    val toneColor = when (status.tone) {
        StatusTone.Normal -> scheme.onSurfaceVariant
        StatusTone.Good -> scheme.primary
        StatusTone.Warning -> scheme.tertiary
        StatusTone.Error -> scheme.error
    }
    var menu by remember { mutableStateOf(false) }
    val signedInLike = state is SignInState.SignedIn || state == SignInState.Unavailable
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(start = ROW_PADDING.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = ROW_PADDING.dp), verticalArrangement = Arrangement.spacedBy(LIST_GAP.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ROW_PADDING.dp)) {
                    Box(
                        Modifier.size(GATEWAY_DOT_SIZE.dp).background(if (active) scheme.primary else scheme.outline, CircleShape),
                    )
                    Text(gateway.name, Modifier.weight(1f, fill = false), style = roles.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (active) Text("Active", style = roles.caption, color = scheme.primary)
                }
                Text(gateway.url, style = roles.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status.text, style = roles.body, color = toneColor)
                if (state is SignInState.SignedOut) {
                    Button(onClick = onSignIn) { Text("Sign in") }
                }
            }
            Column {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Gateway menu") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Test connection") }, onClick = { menu = false; onTest() })
                    if (signedInLike) {
                        DropdownMenuItem(text = { Text("Sign out") }, onClick = { menu = false; onSignOut() })
                    } else {
                        DropdownMenuItem(
                            text = { Text("Sign in") },
                            enabled = state != SignInState.SigningIn,
                            onClick = { menu = false; onSignIn() },
                        )
                    }
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}

private const val GATEWAY_DOT_SIZE = 8
