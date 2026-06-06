import Foundation

/// Persists the in-app language choice. Stored value is a supported language
/// code (e.g. "es") or `systemValue` to follow the device language.
final class LanguageStore {
    private let defaults = UserDefaults.standard
    private let key = "app.language"
    static let systemValue = "system"

    func load() -> String { defaults.string(forKey: key) ?? Self.systemValue }
    func save(_ value: String) { defaults.set(value, forKey: key) }
    func clear() { defaults.removeObject(forKey: key) }

    /// The concrete locale code to apply (resolves "system" to the device language).
    func resolvedCode() -> String {
        let value = load()
        return value == Self.systemValue ? AppLanguage.systemResolvedCode() : value
    }
}
