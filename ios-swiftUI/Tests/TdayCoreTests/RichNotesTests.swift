import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

final class RichNotesTests: XCTestCase {
    // MARK: - isRichNotes

    func testIsRichNotesDetectsMarkerPrefix() {
        XCTAssertTrue(isRichNotes("<!--tday:rich--><p>hi</p>"))
        XCTAssertFalse(isRichNotes("plain text"))
        XCTAssertFalse(isRichNotes(nil))
        XCTAssertFalse(isRichNotes(""))
    }

    // MARK: - sanitizeHtml

    func testSanitizeKeepsAllowedTagsAndDropsAttributes() {
        let input = "<b class=\"x\">bold</b><i style=\"color:red\">it</i>"
        XCTAssertEqual(sanitizeHtml(input), "<b>bold</b><i>it</i>")
    }

    func testSanitizeUnwrapsDisallowedTagsButKeepsText() {
        XCTAssertEqual(sanitizeHtml("<div><span>hi</span> <a href=\"x\">link</a></div>"), "hi link")
    }

    func testSanitizeDropsScriptAndStyleContentsEntirely() {
        XCTAssertEqual(sanitizeHtml("<p>before</p><script>alert(1)</script><style>.x{}</style><p>after</p>"), "<p>before</p><p>after</p>")
    }

    func testSanitizeHandlesSelfClosingBr() {
        XCTAssertEqual(sanitizeHtml("a<br/>b<br>c"), "a<br>b<br>c")
    }

    func testSanitizeDropsFontSizeColorAndFamilyByStrippingAllAttributes() {
        XCTAssertEqual(
            sanitizeHtml("<span style=\"font-size:40px;color:red;font-family:Comic Sans\">loud</span>"),
            "loud"
        )
    }

    func testSanitizeHandlesUnclosedVoidLikeTagsWithoutCorruptingTree() {
        // A stray <img> (disallowed, no closing tag in real-world paste) must
        // not swallow the sibling content that follows it.
        XCTAssertEqual(sanitizeHtml("<p>before<img src=\"x\">after</p>"), "<p>beforeafter</p>")
    }

    func testSanitizeDecodesEntities() {
        XCTAssertEqual(sanitizeHtml("Fish &amp; Chips &lt;3 &#38; &#x26;"), "Fish &amp; Chips &lt;3 &amp; &amp;")
    }

    func testSanitizeReEscapesNonBreakingSpaceAsEntity() {
        // Byte-for-byte parity with web (DOM fragment serialization) and
        // Android (jsoup), which both re-emit U+00A0 as "&nbsp;" rather than
        // the raw byte.
        XCTAssertEqual(sanitizeHtml("<p>a&nbsp;b</p>"), "<p>a&nbsp;b</p>")
    }

    func testSanitizeTreatsScriptBodyAsOpaqueRawText() {
        // A stray '<' inside a script body (e.g. "a<b") must not be parsed
        // as a tag — that would swallow the real closing </script> and
        // silently drop everything that follows it in the paste.
        XCTAssertEqual(
            sanitizeHtml("<script>if (a<b) foo()</script><p>real content</p>"),
            "<p>real content</p>"
        )
    }

    func testBuildTreeImpliesListItemEndTag() {
        // "<li>" has an optional end tag in HTML5 — a new "<li>" implicitly
        // closes the previous one, common in real-world (non-XHTML) markup.
        XCTAssertEqual(htmlToPlainText("<ul><li>a<li>b</ul>"), "\u{2022} a\n\u{2022} b")
    }

    func testBuildTreeImpliesParagraphEndTag() {
        XCTAssertEqual(htmlToPlainText("<p>a<p>b</p>"), "a\nb")
    }

    func testSanitizeSurvivesWordConditionalComments() {
        XCTAssertEqual(
            sanitizeHtml("<!--[if gte mso 9]><xml>junk > more junk</xml><![endif]--><p>real</p>"),
            "<p>real</p>"
        )
    }

