package com.ohmz.tday.compose

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.ohmz.tday.compose.core.data.ConnectionFailureKind
import com.ohmz.tday.compose.core.model.DashboardSummary
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.core.navigation.AppRoute
import com.ohmz.tday.compose.core.navigation.CompletedScope
import com.ohmz.tday.compose.core.navigation.navigateFromHomeTile
import com.ohmz.tday.compose.core.navigation.tileTransitionKey
import com.ohmz.tday.compose.core.ui.LocalSnackbarManager
import com.ohmz.tday.compose.core.ui.LocalTdayTileSourceScope
import com.ohmz.tday.compose.core.ui.SnackbarEvent
import com.ohmz.tday.compose.core.ui.SnackbarKind
import com.ohmz.tday.compose.core.ui.TaskSwipeSlot
import com.ohmz.tday.compose.core.ui.TdayMotionTokens
import com.ohmz.tday.compose.core.ui.TdayTileDestination
import com.ohmz.tday.compose.core.ui.TdayTileTransitionLayout
import com.ohmz.tday.compose.core.ui.TdayToastData
import com.ohmz.tday.compose.core.ui.TdayToastHost
import com.ohmz.tday.compose.core.ui.TdayToastKind
import com.ohmz.tday.compose.core.ui.actionToastTimeoutMillis
import com.ohmz.tday.compose.core.ui.informationalToastTimeoutMillis
import com.ohmz.tday.compose.core.ui.rememberHomeTileColor
import com.ohmz.tday.compose.core.ui.rememberHomeTileOrigin
import com.ohmz.tday.compose.core.ui.rememberTdayMotionEnabled
import com.ohmz.tday.compose.core.ui.tdayClosesSwipeRowOnOutsideTap
import com.ohmz.tday.compose.feature.app.AppUiState
import com.ohmz.tday.compose.feature.app.AppViewModel
import com.ohmz.tday.compose.feature.app.ProfileEditResult
import com.ohmz.tday.compose.feature.app.RootDestination
import com.ohmz.tday.compose.feature.app.SessionResolution
import com.ohmz.tday.compose.feature.auth.AuthUiState
import com.ohmz.tday.compose.feature.auth.AuthViewModel
import com.ohmz.tday.compose.feature.auth.ForgotPasswordScreen
import com.ohmz.tday.compose.feature.auth.SetSecurityQuestionsGate
import com.ohmz.tday.compose.feature.calendar.CalendarScreen
import com.ohmz.tday.compose.feature.calendar.CalendarViewModel
import com.ohmz.tday.compose.feature.car.CarTaskMode
import com.ohmz.tday.compose.feature.car.CarTaskSurfaceScreen
import com.ohmz.tday.compose.feature.car.CarTaskSurfaceViewModel
import com.ohmz.tday.compose.feature.car.rememberCarTaskVoiceCreateLauncher
import com.ohmz.tday.compose.feature.completed.CompletedScreen
import com.ohmz.tday.compose.feature.completed.CompletedViewModel
import com.ohmz.tday.compose.feature.scheduledtaskhome.ScheduledTaskHomeScreen
import com.ohmz.tday.compose.feature.scheduledtaskhome.ScheduledTaskHomeUiState
import com.ohmz.tday.compose.feature.scheduledtaskhome.ScheduledTaskHomeViewModel
import com.ohmz.tday.compose.feature.onboarding.OnboardingWizardOverlay
import com.ohmz.tday.compose.feature.guide.HelpGuideScreen
import com.ohmz.tday.compose.feature.guide.LocalOpenGuideTopic
import com.ohmz.tday.compose.feature.release.LatestReleaseScreen
import com.ohmz.tday.compose.feature.release.LatestReleaseUiState
import com.ohmz.tday.compose.feature.release.LatestReleaseViewModel
import com.ohmz.tday.compose.feature.settings.SettingsScreen
import com.ohmz.tday.compose.feature.sweep.MorningSweepScreen
import com.ohmz.tday.compose.feature.todos.TodoListScreen
import com.ohmz.tday.compose.feature.todos.TodoListViewModel
import com.ohmz.tday.compose.ui.component.RootCreateTaskButton
import com.ohmz.tday.compose.ui.component.RootFeedDock
import com.ohmz.tday.compose.ui.component.RootFeedTab
import com.ohmz.tday.compose.ui.theme.TdayDimens
import com.ohmz.tday.compose.ui.theme.TdayFloaterAccent
import com.ohmz.tday.compose.ui.theme.TdayTheme
import com.ohmz.tday.compose.ui.theme.TdayTodayBlue
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import io.sentry.android.navigation.SentryNavigationListener

private const val PENDING_SEARCH_HIGHLIGHT_TODO_ID = "pendingSearchHighlightTodoId"

// How far the screen a predictive-back drag has hold of recedes by the end of that drag.
//
// not a token — see docs/motion.md. The press scales are the nearest thing in the vocabulary
// and they are a different quantity: they grade by surface class, and they grade the wrong
// way for this — the table's own rule is that SMALLER surfaces move further, so extending it
// past `Row`'s 0.985 to a whole screen gives a recede of nothing at all. That rule is about a
// surface squashing under the finger that is on it. This is a screen being carried off the
// side, and the number's job is to report how far the drag has got, not how hard it is being
// pressed. Same distinction the table already draws for `pressedScale * revealScale`.
//
// A tenth, because that is the recede the platform itself plays when a back gesture takes the
// whole app away, and an in-app back that pulls a smaller distance than the system's would
// make the two gestures read as two different things on the same edge of the same screen.
private const val PREDICTIVE_BACK_MIN_SCALE = 0.90f

// How far out of focus the app is pushed behind the onboarding wizard. A radius, not a
// motion spec: what the vocabulary fixes is how long it takes to get here, not how far.
private val ONBOARDING_BACKDROP_BLUR = 14.dp

// Nav argument names, shared by the route templates that declare them and the back stack
// entries that read them back.
private const val ARG_LIST_ID = "listId"
private const val ARG_LIST_NAME = "listName"
private const val ARG_CREATE_TARGET = "target"
private const val ARG_HIGHLIGHT_TODO_ID = "highlightTodoId"
private const val ARG_GUIDE_TOPIC = "topic"
/** Which of the completion history's two tabs an arrival opens on — see `CompletedScope`. */
private const val ARG_COMPLETED_SCOPE = "scope"

// The `target` vocabulary of `tday://todos/create?target=...` — the widget, the reminder
// notification and the car surface all speak it.
private const val CREATE_TARGET_TODAY = "today"
private const val CREATE_TARGET_FLOATER = "floater"

@Composable
fun TdayApp(
    onFirstFrameDrawn: () -> Unit = {},
) {
    val splashTaglineOptions = stringArrayResource(R.array.splash_taglines)
    val startupTagline = rememberSaveable(splashTaglineOptions.contentHashCode()) {
        splashTaglineOptions.random()
    }
    // A fresh instance every composition, which makes it the one unstable key of the memoized
    // NavHost builder lambda below and so rebuilds the nav graph on every recomposition. That is
    // incidental, not load-bearing: nothing the graph hands a destination is a snapshot value, so
    // no screen depends on the rebuild to see a change (see the note at the builder). Memoizing it
    // is therefore safe, but it is a perf change rather than a fix and does not belong here.
    val unauthenticatedScheduledTaskHomeUiState = unauthenticatedScheduledTaskHomeUiState(
        lockedListName = stringResource(R.string.scheduled_task_home_locked_list_name),
    )
    var hasDrawnStartupFrame by remember { mutableStateOf(false) }
    val currentOnFirstFrameDrawn by rememberUpdatedState(onFirstFrameDrawn)

    if (!hasDrawnStartupFrame) {
        TdayTheme {
            SplashScreen(
                tagline = startupTagline,
                onHoldChanged = {},
            )
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            hasDrawnStartupFrame = true
            currentOnFirstFrameDrawn()
        }
        return
    }

    val navController = rememberNavController()

    DisposableEffect(navController) {
        val listener = SentryNavigationListener()
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    val appViewModel: AppViewModel = hiltViewModel()
    val releaseViewModel: LatestReleaseViewModel = hiltViewModel()
    val appUiState by appViewModel.uiState.collectAsStateWithLifecycle()
    val releaseUiState by releaseViewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val updateToastMessage = releaseUiState.latestRelease?.tagName?.let { versionLabel ->
        stringResource(R.string.release_launch_update_toast, versionLabel)
    }
    val passwordChangedToastMessage = stringResource(R.string.password_changed_toast)
    val profileNameUpdatedToastMessage = stringResource(R.string.profile_name_updated_toast)
    val securityQuestionsUpdatedToastMessage =
        stringResource(R.string.settings_account_security_questions_updated)
    var activeToast by remember { mutableStateOf<TdayToastData?>(null) }
    var hasShownLaunchUpdateToast by rememberSaveable { mutableStateOf(false) }
    var isStartupSplashHeld by remember { mutableStateOf(false) }
    var isSessionSplashHeld by remember { mutableStateOf(false) }
    // Seeded once, from the user's "Default home screen" setting, on a genuinely fresh
    // composition (no saved instance state to restore) — a config change, process-death
    // restore, or in-session dock tap all keep whatever rootFeedTab already holds instead of
    // re-applying the default underneath the user.
    var rootFeedTab by rememberSaveable { mutableStateOf(appViewModel.defaultHomeScreenSnapshot()) }
    // Whether anything other than that seed has put a tab in `rootFeedTab` — a dock tap, a
    // swipe, a deep link, or a route that implies a feed. Saved, so a config change or a
    // process-death restore cannot re-arm the reconcile below over a choice already made.
    var rootFeedTabWasChosen by rememberSaveable { mutableStateOf(false) }
    var rootCreateTaskRequestSerial by rememberSaveable { mutableStateOf(0) }
    var rootCreateTaskRequestKey by rememberSaveable { mutableStateOf(0) }
    var pendingFloaterTaskHomeCreateTask by rememberSaveable { mutableStateOf(false) }
    var scheduledTaskHomeScrollToTopRequestKey by remember { mutableStateOf(0) }
    var floaterTaskHomeScrollToTopRequestKey by remember { mutableStateOf(0) }
    var rootDockCollapsed by rememberSaveable { mutableStateOf(false) }
    var rootControlsVisible by rememberSaveable { mutableStateOf(true) }

    fun requestRootCreateTask() {
        rootCreateTaskRequestSerial += 1
        rootCreateTaskRequestKey = rootCreateTaskRequestSerial
    }

    fun consumeRootCreateTaskRequest(requestKey: Int) {
        if (rootCreateTaskRequestKey == requestKey) {
            rootCreateTaskRequestKey = 0
        }
    }

    HandlePendingDeepLink(
        isWorkspaceAvailable = appUiState.isWorkspaceAvailable,
        currentRoute = currentRoute,
        navController = navController,
    )

    HandlePendingFloaterCreateTask(
        isCreateTaskPending = pendingFloaterTaskHomeCreateTask,
        currentRoute = currentRoute,
        rootFeedTab = rootFeedTab,
        isWorkspaceAvailable = appUiState.isWorkspaceAvailable,
        onCreateTask = {
            pendingFloaterTaskHomeCreateTask = false
            requestRootCreateTask()
        },
    )

    CollectAppSnackbars(
        appViewModel = appViewModel,
        onShowToast = { activeToast = it },
    )
    CollectConnectivityToasts(
        appViewModel = appViewModel,
        isOffline = appUiState.showsOfflineNotice(),
        offlineReason = appUiState.offlineReason,
        pendingMutationCount = appUiState.pendingMutationCount,
        manualNoticePulse = appUiState.manualNoticePulse,
    )
    OnAppForegroundResume {
        appViewModel.reconnectAfterForeground()
    }

    /**
     * Puts a tab in `rootFeedTab` on the user's behalf — or on behalf of a navigation that
     * implies one (a deep link into a floater list, a create-floater flow). It is also what
     * pins the feed for the rest of the session: see [rootFeedTabWasChosen].
     */
    fun selectRootFeedTab(tab: RootFeedTab) {
        rootFeedTabWasChosen = true
        rootFeedTab = tab
    }

    fun handleRootFeedTabSelection(tab: RootFeedTab) {
        if (rootFeedTab == tab) {
            // Re-tapping the tab that is already showing scrolls it to the top, and is still
            // the user saying where they want to be — so it pins the feed too.
            rootFeedTabWasChosen = true
            when (tab) {
                RootFeedTab.SCHEDULED_TASK_HOME -> scheduledTaskHomeScrollToTopRequestKey += 1
                RootFeedTab.FLOATER_TASK_HOME -> floaterTaskHomeScrollToTopRequestKey += 1
            }
        } else {
            selectRootFeedTab(tab)
        }
    }

    // The composition seed above reads the DEVICE cache, and that cache only holds the
    // account's value once the sync carrying it has run — so on the first launch after the
    // setting was changed on another device, the seed is still the screen that device left
    // behind, and the account's real answer arrives a launch late.
    //
    // It does not have to: `bootstrap()` primes that sync BEFORE it publishes the resolved
    // state (`restoreSessionAndPrimeData` runs `syncCachedData`, which mirrors
    // `defaultHomeScreen` into the cache), so by the time `sessionResolution` reads RESOLVED,
    // `appUiState.defaultHomeScreen` already carries what the account says. Apply it then —
    // which is this same launch, not the next one — unless the user has already steered the
    // root feed themselves, in which case the choice stands for the session.
    LaunchedEffect(appUiState.sessionResolution, appUiState.defaultHomeScreen) {
        if (appUiState.sessionResolution != SessionResolution.RESOLVED) return@LaunchedEffect
        if (rootFeedTabWasChosen) return@LaunchedEffect
        rootFeedTab = appUiState.defaultHomeScreen
    }

    HandleStartupNavigation(
        appUiState = appUiState,
        currentRoute = currentRoute,
        navController = navController,
        isStartupSplashHeld = isStartupSplashHeld,
    )

    HandleLaunchUpdateToast(
        appUiState = appUiState,
        releaseUiState = releaseUiState,
        currentRoute = currentRoute,
        updateToastMessage = updateToastMessage,
        activeToast = activeToast,
        hasShownLaunchUpdateToast = hasShownLaunchUpdateToast,
        onToastShown = { hasShownLaunchUpdateToast = true },
        onShowToast = { toast -> activeToast = toast },
        onClearToast = { activeToast = null },
        onOpenLatestRelease = {
            navController.navigate(AppRoute.LatestRelease.route) {
                launchSingleTop = true
            }
        },
    )

    TdayTheme(themeMode = appUiState.themeMode) {
        // Blur source for the bottom toast: the whole nav content is captured so the
        // toast's hazeChild can render a translucent frosted backdrop (matches iOS).
        val hazeState = remember { HazeState() }
        Box(modifier = Modifier.fillMaxSize()) {
            // One provider for every contextual "?" help link (GuideHelpLink);
            // screens without a nav host (widget quick-add) simply have none.
            CompositionLocalProvider(
                LocalOpenGuideTopic provides { topicId ->
                    navController.navigate(AppRoute.HelpGuide.create(topicId)) {
                        launchSingleTop = true
                    }
                },
                // Every screen can raise the unified frosted toast (TdayToastHost) via
                // LocalSnackbarManager, instead of the plain system Toast.makeText.
                LocalSnackbarManager provides appViewModel.snackbarManager,
            ) {
                // No nav graph until the persisted session has resolved. Setting the graph is
                // where Navigation handles the launch intent's deep link (tday://home from the
                // update-ready notification, tday://floater from the widget) and restores a
                // saved back stack after process death — both land on `home` directly, and
                // `home` draws the sign-in wizard whenever the workspace is unavailable, which
                // it always is before bootstrap has answered. Holding the branded splash here
                // means the first real screen composed is already the right one. The splash
                // keeps its tap-and-hold pause; that hold is tracked apart from the in-graph
                // Splash route's (isStartupSplashHeld) so a press there can never tear the
                // graph back down.
                if (shouldHoldSessionSplash(appUiState.rootDestination, isSessionSplashHeld)) {
                    SplashScreen(
                        tagline = startupTagline,
                        onHoldChanged = { isSessionSplashHeld = it },
                    )
                    return@CompositionLocalProvider
                }
                // Hoisted, not read below: the four lambdas underneath are
                // `AnimatedContentTransitionScope` receivers rather than composables, so this
                // is the last scope that can ask the question. Compose's animator scale would
                // zero these transitions on its own; the in-app Reduce Motion switch is the
                // half Compose knows nothing about, and until now the NavHost was the one
                // surface in the app still deaf to it.
                val motionEnabled = rememberTdayMotionEnabled()
                // One namespace for every tile and the screen it opens. It wraps the
                // NavHost rather than any one screen: the two halves of a shared element
                // live in two different destinations, and only the layout above both of
                // them can match them. The scope is read back out of a local by the tiles,
                // and `home` below publishes the visibility scope they are drawn in. Every
                // piece of this is a no-op where motion is refused.
                TdayTileTransitionLayout(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.Splash.route,
                        modifier = Modifier.haze(hazeState),
                        // Crossfade, with no slide in it.
                        //
                        // Every screen draws its own toolbar at the same place in the same
                        // row, so a slide carried the back chevron and the action cluster
                        // 18% of the screen's width sideways and then dropped them back
                        // where they started — the one part of the frame that is the SAME
                        // on both screens, moving. Fading in place hands each button over
                        // to its counterpart instead of travelling it there and back.
                        //
                        // Directional transitions go with it wherever the change is COMMITTED:
                        // there is no direction left to express once nothing moves, so three of
                        // the four slots below share one pair. The fourth is the one the system
                        // SEEKS against a finger rather than plays, and a seeked crossfade is a
                        // progress report nobody can read — see `navigationPopExitTransition`.
                        // The screen being dragged off is the only thing that moves, and the
                        // arriving screen still fades in place, so the hand-over argument above
                        // survives in the half it was written about.
                        enterTransition = { navigationEnterTransition(motionEnabled) },
                        exitTransition = { navigationExitTransition(motionEnabled) },
                        popEnterTransition = { navigationEnterTransition(motionEnabled) },
                        popExitTransition = { navigationPopExitTransition(motionEnabled) },
                    ) {
                        splashAndAuthRoutes(
                            startupTagline = startupTagline,
                            onStartupSplashHoldChanged = { isStartupSplashHeld = it },
                            navController = navController,
                            appViewModel = appViewModel,
                        )
                        // Every changing value crosses into a route as a `() -> T` reader, never as
                        // the value itself. This lambda is the NavGraph *builder*: it runs once per
                        // graph build, inside NavHost's `remember(route, startDestination, builder)`,
                        // and NOT on recomposition. A `by`-delegated read performed here is recorded
                        // against NavHost's scope and then frozen into the destination for the life of
                        // the graph, so writing the state would never reach the screen. Reading through
                        // the lambda instead defers the snapshot read to the `composable { }` body,
                        // where it belongs to the destination's own recompose scope.
                        rootFeedRoutes(
                            appUiState = { appUiState },
                            appViewModel = appViewModel,
                            navController = navController,
                            unauthenticatedUiState = unauthenticatedScheduledTaskHomeUiState,
                            rootFeedTab = { rootFeedTab },
                            onSelectRootFeedTab = ::handleRootFeedTabSelection,
                            onChangeRootFeedTab = ::selectRootFeedTab,
                            rootCreateTaskRequestKey = { rootCreateTaskRequestKey },
                            onCreateTaskRequestHandled = ::consumeRootCreateTaskRequest,
                            onRequestCreateTask = ::requestRootCreateTask,
                            scheduledScrollToTopRequestKey = { scheduledTaskHomeScrollToTopRequestKey },
                            floaterScrollToTopRequestKey = { floaterTaskHomeScrollToTopRequestKey },
                            rootDockCollapsed = { rootDockCollapsed },
                            onRootDockCollapsedChange = { rootDockCollapsed = it },
                            rootControlsVisible = { rootControlsVisible },
                            onRootControlsVisibleChange = { rootControlsVisible = it },
                        )
                        todoScopeRoutes(
                            navController = navController,
                            isLocalMode = { appUiState.isLocalMode },
                            onChangeRootFeedTab = ::selectRootFeedTab,
                            onRequestFloaterCreateTask = { pendingFloaterTaskHomeCreateTask = true },
                        )
                        listRoutes(
                            navController = navController,
                            isLocalMode = { appUiState.isLocalMode },
                            onChangeRootFeedTab = ::selectRootFeedTab,
                        )
                        utilityRoutes(
                            navController = navController,
                            isLocalMode = { appUiState.isLocalMode },
                        )
                        settingsRoutes(
                            navController = navController,
                            appUiState = { appUiState },
                            appViewModel = appViewModel,
                            releaseUiState = { releaseUiState },
                            releaseViewModel = releaseViewModel,
                            passwordChangedToastMessage = passwordChangedToastMessage,
                            profileNameUpdatedToastMessage = profileNameUpdatedToastMessage,
                            securityQuestionsUpdatedToastMessage = securityQuestionsUpdatedToastMessage,
                        )
                    }
                }
            }

            TdayToastHost(
                toast = activeToast,
                onDismiss = { activeToast = null },
                hazeState = hazeState,
            )
        }
    }
}

