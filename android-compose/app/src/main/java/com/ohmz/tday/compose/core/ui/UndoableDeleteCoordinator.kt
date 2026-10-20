package com.ohmz.tday.compose.core.ui

import android.content.Context
import android.util.Log
import com.ohmz.tday.compose.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the delayed-commit delete flow shared by task, floater-task and list
 * deletes: the caller first *stages* the delete (local/optimistic cache removal
 * only — nothing is sent to the server), then hands this coordinator a commit
 * and an undo lambda. The coordinator shows a success toast with an Undo action
 * and starts a commit timer slightly longer than that toast's lifetime — which is
 * not a fixed number: it follows the user's accessibility timeout, so someone who
 * has asked Android for longer to act gets a longer window here and an Undo button
 * that still works for all of it (see [undoCommitDelayMillis]):
 * - if the window elapses, [onCommit] runs (the real delete — the existing
 *   repository delete whose local prune re-runs as a no-op on staged state);
 * - if the user taps Undo, the timer is cancelled and [onUndo] restores the
 *   staged local state. The server row was never touched.
 *
 * Backed by its own [MainScope] (not a ViewModel scope) so the window survives
 * screen navigation — e.g. deleting a list navigates to the scheduled task home screen immediately while the
 * commit/undo decision is still pending. Each call owns independent state and
 * timer, so rapid successive deletes commit independently (the newest toast
 * simply replaces the previous one visually).
 *
 * Accepted edge cases of the delayed-commit strategy: killing the app inside
 * the window means the commit never runs and the item survives (self-healing);
 * a sync pull during the window may briefly flash the staged-away row.
 *
 * Both of those are wider than they were, and by how much is worth knowing before
 * weighing them. The window is no longer 8.5s but whatever the user's "Time to take
 * action" stretches it to, up to [INTERACTIVE_TIMEOUT_CEILING_MS] + [UNDO_COMMIT_GRACE_MS]
 * — two minutes of staged state rather than eight seconds, and it is the accessibility
 * user who gets the long one. That is still the right trade: an Undo offered for longer
 * than the thing it would restore exists is a button that lies, and the two exposures
 * here are both recoverable. But two minutes is a real fraction of
 * `SyncManager.OFFLINE_RESYNC_INTERVAL_MS`, so the resurrection guard stops being a
 * formality at the long end — a reader touching either path should assume a pull lands
 * inside the window rather than assume it does not.
 */
@Singleton
class UndoableDeleteCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val snackbarManager: SnackbarManager,
) {
    private val scope = MainScope()

    /** Delayed-commit delete (see class doc). */
    fun showUndoableDelete(
        message: String,
        onCommit: suspend () -> Unit,
        onUndo: suspend () -> Unit,
    ) = showUndoable("delete", message, onCommit, onUndo)

    /**
     * Delayed-commit *complete*, same mechanism as delete: the caller stages the
     * completion (local/optimistic UI removal only — the task stays incomplete on
     * disk), then [onCommit] runs the real complete after the window unless the
     * user taps Undo, in which case [onUndo] restores the staged-away row.
     */
    fun showUndoableComplete(
        message: String,
        onCommit: suspend () -> Unit,
        onUndo: suspend () -> Unit,
    ) = showUndoable("complete", message, onCommit, onUndo)

    private fun showUndoable(
        opName: String,
        message: String,
        onCommit: suspend () -> Unit,
        onUndo: suspend () -> Unit,
    ) {
        // Exactly one of commit/undo may claim the action. The toast
        // auto-dismisses before the commit delay elapses, so in practice a tap
        // on Undo always wins; the flag guards the races regardless.
        val resolved = AtomicBoolean(false)
        // Read now, in the same breath as the toast this window belongs to, so the two
        // are answering the same setting: the button is offered for exactly as long as
        // the thing it would put back is still there. See [undoCommitDelayMillis].
        val commitDelayMillis = undoCommitDelayMillis(context)
        val commitJob = scope.launch {
            delay(commitDelayMillis)
            if (!resolved.compareAndSet(false, true)) return@launch
            // Once the commit starts it must not be torn mid-flight.
            withContext(NonCancellable) {
                runCatching { onCommit() }.onFailure {
                    Log.w(LOG_TAG, "$opName commit failed reason=${it.javaClass.simpleName}")
                }
            }
        }
        snackbarManager.show(
            SnackbarEvent(
                message = message,
                kind = SnackbarKind.SUCCESS,
                actionLabel = context.getString(R.string.action_undo),
                actionIconRes = R.drawable.ic_lucide_undo_2,
                onAction = {
                    if (resolved.compareAndSet(false, true)) {
                        commitJob.cancel()
                        scope.launch {
                            runCatching { onUndo() }.onFailure {
                                Log.w(
                                    LOG_TAG,
                                    "$opName undo failed reason=${it.javaClass.simpleName}",
                                )
                            }
                        }
                    }
                },
            ),
        )
    }

    private companion object {
        const val LOG_TAG = "UndoableDeleteCoordinator"
    }
}
