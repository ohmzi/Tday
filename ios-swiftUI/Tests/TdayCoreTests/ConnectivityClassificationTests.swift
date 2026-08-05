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

    // MARK: - TLS trust decisions
    //
    // These pin the fail-closed rule. Previously, a certificate the system could not verify was
    // trusted on first use and pinned silently. Because a public-CA host never stores a pin (the
    // system-trusted branch clears it), that path was reachable on EVERY connection — so anyone
    // on the same network could present a self-signed certificate for the real server and be
    // accepted without a prompt.
    //
    // decideTrust is pure precisely so this is testable: a real SecTrust cannot be fabricated.

    func testUnknownCertificateIsRefusedRatherThanTrustedOnFirstUse() {
        let decision = NetworkConfiguration.decideTrust(
            fingerprint: "attacker-fingerprint",
            storedPin: nil,
            enrollmentExpecting: nil,
            isPrivateHost: true
        )
        XCTAssertEqual(decision, .rejectUnknown)
    }

    func testStoredPinIsAcceptedWhenItMatches() {
        let decision = NetworkConfiguration.decideTrust(
            fingerprint: "pinned",
            storedPin: "pinned",
            enrollmentExpecting: nil,
            isPrivateHost: true
        )
        XCTAssertEqual(decision, .accept)
    }

    func testChangedCertificateIsRejectedAsMismatch() {
        let decision = NetworkConfiguration.decideTrust(
            fingerprint: "different",
            storedPin: "pinned",
            enrollmentExpecting: nil,
            isPrivateHost: true
        )
        XCTAssertEqual(decision, .rejectMismatch)
    }

    func testApprovedFingerprintEnrolls() {
        let decision = NetworkConfiguration.decideTrust(
            fingerprint: "approved",
            storedPin: nil,
            enrollmentExpecting: "approved",
            isPrivateHost: true
        )
        XCTAssertEqual(decision, .enroll("approved"))
    }

    func testApprovalDoesNotTrustADifferentCertificate() {
        // The approval names one exact fingerprint, so swapping the certificate between the
        // prompt and the retry must still fail. Otherwise the confirm step is just delayed TOFU.
        let decision = NetworkConfiguration.decideTrust(
            fingerprint: "swapped-after-approval",
            storedPin: nil,
            enrollmentExpecting: "approved",
            isPrivateHost: true
        )
        XCTAssertEqual(decision, .rejectUnknown)
    }

    func testExistingPinWinsOverAnApproval() {
        let decision = NetworkConfiguration.decideTrust(
            fingerprint: "attacker",
            storedPin: "pinned",
            enrollmentExpecting: "attacker",
            isPrivateHost: true
        )
        XCTAssertEqual(decision, .rejectMismatch)
    }

    func testUnderivableFingerprintIsNeverAccepted() {
        // The old code fell through to .useCredential here.
        XCTAssertEqual(
            NetworkConfiguration.decideTrust(
                fingerprint: nil,
                storedPin: nil,
                enrollmentExpecting: nil,
                isPrivateHost: true
            ),
            .rejectUnknown
        )
        XCTAssertEqual(
            NetworkConfiguration.decideTrust(
                fingerprint: nil,
                storedPin: "pinned",
                enrollmentExpecting: nil,
                isPrivateHost: true
            ),
            .rejectUnknown
        )
        XCTAssertEqual(
            NetworkConfiguration.decideTrust(
                fingerprint: nil,
                storedPin: nil,
                enrollmentExpecting: "approved",
                isPrivateHost: true
            ),
            .rejectUnknown
        )
    }

    // MARK: Enrollment is private/LAN hosts only
    //
    // A certificate that fails system validation is only adoptable on a host that can't be
    // reached from the public internet. On a public hostname the same situation means the
    // certificate should have chained to a public CA and didn't — so the "Trust this
    // fingerprint" button the UI puts on .rejectUnknown IS the attack on hostile wifi.

    func testPublicHostWithUntrustedCertificateIsRefusedWithoutOfferingEnrollment() {
        // .rejectUntrustedPublic is the whole point: it is the one refusal the setup screen
        // does not attach a Trust button to.
        XCTAssertEqual(
            NetworkConfiguration.decideTrust(
                fingerprint: "attacker-on-public-wifi",
                storedPin: nil,
                enrollmentExpecting: nil,
                isPrivateHost: false
            ),
            .rejectUntrustedPublic
        )
        // No fingerprint derivable — still a flat refusal, still no enrollment.
        XCTAssertEqual(
            NetworkConfiguration.decideTrust(
                fingerprint: nil,
                storedPin: nil,
                enrollmentExpecting: nil,
                isPrivateHost: false
            ),
            .rejectUntrustedPublic
        )
    }

    func testPrivateHostWithUntrustedCertificateIsOfferedEnrollment() {
        // Self-signed LAN server: refused for now, but .rejectUnknown is what makes the UI show
        // the fingerprint and let the user adopt it deliberately.
        XCTAssertEqual(
            NetworkConfiguration.decideTrust(
                fingerprint: "lan-self-signed",
                storedPin: nil,
                enrollmentExpecting: nil,
                isPrivateHost: true
            ),
            .rejectUnknown
        )
        // And once the user has confirmed that exact fingerprint, it enrolls.
        XCTAssertEqual(
            NetworkConfiguration.decideTrust(
                fingerprint: "lan-self-signed",
                storedPin: nil,
                enrollmentExpecting: "lan-self-signed",
                isPrivateHost: true
            ),
            .enroll("lan-self-signed")
        )
    }

    func testPublicHostCannotEnrollEvenWithAnApprovalPending() {
        // Belt and braces: even if an approval were somehow recorded for a public host, the
        // host class alone must refuse it. No path exists that pins a new key for a public host.
        XCTAssertEqual(
            NetworkConfiguration.decideTrust(
                fingerprint: "approved",
                storedPin: nil,
                enrollmentExpecting: "approved",
                isPrivateHost: false
            ),
            .rejectUntrustedPublic
        )
    }

    func testPublicHostStillHonoursAnAlreadyEstablishedPin() {
        // An existing pin came from a deliberate approval and is strictly stronger than CA
        // validation, so it keeps working; a changed certificate still reads as a mismatch.
        XCTAssertEqual(
            NetworkConfiguration.decideTrust(
                fingerprint: "pinned",
                storedPin: "pinned",
                enrollmentExpecting: nil,
                isPrivateHost: false
            ),
            .accept
        )
        XCTAssertEqual(
            NetworkConfiguration.decideTrust(
                fingerprint: "swapped",
                storedPin: "pinned",
                enrollmentExpecting: nil,
                isPrivateHost: false
            ),
            .rejectMismatch
        )
    }

    func testEnrollmentApprovalIsConsumedSoItCannotBeReused() {
        let suiteName = "com.ohmz.tday.tests.trust.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defaults.removePersistentDomain(forName: suiteName)
        let secureStore = SecureStore(
            service: "com.ohmz.tday.tests.trust.secure-store.\(UUID().uuidString)",
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

        configuration.allowTrustEnrollment(host: "Tday.Example.com", expecting: "fp-1")
        // Host lookup is case-insensitive, and the approval is one-shot.
        XCTAssertNil(configuration.consumeTrustUnknown(host: "tday.example.com") ?? nil)
        configuration.cancelTrustEnrollment(host: "tday.example.com")
        XCTAssertNil(configuration.consumeTrustUnknown(host: "tday.example.com") ?? nil)
    }

    func testServerUnavailableResponsesAreConnectivityIssues() {
        // 500 = database down (backend up), 502/503/504 = backend container down behind a
        // live proxy — all are "server can't sync right now", treated the same as offline.
        let unavailableStatuses = [408, 500, 501, 502, 503, 504, 520, 521, 522, 523, 524]

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
            "The server isn't responding right now — it may be down or restarting. If this keeps happening, contact your administrator."
        )
    }

    func testDatabaseOutageFivehundredIsAConnectivityIssue() {
        // A 500 (e.g. the backend is up but the database is down) means sync can't happen,
        // so it must read as offline — keep the session, defer sync, show the offline notice.
        XCTAssertTrue(
            isLikelyConnectivityIssue(
                APIError(message: "Internal Server Error", statusCode: 500)
            )
        )
    }

    func testClientValidationErrorsAreNotConnectivityIssues() {
        // 4xx is a real client-side problem (bad request / validation), not an outage.
        XCTAssertFalse(
            isLikelyConnectivityIssue(
                APIError(message: "Unprocessable", statusCode: 422)
            )
        )
    }

    func testBackendUnavailableSplitsFromNoNetwork() {
        // A 5xx = the backend answered but is down → "server error" message, not "you're offline".
        for code in [500, 502, 503, 504] {
            XCTAssertTrue(
                isBackendUnavailableError(APIError(message: "down", statusCode: code)),
                "Expected HTTP \(code) to read as backend-down"
            )
        }
        // No-network states (transport error / no HTTP status) and non-5xx are NOT backend-down.
        XCTAssertFalse(isBackendUnavailableError(APIError(message: "no status", statusCode: nil)))
        XCTAssertFalse(isBackendUnavailableError(URLError(.notConnectedToInternet)))
        XCTAssertFalse(isBackendUnavailableError(APIError(message: "unauth", statusCode: 401)))
        XCTAssertFalse(isBackendUnavailableError(APIError(message: "timeout", statusCode: 408)))
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
            "The server isn't responding right now — it may be down or restarting. If this keeps happening, contact your administrator."
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
            "Your app is out of date. Please update to the latest version to continue."
        )
        XCTAssertEqual(
            userFacingMessage(
                for: APIError(
                    message: "Server update required",
                    statusCode: 409,
                    reason: "server_update_required"
                )
            ),
            "This app is newer than the server. Ask your administrator to update the server."
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
