@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.ohmz.tday.compose.core.navigation.AppRoute
import com.ohmz.tday.compose.core.navigation.CompletedScope
import com.ohmz.tday.compose.core.navigation.TILE_TRANSITION_COLOR
import com.ohmz.tday.compose.core.navigation.TILE_TRANSITION_ORIGIN
import com.ohmz.tday.compose.core.navigation.tileTransitionKey
import com.ohmz.tday.compose.ui.theme.TdayDimens

/**
 * The tile→screen zoom: the Compose half of what iOS does with
 * `.matchedTransitionSource` + `.navigationTransition(.zoom(...))`.
 *
 * A tile tap is otherwise a route change like any other, and the NavHost hands the
 * destination over with one fade. This makes the ten rectangles the user actually
 * pressed — the six scheduled-board category tiles, that board's Today card and its
 * custom list rows, the Anytime feed's Completed entry and its custom list rows —
 * grow into the screen they open, so the arrival is seen coming from where it was
 * asked for.
 *
 * WHAT TRAVELS IS A SURFACE, AND ONLY A SURFACE.
 *
 * iOS's zoom does not carry a picture of either end from one rectangle to the other. What
 * grows is a surface, and the content on each side crossfades behind it: the tile's own
 * icon, label and count fade where they stand — they do not scale — and the screen's
 * toolbar and rows are laid out where they are going to end up and fade in as the surface
 * arrives. The screen opening "evenly" is exactly that: every component reaching its final
 * place at once and becoming visible underneath a surface that is covering it. It is the
 * one shape that a scaled copy cannot produce, because a scaled copy keeps every component
 * at its own size inside a window that is still growing — which is what reads as giant UI
 * components climbing out of a tile.
 *
 * Compose draws the shared element's OWN CONTENT at both ends, so this file's whole job is
 * to make the shared element contain nothing but that surface:
 *
 * 1. THE DESTINATION contributes the surface. It is a full-size filled rectangle, and it is
 *    the travelling half — the thing the eye follows. Its corners are clipped to a radius
 *    that starts at the tile's and ends square, so the rectangle is the tile's shape where
 *    it comes out of the tile and the screen's (no radius) where it lands. THE SCREEN IS
 *    NOT INSIDE IT: `content()` is a sibling, laid out at its final size, and fades in on
 *    the route's own enter. That is deliberate and is the whole difference between this and
 *    a stretched screenshot of the screen — see [TdayTileResizeMode] for how the rectangle is
 *    reconciled with the surface it carries, and [TdayTileCornerClip] for why the corners
 *    need a clip at all when the thing inside it is a solid colour. It is filled with the
 *    TILE'S OWN COLOUR — handed to the destination by the push site, because no route
 *    carries it; see [TILE_TRANSITION_COLOR] — lerped to the app's background as it lands,
 *    and its opacity hands over to zero as the rectangle arrives, so the screen it grew over
 *    is never uncovered by a cut. See [TdayTileSurface] for both halves of that argument;
 *    they are the two things this file got wrong first, and the first generated the "white
 *    box" and the second the "and then the screen loads again" the user reported.
 *
 * 2. THE SOURCE contributes nothing at all. It publishes the rectangle the push started
 *    from and draws no pixels: the tile's own `Card` already paints exactly that rounded
 *    rectangle, in place, in the colour the tile is, and a second copy drawn in the overlay
 *    over the tile could only cover the icon and label the user just pressed — the pop this
 *    shape exists to remove. The tile's content therefore leaves the screen the ordinary
 *    way, on the route's own exit fade, which is the same length the surface arrives on, so
 *    the two blend rather than cut.
 *
 * Three pieces, none of which a tile has to know about beyond being handed its key — a
 * value it passes straight to a bounds-only sibling, not a modifier it has to place:
 *
 * 1. [LocalTdaySharedTransitionScope] is the namespace. It is provided ONCE, around
 *    the NavHost, by [TdayTileTransitionLayout] — every tile and every destination
 *    below it is a child of the same one. `SharedTransitionLayout`'s scope exists
 *    only as a receiver of its content lambda; there is no `LocalSharedTransitionScope`
 *    in Compose 1.7 to read it back from, so the wrapper below is what re-publishes
 *    it. It travels as a local rather than as a parameter for the reason iOS's
 *    namespace travels in the environment: the tiles are built two files away from
 *    the graph that hosts them, behind private composables, and a parameter chain
 *    through all of them would be plumbing with no reader.
 *
 * 2. [LocalTdayTileSourceScope] is the `AnimatedVisibilityScope` of the screen the
 *    TILES are drawn on. A shared bounds node needs the scope of the visibility the
 *    element participates in, and for a source tile that is the `home` destination's
 *    own scope, not the one it is navigating to. It is provided at that one
 *    destination, so a tile cannot be wired to the wrong half.
 *
 * 3. [tdayTileTransitionSource] on the tile and [TdayTileDestination] on the screen it
 *    opens. Both are no-ops when anything is missing — no scope, no key, no origin, or
 *    no motion — which is what keeps a half-wired surface from being worse than an
 *    unwired one.
 *
 * The key itself is never written here and never passed in by a caller: it is read
 * from `AppRoute.tileTransitionKey`, the one table both ends share, so the two halves
 * cannot disagree about which rectangle a screen came out of.
 *
 * WHERE THE SCREEN CAME FROM IS A PUSH SITE'S FACT, NOT THE ROUTE'S. A tile route is
 * reachable without a press — a shortcut, a notification, a widget row — and on a warm
 * app every one of those pushes runs with `home` still underneath and its tiles still
 * composed, so a key that matched the route alone would grow the screen out of a
 * rectangle nobody touched. The route cannot tell those arrivals apart and neither can
 * the back stack, because home is the entry below both. So the push site says so, once,
 * through `navigateFromHomeTile`, and [rememberHomeTileOrigin] carries the answer to the
 * destination, where [TdayTileDestination] will not compile without it.
 *
 * REDUCE MOTION. Both ends read [rememberTdayMotionEnabled], the same gate the rest of
 * the app reads; with motion refused the modifier is not installed at all and the tile
 * simply does not grow. What the user gets instead is the ordinary route change, which
 * the NavHost now plays as a short fade rather than as a cut — `docs/motion.md`'s fifth
 * idiom rule is "removes the trip, never the destination", and a zoom is a
 * large-amplitude trip, not the destination. The gate is read symmetrically on purpose:
 * a shared element whose source plays and whose destination does not is a transition
 * that silently degrades, which is exactly the failure mode this file exists to avoid.
 *
 * The surface is not painted at all in that case, and that is now enforced rather than
 * merely claimed. It used to read "with no shared node there is no rectangle for it to
 * fill", which was not true of the composition: the node was skipped, the fill inside it
 * was not, and what sat behind the route's fade was a full-screen rectangle in
 * `colorScheme.background` — the colour the destination screen is filled with, so the
 * claim held in the only way that mattered, by being invisible. The fill is the tile's
 * colour now, and a tile-coloured rectangle behind a screen that is halfway through its
 * own fade is a wash nobody asked for on the one path where the user has explicitly asked
 * for plainness. So the fill is composed behind the same gate the node is, read from the
 * same function ([rememberTdaySharedScope]) instead of being written out twice.
 */
val LocalTdaySharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * The `AnimatedVisibilityScope` of the destination the tiles live on — `home` for all
 * ten surfaces. Null outside it, which is also the honest answer for a preview, a
 * widget surface, or anything else drawn without the graph above it.
 *
 * The tiles do not need one scope for the source half of a push and another for the
 * target half; each end of a shared element names the visibility it is part of, and
 * for a tile that is always the screen it is drawn on.
 */
val LocalTdayTileSourceScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Wraps the NavHost so every tile and destination underneath is matched in one
 * namespace.
 *
 * A named wrapper rather than the bare `SharedTransitionLayout` call at the call site,
 * because the scope has to be re-published and the two lines belong together: a
 * `SharedTransitionLayout` whose scope nobody provides is a layout that matches
 * nothing, and nothing about it would say so.
 */
@Composable
fun TdayTileTransitionLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SharedTransitionLayout(modifier = modifier) {
        CompositionLocalProvider(LocalTdaySharedTransitionScope provides this) {
            content()
        }
    }
}

/**
 * How a shared element's rectangle travels from the tile to the screen.
 *
 * [TdayMotionTokens.Springs.settle], named rather than left to Compose's own default
 * for the same reason every other spec in the app names one: the library default is a
 * bare `spring` on a library stiffness that nobody here chose, which is neither a token
 * nor a decision a comment can defend. `settle` is the vocabulary's slot for something
 * heavy coming to rest — a dock, a bar, a sheet finding its height — and a whole screen
 * finding its size is that same description; `snappy` is the vocabulary's slot for a
 * control committing to a new state, which this is not.
 *
 * Assessed against the two ways a rectangle spring can be read as jank, and kept:
 *
 * - It cannot fight a clip any more. `dampingRatio` 0.86 is about half a percent of
 *   overshoot, and the shape the overlay is clipped to — [TdayTileCornerClip] — is
 *   recomputed from the rectangle's own size on every frame, so a half percent of
 *   overshoot moves the corner with the rectangle instead of against it.
 * - Its tail is invisible here, and it is a tail and not a cut. The rectangle is most of
 *   the way there at roughly 200 ms and runs on to about half a second before it is inside
 *   `Rect.VisibilityThreshold`; what sits at the end of it is the screen drawing itself
 *   underneath the surface, so the last percent of the spring is the surface settling
 *   rather than a blank window. And the tail is not truncated: the shared transition stays
 *   active until the bounds animation reports finished (Compose ends a `Transition` only
 *   once its child transitions agree with it, so the bounds spring is what decides when
 *   this hand-over is over), which means the destination's own screen takes over at the
 *   settled rectangle rather than at whatever fraction a shorter transition had reached.
 *   That is the sub-pixel hand-off this shape depends on, and it is worth knowing it is the
 *   spring's own threshold, not the route fade's length, that decides where the open ends.
 *
 * The threshold is `Rect`'s own and not a number written here: the token factory keeps
 * `visibilityThreshold` a parameter because how close is close enough to stopped is a
 * question about the units being animated, and these units are a rectangle. It follows
 * that the four edges settle independently and not off one progress value; the only thing
 * below that reads a progress back out of the rectangle is [TdayTileCornerClip], and it is
 * fed by the transition's own state rather than by the bounds — see there for why.
 */
