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
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.lastnyx.hermesclient.data.auth.CustomTabBrowserLauncher
import io.github.lastnyx.hermesclient.data.auth.FileTokenStorage
import io.github.lastnyx.hermesclient.data.auth.KeystoreSecretCipher
import io.github.lastnyx.hermesclient.data.auth.SignInController
import io.github.lastnyx.hermesclient.data.auth.SignInState
import io.github.lastnyx.hermesclient.data.auth.TokenStore
import io.github.lastnyx.hermesclient.data.gateway.GatewayClient
import io.github.lastnyx.hermesclient.data.gateway.GatewayRegistry
import io.github.lastnyx.hermesclient.data.gateway.PrefsGatewayStorage
import io.github.lastnyx.hermesclient.data.gateway.restClient
import io.github.lastnyx.hermesclient.data.settings.PrefsTextScaleStorage
import io.github.lastnyx.hermesclient.data.share.ShareRequest
import io.github.lastnyx.hermesclient.data.share.draftFromShare
import io.github.lastnyx.hermesclient.data.update.ApkInstaller
import io.github.lastnyx.hermesclient.data.update.UpdateController
import io.github.lastnyx.hermesclient.data.update.Updater
import io.github.lastnyx.hermesclient.data.watch.EXTRA_SESSION_ID
import io.github.lastnyx.hermesclient.ui.GatewaysScreen
import io.github.lastnyx.hermesclient.ui.SessionsHost
import io.github.lastnyx.hermesclient.ui.SettingsScreen
import io.github.lastnyx.hermesclient.ui.TextScaleStore
import io.github.lastnyx.hermesclient.ui.scaledTypography
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient

/** Opens on the Gateways screen; later tickets add navigation past it. */
class MainActivity : ComponentActivity() {
    // Set when a Watch notification is tapped; SessionsHost opens that chat.
    private var requestedSession by mutableStateOf<String?>(null)

    private var shareRequest by mutableStateOf<ShareRequest?>(null)
    private var shareCount = 0L

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_SESSION_ID)?.let { requestedSession = it }
        handleShare(intent)
    }

    // The text only fills the new-chat composer (SessionsHost); it is never sent.
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("text/") != true) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val draft = draftFromShare(intent.getStringExtra(Intent.EXTRA_SUBJECT), text) ?: return
        shareRequest = ShareRequest(++shareCount, draft)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedSession = intent?.getStringExtra(EXTRA_SESSION_ID)
        if (savedInstanceState == null) handleShare(intent)
        enableEdgeToEdge()
        val registry = GatewayRegistry(PrefsGatewayStorage(applicationContext))
        val http = OkHttpClient()
        // LiveLink (WebSocket, readTimeout 0) and Updater (long APK download) stay on plain `http`: a call limit would kill them.
        val rest = restClient(http)
        val client = GatewayClient(rest)
        val tokens = TokenStore(FileTokenStorage(applicationContext), KeystoreSecretCipher())
        val signIn = SignInController(
            rest,
            tokens,
            CustomTabBrowserLauncher(this),
            onFinished = {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            },
        )
        val updates = UpdateController(
            Updater(http, "https://api.github.com", BuildConfig.RELEASES_REPO, BuildConfig.VERSION_NAME),
            cacheDir,
            Dispatchers.IO,
            install = { ApkInstaller.install(applicationContext, it) },
        )
        val textScaleStore = TextScaleStore(PrefsTextScaleStorage(applicationContext))
        setContent {
            var textScale by remember { mutableStateOf(textScaleStore.current()) }
            MaterialTheme(
                colorScheme = darkColorScheme(),
                typography = scaledTypography(Typography(), textScale),
            ) {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    var showGateways by rememberSaveable {
                        mutableStateOf(registry.active()?.let { signIn.stateOf(it.id) } !is SignInState.SignedIn)
                    }
                    var showSettings by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(Unit) { updates.check(silent = true) }
                    LaunchedEffect(Unit) {
                        io.github.lastnyx.hermesclient.data.update.InstallFailures.messages.collect { updates.installFailed(it) }
                    }
                    val updateUi by updates.state.collectAsState()
                    val active = registry.active()
                    if (showSettings) {
                        SettingsScreen(
                            updates, BuildConfig.VERSION_NAME,
                            textScale = textScale,
                            onTextScale = { textScale = it; textScaleStore.set(it) },
                            onBack = { showSettings = false },
                        )
                    } else if (showGateways || active == null) {
                        GatewaysScreen(
                            registry, client, signIn,
                            onOpenSessions = { showGateways = false },
                            onOpenSettings = { showSettings = true },
                            updateStatus = updateUi.status,
                        )
                    } else {
                        SessionsHost(active, http, signIn, onGateways = { showGateways = true }, openSessionId = requestedSession, share = shareRequest)
                    }
                }
            }
        }
    }
}
