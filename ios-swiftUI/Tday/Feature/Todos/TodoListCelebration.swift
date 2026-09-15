import Foundation

// `TodoListScreen`'s celebration gate, in a file of its own.
//
// It is here rather than beside its one caller for the reason it is a free
// function at all: it is the decision, and the decision has to be reachable
// from a test. `Package.swift` points the `TdayCore` target at the whole `Tday`
// tree, so an internal function anywhere under it IS visible to
// `TdayCoreTests`, and `ShouldCelebrateEmptyStateTests` is what stands in for
// the device nobody here has. `TodoListScreen.swift` is six thousand lines of
// view builder; a rule this small does not get read in there, and a rule this
// small is the difference between a payoff and paper flying over a row the user
// just got back.
//
// One practical reason not to move it back, recorded because the failure it
// causes points nowhere near the cause. `motion-reachability-ios.test.ts` finds
// a branch by walking back up to twelve lines from any line that ends in an
// opening brace, rejoining a wrapped header as it goes, and it stops at neither
// a closing brace nor a declaration boundary. A file-scope `if` within twelve
// lines above a type declaration therefore makes that TYPE read as a branch,
// and every line inside it comes back "inside a branch" — which fails the
// row-action rules on code those rules are not about. Put this back above
// `struct TodoListScreen` and the suite goes red in `minimalTimelineRow`,
// pointing at a swipe modifier nothing in this change ever touched.

/// Whether the empty state about to be shown is the end of a finished list
/// rather than a list that was never filled. Deleting the last task, or opening
/// an empty list, gets the plain arrival; ticking the last one off gets the
/// confetti — whether that tick happened here, on another device, or from a
/// collaborator on a shared list.
///
/// A free function rather than the private `View` property it was, and the same
/// shape for the same reason as Android's `internal shouldCelebrateEmptyState`:
/// nothing about this decision can be proved by running the app on the machine
/// it is written on, so the ordering below has to be an assertion rather than a
/// claim.
///
/// Three inputs, in the order they are read:
///
/// - `hasNoPendingItems` — nothing left pending in this screen's own scope. Not
///   "no items at all": on Today, Priority, All and List, completing the very
///   last pending task while Earlier still holds overdue rows leaves
///   `viewModel.items` non-empty, and requirement 4 says that still earns the
///   payoff. The caller's own property decides it; this only reads the answer.
/// - `celebrationCancelledAt` — a pending row ARRIVED on this screen. The
///   ending this gate did not have, and the reported bug is the whole of what
///   its absence cost: a celebration was OPENED by a transition and only ever
///   CLOSED by re-reading the static predicate above plus a four-second timer,
///   so nothing observed a task coming back. It is compared against the two
///   opening stamps rather than clearing them, so a completion landing after an
///   arrival re-opens the window simply by being newer — no mutation from an
///   effect, and no ordering question left over. `>=` and not `>`: an undo
///   always follows the completion it undoes, so a stamp that ties with one has
///   to win.
/// - the two opening stamps, windowed. `lastCompletionAt` is this device's own
///   precise signal (only `complete`/`bulkComplete` set it); `remoteEmptiedAt`
///   is the broader one a cache change this device did not stage leaves behind
///   (see its doc comment for why it cannot be as precise), and it additionally
///   requires the screen to be visible and the app foregrounded — a transition
///   nobody was looking at does not get to surface a burst retroactively.
///
/// The window is wider than the burst's own flight, so a redraw mid-flight
/// cannot cut the paper off in mid-air — and since the cancel now can end a
/// burst early, `TdayConfetti` takes its own paint away over `Quick` rather than
/// vanishing on the frame this turns false.
///
/// Known gap, pre-existing and untouched: `remoteEmptiedAt` is armed from a raw
/// non-empty→empty transition on `viewModel.items`, so a remote completion of
/// the last pending task while Earlier still holds overdue rows never flips
/// `items` to empty and so never sets it. A remote DELETE of the last task still
/// false-celebrates for the same root cause — the cache-change signal carries no
/// reason. Cancellation does not fix either and is not meant to; closing them
/// needs the server to say WHY a list changed.
func shouldCelebrateEmptyState(
    hasNoPendingItems: Bool,
    lastCompletionAt: Date?,
    remoteEmptiedAt: Date?,
    celebrationCancelledAt: Date?,
    isVisible: Bool,
    isActive: Bool,
    now: Date,
    window: TimeInterval
) -> Bool {
    guard hasNoPendingItems else { return false }
    if let cancelledAt = celebrationCancelledAt,
       cancelledAt >= max(lastCompletionAt ?? .distantPast, remoteEmptiedAt ?? .distantPast) {
        return false
    }
    if let completedAt = lastCompletionAt, now.timeIntervalSince(completedAt) < window {
        return true
    }
    if let emptiedAt = remoteEmptiedAt,
       isVisible, isActive,
       now.timeIntervalSince(emptiedAt) < window {
        return true
    }
    return false
}
