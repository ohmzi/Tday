import Foundation

// Canonical rich-text encoding for task/floater "notes" (`description`) — must
// match tday-web's src/lib/richNotes.ts and Android's RichNotes.kt byte-for-byte,
// since all three are reimplementations of the same client-side convention (the
// backend column is just a plain nullable string; there is no shared code path
// for this across platforms).
//
// A note with real formatting (bold/italic/underline/strike/lists) is stored
// as `richNotesMarker + sanitizedHtml`; everything else (including multi-line
// plain text) is stored as a plain string with real "\n" characters, exactly
// like before this feature — untouched notes round-trip byte-identical.

let richNotesMarker = "<!--tday:rich-->"

private let allowedTags: Set<String> = ["b", "strong", "i", "em", "u", "s", "strike", "ul", "ol", "li", "p", "br"]
private let dropContentsTags: Set<String> = ["script", "style"]
private let voidTags: Set<String> = [
    "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr",
]

func isRichNotes(_ value: String?) -> Bool {
    guard let value else { return false }
    return value.hasPrefix(richNotesMarker)
}

// MARK: - Minimal HTML tree

// A hand-rolled tokenizer/tree-builder, not a full HTML parser — there is no
// on-device DOM parser in Foundation that doesn't pull in WebKit. It only
// needs to be correct for the narrow allow-listed tag set below plus lenient
// enough to survive real-world pasted markup (Word/Gmail/Notes), matching
// what Jsoup (Android) and DOMParser (web) give those platforms for free.
indirect enum RichHTMLNode {
    case text(String)
    case element(String, [RichHTMLNode])
}

private enum RichHTMLToken {
    case text(String)
    case open(tag: String, selfClosing: Bool)
    case close(tag: String)
}

private func isAsciiLetter(_ c: Character) -> Bool {
    c.isASCII && c.isLetter
}

private func tokenize(_ html: String) -> [RichHTMLToken] {
    var tokens: [RichHTMLToken] = []
    let chars = Array(html)
    let n = chars.count
    var i = 0
    var textBuffer = ""

    func flushText() {
        if !textBuffer.isEmpty {
            tokens.append(.text(decodeEntities(textBuffer)))
            textBuffer = ""
        }
    }

    while i < n {
        let c = chars[i]
        if c != "<" {
            textBuffer.append(c)
            i += 1
            continue
        }

        // Comments (incl. Word/Outlook conditional comments), doctype, CDATA,
        // processing instructions: never treated as real markup.
        if i + 1 < n, chars[i + 1] == "!" {
            if i + 3 < n, chars[i + 2] == "-", chars[i + 3] == "-" {
                if let end = findSubsequence(chars, from: i + 4, target: ["-", "-", ">"]) {
                    i = end + 3
                } else {
                    i = n
                }
                continue
            }
            if let closeIdx = firstIndex(of: ">", in: chars, from: i) {
                i = closeIdx + 1
            } else {
                i = n
            }
            continue
        }

        let isClosing = i + 1 < n && chars[i + 1] == "/"
        let nameStart = isClosing ? i + 2 : i + 1
        guard nameStart < n, isAsciiLetter(chars[nameStart]) else {
            // Not a real tag start (e.g. "a < b"); keep the '<' as literal text.
            textBuffer.append(c)
            i += 1
            continue
        }

        var j = nameStart
        var name = ""
        while j < n, chars[j].isASCII, chars[j].isLetter || chars[j].isNumber {
            name.append(chars[j])
            j += 1
        }

        // Scan to the matching, quote-aware '>' — attributes are never parsed,
        // only discarded, so a '>' inside a quoted attribute value must not
        // end the tag early.
        var k = j
        var inQuote: Character?
        var selfClosing = false
        while k < n {
            let ch = chars[k]
            if let q = inQuote {
                if ch == q { inQuote = nil }
                k += 1
                continue
            }
            if ch == "\"" || ch == "'" {
                inQuote = ch
                k += 1
                continue
            }
            if ch == ">" {
                break
            }
            if ch == "/", k + 1 < n, chars[k + 1] == ">" {
                selfClosing = true
            }
            k += 1
        }
        guard k < n else {
            // Unterminated tag; treat the remainder as literal text.
            textBuffer.append(contentsOf: chars[i...])
            i = n
            continue
        }

        flushText()
        let lowerName = name.lowercased()
        if isClosing {
            tokens.append(.close(tag: lowerName))
            i = k + 1
        } else if rawTextTags.contains(lowerName), !selfClosing {
            // <script>/<style> are HTML5 "raw text" elements: everything up
            // to their matching close tag is opaque content, never markup —
            // without this, a stray '<' inside the body (e.g. "if (a<b)")
            // would be parsed as a tag, potentially swallowing the real
            // close tag and everything that follows it in the paste.
            tokens.append(.open(tag: lowerName, selfClosing: false))
            let contentStart = k + 1
            if let closeStart = findRawTextEnd(chars, from: contentStart, tag: lowerName) {
                i = closeStart
            } else {
                i = n
            }
        } else {
            tokens.append(.open(tag: lowerName, selfClosing: selfClosing || voidTags.contains(lowerName)))
            i = k + 1
        }
    }
    flushText()
    return tokens
}

