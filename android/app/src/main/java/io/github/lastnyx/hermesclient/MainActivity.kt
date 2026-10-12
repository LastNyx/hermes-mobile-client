package io.github.lastnyx.hermesclient

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.lastnyx.hermesclient.data.watch.EXTRA_SESSION_ID
import io.github.lastnyx.hermesclient.data.auth.SignInState
import io.github.lastnyx.hermesclient.ui.SessionsHost
import io.github.lastnyx.hermesclient.data.auth.CustomTabBrowserLauncher
import io.github.lastnyx.hermesclient.data.auth.FileTokenStorage
import io.github.lastnyx.hermesclient.data.auth.KeystoreSecretCipher
import io.github.lastnyx.hermesclient.data.auth.SignInController
import io.github.lastnyx.hermesclient.data.auth.TokenStore
import io.github.lastnyx.hermesclient.data.gateway.GatewayClient
import io.github.lastnyx.hermesclient.data.gateway.GatewayRegistry
import io.github.lastnyx.hermesclient.data.gateway.PrefsGatewayStorage
import io.github.lastnyx.hermesclient.ui.GatewaysScreen
import okhttp3.OkHttpClient

/** Opens on the Gateways screen; later tickets add navigation past it. */
class MainActivity : ComponentActivity() {
    // Set when a Watch notification is tapped; SessionsHost opens that chat.
    private var requestedSession by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestedSession = intent.getStringExtra(EXTRA_SESSION_ID)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedSession = intent?.getStringExtra(EXTRA_SESSION_ID)
        enableEdgeToEdge()
        val registry = GatewayRegistry(PrefsGatewayStorage(applicationContext))
        val http = OkHttpClient()
        val client = GatewayClient(http)
        val tokens = TokenStore(FileTokenStorage(applicationContext), KeystoreSecretCipher())
        val signIn = SignInController(
            http,
            tokens,
            CustomTabBrowserLauncher(this),
            onFinished = {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            },
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    var showGateways by remember {
                        mutableStateOf(registry.active()?.let { signIn.stateOf(it.id) } !is SignInState.SignedIn)
                    }
                    val active = registry.active()
                    if (showGateways || active == null) {
                        GatewaysScreen(registry, client, signIn, onOpenSessions = { showGateways = false })
                    } else {
                        SessionsHost(active, http, tokens, onGateways = { showGateways = true }, openSessionId = requestedSession)
                    }
                }
            }
        }
    }
}
