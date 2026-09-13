package com.ohmz.tday.shared.motion

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The canonical motion vocabulary — the single hand-authored source of truth for
 * every duration, easing, spring and press scale the three clients animate with.
 *
 * This is the second cross-platform Gradle codegen in the repo, modelled on the
 * first (`GuideCatalog` -> `exportGuideContent`). Android, iOS and web each read a
 * generated artifact produced from this file; `./gradlew :shared:verifyMotionTokens`
 * is the CI drift gate. See `docs/motion.md` for the normative table, the idiom
 * rules, and why each rung exists.
 *
 * The rule that keeps this honest: a number that appears in two clients belongs
 * here; a number that appears in one, with a written argument for why it is
 * special, stays where it is and gets a `not a token — see docs/motion.md`
 * comment. A token file that swallows the best-reasoned specs in the codebase is
 * a regression, not a cleanup.
 */
object MotionTokens {

    /**
     * A duration rung, in milliseconds.
     *
     * Five rungs, deliberately. Rungs closer together than about two frames at
     * 60 Hz cannot be told apart by eye, which means the guardrail cannot tell a
     * correct one from a lazy one — so the ladder is only as fine as it is
     * enforceable.
     */
    data class Duration(val name: String, val ms: Int, val doc: String) {
        init {
            require(ms in 0..5_000) { "$name: $ms ms is outside the plausible range" }
        }
    }

    /** A cubic-bezier easing. The four control points, in CSS order. */
    data class Easing(
        val name: String,
        val x1: Double,
        val y1: Double,
        val x2: Double,
        val y2: Double,
        val doc: String,
    )

    /**
     * A spring, carried as both platforms' parameters because neither can be
     * derived at read time.
     *
     * Compose fixes mass = 1, so its undamped natural frequency is `sqrt(stiffness)`;
     * SwiftUI's is `2*PI/response`. The two are the same spring when
     * `stiffness == (2*PI/response)^2`, which [init] enforces here rather than
     * leaving to a test — a source of truth that can hold two different springs
     * under one name is not one.
     */
    data class Spring(
        val name: String,
        val response: Double,
        val damping: Double,
        val stiffness: Int,
        val doc: String,
    ) {
        init {
            val implied = (2 * PI / response) * (2 * PI / response)
            val drift = abs(implied - stiffness) / implied
            require(drift <= SPRING_TOLERANCE) {
                "$name: response $response implies stiffness ${implied.roundToInt()}, not $stiffness " +
                    "(${(drift * 100).roundToInt()}% apart, tolerance ${(SPRING_TOLERANCE * 100).roundToInt()}%)"
            }
        }
    }

    /** A press scale, by surface class. */
    data class PressScale(val name: String, val scale: Double, val doc: String)

    /** How far a spring's two parameter sets may disagree before they are two springs. */
    const val SPRING_TOLERANCE: Double = 0.02

    // ── Durations ────────────────────────────────────────────────────────
    val durations: List<Duration> = listOf(
        Duration(
            "Quick", 150,
            "The app answering a finger that is on it, or something leaving that nobody is meant " +
                "to watch go. Press feedback, a hint retracting, a toast fading, a row dropping " +
                "out because a filter changed. Absorbs the whole 120-160 band.",
        ),
        Duration(
            "Enter", 190,
            "One element arriving, or a control changing state under its own steam. The default " +
                "rung: if a motion has no better reason to be a different length, it is this one.",
        ),
        Duration(
            "Change", 260,
            "A change the user caused, played back to them — they are meant to watch it finish. " +
                "The completion fade, overdue rows clearing, a calendar page sliding, a surface a " +
                "deliberate action put on screen. The strongest three-way cluster in the tree.",
        ),
        Duration(
            "Emphasis", 320,
            "Something travelling a real distance across the screen: a row taking its new slot, a " +
                "sheet arriving, a hero moving. Long enough to be followed with the eye.",
        ),
        Duration(
            "Scene", 520,
            "A whole scene handing over. Long enough to read as a hand-off rather than a page load.",
        ),
    )

