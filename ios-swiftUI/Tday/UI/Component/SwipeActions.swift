import SwiftUI

struct TodoRowAction {
    let title: String
    let systemImage: String
    let tint: Color
    let role: ButtonRole?
    let action: () -> Void
}

extension View {
    func todoSwipeActions(_ actions: [TodoRowAction]) -> some View {
        swipeActions {
            ForEach(Array(actions.enumerated()), id: \.offset) { entry in
                let item = entry.element
                Button(role: item.role, action: item.action) {
                    Label(item.title, systemImage: item.systemImage)
                }
                .tint(item.tint)
            }
        }
    }
}
