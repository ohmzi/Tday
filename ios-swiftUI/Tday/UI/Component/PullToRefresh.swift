import SwiftUI
import UIKit

struct PullToRefreshContainer<Content: View>: View {
    let isRefreshing: Bool
    let isEnabled: Bool
    let action: @Sendable () async -> Void
    private let content: Content

    init(
        isRefreshing: Bool,
        isEnabled: Bool = true,
        action: @escaping @Sendable () async -> Void,
        @ViewBuilder content: () -> Content
    ) {
        self.isRefreshing = isRefreshing
        self.isEnabled = isEnabled
        self.action = action
        self.content = content()
    }

    var body: some View {
        if isEnabled {
            RefreshContainerBody(isRefreshing: isRefreshing, action: action) {
                content
            }
        } else {
            content
        }
    }
}

extension View {
    func tdayPullToRefresh(
        isRefreshing: Bool,
        isEnabled: Bool = true,
        action: @escaping @Sendable () async -> Void
    ) -> some View {
        PullToRefreshContainer(isRefreshing: isRefreshing, isEnabled: isEnabled, action: action) {
            self
        }
    }
}

private struct RefreshContainerBody<Content: View>: View {
    let isRefreshing: Bool
    let action: @Sendable () async -> Void
    private let content: Content

    @State private var pullDistance: CGFloat = 0
    @State private var localRefreshInFlight = false
    @State private var hasTriggeredForCurrentPull = false

    init(
        isRefreshing: Bool,
        action: @escaping @Sendable () async -> Void,
        @ViewBuilder content: () -> Content
    ) {
        self.isRefreshing = isRefreshing
        self.action = action
        self.content = content()
    }

    private var pullProgress: CGFloat {
        min(max(pullDistance / TdayRefreshIndicatorMetrics.triggerDistance, 0), 1)
    }

    private var effectiveRefreshing: Bool {
        isRefreshing || localRefreshInFlight
    }

    var body: some View {
        content
            .background(
                PullRefreshOffsetObserver { distance in
                    updatePullDistance(distance)
                }
            )
            .overlay(alignment: .top) {
                TdayPullRefreshIndicator(
                    isRefreshing: effectiveRefreshing,
                    pullProgress: pullProgress
                )
                .padding(.top, 10)
                .allowsHitTesting(false)
            }
            .onChange(of: pullProgress) { _, progress in
                handlePullProgressChange(progress)
            }
            .onChange(of: effectiveRefreshing) { _, refreshing in
                if !refreshing && pullProgress < 0.1 {
                    hasTriggeredForCurrentPull = false
                }
            }
    }

    private func handlePullProgressChange(_ progress: CGFloat) {
        if progress < 0.1 && !effectiveRefreshing {
            hasTriggeredForCurrentPull = false
        }

        guard progress >= 1,
              !effectiveRefreshing,
              !hasTriggeredForCurrentPull else {
            return
        }

        hasTriggeredForCurrentPull = true
        localRefreshInFlight = true
        Task {
            await action()
            await MainActor.run {
                localRefreshInFlight = false
            }
        }
    }

    private func updatePullDistance(_ distance: CGFloat) {
        let nextDistance = max(distance, 0)
        guard abs(pullDistance - nextDistance) > 0.5 ||
            (nextDistance == 0 && pullDistance != 0) else {
            return
        }

        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            pullDistance = nextDistance
        }
    }
}

private enum TdayRefreshIndicatorMetrics {
    static let triggerDistance: CGFloat = 112
    static let containerWidth: CGFloat = 152
    static let containerHeight: CGFloat = 58
    static let barCount = 5
    static let dotWidth: CGFloat = 9
    static let dotMinHeight: CGFloat = 12
    static let dotMaxHeight: CGFloat = 30
    static let dotSpacing: CGFloat = 10
    static let cornerRadius: CGFloat = 29
    static let sweepInset: CGFloat = 11
    static let sweepHeight: CGFloat = 40
}

private struct TdayPullRefreshIndicator: View {
    let isRefreshing: Bool
    let pullProgress: CGFloat

    @Environment(\.tdayColors) private var colors

