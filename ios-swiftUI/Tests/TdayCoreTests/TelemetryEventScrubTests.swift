import Sentry
import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// The scrubber applied to the SDK's own `Event` and `Breadcrumb`, ending in a golden event: one
/// that carries everything the SDK would attach on its own, serialised the way it is sent, and
/// searched for what must not be in it.
final class TelemetryEventScrubTests: XCTestCase {
    private let consentedAt = Date(timeIntervalSince1970: 1_800_000_000)

    private var context: TelemetryEventContext {
        TelemetryEventContext(
            appVersion: "0.8.0",
            mode: "server",
            secondsFromGMT: 3_600,
            languageCode: "de",
            consentedAt: consentedAt
        )
    }

    private func makeGoldenEvent() -> Event {
        let event = Event(level: .error)
        event.timestamp = consentedAt.addingTimeInterval(60)

        let user = User(userId: "5F3C0D2E-91AB-4C7D-8E2F-0A1B2C3D4E5F")
        user.ipAddress = "203.0.113.9"
        user.email = "alex@example.com"
        event.user = user
        event.serverName = "Alexs-iPhone.local"

        event.message = SentryMessage(
            formatted: "Sync failed for alex@example.com at https://tday.example.com/api/todo/9f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f"
        )
        event.exceptions = [
            Exception(value: "Could not reach tday.example.com (10.0.0.7)", type: "NSURLErrorDomain"),
        ]
        event.extra = ["duration_ms": 12_000, "note": "typed by alex@example.com"]
        event.tags = ["operation": "sync_replay"]

        let request = SentryRequest()
        request.url = "https://tday.example.com/api/todo"
        request.queryString = "title=Buy+milk"
        event.request = request

        event.context = [
            "device": [
                "name": "Alexs iPhone",
                "model": "iPhone15,2",
                "locale": "de_DE",
                "timezone": "Europe/Berlin",
                "memory_size": 6_000_000_000,
            ],
            "culture": ["locale": "de_DE", "timezone": "Europe/Berlin", "display_name": "Deutsch (Deutschland)"],
            "app": ["app_version": "0.8.0", "device_app_hash": "b6d2c1f09a77aa31"],
            "os": ["name": "iOS", "version": "17.5"],
            "user info": ["NSErrorFailingURLStringKey": "https://tday.example.com/api/todo"],
        ]

        let kept = Breadcrumb(level: .info, category: "tday")
        kept.message = "sync.replay"
        kept.setData(value: "Buy milk", key: "title")
        kept.setData(value: 3, key: "count")
        let tap = Breadcrumb(level: .info, category: "touch")
        tap.message = "Buy milk"
        event.breadcrumbs = [kept, tap]

        let image = DebugMeta()
        image.codeFile = "/private/var/containers/Bundle/Application/7A1C0FE3-0B2D-4A9C-8E5F-1A21B3C4D5E6/Tday.app/Tday"
        event.debugMeta = [image]

        return event
    }

    private func serialized(_ event: Event) throws -> String {
        let data = try JSONSerialization.data(withJSONObject: event.serialize())
        return String(decoding: data, as: UTF8.self)
    }

    // MARK: - The golden event

    func testNothingThatIdentifiesAPersonOrADeviceSurvives() throws {
        let scrubbed = try XCTUnwrap(TelemetryScrubber.scrub(makeGoldenEvent(), context: context))

        let json = try serialized(scrubbed)

        for leak in [
            "5F3C0D2E", "203.0.113.9", "alex@example.com", "Alexs-iPhone", "Alexs iPhone", "tday.example.com",
            "10.0.0.7", "9f1c2d3e", "Buy milk", "Buy+milk", "Europe/Berlin", "de_DE", "Deutsch",
            "device_app_hash", "b6d2c1f09a77aa31", "NSErrorFailingURLStringKey", "7A1C0FE3",
        ] {
            XCTAssertFalse(json.contains(leak), "\(leak) is still in the report: \(json)")
        }
        XCTAssertNil(scrubbed.user)
        XCTAssertNil(scrubbed.request)
    }

