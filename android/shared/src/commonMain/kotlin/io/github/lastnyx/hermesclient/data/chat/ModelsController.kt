package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.friendlyError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

data class ProviderGroup(val slug: String, val name: String, val models: List<String>)

data class ModelsState(
    val groups: List<ProviderGroup> = emptyList(),
    val model: String = "",
    val provider: String = "",
    val reasoningEffort: String = "",
    val reasoningDisplay: String = "",
    /** Set when the gateway wants the user to confirm an expensive model; the text is the gateway's. */
    val confirmMessage: String? = null,
    val error: String? = null,
)

val REASONING_LEVELS = listOf("none", "minimal", "low", "medium", "high", "xhigh", "max", "ultra")

data class ReasoningCurrent(val value: String, val display: String)

fun reasoningFromConfig(result: JSONObject): ReasoningCurrent =
    ReasoningCurrent(result.optString("value"), result.optString("display"))

/** Model picker and reasoning selector for the chat's session. */
class ModelsController(private val chat: ChatController) {
    private val _state = MutableStateFlow(ModelsState())
    val state: StateFlow<ModelsState> = _state

    suspend fun load() {
        try {
            val result = chat.sessionCall("model.options") { it }
            val array = result.optJSONArray("providers")
            val groups = (0 until (array?.length() ?: 0)).map { i ->
                val p = array!!.getJSONObject(i)
                val models = p.optJSONArray("models")
                ProviderGroup(
                    p.optString("slug"),
                    p.optString("name").ifEmpty { p.optString("slug") },
                    (0 until (models?.length() ?: 0)).map { models!!.getString(it) },
                )
            }
            _state.update { it.copy(groups = groups, model = result.optString("model"), provider = result.optString("provider"), error = null) }
        } catch (e: Exception) {
            fail(e)
            return
        }
        // The model list is the important part, so a failed reasoning read stays silent.
        try {
            val current = reasoningFromConfig(chat.sessionCall("config.get") { it.put("key", "reasoning") })
            _state.update { it.copy(reasoningEffort = current.value, reasoningDisplay = current.display) }
        } catch (e: Exception) {
            _state.update { it.copy(reasoningEffort = "", reasoningDisplay = "") }
        }
    }

    private var pending: Pair<String, String>? = null

    suspend fun confirmPending() {
        val (provider, model) = pending ?: return
        selectModel(provider, model, confirmExpensive = true)
    }

    fun cancelConfirm() {
        pending = null
        _state.update { it.copy(confirmMessage = null) }
    }

    suspend fun selectModel(provider: String, model: String, confirmExpensive: Boolean = false) {
        try {
            val result = chat.sessionCall("config.set") {
                it.put("key", "model").put("value", "$model --provider $provider --session")
                if (confirmExpensive) it.put("confirm_expensive_model", true)
                it
            }
            if (result.optBoolean("confirm_required")) {
                pending = provider to model
                _state.update { it.copy(confirmMessage = result.optString("confirm_message").ifEmpty { "Use this model?" }) }
                return
            }
            pending = null
            _state.update { it.copy(model = model, provider = provider, confirmMessage = null, error = null) }
        } catch (e: Exception) {
            fail(e)
        }
    }

    suspend fun selectReasoning(level: String) {
        try {
            chat.sessionCall("config.set") { it.put("key", "reasoning").put("value", level) }
            _state.update { it.copy(reasoningEffort = level, reasoningDisplay = "", error = null) }
        } catch (e: Exception) {
            fail(e)
        }
    }

    private fun fail(e: Exception) {
        _state.update { it.copy(error = friendlyError(e)) }
    }
}
