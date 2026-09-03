#if canImport(WidgetKit) && canImport(SwiftUI)
import AppIntents
import CryptoKit
import Security
import SwiftUI
import WidgetKit

/// Inline widget completions (widgets v2). The widget process has no cache or
/// SwiftData access, so a tapped check ring only queues a `{kind, id}`
/// descriptor in the app group; the app drains the queue through the normal
/// repository complete path on next activation. Until then both providers hide
/// queued ids so the row disappears immediately.
enum WidgetPendingCompletionStore {
    static let queueKey = "tday.widget.pendingCompletions"
    static let checkingKey = "tday.widget.checkingCompletions"
    static let appGroupSuiteName = "group.com.ohmz.tday"
    static let todoKind = "todo"
    static let floaterKind = "floater"

    struct Entry: Codable, Equatable {
        let kind: String
        let id: String
    }

    /// A row mid check-off animation: shown as checked + struck-through until it
    /// expires, then the pending queue hides it. Timestamped so a process killed
    /// mid-animation can't strand a row in the struck state — a stale entry is
    /// simply ignored (the row falls back to the pending-hide / normal rendering).
    struct CheckEntry: Codable, Equatable {
        let kind: String
        let id: String
        let atEpochMs: Int64
    }

    /// The checked-and-struck frame lasts this long before the pending queue removes
    /// the row. Also the staleness cutoff for a crashed animation.
    static let checkingWindowMs: Int64 = 900

    static func load() -> [Entry] {
        guard let data = store().data(forKey: queueKey),
              let entries = try? JSONDecoder().decode([Entry].self, from: data) else {
            return []
        }
        return entries
    }

    static func append(kind: String, id: String) {
        var entries = load()
        guard !entries.contains(Entry(kind: kind, id: id)) else {
            return
        }
        entries.append(Entry(kind: kind, id: id))
        guard let data = try? JSONEncoder().encode(entries) else {
            return
        }
        store().set(data, forKey: queueKey)
    }

    static func pendingIds(kind: String) -> Set<String> {
        Set(load().filter { $0.kind == kind }.map(\.id))
    }

    // MARK: Check-off animation

    private static func loadChecking() -> [CheckEntry] {
        guard let data = store().data(forKey: checkingKey),
              let entries = try? JSONDecoder().decode([CheckEntry].self, from: data) else {
            return []
        }
        return entries
    }

    static func beginChecking(kind: String, id: String, nowEpochMs: Int64) {
        var entries = loadChecking().filter { $0.id != id || $0.kind != kind }
        entries.append(CheckEntry(kind: kind, id: id, atEpochMs: nowEpochMs))
        if let data = try? JSONEncoder().encode(entries) {
            store().set(data, forKey: checkingKey)
        }
    }

    static func endChecking(kind: String, id: String) {
        let entries = loadChecking().filter { $0.id != id || $0.kind != kind }
        if let data = try? JSONEncoder().encode(entries) {
            store().set(data, forKey: checkingKey)
        }
    }

    /// Ids currently in the (non-stale) checked+struck frame.
    static func checkingIds(kind: String, nowEpochMs: Int64) -> Set<String> {
        Set(
            loadChecking()
                .filter { $0.kind == kind && (nowEpochMs - $0.atEpochMs) < checkingWindowMs }
                .map(\.id)
        )
    }

    private static func store() -> UserDefaults {
        UserDefaults(suiteName: appGroupSuiteName) ?? .standard
    }
}

/// Widget-side reader for the widget CONTENT snapshots (task titles, notes, due times).
/// The app writes these into the App Group container with
/// `.completeUntilFirstUserAuthentication` protection instead of UserDefaults, which is
/// unencrypted and backed up. That protection class is what keeps the widget renderable on
/// a locked device; the writer is `WidgetSnapshotFileStore` in
/// Tday/Core/Widget/TodayTasksWidgetSnapshotStore.swift and the file names must stay in
/// lockstep with it.
enum WidgetSnapshotFileStore {
    static let appGroupSuiteName = "group.com.ohmz.tday"
    static let todayFileName = "widget-today-snapshot.json"
    static let floaterFileName = "widget-floater-snapshot.json"
    /// Lightweight list catalog (id/name/kind, no task content) backing the widget
    /// CONFIGURATION picker — see `TdayWidgetListEntityQuery` below. Written by
    /// `WidgetConfigurableListsStore` in Tday/Core/Widget/TodayTasksWidgetSnapshotStore.swift;
    /// the name must stay in lockstep with it.
    static let listsFileName = "widget-lists-snapshot.json"

    static func read(_ fileName: String) -> Data? {
        guard let fileURL = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroupSuiteName)?
            .appendingPathComponent(fileName) else {
            return nil
        }
        return try? Data(contentsOf: fileURL)
    }
}

/// Widget-side twin of the app's `WidgetConfigurableListEntry` — decodes the same
/// `widget-lists-snapshot.json` rows. `kind` is the raw string ("todo" / "floater"); see
/// `TdayWidgetListKind`.
private struct TdayWidgetConfigurableListEntry: Codable {
    let id: String
    let name: String
    let kind: String
}

/// A picked list is either a todo list (due-date-shaped widget content) or a floater list
/// (undated widget content) — see `TaskWidgetMode`, which this maps onto 1:1. There is no
/// third shape: whichever kind of list the user picks, the widget renders in THAT list
/// type's native shape, regardless of which widget (Today vs Floater) they dragged out of
/// the gallery.
enum TdayWidgetListKind: String, Codable {
    case todo
    case floater

    // fileprivate, not internal: TaskWidgetMode (below) is `private`, i.e. file-scoped, and a
    // property can be no more visible than the types in its own signature. Every call site
    // (TodayTasksProvider, FloaterTasksProvider, PerListWidgetContentLoader) lives in this same
    // file, so fileprivate costs nothing here.
    fileprivate var mode: TaskWidgetMode {
        switch self {
        case .todo: return .today
        case .floater: return .floater
        }
    }
}

/// A todo list OR floater list, offered by the widget's "Edit Widget" configuration picker so
/// the user can choose which specific list a widget instance shows (R7 per-list widgets).
///
/// This is a WIDGET-EXTENSION-ONLY sibling of `TdayListAppEntity`
/// (Feature/CarPlay/CarTaskIntents.swift, app target only) rather than a reuse of that exact
/// type: `TdayListAppEntity`'s query reads `AppContainer.shared.cacheManager` in-process,
/// which only exists in the app target — the widget extension is deliberately lightweight
/// (see the file-header comment on `WidgetSnapshotFileStore` above) and has neither
/// AppContainer nor SwiftData linked in. This type follows the identical AppEntity/EntityQuery
/// pattern, just reading the App-Group-file handoff the rest of this extension already uses,
/// and additionally spans BOTH todo and floater lists (`TdayListAppEntity` is todo-only, for
/// the unrelated Focus Filter feature) since the widget picker must offer both.
struct TdayWidgetListEntity: AppEntity {
    let listId: String
    let name: String
    let kind: TdayWidgetListKind

    /// Namespaced so a todo list and a floater list can never collide even if their raw ids
    /// ever did (local-mode ids already differ by prefix; server ids are independent UUID
    /// spaces per table). Also what `TdayWidgetListEntityQuery.entities(for:)` parses back.
    var id: String { "\(kind.rawValue):\(listId)" }

    static var typeDisplayRepresentation = TypeDisplayRepresentation(name: "List")
    static var defaultQuery = TdayWidgetListEntityQuery()

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(name)")
    }
}

struct TdayWidgetListEntityQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [TdayWidgetListEntity] {
        let wanted = Set(identifiers)
        return Self.allLists().filter { wanted.contains($0.id) }
    }

    func suggestedEntities() async throws -> [TdayWidgetListEntity] {
        Self.allLists()
    }

    private static func allLists() -> [TdayWidgetListEntity] {
        guard let data = WidgetSnapshotFileStore.read(WidgetSnapshotFileStore.listsFileName),
              let entries = try? JSONDecoder().decode([TdayWidgetConfigurableListEntry].self, from: data) else {
            return []
        }
        return entries.compactMap { entry in
            guard let kind = TdayWidgetListKind(rawValue: entry.kind) else { return nil }
            return TdayWidgetListEntity(listId: entry.id, name: entry.name, kind: kind)
        }
    }
}

/// Widget configuration (R7): which list, if any, this widget instance shows. `list == nil`
/// (the default — both when the user leaves it unset from the picker, and for every widget
/// placed before this update, which iOS resolves against a config intent's declared default)
/// means "no specific list" and falls back to the ORIGINAL global behavior — the same
/// due-today Today feed / same all-floaters Floater feed this widget kind always showed. That
/// fallback is what keeps every already-placed widget working unchanged after this ships.
struct SelectTaskListIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Select List"
    static var description = IntentDescription("Choose which T'Day list this widget shows. Leave unset for the default feed.")

    @Parameter(title: "List")
    var list: TdayWidgetListEntity?

    init() {}

    init(list: TdayWidgetListEntity?) {
        self.list = list
    }
}