    /**
     * Read off the ladder rather than restated, so the two celebration legs
     * cannot drift away from `Emphasis` without the rung itself moving.
     */
    private val EMPHASIS_MS: Int = durations.first { it.name == "Emphasis" }.ms

    // ── Celebration ladder ───────────────────────────────────────────────
    /**
     * The two legs of the completion celebration, which are sequential and not
     * competing: displaced rows take `PlacementLead` to reach their new slots,
     * the burst fires, and the scene comes up `CelebrationLead` after that.
     *
     * Both equal `Emphasis` by construction — the relationship is the token, not
     * the number, which is what `motion-parity.test.ts` pins.
     */
    val celebration: List<Duration> = listOf(
        Duration(
            "PlacementLead", EMPHASIS_MS,
            "How long a feed holds its celebration back — exactly as long as the items that scene " +
                "displaces take to reach their new slots.",
        ),
        Duration(
            "CelebrationLead", EMPHASIS_MS,
            "How long the confetti has the screen to itself before the scene comes up behind it.",
        ),
    )

    // ── Easings ──────────────────────────────────────────────────────────
    val easings: List<Easing> = listOf(
        Easing(
            "Standard", 0.4, 0.0, 0.2, 1.0,
            "Both ends eased. The unmarked curve: Compose's FastOutSlowInEasing and Tailwind's " +
                "own `--default-transition-timing-function` are already byte-identical to this.",
        ),
        Easing(
            "Enter", 0.0, 0.0, 0.2, 1.0,
            "Decelerate. Something arriving, which should settle rather than stop. " +
                "Compose's LinearOutSlowInEasing; Tailwind's built-in `--ease-out`.",
        ),
        Easing(
            "Exit", 0.4, 0.0, 1.0, 1.0,
            "Accelerate. Something leaving, which should commit rather than drift off. " +
                "Compose's FastOutLinearInEasing; Tailwind's built-in `--ease-in`.",
        ),
        Easing(
            "Scene", 0.05, 0.7, 0.1, 1.0,
            "Material's emphasised decelerate: fast off the mark, settles rather than stops. " +
                "For scene-length motion only, where the long tail is the point.",
        ),
        Easing(
            "Gesture", 0.2, 0.8, 0.2, 1.0,
            "The curve a finger-driven surface continues under once released. Already the curve " +
                "behind every swipe and calendar page in the web client; naming it changes no pixels.",
        ),
    )

    // ── Springs ──────────────────────────────────────────────────────────
    val springs: List<Spring> = listOf(
        Spring(
            "Snappy", 0.30, 0.86, 440,
            "Confirmation dialogs, selector overlays, a control committing to a new state. " +
                "The busiest spring on iOS by a wide margin.",
        ),
        Spring(
            "Gesture", 0.34, 0.82, 340,
            "A surface continuing under its own momentum after a finger lets go. Looser than " +
                "Snappy on purpose: a release should overshoot a little or it reads as a snap-back.",
        ),
        Spring(
            "Settle", 0.40, 0.86, 250,
            "Something heavy coming to rest — a dock, a bar, a sheet finding its height.",
        ),
    )

    // ── Press scales ─────────────────────────────────────────────────────
    /**
     * How far a surface squashes under a finger, by surface class. Smaller
     * surfaces move further, because the same absolute travel reads as a bigger
     * gesture on a bar button than on a full-width row.
     */
    val pressScales: List<PressScale> = listOf(
        PressScale("Bar", 0.94, "Bar buttons and the FAB — small, isolated, and pressed deliberately."),
        PressScale("Card", 0.97, "Cards and tiles."),
        PressScale("Row", 0.985, "Full-width rows, where more travel would read as the list moving."),
    )

    /** Every duration the clients generate, celebration ladder included. */
    val allDurations: List<Duration> get() = durations + celebration
}
