import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

final class CacheMappersDateParsingTests: XCTestCase {
    func testParsesBackendMinutePrecisionLocalDateTimeAsUTC() {
        let parsed = parseOptionalDate("2026-05-20T21:00")

        XCTAssertNotNil(parsed)
        let calendar = Calendar(identifier: .iso8601)
        let components = calendar.dateComponents(in: TimeZone(secondsFromGMT: 0)!, from: parsed!)
        XCTAssertEqual(components.year, 2026)
        XCTAssertEqual(components.month, 5)
        XCTAssertEqual(components.day, 20)
        XCTAssertEqual(components.hour, 21)
        XCTAssertEqual(components.minute, 0)
    }
}
