import UIKit

/// The app's haptic vocabulary — eight names for eight *events*, not eight waveforms.
///
/// Before this file described events, it described places. Eleven functions were named
/// after where they were called from — `buttonTap`, `gentleTap`, `sheetConfirm`,
/// `sheetDismiss` — while eleven other call sites skipped the file entirely and built a
/// `UIImpactFeedbackGenerator(style: .light)` inline. Four of the eleven functions
/// (`dragStart`, `dragReorder`, `dragDrop`, `pullThresholdCrossed`) had no call site at
/// all: a drag fired its haptic from a raw generator two files away while the function
/// written for that exact moment sat here reading as coverage and providing none.
///
/// So call sites name the **event**, never the style. `destructive()` is a decision that
/// survives; `UIImpactFeedbackGenerator(style: .light).impactOccurred()` is a style
/// somebody once picked. When the style for an event turns out wrong it is wrong in one
/// place here, instead of in the twelve screens that copied it.
///
/// ### The names
///
/// `buttonPress` · `selection` · `toggle` · `completion` · `destructive` · `dragPickUp` ·
/// `dragDrop` · `reveal`
///
/// They are Android's names (`core/ui/TdayHaptics.kt`), for the same events, in the same
/// order, so a reviewer comparing the two clients compares events rather than translating
/// `UIImpactFeedbackGenerator` styles into `HapticFeedbackConstantsCompat` ints in their
/// head. Android names a platform constant per event and iOS has none of those constants,
/// so each generator below was picked to read as the same *event*, and each doc comment
/// names the Android constant it stands in for.
///
/// ### Why every name here has a call site
///
/// Two further names have an agreed recipe and are deliberately **not** declared — the
/// same two Android leaves undeclared, for the same reason: nothing calls them yet.
///
/// - `rejection` → `UINotificationFeedbackGenerator.notificationOccurred(.error)` — an
///   action refused: an invalid drop, a copy that failed. (Android: `REJECT`.)
/// - `boundary` → `UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.4)`
///   — a detent crossed *during* a continuous gesture, fired repeatedly inside one drag,
///   so it has to be the lightest repeatable tick. (Android: `SEGMENT_FREQUENT_TICK`.)
///
/// The two deleted functions nothing called, `dragReorder` and `pullThresholdCrossed`,
/// were both that second event, and neither gesture fires a haptic today: there is no
/// reorder-within-a-list drag on iOS at all, and pull-to-refresh crosses its trigger
/// distance silently (`UI/Component/PullToRefresh.swift`, `pullProgress` reaching 1).
/// Declaring `boundary` now to hold their place would put back exactly what this file
/// just removed. Add it in the same commit as the call site that needs it.
enum HapticManager {
    private static let selectionGenerator = UISelectionFeedbackGenerator()

    /// A plain control was tapped: a bar button, the FAB, a card, an action tile, a sheet
    /// row, a swipe action, a sheet's confirm or close button.
    ///
    /// `.light` at 0.6 intensity — the lightest acknowledgement worth firing. Tapping a
    /// control is not an event, it is the cost of using the app, and it has to stay under
    /// the ones that are. (Android: `CLOCK_TICK`.)
    static func buttonPress() {
        let generator = UIImpactFeedbackGenerator(style: .light)
        generator.prepare()
        generator.impactOccurred(intensity: 0.6)
    }

    /// The highlight moved: a root-feed tab changed, an option was picked out of a
    /// selector, a row entered or left a multi-select.
    ///
    /// `UISelectionFeedbackGenerator` — UIKit's own "the selection advanced one notch",
    /// which is the detent feel a picker is supposed to have and the exact counterpart of
    /// Android's `SEGMENT_TICK`. Distinct from ``buttonPress()`` so a tab that *changed*
    /// does not feel like a tab that was already selected.
    static func selection() {
        selectionGenerator.prepare()
        selectionGenerator.selectionChanged()
    }

    /// A two-state control flipped, and `on` says which way: restoring a task that was
    /// already complete. Not the settings switches — those are SwiftUI `Toggle`s, which
    /// bring UIKit's own switch feedback, so routing them through here would double it.
    ///
    /// `.rigid` against `.soft` — iOS ships no directional pair, so this is the nearest
    /// thing to one: two textures at the same energy, one crisp and one dull. Turning
    /// something on must not feel identical to turning it off, or the haptic carries no
    /// information the screen did not already carry. (Android: `TOGGLE_ON` / `TOGGLE_OFF`,
    /// the only pair whose two directions differ in the hand.)
    static func toggle(on: Bool) {
        let style: UIImpactFeedbackGenerator.FeedbackStyle = on ? .rigid : .soft
        let generator = UIImpactFeedbackGenerator(style: style)
        generator.prepare()
        generator.impactOccurred()
    }

    /// The thing the user set out to do landed: a task ticked off, the day's last task
    /// cleared, a create sheet saved, a bulk move applied.
    ///
    /// `.success` — the platform's "that worked" pulse, rounder and heavier than a tick.
    /// This is the one haptic in the app allowed to feel like a reward. (Android:
    /// `CONFIRM`.)
    static func completion() {
        let generator = UINotificationFeedbackGenerator()
        generator.prepare()
        generator.notificationOccurred(.success)
    }

    /// Something is being destroyed: a task deleted, a list deleted, a bulk delete
    /// confirmed.
    ///
    /// `.heavy` — the heaviest single thud in the set, and an impact rather than `.error`
    /// on purpose: the deletion worked, it is not a failure. Deleting has to cost more in
    /// the hand than the Edit pill sitting 16 pt away from it, so a mis-tap on the wrong
    /// swipe action is felt before it is read. (Android: `LONG_PRESS`.)
    static func destructive() {
        let generator = UIImpactFeedbackGenerator(style: .heavy)
        generator.prepare()
        generator.impactOccurred()
    }

    /// A row left the list and is now under the finger — the long press that starts a drag.
    ///
    /// `.medium` — heavier than the tap a button gives, so it reads as something coming
    /// loose rather than as a button firing. (Android: `DRAG_START`, named for this exact
    /// moment.)
    static func dragPickUp() {
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.prepare()
        generator.impactOccurred()
    }

    /// A drag committed: a task dropped onto a new day, or into a new Morning / Afternoon
    /// / Tonight bucket.
    ///
    /// `.success` — the same pulse as ``completion()``, deliberately. A drop that stuck
    /// and a task that finished are the same class of event to the user, and the app
    /// should not invent a distinction its own screens do not make. (Android: `CONFIRM`,
    /// for the same reason.)
    static func dragDrop() {
        let generator = UINotificationFeedbackGenerator()
        generator.prepare()
        generator.notificationOccurred(.success)
    }

    /// A hidden surface came out: a row's swipe actions revealed by a tap on the row.
    ///
    /// `.rigid` at 0.7 intensity — sharp where ``buttonPress()`` is soft. That is the pair
    /// that matters here: uncovering a row's actions and then tapping one of them are two
    /// taps a second apart on the same row, and they must not be the same buzz. (Android:
    /// `CONTEXT_CLICK`, the constant for "a context surface appeared".)
    static func reveal() {
        let generator = UIImpactFeedbackGenerator(style: .rigid)
        generator.prepare()
        generator.impactOccurred(intensity: 0.7)
    }
}
