import UIKit
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

    func testStripToPlainTextRemovesListPrefixesEntirely() {
        // Unlike flattenNotesToPlainText (used for previews, where the "• "
        // still reads as a list), "clear formatting" should leave no trace
        // of the list structure at all.
        let stored = richNotesMarker + "<p><b>Buy</b> milk</p><ul><li>eggs</li><li>bread</li></ul>"
        XCTAssertEqual(stripToPlainText(stored), "Buy milk\neggs\nbread")
    }

    func testStripToPlainTextOfPlainOrNilPassesThrough() {
        XCTAssertEqual(stripToPlainText("plain\nnotes"), "plain\nnotes")
        XCTAssertEqual(stripToPlainText(nil), "")
    }

    func testFlattenOfNilOrEmptyIsEmptyString() {
        XCTAssertEqual(flattenNotesToPlainText(nil), "")
        XCTAssertEqual(flattenNotesToPlainText(""), "")
    }
}

// MARK: - Manual formatting (RichNotesAttributedString.swift)

final class RichNotesManualFormattingTests: XCTestCase {
    private func makeStyle() -> RichNotesTextStyle {
        RichNotesTextStyle(baseFont: TdayFont.uiFont(size: 18, weight: .semibold), baseColor: .black)
    }

    // The whole bold-detection scheme hinges on baseFont and boldFont having
    // different fontNames — if font resolution ever collapsed them to the
    // same font (e.g. a bundling regression), every note would silently
    // encode as permanently bold. Fail loudly instead.
    func testStyleBoldFontIsDistinctFromBaseFont() {
        let style = makeStyle()
        XCTAssertNotEqual(style.baseFont.fontName, style.boldFont.fontName)
    }

    // MARK: isMarkActive / togglingMark

    func testIsMarkActiveTrueOnlyWhenWholeRangeHasTheMark() {
        let style = makeStyle()
        let plain = NSAttributedString(string: "hello", attributes: [.font: style.baseFont])
        let bolded = togglingMark(.bold, in: plain, range: NSRange(location: 0, length: 3), style: style)

        // Fully inside the bolded run.
        XCTAssertTrue(isMarkActive(.bold, in: bolded, range: NSRange(location: 0, length: 3), style: style))
        // Fully inside the untouched tail.
        XCTAssertFalse(isMarkActive(.bold, in: bolded, range: NSRange(location: 3, length: 2), style: style))
        // Straddles both — mixed, so not "active".
        XCTAssertFalse(isMarkActive(.bold, in: bolded, range: NSRange(location: 0, length: 5), style: style))
    }

    func testTogglingMarkAppliesThenRemovesEachMark() {
        let style = makeStyle()
        let base = NSAttributedString(string: "hello", attributes: [.font: style.baseFont, .foregroundColor: style.baseColor])
        let range = NSRange(location: 0, length: 5)

        for mark in [RichNotesMark.bold, .italic, .underline, .strikethrough] {
            XCTAssertFalse(isMarkActive(mark, in: base, range: range, style: style), "\(mark) should start inactive")
            let applied = togglingMark(mark, in: base, range: range, style: style)
            XCTAssertTrue(isMarkActive(mark, in: applied, range: range, style: style), "\(mark) should be active after toggling on")
            XCTAssertEqual(applied.string, "hello", "toggling a mark must never change the text itself")
            let removed = togglingMark(mark, in: applied, range: range, style: style)
            XCTAssertFalse(isMarkActive(mark, in: removed, range: range, style: style), "\(mark) should be inactive after toggling off")
        }
    }

    func testTogglingBoldOnMixedSelectionAppliesRatherThanRemoves() {
        // Notes/Gmail semantics: a selection that's only partially bold gets
        // bolded in full on first toggle, not stripped.
        let style = makeStyle()
        let base = NSAttributedString(string: "helloworld", attributes: [.font: style.baseFont])
        let halfBold = togglingMark(.bold, in: base, range: NSRange(location: 0, length: 5), style: style)
        let range = NSRange(location: 0, length: 10)
        XCTAssertFalse(isMarkActive(.bold, in: halfBold, range: range, style: style))
        let fullyBold = togglingMark(.bold, in: halfBold, range: range, style: style)
        XCTAssertTrue(isMarkActive(.bold, in: fullyBold, range: range, style: style))
    }

