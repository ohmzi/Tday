package com.ohmz.tday.compose.core.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
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

private val BOLD_STYLE = SpanStyle(fontWeight = FontWeight.Black)
private val ITALIC_STYLE = SpanStyle(fontStyle = FontStyle.Italic)
private val UNDERLINE_STYLE = SpanStyle(textDecoration = TextDecoration.Underline)
private val STRIKE_STYLE = SpanStyle(textDecoration = TextDecoration.LineThrough)

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
                var counter = 0
                for (li in child.children()) {
                    if (!li.tagName().equals("li", ignoreCase = true)) continue
                    counter += 1
                    append(if (ordered) "$counter. " else "• ")
                    appendInline(li)
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

// Live editor AnnotatedString → the string that gets saved. Walks the actual
// SpanStyle ranges rather than round-tripping through HTML text, so it stays
// correct no matter how the user has edited around a pasted span.
fun encodeAnnotatedNotes(annotated: AnnotatedString): String {
    val text = annotated.text
    if (text.isEmpty()) return ""
    var hasMark = false
    val html = StringBuilder()

    var paraStart = 0
    for (i in 0..text.length) {
        if (i != text.length && text[i] != '\n') continue
        val paraEnd = i
        html.append("<p>")
        if (paraEnd > paraStart) {
            val boundaries = sortedSetOf(paraStart, paraEnd)
            for (range in annotated.spanStyles) {
                if (range.start in (paraStart + 1) until paraEnd) boundaries.add(range.start)
                if (range.end in (paraStart + 1) until paraEnd) boundaries.add(range.end)
            }
            val points = boundaries.toList()
            for (j in 0 until points.size - 1) {
                val segStart = points[j]
                val segEnd = points[j + 1]
                if (segStart >= segEnd) continue
                val active = annotated.spanStyles.filter { it.start <= segStart && segEnd <= it.end }
                var open = ""
                var close = ""
                if (active.any { it.item.fontWeight == FontWeight.Black }) {
                    open += "<b>"; close = "</b>$close"; hasMark = true
                }
                if (active.any { it.item.fontStyle == FontStyle.Italic }) {
                    open += "<i>"; close = "</i>$close"; hasMark = true
                }
                if (active.any { it.item.textDecoration == TextDecoration.Underline }) {
                    open += "<u>"; close = "</u>$close"; hasMark = true
                }
                if (active.any { it.item.textDecoration == TextDecoration.LineThrough }) {
                    open += "<s>"; close = "</s>$close"; hasMark = true
                }
                html.append(open).append(escapeForHtml(text.substring(segStart, segEnd))).append(close)
            }
        }
        html.append("</p>")
        paraStart = paraEnd + 1
    }

    val builtHtml = html.toString()
    return if (hasMark) RICH_NOTES_MARKER + sanitizeHtml(builtHtml) else htmlToPlainText(builtHtml)
}

private fun escapeForHtml(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
