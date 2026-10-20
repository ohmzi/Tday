import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

final class TelemetrySanitizerTests: XCTestCase {
    func testSanitizesIdsAndQueryStrings() {
        XCTAssertEqual(
            TdayTelemetry.sanitizePath("/api/list/list-123?token=secret"),
            "/api/list/:id"
        )
        XCTAssertEqual(
            TdayTelemetry.sanitizePath("/en/app/list/list-123/Groceries"),
            "/:locale/app/list/:id/:value"
        )
        XCTAssertEqual(
            TdayTelemetry.sanitizePath("/:locale/app/list/:id"),
            "/:locale/app/list/:id"
        )
    }

    func testRedactsSensitiveLabelsAndTokenShapedValues() {
        XCTAssertEqual(TdayTelemetry.safeLabel("alex@example.com"), "redacted")
        XCTAssertEqual(TdayTelemetry.safeLabel("https://example.com/api/todo/123"), "redacted")
        XCTAssertEqual(TdayTelemetry.safeLabel("cjld2cjxh0000qzrmn831i7rn"), "id")
    }

    func testAReportedErrorKeepsOnlyItsDomainAndCode() throws {
        let failed = NSError(
            domain: NSURLErrorDomain,
            code: URLError.Code.badServerResponse.rawValue,
            userInfo: [
                "NSErrorFailingURLStringKey": "https://tday.example.com/api/todo/42",
                NSLocalizedDescriptionKey: "Could not reach alex@example.com",
            ]
        )

        let reported = try XCTUnwrap(TdayTelemetry.reportableError(failed))

        XCTAssertEqual(reported.domain, NSURLErrorDomain)
        XCTAssertEqual(reported.code, URLError.Code.badServerResponse.rawValue)
        XCTAssertTrue(reported.userInfo.isEmpty)
    }

    func testASwiftErrorIsReportedByDomainAndCode() throws {
        struct Failure: Error {}

        let reported = try XCTUnwrap(TdayTelemetry.reportableError(Failure()))

        XCTAssertTrue(reported.userInfo.isEmpty)
        XCTAssertFalse(reported.domain.isEmpty)
    }

    func testATransportFailureIsNotReported() {
        XCTAssertNil(TdayTelemetry.reportableError(URLError(.notConnectedToInternet)))
        XCTAssertNil(TdayTelemetry.reportableError(URLError(.timedOut)))
    }

    func testSanitizesRouteLikeDataByKeyAndRedactsSensitiveFields() {
        XCTAssertEqual(
            TdayTelemetry.safeDataValue(key: "route", value: "https://example.com/api/list/list-123?token=secret") as? String,
            "/api/list/:id"
        )
        XCTAssertEqual(
            TdayTelemetry.safeDataValue(key: "from", value: "/en/app/list/list-123") as? String,
            "/:locale/app/list/:id"
        )
        XCTAssertEqual(TdayTelemetry.safeDataValue(key: "email", value: "alex@example.com") as? String, "redacted")
        XCTAssertEqual(TdayTelemetry.safeDataValue(key: "authorization", value: "Bearer secret") as? String, "redacted")
    }
}
