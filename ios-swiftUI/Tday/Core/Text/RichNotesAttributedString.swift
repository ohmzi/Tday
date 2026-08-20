import UIKit

// UIKit-side bridge for the notes rich-text encoding (see RichNotes.swift).
// There is no external rich-text-editor dependency here — a plain UITextView
// backed by NSAttributedString already supports bold/italic/underline/
// strikethrough as real character runs. List structure from a paste is
// deliberately downgraded at paste time into plain "\u{2022} "/"1. "-prefixed
// text lines (the same convention flattenNotesToPlainText/htmlToPlainText
// already use for previews) rather than modeled as an editable semantic list
// — reliably reconstructing <ul><li> through arbitrary further user edits
// needs a real rich-text-editor engine, which added more risk than it was
// worth for a paste-retention feature with no manual list-editing UI. This
// mirrors Android's RichNotesCompose.kt approach exactly.

// Bold is a genuine font swap (this app's only bundled face is Nunito's
// upright/roman instance — no italic face exists), so "bold" maps to the
// heaviest named weight instead of a symbolic trait, matching how the rest
// of the app already selects Nunito weights via TdayFont. "Italic" instead
// uses NSAttributedString's synthetic-oblique attribute, which works on any
// font regardless of whether a true italic face exists.
struct RichNotesTextStyle {
    let baseFont: UIFont
    let boldFont: UIFont
    let baseColor: UIColor

    init(baseFont: UIFont, baseColor: UIColor) {
        self.baseFont = baseFont
        self.boldFont = TdayFont.uiFont(size: baseFont.pointSize, weight: .black)
        self.baseColor = baseColor
    }
}

private let obliquenessValue: NSNumber = 0.2

private func run(
    _ text: String,
    style: RichNotesTextStyle,
    bold: Bool,
    italic: Bool,
    underline: Bool,
    strike: Bool
) -> NSAttributedString {
    var attrs: [NSAttributedString.Key: Any] = [
        .font: bold ? style.boldFont : style.baseFont,
        .foregroundColor: style.baseColor,
    ]
    if italic { attrs[.obliqueness] = obliquenessValue }
    if underline { attrs[.underlineStyle] = NSUnderlineStyle.single.rawValue }
    if strike { attrs[.strikethroughStyle] = NSUnderlineStyle.single.rawValue }
    return NSAttributedString(string: text, attributes: attrs)
}

private func appendInline(
    _ node: RichHTMLNode,
    into result: NSMutableAttributedString,
    style: RichNotesTextStyle,
    bold: Bool,
    italic: Bool,
    underline: Bool,
    strike: Bool
) {
    switch node {
    case .text(let t):
        result.append(run(t, style: style, bold: bold, italic: italic, underline: underline, strike: strike))
    case .element(let tag, let children):
        switch tag {
        case "br":
            result.append(run("\n", style: style, bold: false, italic: false, underline: false, strike: false))
        case "b", "strong":
            for c in children {
                appendInline(c, into: result, style: style, bold: true, italic: italic, underline: underline, strike: strike)
            }
        case "i", "em":
            for c in children {
                appendInline(c, into: result, style: style, bold: bold, italic: true, underline: underline, strike: strike)
            }
        case "u":
            for c in children {
                appendInline(c, into: result, style: style, bold: bold, italic: italic, underline: true, strike: strike)
            }
        case "s", "strike":
            for c in children {
                appendInline(c, into: result, style: style, bold: bold, italic: italic, underline: underline, strike: true)
            }
        default:
            for c in children {
                appendInline(c, into: result, style: style, bold: bold, italic: italic, underline: underline, strike: strike)
            }
        }
    }
}