private val TdayTileBoundsTransform = BoundsTransform { _, _ ->
    TdayMotionTokens.Springs.settle(visibilityThreshold = Rect.VisibilityThreshold)
}

/**
 * HOW THE GROWING RECTANGLE IS RECONCILED WITH REAL CONTENT. THE DECISION, AND WHY.
 *
 * `ResizeMode` is the one place Compose asks the question this whole file is about, and it
 * has exactly two answers. They differ in what the shared element's content has to be, so
 * the answer follows from the shape above rather than from taste:
 *
 * - `RemeasureToBounds` gives the content the animated rectangle as `Constraints.fixed(...)`
 *   on every frame. It is the mode for content that must genuinely track the rectangle —
 *   and it is exactly the wrong one here, twice over. It re-lays-out whatever is inside
 *   sixty times a second, and what is inside is now the surface and only the surface, so
 *   there would be nothing for that to buy; and it is the mode that produces the reported
 *   symptom when the thing inside is a screen: a component keeps its own size inside a
 *   window that is still growing, which is "giant UI components coming out of the tile".
 *
 * - `ScaleToBounds` measures the content ONCE, at its lookahead size — its stable layout —
 *   and then re-PLACES it under a scale for the rest of the flight. Nothing re-measures,
 *   nothing re-wraps, and nothing re-places at a new size. That is the mode for content that
 *   is not the same at both ends, which is what a surface is: it has the tile's rectangle at
 *   one end and the screen's at the other.
 *
 * `ContentScale.FillBounds` rather than the library's default `FillWidth`, and rather than
 * `Fit`. The content of this node is a single solid colour, so the one thing that would make
 * a non-uniform scale the wrong choice — a stretched picture — cannot happen: there is no
 * detail in it to distort. What the choice buys is that the rectangle is COVERED. `Fit` is
 * min-based, uniform and alignment-centred, so it inscribes the content inside the
 * rectangle and leaves the rectangle's own area unpainted — as tile-coloured bands down the
 * left and right of the growing shape for as long as the two aspects differ, which is the
 * whole flight. `FillWidth` matches the width and overflows vertically, which covers only if
 * the overlay's clip is there to trim the overflow. `FillBounds` maps width to width and
 * height to height, so the surface is the rectangle exactly, at every frame, with nothing
 * left over for the clip to trim and nothing of the rectangle left showing.
 *
 * The clip is still needed, and only for the corners: see [TdayTileCornerClip].
 */
private val TdayTileResizeMode: SharedTransitionScope.ResizeMode =
    SharedTransitionScope.ResizeMode.ScaleToBounds(ContentScale.FillBounds, Alignment.Center)

