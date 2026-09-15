package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest

private const val SWIPE_OPEN_VELOCITY_PX_PER_SECOND = -1450f
private const val SWIPE_OPEN_THRESHOLD_FRACTION = 0.32f
private const val SWIPE_MAX_ELASTIC_FRACTION = 1.14f
private const val SWIPE_HINT_MS = 150L
private const val SWIPE_HINT_SETTLE_MS = 360L

/**
 * The one spring the task row is allowed to use, and the one moment it is
 * allowed to use it.
 *
 * A swipe row has two clocks in it, and only one of them belongs to the app. The
 * finger's clock is the finger: while a pointer is down the row's offset is the
 * pointer's translation and nothing else, because a row that is 1:1 with the
 * thumb feels welded to it and a row that is anything else feels like it is on a
 * string. The app's clock only starts when the finger lifts — the settle to
 * open, the settle back to closed, the snap back from the elastic overdrag.
 *
 * [Release] is that second clock. It is iOS's release spec converted, from
 * `SwipeActions.swift`'s `.interactiveSpring(response: 0.34, dampingFraction: 0.82)`:
 * SwiftUI's `response` is the undamped period, so stiffness is `(2π / 0.34)² ≈ 341`,
 * and `dampingFraction` is Compose's `dampingRatio` unchanged. Two clients
 * releasing a row on the same curve is the whole point of writing the number
 * down; `Spring.StiffnessMedium` would be 1500f, which is 4.4× this and is a
 * Compose default rather than a decision anybody made.
 */
object TaskSwipeMotion {
    const val ReleaseDampingRatio: Float = 0.82f
    const val ReleaseStiffness: Float = 340f

    val Release: SpringSpec<Float> = spring(
        dampingRatio = ReleaseDampingRatio,
        stiffness = ReleaseStiffness,
    )
}

/**
 * One release of the row: where the finger left it, where it is going, and how
 * fast it was travelling when it was let go.
 *
 * Deliberately not a `data class`. Each release is a distinct event even when its
 * numbers repeat — settling closed from zero twice running has to restart the
 * animation both times — and identity equality is what makes `snapshotFlow`
 * emit it again.
 *
 * The `skipcq` marker is that rationale, enforced. DeepSource reads a class with
 * only `val`s as one that wants the `data` keyword, which is the usual case and
 * is wrong here: generated `equals` would make two identical releases compare
 * equal, `snapshotFlow` would drop the second, and a row settling closed from
 * zero twice running would animate once.
 */
@Stable
internal class TaskSwipeRelease(  // skipcq: KT-W1058
    val fromPx: Float,
    val toPx: Float,
    val initialVelocityPxPerSecond: Float,
)