/// Widget-side reader for the shared backend session the app writes after auth/sync
/// (widgets v2 instant sync). Lets the check-ring intent fire an authenticated
/// completion straight to the backend without opening the app. The app-side writer
/// (`WidgetBackendSession.save/clear`) is duplicated in
/// Tday/Core/Widget/TodayTasksWidgetSnapshotStore.swift; the file/key shapes must
/// stay in lockstep. Stored with `.completeUntilFirstUserAuthentication`, so the
/// widget can read it after the device's first unlock.
enum WidgetBackendSession {
    static let appGroupSuiteName = "group.com.ohmz.tday"
    static let fileName = "widget-backend-session.json"

    struct Payload: Codable {
        let baseURL: String
        let cookieHeader: String
        /// The host's TOFU-pinned fingerprint when the app pinned one (self-signed /
        /// privately-issued cert). Decoded leniently so a session written by an older
        /// build (before pinning was shared) still loads.
        let pinnedFingerprint: String?

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            baseURL = try container.decode(String.self, forKey: .baseURL)
            cookieHeader = try container.decode(String.self, forKey: .cookieHeader)
            pinnedFingerprint = try container.decodeIfPresent(String.self, forKey: .pinnedFingerprint)
        }
    }

    private static func fileURL() -> URL? {
        FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroupSuiteName)?
            .appendingPathComponent(fileName)
    }

    static func load() -> (baseURL: URL, cookieHeader: String, pinnedFingerprint: String?)? {
        guard let fileURL = fileURL(),
              let data = try? Data(contentsOf: fileURL),
              let payload = try? JSONDecoder().decode(Payload.self, from: data),
              let baseURL = URL(string: payload.baseURL),
              !payload.cookieHeader.isEmpty else {
            return nil
        }
        return (baseURL, payload.cookieHeader, payload.pinnedFingerprint)
    }
}

/// Reproduces the app's TOFU certificate pinning for the widget's one-shot
/// completion call. The widget has no keychain access, so the app hands it the
/// pinned fingerprint through the shared session; without this delegate a plain
/// URLSession rejects self-signed / privately-issued certs outright and instant
/// sync would be silently dead on exactly the self-hosted setups the app's pinning
/// exists to support. Mirrors NetworkConfiguration.urlSession(_:didReceive:):
/// local hosts and system-trusted chains use default handling; anything else must
/// match the pin. Unlike the app this never pins on first use — the widget only
/// enforces a pin the app already established.
private final class WidgetPinnedTrustDelegate: NSObject, URLSessionDelegate {
    private let pinnedFingerprint: String?

    init(pinnedFingerprint: String?) {
        self.pinnedFingerprint = pinnedFingerprint
    }

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let trust = challenge.protectionSpace.serverTrust else {
            completionHandler(.performDefaultHandling, nil)
            return
        }

        let host = challenge.protectionSpace.host.lowercased()
        if Self.isLocalAddress(host: host) {
            completionHandler(.performDefaultHandling, nil)
            return
        }

        let policy = SecPolicyCreateSSL(true, host as CFString)
        SecTrustSetPolicies(trust, policy)
        if SecTrustEvaluateWithError(trust, nil) {
            // Public-CA chain: standard validation, exactly like the app.
            completionHandler(.performDefaultHandling, nil)
            return
        }

        // Self-signed / privately issued: only proceed if it matches the app's pin.
        guard let pinnedFingerprint,
              let fingerprint = Self.fingerprintForTrust(trust),
              fingerprint == pinnedFingerprint else {
            completionHandler(.cancelAuthenticationChallenge, nil)
            return
        }
        completionHandler(.useCredential, URLCredential(trust: trust))
    }

    private static func isLocalAddress(host: String) -> Bool {
        host == "localhost" ||
        host == "127.0.0.1" ||
        host == "10.0.2.2" ||
        host.hasPrefix("192.168.") ||
        host.hasPrefix("10.") ||
        host.hasSuffix(".local")
    }

    /// Must stay byte-identical to NetworkConfiguration.fingerprintForTrust, or a
    /// pin the app stored would never match here.
    private static func fingerprintForTrust(_ trust: SecTrust) -> String? {
        guard let key = SecTrustCopyKey(trust) else {
            return leafCertificateHash(for: trust)
        }
        var error: Unmanaged<CFError>?
        guard let external = SecKeyCopyExternalRepresentation(key, &error) as Data? else {
            return leafCertificateHash(for: trust)
        }
        return Data(SHA256.hash(data: external)).base64EncodedString()
    }

    private static func leafCertificateHash(for trust: SecTrust) -> String? {
        guard let certificates = SecTrustCopyCertificateChain(trust) as? [SecCertificate],
              let certificate = certificates.first else {
            return nil
        }
        let data = SecCertificateCopyData(certificate) as Data
        return Data(SHA256.hash(data: data)).base64EncodedString()
    }
}

/// Completes a task straight from a widget row without opening the app.
/// Runs in the widget process, so it records the tap (offline fallback), refreshes
/// the timeline, and — best-effort — fires an authenticated completion to the
/// backend so a checked-off task syncs instantly instead of waiting for the next
/// app launch. Any network failure is swallowed: the app drains the queue later.
struct CompleteWidgetTaskIntent: AppIntent {
    static let title: LocalizedStringResource = "Complete Task"
    // Widget-button plumbing only — never surfaced in Shortcuts or Spotlight.
    static let isDiscoverable = false
    static let openAppWhenRun = false

    @Parameter(title: "Task Kind")
    var kind: String

    @Parameter(title: "Task ID")
    var taskID: String

    init() {}

    init(kind: String, taskID: String) {
        self.kind = kind
        self.taskID = taskID
    }

    func perform() async throws -> some IntentResult {
        let nowMs = Int64(Date().timeIntervalSince1970 * 1_000)

        // Durability first: queue the completion (offline fallback + what ultimately
        // hides the row). Then mark it "checking" so the row is shown checked +
        // struck-through for one beat before the pending queue removes it — the app's
        // check → strikethrough → fade, as far as WidgetKit will animate it. Ordering
        // this way means a completion is never lost even if the animation is cut short.
        WidgetPendingCompletionStore.append(kind: kind, id: taskID)
        WidgetPendingCompletionStore.beginChecking(kind: kind, id: taskID, nowEpochMs: nowMs)
        // Reload BOTH kinds, not just the content kind's historical home: a per-list widget
        // (R7) can render a todo row from a "FloaterTasksWidget" instance (or a floater row
        // from a "TodayTasksWidget" instance) when the picked list's type doesn't match the
        // gallery slot it was dragged from, so there is no single kind guaranteed to own the
        // instance that was actually tapped.
        WidgetCenter.shared.reloadAllTimelines()

        // Best-effort instant backend sync (idempotent endpoints; the queue is the
        // fallback, so any failure is swallowed).
        await sendBackendCompletion()

        // Hold the checked+struck frame, then end it so the pending filter drops the
        // row — WidgetKit animates the removal. sendBackendCompletion may already have
        // consumed part of the window; sleep only the remainder so the beat is bounded.
        let elapsedMs = Int64(Date().timeIntervalSince1970 * 1_000) - nowMs
        let remainingMs = WidgetPendingCompletionStore.checkingWindowMs - elapsedMs
        if remainingMs > 0 {
            try? await Task.sleep(for: .milliseconds(remainingMs))
        }
        WidgetPendingCompletionStore.endChecking(kind: kind, id: taskID)
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }

    private func sendBackendCompletion() async {
        guard let session = WidgetBackendSession.load(),
              let request = Self.completionRequest(
                  kind: kind,
                  taskID: taskID,
                  baseURL: session.baseURL,
                  cookieHeader: session.cookieHeader
              ) else {
            return
        }
        let configuration = URLSessionConfiguration.ephemeral
        // perform() must await this call (returning early would let the system tear
        // the process down mid-flight), and the system spins the tapped ring until
        // perform() returns — so keep the ceiling tight. A completion PATCH is tiny;
        // if it can't land in this window the queue fallback covers it anyway.
        configuration.timeoutIntervalForRequest = 6
        configuration.timeoutIntervalForResource = 6
        configuration.waitsForConnectivity = false
        // Delegate reproduces the app's TOFU pin so self-hosted servers with
        // self-signed certs work here too (a delegate-less session rejects them).
        let urlSession = URLSession(
            configuration: configuration,
            delegate: WidgetPinnedTrustDelegate(pinnedFingerprint: session.pinnedFingerprint),
            delegateQueue: nil
        )
        defer { urlSession.finishTasksAndInvalidate() }
        // Swallow every error — the queue is the fallback and must never surface a
        // failure to the user from a widget tap.
        _ = try? await urlSession.data(for: request)
    }

