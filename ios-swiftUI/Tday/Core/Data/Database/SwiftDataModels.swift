import Foundation
import SwiftData

@Model
final class CachedTodoEntity {
    @Attribute(.unique) var id: String
    var canonicalId: String
    var title: String
    var itemDescription: String?
    var priority: String
    var dueEpochMs: Int64
    var rrule: String?
    var instanceDateEpochMs: Int64?
    var pinned: Bool
    var completed: Bool
    var listId: String?
    var updatedAtEpochMs: Int64

    init(from record: CachedTodoRecord) {
        id = record.id
        canonicalId = record.canonicalId
        title = record.title
        itemDescription = record.description
        priority = record.priority
        dueEpochMs = record.dueEpochMs
        rrule = record.rrule
        instanceDateEpochMs = record.instanceDateEpochMs
        pinned = record.pinned
        completed = record.completed
        listId = record.listId
        updatedAtEpochMs = record.updatedAtEpochMs
    }
}

@Model
final class CachedListEntity {
    @Attribute(.unique) var id: String
    var name: String
    var color: String?
    var iconKey: String?
    var todoCount: Int
    var updatedAtEpochMs: Int64

    init(from record: CachedListRecord) {
        id = record.id
        name = record.name
        color = record.color
        iconKey = record.iconKey
        todoCount = record.todoCount
        updatedAtEpochMs = record.updatedAtEpochMs
    }
}

@Model
final class CachedCompletedEntity {
    @Attribute(.unique) var id: String
    var originalTodoId: String?
    var title: String
    var itemDescription: String?
    var priority: String
    var dueEpochMs: Int64
    var completedAtEpochMs: Int64
    var rrule: String?
    var instanceDateEpochMs: Int64?
    var listName: String?
    var listColor: String?

    init(from record: CachedCompletedRecord) {
        id = record.id
        originalTodoId = record.originalTodoId
        title = record.title
        itemDescription = record.description
        priority = record.priority
        dueEpochMs = record.dueEpochMs
        completedAtEpochMs = record.completedAtEpochMs
        rrule = record.rrule
        instanceDateEpochMs = record.instanceDateEpochMs
        listName = record.listName
        listColor = record.listColor
    }
}

@Model
final class PendingMutationEntity {
    @Attribute(.unique) var mutationId: String
    var kindRawValue: String
    var targetId: String?
    var timestampEpochMs: Int64
    var title: String?
    var itemDescription: String?
    var priority: String?
    var dueEpochMs: Int64?
    var rrule: String?
    var listId: String?
    var pinned: Bool?
    var completed: Bool?
    var instanceDateEpochMs: Int64?
    var name: String?
    var color: String?
    var iconKey: String?

    init(from record: PendingMutationRecord) {
        mutationId = record.mutationId
        kindRawValue = record.kind.rawValue
        targetId = record.targetId
        timestampEpochMs = record.timestampEpochMs
        title = record.title
        itemDescription = record.description
        priority = record.priority
        dueEpochMs = record.dueEpochMs
        rrule = record.rrule
        listId = record.listId
        pinned = record.pinned
        completed = record.completed
        instanceDateEpochMs = record.instanceDateEpochMs
        name = record.name
        color = record.color
        iconKey = record.iconKey
    }
}

@Model
final class SyncMetadataEntity {
    @Attribute(.unique) var id: Int
    var lastSuccessfulSyncEpochMs: Int64
    var lastSyncAttemptEpochMs: Int64
    var aiSummaryEnabled: Bool

    init(lastSuccessfulSyncEpochMs: Int64, lastSyncAttemptEpochMs: Int64, aiSummaryEnabled: Bool) {
        id = 1
        self.lastSuccessfulSyncEpochMs = lastSuccessfulSyncEpochMs
        self.lastSyncAttemptEpochMs = lastSyncAttemptEpochMs
        self.aiSummaryEnabled = aiSummaryEnabled
    }
}
