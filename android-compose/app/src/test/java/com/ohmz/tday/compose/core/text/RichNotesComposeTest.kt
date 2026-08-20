package com.ohmz.tday.compose.core.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
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

    // MARK: - Manual formatting (isMarkActive / togglingMark / togglingList)

    @Test
    fun `isMarkActive is true only when the whole selection has the mark`() {
        val plain = AnnotatedString("hello")
        val bolded = togglingMark(RichNotesMark.BOLD, plain, TextRange(0, 3))

        assertTrue(isMarkActive(RichNotesMark.BOLD, bolded, TextRange(0, 3)))
        assertFalse(isMarkActive(RichNotesMark.BOLD, bolded, TextRange(3, 5)))
        assertFalse(isMarkActive(RichNotesMark.BOLD, bolded, TextRange(0, 5)))
    }

    @Test
    fun `togglingMark applies then removes each mark without changing the text`() {
        val base = AnnotatedString("hello")
        val range = TextRange(0, 5)

        for (mark in RichNotesMark.entries) {
            assertFalse(isMarkActive(mark, base, range))
            val applied = togglingMark(mark, base, range)
            assertTrue(isMarkActive(mark, applied, range))
            assertEquals("hello", applied.text)
            val removed = togglingMark(mark, applied, range)
            assertFalse(isMarkActive(mark, removed, range))
        }
    }

    @Test
    fun `togglingMark on a mixed selection applies rather than removes`() {
        val base = AnnotatedString("helloworld")
        val halfBold = togglingMark(RichNotesMark.BOLD, base, TextRange(0, 5))
        val full = TextRange(0, 10)

        assertFalse(isMarkActive(RichNotesMark.BOLD, halfBold, full))
        val fullyBold = togglingMark(RichNotesMark.BOLD, halfBold, full)
        assertTrue(isMarkActive(RichNotesMark.BOLD, fullyBold, full))
    }

    @Test
    fun `togglingMark on a collapsed selection is a no-op`() {
        val base = AnnotatedString("hello")
        val result = togglingMark(RichNotesMark.BOLD, base, TextRange(2, 2))
        assertEquals(base.text, result.text)
        assertFalse(isMarkActive(RichNotesMark.BOLD, result, TextRange(0, 5)))
    }

    @Test
    fun `togglingList inserts a bullet prefix on both lines and tags them`() {
        val base = AnnotatedString("first\nsecond")
        val full = TextRange(0, base.text.length)

        assertFalse(isListActive(RichNotesListKind.BULLET, base, full))
        val (result, selection) = togglingList(RichNotesListKind.BULLET, base, full)
        assertEquals("• first\n• second", result.text)
        assertEquals(TextRange(0, result.text.length), selection)
        assertTrue(isListActive(RichNotesListKind.BULLET, result, TextRange(0, result.text.length)))
    }

    @Test
    fun `togglingList renumbers ordered items sequentially`() {
        val base = AnnotatedString("a\nb\nc")
        val (result, _) = togglingList(RichNotesListKind.ORDERED, base, TextRange(0, base.text.length))
        assertEquals("1. a\n2. b\n3. c", result.text)
    }

    @Test
    fun `togglingList off removes the prefix and tag when already active`() {
        val base = AnnotatedString("first\nsecond")
        val full = TextRange(0, base.text.length)
        val (bulleted, bulletedSelection) = togglingList(RichNotesListKind.BULLET, base, full)
        val (removed, _) = togglingList(RichNotesListKind.BULLET, bulleted, bulletedSelection)
        assertEquals("first\nsecond", removed.text)
        assertFalse(isListActive(RichNotesListKind.BULLET, removed, TextRange(0, removed.text.length)))
    }

    @Test
    fun `togglingList converts bullet to ordered rather than stacking prefixes`() {
        val base = AnnotatedString("a\nb")
        val (bulleted, bulletedSelection) = togglingList(RichNotesListKind.BULLET, base, TextRange(0, base.text.length))
        val (ordered, orderedSelection) = togglingList(RichNotesListKind.ORDERED, bulleted, bulletedSelection)
        assertEquals("1. a\n2. b", ordered.text)
        // Regression guard: subSequence carries over a *clipped* copy of the
        // old BULLET annotation into the extracted "just the content"
        // fragment even after its prefix is stripped, so without explicitly
        // dropping it, this line would end up double-tagged (BULLET from
        // the leak, ORDERED from the explicit re-add) with whichever one
        // getStringAnnotations happens to return first winning arbitrarily.
        assertFalse(isListActive(RichNotesListKind.BULLET, ordered, orderedSelection))
        assertTrue(isListActive(RichNotesListKind.ORDERED, ordered, orderedSelection))
    }

    @Test
    fun `togglingList on a selection starting mid-line still touches that whole line`() {
        // Regression coverage: the "touched" line set must be computed
        // against whole-line ranges, not ranges clipped to the selection —
        // otherwise a selection that starts/ends mid-line never matches by
        // value against the edit loop's own line list and silently no-ops.
        val base = AnnotatedString("hello world\nfoo")
        val midLineSelection = TextRange(6, base.text.length) // starts inside "world"
        val (result, selection) = togglingList(RichNotesListKind.BULLET, base, midLineSelection)
        assertEquals("• hello world\n• foo", result.text)
        // The 'w' of "world" was at index 6; both lines gained a 2-char
        // prefix before it, so it should now sit at index 8.
        assertEquals(8, selection.start)
        assertEquals(result.text.length, selection.end)
    }

    // MARK: - encodeAnnotatedNotes — list promotion (the round-trip regression)

    @Test
    fun `encodeAnnotatedNotes promotes tagged lines back into a real list`() {
        // This is the fix for the pre-existing bug: decode used to expand
        // <ul><li> into plain "• "-prefixed text, but encode only ever
        // emitted <p> — so editing a single character anywhere in a note
        // that contained a list silently destroyed the list on save.
        val stored = encodeNotes("<ul><li>a</li><li>b</li></ul>")
        val decoded = decodeNotesToAnnotatedString(stored)
        val reEncoded = encodeAnnotatedNotes(decoded)
        // The inner <p> is intentional — it matches exactly what web's
        // Tiptap listItem emits, so edits from either platform converge on
        // the same bytes instead of churning back and forth.
        assertEquals(RICH_NOTES_MARKER + "<ul><li><p>a</p></li><li><p>b</p></li></ul>", reEncoded)
    }

    @Test
    fun `encodeAnnotatedNotes round trip is stable on a second pass`() {
        val once = encodeAnnotatedNotes(decodeNotesToAnnotatedString(encodeNotes("<ol><li>a</li><li>b</li></ol>")))
        val twice = encodeAnnotatedNotes(decodeNotesToAnnotatedString(once))
        assertEquals(once, twice)
    }

    @Test
    fun `encodeAnnotatedNotes encodes a manually applied bold mark`() {
        val base = AnnotatedString("say hi")
        val bolded = togglingMark(RichNotesMark.BOLD, base, TextRange(4, 6))
        val encoded = encodeAnnotatedNotes(bolded)
        assertEquals(RICH_NOTES_MARKER + "<p>say <b>hi</b></p>", encoded)
    }

    @Test
    fun `encodeAnnotatedNotes encodes a manually applied list`() {
        val base = AnnotatedString("eggs\nbread")
        val (listed, _) = togglingList(RichNotesListKind.BULLET, base, TextRange(0, base.text.length))
        val encoded = encodeAnnotatedNotes(listed)
        assertEquals(RICH_NOTES_MARKER + "<ul><li><p>eggs</p></li><li><p>bread</p></li></ul>", encoded)
    }

    @Test
    fun `encodeAnnotatedNotes never promotes untagged literal bullet text`() {
        // Invariant this whole design protects: a plain note that happens to
        // contain a literal "• " line (typed by hand, or from data written
        // before this feature existed) must never be promoted into a rich
        // list just because the text looks like one — only the tracked
        // annotation (set exclusively by decode-from-real-<ul> and the list
        // toggle) counts.
        val plain = "• just a bullet character, never tagged"
        val decoded = decodeNotesToAnnotatedString(plain)
        val reEncoded = encodeAnnotatedNotes(decoded)
        assertEquals(plain, reEncoded)
        assertFalse(isRichNotes(reEncoded))
    }
}
