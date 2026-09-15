import SwiftUI

enum TaskSwipeActionTint {
    static let edit = Color(.sRGB, red: 76.0 / 255.0, green: 125.0 / 255.0, blue: 222.0 / 255.0, opacity: 1)
    static let delete = Color(.sRGB, red: 255.0 / 255.0, green: 69.0 / 255.0, blue: 58.0 / 255.0, opacity: 1)
    static let schedule = Color(.sRGB, red: 51.0 / 255.0, green: 153.0 / 255.0, blue: 136.0 / 255.0, opacity: 1)
    static let float = Color(.sRGB, red: 122.0 / 255.0, green: 156.0 / 255.0, blue: 108.0 / 255.0, opacity: 1)
    static let copy = Color(.sRGB, red: 175.0 / 255.0, green: 82.0 / 255.0, blue: 222.0 / 255.0, opacity: 1)
}

/// An optional mode-specific action revealed before Edit/Delete in the
/// trailing swipe row (e.g. "Schedule" on floaters, "Float" on overdue).
struct TodoSwipeExtraAction {
    let title: String
    /// Asset-catalog name of the lucide template glyph (shared with web/Android).
    let assetName: String
    let tint: Color
    let action: () -> Void
}

extension View {
    /// The app's only row-action gesture, on every task row on every screen.
    ///
    /// Hand-rolled rather than SwiftUI's `.swipeActions` for two reasons that
    /// both survive review: the reveal is a row of pills that fade and scale in
    /// on a per-pill stagger, which `.swipeActions` has no way to express, and a
    /// `List` row carrying both would hand the same horizontal drag to two
    /// recognizers at once. A `UIPanGestureRecognizer` is invisible to the
    /// accessibility API, so the row also carries the same actions as
    /// `.accessibilityActions` — see `body(content:)`. Anything added to the
    /// pills belongs in both, and the guardrail suite says so out loud.
    func todoTrailingSwipeActions(
        rowID: String,
        openRowID: Binding<String?>,
        enabled: Bool = true,
        extraAction: TodoSwipeExtraAction? = nil,
        onEdit: @escaping () -> Void,
        onCopy: @escaping () -> Void,
        onDelete: @escaping () -> Void
    ) -> some View {
        modifier(
            TodoTrailingSwipeActionsModifier(
                rowID: rowID,
                openRowID: openRowID,
                enabled: enabled,
                extraAction: extraAction,
                onEdit: onEdit,
                onCopy: onCopy,
                onDelete: onDelete
            )
        )
    }
}

private struct TodoTrailingSwipeActionsModifier: ViewModifier {
    let rowID: String
    @Binding var openRowID: String?
    let enabled: Bool
    let extraAction: TodoSwipeExtraAction?
    let onEdit: () -> Void
    let onCopy: () -> Void
    let onDelete: () -> Void

    @State private var offsetX: CGFloat = 0
    @State private var isHinting = false

    // Edit + Copy + Delete always show (76pt/pill); the optional mode-specific
    // extraAction (Schedule/Float/Defer) adds a 4th. Reveal width is simply
    // pill-width × button count so the fully-revealed row always seats every
    // pill without clipping or leftover slack.
    private static let pillWidth: CGFloat = 76
    private var buttonCount: Int { extraAction == nil ? 3 : 4 }
    private var revealWidth: CGFloat { Self.pillWidth * CGFloat(buttonCount) }

    private var revealProgress: CGFloat {
        min(1, max(0, -offsetX / revealWidth))
    }