    /// Resolves the completion payload from the App Group snapshot (canonical id +,
    /// for todos, the recurring-instance date) and builds the authenticated PATCH.
    /// Returns nil when the task isn't found — the queue still handles it.
    private static func completionRequest(
        kind: String,
        taskID: String,
        baseURL: URL,
        cookieHeader: String
    ) -> URLRequest? {
        let path: String
        var body: [String: Any] = [:]
        if kind == WidgetPendingCompletionStore.floaterKind {
            guard let canonicalId = floaterCanonicalId(taskID: taskID) else {
                return nil
            }
            path = "/api/floater/complete"
            body["id"] = canonicalId
        } else {
            guard let payload = todoCompletionPayload(taskID: taskID) else {
                return nil
            }
            path = "/api/todo/complete"
            body["id"] = payload.canonicalId
            // Present as an explicit JSON null for non-recurring todos, matching the
            // app's own TodoCompleteRequest encoding.
            if let instanceDateEpochMs = payload.instanceDateEpochMs {
                body["instanceDate"] = Date(timeIntervalSince1970: TimeInterval(instanceDateEpochMs) / 1_000).ISO8601Format()
            } else {
                body["instanceDate"] = NSNull()
            }
        }

        let url = baseURL.appendingPathComponent(path.trimmingCharacters(in: CharacterSet(charactersIn: "/")))
        guard let data = try? JSONSerialization.data(withJSONObject: body) else {
            return nil
        }
        var request = URLRequest(url: url)
        request.httpMethod = "PATCH"
        request.httpBody = data
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(cookieHeader, forHTTPHeaderField: "Cookie")
        // Identify as the iOS client at the app's version, exactly like
        // NetworkConfiguration.defaultHeaders. Without these the backend's mobile
        // version gate (which returns 426/409 for incompatible builds) skips this
        // request entirely, letting the widget write to a server the app itself is
        // deliberately fenced off from. The extension's CFBundleShortVersionString
        // is the app's MARKETING_VERSION, so it blocks identically.
        request.setValue("ios", forHTTPHeaderField: "X-Tday-Client")
        request.setValue(
            Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "unknown",
            forHTTPHeaderField: "X-Tday-App-Version"
        )
        request.setValue(TimeZone.current.identifier, forHTTPHeaderField: "X-User-Timezone")
        return request
    }

    private static func todoCompletionPayload(taskID: String) -> (canonicalId: String, instanceDateEpochMs: Int64?)? {
        // Per-list widgets (R7) render rows sourced from `perList`, not just the global
        // `tasks` aggregate — search both so a tap on a per-list row resolves too.
        guard let snapshot = TodayTasksProvider.loadWidgetSnapshot(),
              let task = snapshot.firstTask(withId: taskID) else {
            return nil
        }
        return (task.canonicalId, task.instanceDateEpochMs)
    }

    private static func floaterCanonicalId(taskID: String) -> String? {
        guard let snapshot = FloaterTasksProvider.loadWidgetSnapshot(),
              let task = snapshot.firstTask(withId: taskID) else {
            return nil
        }
        return task.canonicalId
    }
}

/// Widget-side reader for the App Lock flag the app mirrors into the shared App Group suite
/// (`AppLockStore` in Tday/Core/Security/ProbeDecryptor.swift). Plain UserDefaults is fine —
/// the flag itself is not a secret, only the task content it gates is.
private enum WidgetAppLockStore {
    static let appGroupSuiteName = "group.com.ohmz.tday"
    private static let key = "app.lock.enabled"

    static var isEnabled: Bool {
        (UserDefaults(suiteName: appGroupSuiteName) ?? .standard).bool(forKey: key)
    }
}

/// R7 configurable widgets: `status`/`mode` carry what used to be split across
/// `TodayTasksSnapshotStatus` + a separate `isLocked` bool + a mode hardcoded by the View.
/// A configured-to-a-list instance can render EITHER shape (see `TaskWidgetMode`), so mode is
/// now data on the entry rather than a constant the view supplies.
private struct TodayTasksEntry: TimelineEntry {
    let date: Date
    let title: String
    let status: TaskWidgetStatus
    let taskCount: Int
    let rows: [WidgetTaskRowModel]
    let mode: TaskWidgetMode
}

private struct TodayTaskSnapshot: Codable, Identifiable {
    let id: String
    let title: String
    let dueEpochMs: Int64
    let priority: String
    // Optional so snapshots persisted before this field existed still decode (as nil).
    let description: String?
    // Backend-completion payload (widgets v2 instant sync): the CANONICAL id the
    // /api/todo/complete endpoint expects, plus the recurring-instance date.
    // Defaulted so snapshots persisted before these existed still decode
    // (canonicalId falls back to the display id).
    let canonicalId: String
    let instanceDateEpochMs: Int64?

    init(
        id: String,
        title: String,
        dueEpochMs: Int64,
        priority: String,
        description: String? = nil,
        canonicalId: String? = nil,
        instanceDateEpochMs: Int64? = nil
    ) {
        self.id = id
        self.title = title
        self.dueEpochMs = dueEpochMs
        self.priority = priority
        self.description = description
        self.canonicalId = canonicalId ?? id
        self.instanceDateEpochMs = instanceDateEpochMs
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let decodedId = try container.decode(String.self, forKey: .id)
        id = decodedId
        title = try container.decode(String.self, forKey: .title)
        dueEpochMs = try container.decode(Int64.self, forKey: .dueEpochMs)
        priority = try container.decode(String.self, forKey: .priority)
        description = try container.decodeIfPresent(String.self, forKey: .description)
        canonicalId = try container.decodeIfPresent(String.self, forKey: .canonicalId) ?? decodedId
        instanceDateEpochMs = try container.decodeIfPresent(Int64.self, forKey: .instanceDateEpochMs)
    }
}

private enum TodayTasksSnapshotStatus: String, Codable {
    case setup
    case empty
    case tasks
}

/// Widget-side twin of the app's `TodayTasksWidgetPerListSnapshot` — one todo list's slice of
/// `TodayTasksSnapshot.perList` (R7 per-list widgets): `totalCount` is the list's TRUE
/// due-today-or-overdue count, `tasks` the display-capped rows.
private struct TodayTasksPerListSnapshot: Codable {
    let totalCount: Int
    let tasks: [TodayTaskSnapshot]
}

private func isTaskWidgetDaytime(_ date: Date) -> Bool {
    let hour = Calendar.current.component(.hour, from: date)
    return (6..<18).contains(hour)
}

private func nextTaskWidgetDayNightRefresh(after date: Date, calendar: Calendar = .current) -> Date {
    let hour = calendar.component(.hour, from: date)
    let targetHour = hour < 6 ? 6 : (hour < 18 ? 18 : 6)
    let targetDate: Date
    if hour >= 18 {
        targetDate = calendar.date(byAdding: .day, value: 1, to: date) ?? date.addingTimeInterval(86_400)
    } else {
        targetDate = date
    }

    return calendar.date(bySettingHour: targetHour, minute: 0, second: 0, of: targetDate)
        ?? date.addingTimeInterval(1_800)
}

private struct TodayTasksSnapshot: Codable {
    let schemaVersion: Int
    let generatedAtEpochMs: Int64
    let title: String
    let status: TodayTasksSnapshotStatus
    let taskCount: Int
    let tasks: [TodayTaskSnapshot]
    // Defaulted so snapshots persisted before this field existed still decode (as empty).
    let perList: [String: TodayTasksPerListSnapshot]

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let decodedTasks = try container.decodeIfPresent([TodayTaskSnapshot].self, forKey: .tasks) ?? []
        schemaVersion = try container.decodeIfPresent(Int.self, forKey: .schemaVersion) ?? 1
        generatedAtEpochMs = try container.decode(Int64.self, forKey: .generatedAtEpochMs)
        title = try container.decodeIfPresent(String.self, forKey: .title) ?? "Today's Tasks"
        status = (try? container.decodeIfPresent(TodayTasksSnapshotStatus.self, forKey: .status)) ?? (decodedTasks.isEmpty ? .empty : .tasks)
        taskCount = try container.decodeIfPresent(Int.self, forKey: .taskCount) ?? decodedTasks.count
        tasks = decodedTasks
        perList = try container.decodeIfPresent([String: TodayTasksPerListSnapshot].self, forKey: .perList) ?? [:]
    }

    /// Searches the global `tasks` aggregate first, then every per-list slice — a row rendered
    /// from a configured per-list widget (R7) lives only in `perList`, not `tasks`.
    func firstTask(withId id: String) -> TodayTaskSnapshot? {
        if let match = tasks.first(where: { $0.id == id }) {
            return match
        }
        for list in perList.values {
            if let match = list.tasks.first(where: { $0.id == id }) {
                return match
            }
        }
        return nil
    }
}

