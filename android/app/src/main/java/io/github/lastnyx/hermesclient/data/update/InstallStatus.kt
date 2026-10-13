package io.github.lastnyx.hermesclient.data.update

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

private const val SIGNATURE_HINT = "Install failed; the update must be signed with the same key"

/** Maps a PackageInstaller status to a plain sentence; null for success and "user action needed". */
fun installFailureMessage(status: Int): String? = when (status) {
    0, -1 -> null
    1, 5 -> SIGNATURE_HINT
    2 -> "Install was blocked on this device"
    3 -> "Install was cancelled"
    6 -> "Not enough storage to install the update"
    else -> "Install failed"
}

/** Process-wide hand-off from the manifest receiver to whoever shows Settings. */
object InstallFailures {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages
    fun report(message: String) { _messages.tryEmit(message) }
}