private let rawTextTags: Set<String> = ["script", "style"]

// Finds the start of "</tag" (case-insensitive) at or after `from`, only
// counting a match that's actually followed by a tag-boundary character
// (matching HTML5's raw-text end-tag-open recognition), so a substring that
// merely looks like the closing sequence inside the raw content doesn't
// trigger a false close.
private func findRawTextEnd(_ chars: [Character], from: Int, tag: String) -> Int? {
    let target = Array("</\(tag)")
    var i = from
    while i + target.count <= chars.count {
        var matched = true
        for j in 0 ..< target.count where chars[i + j].lowercased() != target[j].lowercased() {
            matched = false
            break
        }
        if matched {
            let boundaryIdx = i + target.count
            let boundaryOk = boundaryIdx >= chars.count
                || chars[boundaryIdx] == ">" || chars[boundaryIdx] == "/" || chars[boundaryIdx].isWhitespace
            if boundaryOk { return i }
        }
        i += 1
    }
    return nil
}

private func firstIndex(of target: Character, in chars: [Character], from: Int) -> Int? {
    var i = from
    while i < chars.count {
        if chars[i] == target { return i }
        i += 1
    }
    return nil
}

private func findSubsequence(_ chars: [Character], from: Int, target: [Character]) -> Int? {
    guard from <= chars.count - target.count else { return nil }
    var i = from
    while i + target.count <= chars.count {
        if Array(chars[i ..< i + target.count]) == target { return i }
        i += 1
    }
    return nil
}

private let namedEntities: [String: String] = [
    "amp": "&", "lt": "<", "gt": ">", "quot": "\"", "apos": "'", "nbsp": "\u{00A0}",
]

private func decodeEntities(_ s: String) -> String {
    guard s.contains("&") else { return s }
    var result = ""
    var i = s.startIndex
    while i < s.endIndex {
        let c = s[i]
        if c == "&" {
            let searchEnd = s.index(i, offsetBy: 32, limitedBy: s.endIndex) ?? s.endIndex
            if let semi = s[i..<searchEnd].firstIndex(of: ";") {
                let entity = s[s.index(after: i)..<semi]
                if entity.hasPrefix("#x") || entity.hasPrefix("#X"),
                   let code = UInt32(entity.dropFirst(2), radix: 16), let scalar = Unicode.Scalar(code) {
                    result.append(Character(scalar))
                    i = s.index(after: semi)
                    continue
                } else if entity.hasPrefix("#"),
                          let code = UInt32(entity.dropFirst()), let scalar = Unicode.Scalar(code) {
                    result.append(Character(scalar))
                    i = s.index(after: semi)
                    continue
                } else if let named = namedEntities[entity.lowercased()] {
                    result.append(named)
                    i = s.index(after: semi)
                    continue
                }
            }
        }
        result.append(c)
        i = s.index(after: i)
    }
    return result
}