    private var visible: Bool {
        isRefreshing || pullProgress > 0.02
    }

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 60.0, paused: !isRefreshing)) { context in
            let clampedProgress = min(max(pullProgress, 0), 1)
            let revealProgress = isRefreshing ? 1 : clampedProgress
            let cycle = context.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1.05) / 1.05
            let sweepTrackWidth = TdayRefreshIndicatorMetrics.containerWidth - (TdayRefreshIndicatorMetrics.sweepInset * 2)
            let refreshAccent = Color.tdayTodayBlue

            ZStack(alignment: .center) {
                if visible {
                    ZStack {
                        RoundedRectangle(cornerRadius: TdayRefreshIndicatorMetrics.sweepHeight / 2, style: .continuous)
                            .fill(refreshAccent.opacity(isRefreshing ? 0.18 : 0.08 + (Double(clampedProgress) * 0.10)))
                            .overlay {
                                RoundedRectangle(cornerRadius: TdayRefreshIndicatorMetrics.sweepHeight / 2, style: .continuous)
                                    .stroke(refreshAccent.opacity(0.14 + (Double(clampedProgress) * 0.08)), lineWidth: 1)
                            }

                        HStack(spacing: TdayRefreshIndicatorMetrics.dotSpacing) {
                            ForEach(0..<TdayRefreshIndicatorMetrics.barCount, id: \.self) { index in
                                let metrics = barMetrics(
                                    index: index,
                                    pullProgress: clampedProgress,
                                    cycle: cycle
                                )

                                RoundedRectangle(cornerRadius: TdayRefreshIndicatorMetrics.dotWidth / 2, style: .continuous)
                                    .fill(refreshAccent.opacity(metrics.opacity))
                                    .frame(
                                        width: TdayRefreshIndicatorMetrics.dotWidth,
                                        height: metrics.height
                                    )
                            }
                        }
                    }
                    .frame(width: sweepTrackWidth, height: TdayRefreshIndicatorMetrics.sweepHeight)
                    .clipShape(RoundedRectangle(cornerRadius: TdayRefreshIndicatorMetrics.sweepHeight / 2, style: .continuous))
                }
            }
            .frame(
                width: TdayRefreshIndicatorMetrics.containerWidth,
                height: TdayRefreshIndicatorMetrics.containerHeight
            )
            .background(colors.surface, in: RoundedRectangle(cornerRadius: TdayRefreshIndicatorMetrics.cornerRadius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: TdayRefreshIndicatorMetrics.cornerRadius, style: .continuous)
                    .stroke(colors.onSurface.opacity(0.12), lineWidth: 1)
            }
            .clipShape(RoundedRectangle(cornerRadius: TdayRefreshIndicatorMetrics.cornerRadius, style: .continuous))
            .shadow(color: refreshAccent.opacity(0.12), radius: 12, x: 0, y: 0)
            .shadow(color: Color.black.opacity(0.15), radius: 16, x: 0, y: 8)
            .opacity(visible ? Double(revealProgress) : 0)
            .offset(y: -18 + (18 * revealProgress))
            .animation(.easeOut(duration: 0.22), value: visible)
        }
    }

    private func barMetrics(index: Int, pullProgress: CGFloat, cycle: TimeInterval) -> (height: CGFloat, opacity: Double) {
        if isRefreshing {
            let phasedCycle = (cycle + (Double(index) * 0.11)).truncatingRemainder(dividingBy: 1)
            let wave = (sin(phasedCycle * .pi * 2) + 1) / 2
            let easedWave = CGFloat(wave * wave * (3 - (2 * wave)))
            let height = TdayRefreshIndicatorMetrics.dotMinHeight +
                ((TdayRefreshIndicatorMetrics.dotMaxHeight - TdayRefreshIndicatorMetrics.dotMinHeight) * easedWave)
            return (
                height: height,
                opacity: 0.42 + (Double(easedWave) * 0.58)
            )
        }

        let staggerStart = CGFloat(index) * 0.11
        let progress = min(max((pullProgress - staggerStart) / 0.56, 0), 1)
        let easedProgress = progress * progress * (3 - (2 * progress))
        let height = TdayRefreshIndicatorMetrics.dotMinHeight +
            ((TdayRefreshIndicatorMetrics.dotMaxHeight - TdayRefreshIndicatorMetrics.dotMinHeight) * easedProgress)
        return (
            height: height,
            opacity: 0.32 + (Double(easedProgress) * 0.68)
        )
    }
}