    func testWhatReproducesTheFailureSurvives() throws {
        let scrubbed = try XCTUnwrap(TelemetryScrubber.scrub(makeGoldenEvent(), context: context))

        let json = try serialized(scrubbed)

        for kept in ["NSURLErrorDomain", "iPhone15,2", "17.5", "0.8.0", "sync_replay", "sync.replay", "12000"] {
            XCTAssertTrue(json.contains(kept), "\(kept) was scrubbed away: \(json)")
        }
        XCTAssertEqual(scrubbed.level, .error)
    }

    func testEveryEventIsTaggedWithTheClientBuildModeOffsetAndLanguage() throws {
        let scrubbed = try XCTUnwrap(TelemetryScrubber.scrub(makeGoldenEvent(), context: context))

        XCTAssertEqual(scrubbed.tags?["client"], "ios")
        XCTAssertEqual(scrubbed.tags?["app_version"], "0.8.0")
        XCTAssertEqual(scrubbed.tags?["mode"], "server")
        XCTAssertEqual(scrubbed.tags?["tz_offset"], "UTC+1")
        XCTAssertEqual(scrubbed.tags?["locale_lang"], "de")
        // What the event already said is not replaced.
        XCTAssertEqual(scrubbed.tags?["operation"], "sync_replay")
    }

    func testTheMessageAndTheExceptionAreScrubbedInPlace() throws {
        let scrubbed = try XCTUnwrap(TelemetryScrubber.scrub(makeGoldenEvent(), context: context))

        XCTAssertEqual(scrubbed.message?.formatted, "Sync failed for [email] at [url]")
        XCTAssertEqual(scrubbed.exceptions?.first?.value, "Could not reach [host] ([ip])")
        XCTAssertEqual(scrubbed.exceptions?.first?.type, "NSURLErrorDomain")
    }

    // MARK: - What is dropped

    func testAFailureFromBeforeConsentIsDropped() {
        let event = makeGoldenEvent()
        event.timestamp = consentedAt.addingTimeInterval(-60)

        XCTAssertNil(TelemetryScrubber.scrub(event, context: context))
    }

    func testAnEventWithNoConsentDateIsNotHeldBackByTheClock() {
        var noConsentDate = context
        noConsentDate.consentedAt = nil
        let event = makeGoldenEvent()
        event.timestamp = Date(timeIntervalSince1970: 1)

        XCTAssertNotNil(TelemetryScrubber.scrub(event, context: noConsentDate))
    }

    func testATransactionIsDropped() {
        let event = makeGoldenEvent()
        event.type = "transaction"

        XCTAssertNil(TelemetryScrubber.scrub(event, context: context))
    }

    // MARK: - Breadcrumbs

    func testAStructuralBreadcrumbIsKeptWithoutItsTitle() throws {
        let crumb = Breadcrumb(level: .info, category: "api")
        crumb.message = "api.request"
        crumb.setData(value: "Buy milk", key: "title")
        crumb.setData(value: 204, key: "status")

        let kept = try XCTUnwrap(TelemetryScrubber.scrubBreadcrumb(crumb))

        XCTAssertEqual(kept.message, "api.request")
        XCTAssertNil(kept.data?["title"])
        XCTAssertEqual(kept.data?["status"] as? Int, 204)
    }

    func testAUiBreadcrumbIsDropped() {
        for category in ["touch", "ui.lifecycle", "device.event"] {
            XCTAssertNil(TelemetryScrubber.scrubBreadcrumb(Breadcrumb(level: .info, category: category)), category)
        }
    }

    // MARK: - Stack frames and images

    func testTheAppContainerIdIsRemovedFromBinaryPaths() throws {
        let event = makeGoldenEvent()
        let frame = Frame()
        frame.package = "/private/var/containers/Bundle/Application/7A1C0FE3-0B2D-4A9C-8E5F-1A21B3C4D5E6/Tday.app/Tday"
        let stacktrace = SentryStacktrace(frames: [frame], registers: [:])
        event.exceptions?.first?.stacktrace = stacktrace

        let scrubbed = try XCTUnwrap(TelemetryScrubber.scrub(event, context: context))

        XCTAssertEqual(
            scrubbed.exceptions?.first?.stacktrace?.frames.first?.package,
            "/private/var/containers/Bundle/Application/[id]/Tday.app/Tday"
        )
        XCTAssertEqual(
            scrubbed.debugMeta?.first?.codeFile,
            "/private/var/containers/Bundle/Application/[id]/Tday.app/Tday"
        )
    }
}
