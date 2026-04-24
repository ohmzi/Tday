import SwiftUI
import UIKit

extension View {
    func navigationBackButtonBehavior() -> some View {
        background(NavigationBackButtonConfigurator())
    }

    func navigationTitleTypography(
        largeTitleColor: Color,
        inlineTitleColor: Color,
        backgroundColor: Color
    ) -> some View {
        background(
            NavigationTitleAppearanceConfigurator(
                largeTitleColor: UIColor(largeTitleColor),
                inlineTitleColor: UIColor(inlineTitleColor),
                backgroundColor: UIColor(backgroundColor)
            )
        )
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

private struct NavigationTitleAppearanceConfigurator: UIViewControllerRepresentable {
    let largeTitleColor: UIColor
    let inlineTitleColor: UIColor
    let backgroundColor: UIColor

    func makeUIViewController(context: Context) -> Controller {
        Controller()
    }

    func updateUIViewController(_ controller: Controller, context: Context) {
        controller.largeTitleColor = largeTitleColor
        controller.inlineTitleColor = inlineTitleColor
        controller.backgroundColor = backgroundColor
        controller.scheduleNavigationItemUpdate()
    }

    final class Controller: UIViewController {
        var largeTitleColor: UIColor = .label
        var inlineTitleColor: UIColor = .label
        var backgroundColor: UIColor = .systemBackground

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

            let appearance = UINavigationBarAppearance()
            appearance.configureWithOpaqueBackground()
            appearance.backgroundColor = backgroundColor
            appearance.shadowColor = .clear
            appearance.titleTextAttributes = [
                .foregroundColor: inlineTitleColor,
                .font: UIFont.tdayRoundedNavigationFont(size: 17, weight: .bold)
            ]
            appearance.largeTitleTextAttributes = [
                .foregroundColor: largeTitleColor,
                .font: UIFont.tdayRoundedNavigationFont(size: 32, weight: .heavy)
            ]

            owner.navigationItem.standardAppearance = appearance
            owner.navigationItem.compactAppearance = appearance
            owner.navigationItem.scrollEdgeAppearance = appearance
            owner.navigationItem.compactScrollEdgeAppearance = appearance
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

private extension UIFont {
    static func tdayRoundedNavigationFont(size: CGFloat, weight: UIFont.Weight) -> UIFont {
        let baseFont = UIFont.systemFont(ofSize: size, weight: weight)
        guard let roundedDescriptor = baseFont.fontDescriptor.withDesign(.rounded) else {
            return baseFont
        }
        return UIFont(descriptor: roundedDescriptor, size: size)
    }
}
