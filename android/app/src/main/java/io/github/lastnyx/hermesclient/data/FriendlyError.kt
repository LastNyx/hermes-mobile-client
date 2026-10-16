package io.github.lastnyx.hermesclient.data

import io.github.lastnyx.hermesclient.data.live.RpcError
import io.github.lastnyx.hermesclient.data.sessions.CANT_REACH_GATEWAY
import java.io.IOException

class HttpFailure(val code: Int) : Exception("HTTP $code")

private const val GENERIC = "Something went wrong. Try again."
private const val GATEWAY_ERROR = "The gateway hit an error. Try again."

private val SIGN_IN_SENTENCES = setOf("Sign-in expired, try again", "Sign-in timed out", "Sign-in returned no code")

/** Never reads the throwable's message: gateway and OkHttp text can carry hosts or internals. */
fun friendlyError(t: Throwable): String = when (t) {
    is IOException -> CANT_REACH_GATEWAY
    is RpcError -> when (t.code) {
        4009 -> "The agent is busy. Wait for it to finish, then try again."
        4001 -> "This chat session expired. Reopen the chat."
        4130 -> "This chat is too long to continue. Start a new chat."
        4018 -> "That changed on the gateway. Reload the chat and try again."
        4028 -> "That would erase the whole chat. Nothing was changed."
        in 5000..5999 -> GATEWAY_ERROR
        else -> GENERIC
    }
    is HttpFailure -> when (t.code) {
        401, 403 -> "Signed out. Sign in again."
        404 -> "The gateway doesn't have that."
        409 -> "That can't be done right now. Try again in a moment."
        in 500..599 -> GATEWAY_ERROR
        else -> GENERIC
    }
    else -> GENERIC
}

fun signInErrorText(t: Throwable): String = when {
    t is IOException -> CANT_REACH_GATEWAY
    t is IllegalStateException && t.message in SIGN_IN_SENTENCES -> t.message!!
    else -> "Sign-in failed. Try again."
}
