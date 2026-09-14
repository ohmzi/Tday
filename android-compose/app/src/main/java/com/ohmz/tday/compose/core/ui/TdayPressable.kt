package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A surface going down under a finger: how far it sinks, and on whose clock.
 *
 * Android writes this by hand seventeen times. Every copy is the same three
 * statements in the same order — read the press, animate a scale, animate an
 * offset, then `.offset(y = …).graphicsLayer { scaleX = …; scaleY = … }` — so
 * the copies agree about the shape and disagree about the number, which is how
 * one app ended up pressing to five different depths
 * (`docs/motion/LEDGER.md`'s `press-affordance-unification`).
 * [Modifier.tdayPressable] is that shape written once, with the depth left as
 * an argument so a call site can name the class its surface belongs to instead
 * of inventing a number for it.
 *
 * There is no scale constant in this file, deliberately. A press depth is
 * [TdayMotionTokens.PressScales] and nothing else — `Bar` for a round bar
 * button, `Card` for a card or tile, `Row` for a full-width row. The default is
 * `Card`, the middle of the three: a call site that has not thought about which
 * class its surface is gets the middle depth rather than the deepest, and the
 * two ends both have to be asked for. It is NOT the commonest number in the
 * tree — that is 0.93, which is not on a token at all and is what the seventeen
 * are being migrated off.
 */
object TdayPress {

    /**
     * How far a pressed surface drops.
     *
     * Not a retune. Fourteen of the fifteen Android press offsets already
     * write `2.dp` and the fifteenth writes `1.dp`
     * (`ui/component/TdaySheetChrome.kt:451`), so the default is the majority
     * and the sheet button passes its own. iOS splits the same way between its
     * two shared modifiers: `TdayPressEffectModifier` offsets 2 pt
     * (`ios-swiftUI/Tday/Core/UI/TaskFloatingActionButton.swift:225`) and
     * `TdayToolbarButtonEffectModifier` offsets 1 pt (`:168`).
     *
     * `docs/motion.md` has a durations ladder, an easings table and a press-scale
     * table, and no offset family at all. Inventing one here — a `SinkOffset`
     * that every site then has to be argued onto — would be a fourth vocabulary
     * nothing in the programme asked for, so this is a constant for the copies to
     * share and not a token for anybody to migrate onto.
     */
    val SinkOffset: Dp = 2.dp

    /**
     * What a press draws right now: the animation's value, or the destination
     * when motion is off.
     *
     * The reduced-motion answer for a press is the pressed state arriving
     * instantly and leaving instantly — not a shortened tween, which is the call
     * `globals.css`'s `@layer tday-press` makes with its `0.01ms` floor and the
     * call [TdayDragLift.spec] makes with its `snap()`. What is removed is the
     * trip; the destination is exactly as far down as it ever was.
     *
     * Generic, and read once for the scale and once for the offset, so the two
     * cannot come apart: a surface that has sunk 2 dp without shrinking is not a
     * pressed surface, it is a misaligned one.
     *
     * @param animated Where the animation has got to.
     * @param target Where the press is going.
     * @param motionEnabled Whether decorative motion should play at all.
     */
    fun <T> shown(animated: T, target: T, motionEnabled: Boolean): T =
        if (motionEnabled) animated else target
}

/**
 * The press affordance: sink and squash while a finger is down, on this app's
 * one press vocabulary.
 *
 * Apply it to the same surface that owns [interactionSource] — a `Card`, a
 * `Surface`, anything with an `onClick` — and hand that surface the same source,
 * or the modifier will animate a press nothing is having.
 *
 * **No `animationSpec`, on purpose.** The copies this replaces pass none
 * (`core/ui/CategoryCard.kt:58`, `feature/scheduledtaskhome/ScheduledTaskHomeScreen.kt:251`
 * and `:2099` among them), and `animateFloatAsState` cannot be handed
 * the spec it would otherwise use: its default is a private value it compares by
 * identity, and a hand-written `spring()` that looks the same is a *different*
 * spec — it loses the `visibilityThreshold` the call applies only to its own.
 * So naming a spec here, token'd tween included, would not be unifying the
 * seventeen copies onto one curve; it would be quietly retiming all of them.
 * Leave this argument absent.
 *
 * **Which half of reduced motion this handles.** Compose reads the device's
 * animator scale itself — it is the `MotionDurationScale` in the recomposer's
 * context, so at 0x both animations below already land in one frame with nothing
 * written for them. What it cannot see is this app's own switch, which no
 * composable can substitute that context for (`effectiveMotionScale`'s doc has
 * the argument). [TdayPress.shown] is therefore exactly the missing half, and
 * reads the target rather than gating the animation away: a conditional
 * `animate*AsState` would sit in a different composition group and throw its
 * state on the floor every time the preference flipped, mid-press included.
 *
 * @param interactionSource The source the pressed surface is composed with.
 * @param scale How far this surface squashes — a [TdayMotionTokens.PressScales]
 *   value, by surface class.
 * @param offsetY How far it drops. [TdayPress.SinkOffset] unless the surface has
 *   an argument for its own.
 * @param enabled Whether the surface responds to a press at all. A disabled
 *   surface stays at rest however hard it is held.
 */
@Composable
fun Modifier.tdayPressable(
    interactionSource: MutableInteractionSource,
    scale: Float = TdayMotionTokens.PressScales.Card,
    offsetY: Dp = TdayPress.SinkOffset,
    enabled: Boolean = true,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val motionEnabled = rememberTdayMotionEnabled()
    val sunk = pressed && enabled

    val targetScale = if (sunk) scale else 1f
    val targetOffset = if (sunk) offsetY else 0.dp
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        label = "tdayPressableScale",
    )
    val animatedOffset by animateDpAsState(
        targetValue = targetOffset,
        label = "tdayPressableOffsetY",
    )

    val drawnScale = TdayPress.shown(animatedScale, targetScale, motionEnabled)
    val drawnOffset = TdayPress.shown(animatedOffset, targetOffset, motionEnabled)

    // Offset before the layer, which is the order every copy uses: the
    // drop is laid out in the parent's space and the squash is drawn about the
    // surface's own centre. Swap them and the offset is scaled too, so a pressed
    // card drops fractionally less far than a pressed row for no reason anybody
    // chose.
    return this
        .offset(y = drawnOffset)
        .graphicsLayer {
            scaleX = drawnScale
            scaleY = drawnScale
        }
}
