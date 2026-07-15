import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

final class CacheMappersDateParsingTests: XCTestCase {
    func testParsesBackendMinutePrecisionLocalDateTimeAsUTC() {
        let parsed = parseOptionalDate("2026-05-20T21:00")

        XCTAssertNotNil(parsed)
        let calendar = Calendar(identifier: .iso8601)
        let components = calendar.dateComponents(in: TimeZone(secondsFromGMT: 0)!, from: parsed!)
        XCTAssertEqual(components.year, 2026)
        XCTAssertEqual(components.month, 5)
        XCTAssertEqual(components.day, 20)
        XCTAssertEqual(components.hour, 21)
        XCTAssertEqual(components.minute, 0)
    }

    func testMovedDuePreservingTimeKeepsOriginalLocalTime() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/Toronto")!
        let due = Date(timeIntervalSince1970: 1_778_870_730)
        let target = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 6, day: 3)))

        let moved = try XCTUnwrap(movedDuePreservingTime(due: due, targetDay: target, calendar: calendar))
        let components = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: moved)

        XCTAssertEqual(components.year, 2026)
        XCTAssertEqual(components.month, 6)
        XCTAssertEqual(components.day, 3)
        XCTAssertEqual(components.hour, 14)
        XCTAssertEqual(components.minute, 45)
        XCTAssertEqual(components.second, 30)
    }

    func testTimelineRescheduleTargetDateResolvesSectionTargets() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/Toronto")!
        let today = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 5, day: 24)))

        let dayTarget = try XCTUnwrap(timelineRescheduleTargetDate(sectionId: "priority-1779854400.0", today: today, calendar: calendar))
        XCTAssertEqual(calendar.component(.day, from: dayTarget), 27)

        let restTarget = try XCTUnwrap(timelineRescheduleTargetDate(sectionId: "rest-24317", today: today, calendar: calendar))
        XCTAssertEqual(calendar.component(.day, from: restTarget), 31)

        let monthTarget = try XCTUnwrap(timelineRescheduleTargetDate(sectionId: "month-24319", today: today, calendar: calendar))
        XCTAssertEqual(calendar.component(.year, from: monthTarget), 2026)
        XCTAssertEqual(calendar.component(.month, from: monthTarget), 7)
        XCTAssertEqual(calendar.component(.day, from: monthTarget), 1)

        let earlierTarget = try XCTUnwrap(timelineRescheduleTargetDate(sectionId: "earlier", today: today, calendar: calendar))
        XCTAssertEqual(calendar.component(.day, from: earlierTarget), 23)
        XCTAssertNil(timelineRescheduleTargetDate(sectionId: "month-24316", today: today, calendar: calendar))
    }
}

final class RecurrencePriorityGrammarTests: XCTestCase {
    func testCapturesTheFiveRecurrencePresets() {
        XCTAssertEqual(RecurrencePriorityGrammar.parse("water plants every day").rrule, "RRULE:FREQ=DAILY;INTERVAL=1")
        XCTAssertEqual(RecurrencePriorityGrammar.parse("standup weekly").rrule, "RRULE:FREQ=WEEKLY;INTERVAL=1")
        XCTAssertEqual(
            RecurrencePriorityGrammar.parse("gym every weekday").rrule,
            "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR"
        )
        XCTAssertEqual(RecurrencePriorityGrammar.parse("rent monthly").rrule, "RRULE:FREQ=MONTHLY;INTERVAL=1")
        XCTAssertEqual(RecurrencePriorityGrammar.parse("taxes annually").rrule, "RRULE:FREQ=YEARLY;INTERVAL=1")
    }

    func testWeekdayWinsOverWeekSubstring() {
        XCTAssertEqual(
            RecurrencePriorityGrammar.parse("report weekdays").rrule,
            "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR"
        )
    }

    func testCapturesPriority() {
        let bang = RecurrencePriorityGrammar.parse("call mom !!")
        XCTAssertEqual(bang.priority, "High")
        XCTAssertEqual(bang.cleanTitle, "call mom")
        XCTAssertEqual(RecurrencePriorityGrammar.parse("email boss !").priority, "Medium")
        XCTAssertEqual(RecurrencePriorityGrammar.parse("fix bug high priority").priority, "High")
        XCTAssertEqual(RecurrencePriorityGrammar.parse("buy milk low").priority, "Low")
    }

    func testIgnoresNonTrailingBareWords() {
        XCTAssertNil(RecurrencePriorityGrammar.parse("buy low-fat milk").priority)
        XCTAssertNil(RecurrencePriorityGrammar.parse("review highlights").priority)
    }