/**
 * What the surface does with its own opacity while the rectangle travels.
 *
 * `sharedElement` takes no enter or exit at all, which is why the first shape cut: the end
 * that was not arriving drew NOTHING for the whole transition, so the tile vanished on the
 * push's first frame and the screen vanished on the pop's first frame, with no faded pixels
 * in between for the eye to follow. `sharedBounds` draws the ends it is given, each in the
 * overlay with its own alpha, and these are that alpha for the surface.
 *
 * `Durations.Enter` for both, with the vocabulary's decelerate curve arriving and its
 * accelerate curve leaving, for two reasons that agree. It is the rung the route
 * hand-over itself runs on (see `navigationEnterTransition` in `TdayApp.kt`), so the
 * surface's copy of the destination and the destination's own arrival are on ONE clock
 * rather than two, which is the difference between a surface opening and two events; and
 * it is the rung the TILE leaves on, so the tile's own icon, label and count fade out
 * under a surface that is rising over them at the same rate, and the two blend rather
 * than cut.
 *
 * What this fade is NOT is the whole story of the surface's opacity, and the paragraph
 * that used to stand here claimed it was: it read "on `Springs.settle` the rectangle is
 * about 95% of the way at exactly that length, so neither half is left visibly waiting on
 * the other", which is not what the spring does. Solving the token's own numbers —
 * stiffness 250, damping 0.86 — gives 19.9% at 50 ms, 51.0% at 100 ms, 89.2% at 200 ms
 * and 99.4% at 300 ms, so at the length of this fade the rectangle still has about a
 * tenth of the screen's width and height left to cover, and the four edges settle
 * independently. The fades below are one clock; the TRAVEL is another, and the surface's
 * opacity needs both — see [TdayTileSurface], where the second half of the opacity is
 * the hand-over that closes that gap.
 *
 * No new number: both are existing tokens, and both are named rather than left to the
 * library's `fadeIn()`/`fadeOut()` defaults, which would be a bare Compose spec in a
 * counted file.
 */
private val TdayTileEnter: EnterTransition = fadeIn(
    animationSpec = tween(
        durationMillis = TdayMotionTokens.Durations.Enter,
        easing = TdayMotionTokens.Easings.Enter,
    ),
)

private val TdayTileExit: ExitTransition = fadeOut(
    animationSpec = tween(
        durationMillis = TdayMotionTokens.Durations.Enter,
        easing = TdayMotionTokens.Easings.Exit,
    ),
)

/**
 * Publishes THIS RECTANGLE as the one the screen it opens grows out of, and draws
 * nothing at all.
 *
 * Applied to an empty box that matches the tile's own bounds, a SIBLING of the tile's
 * `Card` rather than the Card itself — which is the whole point. What a shared bounds
 * node contributes is whatever is composed inside it, so a tile that put this on its Card
 * would be handing the library its icon, its label and its watermark to carry and scale.
 * Put on a sibling that paints none of them, the tile end contributes the rectangle and
 * only the rectangle: the tile is then free to leave on the route's own fade like any
 * other content, and the screen has somewhere honest to come from.
 *
 * No `OverlayClip` here and none wanted: the node renders in place, never in the overlay
 * (`renderInOverlay = false`), so no clip is ever consulted, and the shape a reader would
 * expect to see it clipped to is the tile's `Card`'s own `RoundedCornerShape`, which is
 * drawn by the Card.
 *
 * A no-op when [key] is null, when there is no namespace or no source scope above it,
 * or when motion is refused — see the file comment.
 */
@Composable
fun Modifier.tdayTileTransitionSource(key: String?): Modifier =
    tdaySharedBounds(
        key = key,
        animatedVisibilityScope = LocalTdayTileSourceScope.current,
        // The rectangle, not pixels. Nothing is composed inside this node, so there is
        // nothing an overlay copy could add, and keeping it out of the overlay keeps the
        // node inside its parent's clip the way a tile is.
        renderInOverlay = false,
        // Unused at this end and passed for the same reason the argument is not defaulted:
        // a reader of either call site should be able to see what that end contributes
        // without looking up a library default. A rectangle with square corners is the
        // honest description of a node that paints nothing.
        cornerFraction = null,
    )

