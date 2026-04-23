import SwiftUI
import UIKit

extension View {
    func navigationBackHistoryTitle(_ title: String) -> some View {
        background(NavigationBackHistoryTitleConfigurator(title: title))
    }
}

private struct NavigationBackHistoryTitleConfigurator: UIViewControllerRepresentable {
    let title: String

    func makeUIViewController(context: Context) -> Controller {
        Controller()
    }

    func updateUIViewController(_ controller: Controller, context: Context) {
        controller.backHistoryTitle = title
        controller.scheduleNavigationItemUpdate()
    }

    final class Controller: UIViewController {
        var backHistoryTitle = ""

        override func loadView() {
            let view = UIView(frame: .zero)
            view.isHidden = true
            view.isUserInteractionEnabled = false
            self.view = view
        }

        override func didMove(toParent parent: UIViewController?) {
            super.didMove(toParent: parent)
            scheduleNavigationItemUpdate()
        }

        override func viewWillAppear(_ animated: Bool) {
            super.viewWillAppear(animated)
            scheduleNavigationItemUpdate()
        }

        func scheduleNavigationItemUpdate() {
            DispatchQueue.main.async { [weak self] in
                self?.applyNavigationItemState()
            }
        }

        private func applyNavigationItemState() {
            guard let owner = owningViewController else {
                return
            }

            let normalizedTitle = backHistoryTitle.trimmingCharacters(in: .whitespacesAndNewlines)
            owner.navigationItem.backBarButtonItem = nil
            owner.navigationItem.backButtonTitle = normalizedTitle.isEmpty ? nil : normalizedTitle
            owner.navigationItem.backButtonDisplayMode = .default
        }

        private var owningViewController: UIViewController? {
            var current = parent
            var owner: UIViewController?

            while let viewController = current {
                if viewController is UINavigationController {
                    break
                }
                owner = viewController
                current = viewController.parent
            }

            return owner
        }
    }
}
