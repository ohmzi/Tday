import Foundation

/// Minimal, platform-neutral view of a task for T'Day's FIXED, non-configurable
/// ordering. Swift twin of the shared Kotlin `TaskSortKey`
/// (shared/src/commonMain/kotlin/com/ohmz/tday/shared/sort/TaskSortEngine.kt).
///
/// Every task-ordering site — scheduled screen, custom lists, floaters, the scheduled task home
/// feed, and the home-screen widgets — maps its own model onto this so the
/// presentation order is identical no matter where the user opens their account.
/// There is NO user setting; this is simply how lists are presented.
///
/// `dueEpochMs`/`updatedAtEpochMs` are UTC millis; a null due date sorts LAST and
/// a null modified date sorts LAST. `priorityRank` is 0-highest
/// (see `TaskSortEngine.priorityRank`). `pinned` tasks always lead.
struct TaskSortKey {
    let id: String
    let pinned: Bool
    let dueEpochMs: Int64?
    let priorityRank: Int
    let updatedAtEpochMs: Int64?

    init(
        id: String,
        pinned: Bool = false,
        dueEpochMs: Int64? = nil,
        priorityRank: Int = TaskSortEngine.normalPriorityRank,
        updatedAtEpochMs: Int64? = nil
    ) {
        self.id = id
        self.pinned = pinned
        self.dueEpochMs = dueEpochMs
        self.priorityRank = priorityRank
        self.updatedAtEpochMs = updatedAtEpochMs
    }
}

/// The single source of truth for how T'Day orders tasks on iOS. Mirrors the
/// shared Kotlin `TaskSortEngine` exactly; keep the two (and the web TS twin) in
/// sync by hand.
///
/// - Todos (scheduled screen + custom lists, applied WITHIN each day group):
///   pinned first, then due date+time ASC (soonest first, undated last), then
///   priority (High -> Medium -> Low -> Lowest), then most-recently-modified, then id.
/// - Floaters: pinned first, then priority (High -> Medium -> Low -> Lowest), then
///   most-recently-modified, then id.
enum TaskSortEngine {

    /// Rank for the canonical "Low" wire value (UI label "Normal", the default).
    /// This is ALSO the fallback rank for a priority string that matches nothing
    /// known — deliberately: an unrecognized/absent priority is a different
    /// concept from the user explicitly picking the new bottom tier below, and
    /// must keep degrading to "as if Normal", not to the new tier. Was previously
    /// named `lowestPriorityRank`; split out from `lowestTierPriorityRank` (below)
    /// when the "Lowest" wire value was added so the two meanings can no longer be
    /// conflated by a single constant. Mirrors the shared Kotlin
    /// `TaskSortEngine.NORMAL_PRIORITY_RANK`.
    static let normalPriorityRank = 2

    /// Rank for the "Lowest" wire value (UI label "Low") — the new, genuinely
    /// least-urgent tier, one rank below Normal. Only reached when a priority
    /// string explicitly matches "lowest"; never used as a fallback. Mirrors the
    /// shared Kotlin `TaskSortEngine.LOWEST_TIER_PRIORITY_RANK`.
    static let lowestTierPriorityRank = 3

    static func sortedTodos<T>(_ items: [T], key: (T) -> TaskSortKey) -> [T] {
        items.sorted { precedesTodo(key($0), key($1)) }
    }

    static func sortedFloaters<T>(_ items: [T], key: (T) -> TaskSortKey) -> [T] {
        items.sorted { precedesFloater(key($0), key($1)) }
    }

    /// Comparator (negative = `a` before `b`), mirroring Kotlin `compareTodos`.
    static func compareTodos(_ a: TaskSortKey, _ b: TaskSortKey) -> Int {
        if let result = pin(a, b) { return result }
        if let result = dueAscNullsLast(a, b) { return result }
        if let result = priority(a, b) { return result }
        if let result = modifiedDesc(a, b) { return result }
        return compareStrings(a.id, b.id)
    }