/**
 * Marks this screen as the destination a tile grows into, and gives it the
 * `AnimatedVisibilityScope` it is transitioning in — which is the receiver, because a
 * `composable { }` block's lambda IS an `AnimatedVisibilityScope` (`AnimatedContentScope`
 * extends it). Nothing has to be threaded in from outside for the destination half.
 *
 * [route] is the route this destination was opened as, and the key is read from it, so
 * the two ends cannot be handed different answers.
 *
 * The SCREEN IS NOT THE SHARED ELEMENT. [TdayTileSurface] is, and it is laid out first so
 * that when no transition is playing it sits UNDER the screen and paints nothing anybody
 * can see; while a transition is playing it is drawn in the shared scope's overlay, above
 * everything, and the screen underneath it is the ordinary screen. That ordering is the
 * whole reason `content()` can be a plain sibling: what the user asked for is the screen
 * reaching its final layout and fading in as the surface covering it arrives, and a screen
 * that is inside the shared element cannot do either — it would be measured once and scaled,
 * or re-measured every frame.
 */
@Composable
fun AnimatedVisibilityScope.TdayTileDestination(
    route: AppRoute,
    fromHomeTile: Boolean,
    tileColor: Color?,
    listId: String? = null,
    highlighted: Boolean = false,
    scope: CompletedScope? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        TdayTileSurface(
            key = route.tileTransitionKey(
                listId = listId,
                highlighted = highlighted,
                scope = scope,
                fromHomeTile = fromHomeTile,
            ),
            animatedVisibilityScope = this@TdayTileDestination,
            tileColor = tileColor,
        )
        content()
    }
}

/**
 * The surface a tile grows into: a full-size filled rectangle in the TILE'S OWN COLOUR, and
 * the whole of what the destination end of a shared element contributes.
 *
 * It carries nothing but the fill. Not one pixel of the screen is inside it, which is the
 * property this file exists to hold — see the file comment.
 *
 * THE FILL IS THE TILE'S COLOUR. It used to be `colorScheme.background` — the colour every
 * screen's `Scaffold` is filled with — and that is the one colour the surface must not be.
 * A rectangle painted in the screen's own background is invisible against the screen it is
 * travelling over and visible only against the CONTENT it covers, so the eye reads a flat
 * near-white box appearing over a list rather than a tile growing into one; on a light theme
 * that box is white. The device row has said so since this transition was rebuilt — item (a)
 * of `docs/verification/phase-9-device-pass.md`'s PR 32e entry asks for "the SURFACE for the
 * first frames past the tile should be the tile's colour at the tile's radius, squaring off
 * as it reaches the screen" — and the colour has to be HANDED here, because it is the only
 * thing about the tile that no route carries: see [TILE_TRANSITION_COLOR] for why it cannot
 * be looked up and where it travels.
 *
 * It is lerped to the screen's background as the rectangle lands, on the same fraction the
 * corners ride, so the surface is the tile at the tile and the screen's own background where
 * it becomes the screen. Both ends of the flight are then invisible against what they sit
 * on: a solid rectangle in the tile's colour left congruent with the screen would be a lid
 * over a screen that has already arrived, and on the way back a solid rectangle in the
 * tile's colour sitting on the tile for the last frames would be a lid over the tile. The
 * lerp costs one draw-phase `lerp` per frame and is what makes the two ends of one surface
 * belong to the two things it is standing in for.
 *
 * THE OPACITY HAS A DEPARTURE AS WELL AS AN ARRIVAL, AND THE DEPARTURE IS WHAT REMOVES THE
 * DOUBLE-TAKE. The shared bounds' own enter fade (see [TdayTileEnter]) is a rise, and it has
 * to be: the tile's icon, its label and its count are not inside this node, so a surface that
 * was solid on the first frame would flatten them instead of letting them fade where they
 * sit. But a rise is not enough on its own, because the rectangle's geometry runs longer than
 * the fades do: `Springs.settle` is 89% of the way at `Durations.Enter` and only lands at
 * about `Durations.Emphasis`. A surface that is still solid when the rectangle is the screen
 * is drawn in the overlay ABOVE the screen (that is what `renderInOverlay` buys, and it is
 * why the surface is visible at all), so the screen it grew over is hidden behind it and then
 * uncovered in one frame when the entry goes — "the screen loads again". So the opacity gets
 * a second half: it hands over, falling to zero on `Durations.Emphasis` — the rung whose
 * length the rectangle's own arrival takes — with `Easings.Exit`, the vocabulary's curve for
 * something committing rather than drifting off. The surface is then gone by the time the
 * rectangle is congruent, and the destination is revealed by a dissolve instead of a cut.
 *
 * The hand-over is a property of the DIRECTION and not of the rectangle, which is the one
 * thing about it that reads oddly and is not an accident. A push wants a surface that is
 * solid near the tile and gone near the screen; a pop wants one that is solid near the screen
 * and gone near the tile. Same fraction, opposite ends — so no function of the rectangle's
 * progress can express both, and the way back keeps the plain exit fade it has always had
 * (the open, reversed) while the way in gets the hand-over on top of its rise.
 *
 * The fill is on an inner box rather than on the shared node itself on purpose: modifiers
 * applied ABOVE `sharedBounds` in the chain draw outside the layer the library records and
 * hands to the overlay, so a fill there would be painted in place, under the screen and
 * outside the animation, and the rectangle in the overlay would still be empty. Anything
 * composed INSIDE the node is what travels — which is also why the hand-over rides a
 * `graphicsLayer` on this box rather than a modifier above the node.
 *
 * Composed at all only when [key] is non-null. A destination nobody pressed a tile for — a
 * deep link, a widget row, a shortcut — has no rectangle to grow out of, and a full-screen
 * background behind the screen for the life of that destination is a draw nobody asked for.
 */
