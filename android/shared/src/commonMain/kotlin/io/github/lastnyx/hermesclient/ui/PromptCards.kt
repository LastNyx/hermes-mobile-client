package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.chat.Prompt

@Composable
internal fun ApprovalCard(prompt: Prompt.Approval, enabled: Boolean, guard: TapGuard, onChoose: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(PROMPT_CARD_PADDING.dp), verticalArrangement = Arrangement.spacedBy(PROMPT_CARD_SPACING.dp)) {
            val preview = commandPreview(prompt.command)
            var full by rememberSaveable(prompt.id) { mutableStateOf(false) }
            SelectionContainer(Modifier.heightIn(max = promptBodyMaxHeight()).verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PROMPT_CARD_SPACING.dp)) {
                    Text(prompt.toolName.ifBlank { "Approval needed" }, style = roles.title)
                    if (prompt.description.isNotBlank()) Text(prompt.description, style = roles.body)
                    Text(if (preview.cut && !full) preview.text else prompt.command, style = roles.code)
                }
            }
            if (preview.cut) TextButton(onClick = { full = !full }) { Text(if (full) "Show less" else "Show full command") }
            val labels = listOf("once" to "Allow once", "session" to "Allow for session", "always" to "Always allow", "deny" to "Deny")
            labels.filter { it.first in prompt.choices }.forEach { (choice, label) ->
                val tap = { if (guard.allows()) onChoose(choice) }
                if (choice == "deny") OutlinedButton(onClick = tap, Modifier.fillMaxWidth(), enabled = enabled) { WrappedLabel(label) }
                else Button(onClick = tap, Modifier.fillMaxWidth(), enabled = enabled) { WrappedLabel(label) }
            }
        }
    }
}

fun promptBodyMaxHeight(screenHeight: Dp): Dp = (screenHeight.value * 3 / 5).dp

@Composable
private fun promptBodyMaxHeight(): Dp = promptBodyMaxHeight(screenSizeDp().height)

@Composable
private fun WrappedLabel(text: String) = Text(text, Modifier.fillMaxWidth(), textAlign = TextAlign.Start)

@Composable
internal fun ClarifyCard(prompt: Prompt.Clarify, enabled: Boolean, guard: TapGuard, onAnswer: (Map<String, String>) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(PROMPT_CARD_PADDING.dp), verticalArrangement = Arrangement.spacedBy(PROMPT_CARD_SPACING.dp)) {
            // §5.1 documents one string per question id, so multi-select is answered as a single value too.
            var answers by remember(prompt.id) { mutableStateOf(mapOf<String, String>()) }
            Column(
                Modifier.heightIn(max = promptBodyMaxHeight()).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PROMPT_CARD_SPACING.dp),
            ) {
                prompt.questions.forEach { q ->
                    SelectionContainer { Text(q.question, style = roles.body) }
                    q.choices.forEach { choice ->
                        val picked = answers[q.qid] == choice
                        val pick = { answers = answers + (q.qid to choice) }
                        if (picked) Button(onClick = pick, Modifier.fillMaxWidth()) { WrappedLabel(choice) }
                        else OutlinedButton(onClick = pick, Modifier.fillMaxWidth()) { WrappedLabel(choice) }
                    }
                    OutlinedTextField(
                        answers[q.qid].orEmpty().takeIf { it !in q.choices }.orEmpty(),
                        { answers = answers + (q.qid to it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Your answer") },
                    )
                }
            }
            val ready = prompt.questions.all { !answers[it.qid].isNullOrBlank() }
            Button(onClick = { if (guard.allows()) onAnswer(answers.mapValues { it.value.trim() }) }, enabled = ready && enabled) { Text("Answer") }
        }
    }
}
