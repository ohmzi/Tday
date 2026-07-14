import Foundation
import SwiftUI
import UniformTypeIdentifiers

// MARK: - Portable bundle models (mirror shared ExportModels.kt)

/// One materialized override of a recurring todo occurrence.
struct TodoInstanceDto: Codable, Equatable {
    let id: String
    var recurId: String = ""
    let instanceDate: String
    var overriddenTitle: String? = nil
    var overriddenDescription: String? = nil
    var overriddenPriority: String? = nil
    var overriddenDue: String? = nil
    var completedAt: String? = nil
}

/// A todo plus the recurrence state the flat TodoDTO doesn't carry.
struct ExportedTodoDto: Codable, Equatable {
    let todo: TodoDTO
    var exdates: [String] = []
    var instances: [TodoInstanceDto] = []
}

/// Versioned, portable snapshot of one user's data. Composes the existing wire
/// DTOs so it stays in lockstep with the live API.
struct TdayExport: Codable, Equatable {
    var schemaVersion: Int = 1
    var exportedAt: String? = nil
    var appVersion: String? = nil
    var source: String? = nil
    var lists: [ListDTO] = []
    var floaterLists: [FloaterListDTO] = []
    var todos: [ExportedTodoDto] = []
    var floaters: [FloaterDTO] = []
    var completedTodos: [CompletedTodoDTO] = []
    var completedFloaters: [CompletedFloaterDTO] = []
    var preferences: PreferencesDTO? = nil
}

/// Body of POST /api/import.
struct ImportRequest: Codable {
    let export: TdayExport
    var dryRun: Bool = false
    var includeCompleted: Bool = true
    var includePreferences: Bool = true
}

/// What an import wrote (or, for a dry run, would write).
struct ImportCounts: Codable, Equatable {
    var lists: Int = 0
    var floaterLists: Int = 0
    var todos: Int = 0
    var floaters: Int = 0
    var todoInstances: Int = 0
    var completedTodos: Int = 0
    var completedFloaters: Int = 0
    var remappedIds: Int = 0
    var preferencesApplied: Bool = false

    /// Rows an import added, for the confirm dialog and success message.
    var total: Int {
        lists + floaterLists + todos + floaters + completedTodos + completedFloaters
    }
}

struct ImportResponse: Codable {
    let dryRun: Bool
    let imported: ImportCounts
    var message: String? = nil
}

// MARK: - Local export mapper

