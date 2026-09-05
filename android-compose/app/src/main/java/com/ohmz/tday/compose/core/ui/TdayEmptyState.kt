package com.ohmz.tday.compose.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.ui.theme.TdayDimens
import kotlinx.coroutines.delay

/**
 * What a screen shows when it has nothing to show.
 *
 * Every one of these used to be a single line of grey text in the middle of an
 * otherwise blank screen, which reads as a page that failed to load rather than
 * as one that is simply empty. This gives the state something to look at: a
 * little stack of cards with the screen's own glyph on top, tinted with that
 * screen's accent so the empty view still tells you where you are.
 *
 * The scene is the web one (`tday-web/src/components/app/EmptyState.tsx`) drawn
 * again in Compose, its px taken as dp so the two read as the same illustration;
 * keep the platforms in step.
 *
 * It never cuts in. The scene rises and fades up over half a second, because
 * the frame before it is the row the user just ticked off leaving the screen,
 * and a state that simply appears in that gap reads as the list breaking rather
 * than as the list being finished.
 *
 * @param icon a drawable rather than an `ImageVector`, because every caller
 *   already holds one of the shared lucide ids for [EmptyTaskWatermark].
 * @param accentColor the screen's accent. Everything in the scene but the front
 *   card is this colour at some alpha.
 * @param celebrate the list emptied because the user finished it, rather than
 *   because there was never anything in it: confetti flies first and the scene
 *   comes up through it a beat later.
 */
