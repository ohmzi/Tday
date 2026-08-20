package com.ohmz.tday.compose.core.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

// Compose-side bridge for the notes rich-text encoding (see RichNotes.kt).
// There is no dedicated Compose rich-text-editor dependency here — Compose
// Foundation's BasicTextField already supports a styled AnnotatedString as
// its live, editable value, so bold/italic/underline/strikethrough round-trip
// as real character spans. List structure from a paste is deliberately
// downgraded at paste time into plain "• "/"1. "-prefixed text lines (the
// same convention flattenNotesToPlainText/htmlToPlainText already use for
// previews) rather than modeled as an editable semantic list — reliably
// reconstructing <ul><li> through arbitrary further user edits needs a real
// rich-text-editor engine, which added more dependency/version risk than it
// was worth for a paste-retention feature with no manual list-editing UI.
//
// Manual formatting (NotesField's format bar) writes the exact same spans
// paste-retention already uses — there is no separate code path, so a
// manually-bolded run and a paste-bolded run are indistinguishable and both
// round-trip identically.

private val BOLD_STYLE = SpanStyle(fontWeight = FontWeight.Black)
private val ITALIC_STYLE = SpanStyle(fontStyle = FontStyle.Italic)
private val UNDERLINE_STYLE = SpanStyle(textDecoration = TextDecoration.Underline)
private val STRIKE_STYLE = SpanStyle(textDecoration = TextDecoration.LineThrough)

enum class RichNotesMark { BOLD, ITALIC, UNDERLINE, STRIKETHROUGH }

enum class RichNotesListKind { BULLET, ORDERED }

// String-annotation tag marking which character run came from a "<li>" (or
// the manual list-toggle button) so encode can promote a run of same-kind
// tagged lines back into a real <ul>/<ol> — never serialized itself (lives
// only in the live AnnotatedString), so it can't leak into the saved string
// or affect plain notes that merely contain literal "• " text.
private const val LIST_ANNOTATION_TAG = "tdayList"

private fun AnnotatedString.Builder.appendInline(node: Node) {
    for (child in node.childNodes()) {
        when (child) {
            is TextNode -> append(child.text())
            is Element -> {
                when (child.tagName().lowercase()) {
                    "br" -> append("\n")
                    "b", "strong" -> withStyle(BOLD_STYLE) { appendInline(child) }
                    "i", "em" -> withStyle(ITALIC_STYLE) { appendInline(child) }
                    "u" -> withStyle(UNDERLINE_STYLE) { appendInline(child) }
                    "s", "strike" -> withStyle(STRIKE_STYLE) { appendInline(child) }
                    else -> appendInline(child)
                }
            }
        }
    }
}

private fun AnnotatedString.Builder.appendBlocks(container: Node) {
    for (child in container.childNodes()) {
        if (child !is Element) continue
        when (child.tagName().lowercase()) {
            "p" -> {
                appendInline(child)
                append("\n")
            }
            "ul", "ol" -> {
                val ordered = child.tagName().equals("ol", ignoreCase = true)
                val kind = if (ordered) RichNotesListKind.ORDERED else RichNotesListKind.BULLET
                var counter = 0
                for (li in child.children()) {
                    if (!li.tagName().equals("li", ignoreCase = true)) continue
                    counter += 1
                    val lineStart = length
                    append(if (ordered) "$counter. " else "• ")
                    appendInline(li)
                    addStringAnnotation(LIST_ANNOTATION_TAG, kind.name, lineStart, length)
                    append("\n")
                }
            }
            "br" -> append("\n")
            else -> appendBlocks(child)
        }
    }
}

// Saved string → editable rich-text state for the field.
fun decodeNotesToAnnotatedString(value: String?): AnnotatedString {
    if (value.isNullOrEmpty()) return AnnotatedString("")
    if (!isRichNotes(value)) return AnnotatedString(value)
    val sanitized = sanitizeHtml(value.removePrefix(RICH_NOTES_MARKER))
    val built = buildAnnotatedString { appendBlocks(Jsoup.parseBodyFragment(sanitized).body()) }
    return if (built.text.endsWith("\n")) built.subSequence(0, built.text.length - 1) else built
}

// MARK: - Manual formatting (NotesField's format bar)

