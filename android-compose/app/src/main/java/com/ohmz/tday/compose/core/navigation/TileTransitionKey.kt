package com.ohmz.tday.compose.core.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

/**
 * The saved-state flag that says the push now in flight is a home tile press.
 *
 * The key table below answers "which rectangle does this route's tile publish", and both
 * ends read it, so the two halves of a zoom cannot disagree about the key. They can still
 * both be wrong in the same way, and they were. A tile route is reachable without a press —
 * a launcher shortcut, a notification, a widget row — and on a warm app every one of those
 * is an in-place push on the running activity, so the home screen is still the entry
 * underneath, its tiles are still composed, and the route's key matches a rectangle the
 * user never touched. The screen then grows out of a tile nobody pressed, which is the
 * animation claiming a press that did not happen.
 *
 * What is missing at the destination is not the key, it is the ORIGIN. Home is the entry
 * below a tile press and the entry below a warm deep link alike, so the back stack cannot
 * tell them apart; only the push site knows, and [navigateFromHomeTile] is the push site
 * saying so. It writes this flag on the entry it is leaving — the same `savedStateHandle`
 * hand-off the All screen's search highlight already uses — and
 * `rememberHomeTileOrigin` reads it back at the destination and takes it away again, so
 * one flag can describe one push and never a later one.
 *
 * The polarity is the safe one. A tile press SETS the flag and every other push leaves it
 * unset, so a push site nobody wired, or a route wired later by somebody who did not know
 * this contract existed, falls back to the ordinary route hand-over rather than to a zoom
 * out of the wrong rectangle. A flag meaning "this was not a tile" would fail open instead,
 * and every push site that forgot it would zoom.
 */
const val TILE_TRANSITION_ORIGIN: String = "tday.tileTransitionOrigin"

/**
 * The saved-state value that carries the pressed tile's OWN COLOUR to the screen it opens.
 *
 * The origin flag above says a tile was pressed. It cannot say WHICH, and
 * `AppRoute.tileTransitionKey` says which rectangle — but neither can say what colour that
 * rectangle is, because the colour is not on the route and not derivable from it. That is
 * the whole reason this second value exists: what grows out of a tile is a surface, and a
 * surface that is not the tile's colour is a sheet of something else (the app background,
 * which is near-white in light mode) sitting where the tile used to be. The device row has
 * always asked for the tile's colour in the first frames; see
 * `docs/verification/phase-9-device-pass.md`'s PR 32e entry, item (a).
 *
 * The colour cannot be a lookup at the destination, and the custom-list rows are why. Six
 * grid tiles, the Today card and the Completed entry are constants — but a list row's
 * container is `lerp(surfaceVariant, tdayListAccentColor(list.color), weight)`, a value
 * derived from server-side per-list data the DESTINATION does not have until it has fetched
 * the list, long after the surface's first frames. It cannot be a route argument either:
 * `AppRoute` is a plain object list with no place for one, and adding a colour to two route
 * patterns would put a presentation value in the navigation graph and change every deep
 * link string. So it travels the way the origin does — a `savedStateHandle` value written
 * by the push site, which is the only place that knows it, and read-and-removed by the
 * destination, which is the only place that needs it.
 *
 * Packed ARGB, not a `Color`: this is a `Bundle` value, and `savedStateHandle` is a
 * `Bundle`. `Color.toArgb()` at the push site and `Color(argb)` at the destination is the
 * whole of the conversion.
 */
const val TILE_TRANSITION_COLOR: String = "tday.tileTransitionColor"

/**
 * Pushes [route] as a home tile press, so the destination may claim the rectangle that
 * route's tile publishes — and carries [tileColorArgb], the pressed tile's own colour, so
 * the surface that grows out of that rectangle is the tile and not a sheet of the app
 * background.
 *
 * This is the only thing that sets [TILE_TRANSITION_ORIGIN] or [TILE_TRANSITION_COLOR], and
 * it is called from the ten tile click handlers — the six grid tiles, the Today card, the
 * scheduled board's list rows, the Anytime feed's Completed entry and its list rows — and
 * from nowhere else. Every other way into these routes (a shortcut, a notification, a widget
 * row, the create flow's own push onto today) calls `navigate` directly and therefore
 * arrives with no origin and no colour.
 *
 * The colour is a parameter with no default, for the reason `fromHomeTile` has none at the
 * other end: a push site that has not said what colour its tile is has not answered the
 * question this hand-off exists to carry, and a default would let it answer wrongly (with
 * the background) instead of failing to compile. The ten call sites are the ten tiles, and a
 * tile always knows what colour it is — for the list rows it is a value they have already
 * computed to paint their own `Card`.
 *
 * [builder] is [NavController.navigate]'s own options, so a tile that grows a
 * `launchSingleTop` or a `popUpTo` later does not have to choose between that and its
 * origin.
 */
fun NavController.navigateFromHomeTile(
    route: String,
    tileColorArgb: Int,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    currentBackStackEntry?.savedStateHandle?.apply {
        set(TILE_TRANSITION_ORIGIN, true)
        set(TILE_TRANSITION_COLOR, tileColorArgb)
    }
    navigate(route, builder)
}

