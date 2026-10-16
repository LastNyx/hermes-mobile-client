package io.github.lastnyx.hermesclient.data.profiles

import io.github.lastnyx.hermesclient.data.sessions.CANT_REACH_GATEWAY
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class ProfilesState(
    val profiles: List<ProfileItem> = emptyList(),
    val selected: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val current: String? = null,
)

private const val LOAD_FAILED = "Could not load profiles"

class ProfilesModel(
    private val repo: ProfilesRepository,
    private val io: CoroutineDispatcher,
    private val store: ProfileStore,
    private val gatewayId: String,
) {
    private val _state = MutableStateFlow(ProfilesState(selected = store.selected(gatewayId)))
    val state: StateFlow<ProfilesState> = _state

    suspend fun load() {
        _state.update { it.copy(loading = true, error = null) }
        val list = withContext(io) { repo.list() }
        // The marker is optional: a failed call just leaves it off.
        val current = if (list.isSuccess) withContext(io) { repo.current() }.getOrNull() else null
        _state.update {
            it.copy(
                profiles = list.getOrDefault(it.profiles),
                current = if (list.isSuccess) current else it.current,
                loading = false,
                error = list.exceptionOrNull()?.let { f -> if (f is IOException) CANT_REACH_GATEWAY else LOAD_FAILED },
            )
        }
    }

    fun select(item: ProfileItem) {
        val name = if (item.isDefault) null else item.name
        store.select(gatewayId, name)
        _state.update { it.copy(selected = name) }
    }
}
