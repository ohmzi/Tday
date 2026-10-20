package com.ohmz.tday.compose.ui.component

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.TransitionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the create sheet tells its host it may tear the composition down.
 *
 * The handover is the whole of the defect. Every affordance that leaves this sheet — the
 * close button, the scrim, the back press, and since PR 41b the Create/Save confirm — has
 * to reach the host's `onDismiss` at the END of the exit, because `onDismiss` is what stops
 * composing the `Dialog` the card is drawn in. Reaching it on the frame of the tap does not
 * shorten the animation, it deletes it.
 *
 * What separates the fixed shape from the broken one is not the endpoints: both of them end
 * with the sheet gone and the host torn down, so a test that checks the endpoints passes
 * against the defect. It is the gap in between — `dismissing` true while `gone` is still
 * false — and that is what these assertions are about.
 *
 * [SheetDismissState.start] latching is the other half, and it is load-bearing for a reason
 * the endpoints do not show either. The card is still composed and still hit-testable for
 * the whole exit, so the Create button stays lit under the user's finger; the confirm path
 * claims the exit BEFORE it builds its payload and drops the tap if the claim was refused,
 * which is the only thing between a second tap mid-slide and a second task. That is why
 * [SheetDismissState.start] answers, and why these assertions are on the answer rather than
 * on the state it leaves behind — a latch that did nothing would leave exactly the same
 * `dismissing` and the same `targetState` as one that works.
 *
 * What this file deliberately cannot see is the wiring: that `submitTask` opens on
 * `sheetDismiss.start()` and drops the tap when it is refused, and that no host still
 * clears its own flag inside the confirm. There is no Compose test runtime on this module's
 * JVM classpath, so the composition those live in cannot be run here — they are read, and
 * they carry a device row.
 */
class CreateSheetDismissTest {

    /**
     * A sheet that is on screen: what every host but the widget's composes on the frame the
     * user asks for it, one enter later.
     */
    private fun presentedSheet() = SheetDismissState(MutableTransitionState(true))

    /**
     * `currentState` and `isRunning` are written by the `Transition` that `AnimatedVisibility`
     * builds, and both setters are `internal` to animation-core, so a plain JVM test has to
     * reach them the way the animation would. The alternative is to assert on `targetState`
     * alone — which the broken tree also set, on the same frame, right before it tore the
     * composition down. That would prove nothing at all.
     *
     * Found by prefix rather than by full name: Kotlin mangles an `internal` member with the
     * producing module's name, and animation-core has already changed its own
     * (`setRunning$animation_core_release` became `setRunning$animation_core`). Pinning the
     * spelling would make a Compose upgrade look like a dismissal regression.
     */
    private fun writeInternal(declaring: Class<*>, name: String, target: Any, value: Any?) {
        val method = declaring.methods.firstOrNull { it.name.startsWith("$name\$") }
            ?: error("$declaring has no internal '$name' setter any more — read Transition.kt")
        method.invoke(target, value)
    }

    private fun MutableTransitionState<Boolean>.writeCurrentState(value: Boolean) {
        writeInternal(MutableTransitionState::class.java, "setCurrentState", this, value)
    }

    private fun MutableTransitionState<Boolean>.writeRunning(value: Boolean) {
        writeInternal(TransitionState::class.java, "setRunning", this, value)
    }

    /** Play the exit out the way the real `Transition` does: run, land, stop. */
    private fun MutableTransitionState<Boolean>.playExit() {
        writeRunning(true)
        writeCurrentState(false)
        writeRunning(false)
    }

    @Test
    fun `a sheet nobody has dismissed is not gone just because it is idle`() {
        val state = presentedSheet()
        assertTrue("a settled sheet reports itself idle", state.transition.isIdle)
        assertFalse("and must not be mistaken for a dismissed one", state.gone)
        assertFalse(state.dismissing)
        assertTrue(state.visible)
    }

    @Test
    fun `a confirm asks for the exit and tells the host nothing yet`() {
        val state = presentedSheet()
        state.start()

        assertTrue("the exit has been asked for", state.dismissing)
        assertFalse("and the sheet is on its way out", state.visible)
        assertFalse(
            "the host must not tear the Dialog down on the frame of the tap — that is the cut",
            state.gone,
        )
    }

    @Test
    fun `the host is told once, at the end of the slide and not before`() {
        val state = presentedSheet()
        val readings = mutableListOf(state.gone)

        state.start()
        readings += state.gone

        // Mid-slide, with the card's own target already flipped: `gone` has to wait for the
        // transition to settle, not merely for it to have been retargeted.
        state.transition.writeRunning(true)
        readings += state.gone
        state.transition.writeCurrentState(false)
        readings += state.gone

        state.transition.writeRunning(false)
        readings += state.gone
        readings += state.gone

        val risingEdges = readings.zipWithNext().count { (before, after) -> !before && after }
        assertEquals("the host is released exactly once", 1, risingEdges)
        assertEquals(
            "and only after the card has landed",
            listOf(false, false, false, false, true, true),
            readings,
        )
    }

    @Test
    fun `a second confirm during the slide is refused, and one task is one task`() {
        val state = presentedSheet()
        assertTrue("the tap that claims the exit is told so, and submits", state.start())
        state.transition.writeRunning(true)

        // The window the defect lives in: the card has started moving and is still under
        // the finger. A confirm that got a `true` here would build a second payload, and a
        // second payload is a second task on every device.
        assertFalse("a second tap mid-slide claims nothing", state.start())
        state.transition.writeCurrentState(false)
        assertFalse("nor does one that lands as the card does", state.start())

        assertTrue(state.dismissing)
        assertFalse("and no repeat tap restarts the slide", state.transition.targetState)

        state.transition.writeRunning(false)
        assertTrue(
            "one exit was asked for and one exit finishes it; the extra taps queued nothing",
            state.gone,
        )
    }

    @Test
    fun `an exit that has already finished is not restarted by a stray tap`() {
        val state = presentedSheet()
        assertTrue(state.start())
        state.transition.playExit()
        assertTrue(state.gone)

        assertFalse("the sheet has already left; there is nothing to claim", state.start())

        assertTrue("still gone", state.gone)
        assertFalse("and still not visible", state.visible)
    }

    @Test
    fun `a sheet its host opens already showing has nothing to slide in`() {
        // The widget create surface: the Activity IS the sheet, so it is presented on the
        // first frame rather than entered. It still leaves the same way.
        val state = SheetDismissState(MutableTransitionState(true))
        assertTrue(state.visible)
        assertTrue(state.transition.isIdle)

        state.start()
        assertFalse(state.gone)
        state.transition.playExit()
        assertTrue(state.gone)
    }
}
