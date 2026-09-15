package com.ohmz.tday.compose.feature.todos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Anytime home's inline empty scene: when it is drawn, and — the half that
 * was wrong — how long its lazy item survives after it stops being drawn.
 *
 * The gate above this scene was never the problem. `celebrateEmptyState` is the
 * screen's one shared value and it does cancel on an arrival
 * ([ShouldCelebrateEmptyStateTest]); what did not hold was the HOST. The scene
 * sat behind a plain `if` on `uiState.items.isEmpty()` inside `LazyListScope`,
 * so an undo took that count 0 -> 1 and the item — with `TdayEmptyState` and the
 * `TdayConfetti` inside it — was dropped on that very frame. The burst's new
 * mount latch never got to run its envelope, because the whole composable was
 * gone: a fade cut by the unmount above it, which is the same complaint the fade
 * was added to answer, one layer up.
 *
 * Pinned as two functions rather than asserted on a device, for this file's
 * usual reason: there is no device here, `TodoListScreen` has no Compose UI test,
 * and a guard written inline in a `LazyListScope` lambda is a decision nothing
 * can check. What Compose itself owns — that `AnimatedVisibility` runs the exit
 * and then flips `currentState` — is deliberately not restated here; what is
 * restated is the rule that reads it.
 */
class FloaterEmptySceneTest {

    @Test
    fun `scene is visible on an empty settled Anytime feed`() {
        assertTrue(
            shouldShowFloaterEmptyScene(
                isFloaterTaskHomeScreen = true,
                itemsEmpty = true,
                isLoading = false,
            ),
        )
    }

    @Test
    fun `scene is not visible while the feed is still loading`() {
        // An empty feed that has not arrived yet is not a finished feed, and the
        // scene would be drawn over its own placeholder.
        assertFalse(
            shouldShowFloaterEmptyScene(
                isFloaterTaskHomeScreen = true,
                itemsEmpty = true,
                isLoading = true,
            ),
        )
    }

    @Test
    fun `scene is not visible once a task is back`() {
        // The undo, as this predicate sees it. It goes false on the arrival
        // frame, which is correct and is precisely why the MOUNT may not be this
        // same question.
        assertFalse(
            shouldShowFloaterEmptyScene(
                isFloaterTaskHomeScreen = true,
                itemsEmpty = false,
                isLoading = false,
            ),
        )
    }

    @Test
    fun `scene belongs to the Anytime home and to no other scope`() {
        // Every other scope draws its own empty state — the full-screen overlay,
        // or the inline one under Earlier's header — and two scenes on one screen
        // is a bug this branch has had before.
        assertFalse(
            shouldShowFloaterEmptyScene(
                isFloaterTaskHomeScreen = false,
                itemsEmpty = true,
                isLoading = false,
            ),
        )
    }

    @Test
    fun `item stays mounted while the exit is still playing`() {
        // THE REPORTED BUG, as arithmetic. The scene has stopped being visible —
        // the undo put a task back — and the item must NOT go with it, because
        // the confetti inside is still fading on its own envelope.
        assertTrue(
            shouldMountFloaterEmptyScene(
                sceneVisible = false,
                sceneStillDrawn = true,
            ),
        )
    }

    @Test
    fun `item goes once the exit has finished with it`() {
        // `currentState` falling is the exit's own ending, so nothing here holds
        // a timer of its own that could disagree with the animation.
        assertFalse(
            shouldMountFloaterEmptyScene(
                sceneVisible = false,
                sceneStillDrawn = false,
            ),
        )
    }

    @Test
    fun `item is mounted on the frame the scene becomes visible`() {
        // The arrival is not deferred by anything: the scene has always appeared
        // on the frame the feed emptied, and the celebration's lead is timed
        // against that.
        assertTrue(
            shouldMountFloaterEmptyScene(
                sceneVisible = true,
                sceneStillDrawn = false,
            ),
        )
    }

    @Test
    fun `a re-complete inside the same beat keeps the one item`() {
        // Ticking another task off while the previous undo's exit is still
        // running: visible again, and still drawn. One item either way — a second
        // mount here would be a second `TdayConfetti` and a second celebration
        // over one feed.
        assertTrue(
            shouldMountFloaterEmptyScene(
                sceneVisible = true,
                sceneStillDrawn = true,
            ),
        )
    }
}