private struct TodayTasksProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> TodayTasksEntry {
        .previewTasks
    }

    func snapshot(for configuration: SelectTaskListIntent, in context: Context) async -> TodayTasksEntry {
        context.isPreview ? .previewTasks : loadEntry(configuration: configuration)
    }

    func timeline(for configuration: SelectTaskListIntent, in context: Context) async -> Timeline<TodayTasksEntry> {
        let now = Date()
        let entry = loadEntry(configuration: configuration, date: now)
        let nextRefresh = Calendar.current.date(byAdding: .minute, value: 30, to: now) ?? now.addingTimeInterval(1800)
        let nextDayNightRefresh = nextTaskWidgetDayNightRefresh(after: now)
        return Timeline(entries: [entry], policy: .after(min(nextRefresh, nextDayNightRefresh)))
    }

    /// `configuration.list == nil` (unset, or a widget placed before R7 existed) falls back to
    /// the ORIGINAL global "due today" feed. A picked list — todo OR floater — renders via the
    /// shared `PerListWidgetContentLoader`, which is what lets this "TodayTasksWidget" gallery
    /// instance render floater-shaped content when the user picked a floater list.
    private func loadEntry(configuration: SelectTaskListIntent, date: Date = Date()) -> TodayTasksEntry {
        // Checked before the snapshot is even read, so a locked device never decodes real
        // task titles into memory it isn't going to render.
        guard !WidgetAppLockStore.isEnabled else {
            let list = configuration.list
            return TodayTasksEntry(
                date: date,
                title: list?.name ?? "Today's Tasks",
                status: .locked,
                taskCount: 0,
                rows: [],
                mode: list?.kind.mode ?? .today
            )
        }
        if let list = configuration.list {
            let content = PerListWidgetContentLoader.load(list: list, date: date)
            return TodayTasksEntry(
                date: date,
                title: content.title,
                status: content.status,
                taskCount: content.taskCount,
                rows: content.rows,
                mode: content.mode
            )
        }
        return Self.loadGlobalEntry(date: date)
    }

    private static func loadGlobalEntry(date: Date) -> TodayTasksEntry {
        guard let snapshot = loadSnapshot() else {
            return TodayTasksEntry(date: date, title: "Today's Tasks", status: .setup, taskCount: 0, rows: [], mode: .today)
        }

        // Hide rows completed from the widget that the app has not drained yet — but
        // keep a row that's mid check-off animation (still shown, checked + struck)
        // until its beat ends, at which point the pending filter removes it.
        let nowMs = Int64(date.timeIntervalSince1970 * 1_000)
        let pending = WidgetPendingCompletionStore.pendingIds(kind: WidgetPendingCompletionStore.todoKind)
        let checking = WidgetPendingCompletionStore.checkingIds(kind: WidgetPendingCompletionStore.todoKind, nowEpochMs: nowMs)
        let visible = snapshot.tasks.filter { checking.contains($0.id) || !pending.contains($0.id) }
        let taskCount = max(0, snapshot.taskCount - (snapshot.tasks.count - visible.count))
        let rows = visible.map { task in
            WidgetTaskRowModel(
                id: task.id,
                title: task.title,
                priority: task.priority,
                dueEpochMs: task.dueEpochMs,
                description: task.description,
                isChecking: checking.contains(task.id)
            )
        }
        return TodayTasksEntry(
            date: date,
            title: snapshot.title,
            status: (snapshot.status == .tasks && taskCount == 0) ? .empty : TaskWidgetStatus(snapshot.status),
            taskCount: taskCount,
            rows: rows,
            mode: .today
        )
    }

    private static func loadSnapshot() -> TodayTasksSnapshot? {
        if let data = WidgetSnapshotFileStore.read(WidgetSnapshotFileStore.todayFileName),
           let snapshot = try? JSONDecoder().decode(TodayTasksSnapshot.self, from: data) {
            return snapshot
        }
        return legacyDefaultsSnapshot()
    }

    /// Transitional read of the pre-migration UserDefaults copy, so the widget still renders
    /// between installing this build and the app's first launch (which is what moves the
    /// snapshot into the protected file and deletes these keys). Read-only on purpose — the
    /// widget must not resurrect the plaintext copy.
    private static func legacyDefaultsSnapshot() -> TodayTasksSnapshot? {
        for store in legacyDefaultsStores() {
            guard let data = store.data(forKey: legacySnapshotKey),
                  let snapshot = try? JSONDecoder().decode(TodayTasksSnapshot.self, from: data) else {
                continue
            }
            return snapshot
        }
        return nil
    }

    /// Snapshot access for the completion intent's canonical-id resolution.
    static func loadWidgetSnapshot() -> TodayTasksSnapshot? {
        loadSnapshot()
    }

    private static func legacyDefaultsStores() -> [UserDefaults] {
        var stores = [UserDefaults]()
        if let shared = UserDefaults(suiteName: appGroupSuiteName) {
            stores.append(shared)
        }
        stores.append(.standard)
        return stores
    }

    private static let appGroupSuiteName = "group.com.ohmz.tday"
    private static let legacySnapshotKey = "tday.widget.todayTasksSnapshot"
}

private extension TodayTasksEntry {
    static let previewTasks = TodayTasksEntry(
        date: Date(),
        title: "Today's Tasks",
        status: .tasks,
        taskCount: 10,
        rows: [
            WidgetTaskRowModel(id: "preview-1", title: "Plan the morning", priority: "medium", dueEpochMs: Date().timeIntervalEpochMs, description: nil),
            WidgetTaskRowModel(id: "preview-2", title: "Review today", priority: "high", dueEpochMs: Date().addingTimeInterval(3_600).timeIntervalEpochMs, description: nil),
            WidgetTaskRowModel(id: "preview-3", title: "Send the quick update", priority: "low", dueEpochMs: Date().addingTimeInterval(7_200).timeIntervalEpochMs, description: nil),
            WidgetTaskRowModel(id: "preview-4", title: "Reset the evening list", priority: "medium", dueEpochMs: Date().addingTimeInterval(10_800).timeIntervalEpochMs, description: nil),
            WidgetTaskRowModel(id: "preview-5", title: "Call the contractor", priority: "high", dueEpochMs: Date().addingTimeInterval(12_600).timeIntervalEpochMs, description: nil),
            WidgetTaskRowModel(id: "preview-6", title: "Pick up groceries", priority: "medium", dueEpochMs: Date().addingTimeInterval(14_400).timeIntervalEpochMs, description: nil),
            WidgetTaskRowModel(id: "preview-7", title: "Prep tomorrow", priority: "low", dueEpochMs: Date().addingTimeInterval(16_200).timeIntervalEpochMs, description: nil),
            WidgetTaskRowModel(id: "preview-8", title: "Evening reset", priority: "medium", dueEpochMs: Date().addingTimeInterval(18_000).timeIntervalEpochMs, description: nil),
            WidgetTaskRowModel(id: "preview-9", title: "Queue notes", priority: "low", dueEpochMs: Date().addingTimeInterval(19_800).timeIntervalEpochMs, description: nil),
            WidgetTaskRowModel(id: "preview-10", title: "Close the loop", priority: "medium", dueEpochMs: Date().addingTimeInterval(21_600).timeIntervalEpochMs, description: nil)
        ],
        mode: .today
    )

    static let previewEmpty = TodayTasksEntry(date: Date(), title: "Today's Tasks", status: .empty, taskCount: 0, rows: [], mode: .today)

    static let previewSetup = TodayTasksEntry(date: Date(), title: "Today's Tasks", status: .setup, taskCount: 0, rows: [], mode: .today)

    static let previewLocked = TodayTasksEntry(date: Date(), title: "Today's Tasks", status: .locked, taskCount: 0, rows: [], mode: .today)
}

private struct TodayTasksWidgetView: View {
    let entry: TodayTasksEntry

    var body: some View {
        TdayTasksWidgetContent(
            title: entry.title,
            status: entry.status,
            taskCount: entry.taskCount,
            rows: entry.rows,
            date: entry.date,
            mode: entry.mode
        )
    }
}

private struct WidgetTaskRowModel: Identifiable {
    let id: String
    let title: String
    let priority: String
    let dueEpochMs: Int64?
    let description: String?
    /// Mid check-off: render the ring filled + the title struck-through for one beat.
    var isChecking: Bool = false
    /// Past due (R7 per-list widgets only — the global Today aggregate is strictly "due
    /// today" and never produces an overdue row): tints the due-time chip red instead of
    /// fabricating a second due-time text style.
    var isOverdue: Bool = false

    var note: String? {
        guard let trimmed = description?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        return trimmed
    }
}

private enum TaskWidgetStatus: Equatable {
    case setup
    case empty
    case tasks
    case locked

    init(_ status: TodayTasksSnapshotStatus) {
        switch status {
        case .setup:
            self = .setup
        case .empty:
            self = .empty
        case .tasks:
            self = .tasks
        }
    }

    init(_ status: FloaterTasksSnapshotStatus) {
        switch status {
        case .setup:
            self = .setup
        case .empty:
            self = .empty
        case .tasks:
            self = .tasks
        }
    }

}

private enum TaskWidgetMode {
    case today
    case floater

    var openURL: URL {
        switch self {
        case .today:
            return URL(string: "tday://home")!
        case .floater:
            return URL(string: "tday://floater")!
        }
    }

    var createURL: URL {
        switch self {
        case .today:
            return URL(string: "tday://todos/create?target=today")!
        case .floater:
            return URL(string: "tday://todos/create?target=floater")!
        }
    }

    var countUnit: String {
        switch self {
        case .today:
            return "due"
        case .floater:
            return "open"
        }
    }

    var emptyTitle: String {
        switch self {
        case .today:
            return "No tasks due today"
        case .floater:
            return "No floater tasks"
        }
    }

    var emptySubtitle: String {
        switch self {
        case .today:
            return "Add one for today"
        case .floater:
            return "Add a floater"
        }
    }

    func emptyWatermarkSystemName(isDaytime: Bool) -> String {
        switch self {
        case .today:
            return isDaytime ? "sun.max.fill" : "moon.stars.fill"
        case .floater:
            return "leaf"
        }
    }

    var addAccessibilityLabel: String {
        switch self {
        case .today:
            return "Add task for today"
        case .floater:
            return "Add floater task"
        }
    }

