package io.github.lastnyx.hermesclient.desktop

import io.github.lastnyx.hermesclient.data.gateway.GatewayRegistry
import io.github.lastnyx.hermesclient.data.gateway.GatewayStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopShellStateTest {
    private class Memory : GatewayStorage {
        var text: String? = null
        override fun load() = text
        override fun save(text: String) {
            this.text = text
        }
    }

    private fun shell(vararg names: String, signedOut: Set<String> = emptySet()): Pair<DesktopShellState, List<String>> {
        val registry = GatewayRegistry(Memory())
        val ids = names.map { registry.add(it, "https://$it.example.com").getOrThrow().id }
        return DesktopShellState(registry) { it !in signedOut.map { n -> ids[names.indexOf(n)] } } to ids
    }

    @Test
    fun selectionManageSignInAndRemoval() {
        val (state, ids) = shell("a", "b", signedOut = setOf("b"))
        state.select(ids[0])
        state.openManage()
        assertTrue(state.right is RightSide.Manage)
        state.select(ids[0])
        assertEquals(ids[0], (state.right as RightSide.Sessions).gateway.id)

        state.select(ids[1])
        assertEquals(ids[1], (state.right as RightSide.SignIn).gateway.id)

        state.remove(ids[1])
        assertEquals(ids[0], (state.right as RightSide.Sessions).gateway.id)
        state.remove(ids[0])
        assertTrue(state.right is RightSide.Manage)
        assertEquals(null, state.selected)
    }

    @Test
    fun openSessionSelectsGatewayAndSetsThenClearsPendingSession() {
        val (state, ids) = shell("a", "b")
        state.select(ids[0])
        state.openManage()
        state.openSession(ids[1], "s9")
        assertEquals(ids[1], state.selected?.id)
        assertEquals(ids[1], (state.right as RightSide.Sessions).gateway.id)
        assertEquals("s9", state.pendingSession)
        state.sessionOpened()
        assertEquals(null, state.pendingSession)
    }

    @Test
    fun settingsAndCapabilitiesOpenAndGoBack() {
        val (state, ids) = shell("a", "b", signedOut = setOf("b"))
        state.select(ids[0])
        state.openSettings()
        assertEquals(ids[0], (state.right as RightSide.Settings).gateway?.id)
        state.openCapabilities()
        assertEquals(ids[0], (state.right as RightSide.Capabilities).gateway.id)
        state.back()
        assertTrue(state.right is RightSide.Settings)
        state.back()
        assertEquals(ids[0], (state.right as RightSide.Sessions).gateway.id)

        state.openSettings()
        state.select(ids[1])
        assertTrue(state.right is RightSide.SignIn)
        state.openSettings()
        assertEquals(null, (state.right as RightSide.Settings).gateway)
        state.openCapabilities()
        assertTrue(state.right is RightSide.Settings)
        state.back()
        assertTrue(state.right is RightSide.SignIn)

        state.openSettings()
        state.openSession(ids[0], "s1")
        assertTrue(state.right is RightSide.Sessions)

        state.openSettings()
        state.openManage()
        assertTrue(state.right is RightSide.Manage)
        state.openSettings()
        state.back()
        assertTrue(state.right is RightSide.Manage)
    }
}
