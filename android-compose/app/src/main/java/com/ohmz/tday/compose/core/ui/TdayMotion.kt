package com.ohmz.tday.compose.core.ui

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

/**
 * How fast the platform is running animations, as a multiplier on every duration.
 *
 * Compose has no equivalent of `prefers-reduced-motion`, so this reads what Android
 * actually offers: the animator duration scale, which is what Settings' "Remove
 * animations" and the developer-options slider both write. A *scale* and not a
 * switch, because that is what the user is given — 0x, 0.5x, 1x, 2x, 5x, 10x — and
 * treating it as a boolean throws away four of those six.
 *
 * Compose already reads the same setting for the animations themselves: an
 * `animateTo` or an `animate*AsState` runs against a `MotionDurationScale` in its
 * coroutine context, so at 0x it lands on its target in one frame and at 2x it takes
 * twice as long. A `delay()` does not. That asymmetry is the whole reason this file
 * exists, and it breaks `docs/motion.md`'s fifth idiom rule from both ends: at 0x a
 * choreography's waits survive the motion they were covering, so the user sits
 * through the timing of an animation nobody is playing, and at 2x the waits fire
 * early and the animation they were meant to lead is cut off mid-flight. The cure is
 * [scaledDelay], which puts the gaps in the same clock as the motions either side of
 * them.
 */
val LocalTdayMotionScale = compositionLocalOf<Float?> { null }

/** What the platform reports when nobody has moved the slider, and our fallback. */
private const val UNSCALED = 1f

/**
 * Reads the scale right now.
 *
 * Falls back to "motion is fine" wherever the question cannot be answered, which is
 * the same way round `prefersReducedMotion.ts` falls on web: a device that cannot
 * report the setting gets the animated build, not a permanently still one.
 */
private fun readAnimatorScale(resolver: ContentResolver): Float =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, UNSCALED)

/**
 * Publishes the live animator scale to everything underneath, and keeps it live.
 *
 * Installed once, in `TdayTheme`, which is the one wrapper all four of the app's
 * `setContent` roots already go through. One registration for the whole app rather
 * than one per asker is the point: [rememberTdayMotionEnabled] is read per *row* in
 * a feed, and a `ContentObserver` per visible row would be a subscription leak
 * dressed up as a preference.
 *
 * The observer is what turns a change in the setting into a recomposition. Compose's
 * own `MotionDurationScale` re-reads the setting on every animation frame and so
 * needs no observer at all — but a composable that branches on the preference is not
 * an animation frame, and without this it would keep whatever answer it was given at
 * mount until something unrelated happened to invalidate it. A user who turns
 * animations off from the quick settings tile while the app is open would go on
 * seeing the animated build until they navigated away.
 */
@Composable
fun ProvideTdayMotionScale(content: @Composable () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    var scale by remember(resolver) { mutableFloatStateOf(readAnimatorScale(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = readAnimatorScale(resolver)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        // Re-read once attached. The setting can move between the read that seeded
        // the state above and this registration, and a change that lands in that
        // window is exactly the one nothing would ever tell us about again.
        scale = readAnimatorScale(resolver)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    CompositionLocalProvider(LocalTdayMotionScale provides scale, content = content)
}

/**
 * The scale to time this composable's motion against.
 *
 * `null` from the composition local means nobody upstream is watching the setting —
 * a `@Preview`, a unit-test harness, a composable hoisted outside `TdayTheme`. Those
 * fall back to a one-shot read rather than to [UNSCALED], because a screen drawn
 * outside the provider should still obey the setting; the only thing it gives up is
 * noticing it change.
 *
 * @return The animator duration scale: 0 for animations off, 1 for untouched.
 */
@Composable
fun rememberTdayMotionScale(): Float {
    val provided = LocalTdayMotionScale.current
    if (provided != null) return provided
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { readAnimatorScale(resolver) }
}

/**
 * Whether decorative animation should play at all.
 *
 * The boolean question, kept because most call sites only have a boolean answer to
 * give: a spec is either a `tween` or a `snap`, and there is no half a `snap`. It is
 * derived from [rememberTdayMotionScale] rather than reading the setting itself, so
 * the switch and the scale can never disagree about the same device.
 *
 * A screen that reads `false` here must still draw its finished state — a scene held
 * at the start of its fade looks half-rendered rather than deliberate, which is the
 * same call the web stylesheet makes (`docs/motion.md`'s fifth idiom rule).
 */
@Composable
fun rememberTdayMotionEnabled(): Boolean = rememberTdayMotionScale() != 0f

/**
 * [delay], on the same clock the platform is running animations on.
 *
 * For the waits *between* the beats of a choreography, and for nothing else. A
 * debounce on a keystroke, a minimum time a spinner stays up, a dwell before a
 * control folds itself away — none of those are covering an animation, and scaling
 * them would take away time the user needs rather than time they spend watching.
 * The test is whether the wait would still make sense with the screen frozen: if it
 * would, it is not this function's business.
 *
 * Every degenerate scale lands on "do not wait", which is the safe direction: the
 * destination is already drawn by the time anything asks, so an unexpected 0 costs a
 * beat of choreography and an unexpected wait costs the user the app.
 *
 * @param millis How long the wait is at 1x.
 * @param scale The animator duration scale, from [rememberTdayMotionScale].
 */
suspend fun scaledDelay(millis: Long, scale: Float) {
    delay(scaledDelayMillis(millis, scale))
}

/**
 * The arithmetic half of [scaledDelay], split out so it can be tested without a
 * composition, a device or a clock.
 *
 * Rounded rather than truncated, and in `Double` rather than in `Float`. The slider's
 * own six values are all exact either way, but the setting is a plain `Settings.Global`
 * float that anything holding `WRITE_SECURE_SETTINGS` can put an arbitrary number in,
 * and truncating would bias every wait in the app short whenever it did.
 */
internal fun scaledDelayMillis(millis: Long, scale: Float): Long {
    // NaN fails every comparison it is put through, including the one inside
    // `coerceAtLeast`, so it has to be named rather than clamped.
    val usable = if (scale.isNaN()) 0f else scale.coerceAtLeast(0f)
    return (millis * usable.toDouble()).roundToLong()
}
