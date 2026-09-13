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

            // Rebuilding tears out the views the in-flight scroll was aimed at and resets the
            // content size under it, and UIKit reports none of that back through the delegate.
            // The index this animation was travelling to no longer means the month it meant when
            // it started, so the flags describing it are stale the moment the pages change.
            endProgrammaticScroll()

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
            guard abs(scrollView.contentOffset.x - targetX) > 0.5 else {
                // Already parked on the target, so nothing is going to scroll and no delegate
                // callback is coming. An un-animated request is the app putting the pager back
                // where it belongs — after a settle, after a month change — and arriving there is
                // the end of the programmatic scroll however we arrived. Returning without saying
                // so is how a flag set by an earlier animated request outlived the animation that
                // was meant to clear it, and a stranded flag here silences every settle the pager
                // will ever report.
                if !animated {
                    endProgrammaticScroll()
                }
                return
            }
            // An animated request for the page we are already animating towards is a duplicate
            // update, not a new scroll: the original animation is still running and still owns
            // these flags, so it — not this call — is what clears them.
            guard !animated || programmaticSelection != selection else { return }

            isProgrammaticScroll = true
            programmaticSelection = animated ? selection : nil
            scrollView.setContentOffset(CGPoint(x: targetX, y: 0), animated: animated)
            if !animated {
                // `setContentOffset(_:animated: false)` fires no
                // `scrollViewDidEndScrollingAnimation`, so this jump has to retract its own flag
                // rather than wait for a callback that is never sent.
                endProgrammaticScroll()
            }
        }

        func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
            // UIKit kills a `setContentOffset(_:animated: true)` the instant a touch takes the
            // scroll view, and it does NOT call `scrollViewDidEndScrollingAnimation` for the
            // animation it just threw away. That callback was the only thing clearing these flags,
            // so a finger landing on a chevron slide left `isProgrammaticScroll` true with nothing
            // in the app able to set it false again: `updateSelection` then declined every drag
            // that followed, the calendar stopped changing page on swipe, `pageSelection` never
            // returned to centre, and both chevrons stayed disabled until the process died.
            //
            // A drag beginning is the unambiguous signal that whatever the app was animating no
            // longer owns this scroll view, which makes this the one callback UIKit is guaranteed
            // to send on the path that used to strand.
            endProgrammaticScroll()
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
            endProgrammaticScroll()
            notifySettledSelection(from: scrollView)
        }

        /// Forgets an in-flight programmatic scroll.
        ///
        /// `isProgrammaticScroll` exists to stop a scroll the app started from being read back as
        /// a page the user chose, which makes it a latch — and every latch needs a path that opens
        /// it again on each frame the animation stops mattering, not just the happy one. Routing
        /// all of them through a single method is what keeps that list honest: there are four ways
        /// a programmatic scroll can end here and only one of them is UIKit telling us so.
        private func endProgrammaticScroll() {
            isProgrammaticScroll = false
            programmaticSelection = nil
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
