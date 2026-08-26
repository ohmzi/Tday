import Foundation
import UserNotifications

final class TaskReminderScheduler {
    static let reminderCategoryID = "TASK_REMINDER"
    static let snoozeHourActionID = "TDAY_SNOOZE_1H"
    static let tonightActionID = "TDAY_MOVE_TONIGHT"

    private let reminderPreferenceStore: ReminderPreferenceStore

    init(reminderPreferenceStore: ReminderPreferenceStore) {
        self.reminderPreferenceStore = reminderPreferenceStore
    }

    /// The one place this app asks iOS for permission, and it is asked at bootstrap. iOS shows
    /// its dialog only while the status is `.notDetermined`; every later call silently returns
    /// the standing answer, which is why a denied device has to be sent to Settings instead.
    @discardableResult
    func requestAuthorization() async -> Bool {
        guard let notificationCenter else {
            return false
        }
        let granted = (try? await notificationCenter.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        registerReminderCategory()
        return granted
    }

    /// Re-read on every foreground return, never cached: the user can turn T'Day's
    /// notifications off in iOS Settings while the app is suspended and nothing tells the app.
    func authorizationStatus() async -> UNAuthorizationStatus {
        guard let notificationCenter else {
            return .notDetermined
        }
        return await notificationCenter.notificationSettings().authorizationStatus
    }

    /// Snooze / Tonight actions shown on every task-reminder notification.
    private func registerReminderCategory() {
        guard let notificationCenter else {
            return
        }
        let snooze = UNNotificationAction(
            identifier: Self.snoozeHourActionID,
            title: L("Snooze 1h")
        )
        let tonight = UNNotificationAction(
            identifier: Self.tonightActionID,
            title: L("Tonight")
        )
        let category = UNNotificationCategory(
            identifier: Self.reminderCategoryID,
            actions: [snooze, tonight],
            intentIdentifiers: []
        )
        notificationCenter.setNotificationCategories([category])
    }

    /// Re-presents a delivered reminder after [interval]. Clears the
    /// notified marker first — without that the next reschedule pass would
    /// silently suppress the task's reminders.
    func snooze(taskID: String, content: UNNotificationContent, interval: TimeInterval) async {
        guard let notificationCenter, NotificationPreferenceStore().isEnabled else {
            return
        }
        reminderPreferenceStore.clearNotified(taskID: taskID)
        guard let snoozedContent = content.mutableCopy() as? UNMutableNotificationContent else {
            return
        }
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: interval, repeats: false)
        let request = UNNotificationRequest(
            identifier: "tday.todo.\(taskID).snoozed",
            content: snoozedContent,
            trigger: trigger
        )
        try? await notificationCenter.add(request)
        TdayTelemetry.addBreadcrumb("reminder.snooze", data: ["intervalSeconds": Int(interval)])
    }

    /// Empties the queue outright. For leaving a workspace, where the tasks those pending
    /// requests were built from are no longer the ones this install is showing — `reschedule`
    /// only sweeps the identifiers of the tasks it is handed, so it cannot do this job.
    func cancelAll() {
        notificationCenter?.removeAllPendingNotificationRequests()
    }