private fun markMatches(mark: RichNotesMark, style: SpanStyle): Boolean = when (mark) {
    RichNotesMark.BOLD -> style.fontWeight == FontWeight.Black
    RichNotesMark.ITALIC -> style.fontStyle == FontStyle.Italic
    RichNotesMark.UNDERLINE -> style.textDecoration == TextDecoration.Underline
    RichNotesMark.STRIKETHROUGH -> style.textDecoration == TextDecoration.LineThrough
}

private fun markStyle(mark: RichNotesMark): SpanStyle = when (mark) {
    RichNotesMark.BOLD -> BOLD_STYLE
    RichNotesMark.ITALIC -> ITALIC_STYLE
    RichNotesMark.UNDERLINE -> UNDERLINE_STYLE
    RichNotesMark.STRIKETHROUGH -> STRIKE_STYLE
}

// A mark is "active" for a selection only if every character in it already
// has the mark — same semantics as iOS/Notes/Gmail: toggling a mixed
// selection always applies it first, a fully-marked selection removes it.
// Only meaningful for a real (non-empty) selection: Compose's BasicTextField
// has no typingAttributes hook, so a collapsed caret's "format what I type
// next" state is tracked separately by NotesField (pendingMarks) and stamped
// on via applyingMarks once characters actually arrive.
fun isMarkActive(mark: RichNotesMark, text: AnnotatedString, range: TextRange): Boolean {
    val start = range.min
    val end = range.max
    if (start >= end) return false
    val boundaries = sortedSetOf(start, end)
    for (r in text.spanStyles) {
        if (r.start in (start + 1) until end) boundaries.add(r.start)
        if (r.end in (start + 1) until end) boundaries.add(r.end)
    }
    val points = boundaries.toList()
    for (i in 0 until points.size - 1) {
        val segStart = points[i]
        val segEnd = points[i + 1]
        if (segStart >= segEnd) continue
        val active = text.spanStyles.filter { it.start <= segStart && segEnd <= it.end }
        if (active.none { markMatches(mark, it.item) }) return false
    }
    return true
}

private data class SimpleRange(val start: Int, val end: Int)

private fun subtractRange(ranges: List<SimpleRange>, remove: SimpleRange): List<SimpleRange> {
    val result = mutableListOf<SimpleRange>()
    for (r in ranges) {
        if (r.end <= remove.start || r.start >= remove.end) {
            result.add(r)
            continue
        }
        if (r.start < remove.start) result.add(SimpleRange(r.start, remove.start))
        if (r.end > remove.end) result.add(SimpleRange(remove.end, r.end))
    }
    return result
}

private fun unionRange(ranges: List<SimpleRange>, add: SimpleRange): List<SimpleRange> {
    val all = (ranges + add).sortedBy { it.start }
    val merged = mutableListOf<SimpleRange>()
    for (r in all) {
        val last = merged.lastOrNull()
        if (last != null && r.start <= last.end) {
            merged[merged.size - 1] = SimpleRange(last.start, maxOf(last.end, r.end))
        } else {
            merged.add(r)
        }
    }
    return merged.filter { it.start < it.end }
}

// Applies `mark` to the whole selection if any character lacks it, otherwise
// strips it from the whole selection — the text itself never changes length
// or content, only which spans cover it.
fun togglingMark(mark: RichNotesMark, text: AnnotatedString, range: TextRange): AnnotatedString {
    val start = range.min
    val end = range.max
    if (start >= end) return text
    val active = isMarkActive(mark, text, range)
    val existingOfMark = text.spanStyles.filter { markMatches(mark, it.item) }.map { SimpleRange(it.start, it.end) }
    val otherSpans = text.spanStyles.filter { !markMatches(mark, it.item) }
    val listAnnotations = text.getStringAnnotations(LIST_ANNOTATION_TAG, 0, text.text.length)

    val newRanges = if (active) {
        subtractRange(existingOfMark, SimpleRange(start, end))
    } else {
        unionRange(existingOfMark, SimpleRange(start, end))
    }

    return buildAnnotatedString {
        append(text.text)
        for (span in otherSpans) addStyle(span.item, span.start, span.end)
        for (r in newRanges) addStyle(markStyle(mark), r.start, r.end)
        for (ann in listAnnotations) addStringAnnotation(ann.tag, ann.item, ann.start, ann.end)
    }
}

