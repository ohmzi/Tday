import Foundation
import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// `tdayInferredListIconKey` and `tdayResolvedListIconKey` — the name-derived list glyph.
///
/// The cases below are the shared `ListIconInferenceTest.kt` case-for-case, deliberately and
/// down to the strings. The keyword TABLE is generated from the shared Kotlin one, so it
/// cannot fork; the RULE is a hand-written twin, so it can — and the only thing that stops it
/// is both rules being asked the same questions. Copying the questions is the cheap half of
/// that; `tday-web/tests/guardrails/list-icon-inference-parity.test.ts` is the half a machine
/// can check, and it fails if iOS ever grows a second keyword list of its own.
///
/// What this file does NOT claim: that a cart is the right picture for "Groceries". That is a
/// judgement about a rendered glyph, there is no device in this repo's CI, and no assertion
/// can stand in for it. What it can claim is the two things the feature actually rests on —
/// the matcher emits the key we meant for the titles we meant, and it emits NOTHING for the
/// titles that would fool a lazier matcher.
///
/// There is no Swift toolchain on the machine this was written on, so `xctest` in CI is this
/// file's gate and nothing here was run locally. What WAS done locally: the rule's fifteen
/// lines were transcribed and every case below evaluated against the transcription reading the
/// committed generated table, then perturbed twice. Replacing whole-word lookup with
/// `contains` turns six of the seven buried-keyword cases red — "Carpentry" answers `car`,
/// "Firewood" `fire`, "Scarlett" `car`, "Barcelona" `drink`, "Workaround" `work`,
/// "Bookkeeping" `book`. ("Homework" stays nil even then, because `home` and `work` both hit
/// and the ambiguity rule refuses; it is evidence for that rule, not for whole-word matching,
/// and is kept for the first.) Making a tie resolve leftmost instead of refusing turns all
/// four ambiguous cases red.
final class ListIconInferenceTests: XCTestCase {

    // MARK: - What a title evidences

    func testATitleWhoseSubjectIsInTheTableInfersThatSubjectsGlyph() {
        let expected: [String: String] = [
            "Groceries": "cart",
            "Grocery Shopping": "cart",
            "Work": "work",
            "Gym": "fitness",
            "Reading": "book",
            "Reading List": "book",
            "Travel": "flight",
            "Recipes": "food",
            "Budget": "money",
            "Home": "home",
            "Passwords": "key",
            "Dog": "pets",
            "Birthday": "cake",
        ]

        for (title, key) in expected {
            XCTAssertEqual(tdayInferredListIconKey(forListName: title), key, title)
        }
    }

    func testCaseAndPunctuationAreNotPartOfTheSubject() {
        // The same list, typed five ways by five people, is one list.
        for title in ["groceries", "GROCERIES", "  Groceries  ", "Groceries!!!", "Groceries 🛒"] {
            XCTAssertEqual(tdayInferredListIconKey(forListName: title), "cart", title)
        }
    }

    /// A script the table does not speak does not stop being part of the word.
    ///
    /// `Character.isLetter`/`isNumber` are Unicode, so "仕事Work" is ONE word here, matches
    /// nothing, and correctly says nothing; an ASCII class would cut it where the script
    /// changes, find "work", and draw a confident briefcase for a list this table cannot
    /// read. "Gymé" is the same failure with one accent instead of two kanji. "Работа Work"
    /// is the control: a SPACE is a boundary in every script, so the English word beside a
    /// Cyrillic one is still found.
    ///
    /// Pinned in all three rule twins because web's splitter really was ASCII-only and
    /// really did answer `work`, `fitness` and `work` here while this one answered nil, nil
    /// and `work`. Every drift gate stayed green through it — the generated TABLE was
    /// identical on both sides; only the hand-written rules differed. The cross-language
    /// case comparison in `list-icon-inference-parity.test.ts` catches that now, and it can
    /// only compare cases that exist in every table.
    func testAWordIsCutOnUnicodeLettersNotASCIIOnes() {
        XCTAssertNil(tdayInferredListIconKey(forListName: "仕事Work"))
        XCTAssertNil(tdayInferredListIconKey(forListName: "Gymé"))
        XCTAssertEqual(tdayInferredListIconKey(forListName: "Работа Work"), "work")
    }

    func testTwoWordsPointingAtTheSameGlyphStillAgree() {
        XCTAssertEqual(tdayInferredListIconKey(forListName: "Shopping Errands"), "cart")
        XCTAssertEqual(tdayInferredListIconKey(forListName: "Gym Workout"), "fitness")
    }

    // MARK: - What a title does not evidence

