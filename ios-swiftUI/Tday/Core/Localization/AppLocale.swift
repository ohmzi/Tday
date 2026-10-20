import Foundation

/// The locale matching the in-app language choice. Reads the stored choice each
/// access so it stays correct after a live language switch. "system" follows the device.
enum AppLocale {
    static var current: Locale {
        let stored = LanguageStore().load()
        return stored == LanguageStore.systemValue ? .autoupdatingCurrent : Locale(identifier: stored)
    }
}
