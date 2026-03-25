import Foundation

final class ThemeStore {
    private let defaults = UserDefaults.standard
    private let key = "theme.mode"

    func load() -> AppThemeMode {
        guard let rawValue = defaults.string(forKey: key), let mode = AppThemeMode(rawValue: rawValue) else {
            return .system
        }
        return mode
    }

    func save(_ mode: AppThemeMode) {
        defaults.set(mode.rawValue, forKey: key)
    }

    func themeMode() -> AppThemeMode {
        load()
    }

    func saveThemeMode(_ mode: AppThemeMode) {
        save(mode)
    }

    func clear() {
        defaults.removeObject(forKey: key)
    }
}
