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
        if priority != "Low" {
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

    static func shareTask(_ todo: TodoItem) {
        presentShareSheet(items: [taskShareText(todo)])
    }

    static func shareList(name: String, items: [TodoItem]) {
        var parts: [String] = [name]
        parts.append(String(repeating: "—", count: min(name.count, 20)))
        for todo in items {
            let bullet = todo.completed ? "✓" : "○"
            parts.append("\(bullet) \(todo.title)")
        }
        parts.append("")
        parts.append("\(items.count) task\(items.count != 1 ? "s" : "")")
        let text = parts.joined(separator: "\n")
        presentShareSheet(items: [text])
    }

    private static func presentShareSheet(items: [Any]) {
        guard let windowScene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first,
              let rootVC = windowScene.windows.first?.rootViewController else {
            return
        }
        let activityVC = UIActivityViewController(activityItems: items, applicationActivities: nil)

        // Find the topmost presented VC
        var topVC = rootVC
        while let presented = topVC.presentedViewController {
            topVC = presented
        }

        if let popover = activityVC.popoverPresentationController {
            popover.sourceView = topVC.view
            popover.sourceRect = CGRect(x: topVC.view.bounds.midX, y: topVC.view.bounds.midY, width: 0, height: 0)
            popover.permittedArrowDirections = []
        }

        topVC.present(activityVC, animated: true)
    }
}
