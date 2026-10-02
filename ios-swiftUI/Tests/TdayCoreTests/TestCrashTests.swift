import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

// TEST-CRASH: the safety net for the temporary cross-check triggers. A fatal kind cannot be fired
// inside a test run, so this pins what can be proven: each id's documented kind, a message in the
// shared shape that the scrubber leaves alone, and the three error values that are built rather than trapped.
final class TestCrashTests: XCTestCase {
    /// The documented mapping. A change to a kind is a change here too, on purpose.
    private let documentedKinds: [String: TestCrashKind] = [
        "TC-FEED-SCHED": .fatalError,
        "TC-FEED-ANY": .arrayIndexOutOfRange,
        "TC-BUILTIN-TODAY": .preconditionFailure,
        "TC-BUILTIN-OVERDUE": .forceUnwrapNil,
        "TC-BUILTIN-SCHED": .integerOverflow,
        "TC-BUILTIN-ALL": .divideByZero,
        "TC-BUILTIN-PRIO": .invalidPointer,
        "TC-BUILTIN-DONE": .tryBang,
        "TC-LIST-SCHED": .duplicateDictionaryKeys,
        "TC-LIST-ANY": .negativeArrayCount,
        "TC-TASK-OPEN": .forcedCast,
        "TC-TASK-EDIT": .invalidRange,
        "TC-NEW-LIST": .negativeToUnsigned,
        "TC-NEW-TASK": .narrowingConversion,
        "TC-CALENDAR": .emptyCollectionRemove,
        "TC-SET-CRASH": .stringIndexOutOfBounds,
        "TC-SET-ERROR": .handledCapture,
        "TC-SET-FREEZE": .mainThreadFreeze,
        "TC-SET-NSEXC": .nsException,
    ]

    func testEveryIdHasItsDocumentedKind() {
        XCTAssertEqual(Set(TestCrash.ID.allCases.map(\.rawValue)), Set(documentedKinds.keys))
        for id in TestCrash.ID.allCases {
            XCTAssertEqual(id.kind, documentedKinds[id.rawValue], id.rawValue)
        }
    }

    func testCrashKindsAreDifferentPerScreen() {
        let fatal = TestCrash.ID.allCases.filter { $0.kind.isFatal }
        XCTAssertEqual(Set(fatal.map(\.kind)).count, fatal.count, "two screens share a crash kind")
    }

    func testMessageIsTheSharedShape() {
        for id in TestCrash.ID.allCases {
            XCTAssertEqual(id.message, "TEST-CRASH \(id.rawValue): \(id.summary)")
            XCTAssertLessThanOrEqual(id.message.count, 80, id.rawValue)
            XCTAssertLessThanOrEqual(id.rawValue.count, 18, id.rawValue)
            XCTAssertTrue(id.message.unicodeScalars.allSatisfy { $0.isASCII }, id.rawValue)
        }
    }

    /// What reaches Sentry is what the scrubber leaves, so the message must come through whole.
    func testMessageAndBreadcrumbSurviveTheScrubber() {
        for id in TestCrash.ID.allCases {
            XCTAssertEqual(TelemetryScrubber.scrubMessage(id.message), id.message, id.rawValue)
            XCTAssertEqual(TelemetryScrubber.scrubMessage(TestCrash.makeHandledError(for: id).domain), id.message, id.rawValue)
            let label = "test_crash:\(id.rawValue)"
            XCTAssertEqual(TdayTelemetry.safeLabel(label), label, id.rawValue)
            XCTAssertEqual(TdayTelemetry.safeDataValue(key: "id", value: id.rawValue) as? String, id.rawValue)
        }
        XCTAssertTrue(TelemetryScrubber.isAllowedBreadcrumbCategory("tday"))
    }

    func testBuiltErrorsCarryTheMessage() {
        let done = TestCrash.ID.builtinDone
        XCTAssertEqual(TestCrash.makeError(for: done).message, done.message)

        let exception = TestCrash.makeException(for: .settingsNSException)
        XCTAssertEqual(exception.name, .internalInconsistencyException)
        XCTAssertEqual(exception.reason, TestCrash.ID.settingsNSException.message)

        let handled = TestCrash.makeHandledError(for: .settingsError)
        XCTAssertEqual(handled.domain, TestCrash.ID.settingsError.message)
        // The helper that reports it keeps the domain, which is where the message travels.
        let reportable = TdayTelemetry.reportableError(handled)
        XCTAssertEqual(reportable?.domain, TestCrash.ID.settingsError.message)
    }

    func testFreezeOutlastsTheHangThreshold() {
        XCTAssertGreaterThanOrEqual(TestCrash.freezeSeconds, 5)
    }

    func testFirstVisibleRowSkipsEmptyAndCollapsedSections() {
        XCTAssertTrue(TestCrash.isFirstVisibleRow(sectionIndex: 0, itemIndex: 0, visibleCounts: [2, 3]))
        XCTAssertFalse(TestCrash.isFirstVisibleRow(sectionIndex: 0, itemIndex: 1, visibleCounts: [2, 3]))
        XCTAssertFalse(TestCrash.isFirstVisibleRow(sectionIndex: 1, itemIndex: 0, visibleCounts: [2, 3]))
        // The first section is collapsed, so the second section's first row is the first one shown.
        XCTAssertTrue(TestCrash.isFirstVisibleRow(sectionIndex: 1, itemIndex: 0, visibleCounts: [0, 3]))
        XCTAssertFalse(TestCrash.isFirstVisibleRow(sectionIndex: 0, itemIndex: 0, visibleCounts: [0, 3]))
        XCTAssertFalse(TestCrash.isFirstVisibleRow(sectionIndex: 0, itemIndex: 0, visibleCounts: []))
    }
}