private struct PullRefreshOffsetObserver: UIViewRepresentable {
    let onChange: (CGFloat) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onChange: onChange)
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.onChange = onChange
        DispatchQueue.main.async {
            context.coordinator.attach(to: uiView)
        }
    }

    final class Coordinator: NSObject {
        var onChange: (CGFloat) -> Void
        private weak var observedScrollView: UIScrollView?
        private var observation: NSKeyValueObservation?
        private var overscrollDistance: CGFloat = 0
        private var gesturePullDistance: CGFloat = 0
        private var pullStartTranslationY: CGFloat?

        init(onChange: @escaping (CGFloat) -> Void) {
            self.onChange = onChange
        }

        deinit {
            observedScrollView?.panGestureRecognizer.removeTarget(self, action: #selector(handlePan(_:)))
        }

        func attach(to view: UIView) {
            guard let scrollView = view.nearestScrollView() else {
                return
            }

            hideNativeRefreshControl(in: scrollView)

            guard observedScrollView !== scrollView else {
                return
            }

            observedScrollView?.panGestureRecognizer.removeTarget(self, action: #selector(handlePan(_:)))
            observedScrollView = scrollView
            scrollView.panGestureRecognizer.addTarget(self, action: #selector(handlePan(_:)))
            observation = scrollView.observe(\.contentOffset, options: [.initial, .new]) { [weak self] scrollView, _ in
                self?.hideNativeRefreshControl(in: scrollView)
                let normalizedOffset = scrollView.contentOffset.y + scrollView.adjustedContentInset.top
                self?.overscrollDistance = max(-normalizedOffset, 0)
                self?.emitPullDistance()
            }
        }

        @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
            guard let scrollView = observedScrollView else {
                return
            }

            let normalizedOffset = scrollView.contentOffset.y + scrollView.adjustedContentInset.top
            let translationY = gesture.translation(in: scrollView).y

            switch gesture.state {
            case .began:
                pullStartTranslationY = nil
                gesturePullDistance = 0
            case .changed:
                if normalizedOffset <= 1, translationY > 0 {
                    if pullStartTranslationY == nil {
                        pullStartTranslationY = translationY
                    }
                    gesturePullDistance = max(translationY - (pullStartTranslationY ?? translationY), 0)
                } else if normalizedOffset > 1 || translationY <= 0 {
                    pullStartTranslationY = nil
                    gesturePullDistance = 0
                }
            case .ended, .cancelled, .failed:
                pullStartTranslationY = nil
                gesturePullDistance = 0
            default:
                break
            }

            emitPullDistance()
        }

        private func emitPullDistance() {
            let pullDistance = max(overscrollDistance, gesturePullDistance)
            if Thread.isMainThread {
                onChange(pullDistance)
            } else {
                DispatchQueue.main.async {
                    self.onChange(pullDistance)
                }
            }
        }

        private func hideNativeRefreshControl(in scrollView: UIScrollView) {
            guard let refreshControl = scrollView.refreshControl else {
                return
            }
            refreshControl.tintColor = .clear
            refreshControl.backgroundColor = .clear
            refreshControl.alpha = 0
            refreshControl.subviews.forEach { $0.alpha = 0 }
        }
    }
}

struct ScrollBounceDisablerRow: View {
    var body: some View {
        VerticalScrollBounceDisabler()
            .frame(height: 0)
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
            .allowsHitTesting(false)
    }
}

extension View {
    func disableVerticalScrollBounce() -> some View {
        background(VerticalScrollBounceDisabler())
    }

    func onVerticalScrollOffsetChange(_ onChange: @escaping (CGFloat) -> Void) -> some View {
        background(VerticalScrollOffsetObserver(onChange: onChange))
    }

    func onVerticalScrollSnap(collapseDistance: CGFloat) -> some View {
        background(VerticalScrollSnapObserver(collapseDistance: collapseDistance))
    }

    func onTopPartialScrollSnap(anchorDistance: CGFloat, isDisabled: Bool = false) -> some View {
        background(TopPartialScrollSnapObserver(anchorDistance: anchorDistance, isDisabled: isDisabled))
    }
}

private struct VerticalScrollBounceDisabler: UIViewRepresentable {
    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        DispatchQueue.main.async {
            context.coordinator.attach(to: uiView)
        }
    }

    final class Coordinator: NSObject {
        private weak var observedScrollView: UIScrollView?
        private var offsetObservation: NSKeyValueObservation?
        private var isClamping = false

        func attach(to view: UIView) {
            guard let scrollView = view.enclosingScrollView() else { return }
            guard observedScrollView !== scrollView else { return }
            observedScrollView = scrollView
            scrollView.bounces = false
            scrollView.alwaysBounceVertical = false

            scrollView.panGestureRecognizer.addTarget(self, action: #selector(handlePan(_:)))

            offsetObservation = scrollView.observe(\.contentOffset, options: .new) { [weak self] sv, _ in
                self?.clampTopOverscroll(sv)
            }
        }

        @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
            guard let sv = observedScrollView else { return }
            clampTopOverscroll(sv)
        }

        private func clampTopOverscroll(_ scrollView: UIScrollView) {
            guard !isClamping else { return }
            let minY = -scrollView.adjustedContentInset.top
            if scrollView.contentOffset.y < minY {
                isClamping = true
                scrollView.contentOffset = CGPoint(x: scrollView.contentOffset.x, y: minY)
                isClamping = false
            }
        }
    }
}

