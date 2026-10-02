import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// Which operations count as slow, how slow they were, and how rarely one is reported.
final class SlowOperationTests: XCTestCase {
    // MARK: - Thresholds and buckets

    /// The ids and thresholds are the same on every client; one that drifts here reads as a
    /// different issue on iOS than on Android and the web.
    func testThresholdsAreTheSharedOnes() {
        XCTAssertEqual(SlowOperation.coldStart.thresholdMs, 5_000)
        XCTAssertEqual(SlowOperation.firstDataReady.thresholdMs, 8_000)
        XCTAssertEqual(SlowOperation.dbOpenMigrate.thresholdMs, 3_000)
        XCTAssertEqual(SlowOperation.cacheHydrate.thresholdMs, 2_000)
        XCTAssertEqual(SlowOperation.syncReplay.thresholdMs, 15_000)
        XCTAssertEqual(SlowOperation.apiCall.thresholdMs, 10_000)
        XCTAssertEqual(SlowOperation.widgetRefresh.thresholdMs, 10_000)
        XCTAssertEqual(SlowOperation.reminderReschedule.thresholdMs, 10_000)
    }

    func testOperationIdsAreTheSharedOnes() {
        XCTAssertEqual(
            Set(SlowOperation.allCases.map(\.rawValue)),
            [
                "cold_start", "first_data_ready", "db_open_migrate", "cache_hydrate",
                "sync_replay", "api_call", "widget_refresh", "reminder_reschedule",
            ]
        )
    }

    func testDurationBuckets() {
        XCTAssertEqual(SlowOperation.durationBucket(ms: 4_999), "<5s")
        XCTAssertEqual(SlowOperation.durationBucket(ms: 5_000), "5-10s")
        XCTAssertEqual(SlowOperation.durationBucket(ms: 9_999), "5-10s")
        XCTAssertEqual(SlowOperation.durationBucket(ms: 10_000), "10-30s")
        XCTAssertEqual(SlowOperation.durationBucket(ms: 29_999), "10-30s")
        XCTAssertEqual(SlowOperation.durationBucket(ms: 30_000), "30-60s")
        XCTAssertEqual(SlowOperation.durationBucket(ms: 60_000), "30-60s")
        XCTAssertEqual(SlowOperation.durationBucket(ms: 60_001), ">60s")
    }

    // MARK: - Rate limits

    private var suiteName: String!
    private var defaults: UserDefaults!
    private var clock: Date!
    private var reported: [(SlowOperation, Int)] = []

    override func setUp() {
        super.setUp()
        suiteName = "com.ohmz.tday.tests.slow-operation.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suiteName)!
        defaults.removePersistentDomain(forName: suiteName)
        clock = Date(timeIntervalSince1970: 1_800_000_000)
        reported = []
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        defaults = nil
        super.tearDown()
    }

    /// A fresh limiter is a fresh process: the persisted cooldown carries over, the in-memory counts do not.
    private func makeLimiter() -> SlowOperationLimiter {
        SlowOperationLimiter(defaults: defaults, now: { [unowned self] in self.clock })
    }

    private func report(_ operation: SlowOperation, durationMs: Int, to limiter: SlowOperationLimiter) {
        limiter.report(operation, durationMs: durationMs) { [unowned self] operation, durationMs in
            self.reported.append((operation, durationMs))
        }
    }

    func testAnOperationUnderItsThresholdIsNotReported() {
        let limiter = makeLimiter()

        report(.coldStart, durationMs: 4_999, to: limiter)

        XCTAssertTrue(reported.isEmpty)
    }

    func testAnOperationAtItsThresholdIsReportedWithItsDuration() {
        let limiter = makeLimiter()

        report(.coldStart, durationMs: 5_000, to: limiter)

        XCTAssertEqual(reported.count, 1)
        XCTAssertEqual(reported.first?.0, .coldStart)
        XCTAssertEqual(reported.first?.1, 5_000)
    }

    func testAnOperationIsReportedOncePerProcess() {
        let limiter = makeLimiter()

        report(.syncReplay, durationMs: 20_000, to: limiter)
        report(.syncReplay, durationMs: 40_000, to: limiter)

        XCTAssertEqual(reported.count, 1)
    }

    func testAtMostFiveOperationsAreReportedPerProcess() {
        let limiter = makeLimiter()

        for operation in SlowOperation.allCases {
            report(operation, durationMs: 120_000, to: limiter)
        }

        XCTAssertEqual(reported.count, SlowOperationLimiter.maxPerProcess)
        XCTAssertEqual(reported.count, 5)
    }

    func testAnOperationIsNotReportedAgainWithinADayEvenAfterARelaunch() {
        report(.cacheHydrate, durationMs: 9_000, to: makeLimiter())

        clock = clock.addingTimeInterval(23 * 60 * 60)
        report(.cacheHydrate, durationMs: 9_000, to: makeLimiter())

        XCTAssertEqual(reported.count, 1)
    }

    func testAnOperationIsReportedAgainOnceTheDayIsUp() {
        report(.cacheHydrate, durationMs: 9_000, to: makeLimiter())

        clock = clock.addingTimeInterval(24 * 60 * 60)
        report(.cacheHydrate, durationMs: 9_000, to: makeLimiter())

        XCTAssertEqual(reported.count, 2)
    }

    func testTheCooldownIsPerOperation() {
        let limiter = makeLimiter()

        report(.cacheHydrate, durationMs: 9_000, to: limiter)
        report(.coldStart, durationMs: 9_000, to: makeLimiter())

        XCTAssertEqual(reported.count, 2)
    }

    func testAnOperationThatWasNotReportedDoesNotStartACooldown() {
        report(.coldStart, durationMs: 100, to: makeLimiter())

        report(.coldStart, durationMs: 9_000, to: makeLimiter())

        XCTAssertEqual(reported.count, 1)
    }
}
