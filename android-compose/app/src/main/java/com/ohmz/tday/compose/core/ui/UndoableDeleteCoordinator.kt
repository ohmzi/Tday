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
 * and starts a commit timer slightly longer than the toast's lifetime:
 * - if the window elapses, [onCommit] runs (the real delete — the existing
 *   repository delete whose local prune re-runs as a no-op on staged state);
 * - if the user taps Undo, the timer is cancelled and [onUndo] restores the
 *   staged local state. The server row was never touched.
 *
 * Backed by its own [MainScope] (not a ViewModel scope) so the window survives
 * screen navigation — e.g. deleting a list navigates home immediately while the
 * commit/undo decision is still pending. Each call owns independent state and
 * timer, so rapid successive deletes commit independently (the newest toast
 * simply replaces the previous one visually).
 *
 * Accepted edge cases of the delayed-commit strategy: killing the app inside
 * the window means the commit never runs and the item survives (self-healing);
 * a sync pull during the window may briefly flash the staged-away row.
 */
@Singleton
class UndoableDeleteCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val snackbarManager: SnackbarManager,
) {
    private val scope = MainScope()

    fun showUndoableDelete(
        message: String,
        onCommit: suspend () -> Unit,
        onUndo: suspend () -> Unit,
    ) {
        // Exactly one of commit/undo may claim the deletion. The toast
        // auto-dismisses before the commit delay elapses, so in practice a tap
        // on Undo always wins; the flag guards the races regardless.
        val resolved = AtomicBoolean(false)
        val commitJob = scope.launch {
            delay(COMMIT_DELAY_MS)
            if (!resolved.compareAndSet(false, true)) return@launch
            // Once the commit starts it must not be torn mid-flight.
            withContext(NonCancellable) {
                runCatching { onCommit() }.onFailure {
                    Log.w(LOG_TAG, "delete commit failed reason=${it.javaClass.simpleName}")
                }
            }
        }
        snackbarManager.show(
            SnackbarEvent(
                message = message,
                kind = SnackbarKind.SUCCESS,
                actionLabel = context.getString(R.string.action_undo),
                onAction = {
                    if (resolved.compareAndSet(false, true)) {
                        commitJob.cancel()
                        scope.launch {
                            runCatching { onUndo() }.onFailure {
                                Log.w(
                                    LOG_TAG,
                                    "delete undo failed reason=${it.javaClass.simpleName}",
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

        // Slightly longer than the action-toast auto-dismiss (8s in TdayApp) so
        // the Undo button can never outlive the staged state it would restore.
        const val COMMIT_DELAY_MS = 8_500L
    }
}
