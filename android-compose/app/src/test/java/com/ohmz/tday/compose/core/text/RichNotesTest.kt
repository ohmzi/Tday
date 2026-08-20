package com.ohmz.tday.compose.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichNotesTest {

    @Test
    fun `plain single-line text encodes with no marker`() {
        assertEquals("Buy milk", encodeNotes("<p>Buy milk</p>"))
    }

    @Test
    fun `plain multi-line text encodes as newline-joined string with no marker`() {
        val encoded = encodeNotes("<p>Line one</p><p>Line two</p>")
        assertFalse(isRichNotes(encoded))
        assertEquals("Line one\nLine two", encoded)
    }

    @Test
    fun `pasted formatting is retained but font-size, color and font-family are stripped`() {
        val pasted = """
            <div style="font-size:32px;color:red;font-family:Comic Sans MS">
              <p>Grocery <b>list</b> for <i>today</i>:</p>
              <ul><li>Milk</li><li>Eggs</li></ul>
              <p>Don't forget: <u>bread</u> and <s>eggs</s> cheese.</p>
            </div>
        """.trimIndent()

        val encoded = encodeNotes(pasted)

        assertTrue(isRichNotes(encoded))
        assertFalse("font-size must never survive", encoded.contains("font-size"))
        assertFalse("color must never survive", encoded.contains("color"))
        assertFalse("font-family must never survive", encoded.contains("Comic Sans"))
        assertTrue(encoded.contains("<b>list</b>") || encoded.contains("<strong>list</strong>"))
        assertTrue(encoded.contains("<ul>"))
        assertTrue(encoded.contains("<u>bread</u>"))
        assertTrue(encoded.contains("<s>eggs</s>"))
    }

    @Test
    fun `script and style tags are dropped along with their contents`() {
        val encoded = encodeNotes("<p>Hello<script>alert('x')</script><style>body{color:red}</style> world</p>")
        assertFalse(encoded.contains("alert"))
        assertFalse(encoded.contains("color:red"))
        assertTrue(encoded.contains("Hello"))
        assertTrue(encoded.contains("world"))
    }

    @Test
    fun `disallowed wrapper tags are unwrapped but their text is kept`() {
        val encoded = encodeNotes("<p><span style=\"font-weight:900\">Hi</span> <a href=\"https://evil.example\">there</a></p>")
        assertFalse(encoded.contains("<span"))
        assertFalse(encoded.contains("<a "))
        assertFalse(encoded.contains("evil.example"))
        assertTrue(encoded.contains("Hi"))
        assertTrue(encoded.contains("there"))
    }

    @Test
    fun `flatten renders bullet and numbered list markers as plain text prefixes`() {
        val encoded = encodeNotes("<p>Todo:</p><ul><li>Milk</li><li>Eggs</li></ul>")
        assertEquals("Todo:\n• Milk\n• Eggs", flattenNotesToPlainText(encoded))

        val orderedEncoded = encodeNotes("<ol><li>First</li><li>Second</li></ol>")
        assertEquals("1. First\n2. Second", flattenNotesToPlainText(orderedEncoded))
    }

    @Test
    fun `legacy plain text with literal angle brackets round-trips untouched`() {
        val legacy = "email me at <foo@bar.com> if x < 5"
        assertFalse(isRichNotes(legacy))
        assertEquals(legacy, flattenNotesToPlainText(legacy))
        // Decoding a legacy note must escape it, never parse it as markup.
        assertTrue(decodeNotesToHtml(legacy).contains("&lt;foo@bar.com&gt;"))
    }

    @Test
    fun `decode then re-encode of a legacy plain note is a no-op`() {
        val legacy = "Just a plain note\nwith two lines"
        val html = decodeNotesToHtml(legacy)
        assertEquals(legacy, encodeNotes(html))
    }

    @Test
    fun `empty and null notes flatten to empty string`() {
        assertEquals("", flattenNotesToPlainText(null))
        assertEquals("", flattenNotesToPlainText(""))
    }
}
