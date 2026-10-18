package io.github.lastnyx.hermesclient.ui

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.chat.sampleSizeFor

actual fun decodeImage(bytes: ByteArray, targetPx: Int): ImageBitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetPx) }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
} catch (e: Exception) {
    null
} catch (e: OutOfMemoryError) {
    null
}

@Composable
actual fun screenSizeDp(): DpSize {
    val config = LocalConfiguration.current
    return DpSize(config.screenWidthDp.dp, config.screenHeightDp.dp)
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled = enabled, onBack = onBack)

actual fun elapsedMs(): Long = SystemClock.elapsedRealtime()
