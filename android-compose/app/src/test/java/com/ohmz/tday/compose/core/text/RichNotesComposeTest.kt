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
    fun `stripToPlainText removes list prefixes entirely, unlike the decoded text`() {
        // "Clear formatting" should remove bullets/numbers, not just the
        // marks around them — different from decodeNotesToAnnotatedString's
        // preview-oriented "keep the • as text" behavior.
        val encoded = encodeNotes("<p>Todo:</p><ul><li>Milk</li><li>Eggs</li></ul>")
        val annotated = decodeNotesToAnnotatedString(encoded)
        assertEquals("Todo:\nMilk\nEggs", stripToPlainText(annotated))
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

    // MARK: - Pending marks ("format what I type next")

    @Test
    fun `typedRunRange locates a single keystroke at the caret`() {
        assertEquals(TextRange(2, 3), typedRunRange("ab", TextRange(2, 2), "abc"))
        // Ambiguous by prefix-diff alone: typing 'b' at index 1 of "ab" looks
        // identical to typing it at index 2 unless the caret anchors it.
        assertEquals(TextRange(1, 2), typedRunRange("ab", TextRange(1, 1), "abb"))
    }

    @Test
    fun `typedRunRange returns null for anything that is not a plain insertion`() {
        assertEquals(null, typedRunRange("abc", TextRange(3, 3), "ab")) // deletion
        assertEquals(null, typedRunRange("abc", TextRange(0, 3), "x")) // replaced a selection
        assertEquals(null, typedRunRange("abc", TextRange(3, 3), "abc")) // no change
    }

    @Test
    fun `typing with a pending mark bolds only the newly typed characters`() {
        val base = AnnotatedString("hi ")
        val typed = typedRunRange("hi ", TextRange(3, 3), "hi x")!!
        val result = applyingMarks(setOf(RichNotesMark.BOLD), AnnotatedString("hi x"), typed)

        assertEquals("hi x", result.text)
        assertTrue(isMarkActive(RichNotesMark.BOLD, result, TextRange(3, 4)))
        assertFalse(isMarkActive(RichNotesMark.BOLD, result, TextRange(0, 3)))
        assertEquals(base.text, "hi ")
    }

    @Test
    fun `pending marks stamped across several keystrokes merge into one span`() {
        var text = AnnotatedString("")
        val marks = setOf(RichNotesMark.BOLD)
        for (ch in "abc") {
            val previous = text.text
            val next = previous + ch
            val range = typedRunRange(previous, TextRange(previous.length, previous.length), next)!!
            text = applyingMarks(marks, AnnotatedString(next).let { plain ->
                buildAnnotatedString {
                    append(plain.text)
                    for (span in text.spanStyles) addStyle(span.item, span.start, span.end)
                }
            }, range)
        }

        assertEquals("abc", text.text)
        assertTrue(isMarkActive(RichNotesMark.BOLD, text, TextRange(0, 3)))
        // unionRange merges touching ranges, so three keystrokes leave one span.
        assertEquals(1, text.spanStyles.count { it.item.fontWeight == FontWeight.Black })
    }

    @Test
    fun `applyingMarks with no armed marks leaves the text untouched`() {
        val plain = AnnotatedString("nothing armed")
        val result = applyingMarks(emptySet(), plain, TextRange(0, 7))
        assertEquals(plain.text, result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `togglingList works from a collapsed caret and leaves it after the bullet`() {
        val base = AnnotatedString("milk")
        val (result, selection) = togglingList(RichNotesListKind.BULLET, base, TextRange(0, 0))

        assertEquals("• milk", result.text)
        // Caret was at the line start; the prefix went in ahead of it, so it
        // should now sit after the bullet rather than stranded before it.
        assertEquals(TextRange(2, 2), selection)
        assertTrue(isListActive(RichNotesListKind.BULLET, result, TextRange(2, 2)))
    }

    // MARK: - Enter continues a list

    @Test
    fun `enter at the end of a bullet item starts the next one`() {
        val (listed, _) = togglingList(RichNotesListKind.BULLET, AnnotatedString("milk"), TextRange(0, 4))
        assertEquals("• milk", listed.text)

        val (result, selection) = continuingListOnNewline(listed, listed.text.length)!!
        assertEquals("• milk\n• ", result.text)
        // Caret sits after the new bullet, ready to type.
        assertEquals(TextRange(9, 9), selection)
        assertTrue(isListActive(RichNotesListKind.BULLET, result, TextRange(9, 9)))
    }

    @Test
    fun `enter in the middle of an ordered list renumbers everything after it`() {
        val (listed, _) = togglingList(RichNotesListKind.ORDERED, AnnotatedString("a\nb\nc"), TextRange(0, 5))
        assertEquals("1. a\n2. b\n3. c", listed.text)

        // Enter at the end of item 1 — the old items 2 and 3 must become 3 and 4.
        val (result, _) = continuingListOnNewline(listed, 4)!!
        assertEquals("1. a\n2. \n3. b\n4. c", result.text)
    }

    @Test
    fun `enter on an item that holds only its prefix ends the list`() {
        val (listed, _) = togglingList(RichNotesListKind.BULLET, AnnotatedString("milk"), TextRange(0, 4))
        val (withEmpty, _) = continuingListOnNewline(listed, listed.text.length)!!
        assertEquals("• milk\n• ", withEmpty.text)

        // Enter again on the empty item: the only way out of a list.
        val (ended, selection) = continuingListOnNewline(withEmpty, withEmpty.text.length)!!
        assertEquals("• milk\n", ended.text)
        assertEquals(TextRange(7, 7), selection)
        assertFalse(isListActive(RichNotesListKind.BULLET, ended, TextRange(7, 7)))
    }

    @Test
    fun `enter on a plain line is left alone for a normal newline`() {
        assertEquals(null, continuingListOnNewline(AnnotatedString("just text"), 9))
    }

    @Test
    fun `renumberOrderedLists is idempotent on already-correct numbering`() {
        val (listed, _) = togglingList(RichNotesListKind.ORDERED, AnnotatedString("a\nb"), TextRange(0, 3))
        assertEquals(listed.text, renumberOrderedLists(listed).text)
    }

    @Test
    fun `enter-continued list still encodes as a real ol`() {
        val (listed, _) = togglingList(RichNotesListKind.ORDERED, AnnotatedString("a"), TextRange(0, 1))
        val (continued, _) = continuingListOnNewline(listed, listed.text.length)!!
        // The second item is empty, but the structure must survive encoding.
        assertEquals(
            RICH_NOTES_MARKER + "<ol><li><p>a</p></li><li><p></p></li></ol>",
            encodeAnnotatedNotes(continued),
        )
    }

    @Test
    fun `isListActive answers for a collapsed caret on a tagged line`() {
        val base = AnnotatedString("a\nb")
        val (listed, _) = togglingList(RichNotesListKind.BULLET, base, TextRange(0, base.text.length))
        // Caret parked inside the first bulleted line, nothing selected.
        assertTrue(isListActive(RichNotesListKind.BULLET, listed, TextRange(3, 3)))
        assertFalse(isListActive(RichNotesListKind.ORDERED, listed, TextRange(3, 3)))
    }
}
