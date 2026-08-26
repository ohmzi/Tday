package com.ohmz.tday.compose

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.ohmz.tday.compose.core.ui.LocalSnackbarManager
import com.ohmz.tday.compose.core.ui.SnackbarEvent
import com.ohmz.tday.compose.core.ui.SnackbarKind
import com.ohmz.tday.compose.core.ui.TdayToastData
import com.ohmz.tday.compose.core.ui.TdayToastHost
import com.ohmz.tday.compose.core.ui.TdayToastKind
import com.ohmz.tday.compose.feature.app.AppUiState
import com.ohmz.tday.compose.feature.app.AppViewModel
import com.ohmz.tday.compose.feature.app.ProfileEditResult
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
import kotlin.math.roundToInt

private const val NAV_ENTER_DURATION_MS = 440
private const val NAV_EXIT_DURATION_MS = 320
private const val NAV_FADE_IN_DURATION_MS = 360
private const val NAV_FADE_OUT_DURATION_MS = 240
private const val NAV_SLIDE_FRACTION = 0.18f
private const val PENDING_SEARCH_HIGHLIGHT_TODO_ID = "pendingSearchHighlightTodoId"
private const val SETTINGS_ENTER_DURATION_MS = 380
private const val SETTINGS_EXIT_DURATION_MS = 260
private const val SETTINGS_VERTICAL_FRACTION = 0.22f

