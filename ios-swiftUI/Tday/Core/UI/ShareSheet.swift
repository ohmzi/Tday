import SwiftUI
import UIKit

struct ShareSheet {
    /// Title + flattened notes + due as plain text — the single source of
    /// truth for "what a task looks like as text", shared by the share sheet
    /// and the swipe-to-copy clipboard action so both read the same on every
    /// platform (see Android's taskCopyText, web's buildTaskShareText).
    ///
    /// The priority flag is deliberately not part of this: copying a task copies
    /// the task, and the urgency tier is a list-view marking rather than part of
    /// what the task says.
    static func taskShareText(title: String, description: String?, due: Date?) -> String {
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
        return parts.joined(separator: "\n")
    }

    static func taskShareText(_ todo: TodoItem) -> String {
        taskShareText(title: todo.title, description: todo.description, due: todo.due)
    }

    static func taskShareText(_ item: CompletedItem) -> String {
        taskShareText(title: item.title, description: item.description, due: item.due)
    }
}
