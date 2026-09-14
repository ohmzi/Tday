package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch

private const val TOAST_ENTER_FADE_DURATION_MS = 180
private const val TOAST_EXIT_FADE_DURATION_MS = 140
private const val TOAST_EXIT_SLIDE_DURATION_MS = 180
private const val TOAST_DRAG_GAIN = 1.18f
private const val TOAST_DISMISS_DURATION_MS = 160
private const val TOAST_FADE_DISTANCE_DP = 96

// How far down the toast has to be taken before letting go throws it away, as a
// fraction of TOAST_FADE_DISTANCE_DP. The distance is not a new number: the fade
// to 45 % alpha and the 3 % shrink are both mapped over TOAST_FADE_DISTANCE_DP,
// so that constant is already this surface's own statement of how far a
// dismissal travels, and a fraction of it is the threshold the toast has been
// drawing all along. 0.32 is TaskSwipeRevealState's SWIPE_OPEN_THRESHOLD_FRACTION
// pointed downwards — a row and a toast that give way at different fractions of
// their own travel are two gestures to learn instead of one — and 0.32 x 96 dp is
// ~31 dp, far enough past touch slop that nothing on the way to a tap reaches it.
private const val TOAST_DISMISS_THRESHOLD_FRACTION = 0.32f

// The escape hatch, so that a flick does not have to travel the distance. The
// row's -1450 px/s, again pointed the other way.
private const val TOAST_DISMISS_VELOCITY_PX_PER_SECOND = 1450f
private val TOAST_CORNER_RADIUS = 24.dp
// Slack around the card for the drop shadow to spill into. The enter fade holds the toast in a
// layer at alpha < 1, and Compose clips a part-transparent layer to the composable's own bounds —
// with the card flush against them the shadow was sliced off flat along the bottom edge until the
// fade finished. Taken straight back out of the bottom padding so the toast sits where it did.
private val TOAST_SHADOW_SPILL = 12.dp
private val TOAST_BOTTOM_PADDING = 88.dp - TOAST_SHADOW_SPILL

// Brand accent — the coral used by the overdue tile on the scheduled task home screen. Non-error
// toasts share it so the toast accent matches the rest of the app; errors keep the
// Material error red so the danger cue survives.
private val TOAST_BRAND_ACCENT = Color(0xFFE06F66)

/** Visual variant of a toast — drives the accent colour and the default icon. */
enum class TdayToastKind { ERROR, SUCCESS, INFO }

data class TdayToastData(
    val id: Long,
    val message: String,
    val kind: TdayToastKind = TdayToastKind.INFO,
    val icon: ImageVector? = null,
    val autoDismissMillis: Long? = null,
    val onTap: (() -> Unit)? = null,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    // Set only for the Undo action (see UndoableDeleteCoordinator): renders the
    // action as an icon button instead of a text button. actionLabel is still
    // required in that case — it becomes the icon's contentDescription. A
    // @DrawableRes id (not an ImageVector) so non-Composable callers building a
    // SnackbarEvent can set it without calling vectorResource().
    @DrawableRes val actionIconRes: Int? = null,
)

@Composable
fun TdayToastHost(
    toast: TdayToastData?,
    onDismiss: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(toast?.id, toast?.autoDismissMillis) {
        if (toast == null) return@LaunchedEffect
        val autoDismissMillis = toast.autoDismissMillis ?: return@LaunchedEffect
        kotlinx.coroutines.delay(autoDismissMillis)
        onDismiss()
    }

    // `toast` is already null on the frame `visible` flips false, and AnimatedVisibility
    // keeps its content composed for the whole exit — that is the only reason an exit can
    // be seen at all. So a content lambda that reads `toast` empties the card on exactly
    // the frame the 140 ms fade and 180 ms slide begin, and both play over nothing. Hold
    // the toast that is on screen and draw that instead: it is the thing leaving.
    //
    // The write sits above the AnimatedVisibility so it happens before the only place that
    // reads it composes; nothing already composed sees the value change under it.
    val onScreenToast = remember { mutableStateOf<TdayToastData?>(null) }
    if (toast != null) {
        onScreenToast.value = toast
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(bottom = TOAST_BOTTOM_PADDING),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = TOAST_ENTER_FADE_DURATION_MS,
                    easing = LinearOutSlowInEasing,
                ),
            ),
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = TOAST_EXIT_FADE_DURATION_MS,
                    easing = FastOutLinearInEasing,
                ),
            ) + slideOutVertically(
                animationSpec = tween(
                    durationMillis = TOAST_EXIT_SLIDE_DURATION_MS,
                    easing = FastOutLinearInEasing,
                ),
                targetOffsetY = { it / 4 },
            ),
        ) {
            // Null only before the very first toast of the session, a state in which
            // `visible` is false and there is nothing to draw or to animate out anyway.
            val visibleToast = onScreenToast.value
            if (visibleToast != null) {
                TdayToastCard(
                    toast = visibleToast,
                    onDismiss = onDismiss,
                    hazeState = hazeState,
                )
            }
        }
    }
}

