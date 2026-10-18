package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.lastnyx.hermesclient.data.gateway.Gateway
import io.github.lastnyx.hermesclient.data.gateway.GatewayUrl
import java.net.URI

private const val CORNER_DP = 12

/** Live, non-blocking hint under the URL field; null when there is nothing to say. */
fun urlHint(url: String): String? {
    val text = url.trim()
    if (text.isEmpty()) return null
    GatewayUrl.normalise(text)?.let { normal ->
        val uri = URI(normal)
        val host = uri.host.removePrefix("[").removeSuffix("]")
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        return "Connects to $host$port"
    }
    val looksLikeHost = !text.contains("://") && Regex("""[^\s/:]+(:\d+)?(/\S*)?""").matches(text)
    return if (looksLikeHost) "Add http:// or https://" else "That doesn't look like an address"
}

/** [onSubmit] returns an error message to show, or null when it saved. */
@Composable
fun GatewayForm(initial: Gateway?, onSubmit: (String, String) -> String?, onCancel: () -> Unit) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember(initial) { mutableStateOf(initial?.url.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val shape = RoundedCornerShape(CORNER_DP.dp)
    val canSave = name.isNotBlank() && url.isNotBlank()
    fun save() { if (canSave) error = onSubmit(name, url) }

    Column(
        Modifier.imePadding().verticalScroll(rememberScrollState()).padding(SCREEN_EDGE.dp),
        verticalArrangement = Arrangement.spacedBy(ROW_PADDING.dp),
    ) {
        Text(if (initial == null) "Add gateway" else "Edit gateway", style = roles.title)
        Text(
            "The address of your `hermes serve`, for example http://100.64.0.1:9119",
            style = roles.secondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            name, { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name") },
            placeholder = { Text("Home PC") },
            leadingIcon = { Icon(Icons.Filled.Dns, contentDescription = null) },
            trailingIcon = { if (name.isNotEmpty()) ClearButton { name = "" } },
            singleLine = true,
            shape = shape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
        )
        OutlinedTextField(
            url, { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("URL") },
            placeholder = { Text("http://host:9119") },
            leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
            trailingIcon = { if (url.isNotEmpty()) ClearButton { url = "" } },
            supportingText = urlHint(url)?.let { hint -> { Text(hint) } },
            singleLine = true,
            shape = shape,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
        if (GatewayUrl.warnsCleartext(url)) {
            Banner("This address uses http://, so your credentials would cross the network unencrypted.", warning = true)
        }
        error?.let { Banner(it, warning = false) }
        Button(onClick = { save() }, enabled = canSave, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun ClearButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
}

@Composable
private fun Banner(text: String, warning: Boolean) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CORNER_DP.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(Modifier.padding(ROW_PADDING.dp), horizontalArrangement = Arrangement.spacedBy(ROW_PADDING.dp), verticalAlignment = Alignment.CenterVertically) {
            if (warning) Icon(Icons.Filled.Warning, contentDescription = null)
            Text(text, style = roles.body)
        }
    }
}
