import SwiftUI
import UIKit

struct PullToRefreshContainer<Content: View>: View {
    let isRefreshing: Bool
    let action: @Sendable () async -> Void
    private let content: Content

    init(
        isRefreshing: Bool,
        action: @escaping @Sendable () async -> Void,
        @ViewBuilder content: () -> Content
    ) {
        self.isRefreshing = isRefreshing
        self.action = action
        self.content = content()
    }

    var body: some View {
        RefreshContainerBody(isRefreshing: isRefreshing, action: action) {
            content
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
                    pullDistance = distance
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
}

private enum TdayRefreshIndicatorMetrics {
    static let triggerDistance: CGFloat = 98
    static let containerWidth: CGFloat = 116
    static let containerHeight: CGFloat = 50
    static let barCount = 5
    static let dotWidth: CGFloat = 8
    static let dotMinHeight: CGFloat = 8
    static let dotMaxHeight: CGFloat = 29
    static let dotSpacing: CGFloat = 9
    static let cornerRadius: CGFloat = 25
    static let sweepInset: CGFloat = 8
    static let sweepHeight: CGFloat = 34
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
            let cycle = context.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1.05) / 1.05
            let sweepTrackWidth = TdayRefreshIndicatorMetrics.containerWidth - (TdayRefreshIndicatorMetrics.sweepInset * 2)
            let sweepWidth = isRefreshing ? sweepTrackWidth * 0.48 : sweepTrackWidth * clampedProgress
            let sweepOffset = isRefreshing ? (sweepTrackWidth - sweepWidth) * CGFloat(cycle) : 0

            ZStack(alignment: .center) {
                if visible {
                    RoundedRectangle(cornerRadius: TdayRefreshIndicatorMetrics.sweepHeight / 2, style: .continuous)
                        .fill(colors.primary.opacity(isRefreshing ? 0.18 : 0.10 + (Double(clampedProgress) * 0.08)))
                        .frame(
                            width: max(sweepWidth, TdayRefreshIndicatorMetrics.dotWidth),
                            height: TdayRefreshIndicatorMetrics.sweepHeight
                        )
                        .offset(x: -(TdayRefreshIndicatorMetrics.containerWidth / 2) + TdayRefreshIndicatorMetrics.sweepInset + (sweepWidth / 2) + sweepOffset)
                        .animation(.easeOut(duration: 0.16), value: clampedProgress)
                }

                HStack(spacing: TdayRefreshIndicatorMetrics.dotSpacing) {
                    ForEach(0..<TdayRefreshIndicatorMetrics.barCount, id: \.self) { index in
                        let metrics = barMetrics(
                            index: index,
                            pullProgress: clampedProgress,
                            cycle: cycle
                        )

                        RoundedRectangle(cornerRadius: TdayRefreshIndicatorMetrics.dotWidth / 2, style: .continuous)
                            .fill(colors.primary.opacity(metrics.opacity))
                            .frame(
                                width: TdayRefreshIndicatorMetrics.dotWidth,
                                height: metrics.height
                            )
                            .shadow(color: colors.primary.opacity(metrics.shadowOpacity), radius: 6, x: 0, y: 0)
                            .offset(y: metrics.verticalOffset)
                    }
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
            .shadow(color: colors.primary.opacity(0.10), radius: 10, x: 0, y: 0)
            .shadow(color: Color.black.opacity(0.14), radius: 14, x: 0, y: 7)
            .opacity(visible ? 1 : 0)
            .scaleEffect(visible ? 1 : 0.82)
            .offset(y: visible ? 0 : -16)
            .animation(.easeOut(duration: 0.22), value: visible)
        }
    }

    private func barMetrics(index: Int, pullProgress: CGFloat, cycle: TimeInterval) -> (height: CGFloat, opacity: Double, shadowOpacity: Double, verticalOffset: CGFloat) {
        if isRefreshing {
            let phasedCycle = (cycle + (Double(index) * 0.11)).truncatingRemainder(dividingBy: 1)
            let wave = (sin(phasedCycle * .pi * 2) + 1) / 2
            let easedWave = CGFloat(wave * wave * (3 - (2 * wave)))
            let height = TdayRefreshIndicatorMetrics.dotMinHeight +
                ((TdayRefreshIndicatorMetrics.dotMaxHeight - TdayRefreshIndicatorMetrics.dotMinHeight) * easedWave)
            return (
                height: height,
                opacity: 0.42 + (Double(easedWave) * 0.58),
                shadowOpacity: 0.08 + (Double(easedWave) * 0.28),
                verticalOffset: -(height - TdayRefreshIndicatorMetrics.dotMinHeight) / 2
            )
        }

        let staggerStart = CGFloat(index) * 0.11
        let progress = min(max((pullProgress - staggerStart) / 0.56, 0), 1)
        let easedProgress = progress * progress * (3 - (2 * progress))
        let height = TdayRefreshIndicatorMetrics.dotMinHeight +
            ((TdayRefreshIndicatorMetrics.dotMaxHeight - TdayRefreshIndicatorMetrics.dotMinHeight) * easedProgress)
        return (
            height: height,
            opacity: 0.32 + (Double(easedProgress) * 0.68),
            shadowOpacity: Double(easedProgress) * 0.22,
            verticalOffset: -(height - TdayRefreshIndicatorMetrics.dotMinHeight) / 2
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

    final class Coordinator {
        var onChange: (CGFloat) -> Void
        private weak var observedScrollView: UIScrollView?
        private var observation: NSKeyValueObservation?

        init(onChange: @escaping (CGFloat) -> Void) {
            self.onChange = onChange
        }

        func attach(to view: UIView) {
            guard let scrollView = view.nearestScrollView() else {
                return
            }

            hideNativeRefreshControl(in: scrollView)

            guard observedScrollView !== scrollView else {
                return
            }

            observedScrollView = scrollView
            observation = scrollView.observe(\.contentOffset, options: [.initial, .new]) { [weak self] scrollView, _ in
                self?.hideNativeRefreshControl(in: scrollView)
                let normalizedOffset = scrollView.contentOffset.y + scrollView.adjustedContentInset.top
                let pullDistance = max(-normalizedOffset, 0)
                DispatchQueue.main.async {
                    self?.onChange(pullDistance)
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
        private var isSnapping = false

        init(collapseDistance: CGFloat) {
            self.collapseDistance = collapseDistance
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
                isSnapping = false
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
                DispatchQueue.main.async { [weak self, weak scrollView] in
                    guard let self, let scrollView else { return }
                    self.maybeSnap(scrollView: scrollView)
                }
            default:
                break
            }
        }

        private func maybeSnap(scrollView: UIScrollView) {
            let distance = collapseDistance
            guard distance > 0 else { return }
            let currentOffset = normalizedOffset(for: scrollView)
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

        private func targetOffset(for currentOffset: CGFloat, distance: CGFloat) -> CGFloat {
            let velocityThreshold: CGFloat = 20
            if releaseVelocityY < -velocityThreshold {
                return distance
            }
            if releaseVelocityY > velocityThreshold {
                return 0
            }

            let dragDelta = currentOffset - dragStartOffset
            if dragDelta > 0.5 || lastDragDelta > 0.05 {
                return distance
            }
            if dragDelta < -0.5 || lastDragDelta < -0.05 {
                return 0
            }

            return settledTargetOffset
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
            let initialVelocity = min(abs(releaseVelocityY) / max(collapseDistance, 1), 3)
            UIView.animate(
                withDuration: 0.34,
                delay: 0,
                usingSpringWithDamping: 0.88,
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
