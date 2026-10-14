package io.github.lastnyx.hermesclient.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionPreviewTest {
    @Test fun fileToken() = assertEquals("look at this", cleanPreview("look at @file:`/home/me/a.kt` this"))

    @Test fun urlToken() = assertEquals("read", cleanPreview("@url:`https://x.dev/a` read"))

    @Test fun bareUrlBecomesHost() = assertEquals("see github.com now", cleanPreview("see https://github.com/foo/bar?x=1 now"))

    @Test fun mixedKeepsWords() =
        assertEquals("fix github.com bug", cleanPreview("fix @image:`/tmp/p.png` https://github.com/a/b bug"))

    @Test fun newlinesCollapse() = assertEquals("a b c", cleanPreview("a\n\n b\t\nc  "))

    @Test fun onlyTokensIsEmpty() = assertEquals("", cleanPreview("@file:`/home/x` @url:`https://y.z`"))

    @Test fun slashCommandKept() = assertEquals("/status", cleanPreview("/status"))

    @Test fun headerTitle() = assertEquals("Today · 6", groupHeaderTitle("Today", 6))
}