/// Builds a [TdayExport] from the local offline cache, for exporting in Local
/// Mode (Server Mode uses the backend's complete GET /api/export). Best-effort:
/// the cache holds no recurrence exdates/instances or completed-on-time flag,
/// so those come out empty/defaulted. Pure, so it unit-tests directly.
enum LocalExportMapper {
    static func make(from state: OfflineSyncState, source: String, exportedAtEpochMs: Int64) -> TdayExport {
        let exportedAtIso = iso(exportedAtEpochMs)
        return TdayExport(
            exportedAt: exportedAtIso,
            source: source,
            lists: state.lists.map { rec in
                ListDTO(
                    id: rec.id, name: rec.name, color: rec.color, todoCount: rec.todoCount,
                    iconKey: rec.iconKey, updatedAt: iso(rec.updatedAtEpochMs), createdAt: iso(rec.createdAtEpochMs),
                    myRole: nil, isShared: nil, memberCount: nil, ownerUsername: nil
                )
            },
            floaterLists: state.floaterLists.map { rec in
                FloaterListDTO(
                    id: rec.id, name: rec.name, color: rec.color, todoCount: rec.todoCount,
                    iconKey: rec.iconKey, userID: nil, updatedAt: iso(rec.updatedAtEpochMs),
                    createdAt: iso(rec.createdAtEpochMs),
                    myRole: nil, isShared: nil, memberCount: nil, ownerUsername: nil
                )
            },
            todos: state.todos.map { rec in
                ExportedTodoDto(
                    todo: TodoDTO(
                        id: rec.canonicalId, title: rec.title, description: rec.description,
                        pinned: rec.pinned, priority: rec.priority, due: iso(rec.dueEpochMs) ?? exportedAtIso,
                        rrule: rec.rrule, timeZone: "UTC", instanceDate: iso(rec.instanceDateEpochMs),
                        completed: rec.completed, order: nil, listID: rec.listId, userID: nil,
                        updatedAt: iso(rec.updatedAtEpochMs), createdAt: nil
                    )
                )
            },
            floaters: state.floaters.map { rec in
                FloaterDTO(
                    id: rec.canonicalId, title: rec.title, description: rec.description,
                    pinned: rec.pinned, priority: rec.priority, completed: rec.completed, order: nil,
                    listID: rec.listId, userID: nil, updatedAt: iso(rec.updatedAtEpochMs), createdAt: nil
                )
            },
            completedTodos: state.completedItems.map { rec in
                CompletedTodoDTO(
                    id: rec.id, originalTodoID: rec.originalTodoId, title: rec.title,
                    description: rec.description, priority: rec.priority,
                    due: iso(rec.dueEpochMs) ?? iso(rec.completedAtEpochMs) ?? exportedAtIso,
                    completedAt: iso(rec.completedAtEpochMs), completedOnTime: true, daysToComplete: nil,
                    rrule: rec.rrule, userID: nil, instanceDate: iso(rec.instanceDateEpochMs),
                    listID: rec.listId, listName: rec.listName, listColor: rec.listColor
                )
            },
            completedFloaters: state.completedFloaters.map { rec in
                CompletedFloaterDTO(
                    id: rec.id, originalFloaterID: rec.originalFloaterId, title: rec.title,
                    description: rec.description, priority: rec.priority,
                    completedAt: iso(rec.completedAtEpochMs), daysToComplete: nil, userID: nil,
                    listID: rec.listId, listName: rec.listName, listColor: rec.listColor
                )
            },
            preferences: PreferencesDTO(
                direction: nil, sortBy: nil, groupBy: nil, rrule: nil,
                aiSummaryEnabled: state.aiSummaryEnabled
            )
        )
    }

    private static func iso(_ epochMs: Int64) -> String { Date(epochMilliseconds: epochMs).ISO8601Format() }

    private static func iso(_ epochMs: Int64?) -> String? {
        epochMs.map { Date(epochMilliseconds: $0).ISO8601Format() }
    }
}

// MARK: - Repository

/// "Your data" export/import. Export works in both modes; import goes through
/// the server's /api/import, so it's a Server-Mode action — which is exactly
/// the Local→Server migration (export locally, sign in, import).
@MainActor
final class DataExportRepository {
    private let api: TdayAPIService
    private let cacheManager: OfflineCacheManager
    private let syncManager: SyncManager
    private let secureStore: SecureStore

    init(api: TdayAPIService, cacheManager: OfflineCacheManager, syncManager: SyncManager, secureStore: SecureStore) {
        self.api = api
        self.cacheManager = cacheManager
        self.syncManager = syncManager
        self.secureStore = secureStore
    }

    var isLocalMode: Bool { secureStore.isLocalMode() }

    func buildExportData() async throws -> Data {
        let export: TdayExport
        if secureStore.isLocalMode() {
            export = LocalExportMapper.make(
                from: try await cacheManager.loadOfflineState(),
                source: "local-ios",
                exportedAtEpochMs: Int64(Date().timeIntervalSince1970 * 1_000)
            )
        } else {
            export = try await api.getExport()
        }
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(export)
    }

    func preview(fileData: Data) async throws -> ImportResponse {
        let bundle = try JSONDecoder().decode(TdayExport.self, from: fileData)
        return try await api.postImport(payload: ImportRequest(export: bundle, dryRun: true))
    }

    func commit(fileData: Data) async throws -> ImportResponse {
        let bundle = try JSONDecoder().decode(TdayExport.self, from: fileData)
        let response = try await api.postImport(payload: ImportRequest(export: bundle, dryRun: false))
        _ = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
        return response
    }
}

// MARK: - File document

/// Minimal JSON document for `.fileExporter`.
struct DataExportDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }

    var data: Data

    init(data: Data) { self.data = data }

    init(configuration: ReadConfiguration) throws {
        data = configuration.file.regularFileContents ?? Data()
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}