private func buildTree(_ tokens: [RichHTMLToken]) -> [RichHTMLNode] {
    var rootChildren: [RichHTMLNode] = []
    var stack: [(tag: String, children: [RichHTMLNode])] = []

    func appendChild(_ node: RichHTMLNode) {
        if stack.isEmpty {
            rootChildren.append(node)
        } else {
            stack[stack.count - 1].children.append(node)
        }
    }

    func popOne() {
        let popped = stack.removeLast()
        appendChild(.element(popped.tag, popped.children))
    }

    for token in tokens {
        switch token {
        case .text(let t):
            appendChild(.text(t))
        case .open(let tag, let selfClosing):
            if selfClosing {
                appendChild(.element(tag, []))
            } else {
                // "p" and "li" have an optional end tag in HTML5: a new same-
                // type sibling implicitly closes the previous one (common in
                // real-world markup, e.g. "<ul><li>a<li>b</ul>"). Without
                // this, "<li>a<li>b</ul>" nests b's content inside a's <li>
                // instead of producing two list items.
                if (tag == "p" || tag == "li"), stack.last?.tag == tag {
                    popOne()
                }
                stack.append((tag, []))
            }
        case .close(let tag):
            // Lenient recovery: close everything down to (and including) the
            // matching open tag; ignore stray closes with no match.
            if stack.contains(where: { $0.tag == tag }) {
                while let top = stack.last, top.tag != tag {
                    popOne()
                }
                if !stack.isEmpty { popOne() }
            }
        }
    }
    while !stack.isEmpty {
        popOne()
    }
    return rootChildren
}

func htmlNodes(from html: String) -> [RichHTMLNode] {
    buildTree(tokenize(html))
}

// MARK: - Sanitize

// Strips everything down to the allowed tag set with no attributes — no
// style/font-size/color/href/on* ever survives, so this is safe to run on
// untrusted pasted HTML or on the editor's own output (defense in depth).
// Disallowed tags are unwrapped (children kept); script/style drop their
// contents entirely.
func sanitizeHtml(_ html: String) -> String {
    let clean = sanitize(htmlNodes(from: html))
    return serialize(clean)
}

private func sanitize(_ nodes: [RichHTMLNode]) -> [RichHTMLNode] {
    var out: [RichHTMLNode] = []
    for node in nodes {
        switch node {
        case .text(let t):
            out.append(.text(t))
        case .element(let tag, let children):
            if dropContentsTags.contains(tag) { continue }
            let cleanChildren = sanitize(children)
            if allowedTags.contains(tag) {
                out.append(.element(tag, cleanChildren))
            } else {
                out.append(contentsOf: cleanChildren)
            }
        }
    }
    return out
}

private func serialize(_ nodes: [RichHTMLNode]) -> String {
    var out = ""
    for node in nodes {
        switch node {
        case .text(let t):
            out += escapeHtml(t)
        case .element(let tag, let children):
            if tag == "br" {
                out += "<br>"
            } else {
                out += "<\(tag)>" + serialize(children) + "</\(tag)>"
            }
        }
    }
    return out
}

func escapeHtml(_ text: String) -> String {
    text.replacingOccurrences(of: "&", with: "&amp;")
        .replacingOccurrences(of: "<", with: "&lt;")
        .replacingOccurrences(of: ">", with: "&gt;")
        .replacingOccurrences(of: "\u{00A0}", with: "&nbsp;")
}

private let formattingTagRegex = try! NSRegularExpression(
    pattern: "<(b|strong|i|em|u|s|strike|ul|ol)(\\s|>)",
    options: [.caseInsensitive]
)

func htmlHasFormatting(_ html: String) -> Bool {
    let range = NSRange(html.startIndex..., in: html)
    return formattingTagRegex.firstMatch(in: html, options: [], range: range) != nil
}