    var showsDueTime: Bool {
        self == .today
    }

    var completionKind: String {
        switch self {
        case .today:
            return WidgetPendingCompletionStore.todoKind
        case .floater:
            return WidgetPendingCompletionStore.floaterKind
        }
    }

    func accentColor(renderingMode: WidgetRenderingMode) -> Color {
        guard renderingMode == .fullColor else {
            return .primary
        }
        switch self {
        case .today:
            return .tdayTodayBlue
        case .floater:
            return .tdayFloaterGreen
        }
    }

}

/// Rendered content for ONE configured-to-a-list widget instance (R7), before being wrapped
/// into either `TodayTasksEntry` or `FloaterTasksEntry`. Shared because either provider can be
/// asked to render EITHER list kind — the "TodayTasksWidget" gallery slot renders
/// floater-shaped content when the user picked a floater list, and vice versa — so this logic
/// belongs to neither provider alone.
private struct PerListWidgetContent {
    let title: String
    let status: TaskWidgetStatus
    let taskCount: Int
    let rows: [WidgetTaskRowModel]
    let mode: TaskWidgetMode
}

private enum PerListWidgetContentLoader {
    static func load(list: TdayWidgetListEntity, date: Date) -> PerListWidgetContent {
        switch list.kind {
        case .todo:
            return loadTodoList(list: list, date: date)
        case .floater:
            return loadFloaterList(list: list, date: date)
        }
    }

    /// That list's due-today-OR-OVERDUE pending todos (see `TodayTasksWidgetSnapshotStore
    /// .makeSnapshot`'s `perList` comment for why the window is wider than the global feed).
    /// `isOverdue` is computed live against `date`, not persisted, so it stays correct across
    /// timeline refreshes even though the underlying snapshot file only refreshes on state
    /// change.
    private static func loadTodoList(list: TdayWidgetListEntity, date: Date) -> PerListWidgetContent {
        guard let snapshot = TodayTasksProvider.loadWidgetSnapshot(),
              let listSnapshot = snapshot.perList[list.listId] else {
            return PerListWidgetContent(title: list.name, status: .empty, taskCount: 0, rows: [], mode: .today)
        }

        let nowMs = Int64(date.timeIntervalSince1970 * 1_000)
        let pending = WidgetPendingCompletionStore.pendingIds(kind: WidgetPendingCompletionStore.todoKind)
        let checking = WidgetPendingCompletionStore.checkingIds(kind: WidgetPendingCompletionStore.todoKind, nowEpochMs: nowMs)
        let visible = listSnapshot.tasks.filter { checking.contains($0.id) || !pending.contains($0.id) }
        let taskCount = max(0, listSnapshot.totalCount - (listSnapshot.tasks.count - visible.count))
        let rows = visible.map { task in
            WidgetTaskRowModel(
                id: task.id,
                title: task.title,
                priority: task.priority,
                dueEpochMs: task.dueEpochMs,
                description: task.description,
                isChecking: checking.contains(task.id),
                isOverdue: task.dueEpochMs < nowMs
            )
        }
        return PerListWidgetContent(
            title: list.name,
            status: rows.isEmpty ? .empty : .tasks,
            taskCount: taskCount,
            rows: rows,
            mode: .today
        )
    }

    private static func loadFloaterList(list: TdayWidgetListEntity, date: Date) -> PerListWidgetContent {
        guard let snapshot = FloaterTasksProvider.loadWidgetSnapshot(),
              let listSnapshot = snapshot.perList[list.listId] else {
            return PerListWidgetContent(title: list.name, status: .empty, taskCount: 0, rows: [], mode: .floater)
        }

        let nowMs = Int64(date.timeIntervalSince1970 * 1_000)
        let pending = WidgetPendingCompletionStore.pendingIds(kind: WidgetPendingCompletionStore.floaterKind)
        let checking = WidgetPendingCompletionStore.checkingIds(kind: WidgetPendingCompletionStore.floaterKind, nowEpochMs: nowMs)
        let visible = listSnapshot.tasks.filter { checking.contains($0.id) || !pending.contains($0.id) }
        let taskCount = max(0, listSnapshot.totalCount - (listSnapshot.tasks.count - visible.count))
        let rows = visible.map { task in
            WidgetTaskRowModel(
                id: task.id,
                title: task.title,
                priority: task.priority,
                dueEpochMs: nil,
                description: nil,
                isChecking: checking.contains(task.id)
            )
        }
        return PerListWidgetContent(
            title: list.name,
            status: rows.isEmpty ? .empty : .tasks,
            taskCount: taskCount,
            rows: rows,
            mode: .floater
        )
    }
}

private struct TdayTasksWidgetContent: View {
    let title: String
    let status: TaskWidgetStatus
    let taskCount: Int
    let rows: [WidgetTaskRowModel]
    let date: Date
    let mode: TaskWidgetMode

    @Environment(\.widgetFamily) private var family
    @Environment(\.widgetRenderingMode) private var renderingMode
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        ZStack(alignment: .topLeading) {
            messageWatermark

            switch status {
            case .setup:
                message(title: "Open T'Day", subtitle: "Set up your workspace")
            case .empty:
                message(title: mode.emptyTitle, subtitle: "")
            case .locked:
                lockedMessage
            case .tasks:
                EmptyView()
            }

            VStack(alignment: .leading, spacing: metrics.contentSpacing) {
                header

                if status == .tasks {
                    taskList
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .padding(.horizontal, metrics.horizontalInset)
            .padding(.top, metrics.topInset)
            .padding(.bottom, metrics.bottomInset)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .widgetURL(mode.openURL)
        .containerBackground(for: .widget) {
            widgetBackground
        }
    }

    private var metrics: WidgetLayoutMetrics {
        WidgetLayoutMetrics(family: family)
    }

    private var accentColor: Color {
        mode.accentColor(renderingMode: renderingMode)
    }

    private var secondaryTextColor: Color {
        renderingMode == .fullColor ? .secondary : .primary.opacity(0.72)
    }

    private var watermarkColor: Color {
        guard renderingMode == .fullColor else {
            return .primary.opacity(0.08)
        }
        let color: Color
        switch mode {
        case .today where !isTaskWidgetDaytime(date):
            color = .tdayTitleNight
        default:
            color = accentColor
        }
        return color.opacity(colorScheme == .dark ? 0.16 : 0.11)
    }

    private var countText: String {
        "\(taskCount) \(mode.countUnit)"
    }

    private var widgetBackground: some View {
        colorScheme == .dark ? Color.tdayDarkSurface : Color.tdayLightSurface
    }

    private var messageWatermark: some View {
        GeometryReader { proxy in
            Image(systemName: mode.emptyWatermarkSystemName(isDaytime: isTaskWidgetDaytime(date)))
                .font(.system(size: metrics.watermarkSize, weight: .regular))
                .foregroundStyle(watermarkColor)
                .rotationEffect(.degrees(-7))
                .frame(width: metrics.watermarkSize, height: metrics.watermarkSize)
                .position(
                    x: proxy.size.width - (metrics.watermarkSize / 2) + metrics.watermarkTrailingOffset,
                    y: proxy.size.height * metrics.watermarkVerticalFraction
                )
        }
        .accessibilityHidden(true)
    }

    private var header: some View {
        HStack(spacing: 8) {
            if family == .systemSmall {
                if status == .tasks {
                    smallCountLabel
                }
            } else {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(title)
                        .font(.system(size: family == .systemLarge ? 17 : 16, weight: .bold, design: .rounded))
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)

                    if status == .tasks {
                        countPill
                    }
                }
            }

            Spacer(minLength: 4)
            addButton
        }
        .frame(minHeight: metrics.headerHeight)
    }

    private var smallCountLabel: some View {
        Text(countText)
            .font(.system(size: 18, weight: .heavy, design: .rounded))
            .foregroundStyle(secondaryTextColor)
            .lineLimit(1)
            .minimumScaleFactor(0.85)
            .accessibilityLabel(countText)
    }

    private var countPill: some View {
        Text(countText)
            .font(.system(size: 12, weight: .heavy, design: .rounded))
            .foregroundStyle(secondaryTextColor)
            .lineLimit(1)
            .minimumScaleFactor(0.85)
            .padding(.horizontal, 2)
            .frame(height: 26)
    }

    private var addButton: some View {
        Link(destination: mode.createURL) {
            Image(systemName: "plus")
                .font(.system(size: 17, weight: .heavy, design: .rounded))
                .foregroundStyle(accentColor)
                .frame(width: metrics.addButtonSize, height: metrics.addButtonSize)
                .background(
                    RoundedRectangle(cornerRadius: metrics.addButtonCornerRadius, style: .continuous)
                        .fill(accentColor.opacity(renderingMode == .fullColor ? 0.14 : 0.10))
                )
                .contentShape(Rectangle())
                .widgetAccentable()
        }
        .buttonStyle(.plain)
        .accessibilityLabel(mode.addAccessibilityLabel)
    }

