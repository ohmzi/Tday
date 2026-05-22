import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

final class ConnectivityClassificationTests: XCTestCase {
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

    func testGenericServerErrorsAreNotConnectivityIssues() {
        XCTAssertFalse(
            isLikelyConnectivityIssue(
                APIError(message: "Internal Server Error", statusCode: 500)
            )
        )
    }
}
