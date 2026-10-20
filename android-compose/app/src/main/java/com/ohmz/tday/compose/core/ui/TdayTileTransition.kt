@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavBackStackEntry
import com.ohmz.tday.compose.core.navigation.AppRoute
import com.ohmz.tday.compose.core.navigation.CompletedScope
import com.ohmz.tday.compose.core.navigation.isHomeTileArrival
import com.ohmz.tday.compose.core.navigation.tileTransitionKey
import com.ohmz.tday.compose.ui.theme.TdayDimens

/**
 * The tile→screen zoom: the Compose half of what iOS does with
 * `.matchedTransitionSource` + `.navigationTransition(.zoom(...))`.
 *
 * iOS's zoom grows the SCREEN out of the rectangle the user pressed. The destination is laid out
 * at its real size, drawn scaled down into the tile's frame and clipped to the tile's corners,
 * and the frame springs open to the full screen with the screen inside it — the tile's own
 * content dissolving into the screen's as it goes. The screen underneath does not move: the new
 * one opens over it. Backing out runs the same zoom in reverse, into the tile.
 *
 * This file is that, on the ten rectangles a tile push can start from: the six scheduled-board
 * category tiles, that board's Today card and custom list rows, and the Anytime feed's Completed
 * entry and custom list rows.
 *
 * - The SOURCE ([tdayTileTransitionSource]) publishes the tile's rectangle and draws nothing. The
 *   tile's `Card` is already painting that rounded rectangle in place, so the icon, label and
 *   count stay exactly where the user pressed them and the screen fades in over them.
 * - The DESTINATION ([TdayTileDestination]) puts the whole screen inside the shared bounds, in
 *   [TdayTileResizeMode]: measured once at its final size and drawn scaled into the travelling
 *   rectangle, so nothing re-lays-out mid-flight and no component is ever drawn larger than it
 *   will finally be. [TdayTileCornerClip] rounds it to the tile's radius at the tile and squares it
 *   off at the screen, and [TdayTileFlight] measures where the rectangle is between the two so the
 *   corner and the close's final dissolve stay with it rather than with a clock.
 * - The NAVHOST holds the home screen still under a tile push ([TdayTileZoomHold]) and shows it at
 *   once under a tile pop, instead of crossfading it, so the screen grows over a home screen that
 *   stays put — the only way the zoom can read as coming out of the tile rather than out of a
 *   blank window. [isHomeTileArrival] is how it knows a push or pop is a tile zoom. Once the
 *   screen has grown over all of it, home stops drawing ([tdayTileCoveredScreen]) until a pop.
 *
 * WHERE THE SCREEN CAME FROM IS A PUSH SITE'S FACT, NOT THE ROUTE'S. A tile route is reachable
 * without a press — a shortcut, a notification, a widget row — with home still composed
 * underneath, so a key that matched the route alone would grow the screen out of a rectangle
 * nobody touched. `navigateFromHomeTile` marks the entry it pushes, and [rememberHomeTileOrigin]
 * reads that mark at the destination, where [TdayTileDestination] will not compile without it.
 *
 * REDUCE MOTION. Every piece reads [rememberTdayMotionEnabled], symmetrically: with motion refused
 * no shared node is installed at either end, the NavHost keeps its ordinary short hand-over, and
 * the tile simply does not grow — `docs/motion.md`'s fifth idiom rule removes the trip and keeps
 * the destination.
 */
val LocalTdaySharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * The `AnimatedVisibilityScope` of the destination the tiles live on — `home` for all ten
 * surfaces. Null outside it, which is also the honest answer for a preview, a widget surface, or
 * anything else drawn without the graph above it.
 */
val LocalTdayTileSourceScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Each tile's width in pixels, by the key it publishes: the tile end of the scale [TdayTileFlight]
 * measures the flight on. Written by the sources as they are laid out and read by the destinations
 * in the draw phase. Entries are never removed: a pop reads the width in the frame home is laid
 * out again, and a stale entry for a deleted list is one nobody asks for.
 */
private val LocalTdayTileWidths = staticCompositionLocalOf<SnapshotStateMap<String, Float>?> { null }

