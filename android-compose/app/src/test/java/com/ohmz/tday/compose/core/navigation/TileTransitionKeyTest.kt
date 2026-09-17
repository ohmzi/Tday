package com.ohmz.tday.compose.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The tile→screen key table, pinned as text.
 *
 * A shared-element transition has one failure mode that nothing else in the tree can see:
 * the source and the destination have to name the same key, and when they do not, the app
 * simply plays the ordinary route change. Nothing crashes, no screen is wrong, and no
 * screenshot differs — the zoom is just quietly not there. The iOS half of this feature
 * carries a test for exactly that reason, and this is its Android counterpart.
 *
 * Three halves are pinned here, and they catch three different ways of losing it.
 *
 * The KEYS are pinned because a key is a string that two call sites have to agree on, and
 * nothing about editing one of them says so. Every key below is written out as a literal
 * rather than re-derived from the table, so a change to the table fails here instead of
 * being read back out of it.
 *
 * The ROUTES are pinned because a route is the other half of the same agreement: the key
 * table is exhaustive over the sealed `AppRoute`, so renaming a route OBJECT is a compile
 * error here, but re-spelling a route STRING is not — and a tile that navigates with a
 * re-spelled string, or a destination registered under one, would drop the zoom just as
 * silently. Pinning the strings means a rename has to come through this file.
 */
class TileTransitionKeyTest {

    /**
     * One tile on screen: the route it opens, the arguments both ends read, and the key.
     *
     * A data class rather than a `Triple` because the completion history needs a FOURTH
     * term — its two tiles differ by scope and by nothing else — and because a named
     * `scope` at each row says which of the two Completed tiles that row is.
     */
    private data class TileSurface(
        val route: AppRoute,
        val listId: String? = null,
        val scope: CompletedScope? = null,
        val key: String,
    )

    /**
     * Every surface that has a tile on screen.
     *
     * The completion history is on this list TWICE, once per tile, and that is the point:
     * this list used to be one row per route, which is how two rectangles pushing one route
     * could publish one key with the test named "no two tiles publish one key" unable to see
     * it. The scheduled board's tile keeps the id the pair shared while it was one key.
     */
    private val tileSurfaces: List<TileSurface> = listOf(
        TileSurface(AppRoute.TodayTodos, key = "home-tile.today"),
        TileSurface(AppRoute.OverdueTodos, key = "home-tile.overdue"),
        TileSurface(AppRoute.ScheduledTodos, key = "home-tile.scheduled"),
        TileSurface(AppRoute.PriorityTodos, key = "home-tile.priority"),
        TileSurface(AppRoute.AllTodos, key = "home-tile.all"),
        TileSurface(AppRoute.Completed, scope = CompletedScope.Tasks, key = "home-tile.completed"),
        TileSurface(AppRoute.Completed, scope = CompletedScope.Floater, key = "floater-tile.completed"),
        TileSurface(AppRoute.Calendar, key = "home-tile.calendar"),
        TileSurface(AppRoute.ListTodos, listId = "list-1", key = "home-tile.list.list-1"),
        TileSurface(AppRoute.FloaterListTodos, listId = "list-1", key = "floater-tile.list.list-1"),
    )

    /** Every route with no rectangle on screen to grow out of. */
    private val tilelessRoutes: List<AppRoute> = listOf(
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
    )

    @Test
    fun `every tile surface names the key its route publishes`() {
        tileSurfaces.forEach { (route, listId, scope, key) ->
            assertEquals(
                "${route.route} no longer publishes ${key}",
                key,
                route.tileTransitionKey(listId = listId, scope = scope),
            )
        }
    }

    @Test
    fun `the whole surface is covered and nothing else is`() {
        // The two lists together are every case in the sealed class, so a route added to the
        // app has to be sorted into one of them here as well as in the table. This is the
        // Android counterpart of iOS's exhaustive `switch`: the table itself fails to compile
        // on a new case, and this fails if the new case was filed as tile-less by accident.
        //
        // Set equality and not a size, now that two entries name one route: a count would
        // have been satisfied by a route filed twice and another filed nowhere.
        val covered = tileSurfaces.map { it.route } + tilelessRoutes
        assertEquals(ALL_ROUTES.toSet(), covered.toSet())
    }

