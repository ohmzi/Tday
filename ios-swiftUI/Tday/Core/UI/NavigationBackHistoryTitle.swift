import SwiftUI
import UIKit

extension View {
    func navigationBackButtonBehavior() -> some View {
        background(NavigationBackButtonConfigurator())
    }
}

private struct NavigationBackButtonConfigurator: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> Controller {
        Controller()
    }

    func updateUIViewController(_ controller: Controller, context: Context) {
        controller.scheduleNavigationItemUpdate()
    }

    final class Controller: UIViewController {
        private let backButtonItem = NoMenuBackBarButtonItem(title: "", style: .plain, target: nil, action: nil)

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

            if owner.navigationItem.backBarButtonItem !== backButtonItem {
                owner.navigationItem.backBarButtonItem = backButtonItem
            }
            owner.navigationItem.backButtonTitle = nil
            owner.navigationItem.backButtonDisplayMode = .minimal
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

private final class NoMenuBackBarButtonItem: UIBarButtonItem {
    override var menu: UIMenu? {
        get { nil }
        set {}
    }
}