/**
 * Whether a tile's screen has grown to cover the whole window — the cue for the screen underneath
 * ([tdayTileCoveredScreen]) to stop drawing itself.
 *
 * The NavHost holds home under an opening zoom for `Scene` and fades it for `Quick` after that
 * ([TdayTileZoomHold]): 670 ms of home drawn in full on every frame, when the spring has the
 * screen over all of it by about 320. Every frame after that paid for two whole screens and showed
 * one — home's rows, tiles and their shadows re-rendered under an opaque screen, and in its last
 * `Quick` into an offscreen layer for a fade nobody could see. On a phone whose GPU is already
 * the bottleneck, that was most of the zoom's second half spent on a screen no one could see.
 *
 * Read off the rectangle, like the corner, rather than off a clock: covered means the flight
 * measured it at the screen's full size on the way IN. A pop is never covered, from its first
 * frame — its target is set before home's first frame back is drawn — so the screen shrinking
 * away always has home under it. Being wrong in the other direction only costs a frame of drawing
 * something hidden, so every doubt resolves to "not covered".
 */
private class TdayTileCover {
    /** The installed destination's answer; null when no zoom is installed. */
    var covering: State<Boolean>? by mutableStateOf(null)

    val coversScreen: Boolean get() = covering?.value == true
}

private val LocalTdayTileCover = staticCompositionLocalOf<TdayTileCover?> { null }

/**
 * Wraps the NavHost so every tile and destination underneath is matched in one namespace, and
 * re-publishes the scope — Compose 1.7 has no `LocalSharedTransitionScope` to read it back from,
 * and the tiles are built two files away from the graph behind private composables.
 */
@Composable
fun TdayTileTransitionLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tileWidths = remember { mutableStateMapOf<String, Float>() }
    val cover = remember { TdayTileCover() }
    SharedTransitionLayout(modifier = modifier) {
        CompositionLocalProvider(
            LocalTdaySharedTransitionScope provides this,
            LocalTdayTileWidths provides tileWidths,
            LocalTdayTileCover provides cover,
        ) {
            content()
        }
    }
}

/**
 * How the rectangle travels between the tile and the screen — chosen by direction, because only
 * one direction is ever driven by a finger.
 *
 * OPENING rides [TdayMotionTokens.Springs.settle], the vocabulary's slot for something heavy
 * coming to rest. Solving its numbers (stiffness 250, damping 0.86) gives 51% of the way at
 * 100 ms, 89% at 200 ms and 99% at 300 ms, with about half a percent of overshoot — which
 * [TdayTileCornerClip] rides rather than fights, because it is rebuilt from the rectangle's own
 * size on every draw.
 *
 * CLOSING rides [tdayTileCloseSpec], because a close can be the predictive-back gesture, and the
 * NavHost does not play that animation: it SEEKS it, mapping the thumb's progress linearly onto
 * the transition's total duration. A spring's duration is estimated out to its visibility
 * threshold — half a pixel here — which puts this one's end near 630 ms, so a seek spent the
 * whole visible shrink in the first third of the swipe (measured: half-way at 15% of it, landed
 * by 40%) and the rest of the drag showed nothing. The tween answers the thumb at once and keeps
 * moving to the end of it — 50% of the way at a fifth of the swipe, 84% at half, 95% at seven
 * tenths — and played by the back button it runs 66/91/100% at 100/200/300 ms against the
 * spring's 51/89/99%, so the button's close barely changes.
 *
 * Direction is the only thing this transform is handed that can separate the two. Compose 1.7.6
 * cannot say whether a transition is being seeked: `Transition.isSeeking` is set by the tooling's
 * `seek` and never by the `SeekableTransitionState` the NavHost drives, and the NavHost's own
 * predictive-back state is private.
 */
private val TdayTileBoundsTransform = BoundsTransform { initialBounds, targetBounds ->
    val closing = targetBounds.width * targetBounds.height <
        initialBounds.width * initialBounds.height
    if (closing) {
        tdayTileCloseSpec()
    } else {
        TdayMotionTokens.Springs.settle(visibilityThreshold = Rect.VisibilityThreshold)
    }
}

