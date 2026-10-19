package io.github.lastnyx.hermesclient.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.DpSize

/** Decodes at most about [targetPx] on the long side; null when the bytes are not a picture. */
expect fun decodeImage(bytes: ByteArray, targetPx: Int): ImageBitmap?

/** Size of the screen (Android) or window (desktop) in dp. */
@Composable
expect fun screenSizeDp(): DpSize

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** Monotonic milliseconds for tap guards. */
expect fun elapsedMs(): Long

/** True where a hardware Enter key sends the message (desktop); the phone's soft Enter adds a line. */
expect val enterSendsMessage: Boolean