    func testTogglingMarkOnEmptyRangeIsANoOp() {
        let style = makeStyle()
        let base = NSAttributedString(string: "hello", attributes: [.font: style.baseFont])
        let result = togglingMark(.bold, in: base, range: NSRange(location: 2, length: 0), style: style)
        XCTAssertEqual(result.string, base.string)
        XCTAssertFalse(isMarkActive(.bold, in: result, range: NSRange(location: 0, length: 5), style: style))
    }

    // MARK: togglingList / isListActive

    func testTogglingBulletListInsertsPrefixAndTagsBothLines() {
        let style = makeStyle()
        let base = NSAttributedString(string: "first\nsecond", attributes: [.font: style.baseFont])
        let full = NSRange(location: 0, length: base.length)

        XCTAssertFalse(isListActive(.bullet, in: base, range: full))
        let (result, selection) = togglingList(.bullet, in: base, range: full, style: style)
        XCTAssertEqual(result.string, "\u{2022} first\n\u{2022} second")
        XCTAssertEqual(selection, NSRange(location: 0, length: result.length))
        XCTAssertTrue(isListActive(.bullet, in: result, range: NSRange(location: 0, length: result.length)))
    }

    func testTogglingOrderedListRenumbersSequentially() {
        let style = makeStyle()
        let base = NSAttributedString(string: "a\nb\nc", attributes: [.font: style.baseFont])
        let (result, _) = togglingList(.ordered, in: base, range: NSRange(location: 0, length: base.length), style: style)
        XCTAssertEqual(result.string, "1. a\n2. b\n3. c")
    }

    func testTogglingListOffRemovesPrefixAndTagWhenAlreadyActive() {
        let style = makeStyle()
        let base = NSAttributedString(string: "first\nsecond", attributes: [.font: style.baseFont])
        let full = NSRange(location: 0, length: base.length)
        let (bulleted, bulletedSelection) = togglingList(.bullet, in: base, range: full, style: style)
        let (removed, _) = togglingList(.bullet, in: bulleted, range: bulletedSelection, style: style)
        XCTAssertEqual(removed.string, "first\nsecond")
        XCTAssertFalse(isListActive(.bullet, in: removed, range: NSRange(location: 0, length: removed.length)))
    }

    func testTogglingListConvertsBulletToOrderedRatherThanStacking() {
        let style = makeStyle()
        let base = NSAttributedString(string: "a\nb", attributes: [.font: style.baseFont])
        let (bulleted, bulletedSelection) = togglingList(.bullet, in: base, range: NSRange(location: 0, length: base.length), style: style)
        let (ordered, _) = togglingList(.ordered, in: bulleted, range: bulletedSelection, style: style)
        XCTAssertEqual(ordered.string, "1. a\n2. b")
    }

    func testTogglingListOnCollapsedCursorAffectsOnlyThatLine() {
        let style = makeStyle()
        let base = NSAttributedString(string: "first\nsecond", attributes: [.font: style.baseFont])
        // Collapsed cursor inside "second" (index 6 = start of "second").
        let (result, selection) = togglingList(.bullet, in: base, range: NSRange(location: 6, length: 0), style: style)
        XCTAssertEqual(result.string, "first\n\u{2022} second")
        XCTAssertEqual(selection, NSRange(location: 8, length: 0))
    }

    // MARK: encodeAttributedNotes — the round-trip regression coverage

    // This is the fix for the pre-existing bug: decode used to expand
    // <ul><li> into plain "• "-prefixed text, but encode only ever emitted
    // <p> — so editing a single character anywhere in a note that contained
    // a list silently destroyed the list on save. encodeAttributedNotes now
    // promotes consecutive same-kind tagged lines back into a real list.
    func testEncodeAttributedNotesPromotesTaggedLinesBackIntoRealList() {
        let style = makeStyle()
        let stored = richNotesMarker + "<ul><li>a</li><li>b</li></ul>"
        let decoded = decodeNotesToAttributedString(stored, style: style)
        let reEncoded = encodeAttributedNotes(decoded, style: style)
        // The inner <p> is intentional — it matches exactly what web's
        // Tiptap listItem emits, so edits from either platform converge on
        // the same bytes instead of churning back and forth.
        XCTAssertEqual(reEncoded, richNotesMarker + "<ul><li><p>a</p></li><li><p>b</p></li></ul>")
    }