/**
 * The close's clock, shared by the rectangle and its corners so the two land together — see
 * [TdayTileBoundsTransform] for why it is a tween and not the opening's spring. `Emphasis`, the
 * rung for a size changing; `Enter`, the decelerate curve, so the screen starts toward the tile
 * the moment back is asked for and settles into it rather than stopping.
 */
private fun <T> tdayTileCloseSpec(): FiniteAnimationSpec<T> = tween(
    durationMillis = TdayMotionTokens.Durations.Emphasis,
    easing = TdayMotionTokens.Easings.Enter,
)

/**
 * How the screen is fitted into the travelling rectangle.
 *
 * `scaleToBounds` measures the screen ONCE, at its final size, and then only scales it into the
 * rectangle — the zoom iOS draws. `RemeasureToBounds` is the other answer and the wrong one for a
 * whole screen: it re-lays-out every frame at the rectangle's size, so the toolbar, rows and
 * empty state keep their own size inside a window that is still growing, which is what reads as
 * giant components climbing out of a tile.
 *
 * `Crop` so the rectangle is always covered: a tile is far wider for its height than a phone
 * screen, and a min-based fit would leave bands of it unpainted. `TopCenter` so what shows inside
 * a small rectangle is the top of the screen — its toolbar and title, the part that answers to the
 * label the user pressed — with the overflow below trimmed by [TdayTileCornerClip].
 */
private val TdayTileResizeMode: SharedTransitionScope.ResizeMode =
    SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Crop, Alignment.TopCenter)

/**
 * The screen's opacity on the way in: over the first `Quick` of the spring, while the rectangle
 * is still close to the tile's size, with the decelerate curve. The tile is fully drawn underneath
 * until then, so the tile's icon, label and count dissolve into the screen's toolbar and title
 * inside the same rounded rectangle, rather than the screen popping over the tile on its first
 * frame.
 */
private val TdayTileScreenEnter: EnterTransition = fadeIn(
    animationSpec = tween(
        durationMillis = TdayMotionTokens.Durations.Quick,
        easing = TdayMotionTokens.Easings.Enter,
    ),
)

/**
 * The same dissolve on the way back, on a clock, for a destination with no [TdayTileFlight] to
 * measure the landing by. The delay is `Quick` so the dissolve is done at 300 ms, as
 * [tdayTileCloseSpec]'s rectangle reaches the tile at 320. A flight exists wherever a zoom is
 * installed, so in practice its own landing ([TdayTileFlight.landingAlpha]) is what plays.
 */
private val TdayTileScreenExit: ExitTransition = fadeOut(
    animationSpec = tween(
        durationMillis = TdayMotionTokens.Durations.Quick,
        delayMillis = TdayMotionTokens.Durations.Quick,
        easing = TdayMotionTokens.Easings.Exit,
    ),
)

/**
 * Where the travelling rectangle is between the tile and the screen — 1 at the tile, 0 at the
 * screen — measured from its WIDTH on every frame it is drawn in the overlay, and the two things
 * that have to stay with it: the corner radius, and the close's final dissolve into the tile.
 *
 * WHY GEOMETRY AND NOT A CLOCK. Measured: a pop cannot start the rectangle toward the tile until
 * home has been laid out again under it, which costs the first frames, while every animation on
 * the transition has already been running. A dissolve on that clock ran ahead of the rectangle it
 * belongs to, so the screen went translucent while it was still twice the tile's size, its title
 * doubled over the tile's label and the tiles around it; and a corner on a clock of its own was
 * nearly square before the opening rectangle had grown past the tile. Read off the rectangle
 * itself, both stay with it on any device, through any stall, and under a back swipe that holds it
 * half-way.
 *
 * The bounds are a straight interpolation, so the width's fraction of the way is the height's too
 * and one number describes the whole rectangle. The close dissolves over the last TENTH of the
 * way, a fraction of the flight and not a duration, so not a motion token: the height closes on
 * the tile's across four times the width's distance, so at 80% of the way the rectangle is still
 * four tiles tall, and a dissolve that started there spilled the screen over the row beneath.
 *
 * The progress is written from the overlay clip ([TdayTileCornerClip]), which is handed the
 * rectangle's bounds each frame, and read by the content's layer, which records before the overlay
 * clips it — so the dissolve is one frame behind the rectangle; the corner, read in the clip
 * itself, is not. It starts at 1: the opening's first frame is drawn at the tile.
 */