/**
 * One release of a toast drag: where the finger left the card, whether it asked
 * for the card to go away, and how fast it was travelling when it let go.
 */
internal data class TdayToastRelease(
    val fromPx: Float,
    val dismisses: Boolean,
    val initialVelocityPxPerSecond: Float,
)

/**
 * The decision a drag on a toast comes down to, kept out of the composable so
 * that something in this repository can check it.
 *
 * The defect this exists for is that there was no decision. Any downward travel
 * at all committed the dismiss — one pixel, a twitch on the way to the Undo
 * button — and the toast was thrown off the bottom of the screen and reported
 * gone. A dismissal now has to be asked for, by distance or by speed, and the
 * two questions are one method because they are one moment.
 *
 * A cancelled gesture goes through the same method, and that is deliberate
 * rather than convenient: `DragGestureNode` answers a cancellation by calling
 * `onDragStopped(Velocity.Zero)`, so a cancel and a release at rest are the same
 * event by the time they arrive here and no branch could tell them apart without
 * being told. Distance is what decides both, which is the right answer for the
 * cancel that actually happens — a parent claiming the pointer a few pixels in,
 * which is exactly the twitch above — and the honest limit of the mechanism: a
 * cancel arriving after the finger has already taken the card a third of the way
 * down is indistinguishable from that finger simply lifting there, and commits.
 *
 * Not a composable and holding no animation: the spring and the fly-off are run
 * for it by [TdayToastCard], the same division [TaskSwipeRevealState] draws
 * between deciding where a released surface is going and taking it there.
 */
@Stable
internal class TdayToastDismissState(private val fadeDistancePx: Float) {

    /**
     * Where the finger has the card, in px down from where it sits at rest.
     * Only meaningful while [isDragging]; the release animation owns the offset
     * from the moment the finger is off.
     */
    var offsetY by mutableFloatStateOf(0f)
        private set

    var isDragging by mutableStateOf(false)
        private set

    /** Picks the card up from wherever an unfinished settle had got it to. */
    fun startDragAt(offsetPx: Float) {
        isDragging = true
        offsetY = offsetPx.coerceAtLeast(0f)
    }

    /**
     * The gain is not a token and is not a fudge: the card outruns the thumb
     * slightly so that a dismissal feels thrown rather than dragged. It is
     * applied here rather than at the call site so that the distance the
     * threshold is measured against is the distance the user can see.
     *
     * Upward travel is clamped at rest rather than rubber-banded — there is
     * nothing above a toast to reveal, and a card that lifts away from the
     * bottom bar reads as a second, unrelated surface.
     */
    fun dragBy(deltaPx: Float) {
        isDragging = true
        offsetY = (offsetY + (deltaPx * TOAST_DRAG_GAIN)).coerceAtLeast(0f)
    }

    /**
     * The finger has gone. Answers what the card does about it.
     *
     * An upward flick outranks distance, and is the only thing that does: a user
     * who has taken the card down and then thrown it back is putting it away
     * from the dismissal, and honouring the distance they have already given up
     * would dismiss on the gesture that asked not to.
     */
    fun settle(velocityPxPerSecond: Float): TdayToastRelease {
        val liftOffOffsetY = offsetY
        val flungBack = velocityPxPerSecond < -TOAST_DISMISS_VELOCITY_PX_PER_SECOND
        val flungAway = velocityPxPerSecond > TOAST_DISMISS_VELOCITY_PX_PER_SECOND
        val draggedFarEnough =
            liftOffOffsetY > (fadeDistancePx * TOAST_DISMISS_THRESHOLD_FRACTION)
        isDragging = false
        // Back to rest, because the finger's offset stops drawing the card the
        // moment the finger is off it. Leaving the lift-off value here would
        // leave a number that nothing reads and that the next drag would have to
        // remember to overwrite.
        offsetY = 0f
        return TdayToastRelease(
            fromPx = liftOffOffsetY,
            dismisses = !flungBack && (flungAway || draggedFarEnough),
            initialVelocityPxPerSecond = velocityPxPerSecond,
        )
    }

    companion object {
        /**
         * The one spring a toast is allowed, and the one moment it is allowed
         * it: a refused dismissal going home.
         *
         * [TdayMotionTokens.Springs.gesture] is the token for a surface carrying
         * on under its own momentum after a finger lets go, and it is also
         * [TaskSwipeMotion.Release] to the digit — 0.82 / 340, converted from
         * iOS's `interactiveSpring(response: 0.34, dampingFraction: 0.82)`. That
         * equality is the point: a refused toast and a refused row are the same
         * refusal, and a user who has learned one has learned the other.
         *
         * Named here rather than written into the call site so that the choice is
         * something a unit test can read; there is no Compose harness on this
         * source set, and an animation spec passed inline is invisible to every
         * gate this repository has.
         */
        val Return: SpringSpec<Float> = TdayMotionTokens.Springs.gesture()
    }
}

