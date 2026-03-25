import Foundation

final class ReminderPreferenceStore {
    private let defaults = UserDefaults.standard
    private let reminderKey = "reminder.default"
    private let notifiedIDsKey = "reminder.notified.ids"

    func getDefaultReminder() -> ReminderOption {
        guard let rawValue = defaults.string(forKey: reminderKey), let option = ReminderOption(rawValue: rawValue) else {
            return .atTime
        }
        return option
    }

    func setDefaultReminder(_ option: ReminderOption) {
        defaults.set(option.rawValue, forKey: reminderKey)
    }

    func defaultReminder() -> ReminderOption {
        getDefaultReminder()
    }

    func saveDefaultReminder(_ option: ReminderOption) {
        setDefaultReminder(option)
    }

    func markNotified(taskID: String) {
        guard !taskID.isEmpty else {
            return
        }
        var ids = Set(defaults.stringArray(forKey: notifiedIDsKey) ?? [])
        ids.insert(taskID)
        defaults.set(Array(ids), forKey: notifiedIDsKey)
    }

    func hasNotified(taskID: String) -> Bool {
        let ids = Set(defaults.stringArray(forKey: notifiedIDsKey) ?? [])
        return ids.contains(taskID)
    }

    func clearNotified(taskID: String) {
        var ids = Set(defaults.stringArray(forKey: notifiedIDsKey) ?? [])
        ids.remove(taskID)
        defaults.set(Array(ids), forKey: notifiedIDsKey)
    }

    func clear() {
        defaults.removeObject(forKey: reminderKey)
        defaults.removeObject(forKey: notifiedIDsKey)
    }
}
