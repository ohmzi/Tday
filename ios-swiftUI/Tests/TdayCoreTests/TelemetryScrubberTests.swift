import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// The rules that decide what a crash report may say, on plain strings and dictionaries. The same
/// rules applied to the SDK's own `Event` are in `TelemetryEventScrubTests`.
final class TelemetryScrubberTests: XCTestCase {
    // MARK: - Text

    func testRedactsUrlsWithTheirHostPathAndQuery() {
        XCTAssertEqual(
            TelemetryScrubber.redact("GET https://tday.example.com:8443/api/todo/42?token=abc failed"),
            "GET [url] failed"
        )
        XCTAssertEqual(TelemetryScrubber.redact("socket wss://tday.example.com/ws closed"), "socket [url] closed")
    }

    func testRedactsEmailAddresses() {
        XCTAssertEqual(
            TelemetryScrubber.redact("no account for alex.smith+tday@example.co.uk"),
            "no account for [email]"
        )
    }

    func testRedactsIpAddresses() {
        XCTAssertEqual(TelemetryScrubber.redact("connect to 192.168.1.20:8080 timed out"), "connect to [ip] timed out")
        XCTAssertEqual(TelemetryScrubber.redact("peer fe80::1ff:fe23:4567:890a refused"), "peer [ip] refused")
        XCTAssertEqual(
            TelemetryScrubber.redact("peer 2001:0db8:85a3:0000:0000:8a2e:0370:7334 refused"),
            "peer [ip] refused"
        )
    }

    func testRedactsHostnamesWithoutAScheme() {
        XCTAssertEqual(TelemetryScrubber.redact("could not resolve nas.home.lan"), "could not resolve [host]")
    }

    func testRedactsHostnamesWrittenWithCapitals() {
        XCTAssertEqual(
            TelemetryScrubber.redact("Could not connect to NAS.Example.com on port 443"),
            "Could not connect to [host] on port 443"
        )
        XCTAssertEqual(TelemetryScrubber.redact("ping Alexs-iPhone.local failed"), "ping [host] failed")
        XCTAssertEqual(TelemetryScrubber.redact("no route to Home-NAS.lan"), "no route to [host]")
        XCTAssertEqual(TelemetryScrubber.redact("tailnet Alex-Mac.Tail1234.ts.net down"), "tailnet [host] down")
        XCTAssertEqual(TelemetryScrubber.redact("lookup Tday.Example.co.uk failed"), "lookup [host] failed")
    }

    func testRedactsIdentifiersAndLongNumbers() {
        XCTAssertEqual(
            TelemetryScrubber.redact("list 9f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f is gone"),
            "list [id] is gone"
        )
        XCTAssertEqual(TelemetryScrubber.redact("todo cjld2cjxh0000qzrmn831i7rn is gone"), "todo [id] is gone")
        XCTAssertEqual(TelemetryScrubber.redact("order 12345678 failed"), "order [num] failed")
    }

    func testRedactsDatabaseFragments() {
        XCTAssertEqual(
            TelemetryScrubber.redact("Key (email)=(alex@example.com) already exists."),
            "Key ([column])=([value]) already exists."
        )
        XCTAssertEqual(TelemetryScrubber.redact("open jdbc:postgresql://db:5432/tday failed"), "open [jdbc] failed")
    }

    func testLeavesTheTextOfARealCrashAlone() {
        for text in [
            "Fatal error: Unexpectedly found nil while unwrapping an Optional value",
            "Index out of range: file Swift/Array.swift, line 12",
            "Tday.APIError code 3",
            "HTTP 503 after 2 attempts",
            "build 0.8.0 (62)",
            "Swift.DecodingError.keyNotFound in Foundation.JSONDecoder",
            "Tday.SyncEngine.Replay failed in Sync.swift line 40",
            "Reading Info.plist or Localizable.strings failed",
            "Cannot find Tday.Todo.Recurrence in scope",
        ] {
            XCTAssertEqual(TelemetryScrubber.redact(text), text)
        }
    }

    func testAMessageIsCutToThreeHundredCharacters() {
        let scrubbed = TelemetryScrubber.scrubMessage(String(repeating: "x", count: 1_000))

        XCTAssertEqual(scrubbed.count, TelemetryScrubber.maxMessageLength)
        XCTAssertEqual(TelemetryScrubber.maxMessageLength, 300)
    }