    private var taskList: some View {
        // WidgetKit system-family widgets do not support true in-widget scrolling, so iOS keeps a best-fit row set plus overflow text.
        let totalCount = max(taskCount, rows.count)
        let visibleRows = visibleTaskRows(totalCount: totalCount)
        let overflowCount = max(0, totalCount - visibleRows.count)

        let hasOverflow = overflowCount > 0

        // spacing 0: the inter-row gap is recreated by rowDivider's own vertical padding,
        // so the separator lives INSIDE the existing gap and adds no height — the row-fit
        // count (3 medium / 9 large) stays exactly the same as without dividers.
        return VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(visibleRows.enumerated()), id: \.element.id) { index, row in
                taskRow(row)
                if index < visibleRows.count - 1 || hasOverflow {
                    rowDivider
                }
            }
            if hasOverflow {
                overflowRow(count: overflowCount)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    /// Native Notes-widget-style separator between rows. It sits within the existing
    /// inter-row gap (vertical padding = (rowSpacing − lineHeight) / 2 on each side), so
    /// introducing it costs no extra height and never pushes a task out of the widget.
    private var rowDivider: some View {
        let lineHeight: CGFloat = 0.75
        return Rectangle()
            .fill(dividerColor)
            .frame(maxWidth: .infinity)
            .frame(height: lineHeight)
            .padding(.vertical, max(0, (metrics.rowSpacing - lineHeight) / 2))
    }

    private var dividerColor: Color {
        guard renderingMode == .fullColor else {
            return Color.primary.opacity(0.12)
        }
        return Color.primary.opacity(colorScheme == .dark ? 0.17 : 0.12)
    }

    private func rowUnitCost(_ row: WidgetTaskRowModel) -> Int {
        metrics.showsNotes && row.note != nil ? 2 : 1
    }

    private func visibleTaskRows(totalCount: Int) -> [WidgetTaskRowModel] {
        let capacity = metrics.rowUnitCapacity

        // If every task is available and fits within the unit budget, show
        // them all and skip the overflow row entirely.
        if rows.count >= totalCount {
            let totalUnits = rows.reduce(0) { $0 + rowUnitCost($1) }
            if totalUnits <= capacity {
                return rows
            }
        }

        // Otherwise reserve 1 unit for the "+X more" row and fill rows
        // greedily in order until the next row would no longer fit.
        var visible: [WidgetTaskRowModel] = []
        var usedUnits = 0
        for row in rows {
            let cost = rowUnitCost(row)
            guard usedUnits + cost + 1 <= capacity else {
                break
            }
            visible.append(row)
            usedUnits += cost
        }
        return visible
    }

    private var lockedMessage: some View {
        VStack(alignment: .center, spacing: family == .systemSmall ? 4 : 6) {
            Image(systemName: "lock.fill")
                .font(.system(size: family == .systemSmall ? 15 : 18, weight: .semibold))
                .foregroundStyle(secondaryTextColor)
            Text("T'Day is locked")
                .font(.system(size: family == .systemSmall ? 14 : (family == .systemLarge ? 17 : 15), weight: .bold, design: .rounded))
                .lineLimit(family == .systemSmall ? 1 : 2)
                .minimumScaleFactor(family == .systemSmall ? 0.75 : 0.85)

            if family != .systemSmall {
                Text("Tasks are hidden while app lock is on")
                    .font(.system(size: 12, weight: .bold, design: .rounded))
                    .foregroundStyle(secondaryTextColor)
                    .lineLimit(2)
                    .minimumScaleFactor(0.85)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        .multilineTextAlignment(.center)
        .padding(.horizontal, metrics.horizontalInset)
    }

    private func message(title: String, subtitle: String) -> some View {
        VStack(alignment: .center, spacing: family == .systemSmall ? 6 : 8) {
            Text(title)
                .font(.system(size: family == .systemSmall ? 14 : (family == .systemLarge ? 17 : 15), weight: .bold, design: .rounded))
                .lineLimit(family == .systemSmall ? 1 : 2)
                .minimumScaleFactor(family == .systemSmall ? 0.75 : 0.85)

            if family != .systemSmall, !subtitle.isEmpty {
                Text(subtitle)
                    .font(.system(size: 12, weight: .bold, design: .rounded))
                    .foregroundStyle(secondaryTextColor)
                    .lineLimit(2)
                    .minimumScaleFactor(0.85)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        .multilineTextAlignment(.center)
        .padding(.horizontal, metrics.horizontalInset)
    }

    private func taskRow(_ row: WidgetTaskRowModel) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 7) {
            completeButton(for: row)
                // Pin the ring to the first line (near its vertical centre) instead
                // of centring it across a wrapped two-line title.
                .alignmentGuide(.firstTextBaseline) { dimension in
                    dimension[VerticalAlignment.center] + 5
                }
            VStack(alignment: .leading, spacing: 1) {
                Text(row.title)
                    .font(.system(size: metrics.rowFontSize, weight: .bold, design: .rounded))
                    .strikethrough(row.isChecking, color: secondaryTextColor)
                    .lineLimit(2)
                if metrics.showsNotes, let note = row.note {
                    Text(note)
                        .font(.system(size: metrics.rowFontSize - 2, weight: .semibold, design: .rounded))
                        .foregroundStyle(secondaryTextColor)
                        .strikethrough(row.isChecking, color: secondaryTextColor)
                        .lineLimit(2)
                }
            }
            Spacer(minLength: 4)
            if mode.showsDueTime, family != .systemSmall, let dueEpochMs = row.dueEpochMs {
                dueTimeChip(Self.dueTimeText(from: dueEpochMs), isOverdue: row.isOverdue)
            }
        }
        .foregroundStyle(.primary)
        // Dim the whole row as it's checked off, so the fade reads as "leaving".
        .opacity(row.isChecking ? 0.55 : 1)
        .frame(minHeight: metrics.rowHeight, alignment: .leading)
        // WidgetKit tweens between reloads; naming the animated values lets the ring
        // fill + strikethrough + dim animate rather than hard-cut.
        .animation(.easeInOut(duration: 0.2), value: row.isChecking)
        .accessibilityLabel(accessibilityLabel(for: row))
    }

    /// `isOverdue` only ever arrives true from a per-list widget (R7) — the global Today feed
    /// is strictly "due today" and never produces one, so this is a pure addition for existing
    /// widgets (isOverdue defaults false, tint is unchanged).
    private func dueTimeChip(_ text: String, isOverdue: Bool) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .bold, design: .rounded))
            .foregroundStyle(isOverdue ? overdueColor : secondaryTextColor)
            .lineLimit(1)
            .minimumScaleFactor(0.85)
            .padding(.horizontal, 2)
            .frame(height: 22)
    }

    /// Reuses the same red the priority-High ring already carries, rather than introducing a
    /// third accent color for a widget with an already-tight palette.
    private var overdueColor: Color {
        guard renderingMode == .fullColor else {
            return .primary.opacity(0.78)
        }
        return colorScheme == .dark ? .tdayPriorityHighDark : .tdayPriorityHigh
    }

    private func overflowRow(count: Int) -> some View {
        Text("+\(count) more")
            .font(.system(size: 11, weight: .heavy, design: .rounded))
            .foregroundStyle(secondaryTextColor)
            .lineLimit(1)
            .padding(.leading, 16)
            .frame(height: metrics.rowHeight, alignment: .leading)
    }

    /// Tappable check ring (widgets v2): completes the task in place without
    /// opening the app. Keeps the priority colour the old leading dot carried.
    private func completeButton(for row: WidgetTaskRowModel) -> some View {
        let ringColor = mode == .floater ? accentColor : widgetPriorityColor(row.priority)
        return Button(intent: CompleteWidgetTaskIntent(kind: mode.completionKind, taskID: row.id)) {
            ZStack {
                Circle()
                    .strokeBorder(ringColor, lineWidth: 1.6)
                // Fill + checkmark for the checked frame of the animation.
                Circle()
                    .fill(ringColor)
                    .opacity(row.isChecking ? 1 : 0)
                Image(systemName: "checkmark")
                    .font(.system(size: 8, weight: .heavy))
                    .foregroundStyle(.white)
                    .opacity(row.isChecking ? 1 : 0)
            }
            .frame(width: 14, height: 14)
            .contentShape(Circle())
            .animation(.easeInOut(duration: 0.2), value: row.isChecking)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Complete \(row.title)")
    }

    private func widgetPriorityColor(_ priority: String) -> Color {
        guard renderingMode == .fullColor else {
            return .primary.opacity(0.78)
        }

        switch priority.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "high", "urgent":
            return colorScheme == .dark ? .tdayPriorityHighDark : .tdayPriorityHigh
        case "medium", "important":
            return colorScheme == .dark ? .tdayPriorityMediumDark : .tdayPriorityMedium
        default:
            return colorScheme == .dark ? .tdayPriorityLowDark : .tdayPriorityLow
        }
    }

    private func accessibilityLabel(for row: WidgetTaskRowModel) -> String {
        guard mode.showsDueTime, let dueEpochMs = row.dueEpochMs else {
            return row.title
        }
        let dueText = Self.dueTimeText(from: dueEpochMs)
        return row.isOverdue ? "\(row.title), overdue, \(dueText)" : "\(row.title), due \(dueText)"
    }

    private static func dueTimeText(from epochMs: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(epochMs) / 1_000)
        return date.formatted(date: .omitted, time: .shortened)
    }
}

