package io.github.lastnyx.hermesclient.data.hub

import io.github.lastnyx.hermesclient.data.gateway.Gateway
import io.github.lastnyx.hermesclient.data.gateway.GatewayRegistry
import io.github.lastnyx.hermesclient.data.gateway.GatewayStorage
import io.github.lastnyx.hermesclient.data.live.LinkEvent
import io.github.lastnyx.hermesclient.data.live.LinkState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HubModelTest {
    private class Memory : GatewayStorage {
        var text: String? = null
        override fun load() = text
        override fun save(text: String) { this.text = text }
    }

    private class FakeLink : HubLink {
        override val state = MutableStateFlow<LinkState>(LinkState.Connecting)
        override val events = MutableSharedFlow<LinkEvent>(extraBufferCapacity = 16)
        var running = false
        var busy = 0
        var waiting = emptyList<String>()
        override fun start() { running = true }
        override fun stop() { running = false }
        override suspend fun call(method: String, params: JSONObject): JSONObject {
            assertEquals("session.active_list", method)
            val items = JSONArray()
            repeat(busy) { items.put(JSONObject().put("status", "working")) }
            waiting.forEach { items.put(JSONObject().put("status", "waiting").put("session_key", it)) }
            items.put(JSONObject().put("status", "idle"))
            return JSONObject().put("sessions", items)
        }
    }

    @Test
    fun needsMeDropsUnreachableAndRemovedGateways() = runTest {
        val registry = GatewayRegistry(Memory())
        val laptop = registry.add("Laptop", "http://laptop").getOrThrow()
        val work = registry.add("WorkLab", "http://work").getOrThrow()
        val links = mutableMapOf<String, FakeLink>()
        val model = HubModel(
            registry = registry,
            signedIn = { true },
            linkFor = { g: Gateway -> FakeLink().also { links[g.id] = it } },
            version = { "0.22.2" },
            scope = backgroundScope,
        )
        model.start()
        runCurrent()
        links[laptop.id]!!.waiting = listOf("a")
        links[work.id]!!.waiting = listOf("b")
        links.values.forEach { it.state.value = LinkState.Connected }
        runCurrent()
        assertEquals(setOf("a" to laptop.id, "b" to work.id), model.needsMe.value.map { it.sessionId to it.gateway.id }.toSet())

        links[laptop.id]!!.state.value = LinkState.Offline
        runCurrent()
        assertEquals(listOf("b"), model.needsMe.value.map { it.sessionId })

        registry.remove(work.id)
        model.refresh()
        runCurrent()
        assertEquals(emptyList<NeedsMeItem>(), model.needsMe.value)
    }

    @Test
    fun linksFollowSignInAndStatesMapFromLinks() = runTest {
        val registry = GatewayRegistry(Memory())
        val laptop = registry.add("Laptop", "http://laptop").getOrThrow()
        val work = registry.add("WorkLab", "http://work").getOrThrow()
        val out = registry.add("Old", "http://old").getOrThrow()
        val links = mutableMapOf<String, FakeLink>()
        val model = HubModel(
            registry = registry,
            signedIn = { it != out.id },
            linkFor = { g: Gateway -> FakeLink().also { links[g.id] = it } },
            version = { "0.22.2" },
            scope = backgroundScope,
        )
        model.start()
        runCurrent()

        assertEquals(setOf(laptop.id, work.id), links.keys)
        assertTrue(links.values.all { it.running })
        links[laptop.id]!!.busy = 2
        links[laptop.id]!!.state.value = LinkState.Connected
        links[work.id]!!.state.value = LinkState.Offline
        runCurrent()
        val states = model.states.value
        assertEquals(
            listOf(
                GatewayState.Online(laptop, "0.22.2", 2),
                GatewayState.Unreachable(work),
                GatewayState.SignedOut(out),
            ),
            states,
        )

        links[laptop.id]!!.busy = 1
        advanceTimeBy(10_001)
        assertEquals(GatewayState.Online(laptop, "0.22.2", 1), model.states.value[0])

        links[laptop.id]!!.busy = 3
        links[laptop.id]!!.events.tryEmit(LinkEvent("sessions.changed", null, null, null))
        runCurrent()
        assertEquals(GatewayState.Online(laptop, "0.22.2", 3), model.states.value[0])

        links[laptop.id]!!.state.value = LinkState.Offline
        runCurrent()
        assertEquals(GatewayState.Unreachable(laptop), model.states.value[0])
        links[laptop.id]!!.state.value = LinkState.Connected
        runCurrent()
        assertTrue(model.states.value[0] is GatewayState.Online)

        registry.remove(work.id)
        model.refresh()
        runCurrent()
        assertTrue(!links[work.id]!!.running)
        assertEquals(2, model.states.value.size)
    }
}
