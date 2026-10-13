package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.m3.Markdown

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val outer = LocalUriHandler.current
    val handler = remember(outer) {
        object : UriHandler {
            override fun openUri(uri: String) {
                if (isAllowedLink(uri)) runCatching { outer.openUri(uri) }
            }
        }
    }
    val components = markdownComponents(
        codeFence = { m ->
            MarkdownCodeFence(m.content, m.node, m.typography.code) { code, language, style -> CodeCard(code, language, style) }
        },
        codeBlock = { m ->
            MarkdownCodeBlock(m.content, m.node, m.typography.code) { code, _, style -> CodeCard(code, null, style) }
        },
        table = { m ->
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                MarkdownTable(m.content, m.node, m.typography.table)
            }
        },
    )
    CompositionLocalProvider(LocalUriHandler provides handler) {
        Markdown(content = text, components = components, modifier = modifier)
    }
}
