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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ohmz.tday.compose.core.data.ReduceMotionPreferenceStore
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

/**
 * How fast this app is running animations, as a multiplier on every duration.
 *
 * Compose has no equivalent of `prefers-reduced-motion`, so this starts from what
 * Android actually offers: the animator duration scale, which is what Settings'
 * "Remove animations" and the developer-options slider both write. A *scale* and not
 * a switch, because that is what the user is given — 0x, 0.5x, 1x, 2x, 5x, 10x — and
 * treating it as a boolean throws away four of those six.
 *
 * The app's own "Reduce motion" switch folds into the same number rather than sitting
 * beside it as a second thing every call site would have to remember to ask — see
 * [effectiveMotionScale] for which way the two compose.
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

/**
 * The device's half of that number, published alongside it.
 *
 * A second local and not a second read. The observer behind it is one registration
 * for the whole app, and a feed that asked per row would be a subscription leak
 * dressed up as a preference — so both halves travel down the same way. They were the
 * same number by construction until the in-app switch existed; now a wait has to be
 * able to say which of the two it is timed against. See [effectiveMotionScale].
 */
val LocalTdaySystemMotionScale = compositionLocalOf<Float?> { null }

/** What the platform reports when nobody has moved the slider, and our fallback. */
private const val UNSCALED = 1f

/** What either answer asking for less motion resolves to: none, and no wait in its place. */
private const val REDUCED = 0f

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
 * The one scale, from the device's answer and the app's own.
 *
 * A composition and not a choice between them, in the only direction an in-app
 * preference is allowed to move: [reduceInApp] can subtract motion and can never add
 * any back. Someone who told Android to remove animations and then left this app's
 * switch off has not asked for animations — they have asked for nothing extra — and a
 * device at 0x stays at 0x however this switch is set. It reads the same way from the
 * other side: the switch reduces on a phone sitting at 10x exactly as it does on one
 * at 1x, because "reduce" is an answer about this app and not about the slider.
 *
 * Split out of the composable so the rule can be tested without a device, which for a
 * rule about never re-enabling something is the part worth pinning.
 *
 * The two halves do **not** have the same reach, and a call site has to know it.
 * [systemScale] is also read by Compose itself — it is the `MotionDurationScale` in
 * the recomposer's coroutine context, which every `animate*AsState` and `animateTo`
 * obeys whether or not anybody wrote code for it. [reduceInApp] cannot be: that
 * context belongs to the composition and no composable can substitute one for its own
 * subtree. So the in-app switch reaches exactly the motion that *asks* — anything
 * built on [rememberTdayMotionEnabled] or [rememberTdayMotionScale] — and an
 * animation that leaves its timing to Compose keeps animating at the device's scale.
 * The site-by-site migration is the `reduced-motion-coverage` item in
 * `docs/motion/LEDGER.md`; this function is what it migrates onto.
 *
 * For an animation that is a coverage gap. For a *wait* it is a correctness hazard,
 * and that is the half to read this doc for. Before this function the two numbers
 * were the same by construction, so any [scaledDelay] was in step with whatever it
 * was covering whichever one it was handed. Now they can differ, and a wait given the
 * wrong one breaks `docs/motion.md`'s fifth idiom rule from the side nobody watches:
 * the motion is kept and the wait is removed, so the app tears a surface out from
 * under a transition that is still running — which is the jump the wait existed to
 * hide. The rule at a call site is therefore not "take the app's scale", it is:
 *
 * > A wait runs on the clock of the motion it is covering. [rememberTdayMotionScale]
 * > when that motion is gated on the preference, [rememberSystemMotionScale] when it
 * > is a Compose animation nobody has gated yet.
 *
 * Every site on the second of those names the un-gated animation it is waiting for,
 * so whoever migrates that animation is told, at the wait, that the wait moves with
 * it.
 */
internal fun effectiveMotionScale(systemScale: Float, reduceInApp: Boolean): Float =
    if (reduceInApp) REDUCED else systemScale

