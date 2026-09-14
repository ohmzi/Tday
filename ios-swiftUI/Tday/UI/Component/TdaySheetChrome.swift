import SwiftUI
import UIKit

enum TdaySheetMetrics {
    static let maximumScreenHeightFraction: CGFloat = 0.80
    static let horizontalPadding: CGFloat = 18
    static let verticalPadding: CGFloat = 14
    static let sectionSpacing: CGFloat = 14
    static let actionSize: CGFloat = 56
    static let actionIconSize: CGFloat = 22
    static let cardCornerRadius: CGFloat = 28
    static let overlayCornerRadius: CGFloat = 30
    static let selectorCornerRadius: CGFloat = 32
    static let sheetCornerRadius: CGFloat = 34
    static let closeAccent = Color(red: 227.0 / 255.0, green: 90.0 / 255.0, blue: 90.0 / 255.0)
    static let confirmAccent = Color(red: 47.0 / 255.0, green: 163.0 / 255.0, blue: 91.0 / 255.0)
}

struct TdaySheetHeader: View {
    let title: String
    var closeSystemName = "xmark"
    var closeAccessibilityLabel = "Close"
    var confirmSystemName: String? = "checkmark"
    var confirmAccessibilityLabel = "Done"
    var isConfirmEnabled = true
    let onClose: () -> Void
    var onConfirm: () -> Void = {}

    @Environment(\.tdayColors) private var colors

    var body: some View {
        HStack {
            TdaySheetActionButton(
                systemName: closeSystemName,
                accessibilityLabel: closeAccessibilityLabel,
                accentColor: TdaySheetMetrics.closeAccent,
                isEnabled: true,
                action: onClose
            )

            Spacer(minLength: 0)

            Text(title)
                .font(.tdayRounded(size: 24, weight: .heavy))
                .foregroundStyle(colors.onSurface)
                .lineLimit(1)
                .minimumScaleFactor(0.78)

            Spacer(minLength: 0)

            if let confirmSystemName {
                TdaySheetActionButton(
                    systemName: confirmSystemName,
                    accessibilityLabel: confirmAccessibilityLabel,
                    accentColor: TdaySheetMetrics.confirmAccent,
                    isEnabled: isConfirmEnabled,
                    action: onConfirm
                )
            } else {
                Color.clear
                    .frame(width: TdaySheetMetrics.actionSize, height: TdaySheetMetrics.actionSize)
            }
        }
        .padding(.horizontal, TdaySheetMetrics.horizontalPadding)
        .padding(.top, TdaySheetMetrics.verticalPadding)
        .padding(.bottom, TdaySheetMetrics.verticalPadding)
        .background(colors.bottomSheetBackground)
    }
}

struct TdaySheetActionButton: View {
    let systemName: String
    let accessibilityLabel: String
    let accentColor: Color
    let isEnabled: Bool
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button {
            // Both heads of the bar are a control being tapped, confirm included:
            // the *landing* is what earns `completion()`, and all three sheets
            // with a confirm that does something fire it there —
            // `CreateTaskSheet.submit`, `CreateListSheet.onConfirm`,
            // `ListSettingsSheet.submit`. Branching on the accent here fired a
            // success pulse on the press and a second one a moment later when
            // the save returned.
            HapticManager.buttonPress()
            action()
        } label: {
            Image(systemName: systemName)
                .font(.system(size: TdaySheetMetrics.actionIconSize, weight: .semibold))
                .foregroundStyle(colors.onSurface.opacity(isEnabled ? 1 : 0.55))
                .frame(width: TdaySheetMetrics.actionSize, height: TdaySheetMetrics.actionSize)
                .background(colors.bottomSheetControlSurface, in: Circle())
                .overlay {
                    Circle()
                        .stroke(accentColor.opacity(isEnabled ? 0.55 : 0.3), lineWidth: 1.5)
                }
                .contentShape(Circle())
        }
        .buttonStyle(
            TdayPressButtonStyle(
                shadowColor: Color.black,
                pressedShadowOpacity: 0.04,
                normalShadowOpacity: isEnabled ? 0.16 : 0.06
            )
        )
        .disabled(!isEnabled)
        .accessibilityLabel(accessibilityLabel)
    }
}

struct TdaySheetSectionTitle: View {
    let text: String

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Text(text)
            .font(.tdayRounded(size: 22, weight: .bold))
            .foregroundStyle(colors.onSurfaceVariant)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 4)
    }
}

struct TdaySheetCard<Content: View>: View {
    @ViewBuilder let content: Content

    @Environment(\.tdayColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            content
        }
            .frame(maxWidth: .infinity)
            .background(
                RoundedRectangle(cornerRadius: TdaySheetMetrics.cardCornerRadius, style: .continuous)
                    .fill(colors.bottomSheetSurface)
            )
            .clipShape(RoundedRectangle(cornerRadius: TdaySheetMetrics.cardCornerRadius, style: .continuous))
    }
}

struct TdaySheetOverlayCard<Content: View>: View {
    @ViewBuilder let content: Content

    @Environment(\.tdayColors) private var colors