    @Test
    fun `no two tiles publish one key`() {
        val keys = tileSurfaces.map { it.key }
        assertEquals("two tiles publish the same key", keys.size, keys.toSet().size)
    }

    @Test
    fun `the two Completed tiles name different keys, because both feeds can be composed at once`() {
        // The defect the route's scope argument and this table's own say in it exist to
        // prevent. The scheduled board's Completed tile and the Anytime feed's are two
        // rectangles on one route, and the root feed crossfades the two boards over
        // `Durations.Enter` with BOTH of them mounted — so one id shared by the pair is two
        // views publishing one key in one namespace, which the arrival can match to the tile
        // the user did not press. iOS has split the same pair since it grew a
        // `HomeTileOrigin`; this is the Android half of that agreement.
        assertNotEquals(
            AppRoute.Completed.tileTransitionKey(scope = CompletedScope.Tasks),
            AppRoute.Completed.tileTransitionKey(scope = CompletedScope.Floater),
        )
        // And the unscoped arrival — the deep link, a shortcut — answers with the
        // scheduled board's id, which is the key the pair shared before the split.
        assertEquals(
            "home-tile.completed",
            AppRoute.Completed.tileTransitionKey(),
        )
        assertEquals(
            AppRoute.Completed.tileTransitionKey(scope = CompletedScope.Tasks),
            AppRoute.Completed.tileTransitionKey(),
        )
    }

    @Test
    fun `the two list routes carry the list they belong to`() {
        // The id is in the key so a list row grows only out of the row for THAT list — and
        // the scheduled and floater feeds are disjoint by construction even for one id, since
        // both feeds can be composed together for the length of the tab crossfade.
        assertEquals(
            "home-tile.list.list-9",
            AppRoute.ListTodos.tileTransitionKey(listId = "list-9"),
        )
        assertEquals(
            "floater-tile.list.list-9",
            AppRoute.FloaterListTodos.tileTransitionKey(listId = "list-9"),
        )
        assertNotEquals(
            AppRoute.ListTodos.tileTransitionKey(listId = "list-9"),
            AppRoute.FloaterListTodos.tileTransitionKey(listId = "list-9"),
        )
        // A list route with no id is a malformed deep link, not a tile.
        assertNull(AppRoute.ListTodos.tileTransitionKey())
        assertNull(AppRoute.FloaterListTodos.tileTransitionKey())
    }

    @Test
    fun `a highlighted All screen names no key, because it did not come from the tile`() {
        // The All tile navigates without a highlight; the home screen's search results and a
        // deep link arrive with one. Growing the search arrival out of the All tile would be
        // the animation claiming the user pressed something they did not press.
        assertEquals("home-tile.all", AppRoute.AllTodos.tileTransitionKey())
        assertNull(AppRoute.AllTodos.tileTransitionKey(highlighted = true))
    }

    @Test
    fun `no tile route names a key for an arrival that was not a tile press`() {
        // The other way to claim a press that did not happen, and the one that was open on every
        // route but All: a shortcut, a notification or a widget row arrives at a tile route with
        // the home screen still composed underneath it, so the tile's rectangle is on screen and
        // matches. Every surface has to answer null when the push was not a press — asserted over
        // the same list the keys above are, so a route filed there is covered here by being
        // there, and a new route cannot be added to one without the other.
        tileSurfaces.forEach { (route, listId, scope, key) ->
            assertNull(
                "${route.route} grows out of a tile on a push that was not a press",
                route.tileTransitionKey(listId = listId, scope = scope, fromHomeTile = false),
            )
            assertNull(
                "${route.route} grows out of a tile on a push that was not a press",
                route.tileTransitionKey(
                    listId = listId,
                    highlighted = true,
                    scope = scope,
                    fromHomeTile = false,
                ),
            )
            // ...and the guard is not swallowing the key it exists to gate.
            assertEquals(
                "${route.route} lost ${key} on a real tile press",
                key,
                route.tileTransitionKey(listId = listId, scope = scope, fromHomeTile = true),
            )
        }
    }

    @Test
    fun `the origin flag the push site and the destination agree on is pinned`() {
        // A saved-state key is a string two files apart agree on, like the keys above and for
        // the same reason: both ends default to "no origin", so a rename would not break either
        // end, it would just stop every tile from zooming.
        assertEquals("tday.tileTransitionOrigin", TILE_TRANSITION_ORIGIN)
    }

