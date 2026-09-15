package com.ohmz.tday.compose.feature.todos

import com.ohmz.tday.compose.core.ui.FeedAnswer
import com.ohmz.tday.compose.core.ui.feedAnswer
import org.junit.Assert.assertEquals
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
 * The gate above it then turned out to be the OTHER half of the same mistake,
 * and that half is the one the user reported: pulling this feed down withdrew
 * the illustration, the heading and the body, and the whole block came back
 * unchanged when the refresh returned with nothing new. `!isLoading` was reading
 * "a refresh over an answer already on screen" as "no answer yet" -- see
 * [feedAnswer], which has no loading term for anyone to pass. The tests below
 * assert BOTH directions of the replacement, because only one of them is the
 * reported bug and the other is the obvious way to overshoot it: the scene
 * survives a refresh, AND it is still withheld before the first answer exists.
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
                answer = FeedAnswer.Empty,
            ),
        )
    }

    @Test
    fun `a refresh cannot withdraw the scene, because it cannot reach this gate`() {
        // THE REPORTED BUG, stated as an arity. There is no refresh parameter to
        // vary here and none inside [feedAnswer] either, so "the scene survives a
        // pull" is not a case that has to be enumerated -- it is a shape. Both
        // signatures are asserted by name so that re-admitting a loading flag to
        // either one fails here rather than on a device nobody has.
        // Bound as typed references rather than inspected reflectively: a
        // function type IS the arity, so re-admitting a loading flag to either
        // signature stops compiling this file. That is the earliest a rule of
        // this shape can be caught, and it needs no reflection on the classpath.
        val sceneGate: (Boolean, FeedAnswer) -> Boolean = ::shouldShowFloaterEmptyScene
        val decide: (Boolean, Boolean, Boolean) -> FeedAnswer = ::feedAnswer
        assertTrue(sceneGate(true, FeedAnswer.Empty))
        assertEquals(FeedAnswer.Empty, decide(true, true, true))
        // And the same fact from the caller's side: the ONLY inputs a pull moves
        // on this screen are `isLoading`/`isRefreshing`, neither of which appears
        // in the three terms below, so the answer on the frame before the pull and
        // the answer during it are the same value.
        val hydratedEmptyAndAnswered = feedAnswer(
            storeRead = true,
            rowsEmpty = true,
            firstAnswerLanded = true,
        )
        assertEquals(FeedAnswer.Empty, hydratedEmptyAndAnswered)
        assertTrue(
            shouldShowFloaterEmptyScene(
                isFloaterTaskHomeScreen = true,
                answer = hydratedEmptyAndAnswered,
            ),
        )
    }

    @Test
    fun `scene is still withheld before the first answer exists`() {
        // The overshoot, and the reason this fix is not "make the empty state
        // unconditional". A fresh install's cache read lands immediately and
        // lands EMPTY, so `storeRead` alone would let the screen say "no Anytime
        // tasks" to someone whose very first sync is still in flight.
        assertEquals(
            FeedAnswer.AwaitingFirst,
            feedAnswer(storeRead = true, rowsEmpty = true, firstAnswerLanded = false),
        )
        assertFalse(
            shouldShowFloaterEmptyScene(
                isFloaterTaskHomeScreen = true,
                answer = FeedAnswer.AwaitingFirst,
            ),
        )
    }

    @Test
    fun `an empty Local Mode workspace is an answer from its first frame`() {
        // The regression the naive sync-stamp fix would have caused. Local Mode
        // has no server and deliberately never records a successful sync, so
        // `lastSuccessfulSyncEpochMs > 0` alone is false forever there -- an empty
        // local workspace would sit in a row skeleton that never resolves and
        // never be allowed to say what it actually knows. [FirstAnswerSignal]
        // reads `isLocalMode()` first for exactly this, and the term arrives here
        // already true.
        assertEquals(
            FeedAnswer.Empty,
            feedAnswer(storeRead = true, rowsEmpty = true, firstAnswerLanded = true),
        )
    }

    @Test
    fun `scene is not visible once a task is back`() {
        // The undo, as this predicate sees it. An arrival moves `rowsEmpty`, which
        // is a term the answer DOES have, so it goes false on the arrival frame --
        // correct, and precisely why the MOUNT may not be this same question.
        assertEquals(
            FeedAnswer.Populated,
            feedAnswer(storeRead = true, rowsEmpty = false, firstAnswerLanded = true),
        )
        assertFalse(
            shouldShowFloaterEmptyScene(
                isFloaterTaskHomeScreen = true,
                answer = FeedAnswer.Populated,
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
                answer = FeedAnswer.Empty,
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
