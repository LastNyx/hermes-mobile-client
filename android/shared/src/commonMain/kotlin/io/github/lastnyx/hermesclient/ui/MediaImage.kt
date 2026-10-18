package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.media.ByteBudgetLru
import io.github.lastnyx.hermesclient.data.media.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaAccess(val repo: MediaRepository, val gatewayUrl: String, val sessionId: () -> String?)

/** Null means no gateway to fetch from (previews, tests): media lines stay text. */
val LocalMedia = compositionLocalOf<MediaAccess?> { null }

private const val INLINE_PX = 1080
internal const val VIEWER_PX = 2048
private const val BYTES_PER_PIXEL = 4L

// Process-wide budgets: scrolling back through a long chat must not run out of memory.
private val byteCache = ByteBudgetLru<String, ByteArray>(24L * 1024 * 1024) { it.size.toLong() }
private val bitmapCache = ByteBudgetLru<String, ImageBitmap>(48L * 1024 * 1024) { it.width * it.height * BYTES_PER_PIXEL }

private fun keyOf(access: MediaAccess, path: String) = "${access.gatewayUrl}|$path"

/** Blocking: call on [Dispatchers.IO]. */
internal fun fetchImageBytes(access: MediaAccess, path: String): ByteArray? {
    val key = keyOf(access, path)
    byteCache.get(key)?.let { return it }
    return access.repo.image(path).getOrNull()?.also { byteCache.put(key, it) }
}

internal suspend fun loadViewerBitmap(access: MediaAccess, path: String): ImageBitmap? = withContext(Dispatchers.IO) {
    fetchImageBytes(access, path)?.let { decodeImage(it, VIEWER_PX) }
}

/** Pictures stacked with the small in-turn gap; a tap opens the full-screen viewer. */
@Composable
fun MediaImageStack(paths: List<String>, modifier: Modifier = Modifier) {
    if (LocalMedia.current == null || paths.isEmpty()) return
    var viewing by remember { mutableStateOf<String?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(INSIDE_TURN_GAP.dp)) {
        paths.forEach { path -> MediaImage(path, onClick = { viewing = path }) }
    }
    viewing?.let { MediaViewer(it) { viewing = null } }
}

@Composable
fun MediaImage(path: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val access = LocalMedia.current ?: return
    val key = keyOf(access, path)
    var bitmap by remember(key) { mutableStateOf(bitmapCache.get(key)) }
    var failed by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (bitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { fetchImageBytes(access, path)?.let { decodeImage(it, INLINE_PX) } }
        if (loaded == null) failed = true else {
            bitmapCache.put(key, loaded)
            bitmap = loaded
        }
    }
    val shown = bitmap
    if (shown != null) {
        Image(
            shown,
            contentDescription = "Picture",
            modifier.widthIn(max = MEDIA_MAX_WIDTH.dp).heightIn(max = MEDIA_MAX_HEIGHT.dp)
                .let { if (onClick != null) it.clickable(onClick = onClick) else it },
            contentScale = ContentScale.Fit,
        )
    } else if (failed) {
        Text("[picture unavailable]", modifier, style = roles.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
