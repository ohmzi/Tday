import XCTest
@testable import Tday

final class ApiModelContractTests: XCTestCase {
    func testTodoDTOAcceptsSharedContractFields() throws {
        let json = """
        {
          "id": "todo-1",
          "title": "Ship the thing",
          "description": null,
          "pinned": false,
          "priority": "High",
          "due": "2026-05-22T18:00:00Z",
          "rrule": null,
          "timeZone": "America/Toronto",
          "instanceDate": null,
          "completed": false,
          "order": 4,
          "listID": "list-1",
          "userID": "user-1",
          "updatedAt": "2026-05-22T17:30:00Z",
          "createdAt": "2026-05-21T12:00:00Z"
        }
        """.data(using: .utf8)!

        let dto = try JSONDecoder().decode(TodoDTO.self, from: json)

        XCTAssertEqual(dto.timeZone, "America/Toronto")
        XCTAssertEqual(dto.order, 4)
        XCTAssertEqual(dto.userID, "user-1")
    }

    func testPromoteFloaterContractRoundTrips() throws {
        let request = PromoteFloaterRequest(due: "2026-07-01T09:00:00Z", rrule: nil)
        let encoded = try JSONEncoder().encode(request)
        let object = try XCTUnwrap(
            try JSONSerialization.jsonObject(with: encoded) as? [String: Any]
        )
        XCTAssertEqual(object["due"] as? String, "2026-07-01T09:00:00Z")

        let responseJson = """
        {
          "message": "floater promoted",
          "todo": {
            "id": "todo-9",
            "title": "Paint shelf",
            "description": null,
            "pinned": true,
            "priority": "Medium",
            "due": "2026-07-01T09:00:00",
            "rrule": "RRULE:FREQ=WEEKLY;INTERVAL=1",
            "timeZone": "UTC",
            "instanceDate": null,
            "completed": false,
            "order": 0,
            "listID": null,
            "userID": "user-1",
            "updatedAt": "2026-06-30T10:00:00",
            "createdAt": "2026-06-01T10:00:00"
          }
        }
        """.data(using: .utf8)!
        let response = try JSONDecoder().decode(PromoteFloaterResponse.self, from: responseJson)
        XCTAssertEqual(response.todo?.id, "todo-9")
        XCTAssertEqual(response.todo?.rrule, "RRULE:FREQ=WEEKLY;INTERVAL=1")
    }

    func testDemoteTodoResponseAcceptsSharedContractShape() throws {
        let json = """
        {
          "message": "todo demoted",
          "floater": {
            "id": "floater-7",
            "title": "Fix the bike",
            "description": null,
            "pinned": false,
            "priority": "Low",
            "completed": false,
            "order": 0,
            "listID": null,
            "userID": "user-1",
            "updatedAt": "2026-06-30T10:00:00",
            "createdAt": "2026-06-01T10:00:00"
          }
        }
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(DemoteTodoResponse.self, from: json)

        XCTAssertEqual(response.floater?.id, "floater-7")
        XCTAssertNil(response.floater?.listID)
    }

    func testSummaryResponseAcceptsFallbackOnlyContract() throws {
        let json = """
        {
          "summary": null,
          "source": "logic",
          "mode": "TODAY",
          "taskCount": 0,
          "generatedAt": null,
          "fallbackReason": "disabled",
          "reason": "disabled"
        }
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(TodoSummaryResponse.self, from: json)

        XCTAssertNil(response.summary)
        XCTAssertEqual(response.source, "logic")
        XCTAssertEqual(response.fallbackReason, "disabled")
        XCTAssertEqual(response.reason, "disabled")
    }

