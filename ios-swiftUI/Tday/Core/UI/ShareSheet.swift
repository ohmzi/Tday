import SwiftUI
import UIKit

struct ShareSheet {
    static func shareTask(_ todo: TodoItem) {
        var parts: [String] = [todo.title]
        if let description = todo.description, !description.isEmpty {
            parts.append(description)
        }
        if let due = todo.due {
            let formatter = DateFormatter()
            formatter.dateFormat = "EEE, MMM d 'at' h:mm a"
            parts.append("Due: \(formatter.string(from: due))")
        }
        if todo.priority != "Low" {
            parts.append("Priority: \(todo.priority)")
        }
        let text = parts.joined(separator: "\n")
        presentShareSheet(items: [text])
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