    func testAnAddressIsRedactedBeforeTheMessageIsCut() {
        // The address straddles the 300th character: cut first, it would be left as a stub no
        // pattern recognises.
        let message = String(repeating: "x", count: 290) + " alex.smith@example.com"

        let scrubbed = TelemetryScrubber.scrubMessage(message)

        XCTAssertFalse(scrubbed.contains("alex"))
        XCTAssertTrue(scrubbed.hasSuffix("[email]"))
    }

    func testAVeryLongMessageIsStillScrubbedAndCut() {
        let scrubbed = TelemetryScrubber.scrubMessage(String(repeating: "lowercase ", count: 50_000))

        XCTAssertEqual(scrubbed.count, TelemetryScrubber.maxMessageLength)
    }

    func testABinaryPathLosesOnlyTheContainerId() {
        XCTAssertEqual(
            TelemetryScrubber.redactContainerID(
                "/private/var/containers/Bundle/Application/7A1C0FE3-0B2D-4A9C-8E5F-1A21B3C4D5E6/Tday.app/Tday"
            ),
            "/private/var/containers/Bundle/Application/[id]/Tday.app/Tday"
        )
        // What `redact` would take for a host is a framework here, and is kept.
        XCTAssertEqual(TelemetryScrubber.redactContainerID("/usr/lib/system/libdispatch.dylib"), "/usr/lib/system/libdispatch.dylib")
    }

    // MARK: - Tags

    func testTimeZoneOffsetLabel() {
        XCTAssertEqual(TelemetryScrubber.tzOffsetLabel(secondsFromGMT: 0), "UTC")
        XCTAssertEqual(TelemetryScrubber.tzOffsetLabel(secondsFromGMT: 7_200), "UTC+2")
        XCTAssertEqual(TelemetryScrubber.tzOffsetLabel(secondsFromGMT: -18_000), "UTC-5")
        XCTAssertEqual(TelemetryScrubber.tzOffsetLabel(secondsFromGMT: 19_800), "UTC+5:30")
        XCTAssertEqual(TelemetryScrubber.tzOffsetLabel(secondsFromGMT: 20_700), "UTC+5:45")
        XCTAssertEqual(TelemetryScrubber.tzOffsetLabel(secondsFromGMT: -34_200), "UTC-9:30")
    }

    func testLanguageTagIsTwoLowercaseLettersOrNothing() {
        XCTAssertEqual(TelemetryScrubber.languageTag(from: "de"), "de")
        XCTAssertEqual(TelemetryScrubber.languageTag(from: "ZH"), "zh")
        XCTAssertNil(TelemetryScrubber.languageTag(from: "fil"))
        XCTAssertNil(TelemetryScrubber.languageTag(from: "e1"))
        XCTAssertNil(TelemetryScrubber.languageTag(from: ""))
        XCTAssertNil(TelemetryScrubber.languageTag(from: nil))
    }

    func testEveryEventCarriesTheClientBuildModeOffsetAndLanguage() {
        let context = TelemetryEventContext(
            appVersion: "0.8.0",
            mode: "local",
            secondsFromGMT: 7_200,
            languageCode: "de",
            consentedAt: nil
        )

        XCTAssertEqual(
            context.tags,
            [
                "client": "ios",
                "app_version": "0.8.0",
                "mode": "local",
                "tz_offset": "UTC+2",
                "locale_lang": "de",
            ]
        )
    }

    func testAnUnusableLanguageLeavesTheTagOutRatherThanGuessing() {
        let context = TelemetryEventContext(
            appVersion: "0.8.0",
            mode: "server",
            secondsFromGMT: 0,
            languageCode: "fil",
            consentedAt: nil
        )

        XCTAssertNil(context.tags["locale_lang"])
        XCTAssertEqual(context.tags["tz_offset"], "UTC")
    }

    // MARK: - What is sent at all

    func testATransactionIsNeverSent() {
        XCTAssertFalse(TelemetryScrubber.shouldSend(timestamp: nil, type: "transaction", consentedAt: nil))
        XCTAssertTrue(TelemetryScrubber.shouldSend(timestamp: nil, type: nil, consentedAt: nil))
    }