@Composable
private fun TdayTileSurface(
    key: String?,
    animatedVisibilityScope: AnimatedVisibilityScope,
    tileColor: Color?,
) {
    if (key == null) return
    // One fraction for the corners, riding the same `Springs.settle` the rectangle rides so
    // that the corner closes at the rate the rectangle opens. It is read as a `State` and
    // never in composition, so nothing recomposes per frame — the fraction is pulled in the
    // draw phase, by the clip, where the rectangle itself is already being pulled per frame.
    // `settle` with no threshold: what is animated here is a 0..1 fraction, and the token
    // factory keeps `visibilityThreshold` a parameter precisely because the answer is about
    // the units — for a fraction, Compose's own default is the answer.
    val cornerFraction = animatedVisibilityScope.transition.animateFloat(
        transitionSpec = { TdayMotionTokens.Springs.settle<Float>() },
        label = "tdayTileSurfaceCorner",
    ) { state ->
        // Visible is the screen's own rectangle, which has square corners; anything on the
        // way in or out is somewhere between the tile and the screen, so it takes the
        // tile's. Written as the negative case so a state this file has never heard of
        // rounds rather than squares — a rounded corner over a tile is invisible, and a
        // square one over a tile is a pop.
        if (state == EnterExitState.Visible) 0f else 1f
    }
    // The hand-over, and the mirror of the fraction above: one exactly at the tile, zero
    // exactly at the screen, and a fraction in between. It does NOT share the fraction's
    // spring, and the reason is the one thing about this value worth knowing. A fall on the
    // same clock as the rise is the same shape in both directions, so the two cancel: the
    // surface would be at half opacity exactly where it is at half its size, which is a wash
    // over the screen rather than a surface growing out of the tile. Held on `Emphasis` with
    // the accelerate curve instead, it is still at nearly nine tenths when the rectangle is
    // halfway and reaches zero just before the rectangle lands.
    val handOver = animatedVisibilityScope.transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = TdayMotionTokens.Durations.Emphasis,
                easing = TdayMotionTokens.Easings.Exit,
            )
        },
        label = "tdayTileSurfaceHandOver",
    ) { state ->
        if (state == EnterExitState.Visible) 0f else 1f
    }
    // Which half of the flight this is. The destination's own transition is the only thing
    // that knows: `Visible` as a target is a push (or a screen already settled), `PostExit`
    // is the way back — and the way back is the one where the surface keeps the exit fade it
    // was always given instead of handing over.
    val arriving = animatedVisibilityScope.transition.targetState != EnterExitState.PostExit
    val screenColor = MaterialTheme.colorScheme.background
    // The tile's colour where one arrived, and the screen's own background where one did
    // not: a push site that sent no colour leaves this exactly as the surface was, rather
    // than leaving no surface at all.
    val tileFill = tileColor ?: screenColor
    Box(
        modifier = Modifier
            .fillMaxSize()
            .tdaySharedBounds(
                key = key,
                animatedVisibilityScope = animatedVisibilityScope,
                renderInOverlay = true,
                cornerFraction = cornerFraction,
            ),
    ) {
        // Composed only where a shared node was actually installed — the same gate, read
        // through the same function `tdaySharedBounds` reads it through. A fill with no node
        // under it is a full-screen rectangle at the destination's own layout position, and
        // the node is what lifts it into the overlay; without one it sits UNDER the screen
        // and is hidden only while the screen is opaque. During the route's own fade — and
        // for the whole of a hand-over with motion refused — the screen is not yet opaque, so
        // a tile-coloured rectangle there is a wash of the tile over a screen the user asked
        // to see arrive plainly. It used to be the app's background, which is why nobody
        // noticed; see the file comment's note on Reduce Motion.
        if (rememberTdaySharedScope(animatedVisibilityScope) != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Both reads are draw-phase and neither recomposes anything per frame:
                    // the alpha is pulled where the overlay is already drawing the
                    // rectangle, and the colour is pulled where it is already being painted.
                    .graphicsLayer { alpha = if (arriving) handOver.value else 1f }
                    .drawBehind {
                        drawRect(color = lerp(screenColor, tileFill, cornerFraction.value))
                    },
            )
        }
    }
}