    var body: some View {
        content
            .background(
                colors.bottomSheetSurface,
                in: RoundedRectangle(cornerRadius: TdaySheetMetrics.overlayCornerRadius, style: .continuous)
            )
            .overlay {
                RoundedRectangle(cornerRadius: TdaySheetMetrics.overlayCornerRadius, style: .continuous)
                    .stroke(colors.cardStroke, lineWidth: 1)
            }
            .shadow(color: Color.black.opacity(colors.isDark ? 0.34 : 0.14), radius: 24, x: 0, y: 12)
    }
}

struct TdayCenteredSelectorCard<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content

    @Environment(\.tdayColors) private var colors

    var body: some View {
        TdaySheetOverlayCard {
            VStack(alignment: .leading, spacing: 0) {
                Text(title)
                    .font(.tdayRounded(size: 18, weight: .heavy))
                    .foregroundStyle(colors.onSurfaceVariant)
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
                    .padding(.bottom, 12)

                content
            }
            .padding(.bottom, 14)
            .frame(maxWidth: 330)
        }
    }
}

extension View {
    /// The app's own bottom-sheet presentation: a `fullScreenCover` put up and torn
    /// down with animations suppressed, so every visible frame of the entrance and
    /// the exit belongs to `TdayBottomSheetPresentationHost` above.
    ///
    /// iOS runs **two** sheet mechanisms and they are not to be merged. This one is
    /// applied at nine sites across four screens — seven of them through
    /// `createTaskSheet(…)`, the thin pair of wrappers `CreateTaskSheet.swift` puts
    /// over it, and two of them directly, for the create-list sheet — and between
    /// them they present exactly two sheets, `CreateTaskSheet` and `CreateListSheet`.
    /// UIKit's `.sheet` + `presentationDetents` has six: Morning Sweep's date picker,
    /// the two summary sheets, promote-floater, list settings and members.
    ///
    /// What decides which is **whose the height is**, not whether there is a keyboard
    /// — list settings and members have text fields too. A sheet on this modifier is
    /// sized by its own content, so nothing above it will move it off a keyboard;
    /// the host opts out of SwiftUI's avoidance (`.ignoresSafeArea(.keyboard)`) and
    /// computes `keyboardBottomInset` itself, which is the only way the lift can be
    /// clamped to `TdaySheetMetrics.maximumScreenHeightFraction` instead of pushing a
    /// tall card off the top of the screen. Owning the presentation is also what buys
    /// the two things `animateOut()` does: resign first responder on the one funnel
    /// every dismissal reaches, and hold the cover up for `exitDuration` after the
    /// card has gone, so a confirm can leave on the same slide as the X.
    ///
    /// A sheet on the native mechanism has a height that is a *choice* —
    /// `.medium`/`.large`, or one measured detent — and UIKit's detents come with the
    /// system's drag-to-dismiss and its own keyboard handling. Migrating those six
    /// would throw both away to gain an inset none of them needs, and no machine on
    /// this branch can compile the result.
    ///
    /// So the unification is of the **chrome**, and that part is mostly already done:
    /// every sheet on either mechanism wears `TdaySheetHeader` over
    /// `colors.bottomSheetBackground`, and the corner radius agrees at 34 everywhere
    /// it is stated — `TdaySheetMetrics.sheetCornerRadius` on this side,
    /// `presentationCornerRadius(34)` on the native one, which takes a value and not
    /// a token.
    ///
    /// Two pieces are not shared, and both are UIKit's rather than anybody's choice.
    /// The **scrim**: `colors.bottomSheetScrim` is drawn by this host and by the
    /// hand-rolled overlays that follow it, while a presented `.sheet` is dimmed by
    /// UIKit, which SwiftUI gives no way to recolour. And the **radius** on the three
    /// native sheets that state none — Morning Sweep's date picker, promote-floater,
    /// and the scheduled-home summary — which take UIKit's default instead of 34.
    /// The radius is worth closing where it is one line; the scrim is not closable
    /// without owning the presentation, which is the trade this comment refuses.
    func tdayBottomSheetPresentation<SheetContent: View>(
        isPresented: Binding<Bool>,
        @ViewBuilder content: @escaping () -> SheetContent
    ) -> some View {
        modifier(TdayBottomSheetPresentationModifier(isPresented: isPresented, sheetContent: content))
    }

    func tdayBottomSheetPresentation<Item: Identifiable, SheetContent: View>(
        item: Binding<Item?>,
        @ViewBuilder content: @escaping (Item) -> SheetContent
    ) -> some View {
        modifier(TdayBottomSheetItemPresentationModifier(item: item, sheetContent: content))
    }
}

/// Timing for the sheet's entrance and exit, kept in one place so the card
/// animation and the deferred teardown can't drift apart.
private enum TdayBottomSheetMotion {
    /// Between Enter (0.20) and Change (0.26) and on neither rung. Left alone: the
    /// deferred teardown is timed against this, and 20 ms either way is the sheet
    /// unmounting before or after its own card has finished leaving.
    static let exitDuration: TimeInterval = 0.24

