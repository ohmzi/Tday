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

    func testGenericServerErrorsUseServerMessage() {
        XCTAssertEqual(
            userFacingMessage(for: APIError(message: "Internal Server Error", statusCode: 500)),
            "Server error. Please try again later."
        )
    }
}
