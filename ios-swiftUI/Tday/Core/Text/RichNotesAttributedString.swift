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
//
// Manual formatting (NotesField.swift's edit-menu Format submenu / list
// buttons) writes the exact same attributes paste-retention already uses —
// there is no separate code path, so a manually-bolded run and a
// paste-bolded run are indistinguishable and both round-trip identically.

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

enum RichNotesMark {
    case bold
    case italic
    case underline
    case strikethrough
}

enum RichNotesListKind: String {
    case bullet
    case ordered
}

// Marks which character run came from a "<li>" so encode can promote a run
// of same-kind tagged lines back into a real <ul>/<ol> — never serialized
// itself (it lives only in the live NSAttributedString), so it can't leak
// into the saved string or affect plain notes that merely contain literal
// "• " text.
extension NSAttributedString.Key {
    static let richNotesListItem = NSAttributedString.Key("richNotesListItem")
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
// with list items downgraded to plain bullet-prefixed text (still tagged
// with .richNotesListItem so encode can promote them back). No trailing
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
                let kind: RichNotesListKind = ordered ? .ordered : .bullet
                var counter = 0
                for child in children {
                    guard case .element("li", let liChildren) = child else { continue }
                    counter += 1
                    if !isFirstBlock {
                        result.append(run("\n", style: style, bold: false, italic: false, underline: false, strike: false))
                    }
                    let lineStart = result.length
                    let prefix = ordered ? "\(counter). " : "\u{2022} "
                    result.append(run(prefix, style: style, bold: false, italic: false, underline: false, strike: false))
                    for c in liChildren {
                        appendInline(c, into: result, style: style, bold: false, italic: false, underline: false, strike: false)
                    }
                    result.addAttribute(
                        .richNotesListItem,
                        value: kind.rawValue,
                        range: NSRange(location: lineStart, length: result.length - lineStart)
                    )
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

// MARK: - Manual formatting (edit-menu Format submenu)

// A mark is "active" for a selection only if every character in it already
// has the mark — same semantics as iOS Notes/Gmail: toggling a mixed
// selection always applies it first, a fully-marked selection removes it.
func isMarkActive(
    _ mark: RichNotesMark,
    in text: NSAttributedString,
    range: NSRange,
    style: RichNotesTextStyle
) -> Bool {
    guard text.length > 0 else { return false }
    let checkRange: NSRange
    if range.length > 0 {
        checkRange = range
    } else if range.location > 0 {
        // Collapsed cursor: reflect the character just before it, matching
        // how typingAttributes convention works for "what happens if I type
        // right here".
        checkRange = NSRange(location: range.location - 1, length: 1)
    } else {
        return false
    }
    var allActive = true
    text.enumerateAttributes(in: checkRange, options: []) { attrs, _, stop in
        if !markIsSet(mark, in: attrs, style: style) {
            allActive = false
            stop.pointee = true
        }
    }
    return allActive
}

private func markIsSet(_ mark: RichNotesMark, in attrs: [NSAttributedString.Key: Any], style: RichNotesTextStyle) -> Bool {
    switch mark {
    case .bold:
        return (attrs[.font] as? UIFont)?.fontName == style.boldFont.fontName
    case .italic:
        return ((attrs[.obliqueness] as? NSNumber)?.doubleValue ?? 0) > 0
    case .underline:
        return ((attrs[.underlineStyle] as? Int) ?? 0) != 0
    case .strikethrough:
        return ((attrs[.strikethroughStyle] as? Int) ?? 0) != 0
    }
}

// Applies `mark` to the whole range if any character lacks it, otherwise
// strips it from the whole range — the range itself never changes length.
func togglingMark(
    _ mark: RichNotesMark,
    in text: NSAttributedString,
    range: NSRange,
    style: RichNotesTextStyle
) -> NSAttributedString {
    guard range.length > 0 else { return text }
    let active = isMarkActive(mark, in: text, range: range, style: style)
    let mutable = NSMutableAttributedString(attributedString: text)
    mutable.beginEditing()
    switch mark {
    case .bold:
        mutable.addAttribute(.font, value: active ? style.baseFont : style.boldFont, range: range)
    case .italic:
        if active {
            mutable.removeAttribute(.obliqueness, range: range)
        } else {
            mutable.addAttribute(.obliqueness, value: obliquenessValue, range: range)
        }
    case .underline:
        if active {
            mutable.removeAttribute(.underlineStyle, range: range)
        } else {
            mutable.addAttribute(.underlineStyle, value: NSUnderlineStyle.single.rawValue, range: range)
        }
    case .strikethrough:
        if active {
            mutable.removeAttribute(.strikethroughStyle, range: range)
        } else {
            mutable.addAttribute(.strikethroughStyle, value: NSUnderlineStyle.single.rawValue, range: range)
        }
    }
    mutable.endEditing()
    return mutable
}

// The attributes newly-typed text at `location` should carry — used to set
// typingAttributes right after a toggle so continued typing keeps whatever
// marks are active at the (now collapsed) selection, the same way every
// other rich-text editor behaves.
func markAttributes(at location: Int, in text: NSAttributedString, style: RichNotesTextStyle) -> [NSAttributedString.Key: Any] {
    guard text.length > 0 else {
        return [.font: style.baseFont, .foregroundColor: style.baseColor]
    }
    let sampleLocation = min(max(location, 0), text.length - 1)
    var attrs = text.attributes(at: sampleLocation, effectiveRange: nil)
    // Never let typing silently inherit the list-tracking attribute — it
    // must only ever be set by decoding a real <ul>/<ol> or by the explicit
    // list toggle, never picked up incidentally from whatever character
    // happened to be at the sample point (typically the "• "/"1. " prefix
    // itself, which has no visible marker of its own to justify carrying
    // the tag into unrelated freshly-typed text).
    attrs[.richNotesListItem] = nil
    if attrs[.font] == nil { attrs[.font] = style.baseFont }
    if attrs[.foregroundColor] == nil { attrs[.foregroundColor] = style.baseColor }
    return attrs
}

// Whether an attribute dictionary (in practice a UITextView's
// typingAttributes) already carries `mark` — the collapsed-cursor
// counterpart to isMarkActive, which needs a range of existing text.
func markIsActive(_ mark: RichNotesMark, inAttributes attrs: [NSAttributedString.Key: Any], style: RichNotesTextStyle) -> Bool {
    markIsSet(mark, in: attrs, style: style)
}

// `attrs` with `mark` flipped. Setting this as typingAttributes at a
// collapsed cursor is what makes "tap Bold, then type" produce bold text —
// the format applies to what comes next rather than needing a selection.
func togglingMarkInTypingAttributes(
    _ mark: RichNotesMark,
    in attrs: [NSAttributedString.Key: Any],
    style: RichNotesTextStyle
) -> [NSAttributedString.Key: Any] {
    var updated = attrs
    let active = markIsSet(mark, in: attrs, style: style)
    switch mark {
    case .bold:
        updated[.font] = active ? style.baseFont : style.boldFont
    case .italic:
        if active {
            updated.removeValue(forKey: .obliqueness)
        } else {
            updated[.obliqueness] = obliquenessValue
        }
    case .underline:
        if active {
            updated.removeValue(forKey: .underlineStyle)
        } else {
            updated[.underlineStyle] = NSUnderlineStyle.single.rawValue
        }
    case .strikethrough:
        if active {
            updated.removeValue(forKey: .strikethroughStyle)
        } else {
            updated[.strikethroughStyle] = NSUnderlineStyle.single.rawValue
        }
    }
    if updated[.font] == nil { updated[.font] = style.baseFont }
    if updated[.foregroundColor] == nil { updated[.foregroundColor] = style.baseColor }
    return updated
}

// MARK: - Manual list toggling

private func listKind(atLineStart lineRange: NSRange, in text: NSAttributedString) -> RichNotesListKind? {
    guard lineRange.length > 0, lineRange.location < text.length else { return nil }
    guard let raw = text.attribute(.richNotesListItem, at: lineRange.location, effectiveRange: nil) as? String else {
        return nil
    }
    return RichNotesListKind(rawValue: raw)
}

// Splits `region` into individual line ranges on "\n" (the newline itself
// belongs to neither line), including a trailing empty line if `region`
// ends right after a newline. Shared by every place that needs to walk
// notes text line-by-line (list toggling, active-state queries, encode).
private func splitIntoLines(_ nsText: NSString, region: NSRange) -> [NSRange] {
    var lines: [NSRange] = []
    var cursor = region.location
    let regionEnd = region.location + region.length
    while true {
        let remaining = NSRange(location: cursor, length: regionEnd - cursor)
        let newlineRange = nsText.range(of: "\n", options: [], range: remaining)
        let thisEnd = (newlineRange.location == NSNotFound || newlineRange.location >= regionEnd) ? regionEnd : newlineRange.location
        lines.append(NSRange(location: cursor, length: thisEnd - cursor))
        if newlineRange.location == NSNotFound || newlineRange.location >= regionEnd { break }
        cursor = newlineRange.location + 1
    }
    return lines
}

// Whether every line touched by `range` is already tagged `kind` — used to
// show a checkmark on the Format menu's list actions. A collapsed cursor
// checks the single line it's on.
func isListActive(_ kind: RichNotesListKind, in text: NSAttributedString, range: NSRange) -> Bool {
    let nsText = text.string as NSString
    guard nsText.length > 0 else { return false }
    let lineRegion = nsText.lineRange(for: range)
    let lines = splitIntoLines(nsText, region: lineRegion).filter { $0.length > 0 }
    guard !lines.isEmpty else { return false }
    return lines.allSatisfy { listKind(atLineStart: $0, in: text) == kind }
}

// UTF-16 length of the visible "\u{2022} " / "1. " prefix at the start of
// `lineRange`, or 0 if it isn't actually there (e.g. the user deleted it
// but the attribute lingers on a stale range — treated as "no prefix to
// strip", not a crash).
private func listPrefixLength(_ nsText: NSString, lineRange: NSRange, kind: RichNotesListKind) -> Int {
    let lineText = nsText.substring(with: lineRange)
    switch kind {
    case .bullet:
        return lineText.hasPrefix("\u{2022} ") ? 2 : 0
    case .ordered:
        var digitCount = 0
        for ch in lineText {
            if ch.isASCII, ch.isNumber { digitCount += 1 } else { break }
        }
        guard digitCount > 0 else { return 0 }
        guard lineText.dropFirst(digitCount).hasPrefix(". ") else { return 0 }
        return digitCount + 2
    }
}

// Toggles `kind` over every non-empty line touched by `range`. If every
// touched line already has `kind`, it's removed from all of them (prefix
// text deleted, attribute cleared); otherwise every touched line is made
// `kind` (converting from the other kind if present), with ordered prefixes
// renumbered 1..n across the touched lines. Mirrors Android's identical
// toggle semantics in RichNotesCompose.kt.
func togglingList(
    _ kind: RichNotesListKind,
    in text: NSAttributedString,
    range: NSRange,
    style: RichNotesTextStyle
) -> (text: NSAttributedString, selection: NSRange) {
    let nsText = text.string as NSString
    guard nsText.length > 0 else { return (text, range) }

    let lineRegion = nsText.lineRange(for: range)
    let lineRanges = splitIntoLines(nsText, region: lineRegion)
    let nonEmpty = lineRanges.filter { $0.length > 0 }
    guard !nonEmpty.isEmpty else { return (text, range) }
    let removing = nonEmpty.allSatisfy { listKind(atLineStart: $0, in: text) == kind }

    enum LineEdit {
        case none
        case remove(oldPrefixLength: Int)
        case apply(oldPrefixLength: Int, newPrefix: String)
    }
    var edits: [LineEdit] = []
    var orderedIndex = 1
    for lr in lineRanges {
        guard lr.length > 0 else { edits.append(.none); continue }
        let existing = listKind(atLineStart: lr, in: text)
        if removing {
            let oldLen = existing == kind ? listPrefixLength(nsText, lineRange: lr, kind: kind) : 0
            edits.append(.remove(oldPrefixLength: oldLen))
        } else {
            let oldLen = existing.map { listPrefixLength(nsText, lineRange: lr, kind: $0) } ?? 0
            let newPrefix = kind == .ordered ? "\(orderedIndex). " : "\u{2022} "
            if kind == .ordered { orderedIndex += 1 }
            edits.append(.apply(oldPrefixLength: oldLen, newPrefix: newPrefix))
        }
    }

    let mutable = NSMutableAttributedString(attributedString: text)
    mutable.beginEditing()
    // Applied in reverse so earlier (not-yet-processed) ranges stay valid
    // while later ones are mutated. Each line's net length change is kept
    // (indexed the same as lineRanges/edits) so selection tracking below —
    // a separate, forward pass — doesn't have to duplicate this mutation.
    var nets = [Int](repeating: 0, count: lineRanges.count)
    for (idx, pair) in Array(zip(lineRanges, edits).enumerated()).reversed() {
        let (lineRange, lineEdit) = pair
        switch lineEdit {
        case .none:
            nets[idx] = 0
        case .remove(let oldPrefixLength):
            if oldPrefixLength > 0 {
                mutable.deleteCharacters(in: NSRange(location: lineRange.location, length: oldPrefixLength))
            }
            let remainingLen = lineRange.length - oldPrefixLength
            if remainingLen > 0 {
                mutable.removeAttribute(.richNotesListItem, range: NSRange(location: lineRange.location, length: remainingLen))
            }
            nets[idx] = -oldPrefixLength
        case .apply(let oldPrefixLength, let newPrefix):
            if oldPrefixLength > 0 {
                mutable.deleteCharacters(in: NSRange(location: lineRange.location, length: oldPrefixLength))
            }
            mutable.insert(
                NSAttributedString(string: newPrefix, attributes: [.font: style.baseFont, .foregroundColor: style.baseColor]),
                at: lineRange.location
            )
            let contentLen = lineRange.length - oldPrefixLength
            mutable.addAttribute(
                .richNotesListItem,
                value: kind.rawValue,
                range: NSRange(location: lineRange.location, length: (newPrefix as NSString).length + contentLen)
            )
            nets[idx] = (newPrefix as NSString).length - oldPrefixLength
        }
    }
    mutable.endEditing()

    // Selection tracking: a collapsed cursor and a real selection need
    // different rules for an edit sitting exactly at the start boundary. An
    // insertion exactly at a collapsed cursor's position pushes the cursor
    // forward past it (typing-at-a-point semantics); the same insertion at
    // the START of a real selection instead grows the selection to include
    // it (the selection's anchor doesn't move, its far edge does) — matching
    // how selecting text and having its start line gain a prefix should
    // still leave that prefix selected, not orphaned just before it.
    let newSelection: NSRange
    if range.length == 0 {
        var locationDelta = 0
        for (lineRange, net) in zip(lineRanges, nets) where lineRange.location <= range.location {
            locationDelta += net
        }
        newSelection = NSRange(location: max(0, range.location + locationDelta), length: 0)
    } else {
        var startDelta = 0
        var withinSelectionDelta = 0
        for (lineRange, net) in zip(lineRanges, nets) {
            if lineRange.location < range.location {
                startDelta += net
            } else if lineRange.location < range.location + range.length {
                withinSelectionDelta += net
            }
        }
        newSelection = NSRange(
            location: max(0, range.location + startDelta),
            length: max(0, range.length + withinSelectionDelta)
        )
    }
    return (mutable, newSelection)
}

// MARK: - Encode

private func encodeInlineHTML(_ range: NSRange, in attributed: NSAttributedString, style: RichNotesTextStyle) -> String {
    guard range.length > 0 else { return "" }
    let nsText = attributed.string as NSString
    var html = ""
    attributed.enumerateAttributes(in: range, options: []) { attrs, subRange, _ in
        let runText = nsText.substring(with: subRange)
        var open = ""
        var close = ""
        if markIsSet(.bold, in: attrs, style: style) { open += "<b>"; close = "</b>" + close }
        if markIsSet(.italic, in: attrs, style: style) { open += "<i>"; close = "</i>" + close }
        if markIsSet(.underline, in: attrs, style: style) { open += "<u>"; close = "</u>" + close }
        if markIsSet(.strikethrough, in: attrs, style: style) { open += "<s>"; close = "</s>" + close }
        html += open + escapeHtml(runText) + close
    }
    return html
}

// Live editor attributed text → the string that gets saved. Walks lines,
// promoting consecutive same-kind .richNotesListItem-tagged lines back into
// a real <ul>/<ol>, then delegates the final marker-vs-plain decision (and
// sanitization) to encodeNotes() — the same function every platform's
// paste/decode path already trusts — so manual and pasted formatting are
// encoded through one identical code path.
func encodeAttributedNotes(_ attributed: NSAttributedString, style: RichNotesTextStyle) -> String {
    let nsText = attributed.string as NSString
    guard nsText.length > 0 else { return "" }

    let lineRanges = splitIntoLines(nsText, region: NSRange(location: 0, length: nsText.length))

    var html = ""
    var index = 0
    while index < lineRanges.count {
        let lineRange = lineRanges[index]
        if let kind = listKind(atLineStart: lineRange, in: attributed) {
            var group: [NSRange] = []
            var j = index
            while j < lineRanges.count, listKind(atLineStart: lineRanges[j], in: attributed) == kind {
                group.append(lineRanges[j])
                j += 1
            }
            let tag = kind == .ordered ? "ol" : "ul"
            html += "<\(tag)>"
            for groupLine in group {
                let prefixLen = listPrefixLength(nsText, lineRange: groupLine, kind: kind)
                let contentRange = NSRange(location: groupLine.location + prefixLen, length: groupLine.length - prefixLen)
                html += "<li><p>" + encodeInlineHTML(contentRange, in: attributed, style: style) + "</p></li>"
            }
            html += "</\(tag)>"
            index = j
        } else {
            html += "<p>" + encodeInlineHTML(lineRange, in: attributed, style: style) + "</p>"
            index += 1
        }
    }

    return encodeNotes(html)
}