    func testCapturesRecurrenceAndPriorityTogether() {
        let result = RecurrencePriorityGrammar.parse("gym every day !!")
        XCTAssertEqual(result.rrule, "RRULE:FREQ=DAILY;INTERVAL=1")
        XCTAssertEqual(result.priority, "High")
        XCTAssertEqual(result.cleanTitle, "gym")
    }

    func testLeavesPlainTitlesUntouched() {
        let result = RecurrencePriorityGrammar.parse("buy groceries")
        XCTAssertEqual(result.cleanTitle, "buy groceries")
        XCTAssertNil(result.rrule)
        XCTAssertNil(result.priority)
    }
}

final class RepeatSuggestionEngineTests: XCTestCase {
    private let day: Int64 = 86_400_000
    private let base: Int64 = 1_700_000_000_000

    private func completion(_ title: String, _ days: Int64) -> RepeatSuggestionEngine.Completion {
        RepeatSuggestionEngine.Completion(title: title, completedAtEpochMs: base + days * day)
    }

    func testSuggestsWeeklyForSteadyCadence() {
        let completions = [0, 7, 14, 21].map { completion("water plants", Int64($0)) }
        XCTAssertEqual(
            RepeatSuggestionEngine.suggest(currentTitle: "water plants", completions: completions),
            "RRULE:FREQ=WEEKLY;INTERVAL=1"
        )
    }

    func testRequiresThreeCompletions() {
        let completions = [0, 7].map { completion("water plants", Int64($0)) }
        XCTAssertNil(RepeatSuggestionEngine.suggest(currentTitle: "water plants", completions: completions))
    }

    func testIgnoresIrregularCadence() {
        let completions = [0, 3, 20, 21].map { completion("random chore", Int64($0)) }
        XCTAssertNil(RepeatSuggestionEngine.suggest(currentTitle: "random chore", completions: completions))
    }

    func testMatchesCaseAndPhraseInsensitively() {
        let completions = [
            completion("Water Plants", 0),
            completion("water plants", 7),
            completion("water plants !", 14),
        ]
        XCTAssertEqual(
            RepeatSuggestionEngine.suggest(currentTitle: "water plants every week", completions: completions),
            "RRULE:FREQ=WEEKLY;INTERVAL=1"
        )
    }

    func testIgnoresUnrelatedTitles() {
        let completions = [completion("water plants", 0), completion("water plants", 7), completion("something else", 100)]
        XCTAssertNil(RepeatSuggestionEngine.suggest(currentTitle: "water plants", completions: completions))
    }
}

final class QuietHoursMathTests: XCTestCase {
    func testSameDayWindow() {
        XCTAssertTrue(QuietHoursMath.contains(13 * 60 + 30, 13 * 60, 14 * 60))
        XCTAssertFalse(QuietHoursMath.contains(14 * 60, 13 * 60, 14 * 60))
    }

    func testMidnightSpanning() {
        XCTAssertTrue(QuietHoursMath.contains(2 * 60, 22 * 60, 7 * 60))
        XCTAssertFalse(QuietHoursMath.contains(12 * 60, 22 * 60, 7 * 60))
    }

    func testShift() {
        XCTAssertEqual(QuietHoursMath.minutesUntilWindowEnd(23 * 60, 22 * 60, 7 * 60), 8 * 60)
        XCTAssertEqual(QuietHoursMath.minutesUntilWindowEnd(15 * 60, 13 * 60, 14 * 60), 0)
    }
}

final class FloaterListReusableContractTests: XCTestCase {
    func testDecodesReusableField() throws {
        let json = """
        {"id":"l1","name":"Packing","color":null,"todoCount":3,"iconKey":null,"userID":"u1","updatedAt":null,"createdAt":null,"reusable":true}
        """.data(using: .utf8)!
        let dto = try JSONDecoder().decode(FloaterListDTO.self, from: json)
        XCTAssertEqual(dto.reusable, true)
        XCTAssertEqual(dto.name, "Packing")
    }

    func testReusableDefaultsNilWhenAbsent() throws {
        let json = """
        {"id":"l2","name":"Old list","color":null,"todoCount":0,"iconKey":null,"userID":"u1","updatedAt":null,"createdAt":null}
        """.data(using: .utf8)!
        let dto = try JSONDecoder().decode(FloaterListDTO.self, from: json)
        XCTAssertNil(dto.reusable)
    }
}

final class TaskStepMutationSerializationTests: XCTestCase {
    private func makeJSON() -> JSONEncoder {
        JSONEncoder()
    }