    /// 0.22 is not a rung either, and the argument for it is written out under
    /// `TdayCenteredSelectorMotion` below: it is matched to the keyboard's own
    /// ~0.25 s dismissal, not to this vocabulary.
    static let scrimIn = Animation.easeOut(duration: 0.22)
    /// Numerically the Enter rung, but this is a scrim leaving on SwiftUI's own
    /// `.easeIn`. Binding an exit to a token whose docstring reads "one element
    /// arriving" would name it wrong for no pixel gained, so it stays a literal.
    static let scrimOut = Animation.easeIn(duration: 0.2)
    /// The card's two specs, and neither has a Reduce Motion twin here — which is the
    /// decision rather than an omission. A card that has stopped travelling is doing
    /// exactly what the scrim beside it is doing, so the substitute is `scrimIn` and
    /// `scrimOut` above; a third pair of numbers minted for it would be the only thing
    /// left making the two read as two surfaces instead of one arriving.
    static let cardIn = TdayMotion.settle
    static let cardOut = Animation.easeIn(duration: exitDuration)
}

/// The one presentation spec for the centred selector overlay — the picker the
/// create/edit sheet layers over its own card for List, Priority, Repeat, Due
/// date and Due time.
///
/// Derived from `TdayBottomSheetMotion` above rather than chosen again, because
/// the two are on screen together: the selector opens over a sheet that is
/// often still settling, and a second set of numbers would read as two
/// different surfaces arguing over one square of glass.
///
/// It takes the *scrim* curves and not the card spring, and that is the whole
/// decision. `cardIn` is a spring tuned for a card travelling a full screen
/// height; this overlay travels nowhere — it arrives where it already is,
/// fading and scaling by 3 %, which is the motion `scrimIn`/`scrimOut`
/// describe. The 0.22 s ease-out also lands within 30 ms of the keyboard's own
/// ~0.25 s dismissal, and opening a selector dismisses the keyboard: the sheet
/// card slides back down by the keyboard's height at the same moment the picker
/// arrives, so sharing that curve is what makes the two read as one move rather
/// than a pop over a slide.
/// **Reduce Motion leaves this one alone, and that is a decision.** Every other
/// large surface on iOS gives its travel up under the setting — the sheet card
/// above, the root dock, the snackbar, the calendar's page turn. This spec is the
/// one that does not, because it has no travel to give up: the overlay arrives
/// where it already is, and the 3 % is a crossfade's own shape rather than a
/// movement across the screen. Dropping it would remove three hundredths of a
/// scale and leave the fade exactly as it is — no amplitude gained, and a picker
/// that reads as pasted on instead of resolved into place. The guidance this
/// phase follows is about the size of what moves, not about whether anything at
/// all is allowed to; gating a 0.97 would be obeying the letter of a rule that
/// was written for a card crossing a screen. `CompletedScreen`'s restoring row
/// (`.scale(scale: 0.985)`) and the three confirmation overlays' `0.96` are the
/// same call and left alone for the same reason.
enum TdayCenteredSelectorMotion {
    /// The same shape the three Settings selectors carry, so the two families
    /// of centred picker enter and leave identically.
    static let transition: AnyTransition = .opacity.combined(with: .scale(scale: 0.97))

    static let presentation: Animation = TdayBottomSheetMotion.scrimIn
    static let dismissal: Animation = TdayBottomSheetMotion.scrimOut

    /// The curve for a state change that opens (`presenting: true`) or closes
    /// the overlay.
    ///
    /// Callers wrap the mutation in `withAnimation(_:)` with this rather than
    /// hanging an `.animation(_:value:)` off the body, because the entrance and
    /// the exit want different curves and one `.animation(_:value:)` applies
    /// one curve in both directions.
    static func animation(presenting: Bool) -> Animation {
        presenting ? presentation : dismissal
    }
}

/// Applies a state change with SwiftUI animations suppressed, so a
/// `fullScreenCover` driven by that state appears/disappears with no
/// container transition of its own.
private func withoutPresentationAnimation(_ body: () -> Void) {
    var transaction = Transaction()
    transaction.disablesAnimations = true
    withTransaction(transaction, body)
}

/// Re-entrancy guard for the deferred dismissal both presentation modifiers
/// use. A dismissal can be requested from several directions at once (the
/// caller clearing its binding *and* SwiftUI writing `false` through the
/// cover's binding); only the first may start the exit animation.
private struct TdayBottomSheetDismissal {
    /// Incremented to ask the presented host to animate itself out.
    private(set) var requestID = 0
    private var isDismissing = false

    mutating func begin() {
        guard !isDismissing else {
            return
        }
        isDismissing = true
        requestID += 1
    }

    mutating func reset() {
        isDismissing = false
    }
}

/// Drives the bottom sheet's own entrance/exit instead of letting
/// `fullScreenCover` animate its container.
///
/// The container animation is the problem being avoided: it slides the *whole*
/// presented view, and the dim scrim lives inside that view, so the dim
/// arrives and leaves as a hard-edged band travelling up and down the screen.
/// Presenting and tearing down with animations suppressed means there is
/// nothing to slide — the cover simply exists, and
/// `TdayBottomSheetPresentationHost` fades the scrim in place while only the
/// card travels, which is how a native `.sheet()` reads.
///
/// Two details matter:
/// - Presentation is triggered from `onChange` of the caller's binding, not
///   from a wrapper `Binding`'s setter. Call sites flip their own `@State`
///   directly (`showingCreateList = true`), which never routes through a
///   wrapper's `set`, so a wrapper-based hook would silently never run.
/// - Teardown is deferred. Every dismissal request — including
///   `@Environment(\.dismiss)` from inside sheet content, which writes `false`
///   through the cover's binding — is intercepted so the host can animate out
///   first; the (by then invisible) cover is removed afterwards.
private struct TdayBottomSheetPresentationModifier<SheetContent: View>: ViewModifier {
    @Binding var isPresented: Bool
    @ViewBuilder let sheetContent: () -> SheetContent