private class TdayTileFlight(
    private val key: String,
    private val tileWidths: SnapshotStateMap<String, Float>,
) {
    var screenWidthPx = 0f
    private val progress = mutableFloatStateOf(1f)

    fun track(rectWidthPx: Float) {
        val tileWidthPx = tileWidths[key] ?: return
        val span = screenWidthPx - tileWidthPx
        if (span <= 0f) return
        progress.floatValue = ((screenWidthPx - rectWidthPx) / span).coerceIn(0f, 1f)
    }

    /** 1 at the tile, 0 at the screen: the share of the tile's corner radius to draw. */
    val cornerFraction: Float get() = progress.floatValue

    /** Opening is faded in on a clock ([TdayTileScreenEnter]); only the landing is measured. */
    fun landingAlpha(closing: Boolean): Float =
        if (closing) 1f - ((progress.floatValue - 0.9f) / 0.1f).coerceIn(0f, 1f) else 1f

    /**
     * The rectangle has reached the screen's full size. Its bounds are one straight interpolation,
     * so the width arriving is the height and the position arriving too; and the opening's fade
     * ([TdayTileScreenEnter], `Quick`) is long done by then, so the screen is opaque over all of it.
     */
    val landed: Boolean get() = progress.floatValue <= 0f
}

/**
 * What the NavHost gives the screen UNDER a tile zoom: it holds, fully opaque, for `Scene`, and
 * only then fades, underneath a screen that is already on top of it, so the fade is never seen.
 *
 * `Scene` and not `Emphasis`, measured rather than assumed: at 10x animator scale the rectangle's
 * edges are still a few percent short of the screen's at `Emphasis`, and a hold that ends there
 * shows a strip of home fading along the top and bottom edges just before the zoom reaches them.
 * The spring is inside half a pixel by about `Scene`.
 *
 * It exists because the NavHost otherwise crossfades every route, and a home screen fading out
 * while the tile grows leaves the growing screen opening over an empty window. What Compose 1.7
 * has for "keep the old screen until the new one is done" (`ExitTransition.KeepUntilTransitionsFinished`)
 * is internal until a later release, so the hold is spelled as the fade it ends in.
 */
val TdayTileZoomHold: ExitTransition = fadeOut(
    animationSpec = tween(
        durationMillis = TdayMotionTokens.Durations.Quick,
        delayMillis = TdayMotionTokens.Durations.Scene,
        easing = TdayMotionTokens.Easings.Exit,
    ),
)

/**
 * Publishes THIS RECTANGLE as the one the screen it opens grows out of, and its width as the tile
 * end of [TdayTileFlight]'s scale. Draws nothing at all.
 *
 * Applied to an empty sibling that matches the tile's own bounds rather than to the tile's `Card`:
 * what a shared bounds node contributes is whatever is composed inside it, and the tile's icon,
 * label and watermark must stay where they are — they are what the screen dissolves over.
 *
 * A no-op when [key] is null, when there is no namespace or no source scope above it, or when
 * motion is refused.
 */
@Composable
fun Modifier.tdayTileTransitionSource(key: String?): Modifier {
    val tileWidths = LocalTdayTileWidths.current
    val publish = if (key != null && tileWidths != null) {
        Modifier.onSizeChanged { size -> tileWidths[key] = size.width.toFloat() }
    } else {
        Modifier
    }
    return this.then(publish).tdaySharedBounds(
        key = key,
        animatedVisibilityScope = LocalTdayTileSourceScope.current,
        // Nothing is composed inside this node, so there is nothing an overlay copy could add,
        // and keeping it in place keeps it inside its parent's clip the way the tile is.
        renderInOverlay = false,
        enter = EnterTransition.None,
        exit = ExitTransition.None,
        cornerFraction = null,
        flight = null,
    )
}