private struct VerticalScrollOffsetObserver: UIViewRepresentable {
    let onChange: (CGFloat) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onChange: onChange)
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.onChange = onChange
        DispatchQueue.main.async {
            context.coordinator.attach(to: uiView)
        }
    }

    final class Coordinator {
        var onChange: (CGFloat) -> Void
        private weak var observedScrollView: UIScrollView?
        private var observation: NSKeyValueObservation?

        init(onChange: @escaping (CGFloat) -> Void) {
            self.onChange = onChange
        }

        func attach(to view: UIView) {
            guard let scrollView = view.enclosingScrollView() else {
                return
            }

            guard observedScrollView !== scrollView else {
                return
            }

            observedScrollView = scrollView
            observation = scrollView.observe(\.contentOffset, options: [.initial, .new]) { [weak self] scrollView, _ in
                let normalizedOffset = scrollView.contentOffset.y + scrollView.adjustedContentInset.top
                self?.onChange(max(normalizedOffset, 0))
            }
        }
    }
}

private struct VerticalScrollSnapObserver: UIViewRepresentable {
    let collapseDistance: CGFloat

    func makeCoordinator() -> Coordinator {
        Coordinator(collapseDistance: collapseDistance)
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.collapseDistance = collapseDistance
        DispatchQueue.main.async {
            context.coordinator.attach(to: uiView)
        }
    }

    final class Coordinator: NSObject {
        var collapseDistance: CGFloat
        private weak var observedScrollView: UIScrollView?
        private var dragStartOffset: CGFloat = 0
        private var lastObservedOffset: CGFloat = 0
        private var lastDragDelta: CGFloat = 0
        private var releaseVelocityY: CGFloat = 0
        private var settledTargetOffset: CGFloat = 0
        private var snapTimer: Timer?
        private var isSnapping = false
        private let releaseVelocityThreshold: CGFloat = 90

        init(collapseDistance: CGFloat) {
            self.collapseDistance = collapseDistance
        }

        deinit {
            snapTimer?.invalidate()
        }

        func attach(to view: UIView) {
            guard let scrollView = view.enclosingScrollView() else {
                return
            }

            guard observedScrollView !== scrollView else {
                return
            }

            observedScrollView = scrollView
            scrollView.panGestureRecognizer.addTarget(self, action: #selector(handlePan(_:)))
        }

        @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
            guard let scrollView = observedScrollView else { return }
            let offset = normalizedOffset(for: scrollView)

            switch gesture.state {
            case .began:
                snapTimer?.invalidate()
                isSnapping = false
                scrollView.layer.removeAllAnimations()
                releaseVelocityY = 0
                lastDragDelta = 0
                dragStartOffset = offset
                lastObservedOffset = offset
            case .changed:
                let delta = offset - lastObservedOffset
                if abs(delta) > 0.05 {
                    lastDragDelta = delta
                }
                lastObservedOffset = offset
            case .ended, .cancelled, .failed:
                releaseVelocityY = gesture.velocity(in: scrollView).y
                scheduleSnapCheck()
            default:
                break
            }
        }