    @State private var isCoverPresented = false
    @State private var dismissal = TdayBottomSheetDismissal()

    private var coverBinding: Binding<Bool> {
        Binding(
            get: { isCoverPresented },
            set: { newValue in
                if newValue {
                    isCoverPresented = true
                } else {
                    dismissal.begin()
                }
            }
        )
    }

    func body(content: Content) -> some View {
        content
            .onChange(of: isPresented) { _, newValue in
                if newValue {
                    dismissal.reset()
                    withoutPresentationAnimation { isCoverPresented = true }
                } else if isCoverPresented {
                    dismissal.begin()
                }
            }
            .fullScreenCover(isPresented: coverBinding) {
                TdayBottomSheetPresentationHost(
                    dismissRequestID: dismissal.requestID,
                    onDismissAnimationCompleted: finishDismissal
                ) {
                    sheetContent()
                }
            }
    }

    private func finishDismissal() {
        withoutPresentationAnimation { isCoverPresented = false }
        dismissal.reset()
        isPresented = false
    }
}

/// `item`-driven counterpart of `TdayBottomSheetPresentationModifier`.
private struct TdayBottomSheetItemPresentationModifier<Item: Identifiable, SheetContent: View>: ViewModifier {
    @Binding var item: Item?
    @ViewBuilder let sheetContent: (Item) -> SheetContent

    // Held separately from `item` so the sheet still has content to render
    // while it animates out after the caller has already cleared `item`.
    @State private var presentedItem: Item?
    @State private var dismissal = TdayBottomSheetDismissal()

    private var coverBinding: Binding<Item?> {
        Binding(
            get: { presentedItem },
            set: { newValue in
                if let newValue {
                    presentedItem = newValue
                } else {
                    dismissal.begin()
                }
            }
        )
    }

    func body(content: Content) -> some View {
        content
            .onChange(of: item?.id) { _, _ in
                if let item {
                    dismissal.reset()
                    withoutPresentationAnimation { presentedItem = item }
                } else if presentedItem != nil {
                    dismissal.begin()
                }
            }
            .fullScreenCover(item: coverBinding) { presented in
                TdayBottomSheetPresentationHost(
                    dismissRequestID: dismissal.requestID,
                    onDismissAnimationCompleted: finishDismissal
                ) {
                    sheetContent(presented)
                }
            }
    }

    private func finishDismissal() {
        withoutPresentationAnimation { presentedItem = nil }
        dismissal.reset()
        item = nil
    }
}

private struct TdayBottomSheetPresentationHost<SheetContent: View>: View {
    /// Bumped by the presenting modifier to ask for an animated exit. The
    /// cover is only torn down once `onDismissAnimationCompleted` fires.
    let dismissRequestID: Int
    let onDismissAnimationCompleted: () -> Void
    let content: SheetContent

    @Environment(\.tdayColors) private var colors
    @Environment(\.dismiss) private var dismiss
    /// Read here and not only at the app root because a `fullScreenCover` is a
    /// presentation of its own; it inherits the environment of the view that
    /// presented it, which is how the root's answer reaches this, and the accessor's
    /// fallback covers a sheet presented from anywhere that answer does not.
    @Environment(\.tdayAnimation) private var tdayAnimation
    @State private var keyboardFrame: CGRect?
    @State private var contentHeight: CGFloat = 0
    // The cover is presented and torn down with animations suppressed (see
    // TdayBottomSheetPresentationModifier), so these two flags supply the
    // entire visible entrance and exit: the scrim fades where it already is,
    // and only the card travels — or, under Reduce Motion, fades beside it.
    @State private var isScrimVisible = false
    @State private var isCardRaised = false
    /// How far the finger has carried the card down, clamped at the gesture so
    /// nothing downstream has to remember that this one only goes one way. An
    /// upward pull on a card already sitting at its own content height has
    /// nowhere to go: there is no taller state to drag it into.
    @State private var dragTranslation: CGFloat = 0
    /// Whether the content has raised a layer of its own over the card, reported
    /// by the content itself — see `TdaySheetContentIsCoveredPreferenceKey`.
    @State private var isContentCovered = false

    init(
        dismissRequestID: Int,
        onDismissAnimationCompleted: @escaping () -> Void,
        @ViewBuilder content: () -> SheetContent
    ) {
        self.dismissRequestID = dismissRequestID
        self.onDismissAnimationCompleted = onDismissAnimationCompleted
        self.content = content()
    }