/**
 * For the screen the tiles live on: draws nothing while a tile's screen covers the whole window
 * (see [TdayTileCover]), and itself the rest of the time. Only drawing is skipped — the screen stays
 * composed and laid out, so a pop has it in place on its first frame.
 */
@Composable
fun Modifier.tdayTileCoveredScreen(): Modifier {
    val cover = LocalTdayTileCover.current ?: return this
    return this.drawWithContent {
        if (!cover.coversScreen) drawContent()
    }
}

/**
 * Marks this screen as the destination a tile grows into. The receiver is the
 * `AnimatedVisibilityScope` the screen is transitioning in — a `composable { }` block's lambda is
 * one — so nothing has to be threaded in for the destination half.
 *
 * The screen itself is the shared element: [content] is composed INSIDE the shared bounds, which
 * draws it in the shared scope's overlay, scaled into the travelling rectangle, for as long as a
 * zoom is playing, and in place like any other screen the rest of the time. [content] sits at the
 * same position in the tree whether or not a zoom is installed, so the screen keeps its state if
 * motion is switched off while it is open.
 *
 * [route] is the route this destination was opened as, and the key is read from it, so the two
 * ends cannot be handed different answers.
 */
@Composable
fun AnimatedVisibilityScope.TdayTileDestination(
    route: AppRoute,
    fromHomeTile: Boolean,
    listId: String? = null,
    highlighted: Boolean = false,
    scope: CompletedScope? = null,
    content: @Composable () -> Unit,
) {
    val key = route.tileTransitionKey(
        listId = listId,
        highlighted = highlighted,
        scope = scope,
        fromHomeTile = fromHomeTile,
    )
    // Composed only when a zoom can actually be installed: an animation on this transition keeps
    // the route hand-over running until it settles, and a deep-link arrival has no corner to
    // animate.
    val zoomInstalled = key != null && rememberTdaySharedScope(this) != null
    val cornerFraction = if (zoomInstalled) {
        // The corner's clock, on the one the rectangle rides in each direction (see
        // TdayTileBoundsTransform). The clip draws the corner from the measured flight instead
        // wherever there is one (TdayTileFlight — always, once a zoom is installed); this stays as
        // its fallback. Pulled in the draw phase by the clip; nothing recomposes per frame. Written
        // as the negative case so a state this file has never heard of rounds rather than squares
        // — a rounded corner over a tile is invisible, a square one is a pop.
        transition.animateFloat(
            transitionSpec = {
                if (targetState == EnterExitState.PostExit) {
                    tdayTileCloseSpec()
                } else {
                    TdayMotionTokens.Springs.settle()
                }
            },
            label = "tdayTileCorner",
        ) { state -> if (state == EnterExitState.Visible) 0f else 1f }
    } else {
        null
    }
    val tileWidths = LocalTdayTileWidths.current
    val flight = if (zoomInstalled && key != null && tileWidths != null) {
        remember(key, tileWidths) { TdayTileFlight(key, tileWidths) }
    } else {
        null
    }
    val cover = LocalTdayTileCover.current
    if (flight != null && cover != null) {
        // Derived, so the screen underneath is invalidated when the answer flips and not on every
        // frame the rectangle moves.
        val covering = remember(flight) {
            derivedStateOf { transition.targetState == EnterExitState.Visible && flight.landed }
        }
        DisposableEffect(cover, covering) {
            cover.covering = covering
            onDispose { if (cover.covering === covering) cover.covering = null }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (flight != null) {
                    // The screen end of the flight's scale: this box's own size, which the overlay
                    // scales but never re-lays-out.
                    Modifier.onSizeChanged { size -> flight.screenWidthPx = size.width.toFloat() }
                } else {
                    Modifier
                },
            )
            .tdaySharedBounds(
                key = key,
                animatedVisibilityScope = this@TdayTileDestination,
                renderInOverlay = true,
                enter = TdayTileScreenEnter,
                // With a flight the landing is measured, not timed (TdayTileFlight.landingAlpha).
                exit = if (flight != null) ExitTransition.None else TdayTileScreenExit,
                cornerFraction = cornerFraction,
                flight = flight,
            )
            .then(
                if (flight != null) {
                    // Read in the draw phase, so nothing recomposes the screen per frame. Only while
                    // the zoom is moving: once it settles the screen is drawn as itself, whatever the
                    // last measured frame said.
                    Modifier.graphicsLayer {
                        alpha = if (transition.currentState == transition.targetState) {
                            1f
                        } else {
                            flight.landingAlpha(closing = transition.targetState == EnterExitState.PostExit)
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}

/**
 * The corner of the travelling screen: the tile's radius at the tile, square at the screen, and
 * whatever is between those two on every frame in between.
 *
 * A fixed radius is wrong at one end or the other — the tile's 26.dp held over a full screen reads
 * as a rounded screen that snaps square at the end of every open, and a square corner at the tile
 * takes the tile's own radius away on the first frame. So the radius is a function of the
 * animation, rebuilt into the path on every draw. A `Path` rather than the library's
 * `OverlayClip(shape)` because that factory takes its shape at construction time, and a morphing
 * radius would mean rebuilding the whole shared-bounds modifier every frame.
 */
private class TdayTileCornerClip(
    private val cornerFraction: State<Float>,
    private val flight: TdayTileFlight?,
) : SharedTransitionScope.OverlayClip {

    private val path = Path()

    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path {
        // The one place each frame's rectangle is handed over, so the flight is measured here, and
        // the corner follows it where there is one.
        flight?.track(bounds.width)
        val fraction = flight?.cornerFraction ?: cornerFraction.value
        val radius = with(density) {
            (TdayDimens.RadiusCard * fraction).toPx()
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
 * Whether the push that opened [entry] was a home tile press — the mark `navigateFromHomeTile`
 * leaves on the entry it pushes. It is read from the arriving entry itself, so it describes this
 * push and no other, and the NavHost's transitions read the same mark through
 * [isHomeTileArrival] to hold the screen underneath.
 *
 * No default and no reading of the route: a destination that has not answered this question does
 * not compile, which is the half of the guard a test cannot hold. `TileTransitionKeyTest` holds
 * the other half — that no route in the table names a key when this is false.
 */
@Composable
fun rememberHomeTileOrigin(entry: NavBackStackEntry): Boolean =
    remember(entry) { entry.isHomeTileArrival() }

/**
 * The namespace a zoom can be installed in, or null when there is no zoom to be had: no
 * [TdayTileTransitionLayout] above this point, no visibility scope to participate in, or motion
 * refused. Read by the modifier that installs the node and by the destination's corner animation,
 * so the two cannot disagree about whether a zoom exists.
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
 * The one place a shared element is installed, for both ends. A no-op when anything is missing —
 * no key, no namespace, no visibility scope, or motion refused — so the route change plays as it
 * always did: the trip is removed, and the destination is still handed over.
 */
@Composable
private fun Modifier.tdaySharedBounds(
    key: String?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    renderInOverlay: Boolean,
    enter: EnterTransition,
    exit: ExitTransition,
    cornerFraction: State<Float>?,
    flight: TdayTileFlight?,
): Modifier {
    val sharedTransitionScope = rememberTdaySharedScope(animatedVisibilityScope)
    val bounds = if (
        key != null &&
        animatedVisibilityScope != null &&
        sharedTransitionScope != null
    ) {
        with(sharedTransitionScope) {
            this@tdaySharedBounds.sharedBounds(
                sharedContentState = rememberSharedContentState(key),
                animatedVisibilityScope = animatedVisibilityScope,
                enter = enter,
                exit = exit,
                boundsTransform = TdayTileBoundsTransform,
                resizeMode = TdayTileResizeMode,
                renderInOverlayDuringTransition = renderInOverlay,
                // A morphing radius where the destination supplies one; a plain rectangle for
                // the source, which never draws in the overlay and so never consults it.
                clipInOverlayDuringTransition = cornerFraction
                    ?.let { TdayTileCornerClip(it, flight) }
                    ?: OverlayClip(RectangleShape),
            )
        }
    } else {
        Modifier
    }
    return this.then(bounds)
}