    func body(content: Content) -> some View {
        ZStack(alignment: .trailing) {
            content
                .offset(x: offsetX)
                .contentShape(Rectangle())
                .background(
                    HorizontalSwipePanObserver(
                        enabled: enabled,
                        rowID: rowID,
                        openRowID: $openRowID,
                        revealWidth: revealWidth,
                        offsetX: $offsetX
                    )
                )
                .onTapGesture {
                    guard enabled else { return }
                    if offsetX != 0 {
                        closeActions()
                    } else {
                        HapticManager.reveal()
                        revealHint()
                    }
                }
                .onChange(of: openRowID) { _, activeID in
                    if activeID != rowID && offsetX != 0 {
                        closeActions(clearOpenRow: false)
                    }
                }
                .onChange(of: enabled) { _, isEnabled in
                    if !isEnabled {
                        closeActions()
                    }
                }
                // The pan recognizer this row installs is invisible to the
                // accessibility API, and there is no context menu on a task row
                // anywhere in the app — so without this block Edit, Copy, Delete
                // and the mode's own third action have no way in at all, and the
                // row is read-only to VoiceOver, Switch Control and Full Keyboard
                // Access alike. They are the same four closures the pills call,
                // haptic included: a second path to an action must not be a
                // quieter one.
                //
                // `enabled` gates them because it is already the row's answer to
                // "can this be acted on" — a viewer's list, a row mid-completion,
                // a selection sweep. An action offered where the pills are not is
                // one the app would refuse to perform.
                .accessibilityActions {
                    if enabled {
                        // The extra action keeps the pill's own word rather than
                        // a fuller phrase invented here. Schedule, Float and Defer
                        // are what the app teaches this button is called; a second
                        // name for it would only be a second name.
                        if let extraAction {
                            Button(extraAction.title) {
                                HapticManager.buttonPress()
                                closeActions()
                                extraAction.action()
                            }
                        }

                        Button(L("Edit task")) {
                            HapticManager.buttonPress()
                            closeActions()
                            onEdit()
                        }

                        Button(L("Copy task")) {
                            HapticManager.buttonPress()
                            closeActions()
                            onCopy()
                        }

                        Button(L("Delete task"), role: .destructive) {
                            HapticManager.destructive()
                            closeActions()
                            onDelete()
                        }
                    }
                }

            HStack(spacing: 16) {
                Spacer()
                if let extraAction {
                    TodoSwipePillActionButton(
                        title: extraAction.title,
                        assetName: extraAction.assetName,
                        tint: extraAction.tint,
                        revealProgress: revealProgress,
                        revealDelay: 0.74
                    ) {
                        HapticManager.buttonPress()
                        closeActions()
                        extraAction.action()
                    }
                }

                TodoSwipePillActionButton(
                    title: "Edit",
                    assetName: "ActionEdit",
                    tint: TaskSwipeActionTint.edit,
                    revealProgress: revealProgress,
                    revealDelay: 0.62
                ) {
                    HapticManager.buttonPress()
                    closeActions()
                    onEdit()
                }

                TodoSwipePillActionButton(
                    title: "Copy",
                    assetName: "ActionCopy",
                    tint: TaskSwipeActionTint.copy,
                    revealProgress: revealProgress,
                    revealDelay: 0.40
                ) {
                    HapticManager.buttonPress()
                    closeActions()
                    onCopy()
                }

                TodoSwipePillActionButton(
                    title: "Delete",
                    assetName: "ActionDelete",
                    tint: TaskSwipeActionTint.delete,
                    revealProgress: revealProgress,
                    revealDelay: 0.04
                ) {
                    HapticManager.destructive()
                    closeActions()
                    onDelete()
                }
            }
            .padding(.trailing, 2)
            .frame(maxWidth: .infinity)
            // Not decorative — the same four actions, behind a reveal only a pan
            // can perform. Left exposed they are four transparent, un-hittable
            // buttons sitting in the tree beside the real ones, so VoiceOver
            // would read Edit twice and only one of them would do anything.
            .accessibilityHidden(true)
        }
    }

    private func claimRow() {
        if openRowID != rowID {
            openRowID = rowID
        }
    }

    private func closeActions(clearOpenRow: Bool = true) {
        withAnimation(.interactiveSpring(response: 0.26, dampingFraction: 0.86)) {
            offsetX = 0
        }
        if clearOpenRow && openRowID == rowID {
            openRowID = nil
        }
    }

    private func revealHint() {
        guard !isHinting else { return }

        isHinting = true
        claimRow()
        Task { @MainActor in
            withAnimation(.spring(response: 0.26, dampingFraction: 0.78)) {
                offsetX = -28
            }
            try? await Task.sleep(nanoseconds: 150_000_000)
            withAnimation(.spring(response: 0.38, dampingFraction: 0.68)) {
                offsetX = 0
            }
            try? await Task.sleep(nanoseconds: 340_000_000)
            isHinting = false
            if openRowID == rowID && offsetX == 0 {
                openRowID = nil
            }
        }
    }
}

