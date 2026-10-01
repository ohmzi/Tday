import SwiftUI
import UIKit

extension View {
    func navigationBackButtonBehavior() -> some View {
        background(NavigationBackButtonConfigurator())
            .navigationInteractivePopGesture()
    }

    func navigationInteractivePopGesture() -> some View {
        background(NavigationInteractivePopGestureConfigurator())
    }

    /// Refuses the zoom transition's swipe-down and pinch dismissals, and leaves its
    /// leading-edge pan alone.
    ///
    /// Only meaningful on a screen pushed with `.navigationTransition(.zoom(…))` — see
    /// `ZoomNavigation.swift`, which is the one call site. Everywhere else it is inert,
    /// because the recognizers it looks for exist only where UIKit installed them.
    func navigationZoomDismissRefusal() -> some View {
        background(NavigationZoomDismissRefusalConfigurator())
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

/// The names UIKit gives the recognizers that drive a zoom transition's interactive
/// dismiss.
///
/// `UIGestureRecognizer.name` is public API (iOS 11). These VALUES are not: they are
/// what UIKit's own `_UIContentSwipeDismissSubInteraction` and
/// `_UIPinchDismissSubInteraction` set when `preferredTransition = .zoom(…)` installs a
/// `_UIDismissInteraction` on the pushed controller, in the same reverse-DNS convention
/// it uses for `com.apple.UIKit.ContextMenuDismissalTap`. A rename in a future iOS
/// therefore fails OPEN — the filter matches nothing, the gesture comes back, and
/// nothing else in the app changes. It can never fail closed onto a recognizer this
/// set does not name.
///
/// `com.apple.UIKit.ZoomInteractiveDismissLeadingEdgePan` is deliberately ABSENT, and
/// that absence is the whole contract: it is the back gesture the product keeps. The
/// supported lever for this — `UIZoomTransitionOptions.interactiveDismissShouldBegin` —
/// cannot be reached from SwiftUI's `.navigationTransition(.zoom(sourceID:in:))`, which
/// takes no options and whose `NavigationTransition` protocol is not conformable
/// (`_NavigationTransitionOutputs` is an empty public struct with no initialiser). Owning
/// the transition from UIKit instead would mean handing back the tile's real `UIView`,
/// which SwiftUI does not expose, and giving up the source `clipShape` that the corner
/// fix in `ZoomNavigation.swift` rests on.
private enum ZoomDismissRefusal {
    static let refusedNames: Set<String> = [
        "com.apple.UIKit.ZoomInteractiveDismissSwipeDown",
        "com.apple.UIKit.ZoomInteractiveDismissPinch"
    ]

    /// A second, independent signal for the swipe alone. `_UIContentSwipeDismissGestureRecognizer`
    /// is private to UIKit, so no app recognizer can collide with it — unlike pinch, whose
    /// recognizer is a plain `UIPinchGestureRecognizer` the app could itself own, which is
    /// why the class test is not extended to cover that one.
    static let refusedClassFragment = "ContentSwipeDismiss"

    static func isRefused(_ recognizer: UIGestureRecognizer) -> Bool {
        if let name = recognizer.name, refusedNames.contains(name) {
            return true
        }
        return String(describing: type(of: recognizer)).contains(refusedClassFragment)
    }
}

private struct NavigationZoomDismissRefusalConfigurator: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> Controller {
        Controller()
    }

    func updateUIViewController(_ controller: Controller, context: Context) {
        controller.scheduleRefusal()
    }

    final class Controller: UIViewController {
        override func loadView() {
            let view = UIView(frame: .zero)
            view.isHidden = true
            view.isUserInteractionEnabled = false
            self.view = view
        }

        override func didMove(toParent parent: UIViewController?) {
            super.didMove(toParent: parent)
            scheduleRefusal()
        }

        override func viewWillAppear(_ animated: Bool) {
            super.viewWillAppear(animated)
            scheduleRefusal()
        }

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            scheduleRefusal()
        }

        /// The same cadence the pop-gesture configurator above runs on, and for the same
        /// reason: the interaction is installed by UIKit around the push rather than at a
        /// moment this view can observe, so the assert is repeated rather than timed.
        func scheduleRefusal() {
            [0.0, 0.05, 0.20].forEach { delay in
                DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
                    self?.refuseDismissGestures()
                }
            }
        }

        private func refuseDismissGestures() {
            guard let owner = owningViewController else {
                return
            }

            let popGesture = owner.navigationController?.interactivePopGestureRecognizer

            for host in searchHosts(from: owner.view, upTo: owner.navigationController?.view) {
                guard let recognizers = host.gestureRecognizers else { continue }
                for recognizer in recognizers {
                    // Belt and braces over the name filter: never the navigation
                    // controller's own pop recognizer, and never a screen-edge pan. Neither
                    // carries a refused name, so this is redundant by construction — which
                    // is exactly why it is cheap to keep.
                    if recognizer === popGesture { continue }
                    if recognizer is UIScreenEdgePanGestureRecognizer { continue }
                    guard ZoomDismissRefusal.isRefused(recognizer) else { continue }
                    recognizer.isEnabled = false
                }
            }
        }