    /// Comparator (negative = `a` before `b`), mirroring Kotlin `compareFloaters`.
    static func compareFloaters(_ a: TaskSortKey, _ b: TaskSortKey) -> Int {
        if let result = pin(a, b) { return result }
        if let result = priority(a, b) { return result }
        if let result = modifiedDesc(a, b) { return result }
        return compareStrings(a.id, b.id)
    }

    /// Strict-weak-ordering predicate for `sorted(by:)`: true iff `a` should come
    /// before `b`. Derived from the same key sequence as `compareTodos`.
    static func precedesTodo(_ a: TaskSortKey, _ b: TaskSortKey) -> Bool {
        compareTodos(a, b) < 0
    }

    /// Strict-weak-ordering predicate for `sorted(by:)`: true iff `a` should come
    /// before `b`. Derived from the same key sequence as `compareFloaters`.
    static func precedesFloater(_ a: TaskSortKey, _ b: TaskSortKey) -> Bool {
        compareFloaters(a, b) < 0
    }

    /// 0 = highest priority (sorts first). Tolerant of every priority spelling the app stores:
    /// canonical Lowest/Low/Medium/High, the server/legacy vocabulary normal/important/urgent,
    /// and any case. Realtime-synced rows arrive un-normalized, so a strict match would collapse
    /// them to Normal and the sort would ignore priority. Unknown/absent -> Normal (NOT the new
    /// "lowest" tier — see `normalPriorityRank`'s doc comment). Mirrors the shared Kotlin
    /// `TaskSortEngine.priorityRank(String?)`.
    static func priorityRank(_ priority: String) -> Int {
        switch priority.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "high", "urgent":
            return 0
        case "medium", "important":
            return 1
        case "lowest":
            return lowestTierPriorityRank
        default:
            return normalPriorityRank
        }
    }

    // MARK: - Stages (each returns non-nil only when it decides the order)

    private static func pin(_ a: TaskSortKey, _ b: TaskSortKey) -> Int? {
        let delta = rank(b.pinned) - rank(a.pinned)
        return delta != 0 ? delta : nil
    }

    private static func priority(_ a: TaskSortKey, _ b: TaskSortKey) -> Int? {
        let delta = compareInts(a.priorityRank, b.priorityRank)
        return delta != 0 ? delta : nil
    }

    /// Floor a UTC-epoch-millis instant to its minute (drop seconds/millis).
    private static func floorToMinute(_ epochMs: Int64) -> Int64 { epochMs - (epochMs % 60_000) }

    private static func dueAscNullsLast(_ a: TaskSortKey, _ b: TaskSortKey) -> Int? {
        // Minute precision: times are shown to the minute ("9:41 PM"), so two tasks in the
        // same clock minute differing only by seconds are the "same time" and fall through
        // to the priority tiebreak.
        let delta: Int
        switch (a.dueEpochMs, b.dueEpochMs) {
        case (nil, nil):
            delta = 0
        case (nil, _):
            delta = 1
        case (_, nil):
            delta = -1
        case let (x?, y?):
            delta = compareInt64(floorToMinute(x), floorToMinute(y))
        }
        return delta != 0 ? delta : nil
    }

    private static func modifiedDesc(_ a: TaskSortKey, _ b: TaskSortKey) -> Int? {
        let delta: Int
        switch (a.updatedAtEpochMs, b.updatedAtEpochMs) {
        case (nil, nil):
            delta = 0
        case (nil, _):
            delta = 1              // no timestamp sorts last
        case (_, nil):
            delta = -1
        case let (x?, y?):
            delta = compareInt64(y, x)  // DESC: most recently modified first
        }
        return delta != 0 ? delta : nil
    }

    private static func rank(_ pinned: Bool) -> Int { pinned ? 1 : 0 }

    private static func compareInts(_ a: Int, _ b: Int) -> Int {
        a == b ? 0 : (a < b ? -1 : 1)
    }

    private static func compareInt64(_ a: Int64, _ b: Int64) -> Int {
        a == b ? 0 : (a < b ? -1 : 1)
    }

    private static func compareStrings(_ a: String, _ b: String) -> Int {
        a == b ? 0 : (a < b ? -1 : 1)
    }
}