@Composable
private fun TdayToastCard(
    toast: TdayToastData,
    onDismiss: () -> Unit,
    hazeState: HazeState,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val fadeDistancePx = with(LocalDensity.current) { TOAST_FADE_DISTANCE_DP.dp.toPx() }
    // Icons are removed app-wide. Every toast shares one neutral frosted surface
    // (matches iOS's `.ultraThinMaterial`) — no per-variant red tint, just text
    // over a strong Haze blur (hazeChild below).
    //
    // iOS uses `.ultraThinMaterial`: a strong blur with a *light, translucent*
    // tint that lets the colourful content behind bleed through. The Haze default
    // tint is the background colour at 70% alpha, which here is the near-black app
    // background — that's why the toast used to read as a flat opaque card. We
    // instead pass an explicit low-alpha surface tint and a larger blur radius so
    // the frost is translucent and the content shows through, exactly like iOS.
    val frostTint = HazeTint(colorScheme.surface.copy(alpha = if (isDark) 0.38f else 0.55f))
    // The accent now only colours the optional action-label button.
    val accentColor = when (toast.kind) {
        TdayToastKind.ERROR -> colorScheme.error
        TdayToastKind.SUCCESS -> TOAST_BRAND_ACCENT
        TdayToastKind.INFO -> TOAST_BRAND_ACCENT
    }
    val scope = rememberCoroutineScope()
    val onDismissState by rememberUpdatedState(onDismiss)
    val settleOffsetY = remember(toast.id) { Animatable(0f) }
    val dismissState = remember(toast.id, fadeDistancePx) { TdayToastDismissState(fadeDistancePx) }
    // How far the card has to travel to be off the screen, measured rather than
    // assumed. `detectDragGestures` handed this over as the pointer scope's own
    // `size`; `draggable` has no such scope, so the same node reports it here.
    var toastHeightPx by remember(toast.id) { mutableIntStateOf(0) }
    val tapModifier = toast.onTap?.let { onTap ->
        Modifier.clickable { onTap() }
    } ?: Modifier
    val displayedOffsetY =
        if (dismissState.isDragging) dismissState.offsetY else settleOffsetY.value
    val dragProgress = (displayedOffsetY / fadeDistancePx).coerceIn(0f, 1f)
    val toastAlpha = 1f - (dragProgress * 0.55f)
    val toastScale = 1f - (dragProgress * 0.03f)

    val toastShape = RoundedCornerShape(TOAST_CORNER_RADIUS)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .graphicsLayer {
                translationY = displayedOffsetY
                alpha = toastAlpha
                scaleX = toastScale
                scaleY = toastScale
            }
            .padding(vertical = TOAST_SHADOW_SPILL)
            // Drop shadow (matches the old Card elevation) + clip so the frosted
            // Haze backdrop and the translucent tint round to the toast shape.
            .shadow(elevation = if (isDark) 8.dp else 6.dp, shape = toastShape)
            .clip(toastShape)
            // Haze needs an explicit backgroundColor; without it the blur crashes
            // ("backgroundColor not specified") when a toast draws over content
            // that has no opaque backing (e.g. the offline toast). The light
            // `frostTint` + larger blur emulate iOS's `.ultraThinMaterial`; we no
            // longer paint an opaque surface fill on top (that flattened the blur
            // into a solid dark card).
            .hazeChild(
                state = hazeState,
                style = HazeDefaults.style(
                    backgroundColor = colorScheme.background,
                    tint = frostTint,
                    blurRadius = 30.dp,
                ),
            )
            .border(
                width = 1.dp,
                color = colorScheme.outlineVariant.copy(alpha = if (isDark) 0.52f else 0.72f),
                shape = toastShape,
            )
            // The drag below is the only way to send a toast away early, and a drag is
            // reachable by exactly one kind of user. This publishes the same escape as
            // ACTION_DISMISS, which is what TalkBack's dismiss gesture and Switch
            // Access's menu look for — the difference between "wait it out" and "put it
            // away" for anyone not driving the screen with a fingertip. It matters most
            // where there is nothing to wait out: a user whose accessibility timeout
            // asks for no timeout at all gets a toast that never leaves on its own (see
            // AccessibilityTimeout.kt), and this is how they close it. No label is
            // passed, so the platform's own localised "Dismiss" is announced rather than
            // a string this repo would have to translate into every locale to say the
            // same word.
            //
            // The live region is what makes the extra seconds worth anything. A toast
            // arriving is a newly composed subtree, which the framework reports as
            // TYPE_WINDOW_CONTENT_CHANGED and TalkBack does not speak; without this the
            // longer window buys a screen-reader user more time to reach a card nobody
            // told them was there, so it only helps if they happen to be exploring the
            // bottom of the screen when it lands. Polite rather than Assertive because
            // the delete they just made is still being read out and the toast is a
            // report of it, not an interruption of it — and because the Undo it carries
            // is an offer that holds for the whole window, not an alarm.
            //
            // Merging is what makes both of those reachable rather than merely present.
            // An un-merged container is not something a screen reader stops on — focus
            // would land on the message Text inside it and the dismiss would sit on a
            // node nobody visits, which is an accessibility affordance that exists only
            // in the source. Merged, the card is one stop that reads the message and
            // offers the dismiss. The Undo button keeps its own stop: `clickable` is
            // itself a merging node, and a merge does not reach through one.
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                dismiss { onDismissState(); true }
            }
            .onSizeChanged { toastHeightPx = it.height }
            // `draggable` rather than `detectDragGestures`, because a dismissal
            // has to be able to be flicked and `detectDragGestures` reports no
            // release velocity — the same reason every other swipe in this app
            // is built on it. Vertical-only is the second thing it buys: a
            // horizontal drag across a toast is now somebody swiping the screen
            // underneath, not a dismissal that happens to go sideways.
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> dismissState.dragBy(delta) },
                onDragStarted = {
                    dismissState.startDragAt(settleOffsetY.value)
                    scope.launch { settleOffsetY.stop() }
                },
                onDragStopped = { velocity ->
                    // Launched rather than awaited, and the decision goes inside
                    // the launch with the animation it decides. Awaited, this
                    // callback would hold `draggable` shut for the whole settle
                    // spring and the card would be deaf to a second grab until it
                    // got home. Decided outside it, `isDragging` would flip a
                    // dispatch before the Animatable was told where the finger
                    // left the card, and the frame in between would draw the card
                    // back at rest — a jump home, and then a spring home from
                    // where it already was.
                    scope.launch {
                        val release = dismissState.settle(velocity)
                        settleOffsetY.snapTo(release.fromPx)
                        if (release.dismisses) {
                            // The card leaves on the exit it already had, at a
                            // fixed length and accelerating away. The lift-off
                            // velocity is deliberately not carried into it: a
                            // tween has no use for one, and letting a flick
                            // leave faster than a slow drag is a retiming of the
                            // exit spec rather than a threshold fix. The cost is
                            // named rather than hidden — a hard flick hangs for
                            // a frame or two at lift-off before the card goes —
                            // and paying it is `toast-drag-two-stage-exit`.
                            settleOffsetY.animateTo(
                                targetValue = toastHeightPx.toFloat() * 1.15f,
                                animationSpec = tween(
                                    durationMillis = TOAST_DISMISS_DURATION_MS,
                                    easing = FastOutLinearInEasing,
                                ),
                            )
                            onDismissState()
                        } else {
                            // The lift-off velocity is carried into the spring so
                            // that a refused throw and its return are one
                            // movement rather than a stop and a restart.
                            settleOffsetY.animateTo(
                                targetValue = 0f,
                                animationSpec = TdayToastDismissState.Return,
                                initialVelocity = release.initialVelocityPxPerSecond,
                            )
                        }
                    }
                },
            )
            .then(tapModifier),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                Text(
                    text = toast.message,
                    modifier = Modifier.fillMaxWidth(),
                    color = colorScheme.onSurface,
                    // Centered horizontally to match iOS/web.
                    textAlign = TextAlign.Center,
                    // Match iOS's `.tdayRounded(.subheadline, weight: .bold)` — the
                    // app's rounded family at ~15sp bold, rather than the larger
                    // ExtraBold titleMedium which read heavier than iOS/web.
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            val actionLabel = toast.actionLabel
            val onAction = toast.onAction
            val actionIconRes = toast.actionIconRes
            if (actionLabel != null && onAction != null) {
                if (actionIconRes != null) {
                    // Undo: an icon button, not a text label (deliberate, signed-off
                    // reversal of this toast's "icons removed app-wide" rule for the
                    // Undo action specifically — see AppSnackbar's iOS counterpart).
                    IconButton(
                        onClick = {
                            onAction()
                            onDismiss()
                        },
                    ) {
                        Icon(
                            painter = painterResource(actionIconRes),
                            contentDescription = actionLabel,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                } else {
                    TextButton(
                        onClick = {
                            onAction()
                            onDismiss()
                        },
                    ) {
                        Text(
                            text = actionLabel,
                            color = accentColor,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}
