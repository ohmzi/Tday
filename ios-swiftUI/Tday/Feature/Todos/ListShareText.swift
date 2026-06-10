import Foundation

/// Canonical plain-text export of a list. Mirrors the Android
/// `ShareUtils.buildListShareText` output so a shared list reads the same
/// from every platform.
func listShareText(listName: String, items: [TodoItem]) -> String {
    // Per-call formatter so in-app language switches take effect; Locale.current
    // does not follow the in-app override.
    let dateFormatter = DateFormatter()
    dateFormatter.dateStyle = .medium
    dateFormatter.timeStyle = .short
    dateFormatter.locale = AppLocale.current

    var lines: [String] = [listName, String(repeating: "—", count: min(listName.count, 20))]
    for todo in items {
        lines.append("\(todo.completed ? "✓" : "○") \(todo.title)")
        if let due = todo.due {
            lines.append("   " + L("Due: %@", dateFormatter.string(from: due)))
        }
        if let notes = todo.description?.trimmingCharacters(in: .whitespacesAndNewlines), !notes.isEmpty {
            lines.append("   \(notes)")
        }
    }
    lines.append("")
    lines.append(items.count == 1 ? L("1 task") : L("%lld tasks", Int64(items.count)))
    return lines.joined(separator: "\n")
}
