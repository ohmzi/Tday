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

    // MARK: - The freeze's slices

    /// The freeze holds the main thread in slices, and its pass of the run loop is what hands over a
    /// Stop tap that was queued behind the block. This drives that exact path — a tap scheduled on
    /// the main queue, the loop running on this thread — with a budget too small to notice.
    func testAQueuedStopTapEndsTheFreezeEarly() {
        let state = TestCrash.FreezeState()
        // What the panel's Stop control does, one tap's worth of delay after the block starts.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
            TestCrash.cancelFreeze(state: state)
        }

        let started = Date()
        let cancelled = TestCrash.runFreeze(
            budget: 1,
            sliceSeconds: 0.02,
            holdOffSeconds: 0,
            state: state,
        )
        let elapsed = Date().timeIntervalSince(started)

        XCTAssertTrue(cancelled, "the queued Stop tap never reached the freeze")
        XCTAssertTrue(state.wasCancelled)
        XCTAssertFalse(state.isFrozen)
        XCTAssertLessThan(elapsed, 0.5, "the block outlived the Stop tap")
        XCTAssertEqual(state.lastBlockedMillis ?? 0, Int((elapsed * 1000).rounded()), accuracy: 50)
        XCTAssertTrue(state.resultLine?.hasPrefix("Cancelled after ") == true, state.resultLine ?? "nil")
    }

    /// The other half of the same promise: with no tap, every slice and every pass of the run loop is
    /// capped by what is left of the budget, so a full run cannot overrun it.
    func testAFullFreezeStaysInsideItsBudget() {
        let state = TestCrash.FreezeState()
        let budget: TimeInterval = 0.4

        let started = Date()
        let cancelled = TestCrash.runFreeze(
            budget: budget,
            sliceSeconds: 0.1,
            holdOffSeconds: 0,
            state: state,
        )
        let elapsed = Date().timeIntervalSince(started)

        XCTAssertFalse(cancelled)
        XCTAssertFalse(state.wasCancelled)
        XCTAssertFalse(state.isFrozen)
        XCTAssertLessThanOrEqual(elapsed, budget + 0.1, "the block overran its budget")
        XCTAssertGreaterThanOrEqual(elapsed, budget * 0.8)
        XCTAssertEqual(state.lastBlockedMillis ?? 0, Int((budget * 1000).rounded()), accuracy: 100)
        XCTAssertTrue(state.resultLine?.hasPrefix("Main thread was blocked for ") == true, state.resultLine ?? "nil")
    }

    /// The two decisions the numbers above encode: a slice bounds how long a tap sits behind the
    /// block, and the hold-off covers the SDK's app-hang window (`SentryConfiguration` sets 2 s) so
    /// that servicing the run loop — which is what lets the frames tracker see a frame — cannot happen
    /// before the hang has been reported. It still leaves more than half the budget reachable by Stop.
    func testTheFreezeIsSlicedAndHoldsOffTheRunLoopUntilTheSdkHasSeenTheHang() {
        XCTAssertLessThanOrEqual(TestCrash.freezeSliceSeconds, 0.5)
        XCTAssertGreaterThanOrEqual(TestCrash.freezeRunLoopHoldOffSeconds, TestCrash.freezeSliceSeconds * 5)
        XCTAssertGreaterThan(TestCrash.freezeRunLoopHoldOffSeconds, 2)
        XCTAssertLessThan(TestCrash.freezeRunLoopHoldOffSeconds, TestCrash.freezeSeconds)
        XCTAssertLessThanOrEqual(TestCrash.freezeRunLoopPumpSeconds, TestCrash.freezeSliceSeconds)
    }

    func testCancellingWithoutAFreezeReportsNothingToCancel() {
        XCTAssertFalse(TestCrash.cancelFreeze(state: TestCrash.FreezeState()))
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
