import Foundation

// The decision behind every empty-state and every row-skeleton gate on this
// client, in a file of its own and beside `TodoListCelebration.swift` for that
// file's stated reasons, which apply here word for word.
//
// `Package.swift` points the `TdayCore` target at the whole `Tday` tree, so an
// internal free function anywhere under it IS visible to `TdayCoreTests`, and
// `FeedAnswerTests` is what stands in for the device nobody here has. The three
// screens that ask this question are six thousand, four hundred and six hundred
// lines of view builder between them; a rule this small does not get read in
// there, and a rule this small is the difference between an app that keeps its
// answer through a refresh and one that withdraws it every time the user asks
// to check it.
//
// One practical reason not to move it back into a `View`, recorded because the
// failure it causes points nowhere near the cause.
// `motion-reachability-ios.test.ts` finds a branch by walking back up to twelve
// lines from any line that ends in an opening brace, rejoining a wrapped header
// as it goes, and it stops at neither a closing brace nor a declaration
// boundary. A file-scope `if` within twelve lines above a type declaration
// therefore makes that TYPE read as a branch. Nothing below uses the keyword at
// all -- `guard` and a ternary say the same three things -- so this file cannot
// cause that even after someone adds a fourth declaration to it.

/// What a feed has to say about itself, in the only three shapes it can say
/// anything at all.
///
/// An empty state is an ANSWER, not an absence of one. "You have no Anytime
/// tasks" is information this app already has and has already drawn; a
/// pull-to-refresh is a request to CHECK that answer, not a reason to withdraw
/// it. Only a feed that has no answer yet has anything to hide, and that is
/// exactly one case -- the first load -- which ``awaitingFirst`` names so it can
/// be told apart from the refresh it used to be collapsed into.
enum FeedAnswer: Equatable {
    /// No answer yet. Draw the row skeleton; never the empty state.
    case awaitingFirst

    /// The answer is "nothing here". Draw the empty state, and keep drawing it.
    case empty

    /// The answer is rows.
    case populated
}

/// The one decision, with no loading term anywhere in it. That absence is the
/// fix, made structural.
///
/// Every gate on this client used to spell "we have an answer" as `!isLoading`,
/// and `isLoading` is the pull-to-refresh flag. Every feed view model here
/// hydrates from its local cache synchronously in `init` -- `TodoListViewModel`,
/// `ScheduledTaskHomeViewModel` and `CompletedViewModel` all call their hydrate
/// from the initializer -- and raises `isLoading` only inside `refresh()`, whose
/// callers are the pull lambda and the error card's Retry. So `isLoading` has
/// always meant EXACTLY "a refresh over an answer already on screen", which is
/// the one condition an empty state must be held THROUGH, and every `!isLoading`
/// read it as "no answer yet" -- the meaning inverted. The gesture that asks the
/// app to re-check its answer was literally the term that withdrew it.
///
/// This client had it in the worse form, too: two gates tripped on the same
/// pull, so the illustration was not merely withdrawn but replaced by three grey
/// placeholder bars and then put back.
///
/// So no caller may pass a loading flag in here, because there is no parameter
/// to pass it to. A refresh cannot move any term below, therefore a refresh
/// cannot change what is drawn. A task arriving moves ``rowsEmpty``, so an
/// arrival still takes the empty state away. A first load on a fresh install has
/// `rowsEmpty && !firstAnswerLanded`, so the empty state still cannot be said
/// prematurely -- which is the worse bug, and the obvious way to overshoot this
/// one.
///
/// - Parameters:
///   - storeRead: this scope's local cache read has landed in state. On this
///     client that is true from the first frame, because the hydrate runs in
///     `init` and beats the first body pass; it is a parameter anyway so the
///     predicate is the same sentence Android and the web ask, and so a future
///     view model that hydrates asynchronously does not have to rediscover it.
///   - rowsEmpty: this scope's own rows, counted the way the calling gate counts
///     them. Today subtracts its Earlier section out; the Anytime home has no
///     Earlier bucket and uses the raw count.
///   - firstAnswerLanded: `isLocalMode || lastSuccessfulSyncEpochMs > 0`. See
///     ``feedFirstAnswerLanded(in:)``; the local-mode half is not optional.
func feedAnswer(
    storeRead: Bool,
    rowsEmpty: Bool,
    firstAnswerLanded: Bool
) -> FeedAnswer {
    guard storeRead else { return .awaitingFirst }
    guard rowsEmpty else { return .populated }
    return firstAnswerLanded ? .empty : .awaitingFirst
}

