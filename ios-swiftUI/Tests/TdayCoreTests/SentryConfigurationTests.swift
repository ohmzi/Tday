import Sentry
import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// The options the SDK is started with, and the two callbacks that are the gate.
///
/// Every line of `makeOptions` exists because the SDK's default for it describes a product that
/// wants analytics, so a default that moves under a minor-version bump is the failure these tests
/// are for: they pin the answer rather than trusting the SDK to keep giving it.
final class SentryConfigurationTests: XCTestCase {
    private let dsn = "https://publickey@o0.ingest.example.invalid/1"

    private func makeOptions(
        consentedAt: Date? = nil,
        gate: TelemetryGate = TelemetryGate()
    ) -> Options {
        SentryConfiguration.makeOptions(dsn: dsn, consentedAt: consentedAt, gate: gate)
    }

    // MARK: - Failures only

    func testNothingIsCollectedOrSentExceptFailures() {
        let options = makeOptions()

        XCTAssertFalse(options.sendDefaultPii)
        XCTAssertFalse(options.enableAutoSessionTracking)
        XCTAssertFalse(options.sendClientReports)
        XCTAssertEqual(options.sampleRate?.doubleValue, 1)
        XCTAssertEqual(options.tracesSampleRate?.doubleValue, 0)
        XCTAssertNil(options.tracesSampler)
        XCTAssertFalse(options.isTracingEnabled)
    }

    func testNoAutomaticInstrumentationIsLeftOn() {
        let options = makeOptions()

        XCTAssertFalse(options.enableAutoPerformanceTracing)
        XCTAssertFalse(options.enableUIViewControllerTracing)
        XCTAssertFalse(options.enableUserInteractionTracing)
        XCTAssertFalse(options.enablePreWarmedAppStartTracing)
        XCTAssertFalse(options.enableStandaloneAppStartTracing)
        XCTAssertFalse(options.enableTimeToFullDisplayTracing)
        XCTAssertFalse(options.enableFileIOTracing)
        XCTAssertFalse(options.enableDataSwizzling)
        XCTAssertFalse(options.enableFileManagerSwizzling)
        XCTAssertFalse(options.enableCoreDataTracing)
    }

    func testTheNetworkIsInvisibleToTheSdk() {
        let options = makeOptions()

        XCTAssertFalse(options.enableSwizzling)
        XCTAssertFalse(options.enableNetworkTracking)
        XCTAssertFalse(options.enableNetworkBreadcrumbs)
        XCTAssertFalse(options.enableCaptureFailedRequests)
        XCTAssertTrue(options.tracePropagationTargets.isEmpty)
        XCTAssertFalse(options.enablePropagateTraceparent)
    }

    func testNoPictureOfTheScreenAndNoMemoryContents() {
        let options = makeOptions()

        XCTAssertEqual(options.sessionReplay.sessionSampleRate, 0)
        XCTAssertEqual(options.sessionReplay.onErrorSampleRate, 0)
        XCTAssertFalse(options.attachScreenshot)
        XCTAssertFalse(options.attachViewHierarchy)
        XCTAssertFalse(options.reportAccessibilityIdentifier)
        XCTAssertFalse(options.enableMemoryIntrospection)
        XCTAssertFalse(options.enableMetricKit)
    }

    func testTheFailureClassesTheReportsAreForStayOn() {
        let options = makeOptions()

        XCTAssertTrue(options.enableCrashHandler)
        XCTAssertTrue(options.enableWatchdogTerminationTracking)
        XCTAssertTrue(options.enableAppHangTracking)
        XCTAssertEqual(options.appHangTimeoutInterval, 2)
    }

    func testClosingTheSdkDoesNotWaitToFlush() {
        XCTAssertEqual(makeOptions().shutdownTimeInterval, 0)
    }

    func testTheReleaseAndTheDsnAreSet() {
        let options = makeOptions()

        XCTAssertEqual(options.dsn, dsn)
        XCTAssertTrue(options.releaseName?.hasPrefix("tday-ios@") ?? false)
        XCTAssertNotNil(options.dist)
    }

    // MARK: - The gate

    func testNothingIsSentWhileTheGateIsClosed() {
        let gate = TelemetryGate()
        let options = makeOptions(gate: gate)

        XCTAssertNil(options.beforeSend?(Event(level: .error)))
        XCTAssertNil(options.beforeBreadcrumb?(Breadcrumb(level: .info, category: "tday")))
    }

