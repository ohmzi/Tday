package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp

/**
 * The rule a task row draws across its own text while the task is being checked
 * off — the second beat of the completion choreography the three clients share.
 *
 * Compose's `TextDecoration.LineThrough` is the obvious way to write this and is
 * the wrong one: it is a boolean. The rule is either absent or fully painted on
 * the next frame, so the beat the user is meant to watch — the one that says
 * *this task is done* — was the only beat of the sequence with no motion in it
 * at all, while the fade either side of it was tweened. Web has the same problem
 * and solves it the same way, by animating the decoration rather than toggling
 * it (`.task-strike` in `globals.css` runs `text-decoration-color` from
 * transparent); iOS animates `.strikethrough()` through `withAnimation`. This is
 * Android's half of that agreement.
 *
 * **Emphasis, not Change.** The rule grows across the text, and `docs/motion.md`'s
 * second idiom rule decides that boundary by geometry rather than by importance:
 * something that changes how big it is takes the longer rung. The row's title
 * colour travels alongside it on the same rung for a related reason — the two
 * are one event, and a colour that settled first would read as two.
 *
 * **Why it is drawn per line.** A single rule down the middle of the block is
 * what the swipe rows used to carry, and on a wrapped title it lands in the gap
 * between two lines. Every row that shows more than one line of title therefore
 * gets one rule per line, all sweeping together, which is the same thing the
 * font's own `line-through` would have drawn had it been animatable.
 */

/**
 * How far across the text the rule has travelled, 0f to 1f.
 *
 * Reduced motion is answered here rather than at the call site so that no row can
 * forget it: with the animator scale at zero the row is handed the finished
 * frame — fully struck, or not struck at all — instead of the start of a sweep
 * it will never be shown the end of. That is `docs/motion.md`'s fifth idiom rule,
 * and a half-drawn rule is exactly the "looks broken rather than deliberate"
 * failure it exists to prevent.
 *
 * @param struck whether the task is currently marked done.
 * @param label the animation's name in Compose's tooling, per call site.
 * @return the sweep progress to hand [taskStrikethrough].
 */
@Composable
fun rememberTaskStrikeProgress(struck: Boolean, label: String): Float {
    if (!rememberTdayMotionEnabled()) {
        return if (struck) 1f else 0f
    }
    val progress by animateFloatAsState(
        targetValue = if (struck) 1f else 0f,
        animationSpec = tween(
            durationMillis = TdayMotionTokens.Durations.Emphasis,
            easing = TdayMotionTokens.Easings.Standard,
        ),
        label = label,
    )
    return progress
}

/**
 * Draws the rule over the text this modifier is applied to.
 *
 * Takes the [TextLayoutResult] the `Text` reports through `onTextLayout` rather
 * than measuring anything itself: line boxes are the layout's answer to give,
 * and a second opinion computed from the block's height is what put the old
 * single rule in the wrong place on a wrapped title.
 *
 * @param progress how far the sweep has got, from [rememberTaskStrikeProgress].
 * @param layout the title's last reported layout, or null before the first pass.
 * @param color the rule's colour — the caller's, because a row that dims its
 *   title as it strikes wants the rule to dim with it.
 * @param thickness the rule's stroke width.
 * @return the modifier, with the rule drawn over the text's own content.
 */
fun Modifier.taskStrikethrough(
    progress: Float,
    layout: TextLayoutResult?,
    color: Color,
    thickness: Dp,
): Modifier = drawWithContent {
    drawContent()
    if (progress <= 0f) return@drawWithContent
    val lines = layout?.lineCount ?: return@drawWithContent
    val stroke = thickness.toPx()
    for (line in 0 until lines) {
        val left = layout.getLineLeft(line).coerceIn(0f, size.width)
        val right = layout.getLineRight(line).coerceIn(left, size.width)
        // 0.56 of the line box rather than its exact middle: a line-through sits
        // slightly below centre, because the glyphs it crosses hang above the
        // baseline and a centred rule reads as underlining the row above it.
        val top = layout.getLineTop(line)
        val y = top + (layout.getLineBottom(line) - top) * 0.56f
        drawLine(
            color = color,
            start = Offset(left, y),
            end = Offset(left + (right - left) * progress, y),
            strokeWidth = stroke,
        )
    }
}
