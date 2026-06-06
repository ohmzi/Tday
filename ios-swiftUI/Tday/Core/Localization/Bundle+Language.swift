import Foundation

private var bundleLanguageKey: UInt8 = 0

/// A `Bundle` subclass whose string lookups are redirected to a selected
/// `.lproj` sub-bundle. We swap `Bundle.main`'s class to this so that every
/// `Text("key")` / `NSLocalizedString` resolves against the in-app chosen
/// language instead of the OS language — enabling instant switching with no
/// restart. When no language is selected we fall back to the system default.
final class LocalizedBundle: Bundle, @unchecked Sendable {
    override func localizedString(
        forKey key: String,
        value: String?,
        table tableName: String?,
    ) -> String {
        if let langBundle = objc_getAssociatedObject(self, &bundleLanguageKey) as? Bundle {
            return langBundle.localizedString(forKey: key, value: value, table: tableName)
        }
        return super.localizedString(forKey: key, value: value, table: tableName)
    }
}

enum LanguageBundle {
    /// Swap `Bundle.main`'s class once, as early as possible (app init).
    static func activate() {
        guard !(Bundle.main is LocalizedBundle) else { return }
        object_setClass(Bundle.main, LocalizedBundle.self)
    }

    /// Point `Bundle.main` at the `.lproj` for `code`; pass `nil` to follow the
    /// system language (clears the override).
    static func setLanguage(_ code: String?) {
        activate()
        var target: Bundle?
        if let code,
           let path = Bundle.main.path(forResource: code, ofType: "lproj"),
           let bundle = Bundle(path: path) {
            target = bundle
        }
        objc_setAssociatedObject(
            Bundle.main,
            &bundleLanguageKey,
            target,
            .OBJC_ASSOCIATION_RETAIN_NONATOMIC,
        )
    }
}