// Applies every mark in `marks` to `range`, never removing anything — this
// stamps armed ("format what I type next") marks onto freshly typed
// characters, where togglingMark's flip semantics would be wrong.
fun applyingMarks(marks: Set<RichNotesMark>, text: AnnotatedString, range: TextRange): AnnotatedString {
    if (marks.isEmpty() || range.min >= range.max) return text
    var result = text
    for (mark in marks) {
        val add = SimpleRange(range.min, range.max)
        val existingOfMark = result.spanStyles.filter { markMatches(mark, it.item) }.map { SimpleRange(it.start, it.end) }
        val otherSpans = result.spanStyles.filter { !markMatches(mark, it.item) }
        val listAnnotations = result.getStringAnnotations(LIST_ANNOTATION_TAG, 0, result.text.length)
        val newRanges = unionRange(existingOfMark, add)
        val text2 = result
        result = buildAnnotatedString {
            append(text2.text)
            for (span in otherSpans) addStyle(span.item, span.start, span.end)
            for (r in newRanges) addStyle(markStyle(mark), r.start, r.end)
            for (ann in listAnnotations) addStringAnnotation(ann.tag, ann.item, ann.start, ann.end)
        }
    }
    return result
}

// The range of characters a plain keystroke just inserted, or null if this
// edit was anything else (a deletion, a replacement, or an insertion over a
// selection). Anchoring on the known caret rather than a prefix/suffix diff
// avoids the ambiguity of repeated characters — typing "b" at index 1 of
// "ab" is indistinguishable from typing it at index 2 by diff alone.
fun typedRunRange(previousText: String, previousSelection: TextRange, newText: String): TextRange? {
    if (!previousSelection.collapsed) return null
    if (newText.length <= previousText.length) return null
    val caret = previousSelection.start
    if (caret < 0 || caret > previousText.length) return null
    val insertedLength = newText.length - previousText.length
    if (newText.substring(0, caret) != previousText.substring(0, caret)) return null
    if (newText.substring(caret + insertedLength) != previousText.substring(caret)) return null
    return TextRange(caret, caret + insertedLength)
}

// MARK: - Manual list toggling

private fun listKindAtLineStart(text: AnnotatedString, line: SimpleRange): RichNotesListKind? {
    if (line.start >= line.end || line.start >= text.text.length) return null
    val annotations = text.getStringAnnotations(LIST_ANNOTATION_TAG, line.start, line.start + 1)
    val raw = annotations.firstOrNull()?.item ?: return null
    return runCatching { RichNotesListKind.valueOf(raw) }.getOrNull()
}

// Length of the visible "• "/"1. " prefix at the start of `line`, or 0 if
// it isn't actually there (e.g. the user deleted it but the annotation
// lingers on a stale range — treated as "no prefix to strip", not a crash).
private fun listPrefixLength(text: String, line: SimpleRange, kind: RichNotesListKind): Int {
    val lineText = text.substring(line.start, line.end)
    return when (kind) {
        RichNotesListKind.BULLET -> if (lineText.startsWith("• ")) 2 else 0
        RichNotesListKind.ORDERED -> {
            val digitCount = lineText.takeWhile { it.isDigit() }.length
            if (digitCount > 0 && lineText.drop(digitCount).startsWith(". ")) digitCount + 2 else 0
        }
    }
}

// AnnotatedString.subSequence carries over a *clipped* copy of any
// annotation that merely overlaps the sliced range — so extracting "just
// the content after the prefix" from a list-tagged line still leaves a
// remnant tdayList annotation on it (covering the content's full, now-
// prefix-free range) unless explicitly stripped here. Other spans (bold/
// italic/…) are preserved as-is; only this tag is dropped.
private fun withoutListAnnotation(text: AnnotatedString): AnnotatedString = buildAnnotatedString {
    append(text.text)
    for (span in text.spanStyles) addStyle(span.item, span.start, span.end)
}

private fun splitLines(text: String, region: SimpleRange): List<SimpleRange> {
    val lines = mutableListOf<SimpleRange>()
    var cursor = region.start
    while (true) {
        val newlineIdx = text.indexOf('\n', cursor).let { if (it == -1 || it >= region.end) -1 else it }
        val lineEnd = if (newlineIdx == -1) region.end else newlineIdx
        lines.add(SimpleRange(cursor, lineEnd))
        if (newlineIdx == -1) break
        cursor = newlineIdx + 1
    }
    return lines
}