    var body: some View {
        GeometryReader { proxy in
            let keyboardBottomInset = keyboardBottomInset(for: proxy, contentHeight: contentHeight)

            ZStack(alignment: .bottom) {
                // Dimmed backdrop so the sheet reads as a modal layer above the
                // screen behind it. Tapping it dismisses, matching the platform's
                // standard bottom-sheet behavior.
                colors.bottomSheetScrim
                    .opacity(isScrimVisible ? 1 : 0)
                    .contentShape(Rectangle())
                    .ignoresSafeArea()
                    .onTapGesture { dismissSheet() }

                content
                    .frame(maxWidth: .infinity, alignment: .bottom)
                    .background {
                        GeometryReader { contentProxy in
                            Color.clear.preference(
                                key: TdayBottomSheetContentHeightPreferenceKey.self,
                                value: contentProxy.size.height
                            )
                        }
                    }
                    // The grabber the card has never had, drawn by the chrome so
                    // that both sheets on this mechanism get it across all nine
                    // application sites at once, rather than each caller
                    // remembering to draw its own. An overlay and
                    // not a row above the content, because the card's background
                    // and its clip shape belong to the content
                    // (`CreateTaskSheet.body`): a row inserted here would
                    // sit in the transparent strip above the card rather than on
                    // it. Applied before the offset and the opacity below so it
                    // travels and crossfades as part of the card and not as a mark
                    // hanging over it.
                    //
                    // Which also means it lands above anything the content draws
                    // over its *own* card, and `CreateTaskSheet`'s centred selector
                    // is exactly that: a full scrim and a picker, inside the view
                    // handed to this host. So the bar fades out for as long as the
                    // content says it is covered, on the curve a scrim arrives on,
                    // rather than floating lit above the dim.
                    .overlay(alignment: .top) {
                        TdaySheetGrabber().opacity(isContentCovered ? 0 : 1)
                    }
                    // Parked a full screen height down until raised, so the card
                    // starts (and ends) fully offscreen without needing a
                    // measured height on the very first render.
                    //
                    // Under Reduce Motion it is never parked. This is the longest
                    // travel in the app — a whole screen — and the case Apple's
                    // guidance describes most exactly, so the card is placed where
                    // it will stay and crossfades in alongside the scrim instead.
                    // Refusing the motion outright was the other candidate and is
                    // worse here than anywhere else: a full-bleed modal that
                    // replaces the screen between two frames gives the eye nothing
                    // to follow to it, and the sheet is the one surface in this app
                    // that arrives over content the user was reading. The finished
                    // state is still what gets drawn — the fifth idiom rule asks
                    // for the destination, not for the absence of a fade.
                    //
                    // `dragTranslation` is added and not gated on Reduce Motion:
                    // a card under a finger is direct manipulation rather than an
                    // animation, and refusing to follow the finger would be the app
                    // declining to admit the gesture happened.
                    .offset(
                        y: (isCardRaised || !tdayAnimation.isEnabled ? 0 : proxy.size.height)
                            - keyboardBottomInset
                            + dragTranslation
                    )
                    // The crossfade's own half, and inert while motion is full: the
                    // card is opaque through every frame of its travel, so this is
                    // 1 whenever the branch above is the one doing the work.
                    .opacity(isCardRaised || tdayAnimation.isEnabled ? 1 : 0)
                    // `.subviews` while the content is covered, and not `.none`:
                    // the layer doing the covering is made of the content's own
                    // subviews, and it still has rows to tap and a scrim of its own
                    // to tap through. What has to stop is this card's drag, because
                    // a pull downward on that scrim threw the whole half-written
                    // task away where a tap on the same pixels only closes the
                    // picker — and a tap gesture does not claim a 10 pt pan, so
                    // nothing under there was ever going to win the argument.
                    .gesture(cardDragGesture, including: isContentCovered ? .subviews : .all)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        .ignoresSafeArea(.container, edges: .bottom)
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .presentationBackground(.clear)
        .onAppear {
            withAnimation(TdayBottomSheetMotion.scrimIn) {
                isScrimVisible = true
            }
            // The scrim's own curve is what the card falls back to, not a third
            // number: under Reduce Motion the two are the same gesture — one
            // surface fading up over another — and giving them separate lengths
            // would be the only thing making them read as two.
            withAnimation(tdayAnimation(TdayBottomSheetMotion.cardIn, reduced: TdayBottomSheetMotion.scrimIn)) {
                isCardRaised = true
            }
        }
        .onChange(of: dismissRequestID) { _, _ in
            animateOut()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillChangeFrameNotification)) { notification in
            updateKeyboardFrame(from: notification)
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { notification in
            updateKeyboardFrame(from: notification)
        }
        .onPreferenceChange(TdayBottomSheetContentHeightPreferenceKey.self) { height in
            contentHeight = height
        }
        // The scrim curves and not the selector's own aliases of them, because what
        // this matches is a dimming layer arriving over the bar, which is the move
        // `scrimIn`/`scrimOut` describe; the chrome should not have to know which
        // picker is on top of it. Animated at all because the alternative is the
        // grabber blinking out a frame before the thing covering it starts to fade in.
        //
        // Not routed through `tdayAnimation`, which is the call
        // `TdayCenteredSelectorMotion` already makes for itself: the layer doing the
        // covering keeps its crossfade under Reduce Motion because it has no travel to
        // give up, and handing the setting to one half of one crossfade would be the
        // only thing left making the bar and the dim read as two surfaces.
        .onPreferenceChange(TdaySheetContentIsCoveredPreferenceKey.self) { isCovered in
            withAnimation(isCovered ? TdayBottomSheetMotion.scrimIn : TdayBottomSheetMotion.scrimOut) {
                isContentCovered = isCovered
            }
        }
    }

    /// Pulling the card down, which is the same dismissal the scrim tap asks for.
    ///
    /// Attached to the whole card and not to the grabber, so the affordance is a
    /// label for the gesture rather than the only place it works. `.gesture` and
    /// deliberately not `.highPriorityGesture`: a gesture declared on an ancestor
    /// yields to one declared inside it, which is what leaves `CreateListSheet`'s
    /// `ScrollView` scrolling under a card that still drags by its header and its
    /// margins — the same division of a sheet a UIKit `.sheet` makes.
    private var cardDragGesture: some Gesture {
        // Global space is not optional here. Measured locally, the card's own
        // offset feeds back into the translation and the drag oscillates —
        // `AppRootView.swift:964-968` carries that comment over the same
        // construction for the toast, and this is the second site rather than a
        // second discovery.
        DragGesture(minimumDistance: TdaySheetDragToDismiss.minimumDistance, coordinateSpace: .global)
            .onChanged { value in
                // Every delivery is already past `minimumDistance`, so the first
                // one is the drag beginning — and the moment the keyboard has to
                // go, not the release. `keyboardBottomInset` lifts the card while
                // a field is focused, so resigning mid-flight would collapse that
                // lift underneath a card the finger is already moving: PR 15b's
                // Android failure and PR 44's `ios-selector-pops-while-card-slides`
                // are both that same shape. Resigning here spends the collapse in
                // the direction the finger is already going (the inset is
                // subtracted, so losing it drops the card) and gets it over with
                // while the drag is young.
                //
                // Guarded on the frame rather than on a flag of its own: the first
                // resign makes `keyboardWillHide` fire, which nils `keyboardFrame`,
                // so the check stops answering after one call without anything
                // having to remember that it already ran.
                if keyboardFrame != nil {
                    resignFirstResponder()
                }
                dragTranslation = max(0, value.translation.height)
            }
            .onEnded { value in
                guard TdaySheetDragToDismiss.shouldDismiss(
                    translation: value.translation.height,
                    predictedEndTranslation: value.predictedEndTranslation.height,
                    sheetHeight: contentHeight
                ) else {
                    // Gesture, which `docs/motion.md` defines as a surface
                    // continuing under its own momentum after a finger lets go —
                    // and the one rung on this card whose damping was chosen for a
                    // release rather than for an arrival. Under Reduce Motion the
                    // resolver answers nil, which puts the card home in the frame
                    // the finger left rather than holding it out there: the trip
                    // is the thing being refused, and the eye has just watched the
                    // finger draw the way back.
                    withAnimation(tdayAnimation(TdayMotion.gesture)) {
                        dragTranslation = 0
                    }
                    return
                }
                // Into the same funnel the scrim tap uses, rather than animating
                // out here: `animateOut()` is what resigns the keyboard and times
                // the teardown, and a second exit written at the gesture is a
                // second thing to keep in step with it. The card is left where the
                // finger put it — `dragTranslation` is not reset — so the exit
                // carries on from there instead of snapping home first.
                //
                // No haptic, unlike `dismissSheet()`. `buttonPress` is the answer
                // to a control being pressed; a finger that has just carried the
                // card down has been answered by the card the whole way, and a
                // buzz on release would read as a button it never touched.
                dismiss()
            }
    }

    private func dismissSheet() {
        HapticManager.buttonPress()
        // Routed through `dismiss()` rather than animating here, so a scrim tap
        // takes exactly the same path as a `dismiss()` from inside the sheet:
        // the presenting modifier intercepts it and drives `animateOut()`.
        dismiss()
    }

    /// Fades the scrim out where it stands and slides only the card away, then
    /// lets the presenting modifier remove the (by then invisible) cover.
    private func animateOut() {
        // Drop the keyboard here, not at the gesture that asked for the
        // dismissal, because this is the one funnel every dismissal reaches:
        // the scrim tap, the header's X, a confirm that ends in `dismiss()`
        // and the caller clearing its own binding all arrive as a bumped
        // `dismissRequestID`. It used to live in `dismissSheet()`, which is
        // the scrim tap alone — so every other way out of a sheet with a
        // focused field slid the card down from behind a keyboard that was
        // still standing, and left it standing after the sheet had gone.
        //
        // The drag resigns earlier as well, at the gesture rather than at the
        // dismissal, and that is not a duplicate: a drag can be refused, so it
        // has to put the keyboard down before it knows whether this call will
        // ever run.
        resignFirstResponder()
        withAnimation(TdayBottomSheetMotion.scrimOut) {
            isScrimVisible = false
        }
        withAnimation(tdayAnimation(TdayBottomSheetMotion.cardOut, reduced: TdayBottomSheetMotion.scrimOut)) {
            isCardRaised = false
        }
        // The wait below stays as it is, and it is worth saying why rather than
        // leaving it to be rediscovered: under Reduce Motion the card is still
        // animating, it is just fading instead of travelling, and `scrimOut`'s
        // 0.20 finishes inside `exitDuration`'s 0.24. This is the deferred
        // teardown covering a real animation, not the wait-without-a-trip the
        // fifth idiom rule forbids — which is exactly what it WOULD have become
        // had the card been handed `nil` above.
        DispatchQueue.main.asyncAfter(deadline: .now() + TdayBottomSheetMotion.exitDuration) {
            onDismissAnimationCompleted()
        }
    }

    /// Drops the keyboard wherever it is. Sent to `nil` rather than to a field
    /// this host holds, because the field belongs to the sheet's content and the
    /// chrome has never been told which one it is.
    private func resignFirstResponder() {
        UIApplication.shared.sendAction(
            #selector(UIResponder.resignFirstResponder),
            to: nil,
            from: nil,
            for: nil
        )
    }

    private func keyboardBottomInset(for proxy: GeometryProxy, contentHeight: CGFloat) -> CGFloat {
        guard let keyboardFrame else {
            return 0
        }
        let hostFrame = proxy.frame(in: .global)
        let overlap = hostFrame.maxY - keyboardFrame.minY
        let requestedInset = min(max(overlap, 0), hostFrame.height)
        guard contentHeight > 0 else {
            return 0
        }
        let minimumSheetTop = hostFrame.height * (1 - TdaySheetMetrics.maximumScreenHeightFraction)
        let currentSheetTop = hostFrame.height - contentHeight
        let maximumInsetBeforeExceedingSheetLimit = max(currentSheetTop - minimumSheetTop, 0)
        return min(requestedInset, maximumInsetBeforeExceedingSheetLimit)
    }

    private func updateKeyboardFrame(from notification: Notification) {
        guard let endFrame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect else {
            return
        }
        let duration = notification.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double ?? 0.25
        let visible = TdayKeyboardFrameProbe.visibleFrame(
            endFrame: endFrame,
            screenMaxY: TdayKeyboardFrameProbe.activeScreenMaxY()
        )

        withAnimation(.easeOut(duration: duration)) {
            keyboardFrame = visible
        }
    }
}

/// Whether a released downward drag has asked to close the sheet.
///
/// A free enum with no view in it, for the same reason `TdayKeyboardFrameProbe`
/// below is one: this is the half of the gesture a test can hold, and the half
/// that is actually a decision. What the card does while the finger is on it is
/// one addition; what a release means is a rule, and a rule that lives inside a
/// `body` is a rule nothing can ask a question of.
///
/// **A release is a projection, not a position.** That is the argument web's
/// `src/lib/swipeGesture.ts` makes for `projectedRest`, written up in
/// `docs/motion/LEDGER.md:731-736`, and it is the same argument here: a position
/// rule reads the last frame of a gesture as though it were the end of one. A
/// short fast flick is still travelling and commits; a long drag already being
/// walked back is not and does not. SwiftUI hands that projection over ready
/// made — `predictedEndTranslation` is where the finger was going to put the
/// card — so unlike web there is no sampler to build, only a threshold to name.
///
/// The nearest iOS precedent, the toast at `AppRootView.swift:973`, spells
/// `translation > 30 || predictedEndTranslation > 90`. Both halves are wrong for
/// this surface. The `||` is what lets a walked-back drag through, and a flat
/// 30 pt means nothing on a card whose height runs from roughly half the screen
/// to `TdaySheetMetrics.maximumScreenHeightFraction` of it: the same 30 pt is a
/// decisive pull on the short card and a twitch on the tall one. A fraction of
/// the card is the same gesture on both.
enum TdaySheetDragToDismiss {

    /// The slop the drag starts at, and the same 10 pt the toast uses. Below
    /// this a move is a hand steadying on a card it means to type into.
    static let minimumDistance: CGFloat = 10

    /// How much of its own height the card has to be projected past.
    ///
    /// A quarter is where a pull stops being a wobble and has visibly become a
    /// dismissal — far enough that resting a thumb on the card cannot reach it,
    /// near enough that a deliberate pull never has to travel the card's whole
    /// height. It is not the distance a flick has to cover, because a flick is
    /// projected past it rather than dragged there.
    static let commitFraction: CGFloat = 0.25

    static func shouldDismiss(
        translation: CGFloat,
        predictedEndTranslation: CGFloat,
        sheetHeight: CGFloat
    ) -> Bool {
        // No measured height yet, which is the first frame of the first sheet of
        // a session. A fraction of nothing is nothing, and a threshold of zero
        // would dismiss on the slop itself — so the card is not judged as a
        // fraction of a height it has not reported, and the drag springs home.
        // The scrim and the header are both still one tap away.
        guard sheetHeight > 0 else {
            return false
        }
        // The card only ever moved for a downward drag, so a gesture that ended
        // above where it started never moved it and cannot have been asking to
        // close it — whatever it was flicked at on the way back up.
        guard translation > 0 else {
            return false
        }
        return predictedEndTranslation >= sheetHeight * commitFraction
    }
}

/// The horizontal bar that says the card above it can be pulled down.
///
/// 36 × 5 at 5 pt from the top edge is UIKit's own grabber, to the point, and
/// that is the whole argument for those three numbers rather than three of our
/// own. The app presents sheets on two mechanisms (see
/// `tdayBottomSheetPresentation`) and the native one is handed this exact bar
/// for free. Four of its six sheets still answer `presentationDragIndicator`
/// with `.hidden`, which was defensible while no sheet in the app could be
/// dragged and is a question now that this one can — and whichever way that
/// gets settled, the two mechanisms must not disagree by a pixel at the one
/// place a user looks to find out whether a sheet can be pulled down.
///
/// Hidden from accessibility: it is a picture of a gesture, and VoiceOver
/// reaches the same dismissal through the header's Close button, which is
/// labelled.
private struct TdaySheetGrabber: View {
    @Environment(\.tdayColors) private var colors

    var body: some View {
        Capsule(style: .continuous)
            .fill(colors.onSurfaceVariant.opacity(0.35))
            .frame(width: 36, height: 5)
            .padding(.top, 5)
            .accessibilityHidden(true)
    }
}

/// Where the keyboard is, relative to the screen the sheet is actually on.
///
/// Split out of the host because it is two decisions, and neither of them needs
/// a view to be tested. The one it replaces was `UIScreen.main.bounds.maxY`,
/// which is deprecated and, before that, a guess: it answers "the device's
/// built-in screen", which stops being the same question as "the screen this
/// window is on" the moment there is an external display, a CarPlay scene or a
/// second window under Stage Manager. The keyboard's end frame is reported in
/// the coordinates of the screen it actually appeared on, so a sheet anywhere
/// else compared that frame against the wrong bottom edge and read a visible
/// keyboard as hidden — and then sat with the field it was raised for behind
/// the keyboard covering it.
enum TdayKeyboardFrameProbe {
    /// The bottom edge of the screen the app is showing on, or nil when no
    /// window scene is attached — the app is in the background, or this is a
    /// test host with no UI.
    static func activeScreenMaxY() -> CGFloat? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let scene = scenes.first { $0.activationState == .foregroundActive }
            ?? scenes.first { $0.activationState != .unattached }
            ?? scenes.first
        return scene?.screen.bounds.maxY
    }

    /// The keyboard frame the sheet should inset for, or nil when the keyboard
    /// is offscreen — which is how both `keyboardWillHide` and a
    /// `keyboardWillChangeFrame` that parks the keyboard below the screen
    /// report themselves.
    ///
    /// A nil `screenMaxY` means no window scene answered, and therefore that
    /// there is no keyboard to inset for either: the sheet sits at rest rather
    /// than moving for a frame nothing can be measured against.
    static func visibleFrame(endFrame: CGRect, screenMaxY: CGFloat?) -> CGRect? {
        guard let screenMaxY, endFrame.minY < screenMaxY else {
            return nil
        }
        return endFrame
    }
}

private struct TdayBottomSheetContentHeightPreferenceKey: PreferenceKey {
    static var defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

/// Whether the sheet's content has raised a modal layer over its own card.
///
/// The host's chrome — the grabber, and the drag that grabber advertises — is
/// applied to the content it was handed, so z-order settles this without being
/// asked: the overlay lands *above* a scrim drawn inside that content, and the
/// ancestor drag stays live over the scrim's pixels. `CreateTaskSheet` draws
/// exactly such a scrim for List, Priority, Repeat, Due date and Due time.
///
/// A preference and not an environment value because the direction is inward-out:
/// the content is the only thing that knows a picker is open, and the host is the
/// only thing that can stand its chrome down. Reported by the content rather than
/// sniffed for by the host, so a second sheet that ever layers over itself gets
/// the same answer by saying so in one line.
private struct TdaySheetContentIsCoveredPreferenceKey: PreferenceKey {
    static var defaultValue = false

