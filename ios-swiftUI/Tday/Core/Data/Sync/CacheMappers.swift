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

// MARK: - Fixed task ordering (TaskSortEngine)
//
// The ONE mapping from each iOS task model onto the shared, non-configurable
// ordering. Every task-ordering site derives its sort from these keys so the
// presentation order matches web, Android, and the widgets exactly.

func taskSortKey(_ item: TodoItem) -> TaskSortKey {
    TaskSortKey(
        id: item.id,
        pinned: item.pinned,
        dueEpochMs: item.due.map { Int64($0.timeIntervalSince1970 * 1000.0) },
        priorityRank: TaskSortEngine.priorityRank(item.priority),
        updatedAtEpochMs: item.updatedAt.map { Int64($0.timeIntervalSince1970 * 1000.0) }
    )
}

func taskSortKey(_ record: CachedTodoRecord) -> TaskSortKey {
    TaskSortKey(
        id: record.id,
        pinned: record.pinned,
        dueEpochMs: record.dueEpochMs,
        priorityRank: TaskSortEngine.priorityRank(record.priority),
        updatedAtEpochMs: record.updatedAtEpochMs > 0 ? record.updatedAtEpochMs : nil
    )
}

func taskSortKey(_ record: CachedFloaterRecord) -> TaskSortKey {
    TaskSortKey(
        id: record.id,
        pinned: record.pinned,
        dueEpochMs: nil,
        priorityRank: TaskSortEngine.priorityRank(record.priority),
        updatedAtEpochMs: record.updatedAtEpochMs > 0 ? record.updatedAtEpochMs : nil
    )
}

// Fixed TODO ordering (pinned, due asc nulls-last, priority, modified desc, id).
func todoSortPrecedes(_ lhs: TodoItem, _ rhs: TodoItem) -> Bool {
    TaskSortEngine.precedesTodo(taskSortKey(lhs), taskSortKey(rhs))
}

func cachedTodoSortPrecedes(_ lhs: CachedTodoRecord, _ rhs: CachedTodoRecord) -> Bool {
    if lhs.pinned != rhs.pinned {
        return lhs.pinned && !rhs.pinned
    }
    if lhs.dueEpochMs != rhs.dueEpochMs {
        return (lhs.dueEpochMs ?? Int64.max) < (rhs.dueEpochMs ?? Int64.max)
    }
    let lhsKey = todoMergeKey(record: lhs)
    let rhsKey = todoMergeKey(record: rhs)
    if lhsKey != rhsKey {
        return lhsKey < rhsKey
    }
    return lhs.id < rhs.id
}

func cachedFloaterSortPrecedes(_ lhs: CachedFloaterRecord, _ rhs: CachedFloaterRecord) -> Bool {
    if lhs.pinned != rhs.pinned {
        return lhs.pinned && !rhs.pinned
    }
    let lhsRank = floaterPriorityRank(lhs.priority)
    let rhsRank = floaterPriorityRank(rhs.priority)
    if lhsRank != rhsRank {
        return lhsRank < rhsRank
    }
    if lhs.title.localizedCaseInsensitiveCompare(rhs.title) != .orderedSame {
        return lhs.title.localizedCaseInsensitiveCompare(rhs.title) == .orderedAscending
    }
    return lhs.id < rhs.id
}

private func floaterPriorityRank(_ priority: String) -> Int {
    if TaskPriorityDisplay.isUrgent(priority) {
        return 0
    }
    if TaskPriorityDisplay.isImportant(priority) {
        return 1
    }
    return 2
}

// Fixed TODO ordering applied WITHIN each day group (scheduled screen, custom
// lists, Today buckets). Same total order as `todoSortPrecedes`; day-bucket
// grouping is unchanged, only the within-day order follows the fixed sequence.
func todoTimelineSortPrecedes(_ lhs: TodoItem, _ rhs: TodoItem) -> Bool {
    TaskSortEngine.precedesTodo(taskSortKey(lhs), taskSortKey(rhs))
}

func completedMergeKey(originalTodoId: String?, fallbackId: String, instanceDateEpochMs: Int64?) -> String {
    guard let originalTodoId else {
        return "completed-id::\(fallbackId)"
    }
    return "completed-original::\(originalTodoId)::\(instanceDateEpochMs.map(String.init) ?? "root")"
}

func completedMergeKey(record: CachedCompletedRecord) -> String {
    completedMergeKey(originalTodoId: record.originalTodoId, fallbackId: record.id, instanceDateEpochMs: record.instanceDateEpochMs)
}

func completedMergeKey(item: CompletedItem) -> String {
    completedMergeKey(
        originalTodoId: item.originalTodoId,
        fallbackId: item.id,
        instanceDateEpochMs: item.instanceDate.map { Int64($0.timeIntervalSince1970 * 1000.0) }
    )
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
        due: parseOptionalDate(dto.due),
        rrule: dto.rrule,
        instanceDate: instanceDate,
        pinned: dto.pinned,
        completed: dto.completed,
        listId: dto.listID,
        updatedAt: parseOptionalDate(dto.updatedAt)
    )
}