    func reschedule(tasks: [TodoItem], defaultReminder: ReminderOption) async {
        guard let notificationCenter else {
            return
        }
        TdayTelemetry.addBreadcrumb(
            "reminder.reschedule",
            data: ["taskCount": tasks.count, "enabled": defaultReminder.offsetSeconds != nil]
        )
        let identifiers = tasks.map { notificationIdentifier(for: $0) }
        notificationCenter.removePendingNotificationRequests(withIdentifiers: identifiers)

        // The app's own switch, and a real gate rather than a display of the OS bit: an off switch
        // empties the queue instead of leaving everything scheduled behind a permission the user
        // could grant back at any moment.
        //
        // The whole queue, not the per-task identifiers removed above. `snooze` files its request
        // under "<id>.snoozed" and nothing else in this app ever removes one, so an off switch that
        // only swept the un-suffixed ids left a snoozed reminder to fire up to an hour later while
        // the switch read off. Day Ahead's own request goes with it, and both come back on the
        // reschedule pass that follows the switch coming on.
        guard NotificationPreferenceStore().isEnabled else {
            notificationCenter.removeAllPendingNotificationRequests()
            return
        }

        guard let offsetSeconds = defaultReminder.offsetSeconds else {
            return
        }

        let quietHours = QuietHoursStore()
        for task in tasks where !task.completed {
            guard let due = task.due else {
                continue
            }
            var triggerDate = due.addingTimeInterval(-offsetSeconds)
            // Quiet hours: shift a reminder that would fire inside the held window to the
            // window end, so nothing buzzes overnight.
            if quietHours.isEnabled {
                let comps = Calendar.current.dateComponents([.hour, .minute], from: triggerDate)
                let minuteOfDay = (comps.hour ?? 0) * 60 + (comps.minute ?? 0)
                let shift = QuietHoursMath.minutesUntilWindowEnd(
                    minuteOfDay, quietHours.startMinute, quietHours.endMinute
                )
                if shift > 0 {
                    triggerDate = triggerDate.addingTimeInterval(Double(shift) * 60)
                }
            }
            guard triggerDate > Date(), !reminderPreferenceStore.hasNotified(taskID: task.id) else {
                continue
            }

            let content = UNMutableNotificationContent()
            content.title = task.title
            let flattenedBody = flattenNotesToPlainText(task.description)
            content.body = flattenedBody.isEmpty ? "Due soon" : flattenedBody
            content.sound = .default
            content.categoryIdentifier = Self.reminderCategoryID
            content.userInfo = [
                "deepLink": Self.deepLinkURLString(for: task.id),
                "taskId": task.id,
            ]

            let components = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: triggerDate)
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            let request = UNNotificationRequest(identifier: notificationIdentifier(for: task), content: content, trigger: trigger)
            try? await notificationCenter.add(request)
        }
    }

    private func notificationIdentifier(for task: TodoItem) -> String {
        "tday.todo.\(task.id)"
    }

    private static func deepLinkURLString(for taskID: String) -> String {
        var components = URLComponents()
        components.scheme = "tday"
        components.host = "todos"
        components.path = "/all"
        components.queryItems = [URLQueryItem(name: "highlightTodoId", value: taskID)]
        return components.url?.absoluteString ?? "tday://todos/all"
    }

    private var notificationCenter: UNUserNotificationCenter? {
        guard Bundle.main.bundleURL.pathExtension == "app" else {
            return nil
        }
        return UNUserNotificationCenter.current()
    }
}

// MARK: - Backend security alerts

/// The newest alert this device has already notified about. `createdAt` is the backstop for
/// when that alert has aged out of the server's 50-row window and its id is no longer returned.
struct SecurityAlertMarker: Equatable {
    let id: String
    let createdAt: Date?
}

/// Persists the marker in UserDefaults, like `QuietHoursStore` above and
/// `ReminderPreferenceStore`'s notified markers. Not the keychain: an alert id is a server cuid,
/// not a secret, and a background wake-up should not have to reach into the keychain to decide
/// whether it has anything to say. Surviving relaunch is the whole point — without it every cold
/// start would re-announce the same alerts.
struct SecurityAlertSeenStore {
    private let defaults: UserDefaults
    private static let idKey = "securityAlerts.lastNotifiedId"
    private static let createdAtKey = "securityAlerts.lastNotifiedEpochSeconds"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var marker: SecurityAlertMarker? {
        guard let id = defaults.string(forKey: Self.idKey) else {
            return nil
        }
        let seconds = defaults.double(forKey: Self.createdAtKey)
        return SecurityAlertMarker(id: id, createdAt: seconds > 0 ? Date(timeIntervalSince1970: seconds) : nil)
    }

    func record(_ marker: SecurityAlertMarker) {
        defaults.set(marker.id, forKey: Self.idKey)
        defaults.set(marker.createdAt?.timeIntervalSince1970 ?? 0, forKey: Self.createdAtKey)
    }
}

/// Pure decisions behind the security-alert notification — kept free of network, clock and
/// UNUserNotificationCenter so they can be tested without a live server.
enum SecurityAlertNotifier {
    /// The backend emits `LocalDateTime.toString()`: no timezone suffix, and the seconds field
    /// is dropped when it is zero, so "2026-08-05T12:00" and "2026-08-05T12:00:03.123" both
    /// occur. `parseOptionalDate` already walks that whole family of patterns (it exists for the
    /// same backend quirk on task timestamps), so reuse it rather than a fixed formatter that
    /// would silently fail on every top-of-the-minute alert.
    static func parseCreatedAt(_ raw: String) -> Date? {
        parseOptionalDate(raw)
    }

    /// The alerts worth notifying about, given what this device last announced.
    ///
    /// A nil marker means this device has never looked: seed the baseline silently instead of
    /// announcing up to 50 rows of history the owner has likely already seen on the web admin.
    static func unseenAlerts(in alerts: [SecurityAlertDTO], since marker: SecurityAlertMarker?) -> [SecurityAlertDTO] {
        guard let marker else {
            return []
        }
        var unseen: [SecurityAlertDTO] = []
        for alert in alerts {
            // The server returns newest-first, so everything from the marker down is old news.
            if alert.id == marker.id {
                break
            }
            // ...but the marker's alert can have aged out of the 50-row window, in which case the
            // id above never matches and only the timestamp keeps the history from re-announcing.
            if let seenAt = marker.createdAt,
               let createdAt = parseCreatedAt(alert.createdAt),
               createdAt <= seenAt {
                continue
            }
            unseen.append(alert)
        }
        return unseen
    }