private struct WidgetLayoutMetrics {
    let contentSpacing: CGFloat
    let headerHeight: CGFloat
    let addButtonSize: CGFloat
    let addButtonCornerRadius: CGFloat
    let rowHeight: CGFloat
    let rowSpacing: CGFloat
    let rowFontSize: CGFloat
    // Vertical budget in row units: a noteless row costs 1 unit, a row with a
    // visible note costs 2 units, and the "+X more" overflow row costs 1 unit.
    let rowUnitCapacity: Int
    let showsNotes: Bool
    // Custom content insets (default WidgetKit content margins are disabled) so tasks run
    // closer edge-to-edge and reclaim the horizontal space the bullet indent used to waste.
    let horizontalInset: CGFloat
    let topInset: CGFloat
    let bottomInset: CGFloat
    let watermarkSize: CGFloat
    let watermarkTrailingOffset: CGFloat
    let watermarkVerticalFraction: CGFloat

    init(family: WidgetFamily) {
        switch family {
        case .systemSmall:
            // Small shares medium's insets, header, + button, row height, spacing and font so
            // its padding and task placement match the wider sizes exactly (parity with the
            // Android small widget). Only the row capacity, notes and watermark stay small — a
            // 2x2 just shows fewer rows, and its header still leads with the count (the title +
            // count + button can't fit a 2x2 width).
            contentSpacing = 7
            headerHeight = 42
            addButtonSize = 42
            addButtonCornerRadius = 13
            rowHeight = 22
            rowSpacing = 3
            rowFontSize = 12
            rowUnitCapacity = 2
            showsNotes = false
            horizontalInset = 14
            topInset = 13
            bottomInset = 11
            watermarkSize = 116
            watermarkTrailingOffset = 18
            watermarkVerticalFraction = 0.70
        case .systemLarge:
            contentSpacing = 8
            headerHeight = 45
            addButtonSize = 46
            addButtonCornerRadius = 14
            rowHeight = 24
            rowSpacing = 4
            rowFontSize = 13
            // ~306pt usable - 45pt header - 8pt spacing ~= 253pt; 9 x 28pt units - 4 ~= 248pt.
            rowUnitCapacity = 9
            showsNotes = true
            horizontalInset = 15
            topInset = 14
            bottomInset = 12
            watermarkSize = 224
            watermarkTrailingOffset = 28
            watermarkVerticalFraction = 0.68
        default:
            contentSpacing = 7
            headerHeight = 42
            addButtonSize = 42
            addButtonCornerRadius = 13
            rowHeight = 22
            rowSpacing = 3
            rowFontSize = 12
            rowUnitCapacity = 3
            showsNotes = true
            horizontalInset = 14
            topInset = 13
            bottomInset = 11
            watermarkSize = 164
            watermarkTrailingOffset = 22
            watermarkVerticalFraction = 0.68
        }
    }
}

struct TodayTasksWidget: Widget {
    let kind = "TodayTasksWidget"

    var body: some WidgetConfiguration {
        // R7: AppIntentConfiguration (was StaticConfiguration) lets the user pick a specific
        // list from "Edit Widget" — any todo OR floater list (`TdayWidgetListEntityQuery`),
        // rendering in whichever shape that list's type calls for. Leaving the picker unset
        // keeps the ORIGINAL global "due today" feed this kind always showed — including for
        // every instance placed before this shipped, which iOS resolves against the intent's
        // nil default.
        AppIntentConfiguration(kind: kind, intent: SelectTaskListIntent.self, provider: TodayTasksProvider()) { entry in
            TodayTasksWidgetView(entry: entry)
        }
        .configurationDisplayName("Today's Tasks")
        .description("Shows today's tasks, or pick a specific list.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
        .containerBackgroundRemovable(true)
        .contentMarginsDisabled()
    }
}

/// See the Today twin's comment on `TodayTasksEntry` for why `status`/`mode` now live on the
/// entry rather than a bool + a view-hardcoded constant.
private struct FloaterTasksEntry: TimelineEntry {
    let date: Date
    let title: String
    let status: TaskWidgetStatus
    let taskCount: Int
    let rows: [WidgetTaskRowModel]
    let mode: TaskWidgetMode
}

private struct FloaterTaskSnapshot: Codable, Identifiable {
    let id: String
    let title: String
    let priority: String
    // Backend-completion payload (widgets v2 instant sync): the CANONICAL id the
    // /api/floater/complete endpoint expects. Defaulted so snapshots persisted
    // before this existed still decode (canonicalId falls back to the display id).
    let canonicalId: String

    init(
        id: String,
        title: String,
        priority: String,
        canonicalId: String? = nil
    ) {
        self.id = id
        self.title = title
        self.priority = priority
        self.canonicalId = canonicalId ?? id
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let decodedId = try container.decode(String.self, forKey: .id)
        id = decodedId
        title = try container.decode(String.self, forKey: .title)
        priority = try container.decode(String.self, forKey: .priority)
        canonicalId = try container.decodeIfPresent(String.self, forKey: .canonicalId) ?? decodedId
    }
}

private enum FloaterTasksSnapshotStatus: String, Codable {
    case setup
    case empty
    case tasks
}

/// Widget-side twin of the app's `FloaterTasksWidgetPerListSnapshot` — see the Today twin,
/// `TodayTasksPerListSnapshot`.
private struct FloaterTasksPerListSnapshot: Codable {
    let totalCount: Int
    let tasks: [FloaterTaskSnapshot]
}

private struct FloaterTasksSnapshot: Codable {
    let schemaVersion: Int
    let generatedAtEpochMs: Int64
    let title: String
    let status: FloaterTasksSnapshotStatus
    let taskCount: Int
    let tasks: [FloaterTaskSnapshot]
    // Defaulted so snapshots persisted before this field existed still decode (as empty).
    let perList: [String: FloaterTasksPerListSnapshot]

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let decodedTasks = try container.decodeIfPresent([FloaterTaskSnapshot].self, forKey: .tasks) ?? []
        schemaVersion = try container.decodeIfPresent(Int.self, forKey: .schemaVersion) ?? 1
        generatedAtEpochMs = try container.decode(Int64.self, forKey: .generatedAtEpochMs)
        title = try container.decodeIfPresent(String.self, forKey: .title) ?? "Floater Tasks"
        status = (try? container.decodeIfPresent(FloaterTasksSnapshotStatus.self, forKey: .status)) ?? (decodedTasks.isEmpty ? .empty : .tasks)
        taskCount = try container.decodeIfPresent(Int.self, forKey: .taskCount) ?? decodedTasks.count
        tasks = decodedTasks
        perList = try container.decodeIfPresent([String: FloaterTasksPerListSnapshot].self, forKey: .perList) ?? [:]
    }

    /// See the Today twin's `firstTask(withId:)` — searches `tasks` then every `perList` slice.
    func firstTask(withId id: String) -> FloaterTaskSnapshot? {
        if let match = tasks.first(where: { $0.id == id }) {
            return match
        }
        for list in perList.values {
            if let match = list.tasks.first(where: { $0.id == id }) {
                return match
            }
        }
        return nil
    }
}

