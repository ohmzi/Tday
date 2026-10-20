import SwiftUI
import UIKit
import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// `TdayListAccent.swift` — the list-colour and list-glyph tables the widget extension shares
/// with the app.
///
/// The first test is the one with teeth. These fifteen sRGB values exist twice on this platform:
/// here, and in `todoListAccentColor(for:)` in `Feature/Todos/TodoListScreen.swift`, which the
/// whole app draws list accents from and which could not be repointed at the shared table in the
/// change that introduced it. A second copy of a palette is a fork waiting to happen — someone
/// adjusts one teal and the widget keeps the other forever, which is precisely the bug a user
/// reports as "the widget is the wrong colour". Asserting the two agree key for key costs
/// nothing and fails the moment either moves.
///
/// What it does NOT claim: that these are the right colours, or that they match Android and web.
/// That is a cross-client assertion, and the place for it is a guardrail over all three sources,
/// not a Swift test that can only see one.
final class TdayListAccentTests: XCTestCase {
    func testSharedPaletteMatchesTheAppsOwnListAccentColors() {
        for key in tdayListAccentColorKeys {
            assertSameColor(
                tdayListAccentColorOrNil(colorKey: key),
                todoListAccentColor(for: key),
                key
            )
        }
    }

    /// GREEN and GRAY are the pre-rename spellings of LIME and SLATE; a list created before the
    /// rename still carries one, so both resolvers have to keep answering them.
    func testHistoricalColorKeyAliasesResolveToTheirNewNames() {
        assertSameColor(tdayListAccentColorOrNil(colorKey: "GREEN"), todoListAccentColor(for: "LIME"), "GREEN")
        assertSameColor(tdayListAccentColorOrNil(colorKey: "GRAY"), todoListAccentColor(for: "SLATE"), "GRAY")
        XCTAssertEqual(tdayNormalizedListAccentColorKeyOrNil("GREEN"), "LIME")
        XCTAssertEqual(tdayNormalizedListAccentColorKeyOrNil("GRAY"), "SLATE")
    }

    /// The key arrives from a DTO, a cache record or an export file, none of which guarantees the
    /// picker's casing or that it is untrimmed.
    func testColorKeyIsCaseFoldedAndTrimmed() {
        XCTAssertEqual(tdayNormalizedListAccentColorKeyOrNil("  teal "), "TEAL")
        XCTAssertEqual(tdayNormalizedListAccentColorKeyOrNil("deep_blue"), "DEEP_BLUE")
    }

    /// The nil case is the whole reason `…OrNil` exists beside the defaulting resolver: the widget
    /// has to be able to tell a colourless list from a pink one, because it has a better fallback
    /// than pink — its own kind's accent.
    func testNoColorIsNilRatherThanTheDefault() {
        XCTAssertNil(tdayListAccentColorOrNil(colorKey: nil))
        XCTAssertNil(tdayListAccentColorOrNil(colorKey: ""))
        XCTAssertNil(tdayListAccentColorOrNil(colorKey: "   "))
        XCTAssertNil(tdayListAccentColorOrNil(colorKey: "CHARTREUSE"))
        XCTAssertNil(tdayNormalizedListAccentColorKeyOrNil("CHARTREUSE"))
    }

    func testDefaultingResolverFallsBackToTheFeedsDefault() {
        // Scheduled lists fall back to BLUE, floater lists to TEAL.
        assertSameColor(tdayListAccentColor(colorKey: nil), todoListAccentColor(for: "BLUE"), "nil")
        assertSameColor(tdayListAccentColor(colorKey: "CHARTREUSE"), todoListAccentColor(for: "BLUE"), "unknown")
        assertSameColor(
            tdayListAccentColor(colorKey: nil, isFloater: true),
            todoListAccentColor(for: "TEAL"),
            "nil floater"
        )
        assertSameColor(
            tdayListAccentColor(colorKey: "CHARTREUSE", isFloater: true),
            todoListAccentColor(for: "TEAL"),
            "unknown floater"
        )
        XCTAssertEqual(tdayDefaultScheduledListAccentColorKey, "BLUE")
        XCTAssertEqual(tdayDefaultFloaterListAccentColorKey, "TEAL")
        XCTAssertEqual(tdayDefaultListAccentColorKey(isFloater: false), "BLUE")
        XCTAssertEqual(tdayDefaultListAccentColorKey(isFloater: true), "TEAL")
    }

    /// `tdayLucideListAsset` moved out of `TdayTheme.swift` so the widget extension could compile
    /// it without the rest of that file. A move, not a copy — these spot checks are here so a
    /// later "tidy-up" that reintroduces a second table has something to fail.
    func testGlyphAssetResolutionSurvivedTheMove() {
        XCTAssertEqual(tdayLucideListAsset("work"), "LucideBriefcaseBusiness")
        XCTAssertEqual(tdayLucideListAsset("briefcase"), "LucideBriefcaseBusiness")
        XCTAssertEqual(tdayLucideListAsset("eco"), "LucideLeaf")
        XCTAssertEqual(tdayLucideListAsset(nil), "LucideInbox")
        XCTAssertEqual(tdayLucideListAsset("no-such-glyph"), "LucideInbox")
    }