@Stable
class TaskSwipeRevealState internal constructor(
    private val revealWidthPx: Float,
    private val hintOffsetPx: Float,
    private val maxElasticDragPx: Float,
) {
    /**
     * Where the row is actually drawn, in px. While [isDragging] this is the
     * finger's translation exactly — no spring stands between the two.
     */
    var offsetX by mutableFloatStateOf(0f)
        private set

    var isDragging by mutableStateOf(false)
        private set

    var isHinting by mutableStateOf(false)
        private set

    /**
     * Where the row comes to rest once no finger is on it: `0f` closed,
     * `-revealWidthPx` open, `-hintOffsetPx` for the moment a hint holds it out.
     */
    var restOffsetX by mutableFloatStateOf(0f)
        private set

    /** Non-null while a release spring should be running. */
    internal var release by mutableStateOf<TaskSwipeRelease?>(null)
        private set

    /**
     * Bumped by every drag, and read by [playHint] either side of its hold.
     *
     * Asking `isDragging` after the fact only ever sees a finger that is *still*
     * down. A flick is 60-100 ms of contact, so one can land, open the row and
     * be gone again entirely inside the hint's 150 ms hold — at which point a
     * live re-read says "no finger" and the hint slams the row the user just
     * opened shut. A counter remembers the interference instead of sampling it.
     */
    private var dragGeneration = 0

    /**
     * Whether this open-cycle has already spent its reveal haptic.
     *
     * A plain var for the same reason [dragGeneration] is one: nothing draws it,
     * and what it holds is the memory of an event rather than a value to be
     * sampled afterwards. Sampling is exactly the failure — "is the row past the
     * threshold?" answers yes on every frame a finger rests there, and a detent
     * that repeats for as long as you hold still is a rattle rather than a
     * detent.
     *
     * One boolean, and deliberately not a second threshold under the first.
     * A hysteresis band exists to stop a single comparison strobing on its own
     * boundary; this cannot re-arm inside an open-cycle *at all*, so a finger
     * parked on the boundary cannot repeat and a drag that crosses, comes back
     * and crosses again cannot fire twice. The band would be a number nobody can
     * justify, solving a problem the flag has already solved.
     *
     * Its lifetime is this composition's rather than the task's:
     * [rememberTaskSwipeRevealState] keys on the id *and* on the px widths the
     * density produces, so rotating the device with a row held open builds a new
     * state and re-arms the flag. That is a rotation mid-gesture buying one buzz
     * it does not strictly owe, and it is not worth a longer-lived cache to
     * prevent.
     */
    private var hasFiredRevealDetent = false

    /**
     * Under a finger this asks about the finger; otherwise it asks where the row
     * is headed, not where it currently is — a row settling closed has already
     * given up its swipe slot.
     */
    val isOpenOrDragging: Boolean
        get() = if (isDragging) offsetX != 0f else restOffsetX != 0f

    /**
     * Moves the row with the finger, and answers whether this is the update that
     * committed it to opening — the detent.
     *
     * The decision is here and the buzz is not. This class holds no `View` and
     * its test runs on a bare JVM with no Robolectric and no Compose harness, so
     * it returns what happened and the composable performs it; `TdayToastHost`'s
     * `settle` settled that shape two files over. It is also what lets the whole
     * fire-once rule be proven without a device, which is the only way it can be
     * proven at all — no gate in this repository can feel a haptic.
     *
     * Why the detent and not the settle. The row is asked for by sliding it
     * left to show the buttons behind, and *that* is this moment: the actions
     * catching under the thumb, not the app reporting an animation after the
     * hand has already gone. It is the only moment this file believes in, too —
     * the finger's clock is the finger, and a buzz that waits for the release is
     * on the app's clock. The predicate is [settle]'s own `dragOpen` with the
     * velocity term left out and no threshold of its own invented, so the buzz
     * cannot lie: feeling it means "let go now and this row opens".
     *
     * The one honest cost, said out loud rather than left for a device to find:
     * cross the detent, drag back, release closed, and you have felt a reveal
     * that did not happen. That is what a detent on a physical control does. The
     * alternative — silence until the row settles — costs the feature its point.
     *
     * @return `true` on the single update that takes the row from short of the
     *   threshold to past it, at most once per open-cycle.
     */
    fun dragBy(deltaPx: Float): Boolean {
        // A pointer outranks every animation: whatever was in flight — a settle,
        // a close, the tail of a hint — stops here and the row continues from
        // wherever that animation had got to.
        isDragging = true
        dragGeneration++
        release = null
        offsetX = (offsetX + deltaPx).coerceIn(-maxElasticDragPx, 0f)
        // After the clamp, never before: the overdrag limit is part of where the
        // finger actually put the row, and the detent is about where the row is.
        val pastDetent = offsetX < -(revealWidthPx * SWIPE_OPEN_THRESHOLD_FRACTION)
        if (!pastDetent || hasFiredRevealDetent) return false
        hasFiredRevealDetent = true
        return true
    }

    /**
     * The finger has gone. Answers whether the row still owes a reveal haptic.
     *
     * The second arm of the same event, and without it the most deliberate swipe
     * in the app would be the only silent one: a fling opens the row from well
     * under the distance threshold, so a detent that never came round is still a
     * reveal. It reports the decision this function was already making rather than
     * recomputing one, and it answers `false` when [dragBy] has already fired,
     * which is what keeps one open to one buzz.
     *
     * A settle that lands the row *closed* returns `false` and always will:
     * closing puts back what was there, and every pill the reveal uncovers
     * already fires its own haptic and then closes the row, so a close buzz
     * would double each of them. A close is frequently not even something the
     * user did to this row — one row open at a time means the previous row is
     * shut from under a finger that is nowhere near it.
     *
     * @return `true` only when this release opens the row and the detent did not
     *   fire during this open-cycle.
     */
    fun settle(velocityPxPerSecond: Float): Boolean {
        val flingOpen = velocityPxPerSecond < SWIPE_OPEN_VELOCITY_PX_PER_SECOND
        val dragOpen = offsetX < -(revealWidthPx * SWIPE_OPEN_THRESHOLD_FRACTION)
        val opens = flingOpen || dragOpen
        isDragging = false
        val owesReveal = opens && !hasFiredRevealDetent
        if (owesReveal) hasFiredRevealDetent = true
        // The lift-off velocity is carried into the spring so the fling and the
        // settle are one continuous movement rather than a throw and a restart.
        settleTo(if (opens) -revealWidthPx else 0f, velocityPxPerSecond)
        return owesReveal
    }

    fun close() {
        isDragging = false
        settleTo(0f, 0f)
    }

    /**
     * Nudges the row open and lets it fall closed again, to show there is
     * something under it.
     *
     * Takes the animator scale rather than reading it, because this class is not
     * a composable and the springs it starts are run for it by
     * [animateTaskSwipeOffsetAsState] — every caller is a composable that can
     * ask [rememberTdayMotionScale] and hand the answer down. The two waits are
     * gaps between those two springs and nothing else, so they are scaled with
     * them.
     *
     * @param scale the animator duration scale, from [rememberTdayMotionScale].
     */
    suspend fun playHint(scale: Float) {
        // The hint is a suggestion, and a suggestion never overrules a finger:
        // it does not start under one, and it abandons its own second half if
        // one arrives while it is running.
        if (isHinting || isDragging) return
        // With animations off there is no hint to give. The whole gesture is
        // movement — a row that ends exactly where it started — so the finished
        // state this would have to draw instead is the row as it already is.
        // Playing it anyway would put the row 42 dp out and back inside a single
        // frame, which is a flicker rather than a suggestion. Written as "not
        // greater than zero" so that a NaN read off the setting lands here too.
        if (!(scale > 0f)) return
        isHinting = true
        try {
            val generation = dragGeneration
            settleTo(-hintOffsetPx, 0f)
            scaledDelay(SWIPE_HINT_MS, scale)
            // A finger that came and went inside the hold counts as much as one
            // that is still there: either way the row is no longer the hint's to
            // move.
            if (isDragging || dragGeneration != generation) return
            settleTo(0f, 0f)
            scaledDelay(SWIPE_HINT_SETTLE_MS, scale)
        } finally {
            isHinting = false
        }
    }

    fun revealProgress(offsetX: Float): Float {
        return (-offsetX / revealWidthPx).coerceIn(0f, 1f)
    }

    /**
     * Called by the release animation, once per frame.
     *
     * Deliberately holds no detent test. This drives [offsetX] straight through
     * the threshold once per frame for the whole settle-open animation, so a
     * flag derived from the offset in general rather than from the drag path in
     * particular would phantom-fire here one frame after the real event — and
     * again on every programmatic open, where no finger was ever involved.
     */
    internal fun onReleaseFrame(valuePx: Float) {
        if (isDragging) return
        offsetX = valuePx
    }

    internal fun onReleaseSettled(finished: TaskSwipeRelease) {
        if (release === finished) {
            release = null
        }
    }

    /**
     * The single funnel every close already passes through — [settle] landing
     * shut, [close], the row whose slot another row claimed, the tail of
     * [playHint] — which is why re-arming the detent lives here and no close
     * path has to remember it. Cleared at the moment of the decision rather than
     * when the spring lands, to match [restOffsetX]'s own tense: where the row is
     * headed is what the rest of this class already reads.
     */
    private fun settleTo(targetPx: Float, initialVelocityPxPerSecond: Float) {
        if (targetPx == 0f) hasFiredRevealDetent = false
        restOffsetX = targetPx
        release = if (offsetX == targetPx) {
            null
        } else {
            TaskSwipeRelease(offsetX, targetPx, initialVelocityPxPerSecond)
        }
    }
}