        private func scheduleSnapCheck() {
            snapTimer?.invalidate()
            snapTimer = Timer.scheduledTimer(withTimeInterval: 0.03, repeats: true) { [weak self] timer in
                guard let self else {
                    timer.invalidate()
                    return
                }
                guard let scrollView = self.observedScrollView else {
                    timer.invalidate()
                    return
                }
                if !scrollView.isTracking && !scrollView.isDragging && !scrollView.isDecelerating {
                    timer.invalidate()
                    self.maybeSnap(scrollView: scrollView)
                }
            }
        }

        private func maybeSnap(scrollView: UIScrollView) {
            let distance = collapseDistance
            guard distance > 0 else { return }
            let currentOffset = normalizedOffset(for: scrollView)
            guard maxScrollableOffset(for: scrollView) >= distance - 0.5 else {
                guard currentOffset > 0.5 else {
                    settledTargetOffset = 0
                    return
                }
                animate(scrollView: scrollView, toNormalizedOffset: 0, target: 0)
                return
            }
            guard currentOffset > 0.5 else {
                settledTargetOffset = 0
                return
            }
            guard currentOffset < distance - 0.5 else {
                settledTargetOffset = distance
                return
            }

            let target = targetOffset(for: currentOffset, distance: distance)
            let newContentY = target - scrollView.adjustedContentInset.top
            animate(scrollView: scrollView, to: CGPoint(x: scrollView.contentOffset.x, y: newContentY), target: target)
        }

        private func normalizedOffset(for scrollView: UIScrollView) -> CGFloat {
            max(scrollView.contentOffset.y + scrollView.adjustedContentInset.top, 0)
        }

        private func maxScrollableOffset(for scrollView: UIScrollView) -> CGFloat {
            max(
                scrollView.contentSize.height + scrollView.adjustedContentInset.top + scrollView.adjustedContentInset.bottom - scrollView.bounds.height,
                0
            )
        }

        private func targetOffset(for currentOffset: CGFloat, distance: CGFloat) -> CGFloat {
            if releaseVelocityY < -releaseVelocityThreshold {
                return distance
            }
            if releaseVelocityY > releaseVelocityThreshold {
                return 0
            }

            let dragDelta = currentOffset - dragStartOffset
            if dragDelta > 2 || lastDragDelta > 0.2 {
                return distance
            }
            if dragDelta < -2 || lastDragDelta < -0.2 {
                return 0
            }

            let progress = currentOffset / distance
            if abs(progress - 0.5) > 0.08 {
                return progress >= 0.5 ? distance : 0
            }

            return settledTargetOffset
        }

        private func animate(scrollView: UIScrollView, toNormalizedOffset normalizedOffset: CGFloat, target: CGFloat) {
            let newContentY = normalizedOffset - scrollView.adjustedContentInset.top
            animate(scrollView: scrollView, to: CGPoint(x: scrollView.contentOffset.x, y: newContentY), target: target)
        }

        private func animate(scrollView: UIScrollView, to offset: CGPoint, target: CGFloat) {
            guard !isSnapping else { return }
            let boundedY = min(
                max(offset.y, -scrollView.adjustedContentInset.top),
                max(-scrollView.adjustedContentInset.top, scrollView.contentSize.height - scrollView.bounds.height + scrollView.adjustedContentInset.bottom)
            )
            let targetOffset = CGPoint(x: offset.x, y: boundedY)
            guard abs(scrollView.contentOffset.y - targetOffset.y) > 0.5 else {
                settledTargetOffset = target
                return
            }

            isSnapping = true
            scrollView.layer.removeAllAnimations()
            let remainingDistance = abs(scrollView.contentOffset.y - targetOffset.y)
            let progress = min(max(remainingDistance / max(collapseDistance, 1), 0), 1)
            let duration = 0.22 + (0.12 * progress)
            let initialVelocity = min(abs(releaseVelocityY) / max(collapseDistance, 1), 2.4)
            UIView.animate(
                withDuration: duration,
                delay: 0,
                usingSpringWithDamping: 0.92,
                initialSpringVelocity: initialVelocity,
                options: [.allowUserInteraction, .beginFromCurrentState]
            ) {
                scrollView.setContentOffset(targetOffset, animated: false)
            } completion: { [weak self] _ in
                self?.settledTargetOffset = target
                self?.isSnapping = false
            }
        }
    }
}

