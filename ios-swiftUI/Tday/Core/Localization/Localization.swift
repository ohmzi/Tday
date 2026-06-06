import Foundation

/// Localized lookup that honors the in-app language override (the swizzled
/// `Bundle.main`). Use for plain-`String` literals in non-View code (error
/// messages, enum labels). SwiftUI `Text("key")` already resolves through the
/// same bundle, so view call sites need no change.
@inline(__always)
func L(_ key: String, _ args: CVarArg...) -> String {
    let format = Bundle.main.localizedString(forKey: key, value: key, table: nil)
    guard !args.isEmpty else { return format }
    return String(format: format, locale: Locale.current, arguments: args)
}
