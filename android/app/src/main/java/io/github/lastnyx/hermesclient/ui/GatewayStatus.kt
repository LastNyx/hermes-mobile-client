package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.auth.SignInState

enum class StatusTone { Normal, Good, Warning, Error }

data class GatewayStatus(val text: String, val tone: StatusTone)

// SignInController keeps these two strings private; they must match its text.
private const val SIGNED_IN_FALLBACK = "Signed in"
private const val REJECTED_REFRESH = "Signed out — sign in again"

internal fun gatewayStatusLine(state: SignInState, testResult: String?): GatewayStatus {
    val base = when (state) {
        is SignInState.SignedIn ->
            GatewayStatus(
                if (state.name == SIGNED_IN_FALLBACK) state.name else "Signed in as ${state.name}",
                StatusTone.Good,
            )
        SignInState.SigningIn -> GatewayStatus("Signing in…", StatusTone.Normal)
        SignInState.Unavailable -> GatewayStatus("Gateway auth unavailable, retrying", StatusTone.Warning)
        is SignInState.SignedOut -> when (state.message) {
            null -> GatewayStatus("Signed out", StatusTone.Normal)
            REJECTED_REFRESH -> GatewayStatus(state.message, StatusTone.Error)
            else -> GatewayStatus(state.message, StatusTone.Normal)
        }
    }
    return if (testResult == null) base else base.copy(text = "${base.text} · $testResult")
}