// Every whole line (by its full-text range, never clipped to a selection)
// that `[start, end)` overlaps at all — the two callers below both need
// "touched" to mean the same thing as the main edit loop's own line list
// (`allLines` in togglingList), or a selection starting/ending mid-line
// would never match by value against that list.
private fun touchedLines(allLines: List<SimpleRange>, start: Int, end: Int): List<SimpleRange> =
    allLines.filter { it.start < end && it.end > start }

// The line a collapsed caret sits on. touchedLines' strict overlap test can't
// answer this: a zero-width caret resting exactly on a line boundary overlaps
// nothing.
private fun lineContaining(allLines: List<SimpleRange>, position: Int): SimpleRange? =
    allLines.firstOrNull { position >= it.start && position <= it.end }

// Lines a selection acts on, whether or not it's collapsed. A list is a
// line-level format, so an unselected caret still has an unambiguous target.
private fun linesForSelection(allLines: List<SimpleRange>, start: Int, end: Int): List<SimpleRange> =
    if (start >= end) listOfNotNull(lineContaining(allLines, start)) else touchedLines(allLines, start, end)

// Whether every line touched by `range` is already tagged `kind` — used to
// show the format bar's list buttons as active. Unlike isMarkActive this does
// handle a collapsed caret, since a list applies to whole lines.
fun isListActive(kind: RichNotesListKind, text: AnnotatedString, range: TextRange): Boolean {
    if (text.text.isEmpty()) return false
    val start = range.min
    val end = range.max
    val allLines = splitLines(text.text, SimpleRange(0, text.text.length))
    val lines = linesForSelection(allLines, start, end).filter { it.end > it.start }
    if (lines.isEmpty()) return false
    return lines.all { listKindAtLineStart(text, it) == kind }
}

// Toggles `kind` over every non-empty line touched by `range`. If every
// touched line already has `kind`, it's removed from all of them (prefix
// text deleted, annotation cleared); otherwise every touched line is made
// `kind` (converting from the other kind if present), with ordered prefixes
// renumbered 1..n across the touched lines. Mirrors iOS's identical toggle
// semantics in RichNotesAttributedString.swift.
fun togglingList(kind: RichNotesListKind, text: AnnotatedString, range: TextRange): Pair<AnnotatedString, TextRange> {
    val fullText = text.text
    if (fullText.isEmpty()) return text to range
    val selStart = range.min
    val selEnd = range.max
    val collapsed = selStart >= selEnd

    val allLines = splitLines(fullText, SimpleRange(0, fullText.length))
    val touched = linesForSelection(allLines, selStart, selEnd)
    val nonEmptyTouched = touched.filter { it.end > it.start }
    if (nonEmptyTouched.isEmpty()) return text to range
    val removing = nonEmptyTouched.all { listKindAtLineStart(text, it) == kind }
    val touchedSet = touched.toHashSet()

    var newSelStart = selStart
    var newSelEnd = selEnd
    var orderedIndex = 1

    val result = buildAnnotatedString {
        for ((idx, line) in allLines.withIndex()) {
            if (idx > 0) append("\n")
            val lineStartBefore = line.start

            if (line !in touchedSet || line.end <= line.start) {
                append(text.subSequence(line.start, line.end))
                continue
            }

            val existingKind = listKindAtLineStart(text, line)
            val oldPrefixLen = if (existingKind != null) listPrefixLength(fullText, line, existingKind) else 0
            val content = withoutListAnnotation(text.subSequence(line.start + oldPrefixLen, line.end))

            val delta: Int
            if (removing) {
                append(content)
                delta = -oldPrefixLen
            } else {
                val prefix = if (kind == RichNotesListKind.ORDERED) "${orderedIndex}. " else "• "
                if (kind == RichNotesListKind.ORDERED) orderedIndex += 1
                val prefixStart = length
                append(prefix)
                append(content)
                addStringAnnotation(LIST_ANNOTATION_TAG, kind.name, prefixStart, length)
                delta = prefix.length - oldPrefixLen
            }

            // Selection tracking: a line entirely before the selection start
            // shifts the whole selection window; a line inside it grows/
            // shrinks the window itself (its far edge moves, its start
            // doesn't) — matching the "selecting text and having its start
            // line gain a prefix should still leave that prefix selected"
            // expectation from a real, non-empty selection. A collapsed caret
            // also shifts when its own line gains a prefix, so it lands after
            // the new bullet rather than stranded in front of it.
            if (lineStartBefore < selStart || (collapsed && lineStartBefore == selStart)) {
                newSelStart += delta
                newSelEnd += delta
            } else if (lineStartBefore < selEnd) {
                newSelEnd += delta
            }
        }
    }

    return result to TextRange(newSelStart.coerceAtLeast(0), newSelEnd.coerceAtLeast(0))
}

