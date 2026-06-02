import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

final class ConnectivityClassificationTests: XCTestCase {
    func testNetworkConfigurationDisablesCachingForApiRequests() {
        let suiteName = "com.ohmz.tday.tests.network.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defaults.removePersistentDomain(forName: suiteName)
        let secureStore = SecureStore(
            service: "com.ohmz.tday.tests.network.secure-store.\(UUID().uuidString)",
            defaults: defaults
        )
        let configuration = NetworkConfiguration(
            secureStore: secureStore,
            serverURLState: ServerURLState(currentURL: URL(string: "https://tday.example.com")),
            cookieStore: CookieStore(secureStore: secureStore)
        )

        defer {
            configuration.session.invalidateAndCancel()
            configuration.probeSession.invalidateAndCancel()
            secureStore.clearAllUserValues()
            defaults.removePersistentDomain(forName: suiteName)
        }

        let headers = configuration.defaultHeaders()
        XCTAssertEqual(headers["Cache-Control"], "no-store")
        XCTAssertEqual(headers["Pragma"], "no-cache")
        XCTAssertEqual(configuration.session.configuration.requestCachePolicy, .reloadIgnoringLocalAndRemoteCacheData)
        XCTAssertNil(configuration.session.configuration.urlCache)
        XCTAssertEqual(configuration.probeSession.configuration.requestCachePolicy, .reloadIgnoringLocalAndRemoteCacheData)
        XCTAssertNil(configuration.probeSession.configuration.urlCache)
    }

    func testServerUnavailableResponsesAreConnectivityIssues() {
        let unavailableStatuses = [408, 502, 503, 504, 520, 521, 522, 523, 524]

        for statusCode in unavailableStatuses {
            XCTAssertTrue(
                isLikelyConnectivityIssue(
                    APIError(message: "Server unavailable", statusCode: statusCode)
                ),
                "Expected HTTP \(statusCode) to be treated as offline"
            )
        }
    }

    func testServerUnavailableResponsesUseConnectionMessage() {
        XCTAssertEqual(
            userFacingMessage(for: APIError(message: "Web server is down", statusCode: 521)),
            "Connection error. Check your internet and try again."
        )
    }

    func testGenericServerErrorsAreNotConnectivityIssues() {
        XCTAssertFalse(
            isLikelyConnectivityIssue(
                APIError(message: "Internal Server Error", statusCode: 500)
            )
        )
    }

    func testUnauthorizedResponsesAreRecoverableSessionIssues() {
        XCTAssertTrue(
            isSessionAuthenticationIssue(
                APIError(message: "Unauthorized", statusCode: 401)
            )
        )
        XCTAssertFalse(
            isLikelyConnectivityIssue(
                APIError(message: "Unauthorized", statusCode: 401)
            )
        )
    }

    func testGenericServerErrorsUseServerMessage() {
        XCTAssertEqual(
            userFacingMessage(for: APIError(message: "Internal Server Error", statusCode: 500)),
            "Server error. Please try again later."
        )
    }

    func testVersionGateErrorsUseUpdateMessages() {
        XCTAssertEqual(
            userFacingMessage(
                for: APIError(
                    message: "Update required",
                    statusCode: 426,
                    reason: "app_update_required"
                )
            ),
            "This version of the app is out of date. Please update to continue."
        )
        XCTAssertEqual(
            userFacingMessage(
                for: APIError(
                    message: "Server update required",
                    statusCode: 409,
                    reason: "server_update_required"
                )
            ),
            "The server needs to be updated before this app can continue."
        )
    }

    func testVersionComparisonAndEmptyUpdateURLFallback() {
        XCTAssertEqual(AppViewModel.compareVersions("1.44.0", "1.43.9"), 1)
        XCTAssertEqual(AppViewModel.compareVersions("1.44.0", "1.44.0"), 0)
        XCTAssertEqual(AppViewModel.compareVersions("1.43.9", "1.44.0"), -1)
        XCTAssertNil(AppViewModel.bundleUpdateURL())
    }

    func testMobileSyncStatusFormatsLocalWorkspace() {
        let status = MobileSyncStatus(
            dataMode: .local,
            isOffline: true,
            isManualSyncing: true,
            pendingMutationCount: 3,
            lastSuccessfulSyncEpochMs: 1_000,
            lastSyncAttemptEpochMs: 2_000
        )

        XCTAssertTrue(status.isLocalMode)
        XCTAssertEqual(status.title, "Local workspace")
        XCTAssertEqual(status.statusText, "On this device only")
    }

    func testMobileSyncStatusFormatsServerStates() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let now = date(year: 2026, month: 6, day: 2, hour: 16, minute: 0, calendar: calendar)
        let syncedAt = date(year: 2026, month: 6, day: 2, hour: 14, minute: 30, calendar: calendar)
        let attemptedAt = date(year: 2026, month: 6, day: 1, hour: 9, minute: 15, calendar: calendar)

        let synced = MobileSyncStatus(
            dataMode: .server,
            lastSuccessfulSyncEpochMs: syncedAt.epochMilliseconds,
            lastSyncAttemptEpochMs: syncedAt.epochMilliseconds
        )
        XCTAssertEqual(synced.title, "Server sync")
        XCTAssertEqual(synced.statusText, "Synced")
        XCTAssertEqual(synced.lastSyncedText(now: now, calendar: calendar), "2:30 PM")
        XCTAssertNil(synced.lastAttemptText(now: now, calendar: calendar))

        let neverSynced = MobileSyncStatus(dataMode: .server)
        XCTAssertEqual(neverSynced.statusText, "Ready to sync")
        XCTAssertEqual(neverSynced.lastSyncedText(now: now, calendar: calendar), "Not yet")

        let offline = MobileSyncStatus(
            dataMode: .server,
            isOffline: true,
            pendingMutationCount: 2,
            lastSyncAttemptEpochMs: attemptedAt.epochMilliseconds
        )
        XCTAssertEqual(offline.statusText, "Offline. Changes will sync when connection returns.")
        XCTAssertEqual(offline.pendingText, "2 changes waiting")
        XCTAssertEqual(offline.lastAttemptText(now: now, calendar: calendar), "Jun 1, 9:15 AM")

        let syncing = MobileSyncStatus(dataMode: .server, isManualSyncing: true)
        XCTAssertEqual(syncing.statusText, "Syncing now")
    }

    func testMobileSyncStatusBuildsFromCacheMetadata() {
        let state = OfflineSyncState(
            lastSuccessfulSyncEpochMs: 4_000,
            lastSyncAttemptEpochMs: 5_000,
            pendingMutations: [
                PendingMutationRecord(
                    mutationId: "mutation-1",
                    kind: .createTodo,
                    targetId: "local-todo-1",
                    timestampEpochMs: 1,
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
                    iconKey: nil
                )
            ]
        )

        let serverStatus = MobileSyncStatus(dataMode: .server, state: state)
        XCTAssertEqual(serverStatus.pendingMutationCount, 1)
        XCTAssertEqual(serverStatus.lastSuccessfulSyncEpochMs, 4_000)
        XCTAssertEqual(serverStatus.lastSyncAttemptEpochMs, 5_000)

        let localStatus = MobileSyncStatus(dataMode: .local, state: state)
        XCTAssertEqual(localStatus.pendingMutationCount, 0)
        XCTAssertEqual(localStatus.lastSuccessfulSyncEpochMs, 0)
        XCTAssertEqual(localStatus.lastSyncAttemptEpochMs, 0)
    }

    private func date(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        calendar: Calendar
    ) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day, hour: hour, minute: minute))!
    }
}
