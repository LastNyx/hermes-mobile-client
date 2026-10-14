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
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding

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
    val typography = markdownTypography(
        text = roles.body,
        code = roles.code,
        inlineCode = roles.code,
        h1 = roles.title,
        h2 = roles.title,
        h3 = roles.title,
        h4 = roles.label,
        h5 = roles.label,
        h6 = roles.label,
    )
    CompositionLocalProvider(LocalUriHandler provides handler) {
        Markdown(
            content = text,
            typography = typography,
            components = components,
            padding = markdownPadding(block = 0.dp, list = 2.dp, listItemTop = 2.dp, listItemBottom = 2.dp),
            dimens = markdownDimens(tableCellPadding = 8.dp),
            modifier = modifier,
        )
    }
}
