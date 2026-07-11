package com.eastweblite.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserUrlTest {
    @Test
    fun `plain domains are upgraded to https`() {
        assertEquals("https://example.com", formatBrowserUrl("example.com", "https://duckduckgo.com/?q=%s"))
    }

    @Test
    fun `http input is upgraded to https`() {
        assertEquals("https://example.com/path", formatBrowserUrl("http://example.com/path", "https://duckduckgo.com/?q=%s"))
    }

    @Test
    fun `dangerous schemes become safe search queries`() {
        val result = formatBrowserUrl("javascript:alert(1)", "https://duckduckgo.com/?q=%s")
        assertTrue(result.startsWith("https://duckduckgo.com/?q="))
    }

    @Test
    fun `unsafe search pattern falls back to duckduckgo`() {
        val result = formatBrowserUrl("hello world", "http://unsafe.test/?q=%s")
        assertTrue(result.startsWith("https://duckduckgo.com/?q="))
    }

    @Test
    fun `internal lite urls pass through`() {
        assertEquals("lite://settings", formatBrowserUrl("lite://settings", "https://duckduckgo.com/?q=%s"))
    }
}
