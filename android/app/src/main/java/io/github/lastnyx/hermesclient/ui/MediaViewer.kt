package io.github.lastnyx.hermesclient.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.lastnyx.hermesclient.data.media.clampOffset
import io.github.lastnyx.hermesclient.data.media.clampScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-screen picture: pinch 1x to 6x, drag to pan, Save and Share at the bottom. */
@Composable
fun MediaViewer(path: String, onDismiss: () -> Unit) {
    val access = LocalMedia.current ?: return
    val actions = rememberMediaActions()
    val scope = rememberCoroutineScope()
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(path) { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableFloatStateOf(0f) }
    val name = path.substringAfterLast('/').substringAfterLast('\\')
    LaunchedEffect(path) {
        val loaded = loadViewerBitmap(access, path)
        if (loaded == null) failed = true else bitmap = loaded
    }

    fun withBytes(act: (ByteArray) -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { fetchImageBytes(access, path) }?.let(act)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { widthPx = it.width.toFloat(); heightPx = it.height.toFloat() }) {
            val shown = bitmap
            if (shown != null) {
                Image(
                    shown.asImageBitmap(),
                    contentDescription = "Picture",
                    Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = clampScale(scale * zoom)
                                offsetX = clampOffset(offsetX + pan.x, scale, widthPx)
                                offsetY = clampOffset(offsetY + pan.y, scale, heightPx)
                            }
                        }
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
                    contentScale = ContentScale.Fit,
                )
            } else if (failed) {
                Text("[picture unavailable]", Modifier.align(Alignment.Center), style = roles.caption, color = Color.White)
            }
            IconButton(onClick = onDismiss, Modifier.align(Alignment.TopStart)) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(SCREEN_EDGE.dp)) {
                TextButton(enabled = shown != null, onClick = { withBytes { actions.save(name, it) } }) { Text("Save") }
                TextButton(enabled = shown != null, onClick = { withBytes { actions.share(name, it) } }) { Text("Share") }
                actions.error?.let { Text(it, Modifier.align(Alignment.CenterVertically), style = roles.caption, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