    func testListDeleteContractSupportsBulkPayload() throws {
        let payload = DeleteListRequest(id: nil, ids: ["list-1", "list-2"])
        let data = try JSONEncoder().encode(payload)
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])

        XCTAssertNil(object["id"])
        XCTAssertEqual(object["ids"] as? [String], ["list-1", "list-2"])
    }

    func testListDetailResponseAcceptsSharedContractShape() throws {
        let json = """
        {
          "list": {
            "id": "list-1",
            "name": "Home",
            "color": "#3B82F6",
            "todoCount": 1,
            "iconKey": "home",
            "userID": "user-1",
            "updatedAt": null,
            "createdAt": null
          },
          "todos": [
            {
              "id": "todo-1",
              "title": "Take out trash",
              "priority": "Low",
              "due": "2026-05-22T18:00:00Z",
              "completed": false,
              "order": 0
            }
          ]
        }
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(ListDetailResponse.self, from: json)

        XCTAssertEqual(response.list.id, "list-1")
        XCTAssertEqual(response.todos.first?.id, "todo-1")
    }

    func testDeleteListResponseAcceptsDeletedIds() throws {
        let json = """
        {
          "message": "2 lists deleted",
          "deletedIds": ["list-1", "list-2"]
        }
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(DeleteListResponse.self, from: json)

        XCTAssertEqual(response.message, "2 lists deleted")
        XCTAssertEqual(response.deletedIds, ["list-1", "list-2"])
    }

    func testListResponsesDefaultMissingSharedArraysToEmpty() throws {
        let detailData = """
        {
          "list": {
            "id": "list-1",
            "name": "Home",
            "color": null,
            "todoCount": 0,
            "iconKey": null,
            "userID": null,
            "updatedAt": null,
            "createdAt": null
          }
        }
        """.data(using: .utf8)!
        let deleteData = """
        {
          "message": "list deleted"
        }
        """.data(using: .utf8)!

        let detail = try JSONDecoder().decode(ListDetailResponse.self, from: detailData)
        let delete = try JSONDecoder().decode(DeleteListResponse.self, from: deleteData)

        XCTAssertEqual(detail.todos, [])
        XCTAssertEqual(delete.deletedIds, [])
    }

    func testProbeCompatibilityPayloadAcceptsExactMode() throws {
        let data = """
        {
          "appVersion": "1.44.0",
          "updateRequired": true,
          "compatibilityMode": "exact"
        }
        """.data(using: .utf8)!

        let payload = try JSONDecoder().decode(ProbeCompatibilityPayload.self, from: data)

        XCTAssertEqual(payload.appVersion, "1.44.0")
        XCTAssertTrue(payload.updateRequired)
        XCTAssertEqual(payload.compatibilityMode, "exact")
    }

    func testMobileProbeResponseAcceptsPlainAppVersion() throws {
        let data = """
        {
          "service": "tday",
          "probe": "ok",
          "version": "1",
          "serverTime": "2026-05-30T00:00:00Z",
          "appVersion": "1.44.0",
          "encryptedCompatibility": null
        }
        """.data(using: .utf8)!

        let payload = try JSONDecoder().decode(MobileProbeResponse.self, from: data)

        XCTAssertEqual(payload.appVersion, "1.44.0")
        XCTAssertNil(payload.encryptedCompatibility)
    }

    // MARK: - Security alerts

    private func alert(id: String, createdAt: String, detail: String = "Abuse block applied") -> SecurityAlertDTO {
        SecurityAlertDTO(
            id: id,
            type: "abuse_block_applied",
            detail: detail,
            suppressedCount: 0,
            pushed: true,
            createdAt: createdAt
        )
    }

    func testSecurityAlertsResponseDecodesAdminPayload() throws {
        let data = """
        {
          "alerts": [
            {
              "id": "alert-2",
              "type": "auth_alert_lockout_burst",
              "detail": "4 lockouts in 10 minutes",
              "suppressedCount": 3,
              "pushed": true,
              "createdAt": "2026-08-05T12:00"
            }
          ]
        }
        """.data(using: .utf8)!

        let payload = try JSONDecoder().decode(SecurityAlertsResponse.self, from: data)

        XCTAssertEqual(payload.alerts.count, 1)
        XCTAssertEqual(payload.alerts[0].suppressedCount, 3)
        XCTAssertEqual(payload.alerts[0].createdAt, "2026-08-05T12:00")
    }

    /// The backend emits `LocalDateTime.toString()`, which drops the seconds when they are zero
    /// and never carries a timezone — a fixed "…HH:mm:ss" formatter would fail on half of these.
    func testSecurityAlertCreatedAtParsesEveryBackendShape() {
        let secondsOmitted = SecurityAlertNotifier.parseCreatedAt("2026-08-05T12:00")
        let wholeSeconds = SecurityAlertNotifier.parseCreatedAt("2026-08-05T12:00:00")
        let fractional = SecurityAlertNotifier.parseCreatedAt("2026-08-05T12:00:03.123")

        XCTAssertNotNil(secondsOmitted)
        XCTAssertNotNil(wholeSeconds)
        XCTAssertNotNil(fractional)
        // Seconds-omitted and explicit-zero-seconds must land on the same instant, and both are
        // read as UTC (the value carries no offset).
        XCTAssertEqual(secondsOmitted, wholeSeconds)
        XCTAssertEqual(secondsOmitted?.timeIntervalSince1970, 1785931200)
        XCTAssertEqual(fractional?.timeIntervalSince1970 ?? 0, 1785931203.123, accuracy: 0.001)
        XCTAssertNil(SecurityAlertNotifier.parseCreatedAt("not-a-date"))
    }

    func testFirstPollSeedsBaselineWithoutNotifying() {
        let alerts = [
            alert(id: "a3", createdAt: "2026-08-05T12:00"),
            alert(id: "a2", createdAt: "2026-08-05T11:00"),
        ]

        XCTAssertEqual(SecurityAlertNotifier.unseenAlerts(in: alerts, since: nil), [])
        XCTAssertEqual(SecurityAlertNotifier.newestMarker(in: alerts)?.id, "a3")
    }

    func testOnlyAlertsNewerThanTheMarkerAreUnseen() {
        let alerts = [
            alert(id: "a4", createdAt: "2026-08-05T13:30:05"),
            alert(id: "a3", createdAt: "2026-08-05T12:00"),
            alert(id: "a2", createdAt: "2026-08-05T11:00"),
        ]
        let marker = SecurityAlertMarker(
            id: "a3",
            createdAt: SecurityAlertNotifier.parseCreatedAt("2026-08-05T12:00")
        )

        let unseen = SecurityAlertNotifier.unseenAlerts(in: alerts, since: marker)

        XCTAssertEqual(unseen.map(\.id), ["a4"])
    }

    func testAlreadySeenNewestAlertNotifiesNothing() {
        let alerts = [alert(id: "a3", createdAt: "2026-08-05T12:00")]
        let marker = SecurityAlertMarker(
            id: "a3",
            createdAt: SecurityAlertNotifier.parseCreatedAt("2026-08-05T12:00")
        )

        XCTAssertEqual(SecurityAlertNotifier.unseenAlerts(in: alerts, since: marker), [])
    }

    /// The marker's own alert can age out of the server's 50-row window; the timestamp is then
    /// the only thing stopping the remaining history from being announced a second time.
    func testTimestampSuppressesHistoryWhenMarkerIDHasAgedOut() {
        let alerts = [
            alert(id: "a9", createdAt: "2026-08-05T14:00"),
            alert(id: "a8", createdAt: "2026-08-05T11:00"),
        ]
        let marker = SecurityAlertMarker(
            id: "gone",
            createdAt: SecurityAlertNotifier.parseCreatedAt("2026-08-05T12:00")
        )

        XCTAssertEqual(SecurityAlertNotifier.unseenAlerts(in: alerts, since: marker).map(\.id), ["a9"])
    }

    func testNotificationBodyCoalescesABurstIntoOneMessage() {
        let single = [alert(id: "a1", createdAt: "2026-08-05T12:00", detail: "Abuse block applied")]
        let burst = [
            alert(id: "a3", createdAt: "2026-08-05T12:02", detail: "Repeated sign-in lockouts"),
            alert(id: "a2", createdAt: "2026-08-05T12:01"),
            alert(id: "a1", createdAt: "2026-08-05T12:00"),
        ]

        XCTAssertEqual(SecurityAlertNotifier.notificationBody(for: single), "Abuse block applied")

        let coalesced = SecurityAlertNotifier.notificationBody(for: burst)
        XCTAssertNotNil(coalesced)
        XCTAssertTrue(coalesced?.contains("3") == true)
        // The newest alert's own wording still leads the body.
        XCTAssertTrue(coalesced?.contains("Repeated sign-in lockouts") == true)
        XCTAssertNil(SecurityAlertNotifier.notificationBody(for: []))
    }
}
