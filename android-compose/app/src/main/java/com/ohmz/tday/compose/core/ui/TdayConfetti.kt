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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The burst that plays when the user ticks off the last thing they had left.
 *
 * Deliberately not a library and not a bitmap: a few dozen rounded rectangles
 * on one [Canvas], thrown from a single point and pulled back down, is the whole
 * effect. Pieces flip as they fly — the width is scaled by the cosine of their
 * own spin — which is what reads as paper rather than as coloured dots.
 *
 * The twin of the web `Confetti` component and the iOS `TdayConfetti` view; the
 * three share piece count, fan, timing and palette so completing a list feels
 * the same wherever the user does it.
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
 */
@Composable
fun TdayConfetti(
    play: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    runKey: Any? = Unit,
) {
    val motionEnabled = rememberTdayMotionEnabled()
    if (!play || !motionEnabled) return

    // Fixed per run, so a recomposition mid-flight does not re-roll the pieces
    // and teleport all of them at once.
    val pieces = remember(runKey) { confettiPieces(Random(PieceCount * 31L)) }
    val progress = remember(runKey) { Animatable(0f) }
    val palette = remember(accentColor) { ConfettiPalette + accentColor }

    LaunchedEffect(runKey) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            // Linear: the arc is the physics below, and an eased clock on top of
            // it makes the pieces hang at the apex like they are buffering.
            animationSpec = tween(durationMillis = FlightMillis, easing = LinearEasing),
        )
    }

    Canvas(modifier = modifier) {
        val t = progress.value
        if (t <= 0f || t >= 1f) return@Canvas

        // Everything is thrown in fractions of the box's WIDTH — not of its
        // longest side, which is the screen's height on a phone and throws every
        // piece clean off the sides before it can be seen.
        val span = size.width
        val origin = Offset(size.width * OriginX, size.height * OriginY)

        pieces.forEach { piece ->
            // Staggered launches: one salvo of forty pieces reads as a single
            // expanding ring rather than as confetti.
            val local = (t - piece.delay) / (1f - piece.delay)
            if (local <= 0f) return@forEach

            val travelled = piece.speed * local
            val x = origin.x + cos(piece.angle) * travelled * span
            val y = origin.y +
                sin(piece.angle) * travelled * span +
                Gravity * local * local * span

            // Full opacity for the first half of the flight, then out — pieces
            // that vanish at the apex look like a dropped frame.
            val alpha = if (local < FadeStart) {
                1f
            } else {
                1f - (local - FadeStart) / (1f - FadeStart)
            }

            val spin = piece.spinPhase + piece.spin * local
            // |cos| of the spin is the piece turning edge-on to the viewer; the
            // floor keeps it from disappearing completely on the way round.
            val flip = MinFlip + (1f - MinFlip) * abs(cos(spin))
            val width = piece.width.dp.toPx() * flip
            val height = piece.height.dp.toPx()

            rotate(degrees = spin * DegreesPerRadian, pivot = Offset(x, y)) {
                drawRoundRect(
                    color = palette[piece.colorIndex % palette.size].copy(alpha = alpha),
                    topLeft = Offset(x - width / 2f, y - height / 2f),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(width * 0.4f, width * 0.4f),
                )
            }
        }
    }
}

/** One piece of paper: thrown in fractions of the burst box, sized in dp. */
private class ConfettiPiece(
    val angle: Float,
    val speed: Float,
    val spin: Float,
    val spinPhase: Float,
    val width: Float,
    val height: Float,
    val colorIndex: Int,
    val delay: Float,
)

/**
 * The fan, rolled from a fixed seed: the burst is the same every time, which is
 * what makes it read as a designed celebration instead of a random one, and what
 * lets a screenshot test see the same frame twice.
 */
private fun confettiPieces(random: Random): List<ConfettiPiece> = List(PieceCount) { index ->
    // Fanned upward and outward rather than in a full circle. A ring throws half
    // its pieces straight down through the copy, where they read as a glitch.
    val spread = FanStartRadians + (FanSweepRadians * (index + random.nextFloat()) / PieceCount)
    ConfettiPiece(
        angle = spread,
        speed = MinSpeed + random.nextFloat() * (MaxSpeed - MinSpeed),
        spin = (if (random.nextBoolean()) 1f else -1f) * (MinSpin + random.nextFloat() * MaxSpin),
        spinPhase = random.nextFloat() * TwoPi,
        width = MinPieceWidthDp + random.nextFloat() * (MaxPieceWidthDp - MinPieceWidthDp),
        height = MinPieceHeightDp + random.nextFloat() * (MaxPieceHeightDp - MinPieceHeightDp),
        colorIndex = random.nextInt(ConfettiPalette.size + 1),
        delay = random.nextFloat() * MaxLaunchDelay,
    )
}

/**
 * A festive subset of the list palette rather than a new set of colours, so the
 * burst is made of shades the app already uses; the screen's accent is appended
 * by the caller.
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

private const val PieceCount = 46
private const val FlightMillis = 1800

/** Where the burst is thrown from, as a fraction of the box: the scene's heart. */
private const val OriginX = 0.5f
private const val OriginY = 0.28f

private const val TwoPi = (Math.PI * 2).toFloat()
private const val DegreesPerRadian = (180.0 / Math.PI).toFloat()

/** Up and out: 200°..340°, measured with y growing downward. */
private const val FanStartRadians = (Math.PI * 200.0 / 180.0).toFloat()
private const val FanSweepRadians = (Math.PI * 140.0 / 180.0).toFloat()

private const val MinSpeed = 0.30f
private const val MaxSpeed = 0.78f
private const val Gravity = 0.95f

private const val MinSpin = 3.5f
private const val MaxSpin = 9f
private const val MinFlip = 0.25f

private const val MinPieceWidthDp = 5f
private const val MaxPieceWidthDp = 9f
private const val MinPieceHeightDp = 8f
private const val MaxPieceHeightDp = 13f

private const val MaxLaunchDelay = 0.16f
private const val FadeStart = 0.55f
