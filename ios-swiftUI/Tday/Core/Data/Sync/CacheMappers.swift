import Foundation

private let timezoneSuffixPattern = #"([zZ]|[+\-]\d{2}:?\d{2})$"#

private func parseUTCLikeDate(_ value: String) -> Date? {
    if let date = ISO8601DateFormatter.full.date(from: value) {
        return date
    }
    if let date = ISO8601DateFormatter.standard.date(from: value) {
        return date
    }

    let hasExplicitTimezone = value.range(of: timezoneSuffixPattern, options: .regularExpression) != nil
    guard !hasExplicitTimezone else {
        return nil
    }

    for formatter in DateFormatter.utcLikeParsers {
        if let date = formatter.date(from: value) {
            return date
        }
    }

    return nil
}

func parseOptionalDate(_ value: String?) -> Date? {
    guard let value = value?.trimmingCharacters(in: .whitespacesAndNewlines), value.isEmpty == false else {
        return nil
    }
    return parseUTCLikeDate(value)
}

private func canonicalTodoID(from rawID: String) -> String {
    guard let separator = rawID.firstIndex(of: ":") else {
        return rawID
    }
    return String(rawID[..<separator])
}

private func instanceDateFromTodoID(_ rawID: String) -> Date? {
    guard let separator = rawID.firstIndex(of: ":") else {
        return nil
    }
    let suffix = rawID[rawID.index(after: separator)...]
    guard let epochMilliseconds = Int64(suffix) else {
        return nil
    }
    return Date(epochMilliseconds: epochMilliseconds)
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
    let explicitInstanceDate = parseOptionalDate(dto.instanceDate)
    let instanceDate = explicitInstanceDate ?? instanceDateFromTodoID(dto.id)
    return TodoItem(
        id: dto.id,
        canonicalId: canonicalTodoID(from: dto.id),
        title: dto.title,
        description: dto.description,
        priority: dto.priority,
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

    static let standard: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()
}

extension DateFormatter {
    fileprivate static let utcLikeParsers: [DateFormatter] = {
        let patterns = [
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss.SS",
            "yyyy-MM-dd'T'HH:mm:ss.S",
            "yyyy-MM-dd'T'HH:mm:ss",
        ]

        return patterns.map { pattern in
            let formatter = DateFormatter()
            formatter.calendar = Calendar(identifier: .iso8601)
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.timeZone = TimeZone(secondsFromGMT: 0)
            formatter.dateFormat = pattern
            return formatter
        }
    }()
}