@Composable
fun rememberTaskSwipeRevealState(
    key: Any?,
    revealWidth: Dp = 176.dp,
    hintOffset: Dp = 42.dp,
): TaskSwipeRevealState {
    val density = LocalDensity.current
    val revealWidthPx = with(density) { revealWidth.toPx() }
    val hintOffsetPx = with(density) {
        hintOffset.toPx().coerceAtMost(revealWidthPx * 0.24f)
    }
    val maxElasticDragPx = revealWidthPx * SWIPE_MAX_ELASTIC_FRACTION

    return remember(key, revealWidthPx, hintOffsetPx, maxElasticDragPx) {
        TaskSwipeRevealState(
            revealWidthPx = revealWidthPx,
            hintOffsetPx = hintOffsetPx,
            maxElasticDragPx = maxElasticDragPx,
        )
    }
}

/**
 * The row's drawn offset.
 *
 * There is no animation in the drag path at all: the returned state reads
 * [TaskSwipeRevealState.offsetX], which the drag writes directly. The spring
 * runs only for a release, and it starts from the offset the finger left rather
 * than from wherever an animation happened to have chased to, so the handover
 * costs no frame and shows no jump.
 */
@Composable
fun animateTaskSwipeOffsetAsState(
    state: TaskSwipeRevealState,
    label: String,
): State<Float> {
    val settle = remember(state) {
        Animatable(
            initialValue = state.offsetX,
            typeConverter = Float.VectorConverter,
            label = label,
        )
    }
    LaunchedEffect(state, settle) {
        snapshotFlow { state.release }.collectLatest { release ->
            if (release == null) return@collectLatest
            settle.snapTo(release.fromPx)
            settle.animateTo(
                targetValue = release.toPx,
                animationSpec = TaskSwipeMotion.Release,
                initialVelocity = release.initialVelocityPxPerSecond,
            ) {
                state.onReleaseFrame(value)
            }
            state.onReleaseSettled(release)
        }
    }
    // This line is the defect site. Reinstating an `animateFloatAsState` here —
    // or anything else that interposes a clock between the finger and the draw —
    // brings back the ~141 ms trail, and no gate in this repository can see it:
    // there is no Compose harness on the unit-test source set (`ui-test-junit4`
    // is androidTest-only and androidTest is compiled but never run). The only
    // thing watching it is the PR 19 row in docs/verification/phase-3-device-pass.md.
    return remember(state) { derivedStateOf { state.offsetX } }
}
