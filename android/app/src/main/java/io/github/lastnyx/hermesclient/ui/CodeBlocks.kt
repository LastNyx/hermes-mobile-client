package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal fun codeLabel(language: String?): String =
    if (language.isNullOrBlank()) "code" else language.trim().lowercase().take(20)

internal fun codeForCopy(code: String): String = code.trimEnd('\n', '\r', ' ')

internal fun replyForCopy(text: String): String =
    text.lines().dropWhile { it.isBlank() }.joinToString("\n").trimEnd()

@Composable
internal fun CodeCard(code: String, language: String?, style: TextStyle) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(codeLabel(language), style = MaterialTheme.typography.labelMedium)
                CopyButton(codeForCopy(code))
            }
            Text(
                codeForCopy(code),
                style = style,
                fontFamily = FontFamily.Monospace,
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
        }
    }
}

@Composable
internal fun CopyReplyButton(text: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    Row(Modifier.fillMaxWidth()) {
        IconButton(onClick = {
            clipboard.setText(AnnotatedString(text))
            copied = true
        }) {
            Icon(
                if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                contentDescription = "Copy reply",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun CopyButton(text: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    TextButton(onClick = {
        clipboard.setText(AnnotatedString(text))
        copied = true
    }) { Text(if (copied) "Copied" else "Copy", style = MaterialTheme.typography.labelMedium) }
}