@Composable
fun TdayEmptyState(
    @DrawableRes icon: Int,
    accentColor: Color,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    action: (@Composable () -> Unit)? = null,
    celebrate: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motion = rememberEmptySceneMotion()
    val motionEnabled = rememberTdayMotionEnabled()

    // 0 is off-screen-ish and invisible, 1 is the finished state. Held at 1 from
    // the start when the platform has animations off, so the scene is drawn, not
    // faded to nothing.
    val appear = remember { Animatable(if (motionEnabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!motionEnabled) return@LaunchedEffect
        // The burst leads; the scene follows through it. Without the wait the
        // illustration is already sitting there when the first piece of paper
        // clears it, and the confetti reads as decoration on a static page.
        if (celebrate) delay(TdayEmptyStateCelebrationLeadMillis)
        appear.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = EnterMillis, easing = EnterEasing),
        )
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val entered = appear.value
                    alpha = entered
                    val scale = EnterStartScale + (1f - EnterStartScale) * entered
                    scaleX = scale
                    scaleY = scale
                    translationY = EnterRise.toPx() * (1f - entered)
                }
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    // Decoration only, and a screen reader announcing three cards
                    // and a stack of sparkles would be reading out the wallpaper.
                    .clearAndSetSemantics { }
                    .size(width = SceneWidth, height = SceneHeight)
                    .graphicsLayer {
                        // The whole scene breathes rather than any one piece of it
                        // moving: a stack of cards where only the sparkles animated
                        // reads as a loading spinner, which is the one thing an
                        // empty state must not look like.
                        translationY = -SceneFloatDistance.toPx() * motion.float()
                    },
            ) {
                // Two cards behind, fanned out. Tinted rather than a surface token,
                // so they read as depth instead of as two more empty rows.
                Box(
                    modifier = Modifier
                        .offset(x = 12.dp, y = 16.dp)
                        .size(width = 128.dp, height = 86.dp)
                        .graphicsLayer { rotationZ = -9f }
                        .background(accentColor.copy(alpha = 0.14f), CardShape),
                )
                Box(
                    modifier = Modifier
                        .offset(x = 40.dp, y = 12.dp)
                        .size(width = 128.dp, height = 86.dp)
                        .graphicsLayer { rotationZ = 7f }
                        .background(accentColor.copy(alpha = 0.22f), CardShape),
                )

                // The card in front, holding three task rows. The first is ticked.
                Column(
                    modifier = Modifier
                        .offset(x = 24.dp, y = 24.dp)
                        .size(width = 130.dp, height = 88.dp)
                        .shadow(elevation = 10.dp, shape = CardShape, clip = false)
                        // NOT `colorScheme.surface`: under Material You that is the
                        // same value as `background`, so the card the whole scene is
                        // stacked around would vanish into the page and leave three
                        // rows floating. This is the fill every raised control in
                        // the app already uses for exactly that reason.
                        .background(tdayBarButtonContainerColor(), CardShape)
                        .border(
                            width = TdayDimens.BorderWidth,
                            color = colorScheme.onSurface.copy(alpha = 0.10f),
                            shape = CardShape,
                        )
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically),
                ) {
                    EmptyCardRow(accentColor = accentColor, ticked = true, pillWidth = 44.dp)
                    EmptyCardRow(accentColor = accentColor, ticked = false, pillWidth = 62.dp)
                    EmptyCardRow(accentColor = accentColor, ticked = false, pillWidth = 38.dp)
                }

                // The screen's own glyph, sitting on the corner of the stack. The
                // ring is the page's own colour, so the circle reads as lifted off
                // the cards rather than as another card in the fan.
                Box(
                    modifier = Modifier
                        // The circle hangs 4dp past the stack's right edge and
                        // stands 4dp up from its bottom; the ring is drawn around
                        // it, so this places the ring's box, not the circle's.
                        .offset(x = 120.dp, y = 76.dp)
                        .size(GlyphCircle + GlyphRing * 2)
                        .background(colorScheme.background, CircleShape)
                        .padding(GlyphRing)
                        .shadow(elevation = 12.dp, shape = CircleShape, clip = false)
                        .background(accentColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }

                // Three sparkles on their own staggered twinkle.
                EmptySparkles.forEachIndexed { index, spark ->
                    Canvas(
                        modifier = Modifier
                            .offset(x = spark.x, y = spark.y)
                            .size(spark.size)
                            .graphicsLayer {
                                val fraction = motion.sparkle(index)
                                alpha = SparkleMinAlpha + (1f - SparkleMinAlpha) * fraction
                                val s = SparkleMinScale + (1f - SparkleMinScale) * fraction
                                scaleX = s
                                scaleY = s
                            },
                    ) {
                        drawPath(sparklePath(size.minDimension), accentColor.copy(alpha = 0.70f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = CopyMaxWidth),
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = CopyMaxWidth),
                )
            }
            if (action != null) {
                Spacer(modifier = Modifier.height(24.dp))
                action()
            }
        }

        // Over the whole state rather than over the scene alone: the pieces
        // are thrown far enough to cross the copy, and a burst that stops at
        // the illustration's edge looks masked.
        TdayConfetti(
            play = celebrate,
            accentColor = accentColor,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/** One of the front card's three task rows: a state circle and a title pill. */
@Composable
private fun EmptyCardRow(accentColor: Color, ticked: Boolean, pillWidth: Dp) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (ticked) {
            Canvas(
                modifier = Modifier
                    .size(RowCircle)
                    .background(accentColor, CircleShape),
            ) {
                val unit = size.minDimension / 12f
                // The tick is drawn at two thirds of the circle, which is where
                // the web scene's 8px glyph sits inside its 12px dot — a lucide
                // check scaled this far down draws a visibly thinner stroke.
                val glyph = unit * (8f / 12f)
                val inset = unit * 2f
                drawPath(
                    path = Path().apply {
                        moveTo(inset + 3f * glyph, inset + 6.2f * glyph)
                        lineTo(inset + 5f * glyph, inset + 8.2f * glyph)
                        lineTo(inset + 9f * glyph, inset + 3.9f * glyph)
                    },
                    color = Color.White,
                    style = Stroke(
                        width = 2f * glyph,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(RowCircle)
                    .border(TdayDimens.BorderWidthThick, accentColor.copy(alpha = 0.45f), CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .width(pillWidth)
                .height(5.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = if (ticked) 0.30f else 0.52f)),
        )
    }
}

/** The web scene's four-pointed sparkle, its 24-unit path scaled to [side]. */
private fun sparklePath(side: Float): Path {
    val u = side / 24f
    return Path().apply {
        moveTo(12f * u, 0f)
        relativeCubicTo(0.6f * u, 6.2f * u, 5.2f * u, 10.8f * u, 12f * u, 12f * u)
        relativeCubicTo(-6.8f * u, 1.2f * u, -11.4f * u, 5.8f * u, -12f * u, 12f * u)
        relativeCubicTo(-0.6f * u, -6.2f * u, -5.2f * u, -10.8f * u, -12f * u, -12f * u)
        cubicTo(6.8f * u, 10.8f * u, 11.4f * u, 6.2f * u, 12f * u, 0f)
        close()
    }
}

private val SceneWidth = 172.dp
private val SceneHeight = 136.dp
private val SceneFloatDistance = 7.dp
private val CardShape = RoundedCornerShape(18.dp)
private val RowCircle = 12.dp
private val GlyphCircle = 52.dp
private val GlyphRing = 4.dp
private val CopyMaxWidth = 320.dp

private const val SparkleMinAlpha = 0.3f
private const val SparkleMinScale = 0.7f

/** The scene's own arrival: long enough to read as a hand-off, not as a page load. */
private const val EnterMillis = 520

/** Material's emphasised decelerate: fast off the mark, settles rather than stops. */
private val EnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

private const val EnterStartScale = 0.92f
private val EnterRise = 18.dp

/**
 * How long the confetti has the screen to itself before the scene comes up.
 *
 * Public because a feed that draws this scene inline rather than as an overlay
 * has to move its own layout out of the way inside this window — see
 * [TdayFeedItemMotion].
 */
const val TdayEmptyStateCelebrationLeadMillis = 320L

/** Half of the float's 6s cycle; [RepeatMode.Reverse] plays the other half. */
private const val SceneFloatMillis = 3000

/** Half of the twinkle's 2.8s cycle. */
private const val SparkleTwinkleMillis = 1400

private class EmptySparkle(val x: Dp, val y: Dp, val size: Dp, val delayMillis: Int)

private val EmptySparkles = listOf(
    EmptySparkle(x = 6.dp, y = 2.dp, size = 13.dp, delayMillis = 0),
    EmptySparkle(x = 0.dp, y = 96.dp, size = 9.dp, delayMillis = 700),
    EmptySparkle(x = 150.dp, y = 12.dp, size = 11.dp, delayMillis = SparkleTwinkleMillis),
)

/**
 * The scene's two animations, each 0..1.
 *
 * Handed over as lambdas rather than as values so a frame of the float
 * invalidates only the layer that reads it, never the whole empty state — the
 * same reason [TdayHeroTitleCollapse] takes one.
 */
private class EmptySceneMotion(
    val float: () -> Float,
    val sparkle: (index: Int) -> Float,
)

/**
 * Both animations, or neither.
 *
 * [rememberTdayMotionEnabled] is the platform's answer to `prefers-reduced-motion`.
 * When it says no, the scene is held at the TOP of the twinkle rather than switched off —
 * a scene drawn at the bottom of its fade looks half-rendered rather than
 * deliberate, which is the same call the web stylesheet makes.
 */
@Composable
private fun rememberEmptySceneMotion(): EmptySceneMotion {
    if (!rememberTdayMotionEnabled()) {
        return remember { EmptySceneMotion(float = { 0f }, sparkle = { 1f }) }
    }

    val transition = rememberInfiniteTransition(label = "tdayEmptyScene")
    val float = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SceneFloatMillis, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tdayEmptyFloat",
    )
    val sparkles: List<State<Float>> = EmptySparkles.map { spark ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = SparkleTwinkleMillis, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse,
                // Staggered, so the three do not pulse as one blinking cursor.
                initialStartOffset = StartOffset(spark.delayMillis, StartOffsetType.FastForward),
            ),
            label = "tdayEmptySparkle",
        )
    }

    return remember {
        EmptySceneMotion(
            float = { float.value },
            sparkle = { index -> sparkles[index].value },
        )
    }
}
