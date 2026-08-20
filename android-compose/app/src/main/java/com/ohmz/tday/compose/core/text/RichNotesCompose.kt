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
// Only meaningful for a real (non-empty) selection — Compose's BasicTextField
// has no "typingAttributes" hook to carry a toggle into a collapsed cursor's
// next keystroke, so the format bar only ever acts on an actual selection.
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

// Whether every line touched by `range` is already tagged `kind` — used to
// show the format bar's list buttons as active. Mirrors isMarkActive's
// selection-only scope (no collapsed-cursor support, see its doc comment).
fun isListActive(kind: RichNotesListKind, text: AnnotatedString, range: TextRange): Boolean {
    if (text.text.isEmpty()) return false
    val start = range.min
    val end = range.max
    if (start >= end) return false
    val allLines = splitLines(text.text, SimpleRange(0, text.text.length))
    val lines = touchedLines(allLines, start, end).filter { it.end > it.start }
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
    if (selStart >= selEnd) return text to range

    val allLines = splitLines(fullText, SimpleRange(0, fullText.length))
    val touched = touchedLines(allLines, selStart, selEnd)
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
            // expectation from a real, non-empty selection (this function
            // never runs on a collapsed one — see the guard above).
            if (lineStartBefore < selStart) {
                newSelStart += delta
                newSelEnd += delta
            } else if (lineStartBefore < selEnd) {
                newSelEnd += delta
            }
        }
    }

    return result to TextRange(newSelStart.coerceAtLeast(0), newSelEnd.coerceAtLeast(0))
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

private fun escapeForHtml(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
