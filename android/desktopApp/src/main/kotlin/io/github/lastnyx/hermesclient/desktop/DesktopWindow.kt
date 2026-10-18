package io.github.lastnyx.hermesclient.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.auth.SignInController
import io.github.lastnyx.hermesclient.data.auth.SignInState
import io.github.lastnyx.hermesclient.data.gateway.GatewayClient
import io.github.lastnyx.hermesclient.data.gateway.GatewayRegistry
import io.github.lastnyx.hermesclient.ui.CARD_PADDING_H
import io.github.lastnyx.hermesclient.ui.GatewaysScreen
import io.github.lastnyx.hermesclient.ui.HostPlatform
import io.github.lastnyx.hermesclient.ui.LIST_GAP
import io.github.lastnyx.hermesclient.ui.ROW_PADDING
import io.github.lastnyx.hermesclient.ui.SCREEN_EDGE
import io.github.lastnyx.hermesclient.ui.SessionsHost
import okhttp3.OkHttpClient

const val GATEWAY_COLUMN_DP = 220
const val DEFAULT_WINDOW_WIDTH_DP = 1280
const val DEFAULT_WINDOW_HEIGHT_DP = 800
const val MIN_WINDOW_WIDTH_PX = 900
const val MIN_WINDOW_HEIGHT_PX = 600

/** Left: gateways. Right: the shared sessions list and chat (middle and right panes) or the Manage page. */
@Composable
fun DesktopWindow(
    registry: GatewayRegistry,
    client: GatewayClient,
    signIn: SignInController,
    http: OkHttpClient,
    platform: HostPlatform,
) {
    val state = remember { DesktopShellState(registry) { signIn.stateOf(it) is SignInState.SignedIn } }
    val signStates by signIn.states.collectAsState()
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(GATEWAY_COLUMN_DP.dp).fillMaxHeight()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LIST_GAP.dp)) {
                items(state.gateways, key = { it.id }) { gateway ->
                    val chosen = !state.manageOpen && state.selected?.id == gateway.id
                    Text(
                        gateway.name,
                        style = if (chosen) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().clickable { state.select(gateway.id) }
                            .padding(horizontal = CARD_PADDING_H.dp, vertical = ROW_PADDING.dp),
                    )
                }
            }
            TextButton(onClick = state::openManage, modifier = Modifier.padding(SCREEN_EDGE.dp)) { Text("Manage gateways") }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (val right = signStates.let { state.right }) {
                is RightSide.Sessions -> key(right.gateway.id) {
                    SessionsHost(right.gateway, http, signIn, platform, onGateways = state::openManage)
                }
                RightSide.Manage, is RightSide.SignIn -> Column(Modifier.fillMaxSize()) {
                    if (state.gateways.isNotEmpty()) {
                        TextButton(onClick = state::closeManage, modifier = Modifier.padding(SCREEN_EDGE.dp)) { Text("Close") }
                    }
                    GatewaysScreen(registry, client, signIn, onOpenSessions = state::closeManage)
                }
            }
        }
    }
}