@Composable
fun TdayApp(
    onFirstFrameDrawn: () -> Unit = {},
) {
    val splashTaglineOptions = stringArrayResource(R.array.splash_taglines)
    val startupTagline = rememberSaveable(splashTaglineOptions.contentHashCode()) {
        splashTaglineOptions.random()
    }
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
    var rootFeedTab by rememberSaveable { mutableStateOf(RootFeedTab.SCHEDULED_TASK_HOME) }
    var rootCreateTaskRequestSerial by rememberSaveable { mutableStateOf(0) }
    var rootCreateTaskRequestKey by rememberSaveable { mutableStateOf(0) }
    var pendingFloaterTaskHomeCreateTask by rememberSaveable { mutableStateOf(false) }
    var scheduledTaskHomeScrollToTopRequestKey by remember { mutableStateOf(0) }
    var floaterTaskHomeScrollToTopRequestKey by remember { mutableStateOf(0) }
    var rootDockCollapsed by rememberSaveable { mutableStateOf(false) }
    var rootControlsVisible by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    fun requestRootCreateTask() {
        rootCreateTaskRequestSerial += 1
        rootCreateTaskRequestKey = rootCreateTaskRequestSerial
    }

    fun consumeRootCreateTaskRequest(requestKey: Int) {
        if (rootCreateTaskRequestKey == requestKey) {
            rootCreateTaskRequestKey = 0
        }
    }

    val activity = LocalContext.current as? MainActivity
    val deepLinkIntent by activity?.deepLinkIntent?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }

    LaunchedEffect(deepLinkIntent, appUiState.isWorkspaceAvailable) {
        val intent = deepLinkIntent ?: return@LaunchedEffect
        // Defer deep links (e.g. the Floater widget's tday://floater) until the session is
        // restored and the workspace is available. Handling one during cold-start bootstrap
        // navigated the target route UNDER the login overlay and mounted its screen before
        // auth was ready — which flashed the login screen and fired a generic
        // "something went wrong" error toast before settling. Consume it after so it fires once.
        if (!appUiState.isWorkspaceAvailable) return@LaunchedEffect
        navController.handleDeepLink(intent.withoutTaskRestartFlags())
        activity?.consumeDeepLink()
    }
    LaunchedEffect(
        pendingFloaterTaskHomeCreateTask,
        currentRoute,
        rootFeedTab,
        appUiState.isWorkspaceAvailable,
    ) {
        if (
            pendingFloaterTaskHomeCreateTask &&
            currentRoute == AppRoute.ScheduledTaskHome.route &&
            rootFeedTab == RootFeedTab.FLOATER_TASK_HOME &&
            appUiState.isWorkspaceAvailable
        ) {
            pendingFloaterTaskHomeCreateTask = false
            requestRootCreateTask()
        }
    }

    CollectAppSnackbars(
        appViewModel = appViewModel,
        onShowToast = { activeToast = it },
    )
    CollectConnectivityToasts(
        appViewModel = appViewModel,
        isOffline = appUiState.isOffline &&
                appUiState.authenticated &&
                !appUiState.isLocalMode,
        offlineReason = appUiState.offlineReason,
        pendingMutationCount = appUiState.pendingMutationCount,
        manualNoticePulse = appUiState.manualNoticePulse,
    )
    OnAppForegroundResume {
        appViewModel.reconnectAfterForeground()
    }

    fun handleRootFeedTabSelection(tab: RootFeedTab) {
        if (rootFeedTab == tab) {
            when (tab) {
                RootFeedTab.SCHEDULED_TASK_HOME -> scheduledTaskHomeScrollToTopRequestKey += 1
                RootFeedTab.FLOATER_TASK_HOME -> floaterTaskHomeScrollToTopRequestKey += 1
            }
        } else {
            rootFeedTab = tab
        }
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
                NavHost(
                    navController = navController,
                    startDestination = AppRoute.Splash.route,
                    modifier = Modifier.haze(hazeState),
                    enterTransition = {
                        fadeIn(
                            animationSpec = tween(
                                durationMillis = NAV_FADE_IN_DURATION_MS,
                                easing = LinearOutSlowInEasing,
                            ),
                        ) + slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(
                                durationMillis = NAV_ENTER_DURATION_MS,
                                easing = LinearOutSlowInEasing,
                            ),
                            initialOffset = ::navigationSlideOffset,
                        )
                    },
                    exitTransition = {
                        fadeOut(
                            animationSpec = tween(
                                durationMillis = NAV_FADE_OUT_DURATION_MS,
                                easing = FastOutLinearInEasing,
                            ),
                        ) + slideOutHorizontally(
                            targetOffsetX = { fullWidth -> -navigationSlideOffset(fullWidth) },
                            animationSpec = tween(
                                durationMillis = NAV_EXIT_DURATION_MS,
                                easing = FastOutLinearInEasing,
                            ),
                        )
                    },
                    popEnterTransition = {
                        fadeIn(
                            animationSpec = tween(
                                durationMillis = NAV_FADE_IN_DURATION_MS,
                                easing = LinearOutSlowInEasing,
                            ),
                        ) + slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(
                                durationMillis = NAV_ENTER_DURATION_MS,
                                easing = LinearOutSlowInEasing,
                            ),
                            initialOffset = ::navigationSlideOffset,
                        )
                    },
                    popExitTransition = {
                        fadeOut(
                            animationSpec = tween(
                                durationMillis = NAV_FADE_OUT_DURATION_MS,
                                easing = FastOutLinearInEasing,
                            ),
                        ) + slideOutHorizontally(
                            targetOffsetX = ::navigationSlideOffset,
                            animationSpec = tween(
                                durationMillis = NAV_EXIT_DURATION_MS,
                                easing = FastOutLinearInEasing,
                            ),
                        )
                    },
                ) {
                    composable(
                        route = AppRoute.Splash.route,
                        enterTransition = { fadeIn(tween(300)) },
                        exitTransition = { fadeOut(tween(300)) },
                    ) {
                        SplashScreen(onHoldChanged = { isStartupSplashHeld = it })
                    }

                    composable(
                        route = AppRoute.ServerSetup.route,
                        enterTransition = { fadeIn(tween(300)) },
                        exitTransition = { fadeOut(tween(300)) },
                    ) {
                        SplashScreen(onHoldChanged = { isStartupSplashHeld = it })
                    }

                    composable(
                        route = AppRoute.Login.route,
                        enterTransition = { fadeIn(tween(300)) },
                        exitTransition = { fadeOut(tween(300)) },
                    ) {
                        SplashScreen(onHoldChanged = { isStartupSplashHeld = it })
                    }

                    composable(
                        route = AppRoute.ForgotPassword.route,
                        enterTransition = { settingsEnterTransition() },
                        exitTransition = { settingsExitTransition() },
                        popEnterTransition = { settingsEnterTransition() },
                        popExitTransition = { settingsExitTransition() },
                    ) {
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

                    composable(
                        route = AppRoute.ScheduledTaskHome.route,
                        deepLinks = listOf(navDeepLink { uriPattern = "tday://home" }),
                    ) {
                        val authViewModel: AuthViewModel = hiltViewModel()
                        val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
                        val showOnboardingWizard = !appUiState.isWorkspaceAvailable

                        // Remember the last attempted credentials so a pending-approval result
                        // can be persisted into the holding screen (which re-attempts login).
                        var lastAuthUsername by remember { mutableStateOf("") }
                        var lastAuthPassword by remember { mutableStateOf("") }
                        LaunchedEffect(authUiState.pendingApproval) {
                            if (authUiState.pendingApproval && lastAuthPassword.isNotBlank()) {
                                appViewModel.enterPendingApproval(lastAuthUsername, lastAuthPassword)
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (showOnboardingWizard) {
                                            Modifier.blur(14.dp)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            ) {
                                if (appUiState.isWorkspaceAvailable) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        when (rootFeedTab) {
                                            RootFeedTab.SCHEDULED_TASK_HOME -> {
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
                                                    onOpenToday = { navController.navigate(AppRoute.TodayTodos.route) },
                                                    onOpenOverdue = { navController.navigate(AppRoute.OverdueTodos.route) },
                                                    onOpenScheduled = { navController.navigate(AppRoute.ScheduledTodos.route) },
                                                    onOpenAll = { navController.navigate(AppRoute.AllTodos.create()) },
                                                    onOpenPriority = { navController.navigate(AppRoute.PriorityTodos.route) },
                                                    onOpenCompleted = { navController.navigate(AppRoute.Completed.route) },
                                                    onOpenCalendar = { navController.navigate(AppRoute.Calendar.route) },
                                                    onOpenFloater = {
                                                        rootFeedTab = RootFeedTab.FLOATER_TASK_HOME
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
                                                    onOpenList = { id, name ->
                                                        navController.navigate(
                                                            AppRoute.ListTodos.create(
                                                                id,
                                                                name
                                                            )
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
                                                    showCreateTaskButton = false,
                                                    createTaskRequestKey = rootCreateTaskRequestKey,
                                                    onCreateTaskRequestHandled = ::consumeRootCreateTaskRequest,
                                                    scrollToTopRequestKey = scheduledTaskHomeScrollToTopRequestKey,
                                                    onRootDockCollapsedChange = {
                                                        rootDockCollapsed = it
                                                    },
                                                    onRootControlsVisibleChange = {
                                                        rootControlsVisible = it
                                                    },
                                                )
                                            }

                                            RootFeedTab.FLOATER_TASK_HOME -> {
                                                TodosRoute(
                                                    mode = TodoListMode.FLOATER,
                                                    onBack = { rootFeedTab = RootFeedTab.SCHEDULED_TASK_HOME },
                                                    pullRefreshEnabled = !appUiState.isLocalMode,
                                                    summaryAvailable = !appUiState.isLocalMode,
                                                    onOpenFloaterList = { id, name ->
                                                        navController.navigate(
                                                            AppRoute.FloaterListTodos.create(
                                                                id,
                                                                name
                                                            )
                                                        )
                                                    },
                                                    onOpenSettings = {
                                                        navController.navigate(AppRoute.Settings.route)
                                                    },
                                                    showRootFeedDock = false,
                                                    showCreateTaskButton = false,
                                                    usesRootFeedHeader = true,
                                                    createTaskRequestKey = rootCreateTaskRequestKey,
                                                    onCreateTaskRequestHandled = ::consumeRootCreateTaskRequest,
                                                    scrollToTopRequestKey = floaterTaskHomeScrollToTopRequestKey,
                                                    onRootDockCollapsedChange = {
                                                        rootDockCollapsed = it
                                                    },
                                                    onRootControlsVisibleChange = {
                                                        rootControlsVisible = it
                                                    },
                                                )
                                            }
                                        }

                                        if (rootControlsVisible) {
                                            val rootCreateTaskButtonColor =
                                                if (rootFeedTab == RootFeedTab.FLOATER_TASK_HOME) {
                                                    TdayFloaterAccent
                                                } else {
                                                    TdayTodayBlue
                                                }

                                            RootFeedDock(
                                                activeTab = rootFeedTab,
                                                collapsed = rootDockCollapsed,
                                                onTabSelected = ::handleRootFeedTabSelection,
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .zIndex(8f),
                                            )
                                            RootCreateTaskButton(
                                                onClick = ::requestRootCreateTask,
                                                backgroundColor = rootCreateTaskButtonColor,
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .navigationBarsPadding()
                                                    .padding(
                                                        end = TdayDimens.ContentPaddingHorizontal,
                                                        bottom = TdayDimens.ContentPaddingHorizontal,
                                                    )
                                                    .zIndex(8f),
                                            )
                                        }
                                    }
                                } else {
                                    ScheduledTaskHomeScreen(
                                        uiState = unauthenticatedScheduledTaskHomeUiState,
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
                                        onOpenList = { _, _ -> },
                                        onCreateTask = { _ -> },
                                        onParseTaskTitleNlp = { _, _ -> null },
                                        onCreateList = { _, _, _ -> },
                                        onCompleteTask = {},
                                        onDeleteTask = {},
                                        onUpdateTask = { _, _ -> },
                                        summaryAvailable = false,
                                    )
                                }
                            }

                            if (showOnboardingWizard) {
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
                                                lastAuthUsername = username
                                                lastAuthPassword = password
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
                                                lastAuthUsername = username
                                                lastAuthPassword = password
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
                    }

                    composable(
                        route = AppRoute.FloaterTaskHome.route,
                        deepLinks = listOf(navDeepLink { uriPattern = "tday://floater" }),
                    ) { entry ->
                        LaunchedEffect(entry.destination.id) {
                            rootFeedTab = RootFeedTab.FLOATER_TASK_HOME
                            navController.navigate(AppRoute.ScheduledTaskHome.route) {
                                popUpTo(entry.destination.id) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize())
                    }

                    composable(
                        route = AppRoute.TodayTodos.route,
                        deepLinks = listOf(navDeepLink { uriPattern = "tday://todos/today" }),
                    ) {
                        TodosRoute(
                            mode = TodoListMode.TODAY,
                            onBack = { navController.popBackStack() },
                            pullRefreshEnabled = !appUiState.isLocalMode,
                            summaryAvailable = !appUiState.isLocalMode,
                        )
                    }

                    composable(
                        route = AppRoute.CreateTodayTodo.route,
                        arguments = listOf(
                            navArgument("target") {
                                type = NavType.StringType
                                defaultValue = "today"
                            },
                        ),
                        deepLinks = listOf(navDeepLink {
                            uriPattern = "tday://todos/create?target={target}"
                        }),
                    ) { entry ->
                        val createTarget = entry.arguments?.getString("target") ?: "today"
                        if (createTarget.equals("floater", ignoreCase = true)) {
                            LaunchedEffect(entry.destination.id, createTarget) {
                                rootFeedTab = RootFeedTab.FLOATER_TASK_HOME
                                pendingFloaterTaskHomeCreateTask = true
                                navController.navigate(AppRoute.ScheduledTaskHome.route) {
                                    popUpTo(entry.destination.id) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                            Box(modifier = Modifier.fillMaxSize())
                        } else {
                            val finishCreateTodayFlow = {
                                rootFeedTab = RootFeedTab.SCHEDULED_TASK_HOME
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
                                pullRefreshEnabled = !appUiState.isLocalMode,
                                summaryAvailable = !appUiState.isLocalMode,
                            )
                        }
                    }

                    composable(
                        route = AppRoute.OverdueTodos.route,
                        deepLinks = listOf(navDeepLink { uriPattern = "tday://todos/overdue" }),
                    ) {
                        TodosRoute(
                            mode = TodoListMode.OVERDUE,
                            onBack = { navController.popBackStack() },
                            onOpenMorningSweep = {
                                navController.navigate(AppRoute.MorningSweep.route) {
                                    launchSingleTop = true
                                }
                            },
                            pullRefreshEnabled = !appUiState.isLocalMode,
                            summaryAvailable = !appUiState.isLocalMode,
                        )
                    }

                    composable(
                        route = AppRoute.ScheduledTodos.route,
                        deepLinks = listOf(navDeepLink { uriPattern = "tday://todos/scheduled" }),
                    ) {
                        TodosRoute(
                            mode = TodoListMode.SCHEDULED,
                            onBack = { navController.popBackStack() },
                            pullRefreshEnabled = !appUiState.isLocalMode,
                            summaryAvailable = !appUiState.isLocalMode,
                        )
                    }

                    composable(
                        route = AppRoute.AllTodos.route,
                        arguments = listOf(
                            navArgument("highlightTodoId") {
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
                            entry.arguments?.getString("highlightTodoId").orEmpty(),
                        ).ifBlank { null }
                        val highlightTodoId = pendingSearchHighlightTodoId ?: argumentHighlightTodoId
                        TodosRoute(
                            mode = TodoListMode.ALL,
                            highlightTodoId = highlightTodoId,
                            onBack = { navController.popBackStack() },
                            pullRefreshEnabled = !appUiState.isLocalMode,
                            summaryAvailable = !appUiState.isLocalMode,
                        )
                    }

                    composable(
                        route = AppRoute.PriorityTodos.route,
                        deepLinks = listOf(navDeepLink { uriPattern = "tday://todos/priority" }),
                    ) {
                        TodosRoute(
                            mode = TodoListMode.PRIORITY,
                            onBack = { navController.popBackStack() },
                            pullRefreshEnabled = !appUiState.isLocalMode,
                            summaryAvailable = !appUiState.isLocalMode,
                        )
                    }

                    composable(
                        route = AppRoute.ListTodos.route,
                        arguments = listOf(
                            navArgument("listId") { type = NavType.StringType },
                            navArgument("listName") { type = NavType.StringType },
                        ),
                        deepLinks = listOf(
                            navDeepLink { uriPattern = "tday://todos/list/{listId}/{listName}" },
                        ),
                    ) { entry ->
                        val listId = entry.arguments?.getString("listId").orEmpty()
                        val listName = Uri.decode(entry.arguments?.getString("listName").orEmpty())
                        TodosRoute(
                            mode = TodoListMode.LIST,
                            listId = listId,
                            listName = listName,
                            onBack = { navController.popBackStack() },
                            pullRefreshEnabled = !appUiState.isLocalMode,
                            summaryAvailable = !appUiState.isLocalMode,
                            onListDeleted = {
                                navController.navigate(AppRoute.ScheduledTaskHome.route) {
                                    popUpTo(AppRoute.ScheduledTaskHome.route) { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                        )
                    }

                    composable(
                        route = AppRoute.FloaterListTodos.route,
                        arguments = listOf(
                            navArgument("listId") { type = NavType.StringType },
                            navArgument("listName") { type = NavType.StringType },
                        ),
                        deepLinks = listOf(
                            navDeepLink { uriPattern = "tday://floater/list/{listId}/{listName}" },
                        ),
                    ) { entry ->
                        val listId = entry.arguments?.getString("listId").orEmpty()
                        val listName = Uri.decode(entry.arguments?.getString("listName").orEmpty())
                        TodosRoute(
                            mode = TodoListMode.FLOATER,
                            listId = listId,
                            listName = listName,
                            onBack = { navController.popBackStack() },
                            pullRefreshEnabled = !appUiState.isLocalMode,
                            summaryAvailable = !appUiState.isLocalMode,
                            onListDeleted = {
                                rootFeedTab = RootFeedTab.FLOATER_TASK_HOME
                                navController.navigate(AppRoute.ScheduledTaskHome.route) {
                                    popUpTo(AppRoute.ScheduledTaskHome.route) { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                        )
                    }

                    composable(
                        route = AppRoute.Completed.route,
                        deepLinks = listOf(navDeepLink { uriPattern = "tday://completed" }),
                    ) {
                        val viewModel: CompletedViewModel = hiltViewModel()
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        OnRouteResume { viewModel.load() }
                        CompletedScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onRefresh = { viewModel.refresh(userInitiated = true) },
                            onUncomplete = viewModel::uncomplete,
                            onDelete = viewModel::delete,
                            onUpdateTask = viewModel::update,
                        )
                    }

                    composable(
                        route = AppRoute.Calendar.route,
                        deepLinks = listOf(navDeepLink { uriPattern = "tday://calendar" }),
                    ) {
                        val viewModel: CalendarViewModel = hiltViewModel()
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        OnRouteResume { viewModel.load() }
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
                                    CarTaskMode.TODAY -> "today"
                                    CarTaskMode.FLOATER -> "floater"
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
                        route = AppRoute.Settings.route,
                        deepLinks = listOf(navDeepLink { uriPattern = "tday://settings" }),
                        enterTransition = {
                            settingsEnterTransition()
                        },
                        exitTransition = {
                            settingsExitTransition()
                        },
                        popEnterTransition = {
                            settingsEnterTransition()
                        },
                        popExitTransition = {
                            settingsExitTransition()
                        },
                    ) {
                        OnRouteResume {
                            appViewModel.refreshAiSummaryPreference()
                            appViewModel.refreshVersionInfo()
                        }
                        SettingsScreen(
                            user = appUiState.user,
                            isLocalMode = appUiState.isLocalMode,
                            selectedThemeMode = appUiState.themeMode,
                            selectedReminder = appUiState.selectedReminder,
                            syncStatus = appUiState.syncStatus,
                            aiSummaryEnabled = appUiState.aiSummaryEnabled,
                            hasUpdate = releaseUiState.hasUpdate,
                            latestVersionName = releaseUiState.latestRelease?.version,
                            backendVersion = appUiState.backendVersion,
                            versionCheckResult = appUiState.versionCheckResult,
                            onThemeModeSelected = appViewModel::setThemeMode,
                            onReminderSelected = appViewModel::setDefaultReminder,
                        selectedDayAhead = appUiState.selectedDayAhead,
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

                    composable(
                        route = AppRoute.LatestRelease.route,
                        enterTransition = { settingsEnterTransition() },
                        exitTransition = { settingsExitTransition() },
                        popEnterTransition = { settingsEnterTransition() },
                        popExitTransition = { settingsExitTransition() },
                    ) {
                        OnRouteResume {
                            appViewModel.refreshVersionInfo()
                        }
                        LatestReleaseScreen(
                            uiState = releaseUiState,
                            onBack = { navController.popBackStack() },
                            onRetry = releaseViewModel::load,
                        )
                    }

                    composable(
                        route = AppRoute.MorningSweep.route,
                        deepLinks = listOf(navDeepLink { uriPattern = "tday://morning-sweep" }),
                        enterTransition = { settingsEnterTransition() },
                        exitTransition = { settingsExitTransition() },
                        popEnterTransition = { settingsEnterTransition() },
                        popExitTransition = { settingsExitTransition() },
                    ) {
                        MorningSweepScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable(
                        route = AppRoute.HelpGuide.route,
                        arguments = listOf(
                            navArgument("topic") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                        enterTransition = { settingsEnterTransition() },
                        exitTransition = { settingsExitTransition() },
                        popEnterTransition = { settingsEnterTransition() },
                        popExitTransition = { settingsExitTransition() },
                    ) { backStackEntry ->
                        HelpGuideScreen(
                            isLocalMode = appUiState.isLocalMode,
                            onBack = { navController.popBackStack() },
                            onOpenDeepLink = { route ->
                                navController.navigate(route) { launchSingleTop = true }
                            },
                            initialTopic = backStackEntry.arguments?.getString("topic"),
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

private const val TOAST_AUTO_DISMISS_SHORT_MS = 4_000L
private const val TOAST_AUTO_DISMISS_WITH_ACTION_MS = 8_000L

@Composable
private fun CollectAppSnackbars(
    appViewModel: AppViewModel,
    onShowToast: (TdayToastData) -> Unit,
) {
    LaunchedEffect(Unit) {
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
                    autoDismissMillis = if (event.actionLabel != null) {
                        TOAST_AUTO_DISMISS_WITH_ACTION_MS
                    } else {
                        TOAST_AUTO_DISMISS_SHORT_MS
                    },
                    actionLabel = event.actionLabel,
                    onAction = event.onAction,
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

@Composable
private fun HandleStartupNavigation(
    appUiState: AppUiState,
    currentRoute: String?,
    navController: NavHostController,
    isStartupSplashHeld: Boolean,
) {
    LaunchedEffect(
        appUiState.loading,
        appUiState.isWorkspaceAvailable,
        currentRoute,
        isStartupSplashHeld,
    ) {
        if (appUiState.loading) return@LaunchedEffect
        if (isStartupSplashHeld) return@LaunchedEffect

        if (appUiState.isWorkspaceAvailable) {
            val unauthenticatedRoutes = setOf(
                AppRoute.Splash.route,
                AppRoute.Login.route,
                AppRoute.ServerSetup.route,
            )
            if (currentRoute in unauthenticatedRoutes) {
                navigateScheduledTaskHome(navController, currentRoute)
            }
            return@LaunchedEffect
        }

        // The reset-password screen is reachable while logged out — don't bounce it
        // back to the login/scheduled-task-home overlay.
        if (currentRoute != AppRoute.ScheduledTaskHome.route &&
            currentRoute != AppRoute.ForgotPassword.route
        ) {
            navigateScheduledTaskHome(navController, currentRoute)
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
    onOpenFloaterList: (String, String) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
    onOpenMorningSweep: () -> Unit = {},
    highlightTodoId: String? = null,
    listId: String? = null,
    listName: String? = null,
    rootFeedTab: RootFeedTab? = null,
    onRootFeedTabSelected: ((RootFeedTab) -> Unit)? = null,
    showRootFeedDock: Boolean = true,
    showCreateTaskButton: Boolean = true,
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
        onOpenMorningSweep = onOpenMorningSweep,
        onUpdateListSettings = { targetListId, name, color, iconKey ->
            viewModel.updateListSettings(
                listId = targetListId,
                name = name,
                color = color,
                iconKey = iconKey,
            )
        },
        onDeleteList = { targetListId ->
            viewModel.deleteList(
                listId = targetListId,
                onDeleted = onListDeleted,
            )
        },
        onOpenFloaterList = onOpenFloaterList,
        onOpenSettings = onOpenSettings,
        onCreateList = viewModel::createList,
        rootFeedTab = rootFeedTab,
        onRootFeedTabSelected = onRootFeedTabSelected,
        showRootFeedDock = showRootFeedDock,
        showCreateTaskButton = showCreateTaskButton,
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

private fun navigationSlideOffset(fullDistance: Int): Int =
    (fullDistance * NAV_SLIDE_FRACTION).roundToInt()

private fun settingsVerticalOffset(fullHeight: Int): Int =
    (fullHeight * SETTINGS_VERTICAL_FRACTION).roundToInt()

private fun settingsEnterTransition(): EnterTransition =
    slideInVertically(
        animationSpec = tween(
            durationMillis = SETTINGS_ENTER_DURATION_MS,
            easing = LinearOutSlowInEasing,
        ),
        initialOffsetY = ::settingsVerticalOffset,
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = NAV_FADE_IN_DURATION_MS,
            easing = LinearOutSlowInEasing,
        ),
    )

private fun settingsExitTransition(): ExitTransition =
    slideOutVertically(
        animationSpec = tween(
            durationMillis = SETTINGS_EXIT_DURATION_MS,
            easing = FastOutLinearInEasing,
        ),
        targetOffsetY = ::settingsVerticalOffset,
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = NAV_FADE_OUT_DURATION_MS,
            easing = FastOutLinearInEasing,
        ),
    )

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