    func testAnEventPassesTheGateOnceItIsOpenAndLeavesWithoutAUser() throws {
        let gate = TelemetryGate()
        gate.open()
        let options = makeOptions(consentedAt: Date(timeIntervalSince1970: 1), gate: gate)
        let event = Event(level: .error)
        event.user = User(userId: "5F3C0D2E-91AB-4C7D-8E2F-0A1B2C3D4E5F")

        let sent = try XCTUnwrap(options.beforeSend?(event))

        XCTAssertNil(sent.user)
        XCTAssertEqual(sent.tags?["client"], "ios")
    }

    func testAFailureFromBeforeConsentIsDroppedEvenThroughAnOpenGate() {
        let gate = TelemetryGate()
        gate.open()
        let options = makeOptions(consentedAt: Date(timeIntervalSince1970: 2_000_000_000), gate: gate)
        let event = Event(level: .error)
        event.timestamp = Date(timeIntervalSince1970: 1_900_000_000)

        XCTAssertNil(options.beforeSend?(event))
    }

    func testClosingTheGateStopsTheNextEventAtOnce() {
        let gate = TelemetryGate()
        gate.open()
        let options = makeOptions(consentedAt: Date(timeIntervalSince1970: 1), gate: gate)
        XCTAssertNotNil(options.beforeSend?(Event(level: .error)))

        gate.close()

        XCTAssertNil(options.beforeSend?(Event(level: .error)))
    }

    func testOnlyStructuralBreadcrumbsPassAnOpenGate() {
        let gate = TelemetryGate()
        gate.open()
        let options = makeOptions(gate: gate)

        XCTAssertNotNil(options.beforeBreadcrumb?(Breadcrumb(level: .info, category: "tday")))
        XCTAssertNil(options.beforeBreadcrumb?(Breadcrumb(level: .info, category: "touch")))
    }

    func testTheGateIsClosedUntilSomethingOpensIt() {
        XCTAssertFalse(TelemetryGate().isOpen)
    }

    // MARK: - TEST-CRASH titles

    /// A trap's report has no exception value, so Sentry titles the issue after the crashing symbol
    /// (`_assertionFailure`). The trigger's breadcrumb is what puts the screen back into the title and
    /// marks the report as a test.
    func testATestTriggerNamesItsScreenAndMarksItself() {
        let event = Event()
        let breadcrumb = Breadcrumb(level: .warning, category: "tday")
        breadcrumb.message = "test_crash:TC-FEED-ANY: anytime feed"
        event.breadcrumbs = [breadcrumb]
        let exception = Exception(value: nil, type: "EXC_BREAKPOINT")
        let mechanism = Mechanism(type: "mach")
        mechanism.synthetic = NSNumber(value: true)
        exception.mechanism = mechanism
        event.exceptions = [exception]

        event.applyTestCrashTitle()

        XCTAssertEqual(event.exceptions?.first?.value, "TEST-CRASH TC-FEED-ANY: anytime feed")
        // The message stays out of it while there is an exception to title with: setting both made
        // Sentry repeat the title (`…: TEST-CRASH TC-SET-ERROR`).
        XCTAssertNil(event.message)
        // Sentry titles a synthetic exception after the crashing symbol instead of the value.
        XCTAssertEqual(event.exceptions?.first?.mechanism?.synthetic, NSNumber(value: false))
        XCTAssertEqual(event.tags?["test_crash"], "true")
        XCTAssertEqual(event.tags?["test_crash_id"], "TC-FEED-ANY")
        // Every trap shares a crashing frame, so the fingerprint is what keeps one issue per trigger.
        XCTAssertEqual(event.fingerprint, ["test-crash", "TC-FEED-ANY"])
    }

    /// A captured error already carries a value, and writing over it made Sentry append the two.
    func testACapturedErrorsOwnValueIsLeftExactlyAsItIs() {
        let event = Event()
        let breadcrumb = Breadcrumb(level: .warning, category: "tday")
        breadcrumb.message = "test_crash:TC-SET-ERROR: settings handled capture"
        event.breadcrumbs = [breadcrumb]
        let exception = Exception(value: "TEST-CRASH TC-SET-ERROR: settings handled capture", type: "NSError")
        exception.mechanism = Mechanism(type: "NSError")
        exception.mechanism?.handled = NSNumber(value: true)
        event.exceptions = [exception]

        event.applyTestCrashTitle()

        XCTAssertEqual(
            event.exceptions?.first?.value,
            "TEST-CRASH TC-SET-ERROR: settings handled capture"
        )
        XCTAssertNil(event.message)
        XCTAssertEqual(event.tags?["test_crash_id"], "TC-SET-ERROR")
    }

