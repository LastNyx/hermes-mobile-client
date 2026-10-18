package io.github.lastnyx.hermesclient.data.auth

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Android: a Chrome Custom Tab. JVM tests: a fake that hits the receiver. */
fun interface BrowserLauncher {
    fun open(url: String)
}

class NativeSignIn(
    private val http: OkHttpClient,
    private val tokens: TokenStore,
    private val browser: BrowserLauncher,
    private val timeoutMs: Long = 600_000,
) {
    /** Blocking; call off the main thread. Failure messages are user-facing and never contain secrets. */
    fun signIn(gatewayId: String, baseUrl: String): Result<TokenPair> = runCatching {
        val base = baseUrl.trimEnd('/')
        val verifier = Pkce.verifier()
        val state = Pkce.state()
        LoopbackReceiver(state).use { receiver ->
            val authorize = (base + "/auth/native/authorize").toHttpUrl().newBuilder()
                .addQueryParameter("code_challenge", Pkce.challenge(verifier))
                .addQueryParameter("code_challenge_method", "S256")
                .addQueryParameter("redirect_uri", receiver.redirectUri)
                .addQueryParameter("state", state)
                .build()
            browser.open(authorize.toString())
            val code = receiver.awaitCode(timeoutMs).getOrThrow()
            redeem(base, code, verifier).also { tokens.save(gatewayId, it) }
        }
    }

    private fun redeem(base: String, code: String, verifier: String): TokenPair {
        val body = JSONObject().put("code", code).put("code_verifier", verifier).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("$base/auth/native/token").post(body).build()
        http.newCall(request).execute().use { response ->
            if (response.code == 400) throw IllegalStateException("Sign-in expired, try again")
            check(response.isSuccessful) { "Gateway answered HTTP ${response.code}" }
            val json = JSONObject(response.body!!.string())
            return TokenPair(
                json.getString("access_token"), json.getString("refresh_token"), json.getLong("expires_at"),
                json.optString("provider"), json.optString("user_id"),
            )
        }
    }
}