        /// Which view UIKit hangs the dismiss interaction on is the one thing that cannot
        /// be read off the framework, so the search is made exhaustive rather than guessed:
        /// the superview chain from the pushed screen's own view up to and including the
        /// navigation controller's view, plus each of those views' immediate subviews. That
        /// is a handful of container views — it reaches the zoomed controller's view, the
        /// transition view and the navigation view, and it cannot reach a list cell.
        /// Widening the bound is free because the exact-name filter, not the bound, is what
        /// keeps this off recognizers it has no business touching.
        private func searchHosts(from start: UIView, upTo top: UIView?) -> [UIView] {
            var chain: [UIView] = []
            var current: UIView? = start

            while let view = current {
                chain.append(view)
                if view === top { break }
                current = view.superview
            }

            return chain + chain.flatMap { $0.subviews }
        }

        /// The same walk `NavigationBackButtonConfigurator` makes: the last non-navigation
        /// ancestor, which is the pushed screen's own hosting controller.
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

private struct NavigationInteractivePopGestureConfigurator: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> Controller {
        Controller()
    }

    func updateUIViewController(_ controller: Controller, context: Context) {
        controller.scheduleGestureUpdate()
    }

    final class Controller: UIViewController, UIGestureRecognizerDelegate {
        private weak var configuredNavigationController: UINavigationController?

        override func loadView() {
            let view = UIView(frame: .zero)
            view.isHidden = true
            view.isUserInteractionEnabled = false
            self.view = view
        }

        override func didMove(toParent parent: UIViewController?) {
            super.didMove(toParent: parent)
            scheduleGestureUpdate()
        }

        override func viewWillAppear(_ animated: Bool) {
            super.viewWillAppear(animated)
            scheduleGestureUpdate()
        }

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            scheduleGestureUpdate()
        }

        func scheduleGestureUpdate() {
            [0.0, 0.05, 0.20].forEach { delay in
                DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
                    self?.applyGestureState()
                }
            }
        }

        private func applyGestureState() {
            guard let navigationController = nearestNavigationController else {
                return
            }

            configuredNavigationController = navigationController
            navigationController.interactivePopGestureRecognizer?.isEnabled = true
            navigationController.interactivePopGestureRecognizer?.delegate = self
            navigationController.interactivePopGestureRecognizer?.cancelsTouchesInView = true
        }

        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            guard
                gestureRecognizer === configuredNavigationController?.interactivePopGestureRecognizer,
                let navigationController = configuredNavigationController
            else {
                return true
            }

            return navigationController.viewControllers.count > 1 &&
                navigationController.transitionCoordinator == nil
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            guard gestureRecognizer === configuredNavigationController?.interactivePopGestureRecognizer else {
                return false
            }

            // Scroll views only, which is all the blanket `true` this replaced was ever
            // written for: keeping a list scrollable while the edge pan is on the table.
            // The app's own recognizers are unaffected, because simultaneity needs only
            // ONE side to agree and theirs already answer `true` from their own delegates
            // (`SwipeActions`, `TodoListScreen`, `CalendarScreen`).
            //
            // What this stops volunteering is co-running with a recognizer the app does not
            // own. On iOS 18 that set includes the zoom transition's own interactive
            // dismiss, which is a second pop driver that neither guard in
            // `gestureRecognizerShouldBegin` above can see: it is not
            // `interactivePopGestureRecognizer`, so the `viewControllers.count > 1` and
            // `transitionCoordinator == nil` tests never apply to it. Two pop drivers
            // running at once is the shape of a navigation controller left mid-transition
            // with `isUserInteractionEnabled` never restored.
            //
            // Honest limit: this only withdraws what this delegate volunteers. If UIKit's
            // own delegate answers `true` for the same pairing, simultaneity still stands
            // and the lever has to be `require(toFail:)` or refusing the dismiss recognizer
            // outright — which is what `navigationZoomDismissRefusal()` does.
            return otherGestureRecognizer.view is UIScrollView
        }

        private var nearestNavigationController: UINavigationController? {
            var current = parent

            while let viewController = current {
                if let navigationController = viewController as? UINavigationController {
                    return navigationController
                }

                if let navigationController = viewController.navigationController {
                    return navigationController
                }

                current = viewController.parent
            }

            return view.window?.rootViewController?.nearestNavigationControllerInHierarchy()
        }
    }
}

private extension UIViewController {
    func nearestNavigationControllerInHierarchy() -> UINavigationController? {
        if let navigationController = self as? UINavigationController {
            return navigationController
        }

        if let navigationController {
            return navigationController
        }

        for child in children {
            if let navigationController = child.nearestNavigationControllerInHierarchy() {
                return navigationController
            }
        }

        if let presentedViewController {
            return presentedViewController.nearestNavigationControllerInHierarchy()
        }

        return nil
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
        TdayFont.uiFont(size: size, weight: weight)
    }
}