    /// The words a substring matcher gets wrong, asserted by name.
    ///
    /// Each CONTAINS a table keyword and is about something else entirely. A cart on a
    /// carpentry list is worse than the inbox it replaced — an uninformative glyph became a
    /// confident lie — which is the brief's whole rule about being unsure. Delete this test
    /// and the next person can swap in `title.contains(word)` with everything else still green.
    func testAKeywordBuriedInsideAnotherWordEvidencesNothing() {
        for title in ["Carpentry", "Firewood", "Scarlett", "Homework", "Barcelona", "Workaround", "Bookkeeping"] {
            XCTAssertNil(tdayInferredListIconKey(forListName: title), title)
        }
    }

    func testATitleWithNoSubjectAtAllEvidencesNothing() {
        for title in ["", "   ", "!!!", "---", "2026", "Misc", "Stuff", "Untitled", "Q3"] {
            XCTAssertNil(tdayInferredListIconKey(forListName: title), title)
        }
        XCTAssertNil(tdayInferredListIconKey(forListName: nil))
    }

    func testTwoDifferentSubjectsInOneTitleEvidenceNothing() {
        // Equal warrant for two glyphs is not a reason to pick the leftmost.
        for title in ["Work Travel", "Gym and Groceries", "Dog Food", "Music Books"] {
            XCTAssertNil(tdayInferredListIconKey(forListName: title), title)
        }
    }

    /// The matcher must never answer with the default key.
    ///
    /// `tdayLucideListAsset` paints `LucideInbox` for nil, for blank and for an unrecognised
    /// key alike, so "I inferred inbox" and "I have nothing" would arrive at the render site
    /// as the same pixel — and the never-override rule is built on the caller being able to
    /// tell them apart.
    func testTheMatcherNeverEmitsTheDefaultKey() {
        XCTAssertFalse(TdayListIconInferenceTable.emittableIconKeys.contains("inbox"))
        XCTAssertNil(tdayInferredListIconKey(forListName: "Inbox"))
    }

    // MARK: - The table can only name glyphs this app can draw

    /// The failure this catches is INVISIBLE, which is why it is worth a test at all.
    ///
    /// `tdayLucideListAsset` answers `LucideInbox` for a key it has never heard of, with no
    /// log line and no crash. A key emitted here but missing from the asset table would not
    /// ship as a broken icon; it would ship as a list quietly wearing an inbox — precisely
    /// what this feature exists to stop — and nobody would connect the two. Asserting through
    /// the resolver rather than against the table makes the claim end-to-end: not "the key is
    /// in a list somewhere" but "asking for it gets a different picture than asking for
    /// nothing".
    func testEveryEmittableKeyResolvesToAGlyphOtherThanTheDefault() {
        let inbox = tdayLucideListAsset("inbox")
        for key in TdayListIconInferenceTable.emittableIconKeys {
            XCTAssertNotEqual(tdayLucideListAsset(key), inbox, key)
        }
    }

    // MARK: - The resolver: a choice always beats a guess

    func testAListWhoseOwnerChoseAGlyphKeepsItWhateverItsNameSays() {
        // The one rule the brief calls non-negotiable, asserted at the resolver rather than
        // argued in a comment. "Groceries" infers a cart; a "Groceries" list whose owner
        // picked the flame gets the flame.
        XCTAssertEqual(tdayResolvedListIconKey("fire", listName: "Groceries"), "fire")
        // Including when the choice happens to BE the default. This is the case the old data
        // model could not express at all, and the reason the create sheet stopped posting the
        // preview it was seeded with.
        XCTAssertEqual(tdayResolvedListIconKey("inbox", listName: "Groceries"), "inbox")
    }

    func testAListWithNoChosenGlyphTakesTheOneItsNameEvidences() {
        XCTAssertEqual(tdayResolvedListIconKey(nil, listName: "Groceries"), "cart")
        XCTAssertEqual(tdayResolvedListIconKey("  ", listName: "Groceries"), "cart")
    }

    func testAnUnsureResolutionStaysNilSoTheCallerKeepsItsDefault() {
        // nil out of the resolver is what reaches `tdayLucideListAsset`, which turns it into
        // the inbox. Defaulting inside the resolver instead would leave the sheets — which
        // must know whether a glyph was CHOSEN — with no way back to the distinction.
        XCTAssertNil(tdayResolvedListIconKey(nil, listName: "Carpentry"))
        XCTAssertNil(tdayResolvedListIconKey(nil, listName: nil))
        XCTAssertEqual(tdayLucideListAsset(tdayResolvedListIconKey(nil, listName: "Carpentry")), "LucideInbox")
    }
}
