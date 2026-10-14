package io.github.lastnyx.hermesclient.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/**
 * One style per kind of text, all taken from the scaled MaterialTheme typography so the
 * text-size setting reaches every screen. Body = bodyMedium (replies, bubbles, command output,
 * prompts, Thinking), Secondary = bodySmall, Label = labelLarge, Caption = labelSmall,
 * Title = titleSmall, Code = bodySmall in the one monospace family.
 */
class TextRoles(typography: Typography) {
    val body: TextStyle = typography.bodyMedium
    val secondary: TextStyle = typography.bodySmall
    val label: TextStyle = typography.labelLarge
    val caption: TextStyle = typography.labelSmall
    val title: TextStyle = typography.titleSmall
    val code: TextStyle = monospace(typography.bodySmall)

    fun monospace(base: TextStyle): TextStyle = base.copy(fontFamily = FontFamily.Monospace)
}

val roles: TextRoles
    @Composable @ReadOnlyComposable get() = TextRoles(MaterialTheme.typography)
