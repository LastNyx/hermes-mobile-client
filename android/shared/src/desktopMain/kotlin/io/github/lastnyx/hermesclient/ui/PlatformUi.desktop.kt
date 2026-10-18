package io.github.lastnyx.hermesclient.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import org.jetbrains.skia.Image

// Skia decodes at full size and the GPU scales it, so targetPx needs no sampling here.
actual fun decodeImage(bytes: ByteArray, targetPx: Int): ImageBitmap? = try {
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
} catch (e: Exception) {
    null
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun screenSizeDp(): DpSize {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return with(density) { DpSize(size.width.toDp(), size.height.toDp()) }
}

// A window has no system Back; the screens keep their own Back buttons.
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

actual fun elapsedMs(): Long = System.nanoTime() / NANOS_PER_MILLI

private const val NANOS_PER_MILLI = 1_000_000L
