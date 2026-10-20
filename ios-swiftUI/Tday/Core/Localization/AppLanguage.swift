import Foundation

/// Languages the app ships (offline, bundled). `system` follows the device.
enum AppLanguage: String, CaseIterable, Identifiable {
    case system
    case en, es, fr, de, it, pt, ru, zh, ja, ms

    var id: String { rawValue }

    /// `.lproj` code to load, or nil to follow the system language.
    var code: String? { self == .system ? nil : rawValue }

    /// Endonym (each language shown in its own script). `system` is labelled by
    /// the caller via a localized string.
    var endonym: String {
        switch self {
        case .system: return "System default"
        case .en: return "English"
        case .es: return "Español"
        case .fr: return "Français"
        case .de: return "Deutsch"
        case .it: return "Italiano"
        case .pt: return "Português"
        case .ru: return "Русский"
        case .zh: return "中文"
        case .ja: return "日本語"
        case .ms: return "Bahasa Melayu"
        }
    }

    static let supportedCodes: [String] = [
        "en", "es", "fr", "de", "it", "pt", "ru", "zh", "ja", "ms",
    ]

    /// Map the device's preferred languages onto a supported code (fallback en).
    static func systemResolvedCode() -> String {
        for preferred in Locale.preferredLanguages {
            let base = String(preferred.prefix(2)).lowercased()
            if supportedCodes.contains(base) { return base }
        }
        return "en"
    }
}