/// When a task row is committed to opening, and the one place the two numbers that decide
/// it are written.
///
/// Lifted out of `handlePan` for the reason `RootFeedDockCollapse` was lifted out of the two
/// root feeds: a pan decision living in an `@objc` method, on a coordinator nested inside a
/// `private struct`, is unreachable from `xctest` — and there is no Swift toolchain on the
/// machine this app is written on, so the suite that reads this file is the only one that
/// ever will. It matters more here than there, because the thing this decision now drives is
/// a haptic, and no gate in this repository can feel a haptic. The decision is what gets
/// proven instead: `Tests/TdayCoreTests/TaskSwipeRevealDetentTests.swift` drives the two
/// functions below the way a finger drives them.
///
/// Both numbers were already in the file and neither is new. `openThresholdFraction` was a
/// bare `0.32` inside the release branch — the number Android has named at
/// `TaskSwipeRevealState.SWIPE_OPEN_THRESHOLD_FRACTION` since that file was written, and the
/// one thing of the pair iOS had never named. `openVelocityThreshold` was written three
/// times for one value: a stored property on the modifier, a `let` threaded through the
/// representable, and a default on the coordinator that the threading overwrote with the
/// same `-180`. Three copies of a number is one number on paper and three the first time
/// anybody tunes one of them.
///
/// Deliberately no spring and no `response:` / `dampingFraction:` / `duration:` of its own.
/// The settle these two decide the target of is argued where it stands at the end of
/// `handlePan`, and `tday-web/tests/guardrails/motion-parity.test.ts` holds this client's
/// two spring counters full to the line.
enum TaskSwipeRevealDetent {

    /// How far across the reveal the row has to have travelled for a release to open it.
    static let openThresholdFraction: CGFloat = 0.32

    /// How fast leftwards a release has to be to open the row from wherever it has got to.
    static let openVelocityThreshold: CGFloat = -180

    /// "If the finger lifted right now, this row would open."
    ///
    /// ``shouldOpen(offsetX:velocityX:revealWidth:)``'s own first disjunct with the velocity
    /// term left out, and not a threshold invented for the haptic that fires on it. That is
    /// what keeps the buzz honest: feeling it means let go now and the actions stay out.
    static func isCommittedOpen(offsetX: CGFloat, revealWidth: CGFloat) -> Bool {
        offsetX < -(revealWidth * openThresholdFraction)
    }

    /// Where the row goes when the finger leaves: open, or home.
    ///
    /// An OR, and that is the whole reason the reveal haptic needs an arm at release as well
    /// as one under the finger. A short hard flick — 20 points and gone — opens a row that
    /// never came near the detent, so a detent-only haptic would leave the fastest, most
    /// deliberate swipe in the app as the only silent one.
    static func shouldOpen(offsetX: CGFloat, velocityX: CGFloat, revealWidth: CGFloat) -> Bool {
        isCommittedOpen(offsetX: offsetX, revealWidth: revealWidth)
            || velocityX < openVelocityThreshold
    }
}

private struct HorizontalSwipePanObserver: UIViewRepresentable {
    let enabled: Bool
    let rowID: String
    @Binding var openRowID: String?
    let revealWidth: CGFloat
    @Binding var offsetX: CGFloat

