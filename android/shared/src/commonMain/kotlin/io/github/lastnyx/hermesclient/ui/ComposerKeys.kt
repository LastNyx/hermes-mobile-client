package io.github.lastnyx.hermesclient.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

enum class EnterAction { Send, Newline, Default }

fun enterAction(shift: Boolean, enterSends: Boolean): EnterAction = when {
    !enterSends -> EnterAction.Default
    shift -> EnterAction.Newline
    else -> EnterAction.Send
}

fun withNewline(value: TextFieldValue): TextFieldValue {
    val start = value.selection.min
    val text = value.text.replaceRange(start, value.selection.max, "\n")
    return TextFieldValue(text, TextRange(start + 1))
}
