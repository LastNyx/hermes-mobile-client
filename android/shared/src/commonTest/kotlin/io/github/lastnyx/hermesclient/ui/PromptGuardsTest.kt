package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.live.LinkState
import org.junit.Assert.assertEquals
import org.junit.Test

class PromptGuardsTest {
    @Test
    fun tapGuardWaitsForTheDelayAndGuardsAreIndependent() {
        var t = 0L
        val first = TapGuard({ t })
        assertEquals(false, first.allows())
        t = 699
        assertEquals(false, first.allows())
        t = 700
        assertEquals(true, first.allows())
        t = 5000
        assertEquals(true, first.allows())
        val second = TapGuard({ t })
        assertEquals(false, second.allows())
        assertEquals(true, first.allows())
    }

    @Test
    fun actionsAreEnabledOnlyWhenConnected() {
        assertEquals(true, promptActionsEnabled(LinkState.Connected))
        for (s in listOf(LinkState.Connecting, LinkState.Offline, LinkState.SignedOut, LinkState.Error("x"))) {
            assertEquals(false, promptActionsEnabled(s))
        }
    }

    @Test
    fun previewCutsAfterFourLines() {
        assertEquals(CommandPreview("", false), commandPreview(""))
        assertEquals(CommandPreview("a", false), commandPreview("a"))
        assertEquals(CommandPreview("a\nb\nc\nd", false), commandPreview("a\nb\nc\nd"))
        assertEquals(CommandPreview("a\nb\nc\nd\n", false), commandPreview("a\nb\nc\nd\n"))
        assertEquals(CommandPreview("a\nb\nc\nd", true), commandPreview("a\nb\nc\nd\ne"))
    }
}
