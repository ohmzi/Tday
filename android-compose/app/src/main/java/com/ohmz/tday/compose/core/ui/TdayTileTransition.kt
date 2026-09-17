@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.ohmz.tday.compose.core.navigation.AppRoute
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
 * Three pieces, none of which a tile has to know about:
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
 *    TILES are drawn on. `Modifier.sharedElement` needs the scope of the visibility
 *    the element participates in, and for a source tile that is the `home`
 *    destination's own scope, not the one it is navigating to. It is provided at
 *    that one destination, so a tile cannot be wired to the wrong half.
 *
 * 3. [tdayTileSharedElement] on the tile and [TdayTileDestination] on the screen it
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
 * The threshold is `Rect`'s own and not a number written here: the token factory keeps
 * `visibilityThreshold` a parameter because how close is close enough to stopped is a
 * question about the units being animated, and these units are a rectangle.
 */
private val TdayTileBoundsTransform = BoundsTransform { _, _ ->
    TdayMotionTokens.Springs.settle(visibilityThreshold = Rect.VisibilityThreshold)
}

/**
 * Marks this tile as the rectangle the screen it opens grows out of.
 *
 * Applied to the tile's own [Modifier] chain, OUTSIDE its semantics and its press
 * feedback, so the shared bounds are the whole tile and the spring that squashes it
 * under a finger stays press feedback rather than becoming part of the transition.
 *
 * A no-op when [key] is null, when there is no namespace or no source scope above it,
 * or when motion is refused — see the file comment.
 */
@Composable
fun Modifier.tdayTileSharedElement(key: String?): Modifier =
    tdaySharedElement(key = key, animatedVisibilityScope = LocalTdayTileSourceScope.current)

/**
 * Marks this screen as the destination a tile grows into, and gives it the
 * `AnimatedVisibilityScope` it is transitioning in — which is the receiver, because a
 * `composable { }` block's lambda IS an `AnimatedVisibilityScope` (`AnimatedContentScope`
 * extends it). Nothing has to be threaded in from outside for the destination half.
 *
 * [route] is the route this destination was opened as, and the key is read from it, so
 * the two ends cannot be handed different answers.
 *
 * The content is wrapped in a full-size box because the rectangle that grows is the
 * whole screen: `sharedElement` on the screen's own root is what says "this screen is
 * that tile, at a different size". Everything inside it is that screen and travels with
 * it — which is the effect, and also its one risk: a screen whose content reads badly at
 * a small intermediate scale would read badly here. The clip is the tile's own corner
 * radius, so the growing rectangle is the tile's shape all the way out rather than a
 * hard-edged window.
 */
@Composable
fun AnimatedVisibilityScope.TdayTileDestination(
    route: AppRoute,
    fromHomeTile: Boolean,
    listId: String? = null,
    highlighted: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .tdaySharedElement(
                key = route.tileTransitionKey(
                    listId = listId,
                    highlighted = highlighted,
                    fromHomeTile = fromHomeTile,
                ),
                animatedVisibilityScope = this@TdayTileDestination,
            ),
    ) {
        content()
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
 * The one place a shared element is installed, for both ends.
 *
 * Everything that can be missing is checked here once: no key (a route with nothing on
 * screen to grow out of), no namespace (drawn outside [TdayTileTransitionLayout] — a
 * preview, the pre-graph splash), no visibility scope, or motion refused. Any one of them
 * means the modifier is not installed and the route change plays as it always did, which
 * is the fallback `docs/motion.md` asks for: the trip is removed, and the destination is
 * still handed over.
 */
@Composable
private fun Modifier.tdaySharedElement(
    key: String?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
): Modifier {
    val sharedTransitionScope = LocalTdaySharedTransitionScope.current
    val motionEnabled = rememberTdayMotionEnabled()
    val anchor = if (
        key != null &&
        animatedVisibilityScope != null &&
        sharedTransitionScope != null &&
        motionEnabled
    ) {
        with(sharedTransitionScope) {
            this@tdaySharedElement.sharedElement(
                state = rememberSharedContentState(key),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = TdayTileBoundsTransform,
                clipInOverlayDuringTransition = OverlayClip(
                    RoundedCornerShape(TdayDimens.RadiusCard),
                ),
            )
        }
    } else {
        Modifier
    }
    return this.then(anchor)
}