    @Test
    fun `the colour hand-off the push site and the destination agree on is pinned`() {
        // The second value on the same hand-off, pinned for the same reason and with one
        // more of its own: its absence is not an error at either end. A push site that
        // wrote it under a different name and a destination that read a different one would
        // both compile, and the only symptom would be a surface painted in the app's
        // background again — the "white box" the device row asks about.
        assertEquals("tday.tileTransitionColor", TILE_TRANSITION_COLOR)
    }

    @Test
    fun `a route with nothing on screen to grow out of names no key`() {
        tilelessRoutes.forEach { route ->
            assertNull(
                "${route.route} should not name a shared-element key",
                route.tileTransitionKey(),
            )
        }
    }

    @Test
    fun `the route strings the two ends agree on are pinned`() {
        // The table is exhaustive over the route OBJECTS, so this is the half a rename can
        // still get wrong: a tile navigates with `AppRoute.X.route` and the destination is
        // registered under it, so as long as both read the object they cannot drift — but a
        // re-spelled string is a change to both ends of a contract nothing else in this
        // repository holds together for this feature.
        assertEquals("todos/today", AppRoute.TodayTodos.route)
        assertEquals("todos/overdue", AppRoute.OverdueTodos.route)
        assertEquals("todos/scheduled", AppRoute.ScheduledTodos.route)
        assertEquals("todos/priority", AppRoute.PriorityTodos.route)
        assertEquals("todos/all?highlightTodoId={highlightTodoId}", AppRoute.AllTodos.route)
        assertEquals("completed?scope={scope}", AppRoute.Completed.route)
        assertEquals("calendar", AppRoute.Calendar.route)
        assertEquals("todos/list/{listId}/{listName}", AppRoute.ListTodos.route)
        assertEquals("floater/list/{listId}/{listName}", AppRoute.FloaterListTodos.route)
        // `AppRoute.create` is deliberately not exercised here: it builds a real navigation
        // string through `android.net.Uri`, whose JVM stub throws, and what a tile actually
        // navigates with is the route pattern above plus the two arguments the graph already
        // registers for. This file is about which rectangle a screen came out of, and the
        // empty-highlight `All` case — the one that IS the tile — has no Uri in it at all.
        assertEquals("todos/all", AppRoute.AllTodos.create())
        // The completed route's two forms, which are also the two spellings the two tiles
        // navigate with. Both are plain string concatenation — no `Uri` — so unlike the All
        // screen's they can be read back here.
        assertEquals("completed", AppRoute.Completed.create())
        assertEquals("completed", AppRoute.Completed.create(CompletedScope.Tasks))
        assertEquals("completed?scope=floater", AppRoute.Completed.create(CompletedScope.Floater))
        // ...and the argument the destination reads back is the one the tile wrote.
        assertEquals(
            CompletedScope.Floater,
            CompletedScope.fromWire("floater"),
        )
        assertEquals(CompletedScope.Tasks, CompletedScope.fromWire(null))
        assertEquals(CompletedScope.Tasks, CompletedScope.fromWire(""))
        assertEquals(CompletedScope.Tasks, CompletedScope.fromWire("tasks"))
        assertEquals(CompletedScope.Tasks, CompletedScope.fromWire("something-else"))
    }

    private companion object {
        /** Every case in [AppRoute], written out so a new one has to be filed above. */
        val ALL_ROUTES: List<AppRoute> = listOf(
            AppRoute.Splash,
            AppRoute.ServerSetup,
            AppRoute.Login,
            AppRoute.ForgotPassword,
            AppRoute.ScheduledTaskHome,
            AppRoute.FloaterTaskHome,
            AppRoute.TodayTodos,
            AppRoute.CreateTodayTodo,
            AppRoute.OverdueTodos,
            AppRoute.ScheduledTodos,
            AppRoute.AllTodos,
            AppRoute.PriorityTodos,
            AppRoute.ListTodos,
            AppRoute.FloaterListTodos,
            AppRoute.Completed,
            AppRoute.Calendar,
            AppRoute.Car,
            AppRoute.Settings,
            AppRoute.LatestRelease,
            AppRoute.MorningSweep,
            AppRoute.HelpGuide,
        )
    }
}