private struct FloaterTasksProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> FloaterTasksEntry {
        .previewTasks
    }

    func snapshot(for configuration: SelectTaskListIntent, in context: Context) async -> FloaterTasksEntry {
        context.isPreview ? .previewTasks : loadEntry(configuration: configuration)
    }

    func timeline(for configuration: SelectTaskListIntent, in context: Context) async -> Timeline<FloaterTasksEntry> {
        let entry = loadEntry(configuration: configuration)
        let nextRefresh = Calendar.current.date(byAdding: .minute, value: 30, to: Date()) ?? Date().addingTimeInterval(1800)
        return Timeline(entries: [entry], policy: .after(nextRefresh))
    }

    /// See the Today twin's `loadEntry(configuration:date:)` — identical nil-falls-back-to-
    /// global, list-present-renders-via-`PerListWidgetContentLoader` shape.
    private func loadEntry(configuration: SelectTaskListIntent) -> FloaterTasksEntry {
        // Checked before the snapshot is even read, so a locked device never decodes real
        // task titles into memory it isn't going to render.
        guard !WidgetAppLockStore.isEnabled else {
            let list = configuration.list
            return FloaterTasksEntry(
                date: Date(),
                title: list?.name ?? "Floater Tasks",
                status: .locked,
                taskCount: 0,
                rows: [],
                mode: list?.kind.mode ?? .floater
            )
        }
        if let list = configuration.list {
            let content = PerListWidgetContentLoader.load(list: list, date: Date())
            return FloaterTasksEntry(
                date: Date(),
                title: content.title,
                status: content.status,
                taskCount: content.taskCount,
                rows: content.rows,
                mode: content.mode
            )
        }
        return Self.loadGlobalEntry()
    }

    private static func loadGlobalEntry() -> FloaterTasksEntry {
        guard let snapshot = loadSnapshot() else {
            return FloaterTasksEntry(date: Date(), title: "Floater Tasks", status: .setup, taskCount: 0, rows: [], mode: .floater)
        }

        // Hide rows completed from the widget that the app has not drained yet — but
        // keep a row mid check-off animation (checked + struck) until its beat ends.
        let nowMs = Int64(Date().timeIntervalSince1970 * 1_000)
        let pending = WidgetPendingCompletionStore.pendingIds(kind: WidgetPendingCompletionStore.floaterKind)
        let checking = WidgetPendingCompletionStore.checkingIds(kind: WidgetPendingCompletionStore.floaterKind, nowEpochMs: nowMs)
        let visible = snapshot.tasks.filter { checking.contains($0.id) || !pending.contains($0.id) }
        let taskCount = max(0, snapshot.taskCount - (snapshot.tasks.count - visible.count))
        let rows = visible.map { task in
            WidgetTaskRowModel(
                id: task.id,
                title: task.title,
                priority: task.priority,
                dueEpochMs: nil,
                description: nil,
                isChecking: checking.contains(task.id)
            )
        }
        return FloaterTasksEntry(
            // Preserves the pre-R7 quirk: the global entry's `date` is the snapshot's
            // generation time, not wall-clock — Floater has no day/night refresh tied to it.
            date: Date(timeIntervalSince1970: TimeInterval(snapshot.generatedAtEpochMs) / 1_000),
            title: snapshot.title,
            status: (snapshot.status == .tasks && taskCount == 0) ? .empty : TaskWidgetStatus(snapshot.status),
            taskCount: taskCount,
            rows: rows,
            mode: .floater
        )
    }

    private static func loadSnapshot() -> FloaterTasksSnapshot? {
        if let data = WidgetSnapshotFileStore.read(WidgetSnapshotFileStore.floaterFileName),
           let snapshot = try? JSONDecoder().decode(FloaterTasksSnapshot.self, from: data) {
            return snapshot
        }
        return legacyDefaultsSnapshot()
    }

    /// Transitional read of the pre-migration UserDefaults copy — see the Today provider.
    private static func legacyDefaultsSnapshot() -> FloaterTasksSnapshot? {
        for store in legacyDefaultsStores() {
            guard let data = store.data(forKey: legacySnapshotKey),
                  let snapshot = try? JSONDecoder().decode(FloaterTasksSnapshot.self, from: data) else {
                continue
            }
            return snapshot
        }
        return nil
    }

    /// Snapshot access for the completion intent's canonical-id resolution.
    static func loadWidgetSnapshot() -> FloaterTasksSnapshot? {
        loadSnapshot()
    }

    private static func legacyDefaultsStores() -> [UserDefaults] {
        var stores = [UserDefaults]()
        if let shared = UserDefaults(suiteName: appGroupSuiteName) {
            stores.append(shared)
        }
        stores.append(.standard)
        return stores
    }

    private static let appGroupSuiteName = "group.com.ohmz.tday"
    private static let legacySnapshotKey = "tday.widget.floaterTasksSnapshot"
}

private extension FloaterTasksEntry {
    static let previewTasks = FloaterTasksEntry(
        date: Date(),
        title: "Floater Tasks",
        status: .tasks,
        taskCount: 10,
        rows: [
            WidgetTaskRowModel(id: "preview-1", title: "Draft the idea", priority: "high", dueEpochMs: nil, description: nil),
            WidgetTaskRowModel(id: "preview-2", title: "Queue reading", priority: "medium", dueEpochMs: nil, description: nil),
            WidgetTaskRowModel(id: "preview-3", title: "Try the new shortcut", priority: "low", dueEpochMs: nil, description: nil),
            WidgetTaskRowModel(id: "preview-4", title: "Collect shelf notes", priority: "medium", dueEpochMs: nil, description: nil),
            WidgetTaskRowModel(id: "preview-5", title: "Sketch someday flow", priority: "high", dueEpochMs: nil, description: nil),
            WidgetTaskRowModel(id: "preview-6", title: "Compare tools", priority: "medium", dueEpochMs: nil, description: nil),
            WidgetTaskRowModel(id: "preview-7", title: "Make the checklist", priority: "low", dueEpochMs: nil, description: nil),
            WidgetTaskRowModel(id: "preview-8", title: "Sort bookmarks", priority: "medium", dueEpochMs: nil, description: nil),
            WidgetTaskRowModel(id: "preview-9", title: "Ask about the vendor", priority: "low", dueEpochMs: nil, description: nil),
            WidgetTaskRowModel(id: "preview-10", title: "Polish notes", priority: "medium", dueEpochMs: nil, description: nil)
        ],
        mode: .floater
    )

    static let previewEmpty = FloaterTasksEntry(date: Date(), title: "Floater Tasks", status: .empty, taskCount: 0, rows: [], mode: .floater)

    static let previewSetup = FloaterTasksEntry(date: Date(), title: "Floater Tasks", status: .setup, taskCount: 0, rows: [], mode: .floater)

    static let previewLocked = FloaterTasksEntry(date: Date(), title: "Floater Tasks", status: .locked, taskCount: 0, rows: [], mode: .floater)
}

private struct FloaterTasksWidgetView: View {
    let entry: FloaterTasksEntry

    var body: some View {
        TdayTasksWidgetContent(
            title: entry.title,
            status: entry.status,
            taskCount: entry.taskCount,
            rows: entry.rows,
            date: entry.date,
            mode: entry.mode
        )
    }
}

struct FloaterTasksWidget: Widget {
    let kind = "FloaterTasksWidget"

    var body: some WidgetConfiguration {
        // R7: see TodayTasksWidget's body for the same AppIntentConfiguration rationale — this
        // kind's picker offers the identical todo+floater list catalog.
        AppIntentConfiguration(kind: kind, intent: SelectTaskListIntent.self, provider: FloaterTasksProvider()) { entry in
            FloaterTasksWidgetView(entry: entry)
        }
        .configurationDisplayName("Floater Tasks")
        .description("Shows your floater tasks, or pick a specific list.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
        .containerBackgroundRemovable(true)
        .contentMarginsDisabled()
    }
}

@main
struct TdayWidgetBundle: WidgetBundle {
    var body: some Widget {
        TodayTasksWidget()
        FloaterTasksWidget()
    }
}

private extension Color {
    static let tdayTodayBlue = Color(red: 110.0 / 255.0, green: 168.0 / 255.0, blue: 225.0 / 255.0)
    static let tdayTitleNight = Color(red: 168.0 / 255.0, green: 184.0 / 255.0, blue: 232.0 / 255.0)
    static let tdayFloaterGreen = Color(red: 77.0 / 255.0, green: 143.0 / 255.0, blue: 131.0 / 255.0)
    static let tdayLightSurface = Color.white
    static let tdayDarkSurface = Color(red: 0.09, green: 0.10, blue: 0.13)
    static let tdayPriorityHigh = Color(red: 1.0, green: 59.0 / 255.0, blue: 48.0 / 255.0)
    static let tdayPriorityMedium = Color(red: 1.0, green: 149.0 / 255.0, blue: 0.0)
    static let tdayPriorityLow = Color(red: 0.0, green: 122.0 / 255.0, blue: 1.0)
    static let tdayPriorityHighDark = Color(red: 1.0, green: 107.0 / 255.0, blue: 97.0 / 255.0)
    static let tdayPriorityMediumDark = Color(red: 1.0, green: 180.0 / 255.0, blue: 84.0 / 255.0)
    static let tdayPriorityLowDark = Color(red: 121.0 / 255.0, green: 184.0 / 255.0, blue: 1.0)
}

private extension Date {
    var timeIntervalEpochMs: Int64 {
        Int64(timeIntervalSince1970 * 1_000)
    }
}

#if DEBUG
#Preview("Small Tasks", as: .systemSmall) {
    TodayTasksWidget()
} timeline: {
    TodayTasksEntry.previewTasks
}

#Preview("Medium Tasks", as: .systemMedium) {
    TodayTasksWidget()
} timeline: {
    TodayTasksEntry.previewTasks
}

#Preview("Large Tasks", as: .systemLarge) {
    TodayTasksWidget()
} timeline: {
    TodayTasksEntry.previewTasks
}

#Preview("Empty", as: .systemMedium) {
    TodayTasksWidget()
} timeline: {
    TodayTasksEntry.previewEmpty
}

#Preview("Setup", as: .systemMedium) {
    TodayTasksWidget()
} timeline: {
    TodayTasksEntry.previewSetup
}

#Preview("Locked", as: .systemMedium) {
    TodayTasksWidget()
} timeline: {
    TodayTasksEntry.previewLocked
}

#Preview("Floater Small Tasks", as: .systemSmall) {
    FloaterTasksWidget()
} timeline: {
    FloaterTasksEntry.previewTasks
}

#Preview("Floater Medium Tasks", as: .systemMedium) {
    FloaterTasksWidget()
} timeline: {
    FloaterTasksEntry.previewTasks
}

#Preview("Floater Large Tasks", as: .systemLarge) {
    FloaterTasksWidget()
} timeline: {
    FloaterTasksEntry.previewTasks
}

#Preview("Floater Empty", as: .systemMedium) {
    FloaterTasksWidget()
} timeline: {
    FloaterTasksEntry.previewEmpty
}

#Preview("Floater Setup", as: .systemMedium) {
    FloaterTasksWidget()
} timeline: {
    FloaterTasksEntry.previewSetup
}

#Preview("Floater Locked", as: .systemMedium) {
    FloaterTasksWidget()
} timeline: {
    FloaterTasksEntry.previewLocked
}
#endif
#endif
