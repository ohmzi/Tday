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
}