// Rewrites every maximal run of consecutive ORDERED lines as 1..n. Run as a
// whole-text normalisation rather than patching only the edited line, so an
// insertion in the *middle* of a list renumbers everything after it too.
// Idempotent: already-correct numbering rewrites to itself.
fun renumberOrderedLists(text: AnnotatedString): AnnotatedString {
    val fullText = text.text
    if (fullText.isEmpty()) return text
    val allLines = splitLines(fullText, SimpleRange(0, fullText.length))
    var counter = 0
    var changed = false

    val rebuilt = buildAnnotatedString {
        for ((idx, line) in allLines.withIndex()) {
            if (idx > 0) append("\n")
            val kind = listKindAtLineStart(text, line)
            if (kind != RichNotesListKind.ORDERED) {
                counter = 0
                append(text.subSequence(line.start, line.end))
                continue
            }
            counter += 1
            val oldPrefixLen = listPrefixLength(fullText, line, kind)
            val newPrefix = "$counter. "
            if (newPrefix != fullText.substring(line.start, line.start + oldPrefixLen)) changed = true
            val content = withoutListAnnotation(text.subSequence(line.start + oldPrefixLen, line.end))
            val prefixStart = length
            append(newPrefix)
            append(content)
            addStringAnnotation(LIST_ANNOTATION_TAG, kind.name, prefixStart, length)
        }
    }
    return if (changed) rebuilt else text
}

// Enter pressed on a list line: continue the list with the next prefix, or —
// if the line holds nothing but its own prefix — end the list instead, which
// is the only way out of one. Returns null when the caret isn't on a list
// line, meaning the newline should be inserted normally.
//
// `previous` is the state *before* the newline was typed, so this decides
// what the edit should have been rather than patching up after it.
fun continuingListOnNewline(
    previous: AnnotatedString,
    caret: Int,
): Pair<AnnotatedString, TextRange>? {
    val fullText = previous.text
    if (caret < 0 || caret > fullText.length) return null
    val allLines = splitLines(fullText, SimpleRange(0, fullText.length))
    val line = lineContaining(allLines, caret) ?: return null
    val kind = listKindAtLineStart(previous, line) ?: return null
    val prefixLen = listPrefixLength(fullText, line, kind)

    // Nothing typed on this item yet — Enter ends the list.
    if (line.end - line.start <= prefixLen) {
        val stripped = buildAnnotatedString {
            append(previous.subSequence(0, line.start))
            append(withoutListAnnotation(previous.subSequence(line.start + prefixLen, previous.text.length)))
        }
        return renumberOrderedLists(stripped) to TextRange(line.start, line.start)
    }

    val newPrefix = if (kind == RichNotesListKind.ORDERED) "0. " else "• "
    val inserted = buildAnnotatedString {
        append(previous.subSequence(0, caret))
        append("\n")
        val prefixStart = length
        append(newPrefix)
        addStringAnnotation(LIST_ANNOTATION_TAG, kind.name, prefixStart, length)
        append(previous.subSequence(caret, previous.text.length))
    }
    // The "0. " above is a placeholder; renumbering assigns the real value,
    // which also fixes up every following item in the same run.
    val renumbered = renumberOrderedLists(inserted)
    val caretAfter = caret + 1 + prefixLengthAfterRenumber(renumbered, caret + 1, kind, newPrefix.length)
    return renumbered to TextRange(caretAfter, caretAfter)
}

