package com.ohmz.tday.compose.core.ui

/**
 * What a feed has to say about itself, in the only three shapes it can say
 * anything at all.
 *
 * An empty state is an ANSWER, not an absence of one. "You have no Anytime
 * tasks" is information this app already has and has already drawn; a pull-to-
 * refresh is a request to CHECK that answer, not a reason to withdraw it. Only
 * a feed that has no answer yet has anything to hide, and that is exactly one
 * case -- the first load -- which [AwaitingFirst] names so it can be told apart
 * from the refresh it used to be collapsed into.
 */
internal enum class FeedAnswer {
    /** No answer yet. Draw the row skeleton; never the empty state. */
    AwaitingFirst,

    /** The answer is "nothing here". Draw the empty state, and keep drawing it. */
    Empty,

    /** The answer is rows. */
    Populated,
}

/**
 * The one decision behind every empty-state gate on every feed, with no loading
 * term anywhere in it. That absence is the fix, made structural.
 *
 * Every native gate used to spell "we have an answer" as `!isLoading`, and
 * `isLoading` is the pull-to-refresh flag. On this client `TodoListViewModel`'s
 * `load()` never touches it -- that path ends in the synchronous
 * `hydrateFromCache` -- and the only writer of `isLoading = true` is
 * `refreshInternal(showLoading = true)`, reachable only from `refresh()`, whose
 * only callers are `TdayApp`'s pull lambda and the error card's Retry. So
 * `isLoading` has always meant EXACTLY "a refresh over an answer already on
 * screen" -- the one condition an empty state must be held THROUGH -- and every
 * `!isLoading` reading it as "no answer yet" had the meaning inverted. The
 * gesture that asks the app to re-check its answer was literally the term that
 * withdrew it: the illustration vanished on the pull and came back when the
 * refresh returned with nothing new.
 *
 * So no caller may pass a loading flag in here, because there is no parameter to
 * pass it to. A refresh cannot move any term below, therefore a refresh cannot
 * change what is drawn. A task arriving moves [rowsEmpty], so an arrival still
 * takes the empty state away. A first load on a fresh install has
 * `rowsEmpty && !firstAnswerLanded`, so the empty state still cannot be said
 * prematurely -- which is the worse bug, and the obvious way to overshoot this
 * one.
 *
 * @param storeRead this scope's local cache read has landed in state
 *   (`TodoListUiState.hasHydratedSnapshot`, written on both the success and the
 *   failure path of `hydrateFromCache` -- a cache read that threw still answered).
 * @param rowsEmpty this scope's own items, counted the way the calling gate
 *   counts them. Today subtracts Earlier out; the Anytime home has no Earlier
 *   bucket and uses the raw count.
 * @param firstAnswerLanded `isLocalMode || lastSuccessfulSyncEpochMs > 0`. The
 *   local-mode half is not optional: Local Mode deliberately never records a
 *   successful sync, so a sync stamp alone would leave an empty local workspace
 *   showing a skeleton that never resolves and an empty state that never comes.
 */
internal fun feedAnswer(
    storeRead: Boolean,
    rowsEmpty: Boolean,
    firstAnswerLanded: Boolean,
): FeedAnswer = when {
    !storeRead -> FeedAnswer.AwaitingFirst
    !rowsEmpty -> FeedAnswer.Populated
    firstAnswerLanded -> FeedAnswer.Empty
    else -> FeedAnswer.AwaitingFirst
}
