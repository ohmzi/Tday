import SwiftUI
import UIKit

let calendarNativePagerCenterIndex = 1

enum CalendarPagerDirection {
    case previous
    case next

    var pageIndex: Int {
        switch self {
        case .previous:
            return 0
        case .next:
            return 2
        }
    }
}

struct CalendarPagerPage: Identifiable {
    let id: Int
    let content: AnyView
}

struct CalendarPagingScrollView: UIViewRepresentable {
    let pages: [CalendarPagerPage]
    @Binding var selection: Int
    let onSettledSelection: (Int) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> UIScrollView {
        let scrollView = UIScrollView()
        scrollView.isPagingEnabled = true
        scrollView.bounces = false
        scrollView.alwaysBounceHorizontal = false
        scrollView.alwaysBounceVertical = false
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.showsVerticalScrollIndicator = false
        scrollView.decelerationRate = .fast
        scrollView.delegate = context.coordinator
        scrollView.backgroundColor = .clear
        scrollView.clipsToBounds = true

        let stackView = UIStackView()
        stackView.axis = .horizontal
        stackView.alignment = .fill
        stackView.distribution = .fill
        stackView.spacing = 0
        stackView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(stackView)

        NSLayoutConstraint.activate([
            stackView.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            stackView.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            stackView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            stackView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            stackView.heightAnchor.constraint(equalTo: scrollView.frameLayoutGuide.heightAnchor)
        ])

        context.coordinator.stackView = stackView
        return scrollView
    }

    func updateUIView(_ scrollView: UIScrollView, context: Context) {
        context.coordinator.parent = self
        context.coordinator.rebuildPagesIfNeeded(pages, in: scrollView)
        context.coordinator.scrollToSelection(
            selection,
            in: scrollView,
            animated: selection != calendarNativePagerCenterIndex
        )
    }

    final class Coordinator: NSObject, UIScrollViewDelegate {
        var parent: CalendarPagingScrollView?
        var stackView: UIStackView?
        private var hostedControllers: [UIHostingController<AnyView>] = []
        private var pageIDs: [Int] = []
        private var isProgrammaticScroll = false
        private var programmaticSelection: Int?

        func rebuildPagesIfNeeded(_ pages: [CalendarPagerPage], in scrollView: UIScrollView) {
            let incomingIDs = pages.map(\.id)
            guard incomingIDs != pageIDs else {
                for (controller, page) in zip(hostedControllers, pages) {
                    controller.rootView = page.content
                }
                return
            }

            hostedControllers.forEach { controller in
                controller.view.removeFromSuperview()
            }
            hostedControllers.removeAll()
            pageIDs = incomingIDs

            guard let stackView else { return }
            stackView.arrangedSubviews.forEach { view in
                stackView.removeArrangedSubview(view)
                view.removeFromSuperview()
            }

            for page in pages {
                let controller = UIHostingController(rootView: page.content)
                controller.view.backgroundColor = .clear
                controller.view.translatesAutoresizingMaskIntoConstraints = false
                hostedControllers.append(controller)
                stackView.addArrangedSubview(controller.view)

                NSLayoutConstraint.activate([
                    controller.view.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor),
                    controller.view.heightAnchor.constraint(equalTo: scrollView.frameLayoutGuide.heightAnchor)
                ])
            }
        }

        func scrollToSelection(_ selection: Int, in scrollView: UIScrollView, animated: Bool) {
            guard let index = pageIDs.firstIndex(of: selection) else { return }

            scrollView.layoutIfNeeded()
            guard scrollView.bounds.width > 0 else {
                DispatchQueue.main.async { [weak self, weak scrollView] in
                    guard let self, let scrollView else { return }
                    self.scrollToSelection(selection, in: scrollView, animated: false)
                }
                return
            }

            let targetX = CGFloat(index) * scrollView.bounds.width
            guard abs(scrollView.contentOffset.x - targetX) > 0.5 else { return }
            guard !animated || programmaticSelection != selection else { return }

            isProgrammaticScroll = true
            programmaticSelection = animated ? selection : nil
            scrollView.setContentOffset(CGPoint(x: targetX, y: 0), animated: animated)
            if !animated {
                isProgrammaticScroll = false
            }
        }

        func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
            updateSelection(from: scrollView)
        }

        func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
            if !decelerate {
                updateSelection(from: scrollView)
            }
        }

        func scrollViewDidEndScrollingAnimation(_ scrollView: UIScrollView) {
            isProgrammaticScroll = false
            programmaticSelection = nil
            notifySettledSelection(from: scrollView)
        }

        private func updateSelection(from scrollView: UIScrollView) {
            guard !isProgrammaticScroll else { return }
            notifySettledSelection(from: scrollView)
        }

        private func notifySettledSelection(from scrollView: UIScrollView) {
            guard scrollView.bounds.width > 0 else { return }
            guard let selectedID = settledPageID(from: scrollView) else { return }
            notifyParentIfNeeded(selectedID)
        }

        private func settledPageID(from scrollView: UIScrollView) -> Int? {
            let index = Int(round(scrollView.contentOffset.x / scrollView.bounds.width))
            guard pageIDs.indices.contains(index) else { return nil }
            return pageIDs[index]
        }

        private func notifyParentIfNeeded(_ selectedID: Int) {
            guard selectedID != calendarNativePagerCenterIndex else { return }
            DispatchQueue.main.async {
                self.parent?.onSettledSelection(selectedID)
            }
        }
    }
}