    func testSanitizeIgnoresAngleBracketThatIsNotARealTag() {
        // A literal "<" not followed by a letter (e.g. "3 < 5") is not a tag
        // start and must survive as escaped text, not corrupt parsing.
        XCTAssertEqual(sanitizeHtml("<p>3 < 5 and a < b</p>"), "<p>3 &lt; 5 and a &lt; b</p>")
    }

    // MARK: - htmlHasFormatting

    func testHtmlHasFormattingDetectsEachAllowedFormattingTag() {
        for tag in ["b", "strong", "i", "em", "u", "s", "strike", "ul", "ol"] {
            XCTAssertTrue(htmlHasFormatting("<\(tag)>x</\(tag)>"), "expected \(tag) to be detected")
        }
    }

    func testHtmlHasFormattingIgnoresPlainStructureTags() {
        XCTAssertFalse(htmlHasFormatting("<p>plain</p><br>"))
    }

    // MARK: - htmlToPlainText

    func testHtmlToPlainTextJoinsParagraphsWithNewlines() {
        XCTAssertEqual(htmlToPlainText("<p>line one</p><p>line two</p>"), "line one\nline two")
    }

    func testHtmlToPlainTextPrefixesUnorderedListItems() {
        XCTAssertEqual(htmlToPlainText("<ul><li>a</li><li>b</li></ul>"), "\u{2022} a\n\u{2022} b")
    }

    func testHtmlToPlainTextPrefixesOrderedListItemsWithIndex() {
        XCTAssertEqual(htmlToPlainText("<ol><li>first</li><li>second</li></ol>"), "1. first\n2. second")
    }

    func testHtmlToPlainTextConvertsBrToNewline() {
        XCTAssertEqual(htmlToPlainText("<p>a<br>b</p>"), "a\nb")
    }

    // MARK: - encodeNotes / decodeNotesToHtml round-trips

    func testEncodeUnstyledMultilineTextStaysPlainWithNoMarker() {
        let html = "<p>line one</p><p>line two</p>"
        let encoded = encodeNotes(html)
        XCTAssertFalse(isRichNotes(encoded))
        XCTAssertEqual(encoded, "line one\nline two")
    }

    func testEncodeStyledTextGetsMarkerAndSanitizedHtml() {
        let html = "<p><b>bold</b> plain</p>"
        let encoded = encodeNotes(html)
        XCTAssertTrue(isRichNotes(encoded))
        XCTAssertEqual(encoded, richNotesMarker + "<p><b>bold</b> plain</p>")
    }

    func testDecodePlainMultilineTextWrapsEachLineInAParagraph() {
        XCTAssertEqual(decodeNotesToHtml("line one\nline two"), "<p>line one</p><p>line two</p>")
    }

    func testDecodeEmptyOrNilYieldsSingleEmptyParagraph() {
        XCTAssertEqual(decodeNotesToHtml(nil), "<p></p>")
        XCTAssertEqual(decodeNotesToHtml(""), "<p></p>")
    }

    func testDecodeRichNotesStripsMarkerAndSanitizes() {
        let stored = richNotesMarker + "<p><b onclick=\"x\">bold</b></p>"
        XCTAssertEqual(decodeNotesToHtml(stored), "<p><b>bold</b></p>")
    }

    func testUntouchedPlainNotesRoundTripByteIdentical() {
        let original = "just some plain notes\nacross two lines"
        let encoded = encodeNotes(decodeNotesToHtml(original))
        XCTAssertEqual(encoded, original)
    }

    // MARK: - flattenNotesToPlainText

    func testFlattenPassesThroughPlainNotesUnchanged() {
        XCTAssertEqual(flattenNotesToPlainText("plain\nnotes"), "plain\nnotes")
    }

    func testFlattenStripsMarkupFromRichNotesButKeepsListPrefixes() {
        let stored = richNotesMarker + "<p><b>Buy</b> milk</p><ul><li>eggs</li><li>bread</li></ul>"
        XCTAssertEqual(flattenNotesToPlainText(stored), "Buy milk\n\u{2022} eggs\n\u{2022} bread")
    }

    func testFlattenOfNilOrEmptyIsEmptyString() {
        XCTAssertEqual(flattenNotesToPlainText(nil), "")
        XCTAssertEqual(flattenNotesToPlainText(""), "")
    }
}