func mapFloaterDTO(_ dto: FloaterDTO) -> TodoItem {
    TodoItem(
        id: dto.id,
        canonicalId: dto.id,
        title: dto.title,
        description: dto.description,
        priority: dto.priority,
        due: nil,
        rrule: nil,
        instanceDate: nil,
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
        dueEpochMs: todo.due.map { Int64($0.timeIntervalSince1970 * 1000.0) },
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
        due: record.dueEpochMs.map { Date(timeIntervalSince1970: TimeInterval($0) / 1000.0) },
        rrule: record.rrule,
        instanceDate: record.instanceDateEpochMs.map { Date(timeIntervalSince1970: TimeInterval($0) / 1000.0) },
        pinned: record.pinned,
        completed: record.completed,
        listId: record.listId,
        updatedAt: record.updatedAtEpochMs > 0 ? Date(timeIntervalSince1970: TimeInterval(record.updatedAtEpochMs) / 1000.0) : nil
    )
}

func floaterToCache(_ floater: TodoItem) -> CachedFloaterRecord {
    CachedFloaterRecord(
        id: floater.id,
        canonicalId: floater.canonicalId,
        title: floater.title,
        description: floater.description,
        priority: floater.priority,
        pinned: floater.pinned,
        completed: floater.completed,
        listId: floater.listId,
        updatedAtEpochMs: floater.updatedAt.map { Int64($0.timeIntervalSince1970 * 1000.0) } ?? 0
    )
}

func floaterFromCache(_ record: CachedFloaterRecord) -> TodoItem {
    TodoItem(
        id: record.id,
        canonicalId: record.canonicalId,
        title: record.title,
        description: record.description,
        priority: record.priority,
        due: nil,
        rrule: nil,
        instanceDate: nil,
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
        updatedAt: parseOptionalDate(dto.updatedAt),
        createdAt: parseOptionalDate(dto.createdAt),
        myRole: dto.myRole ?? "OWNER",
        isShared: dto.isShared ?? false,
        memberCount: dto.memberCount ?? 0,
        ownerUsername: dto.ownerUsername
    )
}

func mapFloaterListDTO(_ dto: FloaterListDTO, iconFallback: String? = nil) -> ListSummary {
    ListSummary(
        id: dto.id,
        name: dto.name,
        color: dto.color,
        iconKey: dto.iconKey ?? iconFallback,
        todoCount: dto.todoCount,
        updatedAt: parseOptionalDate(dto.updatedAt),
        createdAt: parseOptionalDate(dto.createdAt),
        myRole: dto.myRole ?? "OWNER",
        isShared: dto.isShared ?? false,
        memberCount: dto.memberCount ?? 0,
        ownerUsername: dto.ownerUsername
    )
}

func listToCache(_ list: ListSummary) -> CachedListRecord {
    CachedListRecord(
        id: list.id,
        name: list.name,
        color: list.color,
        iconKey: list.iconKey,
        todoCount: list.todoCount,
        updatedAtEpochMs: list.updatedAt.map { Int64($0.timeIntervalSince1970 * 1000.0) } ?? 0,
        createdAtEpochMs: list.createdAt.map { Int64($0.timeIntervalSince1970 * 1000.0) } ?? 0,
        myRole: list.myRole,
        isShared: list.isShared,
        memberCount: list.memberCount,
        ownerUsername: list.ownerUsername
    )
}

func floaterListToCache(_ list: ListSummary) -> CachedFloaterListRecord {
    CachedFloaterListRecord(
        id: list.id,
        name: list.name,
        color: list.color,
        iconKey: list.iconKey,
        todoCount: list.todoCount,
        updatedAtEpochMs: list.updatedAt.map { Int64($0.timeIntervalSince1970 * 1000.0) } ?? 0,
        createdAtEpochMs: list.createdAt.map { Int64($0.timeIntervalSince1970 * 1000.0) } ?? 0,
        myRole: list.myRole,
        isShared: list.isShared,
        memberCount: list.memberCount,
        ownerUsername: list.ownerUsername
    )
}

func orderListsLikeWeb(_ lists: [CachedListRecord]) -> [CachedListRecord] {
    guard lists.contains(where: { $0.createdAtEpochMs > 0 }) else {
        return lists
    }
    return lists.enumerated()
        .sorted { lhs, rhs in
            if lhs.element.createdAtEpochMs != rhs.element.createdAtEpochMs {
                return lhs.element.createdAtEpochMs > rhs.element.createdAtEpochMs
            }
            return lhs.offset < rhs.offset
        }
        .map(\.element)
}

func orderFloaterListsLikeWeb(_ lists: [CachedFloaterListRecord]) -> [CachedFloaterListRecord] {
    guard lists.contains(where: { $0.createdAtEpochMs > 0 }) else {
        return lists
    }
    return lists.enumerated()
        .sorted { lhs, rhs in
            if lhs.element.createdAtEpochMs != rhs.element.createdAtEpochMs {
                return lhs.element.createdAtEpochMs > rhs.element.createdAtEpochMs
            }
            return lhs.offset < rhs.offset
        }
        .map(\.element)
}