/**
 * The corner of the travelling surface: the tile's radius at the tile, square at the
 * screen, and whatever is between those two on every frame in between.
 *
 * The clip is needed because the surface's own content is a rectangle with square corners.
 * It cannot be a background with a rounded shape instead: the shape would have to be
 * fixed, and a fixed radius is one of only two wrong answers — the tile's 26.dp held all
 * the way over a full screen reads as a rounded screen that snaps square at the end of
 * every open, and a square corner at the tile reads as the tile's own radius being taken
 * away on the first frame. Only a radius that travels with the rectangle is right at both
 * ends, so the radius is a function of the animation and the clip is where the two meet.
 *
 * A `Path` is built rather than a `Shape` handed to the library's `OverlayClip(shape)`
 * factory for one reason: the factory takes a shape at CONSTRUCTION time, so a morphing
 * radius would mean rebuilding the whole shared-bounds modifier every frame. Reading the
 * fraction here instead keeps the modifier stable for the life of the transition and moves
 * only the path, which is rebuilt on every draw anyway.
 *
 * The path is built over the rectangle's own size at the origin and then translated to its
 * top-left, which is the contract `OverlayClip` documents: the rectangle arrives in the
 * scope's coordinate space, and the path has to be handed back in the same one.
 */
private class TdayTileCornerClip(
    private val cornerFraction: State<Float>,
) : SharedTransitionScope.OverlayClip {

    private val path = Path()

    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path {
        val radius = with(density) {
            (TdayDimens.RadiusCard * cornerFraction.value).toPx()
        }
        path.reset()
        path.addRoundRect(
            RoundRect(
                rect = Rect(Offset.Zero, bounds.size),
                cornerRadius = CornerRadius(radius, radius),
            ),
        )
        path.translate(bounds.topLeft)
        return path
    }
}

/**
 * Whether the push that opened [entry] was a home tile press, read from the flag
 * [navigateFromHomeTile] left on the screen underneath.
 *
 * The route alone cannot answer this, and neither can the back stack: the entry below a
 * tile press and the entry below a warm shortcut, notification or widget push are the same
 * home entry, with the same tiles composed on it. So the answer is carried from the push
 * site, where it was known, to here, where it is needed — the same `savedStateHandle`
 * hand-off the All screen's search highlight uses, including the `remove`: taking the flag
 * away as it is read is what keeps it describing THIS push rather than every later one.
 *
 * `remember(entry)` and not a bare read, for the reason `remove` gives: the read consumes
 * the flag, so a recomposition must not perform it twice. Keying on `entry` also means a
 * re-entry into the same destination re-reads, which is right — it is a new arrival.
 *
 * No default and no reading of the route: a destination that has not answered this question
 * does not compile, which is the half of the guard a test cannot hold. [TileTransitionKeyTest]
 * holds the other half — that no route in the table names a key when this is false.
 */
@Composable
fun rememberHomeTileOrigin(navController: NavController, entry: NavBackStackEntry): Boolean =
    remember(entry) {
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.remove<Boolean>(TILE_TRANSITION_ORIGIN) == true
    }