    func makeCoordinator() -> Coordinator {
        Coordinator(rowID: rowID, openRowID: $openRowID, offsetX: $offsetX)
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.enabled = enabled
        context.coordinator.rowID = rowID
        context.coordinator.openRowID = $openRowID
        context.coordinator.revealWidth = revealWidth
        context.coordinator.offsetX = $offsetX
        DispatchQueue.main.async {
            context.coordinator.attach(to: uiView)
        }
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var enabled = true
        var rowID: String
        var openRowID: Binding<String?>
        var revealWidth: CGFloat = 152
        var offsetX: Binding<CGFloat>

        private weak var markerView: UIView?
        private weak var observedScrollView: UIScrollView?
        private var dragStartOffsetX: CGFloat = 0

        /// Whether this open-cycle has already spent its reveal haptic.
        ///
        /// Seeded at `.began`, set by whichever of the two arms fires, and read nowhere
        /// else. Sampling is the failure it exists to prevent: "is the row past the detent?"
        /// answers yes on every update a finger spends resting there, and a detent that
        /// repeats for as long as you hold still is a rattle rather than a detent.
        ///
        /// One boolean, and deliberately not a second threshold under the first.
        /// `RootFeedDockCollapse` carries a dead band because a single comparison flips on
        /// its own boundary and that dock has no per-gesture memory to lean on; a pan does.
        /// A flag that cannot re-arm inside a gesture *at all* is strictly stronger than a
        /// band: a finger parked exactly on the boundary cannot repeat, and crossing,
        /// coming back and crossing again cannot fire twice. A band here would be a number
        /// nobody could justify, solving a problem the flag has already solved.
        ///
        /// Its lifetime is the row's: the coordinator is made once per row and survives the
        /// body updates that re-assign the bindings, so nothing about a redraw re-arms it.
        private var hasFiredRevealDetent = false

        private lazy var panRecognizer: UIPanGestureRecognizer = {
            let recognizer = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
            recognizer.cancelsTouchesInView = false
            recognizer.delaysTouchesBegan = false
            recognizer.delaysTouchesEnded = false
            recognizer.delegate = self
            return recognizer
        }()

        init(rowID: String, openRowID: Binding<String?>, offsetX: Binding<CGFloat>) {
            self.rowID = rowID
            self.openRowID = openRowID
            self.offsetX = offsetX
        }

        deinit {
            // Re-enable scrolling in case the row is torn down mid-swipe.
            observedScrollView?.isScrollEnabled = true
            observedScrollView?.removeGestureRecognizer(panRecognizer)
        }

        func attach(to markerView: UIView) {
            self.markerView = markerView
            guard let scrollView = markerView.enclosingSwipeScrollView() else {
                return
            }
            guard observedScrollView !== scrollView else {
                return
            }

            observedScrollView?.removeGestureRecognizer(panRecognizer)
            observedScrollView = scrollView
            scrollView.addGestureRecognizer(panRecognizer)
        }

        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            guard enabled,
                  gestureRecognizer === panRecognizer,
                  let scrollView = observedScrollView,
                  let markerView else {
                return false
            }

            let location = panRecognizer.location(in: markerView)
            guard markerView.bounds.insetBy(dx: 0, dy: -4).contains(location) else {
                return false
            }

            let velocity = panRecognizer.velocity(in: scrollView)
            let horizontalVelocity = abs(velocity.x)
            let verticalVelocity = abs(velocity.y)
            return horizontalVelocity > 45 && horizontalVelocity > verticalVelocity + 28
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }

