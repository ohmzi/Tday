package com.ohmz.tday.shared.bulk

/**
 * The four actions a multi-select in a task list can apply to its whole
 * selection. Nothing else belongs in the selection action bar.
 */
enum class BulkAction {
    COMPLETE,
    DELETE,
    SET_PRIORITY,
    MOVE_TO_LIST,
}

/**
 * The parts of `docs/design/bulk-selection.md` that are numbers and rules rather
 * than prose, so the three surfaces agree by construction instead of by review.
 *
 * There is deliberately no backend batch endpoint: every bulk action fans out to
 * the single-item routes that already exist, which keeps Local Mode, the offline
 * pending-mutation queues and the exact-version compatibility gate untouched.
 * The design note records that decision and its revisit trigger in full.
 *
 * Android consumes this directly via `project(":shared")`. Web and iOS mirror the
 * literals with a comment pointing here — the same arrangement the rest of the
 * shared contract already lives with.
 */
object BulkSelectionPolicy {

    /**
     * Hardest cap on one bulk action, because fan-out is N requests.
     *
     * The `api_global` rate-limit policy allows `API_RATE_LIMIT_MAX` (default 180)
     * requests per `API_RATE_LIMIT_WINDOW_SEC` (default 60) per authenticated user,
     * and every mutation additionally fans out one realtime event to each share
     * collaborator, one webhook delivery per subscription and one push poke per
     * device — none of it coalesced. Staying comfortably under the limit is what
     * keeps a large selection from becoming a *partially applied* destructive
     * action halfway through.
     */
    const val MAX_SELECTION: Int = 100

    /** Parallel in-flight requests per bulk action. Sequential is also fine. */
    const val MAX_CONCURRENCY: Int = 4

    /**
     * A recurring occurrence may be selected and bulk-completed *as the occurrence
     * it represents*, but is never eligible for bulk delete, priority or move:
     * those three have no per-occurrence route and would silently act on the whole
     * series. Morning Sweep already excludes recurring tasks from its batch for the
     * same reason.
     *
     * "As the occurrence it represents" is the whole of it — see
     * [effectiveSelection], which additionally requires the row to *have* an
     * occurrence. A recurring row with no `instanceDate` addresses the series, not
     * an occurrence, so it is not something a multi-select may complete.
     */
    fun appliesToRecurring(action: BulkAction): Boolean = action == BulkAction.COMPLETE

    /**
     * Bulk delete always asks first — a confirmation stating the exact count, on top
     * of (not instead of) the existing delayed-commit undo toast.
     *
     * Bulk move asks only when the selection spans more than one source list, because
     * that is the case the user cannot put back: there is no undo for an edit, and the
     * original per-task assignments are gone. [distinctSourceLists] counts "no list" as
     * its own value. Complete is undoable and priority is one tap to reverse, so
     * neither asks.
     */
    fun requiresConfirmation(action: BulkAction, distinctSourceLists: Int): Boolean = when (action) {
        BulkAction.DELETE -> true
        BulkAction.MOVE_TO_LIST -> distinctSourceLists > 1
        BulkAction.COMPLETE, BulkAction.SET_PRIORITY -> false
    }

    /**
     * Whether the action is reversible from its own toast. Complete and delete stage
     * locally and commit after the undo window; priority and move write straight
     * through and succeed silently per the unified toast policy.
     */
    fun isUndoable(action: BulkAction): Boolean =
        action == BulkAction.COMPLETE || action == BulkAction.DELETE

    /**
     * The rows [action] will actually touch, given the whole selection. [isRecurring]
     * is `rrule != null`; [hasInstanceDate] is `instanceDate != null`. Select-all is
     * capped in display order from the top so the outcome is deterministic rather
     * than whichever hundred the set happened to hold.
     *
     * COMPLETE additionally drops a recurring row that carries no `instanceDate`,
     * and does so against every server version.
     *
     * On a server that predates the fix, `TodoService.completeTodo` branched
     * `if (rrule == null) {...} else if (instanceDate != null) {...}`, so completing
     * a recurring row without one took neither branch: it inserted a `CompletedTodos`
     * history row, marked nothing complete and wrote no `TodoInstances` row. The task
     * came straight back on the next refetch and Completed history — plus the on-time
     * and days-to-complete stats built from it — gained an entry that corresponded to
     * nothing.
     *
     * On a fixed server that request completes the **series** instead. That is the
     * right answer for a single deliberate tap, and the wrong one to fan out over a
     * select-all: it would end whole recurring series rather than clear one occurrence
     * each. Either way the row stays out of the set — do not relax this on the grounds
     * that the server is fixed now.
     *
     * [hasInstanceDate] defaults to "no occurrence", so a caller that has not
     * thought about it gets the safe answer rather than the phantom row.
     *
     * [isRecurring] stays the LAST parameter so the trailing-lambda call style every
     * existing caller uses still binds to it. Putting the new predicate last instead
     * would silently rebind `effectiveSelection(action, rows) { it.recurring }` to
     * [hasInstanceDate] and quietly invert the rule.
     */
    fun <T> effectiveSelection(
        action: BulkAction,
        selection: List<T>,
        hasInstanceDate: (T) -> Boolean = { false },
        isRecurring: (T) -> Boolean,
    ): List<T> {
        val eligible = if (appliesToRecurring(action)) {
            selection.filter { !isRecurring(it) || hasInstanceDate(it) }
        } else {
            selection.filterNot(isRecurring)
        }
        return eligible.take(MAX_SELECTION)
    }

    /** True once the selection has reached the cap and further taps must be refused. */
    fun isAtCap(selectedCount: Int): Boolean = selectedCount >= MAX_SELECTION
}
