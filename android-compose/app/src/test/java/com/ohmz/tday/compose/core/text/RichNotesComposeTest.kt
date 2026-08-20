package com.ohmz.tday.compose.core.text

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichNotesComposeTest {

    @Test
    fun `decoding rich notes produces bold, italic, underline and strike spans`() {
        val encoded = encodeNotes(
            "<p>Grocery <b>list</b> for <i>today</i>: <u>bread</u> and <s>eggs</s></p>",
        )
        val annotated = decodeNotesToAnnotatedString(encoded)

        assertEquals("Grocery list for today: bread and eggs", annotated.text)
        assertTrue(annotated.spanStyles.any { it.item.fontWeight == FontWeight.Black })
        assertTrue(annotated.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(annotated.spanStyles.any { it.item.textDecoration == TextDecoration.Underline })
        assertTrue(annotated.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough })
    }

    @Test
    fun `list items decode as plain bullet-prefixed lines, not spans`() {
        val encoded = encodeNotes("<p>Todo:</p><ul><li>Milk</li><li>Eggs</li></ul>")
        val annotated = decodeNotesToAnnotatedString(encoded)
        assertEquals("Todo:\n• Milk\n• Eggs", annotated.text)
    }

    @Test
    fun `encoding a plain unstyled AnnotatedString produces a marker-free string`() {
        val plain = buildAnnotatedString { append("Just typing\nsome lines") }
        val encoded = encodeAnnotatedNotes(plain)
        assertFalse(isRichNotes(encoded))
        assertEquals("Just typing\nsome lines", encoded)
    }

    @Test
    fun `encoding a bold span round-trips through the marker+HTML form`() {
        val styled = buildAnnotatedString {
            append("Buy ")
            withStyle(SpanStyle(fontWeight = FontWeight.Black)) { append("milk") }
            append(" today")
        }
        val encoded = encodeAnnotatedNotes(styled)
        assertTrue(isRichNotes(encoded))
        assertTrue(encoded.contains("<b>milk</b>"))
        assertEquals("Buy milk today", flattenNotesToPlainText(encoded))

        // And it decodes back to an AnnotatedString with the same bold span.
        val redecoded = decodeNotesToAnnotatedString(encoded)
        assertEquals("Buy milk today", redecoded.text)
        assertTrue(redecoded.spanStyles.any { it.item.fontWeight == FontWeight.Black })
    }

    @Test
    fun `overlapping bold and italic both survive encoding`() {
        val styled = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Black)) {
                append("bold ")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("bold-italic") }
            }
        }
        val encoded = encodeAnnotatedNotes(styled)
        assertTrue(encoded.contains("<i>bold-italic</i>") || encoded.contains("<b><i>bold-italic</i></b>"))
        assertTrue(encoded.contains("bold-italic"))
    }
}
