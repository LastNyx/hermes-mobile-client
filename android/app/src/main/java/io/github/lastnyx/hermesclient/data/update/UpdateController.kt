package io.github.lastnyx.hermesclient.data.update

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val INSTALL_START_FAILED = "Couldn't start the install"

data class UpdateUi(val status: String = "", val available: UpdateInfo? = null, val busy: Boolean = false)

/** Holds what Settings shows; the startup check is silent unless an update exists. */
class UpdateController(
    private val updater: Updater,
    private val cacheDir: File,
    private val io: CoroutineDispatcher,
    private val install: (File) -> Unit,
) {
    private val _state = MutableStateFlow(UpdateUi())
    val state: StateFlow<UpdateUi> = _state

    suspend fun check(silent: Boolean) {
        if (_state.value.busy) return
        if (!silent) _state.value = _state.value.copy(busy = true, status = "Checking…")
        val result = kotlinx.coroutines.withContext(io) { updater.check() }
        _state.value = result.fold(
            onSuccess = {
                when (it) {
                    is UpdateOutcome.Available -> UpdateUi("Update available: ${it.info.version}", it.info)
                    UpdateOutcome.UpToDate -> if (silent) UpdateUi() else UpdateUi("You're up to date")
                    UpdateOutcome.NonePublished -> if (silent) UpdateUi() else UpdateUi("No updates published yet")
                }
            },
            onFailure = { if (silent) UpdateUi() else UpdateUi(it.message ?: CANT_REACH_GITHUB) },
        )
    }

    fun installFailed(message: String) {
        _state.value = UpdateUi(message, _state.value.available)
    }

    suspend fun installAvailable() {
        val info = _state.value.available ?: return
        _state.value = _state.value.copy(busy = true, status = "Downloading…")
        val result = kotlinx.coroutines.withContext(io) { updater.download(info, File(cacheDir, "update.apk")) }
        result.onSuccess {
            _state.value = UpdateUi("Installing…", info)
            try {
                install(it)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateUi(INSTALL_START_FAILED, info)
            }
        }.onFailure { _state.value = UpdateUi(it.message ?: CANT_REACH_GITHUB, info) }
    }
}