// MARK: - Plain-text extraction

private func getInlineText(_ node: RichHTMLNode) -> String {
    switch node {
    case .text(let t):
        return t
    case .element(let tag, let children):
        if tag == "br" { return "\n" }
        return children.map(getInlineText).joined()
    }
}

private func extractBlockLines(_ nodes: [RichHTMLNode], into lines: inout [String], includeListPrefixes: Bool) {
    for node in nodes {
        switch node {
        case .text(let t):
            if !t.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                lines.append(t)
            }
        case .element(let tag, let children):
            switch tag {
            case "ul", "ol":
                let ordered = tag == "ol"
                var counter = 0
                for child in children {
                    guard case .element("li", let liChildren) = child else { continue }
                    counter += 1
                    let prefix = includeListPrefixes ? (ordered ? "\(counter). " : "\u{2022} ") : ""
                    let text = liChildren.map(getInlineText).joined()
                    for (idx, part) in text.components(separatedBy: "\n").enumerated() {
                        lines.append(idx == 0 ? prefix + part : part)
                    }
                }
            case "p":
                let text = children.map(getInlineText).joined()
                lines.append(contentsOf: text.components(separatedBy: "\n"))
            case "br":
                lines.append("")
            default:
                extractBlockLines(children, into: &lines, includeListPrefixes: includeListPrefixes)
            }
        }
    }
}

// `includeListPrefixes` controls whether list items keep their "• "/"1. "
// text prefix: true for previews (list rows, search, share text — where the
// structure should still read), false for genuinely stripping all
// formatting (the "clear formatting" button, which should remove bullets
// and numbers, not just the marks around them).
func htmlToPlainText(_ html: String, includeListPrefixes: Bool = true) -> String {
    var lines: [String] = []
    extractBlockLines(htmlNodes(from: html), into: &lines, includeListPrefixes: includeListPrefixes)
    return lines.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
}

// MARK: - Encode / decode

// Editor HTML → the string that gets saved. Multi-line-but-unstyled input
// stays a plain "\n"-joined string with no marker; only real formatting opts
// into the marker+HTML encoding.
func encodeNotes(_ editorHtml: String) -> String {
    let sanitized = sanitizeHtml(editorHtml)
    if htmlHasFormatting(sanitized) {
        return richNotesMarker + sanitized
    }
    return htmlToPlainText(sanitized)
}

// Saved string → editor HTML, for initializing/resetting the rich-text state.
func decodeNotesToHtml(_ value: String?) -> String {
    guard let value, !value.isEmpty else { return "<p></p>" }
    if isRichNotes(value) {
        let sanitized = sanitizeHtml(String(value.dropFirst(richNotesMarker.count)))
        return sanitized.isEmpty ? "<p></p>" : sanitized
    }
    return value.components(separatedBy: "\n").map { "<p>\(escapeHtml($0))</p>" }.joined()
}

// Saved string → flattened plain text, for anywhere notes are shown outside
// the editor (list rows, search, share text): real markup never leaks out,
// but list bullets/numbers are kept as plain-text prefixes so the structure
// still reads.
func flattenNotesToPlainText(_ value: String?) -> String {
    guard let value, !value.isEmpty else { return "" }
    if isRichNotes(value) {
        return htmlToPlainText(sanitizeHtml(String(value.dropFirst(richNotesMarker.count))))
    }
    return value
}

// Saved string → fully plain text for the "clear formatting" action: unlike
// flattenNotesToPlainText, list bullets/numbers are removed entirely rather
// than kept as text prefixes — "clear formatting" means the user wants back
// to genuinely plain text, not a preview that still reads as a list.
func stripToPlainText(_ value: String?) -> String {
    guard let value, !value.isEmpty else { return "" }
    if isRichNotes(value) {
        return htmlToPlainText(sanitizeHtml(String(value.dropFirst(richNotesMarker.count))), includeListPrefixes: false)
    }
    return value
}
