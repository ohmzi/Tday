package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.random.Random

/**
 * The burst that plays when the user ticks off the last thing they had left.
 *
 * Deliberately not a library and not a bitmap: a few dozen rounded rectangles on
 * one [Canvas] is the whole effect. They leave a thumb-sized patch at the scene's
 * heart, the throw is spent against drag so each one has a finite reach rather
 * than a straight line out of the box, and the fall settles to that piece's own
 * terminal speed instead of gathering pace forever. On the way down each turns
 * edge-on at a rate of its own, unrelated to how fast it is spinning in the plane
 * — that separation is what reads as paper rather than as a coloured propeller.
 *
 * This file is the clock, the palette and the draw. [TdayConfettiKinematics] is
 * the arithmetic, and it is a separate file because a `Canvas` cannot be asked
 * what it drew — the numbers are the part that can be wrong, so they live
 * somewhere a plain JUnit test can reach them. `docs/confetti-spec.md` is
 * normative for both halves.
 *
 * The twin of the web `Confetti` component and the iOS `TdayConfetti` view; the
 * three share piece count, fan, timing, physics and palette so completing a list
 * feels the same wherever the user does it.
 *
 * This draws outside its own bounds on purpose (pieces fly above the scene it
 * sits on), so give it a parent that does not clip — `matchParentSize` on a
 * [androidx.compose.foundation.layout.Box] is the intended placement, which also
 * keeps it out of the layout pass entirely.
 *
 * @param play flipping this to `true` starts one run; it never repeats on its
 *   own, and a caller that wants a second burst passes a new [runKey].
 * @param runKey any value that identifies the run — changing it while [play] is
 *   `true` restarts the burst.
 * @param accentColor the screen's own accent, mixed into the palette so the
 *   celebration still belongs to the list it happened on.
 * @param startDelayMillis how long after [play] turns true before the first
 *   piece is thrown. Zero when the burst can have the frame it was triggered
 *   on. A caller whose layout is still settling on that frame passes the time
 *   that settling takes: paper thrown over content that is still sliding reads
 *   as a dropped frame, not as a celebration.
 */
@Composable
fun TdayConfetti(
    play: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    runKey: Any? = Unit,
    startDelayMillis: Long = 0L,
) {
    if (!play) return

    val motionEnabled = rememberTdayMotionEnabled()
    val motionScale = rememberTdayMotionScale()

    // Fixed per run, so a recomposition mid-flight does not re-roll the pieces
    // and teleport all of them at once.
    val pieces = remember(runKey) { confettiFan(Random(PieceCount * 31L)) }
    val progress = remember(runKey) { Animatable(0f) }
    val palette = remember(accentColor) { ConfettiPalette + accentColor }

    // `play` is the only thing that decides whether this composable exists; the
    // preference decides whether the Canvas below it draws. The two are not the
    // same question, and collapsing them into one early return — which is what
    // this used to do — puts the whole run out of reach at 0x, effect included.
    // `docs/confetti-spec.md`'s haptic section is written against the shape this
    // leaves behind: a burst is an event as well as an animation, and the event
    // still happens for someone who has asked not to watch it.
    LaunchedEffect(runKey) {
        progress.snapTo(0f)
        // Held at 0, where the canvas below draws nothing at all, so the wait
        // costs a composition and not a frame of half-drawn paper. And held on the
        // animator's clock, like the flight it leads: the caller times this lead
        // against the scene rising behind it, so a lead that did not stretch with
        // the burst would fire it into a scene that has not started moving yet.
        if (startDelayMillis > 0L) scaledDelay(startDelayMillis, motionScale)
        if (!motionEnabled) return@LaunchedEffect
        progress.animateTo(
            targetValue = 1f,
            // Linear: the arc is the physics in TdayConfettiKinematics, and an
            // eased clock on top of it makes the pieces hang at the apex like they
            // are buffering.
            animationSpec = tween(durationMillis = FlightMillis, easing = LinearEasing),
        )
    }

    if (!motionEnabled) return

    Canvas(modifier = modifier) {
        val t = progress.value
        if (t <= 0f || t >= 1f) return@Canvas

        // Everything is thrown in fractions of the box's WIDTH — not of its
        // longest side, which is the screen's height on a phone and throws every
        // piece clean off the sides before it can be seen.
        val span = size.width
        val origin = Offset(size.width * OriginX, size.height * OriginY)

        pieces.forEach { piece ->
            val f = frame(piece, t) ?: return@forEach

            val x = origin.x + f.dx * span
            val y = origin.y + f.dy * span
            // Only the width turns: the height is the piece seen along the axis it
            // is flipping about, which does not foreshorten.
            val width = piece.width.dp.toPx() * f.widthScale
            val height = piece.height.dp.toPx()

            rotate(degrees = f.rot * DegreesPerRadian, pivot = Offset(x, y)) {
                drawRoundRect(
                    color = palette[piece.colorIndex % palette.size].copy(alpha = f.alpha),
                    topLeft = Offset(x - width / 2f, y - height / 2f),
                    size = Size(width, height),
                    // Of the DRAWN width, so a piece turning edge-on keeps its
                    // proportions instead of squaring off as it narrows.
                    cornerRadius = CornerRadius(width * 0.4f, width * 0.4f),
                )
            }
        }
    }
}

/**
 * A festive subset of the list palette rather than a new set of colours, so the
 * burst is made of shades the app already uses; the screen's accent is appended
 * by the caller, which is what makes the drawn array [ColorCount] long and the
 * fan's `colorIndex` draw an index into it.
 */
private val ConfettiPalette = listOf(
    Color(0xFFE05299), // PINK
    Color(0xFFE8A530), // GOLD
    Color(0xFF3C9ADD), // DEEP_BLUE
    Color(0xFF2EB8AC), // TEAL
    Color(0xFF46B963), // LIME
    Color(0xFF7D67B6), // PURPLE
    Color(0xFFE6664C), // CORAL
)

/** The one place degrees exist: the kinematics are radians end to end. */
private const val DegreesPerRadian = (180.0 / PI).toFloat()
