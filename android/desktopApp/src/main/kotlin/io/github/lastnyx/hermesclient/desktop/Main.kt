package io.github.lastnyx.hermesclient.desktop

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.lastnyx.hermesclient.data.auth.SignInController
import io.github.lastnyx.hermesclient.data.auth.TokenStore
import io.github.lastnyx.hermesclient.data.gateway.GatewayClient
import io.github.lastnyx.hermesclient.data.gateway.GatewayRegistry
import io.github.lastnyx.hermesclient.data.gateway.restClient
import io.github.lastnyx.hermesclient.data.profiles.ProfileStore
import io.github.lastnyx.hermesclient.ui.TextScaleStore
import io.github.lastnyx.hermesclient.ui.scaledTypography
import okhttp3.OkHttpClient

const val WINDOW_TITLE = "Hermes"

fun main() = application {
    val files = remember {
        DesktopFiles(dataFolder(System.getProperty("os.name"), System.getenv(), System.getProperty("user.home")))
    }
    val registry = remember { GatewayRegistry(files.gateways) }
    val rest = remember { restClient(OkHttpClient()) }
    val client = remember { GatewayClient(rest) }
    val http = remember { OkHttpClient() }
    val platform = remember { desktopPlatform(files) }
    val tokens = remember { TokenStore(files.tokens, PlainCipher) }
    val textScaleStore = remember { TextScaleStore(files.textScale) }
    val profileStore = remember { ProfileStore(files.profiles) }
    var textScale by remember { mutableStateOf(textScaleStore.current()) }
    var manualUrl by remember { mutableStateOf<String?>(null) }
    val windowRef = remember { arrayOfNulls<java.awt.Window>(1) }
    val signIn = remember {
        SignInController(
            rest,
            tokens,
            DesktopBrowserLauncher(showUrl = { manualUrl = it }),
            onFinished = { windowRef[0]?.toFront() },
        )
    }
    val windowState = rememberWindowState(size = DpSize(DEFAULT_WINDOW_WIDTH_DP.dp, DEFAULT_WINDOW_HEIGHT_DP.dp))
    Window(onCloseRequest = ::exitApplication, title = WINDOW_TITLE, state = windowState) {
        windowRef[0] = window
        window.minimumSize = java.awt.Dimension(MIN_WINDOW_WIDTH_PX, MIN_WINDOW_HEIGHT_PX)
        MaterialTheme(typography = scaledTypography(Typography(), textScale)) {
            Surface {
                DesktopWindow(
                    registry, client, signIn, http, platform,
                    textScale = textScale,
                    onTextScale = { textScale = it; textScaleStore.set(it) },
                    profileStore = profileStore,
                )
                manualUrl?.let { url ->
                    val clipboard = LocalClipboardManager.current
                    AlertDialog(
                        onDismissRequest = { manualUrl = null },
                        title = { Text("Open this address in a browser") },
                        text = { Text(url) },
                        confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(url)) }) { Text("Copy") } },
                        dismissButton = { TextButton(onClick = { manualUrl = null }) { Text("Close") } },
                    )
                }
            }
        }
    }
}