    /// A trap's exception already has a value — the runtime's own text, which names no screen. It is
    /// the case the rewrite exists for, and a device sweep caught it being skipped: every trap issue
    /// was titled `Fatal error: Index out of range` instead of the trigger.
    func testARuntimeTrapTitleIsWrittenOver() {
        let event = Event()
        let breadcrumb = Breadcrumb(level: .warning, category: "tday")
        breadcrumb.message = "test_crash:TC-CALENDAR: calendar"
        event.breadcrumbs = [breadcrumb]
        let exception = Exception(value: "Fatal error: Can't remove first element from an empty collection", type: "EXC_BREAKPOINT")
        exception.mechanism = Mechanism(type: "mach")
        exception.mechanism?.synthetic = NSNumber(value: true)
        event.exceptions = [exception]

        event.applyTestCrashTitle()

        XCTAssertEqual(event.exceptions?.first?.value, "TEST-CRASH TC-CALENDAR: calendar")
        XCTAssertNil(event.message)
        XCTAssertEqual(event.tags?["test_crash_id"], "TC-CALENDAR")
    }

    /// An event with no exception at all still needs something to be titled with.
    func testAnEventWithNoExceptionFallsBackToTheMessage() {
        let event = Event()
        let breadcrumb = Breadcrumb(level: .warning, category: "tday")
        breadcrumb.message = "test_crash:TC-SET-ERROR: settings handled capture"
        event.breadcrumbs = [breadcrumb]

        event.applyTestCrashTitle()

        XCTAssertEqual(event.message?.formatted, "TEST-CRASH TC-SET-ERROR: settings handled capture")
        XCTAssertEqual(event.tags?["test_crash_id"], "TC-SET-ERROR")
    }

    /// The breadcrumb reaches `beforeSend` through the scrubber, which writes the spaces in a label
    /// as underscores. The title has to come out with the spaces the other clients' titles have, so
    /// the trigger's id-and-screen text reads the same on every platform.
    func testTheScrubbersUnderscoresComeBackAsSpaces() {
        let event = Event()
        let breadcrumb = Breadcrumb(level: .warning, category: "tday")
        breadcrumb.message = "test_crash:TC-FEED-ANY:_anytime_feed"
        event.breadcrumbs = [breadcrumb]
        event.exceptions = [Exception(value: nil, type: "EXC_BREAKPOINT")]

        event.applyTestCrashTitle()

        XCTAssertEqual(event.exceptions?.first?.value, "TEST-CRASH TC-FEED-ANY: anytime feed")
        XCTAssertEqual(event.tags?["test_crash_id"], "TC-FEED-ANY")
    }

    /// The whole way out: the options' own `beforeSend`, with the gate open, on an event shaped like a
    /// crash report.
    func testTheGateOpenSendPathTitlesATestCrash() {
        let gate = TelemetryGate()
        gate.open()
        let options = makeOptions(consentedAt: Date(timeIntervalSince1970: 1_000), gate: gate)

        let event = Event(level: .fatal)
        let breadcrumb = Breadcrumb(level: .warning, category: "tday")
        breadcrumb.message = "test_crash:TC-SET-CRASH:_settings_fatal_crash"
        event.breadcrumbs = [breadcrumb]
        event.exceptions = [Exception(value: nil, type: "EXC_BREAKPOINT")]

        let sent = options.beforeSend?(event)

        XCTAssertEqual(sent?.exceptions?.first?.value, "TEST-CRASH TC-SET-CRASH: settings fatal crash")
        XCTAssertEqual(sent?.tags?["test_crash"], "true")
        XCTAssertEqual(sent?.tags?["test_crash_id"], "TC-SET-CRASH")
    }

    /// Every real event goes through the same hook, and none of them may be renamed or tagged.
    func testAnEventWithoutATestBreadcrumbIsLeftAlone() {
        let event = Event()
        let breadcrumb = Breadcrumb(level: .warning, category: "tday")
        breadcrumb.message = "sync replay failed"
        event.breadcrumbs = [breadcrumb]
        event.exceptions = [Exception(value: "Could not reach the server", type: "NSURLErrorDomain")]
        event.tags = ["operation": "sync_replay"]

        event.applyTestCrashTitle()

        XCTAssertEqual(event.exceptions?.first?.value, "Could not reach the server")
        XCTAssertNil(event.message)
        XCTAssertEqual(event.tags, ["operation": "sync_replay"])
    }
}