// Every block (paragraph/list item) becomes one visual line joined by "\n",
// with list items downgraded to plain bullet-prefixed text. No trailing
// newline is added after the last block, so this is used both for full
// decode and for splicing a pasted fragment into the middle of a line.
//
// Real-world pasted fragments are frequently NOT wrapped in a <p> at all
// (e.g. copying a short selection from Gmail/Safari sanitizes down to bare
// "some <b>bold</b> text", or a browser's fragment mixes loose text with a
// <p>: "Intro<p>Body</p>"). Nodes that aren't a block (p/ul/ol) are buffered
// as an implicit paragraph and flushed whenever a real block boundary is
// hit, so this content is never silently dropped.
private func appendBlocks(
    _ nodes: [RichHTMLNode],
    into result: NSMutableAttributedString,
    style: RichNotesTextStyle,
    isFirstBlock: inout Bool
) {
    var pendingInline: [RichHTMLNode] = []

    func flushPendingInline() {
        guard !pendingInline.isEmpty else { return }
        if !isFirstBlock { result.append(run("\n", style: style, bold: false, italic: false, underline: false, strike: false)) }
        for c in pendingInline {
            appendInline(c, into: result, style: style, bold: false, italic: false, underline: false, strike: false)
        }
        isFirstBlock = false
        pendingInline = []
    }

    for node in nodes {
        switch node {
        case .text:
            pendingInline.append(node)
        case .element(let tag, let children):
            switch tag {
            case "p":
                flushPendingInline()
                if !isFirstBlock { result.append(run("\n", style: style, bold: false, italic: false, underline: false, strike: false)) }
                for c in children {
                    appendInline(c, into: result, style: style, bold: false, italic: false, underline: false, strike: false)
                }
                isFirstBlock = false
            case "ul", "ol":
                flushPendingInline()
                let ordered = tag == "ol"
                var counter = 0
                for child in children {
                    guard case .element("li", let liChildren) = child else { continue }
                    counter += 1
                    if !isFirstBlock {
                        result.append(run("\n", style: style, bold: false, italic: false, underline: false, strike: false))
                    }
                    let prefix = ordered ? "\(counter). " : "\u{2022} "
                    result.append(run(prefix, style: style, bold: false, italic: false, underline: false, strike: false))
                    for c in liChildren {
                        appendInline(c, into: result, style: style, bold: false, italic: false, underline: false, strike: false)
                    }
                    isFirstBlock = false
                }
            default:
                // Inline formatting tags (b/strong/i/em/u/s/strike/br) and
                // anything unexpected: not a block, so buffer the node
                // itself (not its children — appendInline needs the tag to
                // apply the mark) as part of the current implicit paragraph.
                pendingInline.append(node)
            }
        }
    }
    flushPendingInline()
}

// Sanitized HTML fragment → styled text with no trailing newline. Used both
// to build a paste fragment and (via decodeNotesToAttributedString) to
// initialize the full field.
func attributedFragment(fromSanitizedHtml html: String, style: RichNotesTextStyle) -> NSAttributedString {
    let result = NSMutableAttributedString()
    var firstBlock = true
    appendBlocks(htmlNodes(from: html), into: result, style: style, isFirstBlock: &firstBlock)
    return result
}

// Saved string → editable rich-text state for the field.
func decodeNotesToAttributedString(_ value: String?, style: RichNotesTextStyle) -> NSAttributedString {
    guard let value, !value.isEmpty else { return NSAttributedString(string: "") }
    if !isRichNotes(value) {
        return NSAttributedString(string: value, attributes: [.font: style.baseFont, .foregroundColor: style.baseColor])
    }
    let sanitized = sanitizeHtml(String(value.dropFirst(richNotesMarker.count)))
    return attributedFragment(fromSanitizedHtml: sanitized, style: style)
}

// Live editor attributed text → the string that gets saved. Walks the
// actual attribute runs rather than round-tripping through HTML text, so it
// stays correct no matter how the user has edited around a pasted span.
func encodeAttributedNotes(_ attributed: NSAttributedString, style: RichNotesTextStyle) -> String {
    let nsText = attributed.string as NSString
    guard nsText.length > 0 else { return "" }

    var hasMark = false
    var html = ""
    var lineStart = 0
    while true {
        let searchRange = NSRange(location: lineStart, length: nsText.length - lineStart)
        let newlineRange = nsText.range(of: "\n", options: [], range: searchRange)
        let lineEnd = newlineRange.location == NSNotFound ? nsText.length : newlineRange.location

        html += "<p>"
        if lineEnd > lineStart {
            let paraRange = NSRange(location: lineStart, length: lineEnd - lineStart)
            attributed.enumerateAttributes(in: paraRange, options: []) { attrs, range, _ in
                let runText = nsText.substring(with: range)
                let isBold = (attrs[.font] as? UIFont)?.fontName == style.boldFont.fontName
                let isItalic = ((attrs[.obliqueness] as? NSNumber)?.doubleValue ?? 0) > 0
                let isUnderline = ((attrs[.underlineStyle] as? Int) ?? 0) != 0
                let isStrike = ((attrs[.strikethroughStyle] as? Int) ?? 0) != 0

                var open = ""
                var close = ""
                if isBold { open += "<b>"; close = "</b>" + close; hasMark = true }
                if isItalic { open += "<i>"; close = "</i>" + close; hasMark = true }
                if isUnderline { open += "<u>"; close = "</u>" + close; hasMark = true }
                if isStrike { open += "<s>"; close = "</s>" + close; hasMark = true }
                html += open + escapeHtml(runText) + close
            }
        }
        html += "</p>"

        if newlineRange.location == NSNotFound { break }
        lineStart = newlineRange.location + 1
    }

    return hasMark ? richNotesMarker + sanitizeHtml(html) : htmlToPlainText(html)
}