    func testAFailureFromBeforeConsentIsNeverSent() {
        let consentedAt = Date(timeIntervalSince1970: 1_800_000_000)

        XCTAssertFalse(
            TelemetryScrubber.shouldSend(timestamp: consentedAt.addingTimeInterval(-1), type: nil, consentedAt: consentedAt)
        )
        XCTAssertTrue(TelemetryScrubber.shouldSend(timestamp: consentedAt, type: nil, consentedAt: consentedAt))
        XCTAssertTrue(
            TelemetryScrubber.shouldSend(timestamp: consentedAt.addingTimeInterval(1), type: nil, consentedAt: consentedAt)
        )
        // An event with no time is happening now.
        XCTAssertTrue(TelemetryScrubber.shouldSend(timestamp: nil, type: nil, consentedAt: consentedAt))
    }

    // MARK: - Breadcrumbs

    func testOnlyTheStructuralBreadcrumbCategoriesAreKept() {
        for category in ["tday", "api", "error", "app.lifecycle", "device.connectivity"] {
            XCTAssertTrue(TelemetryScrubber.isAllowedBreadcrumbCategory(category), category)
        }
        for category in ["ui.lifecycle", "touch", "device.event", "device.orientation", "http", "console", "navigation"] {
            XCTAssertFalse(TelemetryScrubber.isAllowedBreadcrumbCategory(category), category)
        }
    }

    // MARK: - Contexts

    func testContextsLoseTheLocaleTheZoneTheDeviceNameAndTheDeviceHash() {
        let contexts: [String: [String: Any]] = [
            "device": [
                "name": "Alex's iPhone",
                "model": "iPhone15,2",
                "locale": "de_DE",
                "timezone": "Europe/Berlin",
                "memory_size": 6_000_000_000,
                "free_memory": 1_000_000,
            ],
            "culture": ["locale": "de_DE", "timezone": "Europe/Berlin", "calendar": "Gregorian Calendar"],
            "app": ["app_version": "0.8.0", "device_app_hash": "b6d2c1f09a", "build_type": "app store"],
            "os": ["name": "iOS", "version": "17.5"],
            "user info": ["NSErrorFailingURLStringKey": "https://tday.example.com/api"],
        ]

        let scrubbed = TelemetryScrubber.scrubContexts(contexts)

        XCTAssertNil(scrubbed["culture"])
        XCTAssertNil(scrubbed["user info"])
        XCTAssertNil(scrubbed["device"]?["name"])
        XCTAssertNil(scrubbed["device"]?["locale"])
        XCTAssertNil(scrubbed["device"]?["timezone"])
        XCTAssertNil(scrubbed["app"]?["device_app_hash"])
    }

    func testContextsKeepWhatReproducesAFailure() {
        let contexts: [String: [String: Any]] = [
            "device": ["name": "Alex's iPhone", "model": "iPhone15,2", "memory_size": 6_000_000_000],
            "app": ["app_version": "0.8.0", "device_app_hash": "b6d2c1f09a"],
            "os": ["name": "iOS", "version": "17.5"],
        ]

        let scrubbed = TelemetryScrubber.scrubContexts(contexts)

        XCTAssertEqual(scrubbed["device"]?["model"] as? String, "iPhone15,2")
        XCTAssertEqual(scrubbed["device"]?["memory_size"] as? Int, 6_000_000_000)
        XCTAssertEqual(scrubbed["app"]?["app_version"] as? String, "0.8.0")
        XCTAssertEqual(scrubbed["os"]?["version"] as? String, "17.5")
    }

    // MARK: - Errors

    func testTransportFailuresAreNoiseAndEverythingElseIsNot() {
        for code in [
            URLError.Code.notConnectedToInternet, .timedOut, .networkConnectionLost, .cancelled, .cannotFindHost,
            .secureConnectionFailed,
        ] {
            XCTAssertTrue(
                TelemetryScrubber.isConnectivityNoise(domain: NSURLErrorDomain, code: code.rawValue),
                "\(code)"
            )
        }
        XCTAssertFalse(
            TelemetryScrubber.isConnectivityNoise(domain: NSURLErrorDomain, code: URLError.Code.badServerResponse.rawValue)
        )
        XCTAssertFalse(
            TelemetryScrubber.isConnectivityNoise(domain: NSCocoaErrorDomain, code: URLError.Code.timedOut.rawValue)
        )
    }
}