    /// Any branch reporting covered covers the card: there is one card, and two
    /// layers over it is still a card the finger cannot reach.
    static func reduce(value: inout Bool, nextValue: () -> Bool) {
        value = value || nextValue()
    }
}

extension View {
    /// Tell the sheet chrome around this content that it is currently under a
    /// modal layer of its own, so the grabber and the card's drag stand down for
    /// as long as it is. See `TdaySheetContentIsCoveredPreferenceKey`.
    func tdaySheetContentIsCovered(_ isCovered: Bool) -> some View {
        preference(key: TdaySheetContentIsCoveredPreferenceKey.self, value: isCovered)
    }
}

struct TdayCenteredSelectorRow: View {
    let title: String
    let swatchColor: Color
    let selected: Bool
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button {
            HapticManager.selection()
            action()
        } label: {
            HStack(spacing: 14) {
                Circle()
                    .fill(swatchColor)
                    .frame(width: 10, height: 10)

                Text(title)
                    .font(.tdayRounded(size: 18, weight: .heavy))
                    .foregroundStyle(colors.onSurface)
                    .lineLimit(1)

                Spacer(minLength: 12)

                if selected {
                    Image(systemName: "checkmark")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(colors.primary)
                } else {
                    Color.clear
                        .frame(width: 18, height: 18)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct TdaySheetDivider: View {
    var horizontalPadding: CGFloat = 18
    var opacity: Double = 0.18

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Rectangle()
            .fill(colors.onSurfaceVariant.opacity(opacity))
            .frame(height: 1)
            .padding(.horizontal, horizontalPadding)
    }
}