/**
 * The colour of the tile that opened [entry], read from the value [navigateFromHomeTile] left
 * beside the origin flag on the screen underneath — or null when the push was not a tile
 * press at all, or was one from a push site that predates the colour.
 *
 * A sibling of [rememberHomeTileOrigin] rather than part of it, and deliberately: the two
 * answer different questions and the surface treats a missing answer to each differently. A
 * missing origin means there is no zoom at all (the key comes back null and no surface is
 * composed); a missing colour means there is a zoom whose surface keeps the colour it used to
 * have. Folding them into one read would make the second look like a verdict on the first.
 *
 * Same `remember(entry)`, same `remove`, same reason: the read consumes the value, so it must
 * not happen twice, and a re-entry into the same destination is a new arrival and re-reads.
 * See [TILE_TRANSITION_COLOR] for why the colour cannot be looked up from the route, and
 * [navigateFromHomeTile] for the push sites that send it.
 */
@Composable
fun rememberHomeTileColor(navController: NavController, entry: NavBackStackEntry): Color? =
    remember(entry) {
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.remove<Int>(TILE_TRANSITION_COLOR)
            ?.let { argb -> Color(argb) }
    }

/**
 * The namespace a zoom can be installed in, or null when there is no zoom to be had: no
 * `TdayTileTransitionLayout` above this point, no visibility scope to participate in, or
 * motion refused.
 *
 * Read by BOTH the modifier that installs the shared node and the surface that fills it, and
 * that is the whole reason it is a function rather than two copies of an `if`. The two have
 * to agree, and they are not merely adjacent: `Modifier.tdaySharedBounds` decides whether the
 * node exists, and [TdayTileSurface] decides whether there is anything inside it — a fill
 * with no node under it is not a surface at all, it is a full-screen rectangle composed in
 * place behind the destination, which is invisible only for as long as it happens to match
 * that screen's own background. It does not match it any more; that is the colour half of
 * this change.
 */
@Composable
private fun rememberTdaySharedScope(
    animatedVisibilityScope: AnimatedVisibilityScope?,
): SharedTransitionScope? {
    val sharedTransitionScope = LocalTdaySharedTransitionScope.current
    val motionEnabled = rememberTdayMotionEnabled()
    return if (animatedVisibilityScope != null && sharedTransitionScope != null && motionEnabled) {
        sharedTransitionScope
    } else {
        null
    }
}

/**
 * The one place a shared element is installed, for both ends.
 *
 * Everything that can be missing is checked here once: no key (a route with nothing on
 * screen to grow out of), no namespace (drawn outside [TdayTileTransitionLayout] — a
 * preview, the pre-graph splash), no visibility scope, or motion refused. Any one of them
 * means the modifier is not installed and the route change plays as it always did, which
 * is the fallback `docs/motion.md` asks for: the trip is removed, and the destination is
 * still handed over.
 *
 * Every decision that differs between the two halves is a parameter and both are passed
 * at the call sites above rather than defaulted, so what each end contributes is readable
 * where the end is declared: [renderInOverlay] is true only for the destination, and the
 * clip is only meaningful where that is true.
 */
@Composable
private fun Modifier.tdaySharedBounds(
    key: String?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    renderInOverlay: Boolean,
    cornerFraction: State<Float>?,
): Modifier {
    val sharedTransitionScope = rememberTdaySharedScope(animatedVisibilityScope)
    // The visibility scope is re-checked for the compiler's sake: `rememberTdaySharedScope`
    // has already refused a null one, but a smart cast does not reach back out of a function
    // call, and the check costs nothing beside the call that just made it.
    val bounds = if (
        key != null &&
        animatedVisibilityScope != null &&
        sharedTransitionScope != null
    ) {
        with(sharedTransitionScope) {
            this@tdaySharedBounds.sharedBounds(
                sharedContentState = rememberSharedContentState(key),
                animatedVisibilityScope = animatedVisibilityScope,
                enter = TdayTileEnter,
                exit = TdayTileExit,
                boundsTransform = TdayTileBoundsTransform,
                resizeMode = TdayTileResizeMode,
                renderInOverlayDuringTransition = renderInOverlay,
                // A morphing radius where the destination supplies one, and a plain
                // rectangle where it does not — which is the source, which never draws in
                // the overlay and so never consults this at all.
                clipInOverlayDuringTransition = cornerFraction
                    ?.let { TdayTileCornerClip(it) }
                    ?: OverlayClip(RectangleShape),
            )
        }
    } else {
        Modifier
    }
    return this.then(bounds)
}