    func testStepMutationsRoundTripWithPayloadFields() throws {
        let state = OfflineSyncState(
            pendingMutations: [
                PendingMutationRecord(
                    mutationId: "m-create-step",
                    kind: .createStep,
                    targetId: "todo-1",
                    timestampEpochMs: 1_700_000_003_000,
                    title: "Preheat oven",
                    description: nil,
                    priority: nil,
                    dueEpochMs: nil,
                    rrule: nil,
                    listId: nil,
                    pinned: nil,
                    completed: nil,
                    instanceDateEpochMs: nil,
                    // Optimistic local step id for replay remapping.
                    name: "local-step-xyz",
                    color: nil,
                    iconKey: nil
                ),
                PendingMutationRecord(
                    mutationId: "m-toggle-step",
                    kind: .toggleStep,
                    targetId: "step-1",
                    timestampEpochMs: 1_700_000_004_000,
                    title: nil,
                    description: nil,
                    priority: nil,
                    dueEpochMs: nil,
                    rrule: nil,
                    listId: nil,
                    pinned: nil,
                    completed: true,
                    instanceDateEpochMs: nil,
                    name: nil,
                    color: nil,
                    iconKey: nil
                ),
                PendingMutationRecord(
                    mutationId: "m-reorder-steps",
                    kind: .reorderSteps,
                    targetId: "todo-1",
                    timestampEpochMs: 1_700_000_006_000,
                    title: nil,
                    description: nil,
                    priority: nil,
                    dueEpochMs: nil,
                    rrule: nil,
                    listId: nil,
                    pinned: nil,
                    completed: nil,
                    instanceDateEpochMs: nil,
                    name: nil,
                    color: nil,
                    iconKey: nil,
                    orderedIds: ["step-2", "step-1", "step-3"]
                ),
            ]
        )

        let encoded = try JSONEncoder().encode(state)
        let decoded = try JSONDecoder().decode(OfflineSyncState.self, from: encoded)

        XCTAssertEqual(decoded, state)
        XCTAssertEqual(decoded.pendingMutations[0].kind, .createStep)
        XCTAssertEqual(decoded.pendingMutations[0].title, "Preheat oven")
        XCTAssertEqual(decoded.pendingMutations[0].name, "local-step-xyz")
        XCTAssertEqual(decoded.pendingMutations[1].completed, true)
        XCTAssertEqual(decoded.pendingMutations[2].orderedIds, ["step-2", "step-1", "step-3"])
    }

    func testMutationKindRawValuesMatchBackend() {
        XCTAssertEqual(MutationKind.createStep.rawValue, "CREATE_STEP")
        XCTAssertEqual(MutationKind.toggleStep.rawValue, "TOGGLE_STEP")
        XCTAssertEqual(MutationKind.deleteStep.rawValue, "DELETE_STEP")
        XCTAssertEqual(MutationKind.reorderSteps.rawValue, "REORDER_STEPS")
    }

    func testTaskStepDTODecodesFromBackendShape() throws {
        let json = """
        {"id":"s1","todoID":"t1","title":"Buy milk","completed":false,"position":0,"createdAt":"2026-07-15T10:00"}
        """.data(using: .utf8)!
        let dto = try JSONDecoder().decode(TaskStepDTO.self, from: json)
        XCTAssertEqual(dto.id, "s1")
        XCTAssertEqual(dto.todoID, "t1")
        XCTAssertEqual(dto.title, "Buy milk")
        XCTAssertEqual(dto.completed, false)
        XCTAssertEqual(dto.position, 0)
    }
}

/// Focus filter list gating (R6-3). Uses a real defaults store, so each case
/// clears the filter afterwards to stay hermetic.
final class FocusFilterStoreTests: XCTestCase {
    override func tearDown() {
        TdayFocusFilterStore.setActiveListIDs([])
        super.tearDown()
    }

    func testNoFilterAllowsEverything() {
        TdayFocusFilterStore.setActiveListIDs([])
        XCTAssertNil(TdayFocusFilterStore.activeListIDs())
        XCTAssertTrue(TdayFocusFilterStore.allows(listId: "list-1"))
        XCTAssertTrue(TdayFocusFilterStore.allows(listId: nil))
    }

    func testActiveFilterGatesByList() {
        TdayFocusFilterStore.setActiveListIDs(["list-1", "list-2"])
        XCTAssertEqual(TdayFocusFilterStore.activeListIDs(), ["list-1", "list-2"])
        XCTAssertTrue(TdayFocusFilterStore.allows(listId: "list-1"))
        XCTAssertFalse(TdayFocusFilterStore.allows(listId: "list-9"))
        // A todo with no list is hidden while an explicit filter is active.
        XCTAssertFalse(TdayFocusFilterStore.allows(listId: nil))
    }
}
