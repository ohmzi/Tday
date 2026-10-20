import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// "Off means off" on disk: what the SDK leaves behind is deleted, and nothing else is.
final class TelemetryLifecycleTests: XCTestCase {
    private var caches: URL!

    override func setUpWithError() throws {
        try super.setUpWithError()
        caches = FileManager.default.temporaryDirectory
            .appendingPathComponent("tday-telemetry-tests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: caches, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: caches)
        caches = nil
        try super.tearDownWithError()
    }

    private func write(_ relativePath: String) throws {
        let file = caches.appendingPathComponent(relativePath)
        try FileManager.default.createDirectory(
            at: file.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        try Data("x".utf8).write(to: file)
    }

    private func exists(_ relativePath: String) -> Bool {
        FileManager.default.fileExists(atPath: caches.appendingPathComponent(relativePath).path)
    }

    func testPurgeDeletesTheSdkWorkingDirectoryAndTheCrashReports() throws {
        try write("io.sentry/0a1b2c3d/envelopes/queued")
        try write("io.sentry/0a1b2c3d/app.state")
        try write("SentryCrash/Tday/Reports/crash-1.json")

        TelemetryLifecycle.purge(cachesDirectory: caches)

        XCTAssertFalse(exists("io.sentry"))
        XCTAssertFalse(exists("SentryCrash"))
    }

    func testPurgeLeavesEverythingElseInTheCachesDirectory() throws {
        try write("io.sentry/0a1b2c3d/envelopes/queued")
        try write("com.apple.nsurlsessiond/keep.bin")
        try write("keep.txt")

        TelemetryLifecycle.purge(cachesDirectory: caches)

        XCTAssertTrue(exists("com.apple.nsurlsessiond/keep.bin"))
        XCTAssertTrue(exists("keep.txt"))
    }

    func testPurgeIsHarmlessWhenThereIsNothingToDelete() {
        TelemetryLifecycle.purge(cachesDirectory: caches)
        TelemetryLifecycle.purge(cachesDirectory: caches)
        TelemetryLifecycle.purge(cachesDirectory: nil)
    }

    func testPurgeCleansBothDirectoriesEvenWhenOnlyOneExists() throws {
        try write("SentryCrash/Tday/Reports/crash-1.json")

        TelemetryLifecycle.purge(cachesDirectory: caches)

        XCTAssertFalse(exists("SentryCrash"))
    }
}