        @objc private func handlePan(_ recognizer: UIPanGestureRecognizer) {
            guard let scrollView = observedScrollView else {
                return
            }
            guard enabled else {
                // Swiping was disabled mid-gesture — never leave the list locked.
                scrollView.isScrollEnabled = true
                return
            }

            switch recognizer.state {
            case .began:
                dragStartOffsetX = offsetX.wrappedValue
                // A gesture that starts on a row already past the detent starts with its
                // buzz spent: the actions are out, and dragging an open row further open —
                // or partway back and out again — uncovers nothing. Seeding the flag here
                // rather than clearing it on the way closed is what lets this change touch
                // none of the four close paths, since a closed row's next gesture begins at
                // 0 and is therefore armed. Those four are `closeActions` from a tap or a
                // pill, the row whose single open slot another row claimed, the `enabled`
                // sweep, and the tail of the tap hint — and three of them close a row the
                // finger is nowhere near.
                hasFiredRevealDetent = TaskSwipeRevealDetent.isCommittedOpen(
                    offsetX: dragStartOffsetX,
                    revealWidth: revealWidth
                )
                // Freeze vertical scrolling for the duration of the horizontal reveal. The pan
                // recognizer lives on the scroll view and recognizes simultaneously, so without
                // this the drag's small vertical component keeps nudging the list and it stutters.
                scrollView.isScrollEnabled = false
                if dragStartOffsetX != 0 {
                    openRowID.wrappedValue = rowID
                }
            case .changed:
                let translation = recognizer.translation(in: scrollView)
                let proposed = dragStartOffsetX + translation.x
                if proposed < 0 {
                    openRowID.wrappedValue = rowID
                    offsetX.wrappedValue = max(-revealWidth * 1.12, min(0, proposed))
                    // Arm A, the detent: the first update on which letting go would leave
                    // the actions out. Asked after the clamp and not before, because the
                    // overdrag limit is part of where the finger has actually put the row.
                    //
                    // This moment and not the settle. What was asked for is a row *slid*
                    // left to show the buttons behind it, and that is here — the actions
                    // catching under the thumb — rather than the app reporting an animation
                    // after the hand has already gone.
                    //
                    // The one honest cost, said out loud rather than left for a device to
                    // find: cross the detent, drag back, release closed, and you have felt
                    // a reveal that did not happen. That is what a detent on a physical
                    // control does. The alternative is silence until the row settles, which
                    // costs the feature the point of it.
                    if !hasFiredRevealDetent,
                       TaskSwipeRevealDetent.isCommittedOpen(
                           offsetX: offsetX.wrappedValue,
                           revealWidth: revealWidth
                       ) {
                        hasFiredRevealDetent = true
                        HapticManager.reveal()
                    }
                } else {
                    offsetX.wrappedValue = 0
                    if openRowID.wrappedValue == rowID {
                        openRowID.wrappedValue = nil
                    }
                    // The flag stays set on purpose. The row is at 0 but it is not at rest:
                    // a finger that crosses the detent, drags back past the row's own edge
                    // and goes out again has made one reveal, not two. Only a new gesture
                    // re-arms it, and only from a row that is actually shut.
                }
            case .ended, .cancelled, .failed:
                scrollView.isScrollEnabled = true
                let velocityX = recognizer.velocity(in: scrollView).x
                let shouldOpen = TaskSwipeRevealDetent.shouldOpen(
                    offsetX: offsetX.wrappedValue,
                    velocityX: velocityX,
                    revealWidth: revealWidth
                )
                if shouldOpen {
                    openRowID.wrappedValue = rowID
                } else if openRowID.wrappedValue == rowID {
                    openRowID.wrappedValue = nil
                }
                // Arm B, the velocity commit: the flick that opens the row from short of
                // the detent, which the detent therefore never got to announce. Same event,
                // at the only moment it can be announced, and `hasFiredRevealDetent` is
                // what keeps the ordinary swipe — which crossed the detent on the way here
                // — to one buzz rather than two.
                //
                // `.cancelled` and `.failed` share this branch, so a gesture the system
                // took away still buzzes when it still opens the row. That is right: what
                // the haptic reports is the row opening, and the row below does open.
                //
                // Nothing fires on the way closed, here or anywhere else. Closing puts back
                // what was already there, every pill uncovered by the reveal fires its own
                // haptic and then closes the row — so a close buzz would double each of
                // them a moment later — and most closes are not something the user did to
                // this row at all, since one row open at a time means the last one is shut
                // from under a finger that is nowhere near it.
                if shouldOpen && !hasFiredRevealDetent {
                    hasFiredRevealDetent = true
                    HapticManager.reveal()
                }
                // not a token — see docs/motion.md. The pair IS `Gesture`, and
                // this is the site `TaskSwipeRevealState.kt` converts to Compose
                // by hand. What keeps it written out is the constructor, not the
                // numbers: `TdayMotion.gesture` is a plain `.spring`, and this
                // settle has to survive a second pan landing on the row before it
                // finishes — `.interactiveSpring` re-aims at the new target
                // instead of fighting the one in flight.
                withAnimation(.interactiveSpring(response: 0.34, dampingFraction: 0.82)) {
                    offsetX.wrappedValue = shouldOpen ? -revealWidth : 0
                }
                dragStartOffsetX = 0
            default:
                break
            }
        }
    }
}

private struct TodoSwipePillActionButton: View {
    let title: String
    /// Asset-catalog name of the lucide template glyph (shared with web/Android).
    let assetName: String
    let tint: Color
    let revealProgress: CGFloat
    let revealDelay: CGFloat
    let action: () -> Void

    private var easedReveal: CGFloat {
        let normalized = max(0, min(1, (revealProgress - revealDelay) / (1 - revealDelay)))
        return normalized * normalized * (3 - (2 * normalized))
    }

    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                ZStack {
                    RoundedRectangle(cornerRadius: 17, style: .continuous)
                        .fill(tint)
                    Image(assetName)
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 21, height: 21)
                        .foregroundStyle(.white)
                }
                .frame(width: 56, height: 34)

                Text(title)
                    .font(.tdayRounded(size: 12, weight: .bold))
                    .foregroundStyle(Color(uiColor: .secondaryLabel).opacity(0.82))
                    .lineLimit(1)
            }
            .frame(minWidth: 60)
        }
        .buttonStyle(
            TdayPressButtonStyle(
                shadowColor: Color.black,
                pressedShadowOpacity: 0,
                normalShadowOpacity: 0
            )
        )
        .opacity(Double(easedReveal))
        .scaleEffect(0.38 + (0.62 * easedReveal))
        .allowsHitTesting(easedReveal > 0.8)
    }
}

private extension UIView {
    func enclosingSwipeScrollView() -> UIScrollView? {
        var view: UIView? = self
        while let current = view {
            if let scrollView = current as? UIScrollView {
                return scrollView
            }
            view = current.superview
        }
        return nil
    }
}
