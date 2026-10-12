package io.github.lastnyx.hermesclient.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

sealed interface SignInState {
    data class SignedOut(val message: String?) : SignInState
    data object SigningIn : SignInState
    data class SignedIn(val name: String) : SignInState
}

class SignInController(
    private val http: OkHttpClient,
    private val tokens: TokenStore,
    browser: BrowserLauncher,
    timeoutMs: Long = 600_000,
    private val onFinished: () -> Unit = {},
) {
    private val nativeSignIn = NativeSignIn(http, tokens, browser, timeoutMs)
    private val busy = AtomicBoolean(false)
    private val _states = MutableStateFlow<Map<String, SignInState>>(emptyMap())
    val states: StateFlow<Map<String, SignInState>> = _states

    fun stateOf(gatewayId: String): SignInState =
        _states.value[gatewayId]
            ?: if (tokens.load(gatewayId) != null) SignInState.SignedIn(FALLBACK_NAME) else SignInState.SignedOut(null)

    /** Ignored while another sign-in runs. */
    suspend fun signIn(gatewayId: String, baseUrl: String) {
        if (!busy.compareAndSet(false, true)) return
        try {
            set(gatewayId, SignInState.SigningIn)
            val result = withContext(Dispatchers.IO) {
                nativeSignIn.signIn(gatewayId, baseUrl).map { fetchName(baseUrl, it.accessToken) }
            }
            set(
                gatewayId,
                result.fold(
                    { SignInState.SignedIn(it) },
                    { SignInState.SignedOut(it.message ?: "Sign-in failed") },
                ),
            )
        } finally {
            busy.set(false)
            onFinished()
        }
    }

    /** Re-fetches the name of a gateway that already has tokens; keeps the last known name when offline. */
    suspend fun refreshName(gatewayId: String, baseUrl: String) {
        val pair = tokens.load(gatewayId) ?: return
        val name = withContext(Dispatchers.IO) { fetchName(baseUrl, pair.accessToken) }
        if (_states.value[gatewayId] !is SignInState.SigningIn && (name != FALLBACK_NAME || _states.value[gatewayId] == null)) {
            set(gatewayId, SignInState.SignedIn(name))
        }
    }

    fun signOut(gatewayId: String) {
        tokens.clear(gatewayId)
        set(gatewayId, SignInState.SignedOut(null))
    }

    private fun set(gatewayId: String, state: SignInState) {
        _states.value = _states.value + (gatewayId to state)
    }

    private fun fetchName(baseUrl: String, accessToken: String): String = runCatching {
        val request = Request.Builder().url(baseUrl.trimEnd('/') + "/api/auth/me")
            .header("Authorization", "Bearer $accessToken").build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful)
            val json = JSONObject(response.body!!.string())
            listOf("display_name", "email", "user_id").firstNotNullOfOrNull { key ->
                json.optString(key).takeIf { it.isNotBlank() }
            } ?: FALLBACK_NAME
        }
    }.getOrDefault(FALLBACK_NAME)

    private companion object {
        const val FALLBACK_NAME = "Signed in"
    }
}