    /// A catalog row the app wrote before it carried the list's glyph and colour must still
    /// decode, with both absent — an already-placed widget falls back to its kind's accent
    /// instead of failing to decode and rendering nothing at all.
    func testCatalogRowWithoutAccentKeysStillDecodes() throws {
        let legacy = Data(#"{"id":"list-1","name":"Garden","kind":"floater"}"#.utf8)
        let decoded = try JSONDecoder().decode(WidgetConfigurableListEntry.self, from: legacy)
        XCTAssertEqual(decoded.id, "list-1")
        XCTAssertNil(decoded.iconKey)
        XCTAssertNil(decoded.colorKey)
    }

    func testCatalogRowRoundTripsTheAccentKeys() throws {
        let entry = WidgetConfigurableListEntry(
            id: "list-2",
            name: "Garden",
            kind: "floater",
            iconKey: "eco",
            colorKey: "TEAL"
        )
        let decoded = try JSONDecoder().decode(
            WidgetConfigurableListEntry.self,
            from: try JSONEncoder().encode(entry)
        )
        XCTAssertEqual(decoded, entry)
    }

    /// The catalog the widget picker reads has to carry each list's glyph and colour keys
    /// VERBATIM. Verbatim matters: the writer is where someone would be tempted to resolve the
    /// name-derived glyph guess, which would freeze a guess into a file another process reads as
    /// a user's choice. A list with no keys writes none.
    func testCatalogWriterCarriesEachListsAccentKeysUnresolved() throws {
        let fileURL = try XCTUnwrap(
            FileManager.default.containerURL(
                forSecurityApplicationGroupIdentifier: TodayTasksWidgetSnapshotStore.appGroupSuiteName
            ),
            "App Group container unavailable"
        ).appendingPathComponent(WidgetSnapshotFileStore.listsFileName)
        addTeardownBlock { try? FileManager.default.removeItem(at: fileURL) }

        WidgetConfigurableListsStore.save(
            from: OfflineSyncState(
                lists: [
                    CachedListRecord(
                        id: "todo-1", name: "Work", color: "DEEP_BLUE", iconKey: "work",
                        todoCount: 0, updatedAtEpochMs: 0, createdAtEpochMs: 0
                    ),
                    // "Groceries" would INFER a cart. The catalog must still say nil.
                    CachedListRecord(
                        id: "todo-2", name: "Groceries", color: nil, iconKey: nil,
                        todoCount: 0, updatedAtEpochMs: 0, createdAtEpochMs: 0
                    ),
                ],
                floaterLists: [
                    CachedFloaterListRecord(
                        id: "floater-1", name: "Someday", color: "TEAL", iconKey: "eco",
                        todoCount: 0, updatedAtEpochMs: 0, createdAtEpochMs: 0
                    ),
                ]
            )
        )

        let data = try XCTUnwrap(WidgetSnapshotFileStore.read(WidgetSnapshotFileStore.listsFileName))
        let byId = Dictionary(
            uniqueKeysWithValues: try JSONDecoder()
                .decode([WidgetConfigurableListEntry].self, from: data)
                .map { ($0.id, $0) }
        )

        XCTAssertEqual(byId["todo-1"]?.iconKey, "work")
        XCTAssertEqual(byId["todo-1"]?.colorKey, "DEEP_BLUE")
        XCTAssertEqual(byId["floater-1"]?.iconKey, "eco")
        XCTAssertEqual(byId["floater-1"]?.colorKey, "TEAL")
        XCTAssertNil(byId["todo-2"]?.iconKey)
        XCTAssertNil(byId["todo-2"]?.colorKey)
    }

    // MARK: -

    /// Compares what the two resolvers would actually paint. `Color` is `Equatable`, but its
    /// equality is over the way a colour was *described* rather than the colour it resolves to,
    /// so two identical sRGB triples built through different initialisers can compare unequal.
    /// The components are the assertion that means something.
    private func assertSameColor(_ lhs: Color?, _ rhs: Color, _ label: String) {
        guard let lhs else {
            return XCTFail("\(label): shared table has no colour for a key the app resolves")
        }
        // Four separate vars rather than four slots of one array: `&array[0]` and `&array[1]` in
        // one call are two overlapping exclusive accesses to the same array, which Swift refuses.
        var lhsRed: CGFloat = 0, lhsGreen: CGFloat = 0, lhsBlue: CGFloat = 0, lhsAlpha: CGFloat = 0
        var rhsRed: CGFloat = 0, rhsGreen: CGFloat = 0, rhsBlue: CGFloat = 0, rhsAlpha: CGFloat = 0
        UIColor(lhs).getRed(&lhsRed, green: &lhsGreen, blue: &lhsBlue, alpha: &lhsAlpha)
        UIColor(rhs).getRed(&rhsRed, green: &rhsGreen, blue: &rhsBlue, alpha: &rhsAlpha)
        let lhsRGBA = [lhsRed, lhsGreen, lhsBlue, lhsAlpha]
        let rhsRGBA = [rhsRed, rhsGreen, rhsBlue, rhsAlpha]
        for (index, channel) in ["red", "green", "blue", "alpha"].enumerated() {
            XCTAssertEqual(
                lhsRGBA[index],
                rhsRGBA[index],
                accuracy: 0.001,
                "\(label).\(channel) differs between the shared palette and todoListAccentColor"
            )
        }
    }
}
