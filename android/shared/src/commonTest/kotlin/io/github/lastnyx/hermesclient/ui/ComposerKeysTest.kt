package io.github.lastnyx.hermesclient.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerKeysTest {
    @Test
    fun enterSendsOnDesktopAndShiftEnterAddsALine() {
        assertEquals(EnterAction.Send, enterAction(shift = false, enterSends = true))
        assertEquals(EnterAction.Newline, enterAction(shift = true, enterSends = true))
        assertEquals(EnterAction.Default, enterAction(shift = false, enterSends = false))
        assertEquals(EnterAction.Default, enterAction(shift = true, enterSends = false))

        assertEquals(TextFieldValue("ab\n", TextRange(3)), withNewline(TextFieldValue("ab", TextRange(2))))
        assertEquals(TextFieldValue("a\nb", TextRange(2)), withNewline(TextFieldValue("ab", TextRange(1))))
        assertEquals(TextFieldValue("a\nd", TextRange(2)), withNewline(TextFieldValue("abcd", TextRange(1, 3))))
    }
}
