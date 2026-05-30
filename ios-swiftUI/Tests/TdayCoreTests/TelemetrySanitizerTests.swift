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

    func testClampsTraceSampleRates() {
        XCTAssertEqual(TdayTelemetry.traceSampleRate(rawValue: "0.25", fallback: 1).doubleValue, 0.25)
        XCTAssertEqual(TdayTelemetry.traceSampleRate(rawValue: "5", fallback: 0.2).doubleValue, 1.0)
        XCTAssertEqual(TdayTelemetry.traceSampleRate(rawValue: "nope", fallback: 0.2).doubleValue, 0.2)
    }

    func testRedactsSensitiveLabelsAndTokenShapedValues() {
        XCTAssertEqual(TdayTelemetry.safeLabel("alex@example.com"), "redacted")
        XCTAssertEqual(TdayTelemetry.safeLabel("https://example.com/api/todo/123"), "redacted")
        XCTAssertEqual(TdayTelemetry.safeLabel("cjld2cjxh0000qzrmn831i7rn"), "id")
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
