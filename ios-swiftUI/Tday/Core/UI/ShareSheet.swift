import SwiftUI
import UIKit

struct ShareSheet {
    /// Title + flattened notes as plain text: what the swipe-to-copy clipboard
    /// action puts on the clipboard, read the same on every platform (see
    /// Android's taskCopyText, web's buildTaskShareText).
    ///
    /// The due date/time and the priority flag are deliberately left out. A copy
    /// is text to paste somewhere else, and those are scheduling details rather
    /// than what the task says.
    static func taskShareText(title: String, description: String?) -> String {
        var parts: [String] = [title]
        let flattenedDescription = flattenNotesToPlainText(description)
        if !flattenedDescription.isEmpty {
            parts.append(flattenedDescription)
        }
        return parts.joined(separator: "\n")
    }

    static func taskShareText(_ todo: TodoItem) -> String {
        taskShareText(title: todo.title, description: todo.description)
    }

    static func taskShareText(_ item: CompletedItem) -> String {
        taskShareText(title: item.title, description: item.description)
    }
}