/**
 * The clock Compose is running animations on.
 *
 * The device's answer with the in-app switch left out of it. Two callers want exactly
 * that and nothing else: a wait covering an animation the switch does not reach (see
 * [effectiveMotionScale]), and the Settings row that owns the switch, which has to
 * say "Android has already done this" rather than offer a control that cannot change
 * anything. Everything gated on the preference wants [rememberTdayMotionScale].
 *
 * Reads the local rather than the setting, which makes it as cheap per *row* as
 * [rememberTdayMotionEnabled] — the registration behind it is
 * [ProvideTdayMotionScale]'s. `null` means no provider upstream, and the fallback is
 * a one-shot read for the same reason [rememberTdayMotionScale]'s is: a surface that
 * escaped the provider should still obey the setting, and gives up only noticing it
 * change.
 */
@Composable
fun rememberSystemMotionScale(): Float {
    val provided = LocalTdaySystemMotionScale.current
    if (provided != null) return provided
    val context = LocalContext.current.applicationContext
    return remember(context) { readAnimatorScale(context.contentResolver) }
}

/**
 * The platform's half of the answer, live — the one read the locals are fed from.
 *
 * The observer is what turns a change in the setting into a recomposition. Compose's
 * own `MotionDurationScale` re-reads the setting on every animation frame and so
 * needs no observer at all — but a composable that branches on the preference is not
 * an animation frame, and without this it would keep whatever answer it was given at
 * mount until something unrelated happened to invalidate it. A user who turns
 * animations off from the quick settings tile while the app is open would go on
 * seeing the animated build until they navigated away.
 *
 * Private, and called exactly once. A `ContentObserver` per visible row would be a
 * subscription leak dressed up as a preference, which is why the app-wide
 * registration is [ProvideTdayMotionScale]'s job and every reader downstream is
 * handed a composition local instead.
 */
@Composable
private fun rememberObservedSystemMotionScale(): Float {
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
    return scale
}

/**
 * The app's own half of the answer, live.
 *
 * Same shape and same reason as [rememberSystemMotionScale]: the switch is in
 * Settings and the motion it governs is everywhere else, so a write that nothing
 * observes would take effect at the next cold start and nowhere before it.
 */
@Composable
private fun rememberInAppReduceMotion(): Boolean {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { ReduceMotionPreferenceStore(context) }
    var reduce by remember(store) { mutableStateOf(store.isEnabled()) }
    DisposableEffect(store) {
        val cancel = store.observeEnabled { reduce = it }
        // Same window as the one above: a flip between the seeding read and this
        // registration would otherwise go unheard for the life of the composition.
        reduce = store.isEnabled()
        onDispose { cancel() }
    }
    return reduce
}

/**
 * Publishes the live motion scale to everything underneath, and keeps it live.
 *
 * Installed once, in `TdayTheme`, which is the one wrapper all four of the app's
 * `setContent` roots already go through. One registration for the whole app rather
 * than one per asker is the point — see [rememberSystemMotionScale].
 */
@Composable
fun ProvideTdayMotionScale(content: @Composable () -> Unit) {
    val systemScale = rememberObservedSystemMotionScale()
    val scale = effectiveMotionScale(systemScale, rememberInAppReduceMotion())
    CompositionLocalProvider(
        LocalTdaySystemMotionScale provides systemScale,
        LocalTdayMotionScale provides scale,
        content = content,
    )
}

/**
 * The scale to time this composable's motion against.
 *
 * `null` from the composition local means nobody upstream is watching the setting —
 * a `@Preview`, a unit-test harness, a composable hoisted outside `TdayTheme`. Those
 * fall back to a one-shot read rather than to [UNSCALED], because a screen drawn
 * outside the provider should still obey the setting; the only thing it gives up is
 * noticing it change. The fallback reads *both* halves for the same reason it reads
 * either: a surface that escaped the provider is exactly where a preference quietly
 * failing to apply would never be noticed.
 *
 * @return The effective scale: 0 for no motion, 1 for untouched.
 */
@Composable
fun rememberTdayMotionScale(): Float {
    val provided = LocalTdayMotionScale.current
    if (provided != null) return provided
    val context = LocalContext.current.applicationContext
    return remember(context) {
        effectiveMotionScale(
            readAnimatorScale(context.contentResolver),
            ReduceMotionPreferenceStore(context).isEnabled(),
        )
    }
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
 * @param scale The clock the covered motion runs on — [rememberTdayMotionScale] where
 *   that motion is gated on the preference, [rememberSystemMotionScale] where it is a
 *   Compose animation nobody has gated. [effectiveMotionScale] has the argument for
 *   why those are two different numbers now.
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
