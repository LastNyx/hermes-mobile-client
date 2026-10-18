package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.auth.SAVED_SIGN_IN_UNREADABLE
import io.github.lastnyx.hermesclient.data.auth.SignInController
import io.github.lastnyx.hermesclient.data.auth.SignInState

enum class StatusTone { Normal, Good, Warning, Error }

data class GatewayStatus(val text: String, val tone: StatusTone)

internal fun gatewayStatusLine(state: SignInState, testResult: String?): GatewayStatus {
    val base = when (state) {
        is SignInState.SignedIn ->
            GatewayStatus(
                if (state.name == SignInController.FALLBACK_NAME) state.name else "Signed in as ${state.name}",
                StatusTone.Good,
            )
        SignInState.SigningIn -> GatewayStatus("Signing in…", StatusTone.Normal)
        SignInState.Unavailable -> GatewayStatus("Gateway auth unavailable, retrying", StatusTone.Warning)
        is SignInState.SignedOut -> when (val message = state.message) {
            null -> GatewayStatus("Signed out", StatusTone.Normal)
            SignInController.SIGNED_OUT_MESSAGE, SAVED_SIGN_IN_UNREADABLE -> GatewayStatus(message, StatusTone.Error)
            else -> GatewayStatus(message, StatusTone.Normal)
        }
    }
    return if (testResult == null) base else base.copy(text = "${base.text} · $testResult")
}