    func testEncodeAttributedNotesRoundTripIsStableOnSecondPass() {
        let style = makeStyle()
        let once = encodeAttributedNotes(
            decodeNotesToAttributedString(richNotesMarker + "<ol><li>a</li><li>b</li></ol>", style: style),
            style: style
        )
        let twice = encodeAttributedNotes(decodeNotesToAttributedString(once, style: style), style: style)
        XCTAssertEqual(once, twice)
    }

    func testEncodeAttributedNotesEncodesManuallyAppliedBold() {
        let style = makeStyle()
        let base = NSAttributedString(string: "say hi", attributes: [.font: style.baseFont, .foregroundColor: style.baseColor])
        let bolded = togglingMark(.bold, in: base, range: NSRange(location: 4, length: 2), style: style)
        let encoded = encodeAttributedNotes(bolded, style: style)
        XCTAssertEqual(encoded, richNotesMarker + "<p>say <b>hi</b></p>")
    }

    func testEncodeAttributedNotesEncodesManuallyAppliedList() {
        let style = makeStyle()
        let base = NSAttributedString(string: "eggs\nbread", attributes: [.font: style.baseFont, .foregroundColor: style.baseColor])
        let (listed, _) = togglingList(.bullet, in: base, range: NSRange(location: 0, length: base.length), style: style)
        let encoded = encodeAttributedNotes(listed, style: style)
        XCTAssertEqual(encoded, richNotesMarker + "<ul><li><p>eggs</p></li><li><p>bread</p></li></ul>")
    }

    // Invariant this whole design protects: a plain note that happens to
    // contain a literal "• " line (typed by hand, or from data written
    // before this feature existed) must never be promoted into a rich list
    // just because the text looks like one — only the tracked attribute
    // (set exclusively by decode-from-real-<ul> and the list toggle) counts.
    func testEncodeAttributedNotesNeverPromotesUntaggedLiteralBulletText() {
        let style = makeStyle()
        let plain = "\u{2022} just a bullet character, never tagged"
        let decoded = decodeNotesToAttributedString(plain, style: style)
        let reEncoded = encodeAttributedNotes(decoded, style: style)
        XCTAssertEqual(reEncoded, plain)
        XCTAssertFalse(isRichNotes(reEncoded))
    }

    // MARK: - Typing attributes ("format what I type next")

    func testTogglingMarkInTypingAttributesArmsAndDisarmsEachMark() {
        let style = makeStyle()
        let base: [NSAttributedString.Key: Any] = [.font: style.baseFont, .foregroundColor: style.baseColor]

        for mark in [RichNotesMark.bold, .italic, .underline, .strikethrough] {
            XCTAssertFalse(markIsActive(mark, inAttributes: base, style: style))
            let armed = togglingMarkInTypingAttributes(mark, in: base, style: style)
            XCTAssertTrue(markIsActive(mark, inAttributes: armed, style: style))
            let disarmed = togglingMarkInTypingAttributes(mark, in: armed, style: style)
            XCTAssertFalse(markIsActive(mark, inAttributes: disarmed, style: style))
        }
    }

    // Arming a mark for the next keystroke must not disturb the others —
    // tapping Bold then Italic should give bold-italic, not italic alone.
    func testTypingAttributeMarksStack() {
        let style = makeStyle()
        let base: [NSAttributedString.Key: Any] = [.font: style.baseFont, .foregroundColor: style.baseColor]
        let bold = togglingMarkInTypingAttributes(.bold, in: base, style: style)
        let boldItalic = togglingMarkInTypingAttributes(.italic, in: bold, style: style)

        XCTAssertTrue(markIsActive(.bold, inAttributes: boldItalic, style: style))
        XCTAssertTrue(markIsActive(.italic, inAttributes: boldItalic, style: style))
    }

    // Text typed with armed attributes has to survive the trip through
    // encode — otherwise "tap Bold, type" looks right on screen and then
    // silently saves as plain text.
    func testTextTypedWithArmedAttributesEncodesAsBold() {
        let style = makeStyle()
        let base: [NSAttributedString.Key: Any] = [.font: style.baseFont, .foregroundColor: style.baseColor]
        let armed = togglingMarkInTypingAttributes(.bold, in: base, style: style)

        let typed = NSMutableAttributedString(string: "plain ", attributes: base)
        typed.append(NSAttributedString(string: "bold", attributes: armed))

        XCTAssertEqual(encodeAttributedNotes(typed, style: style), richNotesMarker + "<p>plain <b>bold</b></p>")
    }
}
