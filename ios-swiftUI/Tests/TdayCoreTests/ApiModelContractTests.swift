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
}
