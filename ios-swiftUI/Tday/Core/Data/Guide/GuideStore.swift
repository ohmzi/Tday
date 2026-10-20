import Foundation

/// Persists which release the user last opened the How-To guide in, so NEW
/// badges clear once viewed instead of lingering for the whole release.
final class GuideStore {
    private let defaults = UserDefaults.standard
    private let key = "guide.lastSeenVersion"

    func lastSeenGuideVersion() -> String? {
        defaults.string(forKey: key)
    }

    func setLastSeenGuideVersion(_ version: String) {
        defaults.set(version, forKey: key)
    }
}
