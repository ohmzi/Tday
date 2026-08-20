package com.ohmz.tday.compose.core.text

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

// Canonical rich-text encoding for task/floater "notes" (`description`) — must
// match tday-web's src/lib/richNotes.ts byte-for-byte, since both are
// reimplementations of the same client-side convention (the backend column is
// just a plain nullable string; there is no shared KMP code path for this).
//
// A note with real formatting (bold/italic/underline/strike/lists) is stored
// as `RICH_NOTES_MARKER + sanitizedHtml`; everything else (including
// multi-line plain text) is stored as a plain string with real "\n"
// characters, exactly like before this feature — untouched notes round-trip
// byte-identical.

const val RICH_NOTES_MARKER = "<!--tday:rich-->"

private val ALLOWED_TAGS = arrayOf("b", "strong", "i", "em", "u", "s", "strike", "ul", "ol", "li", "p", "br")
private val FORMATTING_TAG_RE = Regex("<(b|strong|i|em|u|s|strike|ul|ol)(\\s|>)", RegexOption.IGNORE_CASE)

fun isRichNotes(value: String?): Boolean = value != null && value.startsWith(RICH_NOTES_MARKER)

// Strips everything down to the allowed tag set with no attributes — no
// style/font-size/color/href/on* ever survives, so this is safe to run on
// untrusted pasted HTML or on the editor's own output (defense in depth).
fun sanitizeHtml(html: String): String {
    val doc = Jsoup.parseBodyFragment(html)
    doc.select("script, style").remove()
    val safelist = Safelist.none().addTags(*ALLOWED_TAGS)
    val clean = Cleaner(safelist).clean(doc)
    clean.outputSettings(Document.OutputSettings().prettyPrint(false))
    return clean.body().html()
}

fun htmlHasFormatting(html: String): Boolean = FORMATTING_TAG_RE.containsMatchIn(html)

private fun getInlineText(el: Node): String {
    val out = StringBuilder()
    for (child in el.childNodes()) {
        when (child) {
            is TextNode -> out.append(child.text())
            is Element -> {
                if (child.tagName().equals("br", ignoreCase = true)) {
                    out.append("\n")
                } else {
                    out.append(getInlineText(child))
                }
            }
        }
    }
    return out.toString()
}

private fun extractBlockLines(container: Node, lines: MutableList<String>) {
    for (child in container.childNodes()) {
        if (child !is Element) {
            val text = (child as? TextNode)?.text().orEmpty()
            if (text.isNotBlank()) lines.add(text)
            continue
        }
        when (child.tagName().lowercase()) {
            "ul", "ol" -> {
                val ordered = child.tagName().equals("ol", ignoreCase = true)
                var counter = 0
                for (liNode in child.children()) {
                    if (!liNode.tagName().equals("li", ignoreCase = true)) continue
                    counter += 1
                    val prefix = if (ordered) "$counter. " else "• "
                    getInlineText(liNode).split("\n").forEachIndexed { idx, part ->
                        lines.add(if (idx == 0) prefix + part else part)
                    }
                }
            }
            "p" -> getInlineText(child).split("\n").forEach { lines.add(it) }
            "br" -> lines.add("")
            else -> extractBlockLines(child, lines)
        }
    }
}

fun htmlToPlainText(html: String): String {
    val doc = Jsoup.parseBodyFragment(html)
    val lines = mutableListOf<String>()
    extractBlockLines(doc.body(), lines)
    return lines.joinToString("\n").trim()
}

private fun escapeHtml(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

// Editor HTML → the string that gets saved. Multi-line-but-unstyled input
// stays a plain "\n"-joined string with no marker; only real formatting opts
// into the marker+HTML encoding.
fun encodeNotes(editorHtml: String): String {
    val sanitized = sanitizeHtml(editorHtml)
    return if (htmlHasFormatting(sanitized)) {
        RICH_NOTES_MARKER + sanitized
    } else {
        htmlToPlainText(sanitized)
    }
}

// Saved string → editor HTML, for initializing/resetting the rich-text state.
fun decodeNotesToHtml(value: String?): String {
    if (value.isNullOrEmpty()) return "<p></p>"
    if (isRichNotes(value)) {
        val sanitized = sanitizeHtml(value.removePrefix(RICH_NOTES_MARKER))
        return sanitized.ifEmpty { "<p></p>" }
    }
    return value.split("\n").joinToString("") { line -> "<p>${escapeHtml(line)}</p>" }
}

// Saved string → flattened plain text, for anywhere notes are shown outside
// the editor (list rows, search, share text): real markup never leaks out,
// but list bullets/numbers are kept as plain-text prefixes so the structure
// still reads.
fun flattenNotesToPlainText(value: String?): String {
    if (value.isNullOrEmpty()) return ""
    if (isRichNotes(value)) {
        return htmlToPlainText(sanitizeHtml(value.removePrefix(RICH_NOTES_MARKER)))
    }
    return value
}