    /// The newest row, i.e. where the marker should stand after this poll.
    static func newestMarker(in alerts: [SecurityAlertDTO]) -> SecurityAlertMarker? {
        guard let newest = alerts.first else {
            return nil
        }
        return SecurityAlertMarker(id: newest.id, createdAt: parseCreatedAt(newest.createdAt))
    }

    /// One body for the whole batch — a burst of alerts must never become a burst of banners.
    /// The single-alert case is just the server's own detail text, which already spells out how
    /// many events it stands for.
    static func notificationBody(for unseen: [SecurityAlertDTO]) -> String? {
        guard let newest = unseen.first else {
            return nil
        }
        guard unseen.count > 1 else {
            return newest.detail
        }
        return "\(L("%d security alerts", unseen.count)) — \(newest.detail)"
    }
}

/// Polls `GET /api/admin/security/alerts` and raises a LOCAL notification for anything new.
///
/// This is deliberately NOT push. The backend delivers security alerts over Web Push and
/// UnifiedPush only — there is no APNs transport and this app never registers for remote
/// notifications — so an iPhone-only owner would otherwise never hear about them. Instead we
/// piggy-back on the two wake-ups the app already has: the ~30-min `BGAppRefreshTask` in
/// `WidgetBackgroundRefresh` and the foreground reconnect. No new background mode, no timer, and
/// no polling while suspended. An alert therefore surfaces on the next such wake-up, which iOS
/// may delay by hours — the notification is a nicety, not parity with the Android/web push path.
@MainActor
final class SecurityAlertPoller {
    private let api: TdayAPIService
    private let secureStore: SecureStore
    private let seenStore: SecurityAlertSeenStore
    /// The background task and a foreground return can land together; a second concurrent poll
    /// could read the marker before the first one advanced it and notify twice.
    private var isPolling = false

    init(
        api: TdayAPIService,
        secureStore: SecureStore,
        seenStore: SecurityAlertSeenStore = SecurityAlertSeenStore()
    ) {
        self.api = api
        self.secureStore = secureStore
        self.seenStore = seenStore
    }

    /// Never throws and never surfaces anything to the user: offline, 401, 403 and malformed
    /// JSON all end the same way — quietly, leaving the marker where it was.
    func pollForNewAlerts() async {
        guard !isPolling, isApprovedAdminSession() else {
            return
        }
        // Before the fetch, and before the marker moves. This poll advances the seen
        // marker ahead of posting on purpose — see below — which is right when the
        // OS is the thing refusing, because there is nothing to be done about that
        // and a banner every 30 minutes is worse than one missed. It is wrong when
        // the app's own switch is off: that is a state the user reverses, and an
        // alert swallowed here would never be shown again once they did. Leaving
        // the marker where it is defers the alert instead of eating it.
        guard NotificationPreferenceStore().isEnabled else {
            return
        }
        isPolling = true
        defer { isPolling = false }

        let alerts: [SecurityAlertDTO]
        do {
            alerts = try await api.getSecurityAlerts().alerts
        } catch {
            TdayTelemetry.addBreadcrumb(
                "security.alerts",
                level: .warning,
                data: ["phase": "fetch_failed"]
            )
            return
        }

        guard let newest = SecurityAlertNotifier.newestMarker(in: alerts) else {
            return
        }
        let unseen = SecurityAlertNotifier.unseenAlerts(in: alerts, since: seenStore.marker)
        // Advance before posting. `add` can fail silently when the OS has denied
        // notifications, and a duplicate security banner every 30 minutes is a far
        // worse failure than one missed banner. The app's own switch is handled at
        // the top of this function instead, because that one the user can undo.
        seenStore.record(newest)
        guard let body = SecurityAlertNotifier.notificationBody(for: unseen) else {
            return
        }
        await post(body: body)
    }

    /// The endpoint is admin-only, so the app must not touch it at all unless this session is one.
    ///
    /// Three independent gates: Local Mode has no server to ask; a signed-out device has no
    /// cached session (it is cleared on logout, and on reinstall); and a signed-in non-admin
    /// fails the role/approval check. `cachedSessionUser` is the same session snapshot
    /// `AuthRepository` writes on every session restore, so it tracks the live session — and it
    /// is readable from the background task, where no view model exists.
    private func isApprovedAdminSession() -> Bool {
        guard !secureStore.isLocalMode(), secureStore.hasServerURL() else {
            return false
        }
        guard let data = secureStore.loadCachedSessionUserData(),
              let user = try? JSONDecoder().decode(SessionUser.self, from: data),
              user.id != nil else {
            return false
        }
        return user.role?.caseInsensitiveCompare("ADMIN") == .orderedSame
            && user.approvalStatus?.caseInsensitiveCompare("APPROVED") == .orderedSame
    }

