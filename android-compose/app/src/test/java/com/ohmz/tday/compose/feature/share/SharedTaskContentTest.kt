package com.ohmz.tday.compose.feature.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedTaskContentTest {

    @Test
    fun `browser share keeps page title as task title and link as notes`() {
        val content = sharedTaskContent(
            subject = "How to grow tomatoes",
            text = "https://example.com/tomatoes",
        )
        assertEquals("How to grow tomatoes", content?.title)
        assertEquals("https://example.com/tomatoes", content?.notes)
    }

    @Test
    fun `bare text share becomes the title with no notes`() {
        val content = sharedTaskContent(subject = null, text = "Buy milk tomorrow 9am")
        assertEquals("Buy milk tomorrow 9am", content?.title)
        assertNull(content?.notes)
    }

    @Test
    fun `subject-only share becomes the title`() {
        val content = sharedTaskContent(subject = "Call the plumber", text = "  ")
        assertEquals("Call the plumber", content?.title)
        assertNull(content?.notes)
    }

    @Test
    fun `identical subject and text collapse to a single title`() {
        val content = sharedTaskContent(subject = "Ping Alex", text = "Ping Alex")
        assertEquals("Ping Alex", content?.title)
        assertNull(content?.notes)
    }

    @Test
    fun `surrounding whitespace is trimmed before comparison`() {
        val content = sharedTaskContent(subject = " Ping Alex ", text = "Ping Alex\n")
        assertEquals("Ping Alex", content?.title)
        assertNull(content?.notes)
    }

    @Test
    fun `empty share is rejected`() {
        assertNull(sharedTaskContent(subject = "", text = "   "))
        assertNull(sharedTaskContent(subject = null, text = null))
    }
}