/**
 * The pre-workspace routes: the in-graph splash, the two legacy entry points that still resolve to
 * it, and the logged-out password reset.
 */
private fun NavGraphBuilder.splashAndAuthRoutes(
    startupTagline: String,
    onStartupSplashHoldChanged: (Boolean) -> Unit,
    navController: NavHostController,
    appViewModel: AppViewModel,
) {
    // None of these three names its own transition any more. A splash handing over to the
    // first real screen, and an auth screen handing over to the workspace, are route changes
    // like any other and have nothing to say about their own length; the 300 ms each of them
    // used to write was a third number in a hand-over that should only ever have had one.
    // They inherit the NavHost defaults, which is also how they inherit the Reduce Motion gate.
    composable(route = AppRoute.Splash.route) {
        // Same tagline as the pre-graph splash it takes over from, so the
        // hand-off between the two is not visible.
        SplashScreen(
            tagline = startupTagline,
            onHoldChanged = onStartupSplashHoldChanged,
        )
    }

    composable(route = AppRoute.ServerSetup.route) {
        SplashScreen(onHoldChanged = onStartupSplashHoldChanged)
    }

    composable(route = AppRoute.Login.route) {
        SplashScreen(onHoldChanged = onStartupSplashHoldChanged)
    }

    composable(route = AppRoute.ForgotPassword.route) {
        val passwordResetMessage =
            stringResource(R.string.forgot_password_reset_success)
        ForgotPasswordScreen(
            onBackToLogin = { navController.popBackStack() },
            onResetComplete = {
                navController.popBackStack()
                appViewModel.snackbarManager.showSuccess(passwordResetMessage)
            },
        )
    }
}

/**
 * The two root feeds. `home` is the real one — it draws whichever feed the dock has selected, plus
 * the onboarding wizard when there is no workspace — and `floater` only exists so the widget's
 * `tday://floater` has a destination to land on before it hands over to `home`.
 *
 * The changing values arrive as `() -> T` readers and are dereferenced inside `composable { }`, so
 * the snapshot read lands in the destination's recompose scope rather than in the graph builder's.
 */
private fun NavGraphBuilder.rootFeedRoutes(
    appUiState: () -> AppUiState,
    appViewModel: AppViewModel,
    navController: NavHostController,
    unauthenticatedUiState: ScheduledTaskHomeUiState,
    rootFeedTab: () -> RootFeedTab,
    onSelectRootFeedTab: (RootFeedTab) -> Unit,
    onChangeRootFeedTab: (RootFeedTab) -> Unit,
    rootCreateTaskRequestKey: () -> Int,
    onCreateTaskRequestHandled: (Int) -> Unit,
    onRequestCreateTask: () -> Unit,
    scheduledScrollToTopRequestKey: () -> Int,
    floaterScrollToTopRequestKey: () -> Int,
    rootDockCollapsed: () -> Boolean,
    onRootDockCollapsedChange: (Boolean) -> Unit,
    rootControlsVisible: () -> Boolean,
    onRootControlsVisibleChange: (Boolean) -> Unit,
) {
    composable(
        route = AppRoute.ScheduledTaskHome.route,
        deepLinks = listOf(navDeepLink { uriPattern = "tday://home" }),
    ) {
        // This screen draws all ten tiles, so it is the source half of every zoom, and a
        // shared element has to name the visibility it is drawn in. That scope is `this` —
        // the destination's own `AnimatedContentScope`, which is an `AnimatedVisibilityScope`
        // — and it is published once here rather than threaded down through the feed and the
        // private composables that build the tiles.
        CompositionLocalProvider(LocalTdayTileSourceScope provides this) {
            ScheduledTaskHomeRoute(
                appUiState = appUiState(),
                appViewModel = appViewModel,
                navController = navController,
                unauthenticatedUiState = unauthenticatedUiState,
                rootFeedTab = rootFeedTab(),
                onSelectRootFeedTab = onSelectRootFeedTab,
                onChangeRootFeedTab = onChangeRootFeedTab,
                rootCreateTaskRequestKey = rootCreateTaskRequestKey(),
                onCreateTaskRequestHandled = onCreateTaskRequestHandled,
                onRequestCreateTask = onRequestCreateTask,
                scheduledScrollToTopRequestKey = scheduledScrollToTopRequestKey(),
                floaterScrollToTopRequestKey = floaterScrollToTopRequestKey(),
                rootDockCollapsed = rootDockCollapsed(),
                onRootDockCollapsedChange = onRootDockCollapsedChange,
                rootControlsVisible = rootControlsVisible(),
                onRootControlsVisibleChange = onRootControlsVisibleChange,
            )
        }
    }

    composable(
        route = AppRoute.FloaterTaskHome.route,
        deepLinks = listOf(navDeepLink { uriPattern = "tday://floater" }),
    ) { entry ->
        LaunchedEffect(entry.destination.id) {
            onChangeRootFeedTab(RootFeedTab.FLOATER_TASK_HOME)
            navController.navigate(AppRoute.ScheduledTaskHome.route) {
                popUpTo(entry.destination.id) { inclusive = true }
                launchSingleTop = true
            }
        }
        Box(modifier = Modifier.fillMaxSize())
    }
}