/// "Has this install ever had an answer from its workspace at all" -- the term
/// that separates a FIRST load from a REFRESH, read from the two places that
/// already know it.
///
/// A free function rather than three copies, because the three feed view models
/// need the identical sentence and a rule copied three times is a rule that will
/// be corrected in one place. The correction it already needed is the local-mode
/// half, and the audit this change came from got it wrong: Local Mode has no
/// server to sync with and deliberately keeps `lastSuccessfulSyncEpochMs` at
/// zero forever -- `OfflineCacheManager.saveOfflineState` zeroes it on every
/// write while `isLocalMode`, `SyncManager` zeroes it again on its no-op sync,
/// and `AppViewModel.enterLocalWorkspace` zeroes it on the way in. A sync stamp
/// used alone is therefore false there for the life of the install, and an empty
/// local workspace would show a row skeleton that never resolves and an empty
/// state that is never allowed to be said. Reading the mode FIRST is what keeps
/// that case answered.
///
/// The stamp is read off `OfflineCacheManager`'s in-memory mirror rather than
/// through `loadOfflineState()`, and that is deliberate for a reason
/// `TodoListViewModel.hydrateFromCache` already records about its own completed
/// count: every cache write wakes every live feed view model, so a hydrate that
/// re-reads the whole cache doubles the main-actor cost of every sync. This
/// needs one `Int64` that is already in memory.
///
/// On this client the value is settled before any feed is built.
/// `BootstrapSessionUseCase` awaits a forced `syncCachedData` and only then does
/// `AppViewModel` flip `authenticated`, so by the time a feed view model's
/// `init` runs, a workspace that has ever synced has a non-zero stamp. The case
/// where it has not is the case this exists for: a fresh install or a fresh
/// login whose very first sync failed or is still in flight, which today wrongly
/// shows "No Anytime tasks" about a workspace nobody has counted yet.
@MainActor
func feedFirstAnswerLanded(in container: AppContainer) -> Bool {
    firstAnswerLanded(
        isLocalMode: container.serverConfigRepository.isLocalMode(),
        lastSuccessfulSyncEpochMs: container.cacheManager.lastSuccessfulSyncEpochMsSnapshot
    )
}

/// The decision above with the two reads taken out of it, so that the decision
/// can be asserted.
///
/// Split off after a probe deleted the local-mode term from the version that
/// read the container directly and the whole suite stayed green: every test
/// naming Local Mode was passing `firstAnswerLanded: true` to ``feedAnswer`` by
/// hand, which restates the term instead of producing it. A rule that is only
/// ever hard-coded is a rule with no test, however many tests name it — and this
/// one is a single `guard` away from leaving every Local Mode install that has
/// not yet made a task looking at a row skeleton forever.
///
/// The order is the rule and not a formatting choice. `isLocalMode` is asked
/// FIRST because in Local Mode the stamp is not merely unreliable, it is held at
/// zero on purpose by three separate writers — `saveOfflineState` zeroes it on
/// every write while the mode is on, `SyncManager` zeroes it on its no-op sync,
/// and `AppViewModel.enterLocalWorkspace` zeroes it on the way in. There is no
/// value of the second parameter that makes the first one redundant.
///
/// Passing both in rather than short-circuiting costs nothing on this client:
/// `lastSuccessfulSyncEpochMsSnapshot` is an in-memory mirror, not a fetch.
/// Android's `FirstAnswerSignal.hasLanded` keeps the early return instead,
/// because there the same question is a Room query.
///
/// What remains untested is one line: which two properties
/// ``feedFirstAnswerLanded(in:)`` reads these from.
func firstAnswerLanded(isLocalMode: Bool, lastSuccessfulSyncEpochMs: Int64) -> Bool {
    if isLocalMode { return true }
    return lastSuccessfulSyncEpochMs > 0
}
