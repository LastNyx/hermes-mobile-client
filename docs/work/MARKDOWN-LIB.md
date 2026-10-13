# Markdown library reference (for workers; read-only facts)

Library: `com.mikepenz:multiplatform-markdown-renderer-m3` **0.33.0** (+ its core `multiplatform-markdown-renderer`),
already in `libs.versions.toml` as `libs.markdown.renderer.m3` and in `app/build.gradle.kts`. Read from the jar
with `javap` on 2026-10-13. Do not guess a parameter that is not listed here: FAIL with the reason instead.
Do not add other libraries (no syntax highlighter, no TeX, no image loader): `needs-dependency: <coordinates>`.

## Entry point (package `com.mikepenz.markdown.m3`)

`Markdown(content: String, colors, typography, modifier, padding, dimens, flavour, parser, imageTransformer,
annotator, extendedSpans, inlineContent, components, animations, referenceLinkHandler, loading, success, error)`.
The compiled signature has default-argument masks, and the m3 variant is meant to read colours and text styles from
`MaterialTheme`; the compiler is the check, so if a call does not compile, FAIL with the reason.
Pass only `content`, `modifier`, and what a ticket needs (`components`, `typography`, `colors`).

- `markdownColor(...)` and `markdownTypography(...)` (same package) build `MarkdownColors` / `MarkdownTypography`;
  all arguments default from `MaterialTheme`. Call them with named arguments only for what you override.
- `markdownComponents(...)` (package `com.mikepenz.markdown.compose.components`) builds `MarkdownComponents`; each slot is a
  `@Composable (MarkdownComponentModel) -> Unit`. Slots include `codeFence`, `codeBlock`, `table`, `paragraph`,
  `heading1..6`, `blockQuote`, `orderedList`, `unorderedList`, `horizontalRule`, `image`, `checkbox`, `text`.
  Override only the slot you need; the rest keep their defaults.
- `MarkdownComponentModel` has `content: String`, `node: ASTNode`, `typography: MarkdownTypography`, `extra`.

## Code fences

`MarkdownCodeFence(content: String, node: ASTNode, style: TextStyle, block: @Composable (code: String, language: String?, style: TextStyle) -> Unit)`
(package `com.mikepenz.markdown.compose.elements`) parses the fence and hands you the code and language, so a custom
`codeFence` slot is: `codeFence = { m -> MarkdownCodeFence(m.content, m.node, m.typography.code) { code, language, style -> ... } }`.
`MarkdownCodeBlock` has the same shape for indented (not fenced) code.

## Links

The library opens a link through `LocalUriHandler`. Provide your own `UriHandler` with `CompositionLocalProvider`
around `Markdown(...)` to restrict schemes.

## Not available in 0.33.0 / not wanted now

Syntax highlighting, TeX math, loading web images. A Markdown image shows as plain text (no `ImageTransformer`).

## Why 0.33.0 (do not raise it)

The app runs Compose 1.7.6 (BOM 2024.12.01). 0.37.0 and newer call `Composer.shouldExecute`, 0.34.0 and 0.35.0 call a
`BasicText` overload with `TextAutoSize`; neither exists in 1.7.6, so the chat crashed at runtime (`NoSuchMethodError`).
Unit tests and `assembleDebug` do not see this. Raising the version needs a Compose BOM bump first, decided by the planner.
Check any change with `python3 -I <scratch>/tools/linkcheck.py app-debug.apk` (planner tool) and a run on a device.