/** The five scheduled-task scopes reachable from the home cards, plus the create-task deep link. */
private fun NavGraphBuilder.todoScopeRoutes(
    navController: NavHostController,
    isLocalMode: () -> Boolean,
    onChangeRootFeedTab: (RootFeedTab) -> Unit,
    onRequestFloaterCreateTask: () -> Unit,
) {
    composable(
        route = AppRoute.TodayTodos.route,
        deepLinks = listOf(navDeepLink { uriPattern = "tday://todos/today" }),
    ) { entry ->
        // The arriving half of a tile's zoom. The screen is wrapped in a full-size anchor
        // whose shared-element key comes from this route, so the tile that pushed it and
        // this destination cannot be handed different answers; the block's own receiver is
        // the `AnimatedVisibilityScope` this screen is transitioning in, which is what
        // `Modifier.sharedElement` needs from the destination end. The route says which
        // tile, and `rememberHomeTileOrigin` says whether a tile was pressed at all — the
        // two questions this screen's arrival has to answer before it may grow out of a
        // rectangle. Where either answer is no, or where motion is refused, this is an
        // ordinary box around the screen and the route change plays as it always did.
        // `rememberHomeTileColor` is the third answer, and the one no route can give: what
        // colour the rectangle was painted in. See `TILE_TRANSITION_COLOR`.
        TdayTileDestination(
            route = AppRoute.TodayTodos,
            fromHomeTile = rememberHomeTileOrigin(navController, entry),
            // What the tile that pushed this screen was painted in, read off the same
            // hand-off and consumed the same way. A route cannot carry it — see
            // `TILE_TRANSITION_COLOR` — and the surface that grows out of the rectangle is
            // the tile's colour or it is a sheet of the app's background.
            tileColor = rememberHomeTileColor(navController, entry),
        ) {
            TodosRoute(
                mode = TodoListMode.TODAY,
                onBack = { navController.popBackStack() },
                pullRefreshEnabled = !isLocalMode(),
                summaryAvailable = !isLocalMode(),
            )
        }
    }

    createTodayTodoRoute(
        navController = navController,
        isLocalMode = isLocalMode,
        onChangeRootFeedTab = onChangeRootFeedTab,
        onRequestFloaterCreateTask = onRequestFloaterCreateTask,
    )

    composable(
        route = AppRoute.OverdueTodos.route,
        deepLinks = listOf(navDeepLink { uriPattern = "tday://todos/overdue" }),
    ) { entry ->
        TdayTileDestination(
            route = AppRoute.OverdueTodos,
            fromHomeTile = rememberHomeTileOrigin(navController, entry),
            // What the tile that pushed this screen was painted in, read off the same
            // hand-off and consumed the same way. A route cannot carry it — see
            // `TILE_TRANSITION_COLOR` — and the surface that grows out of the rectangle is
            // the tile's colour or it is a sheet of the app's background.
            tileColor = rememberHomeTileColor(navController, entry),
        ) {
            TodosRoute(
                mode = TodoListMode.OVERDUE,
                onBack = { navController.popBackStack() },
                onOpenMorningSweep = {
                    navController.navigate(AppRoute.MorningSweep.route) {
                        launchSingleTop = true
                    }
                },
                pullRefreshEnabled = !isLocalMode(),
                summaryAvailable = !isLocalMode(),
            )
        }
    }

    composable(
        route = AppRoute.ScheduledTodos.route,
        deepLinks = listOf(navDeepLink { uriPattern = "tday://todos/scheduled" }),
    ) { entry ->
        TdayTileDestination(
            route = AppRoute.ScheduledTodos,
            fromHomeTile = rememberHomeTileOrigin(navController, entry),
            // What the tile that pushed this screen was painted in, read off the same
            // hand-off and consumed the same way. A route cannot carry it — see
            // `TILE_TRANSITION_COLOR` — and the surface that grows out of the rectangle is
            // the tile's colour or it is a sheet of the app's background.
            tileColor = rememberHomeTileColor(navController, entry),
        ) {
            TodosRoute(
                mode = TodoListMode.SCHEDULED,
                onBack = { navController.popBackStack() },
                pullRefreshEnabled = !isLocalMode(),
                summaryAvailable = !isLocalMode(),
            )
        }
    }

    composable(
        route = AppRoute.AllTodos.route,
        arguments = listOf(
            navArgument(ARG_HIGHLIGHT_TODO_ID) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
        deepLinks = listOf(
            navDeepLink { uriPattern = "tday://todos/all?highlightTodoId={highlightTodoId}" },
        ),
    ) { entry ->
        val pendingSearchHighlightTodoId = remember(entry) {
            navController.previousBackStackEntry
                ?.savedStateHandle
                ?.remove<String>(PENDING_SEARCH_HIGHLIGHT_TODO_ID)
        }
        val argumentHighlightTodoId = Uri.decode(
            entry.arguments?.getString(ARG_HIGHLIGHT_TODO_ID).orEmpty(),
        ).ifBlank { null }
        val highlightTodoId = pendingSearchHighlightTodoId ?: argumentHighlightTodoId
        // `highlighted` is the one argument in the table that reaches past the route: a
        // resolved highlight id means this arrival came from the home screen's search
        // results or from a deep link rather than from the All tile, so no key is named and
        // the screen does not grow out of a tile the user did not press.
        TdayTileDestination(
            route = AppRoute.AllTodos,
            fromHomeTile = rememberHomeTileOrigin(navController, entry),
            // What the tile that pushed this screen was painted in, read off the same
            // hand-off and consumed the same way. A route cannot carry it — see
            // `TILE_TRANSITION_COLOR` — and the surface that grows out of the rectangle is
            // the tile's colour or it is a sheet of the app's background.
            tileColor = rememberHomeTileColor(navController, entry),
            highlighted = highlightTodoId != null,
        ) {
            TodosRoute(
                mode = TodoListMode.ALL,
                highlightTodoId = highlightTodoId,
                onBack = { navController.popBackStack() },
                pullRefreshEnabled = !isLocalMode(),
                summaryAvailable = !isLocalMode(),
            )
        }
    }

    composable(
        route = AppRoute.PriorityTodos.route,
        deepLinks = listOf(navDeepLink { uriPattern = "tday://todos/priority" }),
    ) { entry ->
        TdayTileDestination(
            route = AppRoute.PriorityTodos,
            fromHomeTile = rememberHomeTileOrigin(navController, entry),
            // What the tile that pushed this screen was painted in, read off the same
            // hand-off and consumed the same way. A route cannot carry it — see
            // `TILE_TRANSITION_COLOR` — and the surface that grows out of the rectangle is
            // the tile's colour or it is a sheet of the app's background.
            tileColor = rememberHomeTileColor(navController, entry),
        ) {
            TodosRoute(
                mode = TodoListMode.PRIORITY,
                onBack = { navController.popBackStack() },
                pullRefreshEnabled = !isLocalMode(),
                summaryAvailable = !isLocalMode(),
            )
        }
    }
}

/**
 * `tday://todos/create` — the widget and car surface's "add a task" entry point. A floater target
 * cannot create anything here (the floater feed lives inside `home`), so it re-points at `home`
 * with the request held as state; a scheduled target opens the sheet on Today directly.
 */
private fun NavGraphBuilder.createTodayTodoRoute(
    navController: NavHostController,
    isLocalMode: () -> Boolean,
    onChangeRootFeedTab: (RootFeedTab) -> Unit,
    onRequestFloaterCreateTask: () -> Unit,
) {
    composable(
        route = AppRoute.CreateTodayTodo.route,
        arguments = listOf(
            navArgument(ARG_CREATE_TARGET) {
                type = NavType.StringType
                defaultValue = CREATE_TARGET_TODAY
            },
        ),
        deepLinks = listOf(navDeepLink {
            uriPattern = "tday://todos/create?target={target}"
        }),
    ) { entry ->
        val createTarget = entry.arguments?.getString(ARG_CREATE_TARGET) ?: CREATE_TARGET_TODAY
        if (createTarget.equals(CREATE_TARGET_FLOATER, ignoreCase = true)) {
            LaunchedEffect(entry.destination.id, createTarget) {
                onChangeRootFeedTab(RootFeedTab.FLOATER_TASK_HOME)
                onRequestFloaterCreateTask()
                navController.navigate(AppRoute.ScheduledTaskHome.route) {
                    popUpTo(entry.destination.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
            Box(modifier = Modifier.fillMaxSize())
        } else {
            val finishCreateTodayFlow = {
                onChangeRootFeedTab(RootFeedTab.SCHEDULED_TASK_HOME)
                val returnedToScheduledTaskHome = navController.popBackStack(
                    route = AppRoute.ScheduledTaskHome.route,
                    inclusive = false,
                )
                if (!returnedToScheduledTaskHome) {
                    navController.navigate(AppRoute.ScheduledTaskHome.route) {
                        popUpTo(AppRoute.CreateTodayTodo.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                navController.navigate(AppRoute.TodayTodos.route) {
                    launchSingleTop = true
                }
            }
            TodosRoute(
                mode = TodoListMode.TODAY,
                onBack = finishCreateTodayFlow,
                openCreateTaskOnStart = true,
                onCreateTaskFlowFinished = finishCreateTodayFlow,
                pullRefreshEnabled = !isLocalMode(),
                summaryAvailable = !isLocalMode(),
            )
        }
    }
}

/** The two per-list feeds: a scheduled list and a floater list. */
private fun NavGraphBuilder.listRoutes(
    navController: NavHostController,
    isLocalMode: () -> Boolean,
    onChangeRootFeedTab: (RootFeedTab) -> Unit,
) {
    composable(
        route = AppRoute.ListTodos.route,
        arguments = listOf(
            navArgument(ARG_LIST_ID) { type = NavType.StringType },
            navArgument(ARG_LIST_NAME) { type = NavType.StringType },
        ),
        deepLinks = listOf(
            navDeepLink { uriPattern = "tday://todos/list/{listId}/{listName}" },
        ),
    ) { entry ->
        val listId = entry.arguments?.getString(ARG_LIST_ID).orEmpty()
        val listName = Uri.decode(entry.arguments?.getString(ARG_LIST_NAME).orEmpty())
        // A list's key carries its id, so this row grows only out of the row for THIS list —
        // and the id comes from the route on both ends, here from the argument and on the
        // tile from the list it is drawn for.
        TdayTileDestination(
            route = AppRoute.ListTodos,
            fromHomeTile = rememberHomeTileOrigin(navController, entry),
            // What the tile that pushed this screen was painted in, read off the same
            // hand-off and consumed the same way. A route cannot carry it — see
            // `TILE_TRANSITION_COLOR` — and the surface that grows out of the rectangle is
            // the tile's colour or it is a sheet of the app's background.
            tileColor = rememberHomeTileColor(navController, entry),
            listId = listId,
        ) {
            TodosRoute(
                mode = TodoListMode.LIST,
                listId = listId,
                listName = listName,
                onBack = { navController.popBackStack() },
                pullRefreshEnabled = !isLocalMode(),
                summaryAvailable = !isLocalMode(),
                onListDeleted = {
                    navController.navigate(AppRoute.ScheduledTaskHome.route) {
                        popUpTo(AppRoute.ScheduledTaskHome.route) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }
    }

    composable(
        route = AppRoute.FloaterListTodos.route,
        arguments = listOf(
            navArgument(ARG_LIST_ID) { type = NavType.StringType },
            navArgument(ARG_LIST_NAME) { type = NavType.StringType },
        ),
        deepLinks = listOf(
            navDeepLink { uriPattern = "tday://floater/list/{listId}/{listName}" },
        ),
    ) { entry ->
        val listId = entry.arguments?.getString(ARG_LIST_ID).orEmpty()
        val listName = Uri.decode(entry.arguments?.getString(ARG_LIST_NAME).orEmpty())
        TdayTileDestination(
            route = AppRoute.FloaterListTodos,
            fromHomeTile = rememberHomeTileOrigin(navController, entry),
            // What the tile that pushed this screen was painted in, read off the same
            // hand-off and consumed the same way. A route cannot carry it — see
            // `TILE_TRANSITION_COLOR` — and the surface that grows out of the rectangle is
            // the tile's colour or it is a sheet of the app's background.
            tileColor = rememberHomeTileColor(navController, entry),
            listId = listId,
        ) {
            TodosRoute(
                mode = TodoListMode.FLOATER,
                listId = listId,
                listName = listName,
                onBack = { navController.popBackStack() },
                pullRefreshEnabled = !isLocalMode(),
                summaryAvailable = !isLocalMode(),
                onListDeleted = {
                    onChangeRootFeedTab(RootFeedTab.FLOATER_TASK_HOME)
                    navController.navigate(AppRoute.ScheduledTaskHome.route) {
                        popUpTo(AppRoute.ScheduledTaskHome.route) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}

/** Completed history, calendar, the car surface, morning sweep, and the in-app guide. */
private fun NavGraphBuilder.utilityRoutes(
    navController: NavHostController,
    isLocalMode: () -> Boolean,
) {
    composable(
        route = AppRoute.Completed.route,
        arguments = listOf(
            // Optional, and null by default, exactly like the All screen's highlight: the
            // bare `completed` route, the deep link below and every arrival that names no
            // tab still match this pattern and open the first tab.
            navArgument(ARG_COMPLETED_SCOPE) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
        deepLinks = listOf(
            navDeepLink { uriPattern = "tday://completed" },
            // The scoped form web's own Floater board links with
            // (`/app/completed?scope=floater`), so the two clients' deep links say the
            // same thing.
            navDeepLink { uriPattern = "tday://completed?scope={scope}" },
        ),
    ) { entry ->
        val viewModel: CompletedViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        OnRouteResume { viewModel.load() }
        // The board the user came through, off the route. It decides which tab opens AND
        // which rectangle the screen grows out of: the two Completed tiles are two
        // rectangles pushing two differently-scoped routes, so both ends have to name the
        // same one or the zoom silently stops. See `AppRoute.tileTransitionKey`.
        val completedScope = CompletedScope.fromWire(entry.arguments?.getString(ARG_COMPLETED_SCOPE))
        TdayTileDestination(
            route = AppRoute.Completed,
            scope = completedScope,
            fromHomeTile = rememberHomeTileOrigin(navController, entry),
            // What the tile that pushed this screen was painted in, read off the same
            // hand-off and consumed the same way. A route cannot carry it — see
            // `TILE_TRANSITION_COLOR` — and the surface that grows out of the rectangle is
            // the tile's colour or it is a sheet of the app's background.
            tileColor = rememberHomeTileColor(navController, entry),
        ) {
            CompletedScreen(
                uiState = uiState,
                initialScope = completedScope,
                onBack = { navController.popBackStack() },
                onRefresh = { viewModel.refresh(userInitiated = true) },
                onUncomplete = viewModel::uncomplete,
                onDelete = viewModel::delete,
                onUpdateTask = viewModel::update,
            )
        }
    }

    composable(
        route = AppRoute.Calendar.route,
        deepLinks = listOf(navDeepLink { uriPattern = "tday://calendar" }),
    ) { entry ->
        val viewModel: CalendarViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        OnRouteResume { viewModel.load() }
        TdayTileDestination(
            route = AppRoute.Calendar,
            fromHomeTile = rememberHomeTileOrigin(navController, entry),
            // What the tile that pushed this screen was painted in, read off the same
            // hand-off and consumed the same way. A route cannot carry it — see
            // `TILE_TRANSITION_COLOR` — and the surface that grows out of the rectangle is
            // the tile's colour or it is a sheet of the app's background.
            tileColor = rememberHomeTileColor(navController, entry),
        ) {
            CalendarScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onRefresh = { viewModel.refresh(userInitiated = true) },
                onCreateTask = viewModel::createTask,
                onParseTaskTitleNlp = viewModel::parseTaskTitleNlp,
                onCompleteTask = viewModel::complete,
                onUpdateTask = viewModel::updateTask,
                onMoveTask = viewModel::moveTask,
                onDelete = viewModel::delete,
            )
        }
    }

    composable(
        route = AppRoute.Car.route,
        deepLinks = listOf(navDeepLink { uriPattern = "tday://car" }),
    ) {
        val viewModel: CarTaskSurfaceViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val voiceLauncher = rememberCarTaskVoiceCreateLauncher(
            onVoiceTitle = viewModel::createFromVoice,
            onVoiceUnavailable = { mode ->
                val target = when (mode) {
                    CarTaskMode.TODAY -> CREATE_TARGET_TODAY
                    CarTaskMode.FLOATER -> CREATE_TARGET_FLOATER
                }
                navController.navigate("todos/create?target=$target") {
                    launchSingleTop = true
                }
            },
        )
        OnRouteResume { viewModel.refresh() }
        CarTaskSurfaceScreen(
            uiState = uiState,
            onModeSelected = viewModel::selectMode,
            onCreateWithVoice = { voiceLauncher(uiState.mode) },
            onComplete = viewModel::complete,
        )
    }

    composable(
        route = AppRoute.MorningSweep.route,
        deepLinks = listOf(navDeepLink { uriPattern = "tday://morning-sweep" }),
    ) {
        MorningSweepScreen(
            onBack = { navController.popBackStack() },
        )
    }

    composable(
        route = AppRoute.HelpGuide.route,
        arguments = listOf(
            navArgument(ARG_GUIDE_TOPIC) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { backStackEntry ->
        HelpGuideScreen(
            isLocalMode = isLocalMode(),
            onBack = { navController.popBackStack() },
            onOpenDeepLink = { route ->
                navController.navigate(route) { launchSingleTop = true }
            },
            initialTopic = backStackEntry.arguments?.getString(ARG_GUIDE_TOPIC),
        )
    }
}

/**
 * Settings and the release notes screen it links to. Both states arrive as `() -> T` readers for
 * the same reason as [rootFeedRoutes]: the read has to happen in the destination, not in the
 * builder.
 */
private fun NavGraphBuilder.settingsRoutes(
    navController: NavHostController,
    appUiState: () -> AppUiState,
    appViewModel: AppViewModel,
    releaseUiState: () -> LatestReleaseUiState,
    releaseViewModel: LatestReleaseViewModel,
    passwordChangedToastMessage: String,
    profileNameUpdatedToastMessage: String,
    securityQuestionsUpdatedToastMessage: String,
) {
    composable(
        route = AppRoute.Settings.route,
        deepLinks = listOf(navDeepLink { uriPattern = "tday://settings" }),
    ) {
        OnRouteResume {
            appViewModel.refreshAiSummaryPreference()
            appViewModel.refreshDefaultHomeScreen()
            appViewModel.refreshVersionInfo()
        }
        val settingsUiState = appUiState()
        val settingsReleaseUiState = releaseUiState()
        SettingsScreen(
            user = settingsUiState.user,
            isLocalMode = settingsUiState.isLocalMode,
            selectedThemeMode = settingsUiState.themeMode,
            selectedReminder = settingsUiState.selectedReminder,
            syncStatus = settingsUiState.syncStatus,
            aiSummaryEnabled = settingsUiState.aiSummaryEnabled,
            defaultHomeScreen = settingsUiState.defaultHomeScreen,
            hasUpdate = settingsReleaseUiState.hasUpdate,
            latestVersionName = settingsReleaseUiState.latestRelease?.version,
            backendVersion = settingsUiState.backendVersion,
            versionCheckResult = settingsUiState.versionCheckResult,
            onThemeModeSelected = appViewModel::setThemeMode,
            onDefaultHomeScreenSelected = appViewModel::setDefaultHomeScreen,
            onReminderSelected = appViewModel::setDefaultReminder,
        selectedDayAhead = settingsUiState.selectedDayAhead,
        onDayAheadSelected = appViewModel::setDayAhead,
            onSyncNow = appViewModel::syncNow,
            onToggleAiSummary = appViewModel::setAiSummaryEnabled,
            onBack = { navController.popBackStack() },
            onLogout = { appViewModel.logout() },
            onLeaveLocalWorkspace = { appViewModel.leaveLocalWorkspace() },
            onOpenLatestRelease = { navController.navigate(AppRoute.LatestRelease.route) },
            onOpenHelpGuide = { navController.navigate(AppRoute.HelpGuide.create()) },
            onUpdateName = { newName ->
                appViewModel.updateDisplayName(newName).also { result ->
                    if (result is ProfileEditResult.Success) {
                        appViewModel.snackbarManager.showSuccess(
                            profileNameUpdatedToastMessage,
                        )
                    }
                }
            },
            onChangePassword = { current, newPassword ->
                appViewModel.changePassword(current, newPassword).also { result ->
                    if (result is ProfileEditResult.Success) {
                        appViewModel.snackbarManager.showSuccess(
                            passwordChangedToastMessage,
                        )
                    }
                }
            },
            onForgotPassword = {
                navController.navigate(AppRoute.ForgotPassword.route) {
                    launchSingleTop = true
                }
            },
            onLoadSecurityQuestionStatus = { appViewModel.securityQuestionStatus() },
            onFetchSecurityQuestions = { appViewModel.fetchSecurityQuestions() },
            onUpdateSecurityQuestions = { current, answers ->
                appViewModel.updateSecurityQuestions(current, answers).also { result ->
                    if (result is ProfileEditResult.Success) {
                        appViewModel.snackbarManager.showSuccess(
                            securityQuestionsUpdatedToastMessage,
                        )
                    }
                }
            },
        )
    }

    composable(route = AppRoute.LatestRelease.route) {
        OnRouteResume {
            appViewModel.refreshVersionInfo()
        }
        LatestReleaseScreen(
            uiState = releaseUiState(),
            onBack = { navController.popBackStack() },
            onRetry = releaseViewModel::load,
        )
    }
}

/**
 * The `home` route: the selected root feed, with the onboarding wizard and the blocking gates
 * (update required, security questions) layered over it.
 */
@Composable
private fun ScheduledTaskHomeRoute(
    appUiState: AppUiState,
    appViewModel: AppViewModel,
    navController: NavHostController,
    unauthenticatedUiState: ScheduledTaskHomeUiState,
    rootFeedTab: RootFeedTab,
    onSelectRootFeedTab: (RootFeedTab) -> Unit,
    onChangeRootFeedTab: (RootFeedTab) -> Unit,
    rootCreateTaskRequestKey: Int,
    onCreateTaskRequestHandled: (Int) -> Unit,
    onRequestCreateTask: () -> Unit,
    scheduledScrollToTopRequestKey: Int,
    floaterScrollToTopRequestKey: Int,
    rootDockCollapsed: Boolean,
    onRootDockCollapsedChange: (Boolean) -> Unit,
    rootControlsVisible: Boolean,
    onRootControlsVisibleChange: (Boolean) -> Unit,
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
    // Never true before the session has resolved: this route only exists
    // once the graph is built, and that waits for rootDestination to
    // leave SPLASH.
    val showOnboardingWizard = appUiState.rootDestination == RootDestination.ONBOARDING

    // Remember the last attempted credentials so a pending-approval result
    // can be persisted into the holding screen (which re-attempts login).
    var lastAuthUsername by remember { mutableStateOf("") }
    var lastAuthPassword by remember { mutableStateOf("") }
    LaunchedEffect(authUiState.pendingApproval) {
        if (authUiState.pendingApproval && lastAuthPassword.isNotBlank()) {
            appViewModel.enterPendingApproval(lastAuthUsername, lastAuthPassword)
        }
    }

    // Locking and unlocking the app used to be three snaps landing on one frame: the
    // backdrop jumped out of focus, the wizard appeared over it, and the feed underneath
    // hard-swapped between the locked placeholder and the real one. They are one event, so
    // they run as one — same rung, same curve, all three. Nothing travels: the wizard is
    // drawn where it will stay and the arriving feed takes the slot the leaving one had, so
    // by the geometry rule this is not Emphasis, and a whole-screen handover is the Quick
    // rung the vocabulary already names for it — the same call the dock's tab swap makes in
    // RootFeedContent below. Standard is the curve because a crossfade runs both halves off
    // one clock and neither Enter nor Exit describes that. Symmetric on purpose: the way in
    // and the way out are one handover reversed rather than an arrival and its departure,
    // which is also the strictest reading of "an exit is never longer than the enter it
    // undoes". iOS times the same moment on this rung and curve, from one transaction; it
    // has no placeholder feed to cross, so its third surface is the floating controls.
    val motionEnabled = rememberTdayMotionEnabled()
    val backdropBlur by animateDpAsState(
        targetValue = if (showOnboardingWizard) ONBOARDING_BACKDROP_BLUR else 0.dp,
        animationSpec = if (motionEnabled) {
            tween(
                durationMillis = TdayMotionTokens.Durations.Quick,
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "onboardingBackdropBlur",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    // Skipped rather than passed a zero radius: `blur` is a RenderEffect on a
                    // graphics layer, and keeping one alive to render nothing costs the whole
                    // feed an offscreen buffer for as long as the app is unlocked.
                    if (backdropBlur > 0.dp) {
                        Modifier.blur(backdropBlur)
                    } else {
                        Modifier
                    },
                ),
        ) {
            // The placeholder feed and the real one used to be an early return inside
            // RootFeedContent, which made the swap a recomposition and therefore a cut. It is
            // decided here instead, beside the blur and the wizard it happens with, so the
            // whole lock/unlock is one moment expressed in one place.
            Crossfade(
                targetState = appUiState.isWorkspaceAvailable,
                modifier = Modifier.fillMaxSize(),
                animationSpec = if (motionEnabled) {
                    tween(
                        durationMillis = TdayMotionTokens.Durations.Quick,
                        easing = TdayMotionTokens.Easings.Standard,
                    )
                } else {
                    snap()
                },
                label = "rootFeedLock",
            ) { workspaceAvailable ->
                if (workspaceAvailable) {
                    RootFeedContent(
                        appUiState = appUiState,
                        appViewModel = appViewModel,
                        navController = navController,
                        rootFeedTab = rootFeedTab,
                        onSelectRootFeedTab = onSelectRootFeedTab,
                        onChangeRootFeedTab = onChangeRootFeedTab,
                        rootCreateTaskRequestKey = rootCreateTaskRequestKey,
                        onCreateTaskRequestHandled = onCreateTaskRequestHandled,
                        onRequestCreateTask = onRequestCreateTask,
                        scheduledScrollToTopRequestKey = scheduledScrollToTopRequestKey,
                        floaterScrollToTopRequestKey = floaterScrollToTopRequestKey,
                        rootDockCollapsed = rootDockCollapsed,
                        onRootDockCollapsedChange = onRootDockCollapsedChange,
                        rootControlsVisible = rootControlsVisible,
                        onRootControlsVisibleChange = onRootControlsVisibleChange,
                    )
                } else {
                    LockedRootFeed(uiState = unauthenticatedUiState)
                }
            }
        }

        // AnimatedVisibility does not play an enter for a `visible` that was already true on
        // the first composition, and that is the behaviour wanted here rather than a limit
        // worked around: a cold start into onboarding has nothing to hand over from, so the
        // wizard is drawn finished over a backdrop that was never in focus. What animates is
        // the two transitions that have a before — signing in, and signing back out.
        AnimatedVisibility(
            visible = showOnboardingWizard,
            enter = if (motionEnabled) {
                fadeIn(
                    animationSpec = tween(
                        durationMillis = TdayMotionTokens.Durations.Quick,
                        easing = TdayMotionTokens.Easings.Standard,
                    ),
                )
            } else {
                EnterTransition.None
            },
            exit = if (motionEnabled) {
                fadeOut(
                    animationSpec = tween(
                        durationMillis = TdayMotionTokens.Durations.Quick,
                        easing = TdayMotionTokens.Easings.Standard,
                    ),
                )
            } else {
                ExitTransition.None
            },
            label = "onboardingWizard",
        ) {
            OnboardingOverlay(
                appUiState = appUiState,
                appViewModel = appViewModel,
                authViewModel = authViewModel,
                authUiState = authUiState,
                onCredentialsAttempted = { username, password ->
                    lastAuthUsername = username
                    lastAuthPassword = password
                },
            )
        }

        AuthenticatedGates(
            appUiState = appUiState,
            appViewModel = appViewModel,
            authViewModel = authViewModel,
        )
    }
}

/**
 * Whichever root feed the dock has selected, with the dock and create button floating over it.
 * Only ever composed for a workspace that is available — the locked placeholder that stands in
 * for it otherwise is the other half of [ScheduledTaskHomeRoute]'s lock crossfade.
 */
@Composable
private fun RootFeedContent(
    appUiState: AppUiState,
    appViewModel: AppViewModel,
    navController: NavHostController,
    rootFeedTab: RootFeedTab,
    onSelectRootFeedTab: (RootFeedTab) -> Unit,
    onChangeRootFeedTab: (RootFeedTab) -> Unit,
    rootCreateTaskRequestKey: Int,
    onCreateTaskRequestHandled: (Int) -> Unit,
    onRequestCreateTask: () -> Unit,
    scheduledScrollToTopRequestKey: Int,
    floaterScrollToTopRequestKey: Int,
    rootDockCollapsed: Boolean,
    onRootDockCollapsedChange: (Boolean) -> Unit,
    rootControlsVisible: Boolean,
    onRootControlsVisibleChange: (Boolean) -> Unit,
) {
    // The two root tabs' swipe slot, owned here rather than by either feed, and
    // this box is the reason. The dock and the create button below are SIBLINGS
    // of the crossfade that draws the feed, so they sit outside the feed's own
    // Scaffold — and Compose routes a pointer down into the hit child's path
    // only, which meant an interceptor installed on that Scaffold could not see
    // a tap on either. "Tapping the dock or the FAB closes the open row" was the
    // decision on all three clients and was quietly false on the two Android
    // screens the user lives in. This box is the smallest composable that
    // actually contains everything a dismissing tap can land on, so it is the
    // screen, and the slot belongs at the same altitude as the interceptor.
    //
    // Nothing reads `openId` in composition -- see [TaskSwipeSlot]. The holder's
    // identity never changes, so handing it to the feeds changes no argument
    // either of them is keyed on, and the pointer coroutine below reads it
    // outside composition entirely. A feed of hundreds recomposes nothing when a
    // row opens, which was already true and had to stay true.
    val rootSwipeSlot = remember { TaskSwipeSlot() }
    // Changing tab is leaving the screen the row was open on. The dock tap that
    // usually causes it already closes the row through the interceptor, but the
    // tab also changes from the widget's deep link and from a feed's own
    // "Anytime" affordance, and an id left pointing at a disposed row would arm
    // the interceptor over a feed with nothing open in it.
    LaunchedEffect(rootFeedTab) { rootSwipeSlot.openId = null }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // One interceptor for both root tabs, installed where the feeds, the
            // dock and the create button all sit inside it. Observes and never
            // consumes -- see `tdayClosesSwipeRowOnOutsideTap`; the dock still
            // switches tab and the button still opens its sheet in the same
            // touch that shuts the row.
            .tdayClosesSwipeRowOnOutsideTap(
                slot = rootSwipeSlot,
                close = { rootSwipeSlot.openId = null },
            )
    ) {
        val motionEnabled = rememberTdayMotionEnabled()

        // The dock's selector springs across to the tab that was tapped, and the feed under
        // it used to change on the next frame: one gesture running at two speeds, so the
        // body read as a cut rather than as the thing the pill was carrying. Nothing here
        // travels — the arriving feed is drawn in the slot the leaving one had — so by the
        // geometry rule this is not Emphasis, and a tab handover is a rung the vocabulary
        // names for it.
        //
        // It is `Enter`, and that is a reversal of what this swap used to say. The Quick
        // pair it carried argued shorter-than-the-selector so the body would not still be
        // resolving after the control it answers had landed; `docs/motion.md`'s `Scene`
        // bullet had already answered that in prose — "Nor is this rung for route or tab
        // handovers: those are `Enter` on both clients that have them" — and `Enter` keeps
        // the ordering the argument was about (200 ms of body against the selector's own
        // spring) while buying the one thing the old shape could not express at all, which
        // is that web's fade is TWO curves.
        //
        // Asymmetric, and it has to be: `tday-web`'s `.tday-route-fade` brings the arriving
        // screen in on `--tday-ease-enter` while `::view-transition-old(root)` takes the
        // leaving one out on `--tday-ease-exit`, both over `--tday-duration-enter`. Those
        // are [TdayMotionTokens.Easings.Enter] ("something arriving, which should settle
        // rather than stop") and [TdayMotionTokens.Easings.Exit] ("something leaving, which
        // should commit rather than drift off") — the same split `TdaySheetMotion` already
        // made for a card and for the same reason. A `Crossfade` cannot say this: it hands
        // one `animationSpec` to both children, so its two halves share a clock and a curve
        // by construction. Two `AnimatedVisibility`s are the smallest shape that can hold
        // two specs, and they keep the property the crossfade had — both feeds stay composed
        // for the whole handover, so the window really does have two live layers in it.
        //
        // The departing feed is drawn ABOVE the arriving one, which is the other half of
        // web's effect and the reason it is not one symmetric crossfade. `globals.css`'s
        // comment states the read it is after — "a fade THROUGH the background rather than a
        // straight crossfade — the new content reaches half opacity a beat after the old has
        // left half of its own" — and `::view-transition-old(root)` carries `z-index: 1` for
        // the ordering. A `Box` orders its children by `Modifier.zIndex`, and each feed asks
        // for it off the one question that answers it: the feed that is NOT [rootFeedTab] is
        // the one on its way out.
        //
        // With motion off neither transition is passed at all, so the leaving feed goes in
        // the frame the tab changes and the arriving one is drawn finished in its slot
        // (docs/motion.md's fifth idiom rule). `AnimatedVisibility` also plays no enter for a
        // `visible` that was already true on the first composition, so a cold start draws the
        // selected feed in place rather than flying it in — and, the point of the shape here,
        // nothing is ever held half-faded: an un-animated departing layer would be opaque and
        // on top, which is the exact trap `globals.css` documents and takes its outgoing
        // snapshot off outright for rather than merely un-animating it.
        val scheduledFeedSelected = rootFeedTab == RootFeedTab.SCHEDULED_TASK_HOME
        val rootFeedEnter = if (motionEnabled) {
            fadeIn(
                animationSpec = tween(
                    durationMillis = TdayMotionTokens.Durations.Enter,
                    easing = TdayMotionTokens.Easings.Enter,
                ),
            )
        } else {
            EnterTransition.None
        }
        val rootFeedExit = if (motionEnabled) {
            fadeOut(
                animationSpec = tween(
                    durationMillis = TdayMotionTokens.Durations.Enter,
                    easing = TdayMotionTokens.Easings.Exit,
                ),
            )
        } else {
            ExitTransition.None
        }
        // How far above the arriving feed the DEPARTING one is raised while it leaves, and it
        // is zero when there is no leaving to do.
        //
        // The raise is the whole of what reproduces web's read — its departing screen is
        // `z-index: 1`, so the new content comes up from underneath it rather than meeting it
        // halfway — and it is only meaningful while a departure is playing. With motion off
        // there is no departure: the exiting `AnimatedVisibility` is handed
        // `ExitTransition.None`, and a raise that is still applied is a live opaque feed
        // ordered ABOVE the one that just arrived, for however long the empty exit takes to
        // dispose its content. That is the one frame web's `globals.css` takes its outgoing
        // snapshot off the screen outright to avoid, and the cheapest way not to have it is
        // not to raise anything when nothing is animating. The z-order is the only thing in
        // this block that a refused motion has to switch off as well as shorten.
        val rootFeedDepartingZ = if (motionEnabled) 1f else 0f
        // Both feeds are composed for the length of the fade and only one of them is the tab
        // that was asked for. A create-task request landing inside that window — the widget's
        // `tday://todos/create?target=floater` switches tab and then asks for the sheet —
        // belongs to the arriving feed alone: handing the live key to the copy on its way out
        // would open a sheet nobody asked for and consume the request the arriving feed is
        // waiting for. 0 is the same "nothing pending" sentinel `consumeRootCreateTaskRequest`
        // writes back. The departing copy is handed it from the first frame it stops being the
        // selected tab, which is what makes that guarantee structural rather than a matter of
        // the fade outlasting a sleep — see the same sentinel on iOS's `createTaskRequestID`.
        AnimatedVisibility(
            visible = scheduledFeedSelected,
            modifier = Modifier
                .fillMaxSize()
                // Above the feed it is leaving, for the whole of its departure — and not
                // at all when there is no departure to be above it for.
                .zIndex(if (scheduledFeedSelected) 0f else rootFeedDepartingZ),
            enter = rootFeedEnter,
            exit = rootFeedExit,
            label = "rootScheduledFeedSwap",
        ) {
            ScheduledTaskHomeFeed(
                appUiState = appUiState,
                appViewModel = appViewModel,
                navController = navController,
                swipeSlot = rootSwipeSlot,
                onChangeRootFeedTab = onChangeRootFeedTab,
                rootCreateTaskRequestKey = if (scheduledFeedSelected) rootCreateTaskRequestKey else 0,
                onCreateTaskRequestHandled = onCreateTaskRequestHandled,
                scrollToTopRequestKey = scheduledScrollToTopRequestKey,
                onRootDockCollapsedChange = onRootDockCollapsedChange,
                onRootControlsVisibleChange = onRootControlsVisibleChange,
            )
        }
        AnimatedVisibility(
            visible = !scheduledFeedSelected,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (scheduledFeedSelected) rootFeedDepartingZ else 0f),
            enter = rootFeedEnter,
            exit = rootFeedExit,
            label = "rootFloaterFeedSwap",
        ) {
            FloaterTaskHomeFeed(
                appUiState = appUiState,
                navController = navController,
                swipeSlot = rootSwipeSlot,
                onChangeRootFeedTab = onChangeRootFeedTab,
                rootCreateTaskRequestKey = if (scheduledFeedSelected) 0 else rootCreateTaskRequestKey,
                onCreateTaskRequestHandled = onCreateTaskRequestHandled,
                scrollToTopRequestKey = floaterScrollToTopRequestKey,
                onRootDockCollapsedChange = onRootDockCollapsedChange,
                onRootControlsVisibleChange = onRootControlsVisibleChange,
            )
        }

        // The dock's own tint already crosses between the two accents when the tab
        // changes; the create button was the last surface still cutting, so a swap left a
        // blue-to-green jump in the corner of an otherwise continuous handover. The accent
        // is part of that one handover rather than a second event, so it rides the body's
        // rung — [TdayMotionTokens.Durations.Enter], the length the feeds above cross on.
        // Its curve stays [TdayMotionTokens.Easings.Standard]: the accent is one property
        // travelling from one colour to another, so it has no arriving half and no departing
        // half for the pair above to hand it, and Standard is the curve the vocabulary keeps
        // for when nothing argues otherwise. Longer than the selector's spring on purpose,
        // exactly as the body is. iOS's create button is a SwiftUI fill inside the
        // transaction its tab switch already runs in, so it crosses on the same length. Its
        // dock only half agrees: the
        // collapsed pill's tint is in that transaction too, but the expanded control is a
        // `UISegmentedControl` whose accent is assigned in `updateUIView`, which reads no
        // transaction and so cuts. With motion off it snaps: the button is drawn in the
        // arriving tab's accent, finished.
        //
        // Hoisted out of the visibility gate below so the accent is not re-seeded every
        // time the search field gives the controls their row back: a button that ducks in
        // already wearing the tab's colour is one event, and one that ducks in blue and
        // then turns green is two.
        val rootCreateTaskButtonColor by animateColorAsState(
            targetValue = if (rootFeedTab == RootFeedTab.FLOATER_TASK_HOME) {
                TdayFloaterAccent
            } else {
                TdayTodayBlue
            },
            animationSpec = if (motionEnabled) {
                tween(
                    durationMillis = TdayMotionTokens.Durations.Enter,
                    easing = TdayMotionTokens.Easings.Standard,
                )
            } else {
                snap()
            },
            label = "rootCreateTaskAccent",
        )

        // The dock and the create button used to be a bare `if`, so opening search took
        // them off the screen in the frame the field expanded into — a hole where the
        // chrome had been, and then the chrome back in it. They duck instead: straight
        // down and out through the bottom edge, and back up the same way.
        //
        // Travel, so Emphasis by the geometry rule — and the Settle spring is the rung's
        // spelling here, the token whose own doc string names a dock and a bar. A tween
        // would have to pick a curve for weight that is leaving under its own momentum
        // and coming back to rest; that is the question springs answer. The same spec
        // drives both directions because the exit IS the enter played backwards, and
        // giving it a length of its own would read as two different gestures rather than
        // one control getting out of the way. Equal also satisfies the first idiom rule,
        // which only forbids an exit that outlasts its arrival.
        //
        // The fade is not decoration. `slideOutVertically { it }` offsets by the height it
        // measured, which clears the control's own box but not the gesture-bar strip
        // underneath it, so opacity is what guarantees the thing is gone rather than
        // parked below the navigation bar. iOS combines the same two for the same reason;
        // web spells the spring as the Gesture easing, having no spring runtime.
        //
        // With motion off, neither transition is passed at all: the controls are taken
        // away and put back finished, which is the fifth idiom rule. AnimatedVisibility
        // also plays no enter for a `visible` that was already true on the first
        // composition, so a cold start draws the chrome in its slot rather than flying it
        // in from nowhere — the same call the onboarding wizard above makes.
        val duckEnter = if (motionEnabled) {
            slideInVertically(
                animationSpec = TdayMotionTokens.Springs.settle(),
                initialOffsetY = { fullHeight -> fullHeight },
            ) + fadeIn(animationSpec = TdayMotionTokens.Springs.settle())
        } else {
            EnterTransition.None
        }
        val duckExit = if (motionEnabled) {
            slideOutVertically(
                animationSpec = TdayMotionTokens.Springs.settle(),
                targetOffsetY = { fullHeight -> fullHeight },
            ) + fadeOut(animationSpec = TdayMotionTokens.Springs.settle())
        } else {
            ExitTransition.None
        }

        // Two wrappers rather than one around both: the dock and the button are anchored
        // to opposite corners, and `Modifier.align` is a BoxScope call that the content
        // scope inside an AnimatedVisibility does not offer.
        AnimatedVisibility(
            visible = rootControlsVisible,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .zIndex(8f),
            enter = duckEnter,
            exit = duckExit,
            label = "rootFeedDockDuck",
        ) {
            RootFeedDock(
                activeTab = rootFeedTab,
                collapsed = rootDockCollapsed,
                onTabSelected = onSelectRootFeedTab,
            )
        }
        AnimatedVisibility(
            visible = rootControlsVisible,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .zIndex(8f),
            enter = duckEnter,
            exit = duckExit,
            label = "rootCreateTaskButtonDuck",
        ) {
            RootCreateTaskButton(
                onClick = onRequestCreateTask,
                backgroundColor = rootCreateTaskButtonColor,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(
                        end = TdayDimens.ContentPaddingHorizontal,
                        bottom = TdayDimens.ContentPaddingHorizontal,
                    ),
            )
        }
    }
}

/** The scheduled-task home feed: the card grid every timeline scope is opened from. */
@Composable
private fun ScheduledTaskHomeFeed(
    appUiState: AppUiState,
    appViewModel: AppViewModel,
    navController: NavHostController,
    swipeSlot: TaskSwipeSlot,
    onChangeRootFeedTab: (RootFeedTab) -> Unit,
    rootCreateTaskRequestKey: Int,
    onCreateTaskRequestHandled: (Int) -> Unit,
    scrollToTopRequestKey: Int,
    onRootDockCollapsedChange: (Boolean) -> Unit,
    onRootControlsVisibleChange: (Boolean) -> Unit,
) {
    val scheduledTaskHomeViewModel: ScheduledTaskHomeViewModel = hiltViewModel()
    val scheduledTaskHomeUiState by scheduledTaskHomeViewModel.uiState.collectAsStateWithLifecycle()
    OnRouteResume {
        scheduledTaskHomeViewModel.refreshFromCache()
        appViewModel.refreshVersionInfo()
    }
    ScheduledTaskHomeScreen(
        uiState = scheduledTaskHomeUiState,
        onRefresh = { scheduledTaskHomeViewModel.refresh(userInitiated = true) },
        pullRefreshEnabled = !appUiState.isLocalMode,
        // Every one of these is a tile press, so every one of them says so: the destination
        // may only grow out of the rectangle that was actually pressed, and the rectangle
        // may only be painted in the colour the user pressed it in.
        // `navigateFromHomeTile(route, tileColorArgb)` is the whole of that statement — see
        // `TILE_TRANSITION_ORIGIN` and `TILE_TRANSITION_COLOR`, and note the colour is a
        // parameter with no default: the tile is where it exists, and the tile hands it to
        // this lambda. The pushes that are NOT presses (the shortcut, the notification, the
        // widget row) call `navigate` and therefore leave both unset.
        onOpenToday = { tileColor ->
            navController.navigateFromHomeTile(AppRoute.TodayTodos.route, tileColor.toArgb())
        },
        onOpenOverdue = { tileColor ->
            navController.navigateFromHomeTile(AppRoute.OverdueTodos.route, tileColor.toArgb())
        },
        onOpenScheduled = { tileColor ->
            navController.navigateFromHomeTile(AppRoute.ScheduledTodos.route, tileColor.toArgb())
        },
        onOpenAll = { tileColor ->
            navController.navigateFromHomeTile(AppRoute.AllTodos.create(), tileColor.toArgb())
        },
        onOpenPriority = { tileColor ->
            navController.navigateFromHomeTile(AppRoute.PriorityTodos.route, tileColor.toArgb())
        },
        onOpenCompleted = { tileColor ->
            navController.navigateFromHomeTile(
                AppRoute.Completed.create(CompletedScope.Tasks),
                tileColor.toArgb(),
            )
        },
        onOpenCalendar = { tileColor ->
            navController.navigateFromHomeTile(AppRoute.Calendar.route, tileColor.toArgb())
        },
        onOpenFloater = {
            onChangeRootFeedTab(RootFeedTab.FLOATER_TASK_HOME)
        },
        onOpenSettings = { navController.navigate(AppRoute.Settings.route) },
        onOpenTaskFromSearch = { todoId ->
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.set(
                    PENDING_SEARCH_HIGHLIGHT_TODO_ID,
                    todoId
                )
            navController.navigate(AppRoute.AllTodos.create())
        },
        onOpenList = { id, name, tileColor ->
            navController.navigateFromHomeTile(
                AppRoute.ListTodos.create(
                    id,
                    name
                ),
                tileColor.toArgb(),
            )
        },
        onCreateTask = { payload ->
            scheduledTaskHomeViewModel.createTask(payload)
        },
        onParseTaskTitleNlp = scheduledTaskHomeViewModel::parseTaskTitleNlp,
        onSuggestRepeat = scheduledTaskHomeViewModel::suggestRepeatRrule,
        onCreateList = { name, color, iconKey ->
            scheduledTaskHomeViewModel.createList(
                name = name,
                color = color,
                iconKey = iconKey,
            )
        },
        onCompleteTask = { todo ->
            scheduledTaskHomeViewModel.completeTodo(
                todo
            )
        },
        onDeleteTask = { todo ->
            scheduledTaskHomeViewModel.deleteTodo(
                todo
            )
        },
        onUpdateTask = { todo, payload ->
            scheduledTaskHomeViewModel.updateTask(
                todo,
                payload
            )
        },
        onSummarize = scheduledTaskHomeViewModel::summarizeToday,
        summaryAvailable = !appUiState.isLocalMode,
        showRootFeedDock = false,
        hostSwipeSlot = swipeSlot,
        createTaskRequestKey = rootCreateTaskRequestKey,
        onCreateTaskRequestHandled = onCreateTaskRequestHandled,
        scrollToTopRequestKey = scrollToTopRequestKey,
        onRootDockCollapsedChange = onRootDockCollapsedChange,
        onRootControlsVisibleChange = onRootControlsVisibleChange,
    )
}

/** The Anytime/Floater sibling feed, drawn in the same slot as the scheduled one. */
@Composable
private fun FloaterTaskHomeFeed(
    appUiState: AppUiState,
    navController: NavHostController,
    swipeSlot: TaskSwipeSlot,
    onChangeRootFeedTab: (RootFeedTab) -> Unit,
    rootCreateTaskRequestKey: Int,
    onCreateTaskRequestHandled: (Int) -> Unit,
    scrollToTopRequestKey: Int,
    onRootDockCollapsedChange: (Boolean) -> Unit,
    onRootControlsVisibleChange: (Boolean) -> Unit,
) {
    TodosRoute(
        mode = TodoListMode.FLOATER,
        onBack = { onChangeRootFeedTab(RootFeedTab.SCHEDULED_TASK_HOME) },
        pullRefreshEnabled = !appUiState.isLocalMode,
        summaryAvailable = !appUiState.isLocalMode,
        onOpenFloaterList = { id, name, tileColor ->
            navController.navigateFromHomeTile(
                AppRoute.FloaterListTodos.create(
                    id,
                    name
                ),
                tileColor.toArgb(),
            )
        },
        onOpenCompleted = { tileColor ->
            navController.navigateFromHomeTile(
                AppRoute.Completed.create(CompletedScope.Floater),
                tileColor.toArgb(),
            )
        },
        onOpenSettings = {
            navController.navigate(AppRoute.Settings.route)
        },
        showRootFeedDock = false,
        showCreateTaskButton = false,
        hostSwipeSlot = swipeSlot,
        usesRootFeedHeader = true,
        createTaskRequestKey = rootCreateTaskRequestKey,
        onCreateTaskRequestHandled = onCreateTaskRequestHandled,
        scrollToTopRequestKey = scrollToTopRequestKey,
        onRootDockCollapsedChange = onRootDockCollapsedChange,
        onRootControlsVisibleChange = onRootControlsVisibleChange,
    )
}

/**
 * The inert feed drawn behind the onboarding wizard, so the blurred backdrop is the app's own
 * layout rather than an empty screen. Every action is a no-op and the only list is a placeholder.
 */
@Composable
private fun LockedRootFeed(uiState: ScheduledTaskHomeUiState) {
    Box(modifier = Modifier.fillMaxSize()) {
        ScheduledTaskHomeScreen(
            uiState = uiState,
            onRefresh = {},
            onOpenToday = {},
            onOpenOverdue = {},
            onOpenScheduled = {},
            onOpenAll = {},
            onOpenPriority = {},
            onOpenCompleted = {},
            onOpenCalendar = {},
            onOpenFloater = {},
            onOpenSettings = {},
            onOpenTaskFromSearch = {},
            onOpenList = { _, _, _ -> },
            onCreateTask = { _ -> },
            onParseTaskTitleNlp = { _, _ -> null },
            onCreateList = { _, _, _ -> },
            onCompleteTask = {},
            onDeleteTask = {},
            onUpdateTask = { _, _ -> },
            summaryAvailable = false,
        )

        // The backdrop draws its own create button now instead of inheriting one from the
        // screen's `Scaffold` slot, which is where it used to come from: this was the single
        // caller still taking that slot's default, and it was the last reason the slot existed.
        // Dropping the slot without putting the button back here would have taken the root
        // feed's most prominent control off the backdrop, which is the opposite of what this
        // composable is for — a layout with a hole where the "+" goes is the empty screen the
        // KDoc above says it exists to avoid.
        //
        // Placed at `RootFeedContent`'s geometry down to the padding, because these two are the
        // branches of one Crossfade: at sign-in the circle is already where the live one is
        // about to be drawn, so the feed changes under a button that holds still rather than
        // one that pops in beside the outgoing frame. Inert like everything else here.
        RootCreateTaskButton(
            onClick = {},
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(
                    end = TdayDimens.ContentPaddingHorizontal,
                    bottom = TdayDimens.ContentPaddingHorizontal,
                ),
        )
    }
}

/** The sign-in / server-setup wizard, and the two holding screens that can stand in for it. */
@Composable
private fun OnboardingOverlay(
    appUiState: AppUiState,
    appViewModel: AppViewModel,
    authViewModel: AuthViewModel,
    authUiState: AuthUiState,
    onCredentialsAttempted: (String, String) -> Unit,
) {
    val context = LocalContext.current
    if (appUiState.pendingApproval) {
        com.ohmz.tday.compose.feature.app.PendingApprovalOverlay(
            username = appUiState.pendingApprovalUsername,
            isChecking = appUiState.isCheckingApproval,
            onCheckStatus = { appViewModel.checkPendingApproval() },
            onUseDifferentAccount = {
                authViewModel.clearStatus()
                appViewModel.cancelPendingApproval()
            },
        )
    } else when (val versionResult = appUiState.versionCheckResult) {
        is com.ohmz.tday.compose.core.data.server.VersionCheckResult.AppUpdateRequired,
        is com.ohmz.tday.compose.core.data.server.VersionCheckResult.ServerUpdateRequired -> {
            com.ohmz.tday.compose.feature.app.UpdateRequiredOverlay(
                versionCheckResult = versionResult,
                requiredUpdateRelease = appUiState.requiredUpdateRelease,
                isCheckingRelease = appUiState.isCheckingUpdateRelease,
                onRetry = { appViewModel.recheckVersion() },
            )
        }
        else -> {
            OnboardingWizardOverlay(
                initialServerUrl = appUiState.serverUrl,
                serverErrorMessage = appUiState.error,
                serverCanResetTrust = appUiState.canResetServerTrust,
                serverTrustFingerprint = appUiState.pendingServerTrustFingerprint,
                pendingApprovalMessage = appUiState.pendingApprovalMessage,
                authUiState = authUiState,
                onUseLocalMode = {
                    authViewModel.clearStatus()
                    appViewModel.clearPendingApprovalNotice()
                    appViewModel.useLocalMode()
                },
                onConnectServer = { rawUrl, onResult ->
                    appViewModel.saveServerUrl(
                        rawUrl = rawUrl,
                        onSuccess = { serverUrl ->
                            onResult(Result.success(serverUrl))
                        },
                        onFailure = { message ->
                            onResult(Result.failure(IllegalStateException(message)))
                        },
                    )
                },
                onResetServerTrust = { rawUrl, onResult ->
                    appViewModel.resetTrustedServer(
                        rawUrl = rawUrl,
                        onSuccess = { onResult(Result.success(Unit)) },
                        onFailure = { message ->
                            onResult(Result.failure(IllegalStateException(message)))
                        },
                    )
                },
                onConfirmServerTrust = { rawUrl, fingerprint, onResult ->
                    appViewModel.confirmServerTrust(
                        rawUrl = rawUrl,
                        fingerprint = fingerprint,
                        onSuccess = { serverUrl ->
                            onResult(Result.success(serverUrl))
                        },
                        onFailure = { message ->
                            onResult(Result.failure(IllegalStateException(message)))
                        },
                    )
                },
                onDismissServerTrust = appViewModel::dismissServerTrustPrompt,
                onLogin = { username, password, source ->
                    onCredentialsAttempted(username, password)
                    authViewModel.login(
                        username = username,
                        password = password,
                        credentialContext = context,
                        source = source,
                    ) {
                        appViewModel.refreshSession()
                    }
                },
                onRegister = { firstName, username, password, securityAnswers, onSuccess ->
                    onCredentialsAttempted(username, password)
                    authViewModel.register(
                        firstName = firstName,
                        lastName = "",
                        username = username,
                        password = password,
                        securityAnswers = securityAnswers,
                        credentialContext = context,
                    ) {
                        onSuccess()
                        appViewModel.refreshSession()
                    }
                },
                onFetchSecurityQuestions = authViewModel::fetchAllSecurityQuestions,
                onRequestSavedCredential = authViewModel::requestSavedCredential,
                onRequestSavedServerUrl = authViewModel::requestSavedServerUrl,
                onSaveServerUrlCredential = authViewModel::offerSaveOrUpdateServerUrl,
                onClearAuthStatus = {
                    authViewModel.clearStatus()
                    appViewModel.clearPendingApprovalNotice()
                },
            )
        }
    }
}

/**
 * The gates that can block an already-signed-in server session: a mandatory app/server update, and
 * the security questions an admin can require before the account is usable.
 */
@Composable
private fun AuthenticatedGates(
    appUiState: AppUiState,
    appViewModel: AppViewModel,
    authViewModel: AuthViewModel,
) {
    val authenticatedVersionCheck = appUiState.versionCheckResult
    if (appUiState.authenticated &&
        !appUiState.isLocalMode &&
        (authenticatedVersionCheck is com.ohmz.tday.compose.core.data.server.VersionCheckResult.AppUpdateRequired ||
            authenticatedVersionCheck is com.ohmz.tday.compose.core.data.server.VersionCheckResult.ServerUpdateRequired)
    ) {
        com.ohmz.tday.compose.feature.app.UpdateRequiredOverlay(
            versionCheckResult = authenticatedVersionCheck,
            requiredUpdateRelease = appUiState.requiredUpdateRelease,
            isCheckingRelease = appUiState.isCheckingUpdateRelease,
            onRetry = { appViewModel.recheckVersion() },
        )
    }

    if (appUiState.authenticated &&
        !appUiState.isLocalMode &&
        appUiState.user?.requireSecurityQuestions == true
    ) {
        SetSecurityQuestionsGate(
            onFetchQuestions = authViewModel::fetchAllSecurityQuestions,
            onSubmit = { answers, onSuccess, onError ->
                authViewModel.submitSecurityQuestions(
                    answers = answers,
                    onSuccess = {
                        onSuccess()
                        appViewModel.refreshSession()
                    },
                    onError = onError,
                )
            },
        )
    }
}

/**
 * Fires the create-task sheet that `tday://todos/create?target=floater` asked for, once the floater
 * feed it belongs to is actually on screen. The request outlives the hop through `home` (the deep
 * link re-points there and switches the dock tab) because it is held as state rather than acted on
 * where it arrived.
 */
@Composable
private fun HandlePendingFloaterCreateTask(
    isCreateTaskPending: Boolean,
    currentRoute: String?,
    rootFeedTab: RootFeedTab,
    isWorkspaceAvailable: Boolean,
    onCreateTask: () -> Unit,
) {
    LaunchedEffect(isCreateTaskPending, currentRoute, rootFeedTab, isWorkspaceAvailable) {
        if (
            isCreateTaskPending &&
            currentRoute == AppRoute.ScheduledTaskHome.route &&
            rootFeedTab == RootFeedTab.FLOATER_TASK_HOME &&
            isWorkspaceAvailable
        ) {
            onCreateTask()
        }
    }
}

/**
 * Whether losing the connection is worth announcing. Only a signed-in server workspace has a
 * server to lose: Local Mode is offline by design, and a signed-out app has nothing to sync.
 */
private fun AppUiState.showsOfflineNotice(): Boolean =
    isOffline && authenticated && !isLocalMode

@Composable
private fun CollectAppSnackbars(
    appViewModel: AppViewModel,
    onShowToast: (TdayToastData) -> Unit,
) {
    // The application context, not this composition's: the window is asked for once per
    // toast from inside a collect that outlives any single Activity, and an accessibility
    // setting is a user-level answer that no Activity has a different one of.
    val context = LocalContext.current.applicationContext
    LaunchedEffect(context) {
        appViewModel.snackbarManager.events.collect { event ->
            onShowToast(
                TdayToastData(
                    id = System.currentTimeMillis(),
                    message = event.message,
                    kind = when (event.kind) {
                        SnackbarKind.ERROR -> TdayToastKind.ERROR
                        SnackbarKind.SUCCESS -> TdayToastKind.SUCCESS
                        SnackbarKind.INFO -> TdayToastKind.INFO
                    },
                    // Read per toast rather than once at the top of the collect: a user
                    // can change "Time to take action" while the app is open, and the
                    // next toast is the first place they would look for it to have
                    // taken. The two branches are two different questions — see
                    // AccessibilityTimeout.kt — and only the one with a button reaches
                    // the interactive half of the setting.
                    autoDismissMillis = if (event.actionLabel != null) {
                        actionToastTimeoutMillis(context)
                    } else {
                        informationalToastTimeoutMillis(context)
                    },
                    actionLabel = event.actionLabel,
                    onAction = event.onAction,
                    actionIconRes = event.actionIconRes,
                ),
            )
        }
    }
}

/**
 * Surfaces connectivity changes as transient toasts (replacing the persistent
 * offline banner): an ERROR toast when the app drops offline and a "Back online"
 * toast when the same gated signal flips back. Drives off the same
 * `isOffline` condition the banner used so behaviour stays in sync.
 *
 * The offline toast fires on an `isOffline` transition OR when [manualNoticePulse]
 * increments — a manual sync (Settings "Sync now" / pull-to-refresh) force-shows the
 * toast every time it applies, even while already offline. The back-online toast fires
 * only on a genuine offline→online transition, never on a pulse. The offline message
 * branches on [offlineReason]: a backend/DB 5xx (SERVER_UNAVAILABLE) shows the distinct
 * "server error" toast; anything else keeps the generic "you're offline" toast.
 */
@Composable
private fun CollectConnectivityToasts(
    appViewModel: AppViewModel,
    isOffline: Boolean,
    offlineReason: ConnectionFailureKind,
    pendingMutationCount: Int,
    manualNoticePulse: Int,
) {
    val context = LocalContext.current
    // Tracks the last announced offline state and pulse so we emit exactly one toast per
    // transition or manual pulse (and skip the initial composition).
    var wasOffline by remember { mutableStateOf<Boolean?>(null) }
    var lastPulse by remember { mutableStateOf(manualNoticePulse) }

    LaunchedEffect(isOffline, manualNoticePulse) {
        val previous = wasOffline
        val transitioned = previous != isOffline
        val pulseIncremented = manualNoticePulse != lastPulse
        wasOffline = isOffline
        lastPulse = manualNoticePulse

        // Skip the very first observation so a cold start that is already online
        // does not flash a spurious "Back online" toast.
        if (previous == null) return@LaunchedEffect
        if (!transitioned && !pulseIncremented) return@LaunchedEffect

        if (!isOffline) {
            // Back-online only announces on a real offline→online flip, not a manual pulse.
            if (!transitioned) return@LaunchedEffect
            appViewModel.snackbarManager.show(
                SnackbarEvent(
                    message = context.getString(R.string.online_toast),
                    kind = SnackbarKind.INFO,
                ),
            )
            return@LaunchedEffect
        }

        val message = if (offlineReason == ConnectionFailureKind.SERVER_UNAVAILABLE) {
            context.getString(R.string.backend_down_toast)
        } else {
            when {
                pendingMutationCount == 1 ->
                    context.getString(R.string.offline_toast_pending_one)

                pendingMutationCount > 1 ->
                    context.getString(R.string.offline_toast_pending_many, pendingMutationCount)

                else -> context.getString(R.string.offline_toast)
            }
        }
        appViewModel.snackbarManager.show(
            SnackbarEvent(
                // Offline / server-down is an "issue" → red shade.
                message = message,
                kind = SnackbarKind.ERROR,
            ),
        )
    }
}

/**
 * Whether the branded splash still stands in for the whole nav graph.
 *
 * True until the persisted session has resolved, and for as long after that as a finger is down on
 * the splash — the tap-and-hold pause it has always offered. Both are reasons NOT to build the
 * NavHost, which is why anything reading the graph has to tolerate its absence: this can outlast
 * the session resolving, and does whenever the user is pressing the splash at that moment.
 */
private fun shouldHoldSessionSplash(
    rootDestination: RootDestination,
    isSessionSplashHeld: Boolean,
): Boolean = rootDestination == RootDestination.SPLASH || isSessionSplashHeld

/**
 * Navigates the deep link MainActivity is holding — the Floater widget's `tday://floater`, a
 * reminder's `tday://todos/...`, `tday://home` from the update-ready notification — once there is
 * somewhere to navigate it to. Both waits matter, and neither drops the link: it stays pending in
 * [MainActivity.deepLinkIntent] until it can be handled, and every key here re-runs the effect.
 */
@Composable
private fun HandlePendingDeepLink(
    isWorkspaceAvailable: Boolean,
    currentRoute: String?,
    navController: NavHostController,
) {
    val activity = LocalContext.current as? MainActivity
    val deepLinkIntent by activity?.deepLinkIntent?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }

    LaunchedEffect(deepLinkIntent, isWorkspaceAvailable, currentRoute) {
        val intent = deepLinkIntent ?: return@LaunchedEffect
        // Defer deep links until the session is restored and the workspace is available.
        // Handling one during cold-start bootstrap navigated the target route UNDER the login
        // overlay and mounted its screen before auth was ready — which flashed the login screen
        // and fired a generic "something went wrong" error toast before settling. Consume it
        // after so it fires once.
        if (!isWorkspaceAvailable) return@LaunchedEffect
        // No current entry means no graph yet — same guard, same reason, as
        // HandleStartupNavigation's. Normally the NavHost composes and sets the graph in the very
        // pass that flips isWorkspaceAvailable, so this effect always ran after it. It no longer
        // has to: holding the pre-graph splash keeps the NavHost unbuilt past that flip, and a
        // press there is a supported gesture, so the graph can still be unset when this runs.
        // handleDeepLink(Intent) reads it unconditionally — navigation-runtime 2.8.5 goes
        // getTopGraph(backQueue) -> backQueue.lastOrNull() is null -> _graph!! — and does so for
        // the plain launcher Intent MainActivity dispatches on every cold launch just as much as
        // for a tday:// one, so calling it here would NPE.
        if (currentRoute == null) return@LaunchedEffect
        navController.handleDeepLink(intent.withoutTaskRestartFlags())
        activity?.consumeDeepLink()
    }
}

@Composable
private fun HandleStartupNavigation(
    appUiState: AppUiState,
    currentRoute: String?,
    navController: NavHostController,
    isStartupSplashHeld: Boolean,
) {
    LaunchedEffect(
        appUiState.loading,
        appUiState.rootDestination,
        currentRoute,
        isStartupSplashHeld,
    ) {
        if (appUiState.loading) return@LaunchedEffect
        if (isStartupSplashHeld) return@LaunchedEffect
        // No current entry means no graph yet: nothing to route from. Navigating here would
        // push `home` on top of the start destination instead of replacing it.
        if (currentRoute == null) return@LaunchedEffect

        when (appUiState.rootDestination) {
            // The graph is not built while the session is unresolved (TdayApp holds the splash
            // in its place), so there is no route to steer yet.
            RootDestination.SPLASH -> Unit

            RootDestination.WORKSPACE -> {
                val unauthenticatedRoutes = setOf(
                    AppRoute.Splash.route,
                    AppRoute.Login.route,
                    AppRoute.ServerSetup.route,
                )
                if (currentRoute in unauthenticatedRoutes) {
                    navigateScheduledTaskHome(navController, currentRoute)
                }
            }

            RootDestination.ONBOARDING -> {
                // The reset-password screen is reachable while logged out — don't bounce it
                // back to the login/scheduled-task-home overlay.
                if (currentRoute != AppRoute.ScheduledTaskHome.route &&
                    currentRoute != AppRoute.ForgotPassword.route
                ) {
                    navigateScheduledTaskHome(navController, currentRoute)
                }
            }
        }
    }
}

private fun navigateScheduledTaskHome(
    navController: NavHostController,
    currentRoute: String?,
) {
    navController.navigate(AppRoute.ScheduledTaskHome.route) {
        when (currentRoute) {
            AppRoute.Splash.route -> popUpTo(AppRoute.Splash.route) { inclusive = true }
            AppRoute.Login.route -> popUpTo(AppRoute.Login.route) { inclusive = true }
            AppRoute.ScheduledTaskHome.route -> popUpTo(AppRoute.ScheduledTaskHome.route) { inclusive = true }
            AppRoute.ServerSetup.route -> popUpTo(AppRoute.ServerSetup.route) { inclusive = true }
        }
        launchSingleTop = true
    }
}

@Composable
private fun HandleLaunchUpdateToast(
    appUiState: AppUiState,
    releaseUiState: LatestReleaseUiState,
    currentRoute: String?,
    updateToastMessage: String?,
    activeToast: TdayToastData?,
    hasShownLaunchUpdateToast: Boolean,
    onToastShown: () -> Unit,
    onShowToast: (TdayToastData) -> Unit,
    onClearToast: () -> Unit,
    onOpenLatestRelease: () -> Unit,
) {
    // Resolve in composable scope; vectorResource can't be called inside LaunchedEffect.
    val updateToastIcon = ImageVector.vectorResource(R.drawable.ic_lucide_sparkles)
    LaunchedEffect(
        appUiState.loading,
        releaseUiState.isLoading,
        releaseUiState.hasUpdate,
        updateToastMessage,
        currentRoute,
    ) {
        if (appUiState.loading || releaseUiState.isLoading) return@LaunchedEffect
        if (!releaseUiState.hasUpdate) return@LaunchedEffect
        if (hasShownLaunchUpdateToast) return@LaunchedEffect
        if (currentRoute == null || currentRoute == AppRoute.Splash.route) return@LaunchedEffect
        if (currentRoute == AppRoute.LatestRelease.route) return@LaunchedEffect
        val message = updateToastMessage ?: return@LaunchedEffect

        onToastShown()
        onShowToast(
            TdayToastData(
                id = System.currentTimeMillis(),
                message = message,
                icon = updateToastIcon,
                onTap = {
                    onClearToast()
                    onOpenLatestRelease()
                },
            ),
        )
    }

    LaunchedEffect(currentRoute, activeToast?.id) {
        if (currentRoute == AppRoute.LatestRelease.route && activeToast != null) {
            onClearToast()
        }
    }
}

@Composable
private fun TodosRoute(
    mode: TodoListMode,
    onBack: () -> Unit,
    onListDeleted: () -> Unit = {},
    onOpenFloaterList: (String, String, Color) -> Unit = { _, _, _ -> },
    onOpenCompleted: (Color) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenMorningSweep: () -> Unit = {},
    highlightTodoId: String? = null,
    listId: String? = null,
    listName: String? = null,
    rootFeedTab: RootFeedTab? = null,
    onRootFeedTabSelected: ((RootFeedTab) -> Unit)? = null,
    showRootFeedDock: Boolean = true,
    showCreateTaskButton: Boolean = true,
    /** See `TodoListScreen`'s parameter of the same name. */
    hostSwipeSlot: TaskSwipeSlot? = null,
    openCreateTaskOnStart: Boolean = false,
    exitToLauncherOnBack: Boolean = false,
    exitOnCreateTaskSheetDismiss: Boolean = false,
    onCreateTaskFlowFinished: () -> Unit = {},
    usesRootFeedHeader: Boolean = false,
    createTaskRequestKey: Int = 0,
    onCreateTaskRequestHandled: (Int) -> Unit = {},
    scrollToTopRequestKey: Int = 0,
    onRootDockCollapsedChange: (Boolean) -> Unit = {},
    onRootControlsVisibleChange: (Boolean) -> Unit = {},
    pullRefreshEnabled: Boolean = true,
    summaryAvailable: Boolean = true,
) {
    val viewModel: TodoListViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Pull-to-refresh belongs to the two root feeds and nowhere else. Every
    // other screen this composable draws — the five timeline scopes, a custom
    // list, a floater list — is opened FROM a root feed and shows a slice of
    // what that feed already fetched, so a second gesture to refetch it offers
    // a wave animation and no new data. `usesRootFeedHeader` is precisely the
    // "this is a root feed" flag, so the two cannot drift apart.
    val rootPullRefreshEnabled = pullRefreshEnabled && usesRootFeedHeader

    LaunchedEffect(mode, listId, listName) {
        viewModel.load(mode = mode, listId = listId, listName = listName)
    }
    OnRouteResume {
        viewModel.load(mode = mode, listId = listId, listName = listName)
    }

    TodoListScreen(
        uiState = uiState,
        onBack = onBack,
        onRefresh = { viewModel.refresh(userInitiated = true) },
        highlightedTodoId = highlightTodoId,
        onSummarize = viewModel::summarizeCurrentMode,
        onDismissSummaryConnectivityError = viewModel::dismissSummaryConnectivityError,
        onAddTask = viewModel::addTask,
        onParseTaskTitleNlp = viewModel::parseTaskTitleNlp,
        onUpdateTask = viewModel::updateTask,
        onMoveTask = viewModel::moveTask,
        onMoveTaskToTimeOfDay = viewModel::moveTaskToTimeOfDay,
        onComplete = viewModel::toggleComplete,
        onDelete = viewModel::delete,
        onPromoteFloater = viewModel::promoteFloater,
        onDemoteTodo = viewModel::demoteTodo,
        onDeferTask = viewModel::deferTask,
        onBulkComplete = viewModel::completeSelected,
        onBulkDelete = viewModel::deleteSelected,
        onBulkSetPriority = viewModel::setPriorityForSelected,
        onBulkMoveToList = viewModel::moveSelectedToList,
        onOpenMorningSweep = onOpenMorningSweep,
        onUpdateListSettings = { targetListId, name, color, iconKey, reusable, defaultPriority ->
            viewModel.updateListSettings(
                listId = targetListId,
                name = name,
                color = color,
                iconKey = iconKey,
                reusable = reusable,
                defaultPriority = defaultPriority,
                defaultPriorityChanged = true,
            )
        },
        onDeleteList = { targetListId ->
            viewModel.deleteList(
                listId = targetListId,
                onDeleted = onListDeleted,
            )
        },
        onOpenFloaterList = onOpenFloaterList,
        onOpenCompleted = onOpenCompleted,
        onOpenSettings = onOpenSettings,
        onCreateList = viewModel::createList,
        onResetFloaterList = viewModel::resetFloaterList,
        rootFeedTab = rootFeedTab,
        onRootFeedTabSelected = onRootFeedTabSelected,
        showRootFeedDock = showRootFeedDock,
        showCreateTaskButton = showCreateTaskButton,
        hostSwipeSlot = hostSwipeSlot,
        openCreateTaskOnStart = openCreateTaskOnStart,
        exitToLauncherOnBack = exitToLauncherOnBack,
        exitOnCreateTaskSheetDismiss = exitOnCreateTaskSheetDismiss,
        onCreateTaskFlowFinished = onCreateTaskFlowFinished,
        pullRefreshEnabled = rootPullRefreshEnabled,
        summaryAvailable = summaryAvailable,
        usesRootFeedHeader = usesRootFeedHeader,
        createTaskRequestKey = createTaskRequestKey,
        onCreateTaskRequestHandled = onCreateTaskRequestHandled,
        scrollToTopRequestKey = scrollToTopRequestKey,
        onRootDockCollapsedChange = onRootDockCollapsedChange,
        onRootControlsVisibleChange = onRootControlsVisibleChange,
    )
}

@Composable
private fun OnRouteResume(
    action: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentAction by rememberUpdatedState(action)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentAction()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
private fun OnAppForegroundResume(
    action: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentAction by rememberUpdatedState(action)
    DisposableEffect(lifecycleOwner) {
        var hasPaused = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    hasPaused = true
                }

                Lifecycle.Event.ON_RESUME -> {
                    if (hasPaused) {
                        hasPaused = false
                        currentAction()
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

/**
 * The route hand-over: one shape, two curves, and the same pair everywhere the change is
 * already committed. The fourth wiring left this pair for a gesture; see
 * [navigationPopExitTransition].
 *
 * Two rungs, not two hand-overs: `Enter` while motion plays, and `Quick` where it is
 * refused, because the length is the thing Reduce Motion is asking to be spared and the
 * curves are what is left saying which way the change is going.
 *
 * The 360 this replaced carried half an argument and the half survives. A route fade sits
 * between a tap and the screen the user asked for, and anything longer than that reads as
 * lag — which against the long end is exactly right, and 360 was the long end it was
 * written against. What 360 could not defend was 360: it named no rung, so nothing
 * downstream could tell a decision from a number somebody liked. A thing arriving with no
 * reason to be another length is `Enter`.
 *
 * One length and two curves is the model web already runs: `.tday-route-fade` and
 * `::view-transition-old(root)` are both `var(--tday-duration-enter)` and differ only in
 * `--tday-ease-enter` against `--tday-ease-exit`. The two Compose built-ins are those two
 * curves byte for byte — `docs/motion.md`'s easing table says so and `TdayMotionTokensTest`
 * pins it — so they are left where they are written rather than renamed for the look of it.
 *
 * There is nothing below these three: the splash, the auth screens and Settings each used to
 * restate a transition here, and every one of them was restating this one. A route that
 * names its own hand-over is a route that drifts off the rung, and — the reason it matters
 * more than tidiness — a route that escapes a gate wired at the NavHost.
 *
 * [motionEnabled] is a parameter and not a `remember` because the callers are
 * `AnimatedContentTransitionScope` lambdas, which are not composable.
 *
 * WHAT REDUCE MOTION GETS, AND WHY IT IS NOT `None`. A DELIBERATE DEPARTURE, NAMED AS ONE.
 *
 * These three used to answer `None` when motion was refused. `None` is not "no transition":
 * it is the destination drawn finished on its first frame, which is the whole of what a
 * route change has to SAY, and `docs/motion.md`'s fifth idiom rule — "removes the trip,
 * never the destination" — is satisfied by it: the rule forbids a scene held at the START
 * of its fade, and a finished frame is not that. So what follows is not an application of
 * the rule, and this comment is not here to claim it is.
 *
 * It is a departure from two places that encode the opposite, and either would have to move
 * with it. `tday-web/tests/guardrails/route-handover.test.ts` asserts `EnterTransition.None`
 * and `ExitTransition.None` in these three declarations, and requires every rung named in
 * `navigationPopExitTransition` to be `Durations.Enter` — its Android block is the only
 * thing in the tree pinning this decision, and it cannot pass while this stands. Web's
 * answer at a route change is the other: `tday-web/src/globals.css` turns `.tday-route-fade`
 * into `animation: none` under `prefers-reduced-motion: reduce`, commented "The destination
 * is the whole of the finished state at a route change". This makes Android the only client
 * that fades.
 *
 * What it buys, stated as the thing it is: a route change is a hand-over, and with the tile
 * zoom gated off by the same switch (`TdayTileTransition.kt`) there is nothing left in it
 * but the hand-over itself — so the destination is still handed over rather than appearing,
 * on the shortest rung the vocabulary has. The trip Reduce Motion is spared is the LENGTH,
 * the 200 ms in front of a screen the user has already chosen, not the hand-over.
 *
 * If the trade is not wanted, this paragraph and the three `Quick` branches below are the
 * whole of it: put `None` back and the guardrail above stops failing.
 */
private fun navigationEnterTransition(motionEnabled: Boolean): EnterTransition =
    if (!motionEnabled) {
        fadeIn(
            animationSpec = tween(
                durationMillis = TdayMotionTokens.Durations.Quick,
                easing = LinearOutSlowInEasing,
            ),
        )
    } else {
        fadeIn(
            animationSpec = tween(
                durationMillis = TdayMotionTokens.Durations.Enter,
                easing = LinearOutSlowInEasing,
            ),
        )
    }

/** The other curve of the pair; see [navigationEnterTransition] for the length they share. */
private fun navigationExitTransition(motionEnabled: Boolean): ExitTransition =
    if (!motionEnabled) {
        fadeOut(
            animationSpec = tween(
                durationMillis = TdayMotionTokens.Durations.Quick,
                easing = FastOutLinearInEasing,
            ),
        )
    } else {
        fadeOut(
            animationSpec = tween(
                durationMillis = TdayMotionTokens.Durations.Enter,
                easing = FastOutLinearInEasing,
            ),
        )
    }

/**
 * The one direction left, and the only transition of the four a finger can hold open.
 *
 * [navigationEnterTransition]'s argument is about a route change that has already been
 * COMMITTED: both screens are going to swap whatever happens, so there is nothing for a
 * direction to tell anybody and a fade in place is the honest description. A predictive-back
 * scrub is the case that argument does not reach. `enableOnBackInvokedCallback` is on in the
 * manifest and `navigation-compose` drives this slot from a `SeekableTransitionState`, so
 * mid-drag the user is not watching a transition play — they are watching this spec's own
 * progress, scrubbed to wherever their thumb is. A crossfade is the one spec that cannot be
 * scrubbed: two screens at half opacity look identical at a third of the pull and at two
 * thirds of it, so the gesture can say neither how far it has come nor whether letting go
 * now commits it or throws it back.
 *
 * Hence travel and a recede, on the screen the finger has hold of and on nothing else. The
 * arriving screen keeps [navigationEnterTransition] and still fades where it stands, which is
 * exactly what leaves the NavHost's toolbar argument intact: the back chevron and the action
 * cluster are still handed to their counterparts rather than carried sideways and dropped
 * back. Push is not touched at all — the forward direction has no gesture to answer.
 *
 * Same rung and same curve as the committed exit, deliberately: this slot also plays whole
 * when back arrives as a button press rather than as a drag, and a scrub that released into a
 * different animation than the button would have played is two backs instead of one. The
 * quarter width is a travel and not a distance anybody measures, so it is written here rather
 * than named; [PREDICTIVE_BACK_MIN_SCALE] is the number that needed an argument and carries
 * one.
 *
 * With motion refused the travel and the recede go — they are the gesture's travelling
 * half, and a refused drag has no travel to report — and the screen being taken away
 * still fades on [navigationEnterTransition]'s short rung, so back remains a hand-over
 * rather than a cut.
 */
private fun navigationPopExitTransition(motionEnabled: Boolean): ExitTransition =
    if (!motionEnabled) {
        fadeOut(
            animationSpec = tween(
                durationMillis = TdayMotionTokens.Durations.Quick,
                easing = FastOutLinearInEasing,
            ),
        )
    } else {
        // Two tweens for one spec, because Compose types an animation by what it animates and
        // the slide moves an `IntOffset` while the other two move a `Float`. The rung and the
        // curve are the part that has to stay identical; the objects cannot be.
        val fade = tween<Float>(
            durationMillis = TdayMotionTokens.Durations.Enter,
            easing = FastOutLinearInEasing,
        )
        val travel = tween<IntOffset>(
            durationMillis = TdayMotionTokens.Durations.Enter,
            easing = FastOutLinearInEasing,
        )
        fadeOut(animationSpec = fade) +
            slideOutHorizontally(animationSpec = travel) { it / 4 } +
            scaleOut(animationSpec = fade, targetScale = PREDICTIVE_BACK_MIN_SCALE)
    }

@Composable
private fun SplashScreen(
    onHoldChanged: (Boolean) -> Unit,
    tagline: String? = null,
) {
    val splashTaglineOptions = stringArrayResource(R.array.splash_taglines)
    val resolvedTagline = tagline ?: remember(splashTaglineOptions.contentHashCode()) {
        splashTaglineOptions.random()
    }

    DisposableEffect(onHoldChanged) {
        onDispose { onHoldChanged(false) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(onHoldChanged) {
                detectTapGestures(
                    onPress = {
                        onHoldChanged(true)
                        try {
                            awaitRelease()
                        } finally {
                            onHoldChanged(false)
                        }
                    },
                )
            }
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.splash_icon),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(160.dp),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = resolvedTagline,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun unauthenticatedScheduledTaskHomeUiState(lockedListName: String): ScheduledTaskHomeUiState {
    return ScheduledTaskHomeUiState(
        isLoading = false,
        summary = DashboardSummary(
            todayCount = 0,
            scheduledCount = 0,
            allCount = 0,
            priorityCount = 0,
            floaterCount = 0,
            completedCount = 0,
            lists = listOf(
                ListSummary(
                    id = "locked",
                    name = lockedListName,
                    color = null,
                    iconKey = null,
                    todoCount = 0,
                ),
            ),
        ),
        errorMessage = null,
    )
}

/**
 * A copy of a deep-link intent with the task-restart flags cleared.
 *
 * `NavController.handleDeepLink` does not navigate in place when the intent carries
 * `FLAG_ACTIVITY_NEW_TASK`: it deliberately rebuilds the whole task through a `TaskStackBuilder`
 * and calls `finish()` on the current activity, because it cannot know what state a
 * externally-started task is in. Every widget and notification PendingIntent has to set
 * `NEW_TASK` to launch from outside the app, so tapping a widget landed us in that path — the
 * launcher started MainActivity, and ~0.4s later Navigation finished it and started a second one
 * (visible in logcat as two `START ... dat=tday://floater` lines, the second with
 * `flg=0x1400c000`, plus `Duplicate finish request`).
 *
 * That teardown is what the user sees: the first instance dies mid-bootstrap, its in-flight
 * `/api/auth/session` and `/api/mobile/probe` calls abort with "stream was reset: CANCEL", the app
 * falls back to the login screen and fires the generic "something went wrong" toast, and only then
 * does the replacement instance finish bootstrapping and navigate to the deep-linked screen.
 *
 * Stripping the flags here (and only here — the PendingIntent still needs them) keeps the deep
 * link a normal in-place navigation on the activity that is already running.
 */
internal fun Intent.withoutTaskRestartFlags(): Intent {
    val taskRestartFlags = Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_ACTIVITY_CLEAR_TASK or
        Intent.FLAG_ACTIVITY_TASK_ON_HOME
    if (flags and taskRestartFlags == 0) return this
    return Intent(this).apply { flags = this@withoutTaskRestartFlags.flags and taskRestartFlags.inv() }
}