private struct TopPartialScrollSnapObserver: UIViewRepresentable {
    let anchorDistance: CGFloat
    let isDisabled: Bool

    func makeCoordinator() -> Coordinator {
        Coordinator(anchorDistance: anchorDistance, isDisabled: isDisabled)
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.anchorDistance = anchorDistance
        context.coordinator.isDisabled = isDisabled
        DispatchQueue.main.async {
            context.coordinator.attach(to: uiView)
        }
    }

    final class Coordinator: NSObject {
        var anchorDistance: CGFloat
        var isDisabled: Bool
        private weak var observedScrollView: UIScrollView?
        private var snapTimer: Timer?
        private var isSnapping = false

        init(anchorDistance: CGFloat, isDisabled: Bool) {
            self.anchorDistance = anchorDistance
            self.isDisabled = isDisabled
        }

        deinit {
            snapTimer?.invalidate()
        }

        func attach(to view: UIView) {
            guard let scrollView = view.enclosingScrollView() else {
                return
            }

            guard observedScrollView !== scrollView else {
                return
            }

            observedScrollView = scrollView
            scrollView.panGestureRecognizer.addTarget(self, action: #selector(handlePan(_:)))
        }

        @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
            switch gesture.state {
            case .ended, .cancelled, .failed:
                scheduleSnapCheck()
            default:
                break
            }
        }

        private func scheduleSnapCheck() {
            guard !isDisabled else { return }
            snapTimer?.invalidate()
            snapTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] timer in
                guard let self else {
                    timer.invalidate()
                    return
                }
                guard let scrollView = self.observedScrollView else {
                    timer.invalidate()
                    return
                }
                if !scrollView.isTracking && !scrollView.isDragging && !scrollView.isDecelerating {
                    timer.invalidate()
                    self.maybeSnap(scrollView: scrollView)
                }
            }
        }

        private func maybeSnap(scrollView: UIScrollView) {
            guard !isDisabled, !isSnapping, anchorDistance > 0 else { return }
            guard maxScrollableOffset(for: scrollView) > 0.5 else { return }

            let currentOffset = normalizedOffset(for: scrollView)
            guard currentOffset > 0.5 && currentOffset < anchorDistance - 0.5 else {
                return
            }

            animate(scrollView: scrollView, toNormalizedOffset: 0)
        }

        private func normalizedOffset(for scrollView: UIScrollView) -> CGFloat {
            max(scrollView.contentOffset.y + scrollView.adjustedContentInset.top, 0)
        }

        private func maxScrollableOffset(for scrollView: UIScrollView) -> CGFloat {
            max(scrollView.contentSize.height + scrollView.adjustedContentInset.bottom - scrollView.bounds.height, 0)
        }

        private func animate(scrollView: UIScrollView, toNormalizedOffset normalizedOffset: CGFloat) {
            let targetY = normalizedOffset - scrollView.adjustedContentInset.top
            guard abs(scrollView.contentOffset.y - targetY) > 0.5 else { return }

            isSnapping = true
            scrollView.layer.removeAllAnimations()
            UIView.animate(
                withDuration: 0.26,
                delay: 0,
                options: [.allowUserInteraction, .beginFromCurrentState, .curveEaseInOut]
            ) {
                scrollView.setContentOffset(CGPoint(x: scrollView.contentOffset.x, y: targetY), animated: false)
            } completion: { [weak self] _ in
                self?.isSnapping = false
            }
        }
    }
}

private extension UIView {
    func nearestScrollView() -> UIScrollView? {
        if let enclosing = enclosingScrollView() {
            return enclosing
        }

        var current = superview
        while let view = current {
            if let scrollView = view.firstDescendantScrollView() {
                return scrollView
            }
            current = view.superview
        }
        return nil
    }

    func enclosingScrollView() -> UIScrollView? {
        var current: UIView? = self
        while let view = current {
            if let scrollView = view as? UIScrollView {
                return scrollView
            }
            current = view.superview
        }
        return nil
    }

    private func firstDescendantScrollView() -> UIScrollView? {
        for subview in subviews {
            if let scrollView = subview as? UIScrollView {
                return scrollView
            }
            if let scrollView = subview.firstDescendantScrollView() {
                return scrollView
            }
        }
        return nil
    }
}
