import Foundation

func parseOptionalDate(_ value: String?) -> Date? {
    guard let value, value.isEmpty == false else {
        return nil
    }
    return ISO8601DateFormatter.full.date(from: value) ?? ISO8601DateFormatter().date(from: value)
}

func todoMergeKey(canonicalId: String, instanceDateEpochMs: Int64?) -> String {
    "\(canonicalId)::\(instanceDateEpochMs.map(String.init) ?? "root")"
}

func todoMergeKey(item: TodoItem) -> String {
    todoMergeKey(canonicalId: item.canonicalId, instanceDateEpochMs: item.instanceDateEpochMillis)
}

func todoMergeKey(record: CachedTodoRecord) -> String {
    todoMergeKey(canonicalId: record.canonicalId, instanceDateEpochMs: record.instanceDateEpochMs)
}

func mapTodoDTO(_ dto: TodoDTO) -> TodoItem {
    let instanceDate = parseOptionalDate(dto.instanceDate)
    let id = instanceDate.map {
        "\(dto.id):\(Int64($0.timeIntervalSince1970 * 1000.0))"
    } ?? dto.id
    return TodoItem(
        id: id,
        canonicalId: dto.id,
        title: dto.title,
        description: dto.description,
        priority: dto.priority,
        dtstart: parseOptionalDate(dto.dtstart) ?? .now,
        due: parseOptionalDate(dto.due) ?? .now,
        rrule: dto.rrule,
        instanceDate: instanceDate,
        pinned: dto.pinned,
        completed: dto.completed,
        listId: dto.listID,
        updatedAt: parseOptionalDate(dto.updatedAt)
    )
}

func todoToCache(_ todo: TodoItem) -> CachedTodoRecord {
    CachedTodoRecord(
        id: todo.id,
        canonicalId: todo.canonicalId,
        title: todo.title,
        description: todo.description,
        priority: todo.priority,
        dtstartEpochMs: Int64(todo.dtstart.timeIntervalSince1970 * 1000.0),
        dueEpochMs: Int64(todo.due.timeIntervalSince1970 * 1000.0),
        rrule: todo.rrule,
        instanceDateEpochMs: todo.instanceDateEpochMillis,
        pinned: todo.pinned,
        completed: todo.completed,
        listId: todo.listId,
        updatedAtEpochMs: todo.updatedAt.map { Int64($0.timeIntervalSince1970 * 1000.0) } ?? 0
    )
}

func todoFromCache(_ record: CachedTodoRecord) -> TodoItem {
    TodoItem(
        id: record.id,
        canonicalId: record.canonicalId,
        title: record.title,
        description: record.description,
        priority: record.priority,
        dtstart: Date(timeIntervalSince1970: TimeInterval(record.dtstartEpochMs) / 1000.0),
        due: Date(timeIntervalSince1970: TimeInterval(record.dueEpochMs) / 1000.0),
        rrule: record.rrule,
        instanceDate: record.instanceDateEpochMs.map { Date(timeIntervalSince1970: TimeInterval($0) / 1000.0) },
        pinned: record.pinned,
        completed: record.completed,
        listId: record.listId,
        updatedAt: record.updatedAtEpochMs > 0 ? Date(timeIntervalSince1970: TimeInterval(record.updatedAtEpochMs) / 1000.0) : nil
    )
}

func mapListDTO(_ dto: ListDTO, iconFallback: String? = nil) -> ListSummary {
    ListSummary(
        id: dto.id,
        name: dto.name,
        color: dto.color,
        iconKey: dto.iconKey ?? iconFallback,
        todoCount: dto.todoCount,
        updatedAt: parseOptionalDate(dto.updatedAt)
    )
}

func listToCache(_ list: ListSummary) -> CachedListRecord {
    CachedListRecord(
        id: list.id,
        name: list.name,
        color: list.color,
        iconKey: list.iconKey,
        todoCount: list.todoCount,
        updatedAtEpochMs: list.updatedAt.map { Int64($0.timeIntervalSince1970 * 1000.0) } ?? 0
    )
}

func listFromCache(_ record: CachedListRecord, todoCountOverride: Int? = nil) -> ListSummary {
    ListSummary(
        id: record.id,
        name: record.name,
        color: record.color,
        iconKey: record.iconKey,
        todoCount: todoCountOverride ?? record.todoCount,
        updatedAt: record.updatedAtEpochMs > 0 ? Date(timeIntervalSince1970: TimeInterval(record.updatedAtEpochMs) / 1000.0) : nil
    )
}

func mapCompletedDTO(_ dto: CompletedTodoDTO) -> CompletedItem {
    CompletedItem(
        id: dto.id,
        originalTodoId: dto.originalTodoID,
        title: dto.title,
        description: dto.description,
        priority: dto.priority,
        dtstart: parseOptionalDate(dto.dtstart) ?? .now,
        due: parseOptionalDate(dto.due) ?? .now,
        completedAt: parseOptionalDate(dto.completedAt),
        rrule: dto.rrule,
        instanceDate: parseOptionalDate(dto.instanceDate),
        listName: dto.listName,
        listColor: dto.listColor
    )
}

func completedToCache(_ item: CompletedItem) -> CachedCompletedRecord {
    CachedCompletedRecord(
        id: item.id,
        originalTodoId: item.originalTodoId,
        title: item.title,
        description: item.description,
        priority: item.priority,
        dtstartEpochMs: Int64(item.dtstart.timeIntervalSince1970 * 1000.0),
        dueEpochMs: Int64(item.due.timeIntervalSince1970 * 1000.0),
        completedAtEpochMs: item.completedAt.map { Int64($0.timeIntervalSince1970 * 1000.0) } ?? 0,
        rrule: item.rrule,
        instanceDateEpochMs: item.instanceDate.map { Int64($0.timeIntervalSince1970 * 1000.0) },
        listName: item.listName,
        listColor: item.listColor
    )
}

func completedFromCache(_ record: CachedCompletedRecord) -> CompletedItem {
    CompletedItem(
        id: record.id,
        originalTodoId: record.originalTodoId,
        title: record.title,
        description: record.description,
        priority: record.priority,
        dtstart: Date(timeIntervalSince1970: TimeInterval(record.dtstartEpochMs) / 1000.0),
        due: Date(timeIntervalSince1970: TimeInterval(record.dueEpochMs) / 1000.0),
        completedAt: record.completedAtEpochMs > 0 ? Date(timeIntervalSince1970: TimeInterval(record.completedAtEpochMs) / 1000.0) : nil,
        rrule: record.rrule,
        instanceDate: record.instanceDateEpochMs.map { Date(timeIntervalSince1970: TimeInterval($0) / 1000.0) },
        listName: record.listName,
        listColor: record.listColor
    )
}

extension ISO8601DateFormatter {
    static let full: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()
}
