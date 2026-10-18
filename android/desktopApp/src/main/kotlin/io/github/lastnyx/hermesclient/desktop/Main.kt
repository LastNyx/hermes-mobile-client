package io.github.lastnyx.hermesclient.desktop

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.lastnyx.hermesclient.data.auth.SignInController
import io.github.lastnyx.hermesclient.data.auth.TokenStore
import io.github.lastnyx.hermesclient.data.gateway.GatewayClient
import io.github.lastnyx.hermesclient.data.gateway.GatewayRegistry
import io.github.lastnyx.hermesclient.data.gateway.restClient
import io.github.lastnyx.hermesclient.ui.GatewaysScreen
import okhttp3.OkHttpClient

const val WINDOW_TITLE = "Hermes"

fun main() = application {
    val files = DesktopFiles(dataFolder(System.getProperty("os.name"), System.getenv(), System.getProperty("user.home")))
    val registry = GatewayRegistry(files.gateways)
    val rest = restClient(OkHttpClient())
    val client = GatewayClient(rest)
    val tokens = TokenStore(files.tokens, PlainCipher)
    var manualUrl by remember { mutableStateOf<String?>(null) }
    var window: java.awt.Window? = null
    val signIn = remember {
        SignInController(
            rest,
            tokens,
            DesktopBrowserLauncher(showUrl = { manualUrl = it }),
            onFinished = { window?.toFront() },
        )
    }
    Window(onCloseRequest = ::exitApplication, title = WINDOW_TITLE) {
        window = this.window
        MaterialTheme {
            Surface {
                GatewaysScreen(registry, client, signIn)
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