func listFromCache(_ record: CachedListRecord, todoCountOverride: Int? = nil) -> ListSummary {
    ListSummary(
        id: record.id,
        name: record.name,
        color: record.color,
        iconKey: record.iconKey,
        todoCount: todoCountOverride ?? record.todoCount,
        updatedAt: record.updatedAtEpochMs > 0 ? Date(timeIntervalSince1970: TimeInterval(record.updatedAtEpochMs) / 1000.0) : nil,
        createdAt: record.createdAtEpochMs > 0 ? Date(timeIntervalSince1970: TimeInterval(record.createdAtEpochMs) / 1000.0) : nil,
        myRole: record.myRole ?? "OWNER",
        isShared: record.isShared ?? false,
        memberCount: record.memberCount ?? 0,
        ownerUsername: record.ownerUsername
    )
}

func floaterListFromCache(_ record: CachedFloaterListRecord, todoCountOverride: Int? = nil) -> ListSummary {
    ListSummary(
        id: record.id,
        name: record.name,
        color: record.color,
        iconKey: record.iconKey,
        todoCount: todoCountOverride ?? record.todoCount,
        updatedAt: record.updatedAtEpochMs > 0 ? Date(timeIntervalSince1970: TimeInterval(record.updatedAtEpochMs) / 1000.0) : nil,
        createdAt: record.createdAtEpochMs > 0 ? Date(timeIntervalSince1970: TimeInterval(record.createdAtEpochMs) / 1000.0) : nil,
        myRole: record.myRole ?? "OWNER",
        isShared: record.isShared ?? false,
        memberCount: record.memberCount ?? 0,
        ownerUsername: record.ownerUsername
    )
}

func mapCompletedDTO(_ dto: CompletedTodoDTO) -> CompletedItem {
    CompletedItem(
        id: dto.id,
        originalTodoId: dto.originalTodoID,
        title: dto.title,
        description: dto.description,
        priority: dto.priority,
        due: parseOptionalDate(dto.due),
        completedAt: parseOptionalDate(dto.completedAt),
        rrule: dto.rrule,
        instanceDate: parseOptionalDate(dto.instanceDate),
        listId: dto.listID,
        listName: dto.listName,
        listColor: dto.listColor
    )
}

func mapCompletedFloaterDTO(_ dto: CompletedFloaterDTO) -> CompletedItem {
    CompletedItem(
        id: dto.id,
        originalTodoId: dto.originalFloaterID,
        title: dto.title,
        description: dto.description,
        priority: dto.priority,
        due: nil,
        completedAt: parseOptionalDate(dto.completedAt),
        rrule: nil,
        instanceDate: nil,
        listId: dto.listID,
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
        dueEpochMs: item.due.map { Int64($0.timeIntervalSince1970 * 1000.0) },
        completedAtEpochMs: item.completedAt.map { Int64($0.timeIntervalSince1970 * 1000.0) } ?? 0,
        rrule: item.rrule,
        instanceDateEpochMs: item.instanceDate.map { Int64($0.timeIntervalSince1970 * 1000.0) },
        listId: item.listId,
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
        due: record.dueEpochMs.map { Date(timeIntervalSince1970: TimeInterval($0) / 1000.0) },
        completedAt: record.completedAtEpochMs > 0 ? Date(timeIntervalSince1970: TimeInterval(record.completedAtEpochMs) / 1000.0) : nil,
        rrule: record.rrule,
        instanceDate: record.instanceDateEpochMs.map { Date(timeIntervalSince1970: TimeInterval($0) / 1000.0) },
        listId: record.listId,
        listName: record.listName,
        listColor: record.listColor
    )
}

func completedFloaterToCache(_ item: CompletedItem) -> CachedCompletedFloaterRecord {
    CachedCompletedFloaterRecord(
        id: item.id,
        originalFloaterId: item.originalTodoId,
        title: item.title,
        description: item.description,
        priority: item.priority,
        completedAtEpochMs: item.completedAt.map { Int64($0.timeIntervalSince1970 * 1000.0) } ?? 0,
        listId: item.listId,
        listName: item.listName,
        listColor: item.listColor
    )
}

func completedFloaterFromCache(_ record: CachedCompletedFloaterRecord) -> CompletedItem {
    CompletedItem(
        id: record.id,
        originalTodoId: record.originalFloaterId,
        title: record.title,
        description: record.description,
        priority: record.priority,
        due: nil,
        completedAt: record.completedAtEpochMs > 0 ? Date(timeIntervalSince1970: TimeInterval(record.completedAtEpochMs) / 1000.0) : nil,
        rrule: nil,
        instanceDate: nil,
        listId: record.listId,
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
            "yyyy-MM-dd'T'HH:mm",
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