    private func post(body: String) async {
        // No notification centre outside a real app bundle (unit tests, previews).
        //
        // Deliberately NOT behind `NotificationPreferenceStore`. That switch is described to the
        // user as covering task reminders and the Day Ahead digest, and this is neither: it is the
        // only way an admin on an iPhone ever hears that something happened to their account, with
        // no APNs path behind it (see the class comment). Gating it would also have swallowed the
        // alert rather than deferred it — `pollForNewAlerts` advances the seen marker before it
        // posts, so anything raised while the switch was off would never be announced again.
        // Denying notifications in iOS Settings still silences this: `add` fails and we stay quiet.
        guard Bundle.main.bundleURL.pathExtension == "app" else {
            return
        }
        let content = UNMutableNotificationContent()
        content.title = L("T'Day security alert")
        content.body = body
        content.sound = .default
        // No `deepLink` in userInfo on purpose: this app has no admin screen (`AppRoute` has no
        // admin route), so a tap simply opens T'Day. Nothing identifying goes in the payload
        // either — the backend only ever stores hashed subjects.
        let request = UNNotificationRequest(
            identifier: "tday.security.\(UUID().uuidString)",
            content: content,
            trigger: nil
        )
        // Authorization is the one the reminder feature already requested at bootstrap; if the
        // user denied it, this throws and we stay quiet rather than prompting a second time.
        try? await UNUserNotificationCenter.current().add(request)
        TdayTelemetry.addBreadcrumb("security.alerts", data: ["phase": "notified"])
    }
}

extension UNAuthorizationStatus {
    /// Whether iOS would actually deliver something scheduled right now.
    ///
    /// `.provisional` and `.ephemeral` count: both deliver, just more quietly. Everything else
    /// — including `.notDetermined`, which is "no" until the prompt is answered — does not.
    var allowsNotificationDelivery: Bool {
        switch self {
        case .authorized, .provisional, .ephemeral:
            return true
        default:
            return false
        }
    }
}

/// The app-level "send me notifications" switch, held apart from the OS permission because the
/// two answer different questions: iOS says whether it *may* deliver, this says whether the user
/// still *wants* it. iOS asks its own question exactly once, so without this a user who granted
/// the launch prompt would have nowhere to go quiet short of the system Settings app.
///
/// Default on. The bootstrap prompt is only shown because the app intends to send reminders, so
/// granting it must leave the Settings switch on without a second tap.
struct NotificationPreferenceStore {
    private let defaults: UserDefaults
    private static let key = "notifications.enabled"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var isEnabled: Bool {
        get { defaults.object(forKey: Self.key) as? Bool ?? true }
        nonmutating set { defaults.set(newValue, forKey: Self.key) }
    }
}

/// Local "hold reminders between HH:MM and HH:MM" setting (UserDefaults). Times are
/// minute-of-day (0..1439). Off by default.
struct QuietHoursStore {
    private let defaults = UserDefaults.standard
    private static let enabledKey = "quietHours.enabled"
    private static let startKey = "quietHours.startMinute"
    private static let endKey = "quietHours.endMinute"

    var isEnabled: Bool {
        get { defaults.bool(forKey: Self.enabledKey) }
        nonmutating set { defaults.set(newValue, forKey: Self.enabledKey) }
    }

    var startMinute: Int {
        get { defaults.object(forKey: Self.startKey) as? Int ?? 22 * 60 }
        nonmutating set { defaults.set(newValue, forKey: Self.startKey) }
    }

    var endMinute: Int {
        get { defaults.object(forKey: Self.endKey) as? Int ?? 7 * 60 }
        nonmutating set { defaults.set(newValue, forKey: Self.endKey) }
    }
}

/// Swift twin of the shared Kotlin QuietHours math. Kept in sync by hand + tests.
enum QuietHoursMath {
    static let minutesPerDay = 24 * 60

    static func contains(_ minuteOfDay: Int, _ startMinute: Int, _ endMinute: Int) -> Bool {
        if startMinute == endMinute { return false }
        let t = ((minuteOfDay % minutesPerDay) + minutesPerDay) % minutesPerDay
        if startMinute < endMinute {
            return t >= startMinute && t < endMinute
        }
        return t >= startMinute || t < endMinute
    }

    static func minutesUntilWindowEnd(_ minuteOfDay: Int, _ startMinute: Int, _ endMinute: Int) -> Int {
        if !contains(minuteOfDay, startMinute, endMinute) { return 0 }
        let t = ((minuteOfDay % minutesPerDay) + minutesPerDay) % minutesPerDay
        let delta = (endMinute - t + minutesPerDay) % minutesPerDay
        return delta == 0 ? minutesPerDay : delta
    }
}
