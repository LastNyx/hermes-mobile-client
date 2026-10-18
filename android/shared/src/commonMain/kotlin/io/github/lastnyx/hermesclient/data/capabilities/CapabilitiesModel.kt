package io.github.lastnyx.hermesclient.data.capabilities

import io.github.lastnyx.hermesclient.data.sessions.CANT_REACH_GATEWAY
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class CapabilitiesState(
    val skills: List<SkillItem> = emptyList(),
    val toolsets: List<ToolsetItem> = emptyList(),
    val servers: List<McpServerItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val testing: String? = null,
    val testResults: Map<String, McpTestResult> = emptyMap(),
    val notice: String? = null,
)

private const val CHANGE_FAILED = "Could not change that. Try again."
private const val LOAD_FAILED = "Could not load"

class CapabilitiesModel(private val repo: CapabilitiesRepository, private val io: CoroutineDispatcher) {
    private val _state = MutableStateFlow(CapabilitiesState())
    val state: StateFlow<CapabilitiesState> = _state

    suspend fun load() {
        _state.update { it.copy(loading = true, error = null) }
        val (skills, toolsets, servers) = withContext(io) { Triple(repo.skills(), repo.toolsets(), repo.mcpServers()) }
        val failure = listOf(skills, toolsets, servers).firstNotNullOfOrNull { it.exceptionOrNull() }
        _state.update {
            it.copy(
                skills = skills.getOrDefault(it.skills),
                toolsets = toolsets.getOrDefault(it.toolsets),
                servers = servers.getOrDefault(it.servers),
                loading = false,
                error = failure?.let { f -> if (f is java.io.IOException || f.message == CANT_REACH_GATEWAY) CANT_REACH_GATEWAY else LOAD_FAILED },
            )
        }
    }

    suspend fun toggleSkill(name: String, enabled: Boolean) = toggle(
        { it.skills }, { s, list -> s.copy(skills = list) }, { it.name == name }, { it.copy(enabled = enabled) },
    ) { repo.setSkill(name, enabled) }

    suspend fun toggleToolset(name: String, enabled: Boolean) = toggle(
        { it.toolsets }, { s, list -> s.copy(toolsets = list) }, { it.name == name }, { it.copy(enabled = enabled) },
    ) { repo.setToolset(name, enabled) }

    suspend fun toggleMcp(name: String, enabled: Boolean) {
        if (_state.value.servers.firstOrNull { it.name == name }?.locked == true) return
        toggle(
            { it.servers }, { s, list -> s.copy(servers = list) }, { it.name == name }, { it.copy(enabled = enabled) },
        ) { repo.setMcp(name, enabled) }
    }

    suspend fun testMcp(name: String) {
        _state.update { it.copy(testing = name) }
        val result = withContext(io) { repo.testMcp(name) }
        _state.update {
            it.copy(
                testing = null,
                testResults = it.testResults + (name to (result.getOrNull() ?: McpTestResult(false, emptyList(), CANT_REACH_GATEWAY))),
            )
        }
    }

    // Optimistic: show the new value now, restore the previous list if the gateway refuses.
    private suspend fun <T> toggle(
        list: (CapabilitiesState) -> List<T>,
        with: (CapabilitiesState, List<T>) -> CapabilitiesState,
        matches: (T) -> Boolean,
        change: (T) -> T,
        send: () -> Result<Unit>,
    ) {
        val before = list(_state.value)
        _state.update { with(it, before.map { row -> if (matches(row)) change(row) else row }).copy(error = null) }
        withContext(io) { send() }.onFailure {
            _state.update { with(it, before).copy(error = CHANGE_FAILED) }
        }
    }
}
