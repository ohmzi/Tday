import SwiftUI
import UIKit

struct ShareSheet {
    /// Title + flattened notes + due + priority as plain text — the single
    /// source of truth for "what a task looks like as text", shared by the
    /// share sheet and the swipe-to-copy clipboard action so both read the
    /// same on every platform (see Android's taskCopyText, web's
    /// buildTaskShareText).
    static func taskShareText(title: String, description: String?, due: Date?, priority: String) -> String {
        var parts: [String] = [title]
        let flattenedDescription = flattenNotesToPlainText(description)
        if !flattenedDescription.isEmpty {
            parts.append(flattenedDescription)
        }
        if let due {
            let formatter = DateFormatter()
            formatter.dateFormat = "EEE, MMM d 'at' h:mm a"
            parts.append("Due: \(formatter.string(from: due))")
        }
        // "Low" is the default priority and "Lowest" sits even further below it —
        // neither is worth calling out in the shared text. Mirrors Android's
        // ShareUtils.taskCopyText and web's listShareText.ts buildTaskShareText.
        if priority != "Low" && priority != "Lowest" {
            parts.append("Priority: \(priority)")
        }
        return parts.joined(separator: "\n")
    }

    static func taskShareText(_ todo: TodoItem) -> String {
        taskShareText(title: todo.title, description: todo.description, due: todo.due, priority: todo.priority)
    }

    static func taskShareText(_ item: CompletedItem) -> String {
        taskShareText(title: item.title, description: item.description, due: item.due, priority: item.priority)
    }
}
