package io.github.lastnyx.hermesclient.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

sealed interface AuthState {
    data object SignedOut : AuthState
    data class SignedIn(val user: String) : AuthState
    data object Expired : AuthState
    data object AuthUnavailable : AuthState
}

/** Signed-in REST for one gateway. Blocking; call off the main thread. */
class AuthedHttp(
    private val http: OkHttpClient,
    private val tokens: TokenStore,
    private val gatewayId: String,
    baseUrl: String,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val base = baseUrl.trimEnd('/')
    private val lock = Any()
    private val _state = MutableStateFlow<AuthState>(
        tokens.load(gatewayId)?.let { AuthState.SignedIn(it.userId) } ?: AuthState.SignedOut,
    )
    val state: StateFlow<AuthState> = _state

    @Throws(IOException::class)
    fun execute(request: Request): Response {
        var pair = tokens.load(gatewayId) ?: run {
            _state.value = AuthState.SignedOut
            throw IOException("Signed out — sign in again")
        }
        if (pair.expiresAt <= 0 || now() >= pair.expiresAt - 60) pair = refresh(pair.accessToken)
        val response = http.newCall(withBearer(request, pair)).execute()
        if (response.code != 401 || !replayable(request) || !refreshable(response)) return response
        response.close()
        return http.newCall(withBearer(request, refresh(pair.accessToken))).execute()
    }

    fun signOut() {
        synchronized(lock) {
            tokens.clear(gatewayId)
            _state.value = AuthState.SignedOut
        }
    }

    private fun withBearer(request: Request, pair: TokenPair) =
        request.newBuilder().header("Authorization", "Bearer ${pair.accessToken}").build()

    private fun replayable(request: Request): Boolean =
        request.method == "GET" || request.method == "HEAD" ||
            (request.method == "POST" && request.url.encodedPath == "/api/auth/ws-ticket")

    private fun refreshable(response: Response): Boolean = runCatching {
        val json = JSONObject(response.peekBody(64 * 1024).string())
        json.optString("error") in setOf("unauthenticated", "session_expired") && json.optString("reason").isNotEmpty()
    }.getOrDefault(false)

    /** [usedAccess] is the token the caller failed with; if another caller already rotated it, reuse that result. */
    private fun refresh(usedAccess: String): TokenPair = synchronized(lock) {
        val current = tokens.load(gatewayId) ?: throw IOException("Signed out — sign in again")
        if (current.accessToken != usedAccess) return current
        val body = JSONObject().put("refresh_token", current.refreshToken).put("provider", current.provider)
            .toString().toRequestBody("application/json".toMediaType())
        val response = try {
            http.newCall(Request.Builder().url("$base/auth/native/refresh").post(body).build()).execute()
        } catch (e: IOException) {
            _state.value = AuthState.AuthUnavailable
            throw IOException("Gateway auth unavailable, retrying")
        }
        response.use {
            when {
                it.code == 401 -> {
                    tokens.clear(gatewayId)
                    _state.value = AuthState.Expired
                    throw IOException("Signed out — sign in again")
                }
                !it.isSuccessful -> {
                    _state.value = AuthState.AuthUnavailable
                    throw IOException("Gateway auth unavailable, retrying")
                }
            }
            val json = JSONObject(it.body!!.string())
            TokenPair(
                json.getString("access_token"), json.getString("refresh_token"), json.getLong("expires_at"),
                json.optString("provider"), json.optString("user_id"),
            ).also { fresh ->
                tokens.save(gatewayId, fresh)
                _state.value = AuthState.SignedIn(fresh.userId)
            }
        }
    }
}