// Where the caret lands after the freshly inserted prefix. Renumbering can
// change that prefix's width ("9. " -> "10. "), so it's measured on the
// renumbered text rather than assumed from the placeholder.
private fun prefixLengthAfterRenumber(
    text: AnnotatedString,
    lineStart: Int,
    kind: RichNotesListKind,
    fallback: Int,
): Int {
    val allLines = splitLines(text.text, SimpleRange(0, text.text.length))
    val line = allLines.firstOrNull { it.start == lineStart } ?: return fallback
    return listPrefixLength(text.text, line, kind)
}

// MARK: - Encode

private fun encodeInlineHtml(text: AnnotatedString, start: Int, end: Int): String {
    if (end <= start) return ""
    val html = StringBuilder()
    val boundaries = sortedSetOf(start, end)
    for (r in text.spanStyles) {
        if (r.start in (start + 1) until end) boundaries.add(r.start)
        if (r.end in (start + 1) until end) boundaries.add(r.end)
    }
    val points = boundaries.toList()
    for (i in 0 until points.size - 1) {
        val segStart = points[i]
        val segEnd = points[i + 1]
        if (segStart >= segEnd) continue
        val active = text.spanStyles.filter { it.start <= segStart && segEnd <= it.end }
        var open = ""
        var close = ""
        if (active.any { markMatches(RichNotesMark.BOLD, it.item) }) {
            open += "<b>"; close = "</b>$close"
        }
        if (active.any { markMatches(RichNotesMark.ITALIC, it.item) }) {
            open += "<i>"; close = "</i>$close"
        }
        if (active.any { markMatches(RichNotesMark.UNDERLINE, it.item) }) {
            open += "<u>"; close = "</u>$close"
        }
        if (active.any { markMatches(RichNotesMark.STRIKETHROUGH, it.item) }) {
            open += "<s>"; close = "</s>$close"
        }
        html.append(open).append(escapeForHtml(text.text.substring(segStart, segEnd))).append(close)
    }
    return html.toString()
}

// Live editor AnnotatedString → the string that gets saved. Walks lines,
// promoting consecutive same-kind tdayList-tagged lines back into a real
// <ul>/<ol>, then delegates the final marker-vs-plain decision (and
// sanitization) to encodeNotes() — the same function every platform's
// paste/decode path already trusts — so manual and pasted formatting are
// encoded through one identical code path.
fun encodeAnnotatedNotes(annotated: AnnotatedString): String {
    val text = annotated.text
    if (text.isEmpty()) return ""

    val lines = splitLines(text, SimpleRange(0, text.length))
    val html = StringBuilder()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val kind = listKindAtLineStart(annotated, line)
        if (kind != null) {
            var j = index
            val group = mutableListOf<SimpleRange>()
            while (j < lines.size && listKindAtLineStart(annotated, lines[j]) == kind) {
                group.add(lines[j])
                j += 1
            }
            val tag = if (kind == RichNotesListKind.ORDERED) "ol" else "ul"
            html.append("<$tag>")
            for (groupLine in group) {
                val prefixLen = listPrefixLength(text, groupLine, kind)
                html.append("<li><p>")
                    .append(encodeInlineHtml(annotated, groupLine.start + prefixLen, groupLine.end))
                    .append("</p></li>")
            }
            html.append("</$tag>")
            index = j
        } else {
            html.append("<p>").append(encodeInlineHtml(annotated, line.start, line.end)).append("</p>")
            index += 1
        }
    }

    return encodeNotes(html.toString())
}

// Live editor AnnotatedString → fully plain text for the "clear formatting"
// action: unlike just stripping SpanStyles (bold/italic/…), list bullets/
// numbers are removed entirely rather than left behind as literal text —
// "clear formatting" means the user wants back to genuinely plain text, not
// a preview that still reads as a list.
fun stripToPlainText(annotated: AnnotatedString): String {
    val text = annotated.text
    if (text.isEmpty()) return ""
    val lines = splitLines(text, SimpleRange(0, text.length))
    val result = StringBuilder()
    for ((idx, line) in lines.withIndex()) {
        if (idx > 0) result.append("\n")
        val kind = listKindAtLineStart(annotated, line)
        val prefixLen = if (kind != null) listPrefixLength(text, line, kind) else 0
        result.append(text.substring(line.start + prefixLen, line.end))
    }
    return result.toString()
}

private fun escapeForHtml(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
