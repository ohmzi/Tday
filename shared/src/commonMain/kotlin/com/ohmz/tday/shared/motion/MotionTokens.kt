package com.ohmz.tday.shared.motion

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The canonical motion vocabulary — the single hand-authored source of truth for
 * every duration, delay, easing, spring and press scale the three clients
 * animate with.
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
 * a regression, not a cleanup. There is one deliberate exception, `Easings.Gesture`,
 * and it is marked as such below.
 *
 * Every value here was measured against the tree rather than chosen: see
 * `docs/motion.md` for the site counts each rung stands on.
 */
object MotionTokens {

    /**
     * A duration rung, in milliseconds — how long a motion runs.
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

    /**
     * A delay, in milliseconds — how long something waits before it starts.
     *
     * Kept in its own type, and its own generated namespace, because a delay is
     * not a duration: emitting them together would mint `duration-emphasis` and
     * `duration-celebration-lead` as interchangeable 320 ms utilities that no
     * guardrail could tell apart.
     */
    data class Delay(val name: String, val ms: Int, val doc: String)

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
     *
     * Compose's `dampingRatio` and SwiftUI's `dampingFraction` are the same
     * dimensionless quantity (zeta, 1.0 being critical damping), so [damping]
     * crosses unchanged. The parity test can only check the two numbers are
     * equal; it cannot check that claim, which is why it is written down here.
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
                "out because a filter changed. Absorbs the whole 120-160 band. This is also " +
                "Tailwind's own un-overridden `--default-transition-duration`, so the web's bare " +
                "`transition-*` utilities are already on this rung for free — do not rebind it.",
        ),
        Duration(
            "Enter", 200,
            "One element arriving, or a control changing state under its own steam. The rung you " +
                "reach for when a motion has no reason to be another length; the web's 52 " +
                "`duration-200` utilities are already here.",
        ),
        Duration(
            "Change", 260,
            "The user's own edit replayed back to them, in place — they are meant to watch it " +
                "finish, and nothing moves position. The completion fade, overdue rows clearing, " +
                "a restore. If the thing changes where or how big it is, that is Emphasis, not " +
                "this. The strongest three-way cluster in the tree.",
        ),
        Duration(
            "Emphasis", 320,
            "Position or size changes: a row taking its new slot, a sheet arriving, a hero " +
                "moving, a strikethrough sweeping across. Long enough to be followed with the " +
                "eye. The boundary with Change is geometry, not importance.",
        ),
        Duration(
            "Scene", 520,
            "A full-bleed illustration rising into an empty feed, or sinking out of one — the " +
                "longest motion the app plays. Four of the five sites at this length are that " +
                "illustration; the fifth is a search-result scroll. NOT for route or tab " +
                "handovers: those are Quick, and globals.css argues in place for why anything " +
                "longer there reads as a stall.",
        ),
    )

    /**
     * Read off the ladder rather than restated, so `PlacementLead` cannot drift
     * away from the placement tween it is defined to match.
     */
    private val EMPHASIS_MS: Int = durations.first { it.name == "Emphasis" }.ms

    // ── Delays ───────────────────────────────────────────────────────────
    /**
     * The two legs of the completion celebration, which are sequential and not
     * competing: displaced rows take `PlacementLead` to reach their new slots,
     * the burst fires, and the scene comes up `CelebrationLead` after that. The
     * source calls them "added, never traded".
     *
     * They are deliberately NOT both derived from `Emphasis`. `PlacementLead` is
     * — it exists to match the placement tween exactly, and has no freedom of its
     * own. `CelebrationLead` is an independent literal, because the confetti
     * window is a decision about the burst, not about whatever a host feed does
     * with its rows; welding it to `Emphasis` would let a later PR that retimes
     * row placement silently retime the confetti on all three clients.
     */
    val delays: List<Delay> = listOf(
        Delay(
            "PlacementLead", EMPHASIS_MS,
            "How long a feed holds its celebration back — exactly as long as the items that scene " +
                "displaces take to reach their new slots, which is why this is Emphasis by " +
                "construction rather than a number of its own.",
        ),
        Delay(
            "CelebrationLead", 320,
            "How long the confetti has the screen to itself before the scene comes up behind it. " +
                "Independent of anything a host feed does — equal to Emphasis today by " +
                "coincidence, not by derivation.",
        ),
    )

    // ── Easings ──────────────────────────────────────────────────────────
    val easings: List<Easing> = listOf(
        Easing(
            "Standard", 0.4, 0.0, 0.2, 1.0,
            "Both ends eased. The unmarked curve: Compose's FastOutSlowInEasing, and Tailwind's " +
                "`--ease-in-out` and `--default-transition-timing-function`, are all byte-identical " +
                "to this. Note for iOS migrations: SwiftUI's .easeInOut is (0.42, 0, 0.58, 1), a " +
                "materially different tail, so moving a site onto this token is a visible change.",
        ),
        Easing(
            "Enter", 0.0, 0.0, 0.2, 1.0,
            "Decelerate. Something arriving, which should settle rather than stop. " +
                "Compose's LinearOutSlowInEasing; Tailwind's built-in `--ease-out`. SwiftUI's " +
                ".easeOut is (0, 0, 0.58, 1) and is not this curve.",
        ),
        Easing(
            "Exit", 0.4, 0.0, 1.0, 1.0,
            "Accelerate. Something leaving, which should commit rather than drift off. " +
                "Compose's FastOutLinearInEasing; Tailwind's built-in `--ease-in`. SwiftUI's " +
                ".easeIn is (0.42, 0, 1, 1) — the one near-match of the three.",
        ),
        Easing(
            "Scene", 0.05, 0.7, 0.1, 1.0,
            "Material's emphasised decelerate: fast off the mark, settles rather than stops. " +
                "Pairs with the Scene duration on the empty-state illustration, and nothing else.",
        ),
        Easing(
            "Gesture", 0.2, 0.8, 0.2, 1.0,
            "WEB ONLY, and the one deliberate exception to the two-client rule above. It is the " +
                "curve already behind the web's press feedback and its calendar paging; naming it " +
                "changes no pixels and retires four raw cubic-beziers. Android and iOS express the " +
                "same intent with the Gesture SPRING, which is a different thing under a shared name.",
        ),
    )

    // ── Springs ──────────────────────────────────────────────────────────
    val springs: List<Spring> = listOf(
        Spring(
            "Snappy", 0.28, 0.86, 504,
            "Confirmation dialogs, selector overlays, a control committing to a new state. The " +
                "busiest spring on iOS by a wide margin: the exact 0.28/0.86 pair is hand-written " +
                "at nineteen sites across eight files. It has no Android anchor yet — Compose's " +
                "nearest value is Spring.StiffnessMediumLow, which is a library default rather " +
                "than a decision anybody made.",
        ),
        Spring(
            "Gesture", 0.34, 0.82, 340,
            "A surface continuing under its own momentum after a finger lets go. Looser than " +
                "Snappy on purpose: a release should overshoot a little or it reads as a snap-back. " +
                "The only token here that was already a working two-platform conversion before it " +
                "had a name — do not round 340 to the arithmetic 342, it would break the Android " +
                "site that already matches.",
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
     *
     * These three are the narrowest part of the vocabulary and the tree is
     * messier than they are: nine distinct press literals span 0.92-0.992, and
     * the FAB alone is 0.93 on Android and iOS and 0.95 on web. `docs/motion.md`
     * records that spread as an open question for the press-scale PRs rather than
     * pretending this PR settles it.
     */
    val pressScales: List<PressScale> = listOf(
        PressScale(
            "Bar", 0.94,
            "Bar buttons. Carries a written argument at its one call site for why it is 0.94 and " +
                "not 0.95 — half a point of travel on a small circle read as a tap rather than a " +
                "press. The FAB is NOT on this token yet; see docs/motion.md.",
        ),
        PressScale("Card", 0.97, "Cards and tiles."),
        PressScale("Row", 0.985, "Full-width rows, where more travel would read as the list moving."),
    )
}