/**
 * The shared-element key a home tile publishes, and the screen it opens answers with.
 *
 * A tile that grows into the screen it opens needs two things the two ends have to
 * agree on: one namespace to be matched in, and one key. The namespace is the
 * `SharedTransitionLayout` the NavHost is drawn in, and it is the same for every
 * pair; the key is this table, and it is the only thing that says WHICH tile a
 * screen came out of.
 *
 * Both ends read it. The tile that pushes asks for the key of the route it is about
 * to navigate to, and the destination asks for the key of the route it was opened
 * as. So a tile re-pointed at a different route cannot end up growing out of a
 * rectangle it never came from: the two ends would name different keys and the
 * transition would simply not happen. Nothing goes down a parameter chain, and no
 * caller passes a key it invented — the route is the input, and the one thing beside
 * it is whether the push was a press at all ([fromHomeTile], argued below).
 *
 * The `when` is written out with no `else` and no `default`. That is the property
 * worth having rather than the tidiness: [AppRoute] is a sealed class, so a route
 * that is renamed or added has to be answered HERE, at compile time, instead of
 * quietly dropping back to the ordinary route change in a release build — a
 * transition that silently stops happening is invisible to every test that reads a
 * screen rather than a rectangle. [TileTransitionKeyTest] pins the other half: the
 * `.route` string each case matches is spelled out there, so re-spelling a route
 * without revisiting this table is a test failure and not a missing zoom.
 *
 * [fromHomeTile] is the question the route cannot answer. The route says which tile
 * publishes this key; it does not say that anybody pressed one. `todos/today` is a home
 * tile AND a launcher shortcut AND a notification, and every one of those arrives with the
 * home screen still composed underneath on a warm app, so the rectangle is on screen and
 * the key would match it. The answer comes from `TILE_TRANSITION_ORIGIN`, which only
 * [navigateFromHomeTile] sets, and it gates EVERY case in the `when` below rather than the
 * one route that happened to be caught first. A tile passes the default, because a tile
 * calling this IS the origin; a destination passes what the flag says, and
 * `TdayTileDestination` gives it no default to forget.
 *
 * The rest of the arguments say the same thing in different shapes.
 *
 * [AppRoute.AllTodos] carries a highlight for another purpose — the home screen's own
 * search results and the deep links that name a todo — and a highlight id means the
 * arrival was not the All tile's press even before the origin flag existed. The flag
 * subsumes it; the highlight is kept because the screen itself is handed the same id, and
 * because a guard that is true for a different stated reason is a cheaper thing to read
 * than to work out.
 *
 * The two list routes carry their `listId` in the route string, so they take it as an
 * argument here and answer `null` without one — a list route with no id is a malformed
 * deep link, not a tile.
 *
 * [AppRoute.Completed] WAS the one key two tiles shared: the scheduled board's Completed
 * tile and the Anytime feed's Completed entry are two rectangles pushing one route. A
 * destination that cannot tell which feed pushed it cannot answer with the right one of
 * two keys, and iOS — which has always split this pair with a `HomeTileOrigin` carried on
 * the route — spells the two `home-tile.completed` and `floater-tile.completed`.
 *
 * The second source has now arrived, and this is the entry that grew, exactly as the
 * paragraph above used to promise it would. The reason it cannot wait any longer is the
 * route argument this change also adds: the two tiles now push DIFFERENT routes
 * ([AppRoute.Completed.create] with a [CompletedScope]), so if the key had stayed a
 * function of the route object alone, the Anytime feed's tile would publish the scheduled
 * key while the destination answered with the floater one — the two ends would name
 * different keys and the zoom would simply stop, silently, on a surface that has one
 * today. [scope] is therefore the argument the tiles and the destination now both pass,
 * and it is the same value the route carries, read from the same place.
 */
fun AppRoute.tileTransitionKey(
    listId: String? = null,
    highlighted: Boolean = false,
    scope: CompletedScope? = null,
    fromHomeTile: Boolean = true,
): String? = if (!fromHomeTile) null else when (this) {
    AppRoute.TodayTodos -> "home-tile.today"
    AppRoute.OverdueTodos -> "home-tile.overdue"
    AppRoute.ScheduledTodos -> "home-tile.scheduled"
    AppRoute.PriorityTodos -> "home-tile.priority"
    AppRoute.AllTodos -> if (highlighted) null else "home-tile.all"
    // Two tiles, two keys, and the scheduled board keeps the id it has always published —
    // the default arm is not a fallback here, it is the scheduled tile's answer, which is
    // why an unscoped arrival (`?scope` absent, a deep link, a shortcut) still matches it.
    AppRoute.Completed -> when (scope) {
        CompletedScope.Floater -> "floater-tile.completed"
        else -> "home-tile.completed"
    }
    AppRoute.Calendar -> "home-tile.calendar"
    AppRoute.ListTodos -> listId?.let { "home-tile.list.$it" }
    AppRoute.FloaterListTodos -> listId?.let { "floater-tile.list.$it" }

    AppRoute.Splash,
    AppRoute.ServerSetup,
    AppRoute.Login,
    AppRoute.ForgotPassword,
    AppRoute.ScheduledTaskHome,
    AppRoute.FloaterTaskHome,
    AppRoute.CreateTodayTodo,
    AppRoute.Car,
    AppRoute.Settings,
    AppRoute.LatestRelease,
    AppRoute.MorningSweep,
    AppRoute.HelpGuide,
    -> null
}
