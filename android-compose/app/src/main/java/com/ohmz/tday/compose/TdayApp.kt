package com.ohmz.tday.compose

import android.net.Uri
import com.ohmz.tday.compose.R
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.compose.ui.platform.LocalContext
import com.ohmz.tday.compose.core.model.DashboardSummary
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.core.navigation.AppRoute
import com.ohmz.tday.compose.core.ui.OfflineBanner
import com.ohmz.tday.compose.core.ui.SnackbarKind
import com.ohmz.tday.compose.feature.app.AppViewModel
import com.ohmz.tday.compose.feature.auth.AuthViewModel
import com.ohmz.tday.compose.feature.calendar.CalendarViewModel
import com.ohmz.tday.compose.feature.calendar.CalendarScreen
import com.ohmz.tday.compose.feature.completed.CompletedScreen
import com.ohmz.tday.compose.feature.completed.CompletedViewModel
import com.ohmz.tday.compose.feature.home.HomeScreen
import com.ohmz.tday.compose.feature.home.HomeUiState
import com.ohmz.tday.compose.feature.home.HomeViewModel
import com.ohmz.tday.compose.feature.onboarding.OnboardingWizardOverlay
import com.ohmz.tday.compose.feature.app.AppUiState
import com.ohmz.tday.compose.feature.release.LatestReleaseScreen
import com.ohmz.tday.compose.feature.release.LatestReleaseUiState
import com.ohmz.tday.compose.feature.release.LatestReleaseViewModel
import com.ohmz.tday.compose.feature.settings.SettingsScreen
import com.ohmz.tday.compose.feature.todos.TodoListScreen
import com.ohmz.tday.compose.feature.todos.TodoListViewModel
import com.ohmz.tday.compose.ui.theme.TdayTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val NAV_ENTER_DURATION_MS = 440
private const val NAV_EXIT_DURATION_MS = 320
private const val NAV_FADE_IN_DURATION_MS = 360
private const val NAV_FADE_OUT_DURATION_MS = 240
private const val NAV_SLIDE_FRACTION = 0.18f
private const val SETTINGS_ENTER_DURATION_MS = 380
private const val SETTINGS_EXIT_DURATION_MS = 260
private const val SETTINGS_VERTICAL_FRACTION = 0.22f

@Composable
fun TdayApp() {
    val navController = rememberNavController()
    val appViewModel: AppViewModel = hiltViewModel()
    val releaseViewModel: LatestReleaseViewModel = hiltViewModel()
    val appUiState by appViewModel.uiState.collectAsStateWithLifecycle()
    val releaseUiState by releaseViewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val updateToastMessage = releaseUiState.latestRelease?.tagName?.let { versionLabel ->
        stringResource(R.string.release_launch_update_toast, versionLabel)
    }
    var activeToast by remember { mutableStateOf<AppToastMessage?>(null) }
    var hasShownLaunchUpdateToast by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val activity = LocalContext.current as? MainActivity
    val deepLinkIntent by activity?.deepLinkIntent?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }

    LaunchedEffect(deepLinkIntent) {
        val intent = deepLinkIntent ?: return@LaunchedEffect
        navController.handleDeepLink(intent)
    }

    CollectAppSnackbars(
        appViewModel = appViewModel,
        snackbarHostState = snackbarHostState,
    )

    fun showTaskDeletedToast() {
        activeToast = AppToastMessage(
            id = System.currentTimeMillis(),
            message = "Task deleted",
        )
    }

    HandleStartupNavigation(
        appUiState = appUiState,
        currentRoute = currentRoute,
        navController = navController,
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
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = AppRoute.Splash.route,
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
                    SplashScreen()
                }

                composable(
                    route = AppRoute.ServerSetup.route,
                    enterTransition = { fadeIn(tween(300)) },
                    exitTransition = { fadeOut(tween(300)) },
                ) {
                    SplashScreen()
                }

                composable(
                    route = AppRoute.Login.route,
                    enterTransition = { fadeIn(tween(300)) },
                    exitTransition = { fadeOut(tween(300)) },
                ) {
                    SplashScreen()
                }

                composable(
                    route = AppRoute.Home.route,
                    deepLinks = listOf(navDeepLink { uriPattern = "tday://home" }),
                ) {
                    val authViewModel: AuthViewModel = hiltViewModel()
                    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
                    val showOnboardingWizard = !appUiState.authenticated

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
                            if (appUiState.authenticated) {
                                val homeViewModel: HomeViewModel = hiltViewModel()
                                val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
                                OnRouteResume {
                                    homeViewModel.refreshFromCache()
                                }
                                HomeScreen(
                                    uiState = homeUiState,
                                    onRefresh = homeViewModel::refresh,
                                    onOpenToday = { navController.navigate(AppRoute.TodayTodos.route) },
                                    onOpenScheduled = { navController.navigate(AppRoute.ScheduledTodos.route) },
                                    onOpenAll = { navController.navigate(AppRoute.AllTodos.create()) },
                                    onOpenPriority = { navController.navigate(AppRoute.PriorityTodos.route) },
                                    onOpenCompleted = { navController.navigate(AppRoute.Completed.route) },
                                    onOpenCalendar = { navController.navigate(AppRoute.Calendar.route) },
                                    onOpenSettings = { navController.navigate(AppRoute.Settings.route) },
                                    onOpenTaskFromSearch = { todoId ->
                                        navController.navigate(AppRoute.AllTodos.create(highlightTodoId = todoId))
                                    },
                                    onOpenList = { id, name ->
                                        navController.navigate(AppRoute.ListTodos.create(id, name))
                                    },
                                    onCreateTask = { payload ->
                                        homeViewModel.createTask(payload)
                                    },
                                    onParseTaskTitleNlp = homeViewModel::parseTaskTitleNlp,
                                    onCreateList = { name, color, iconKey ->
                                        homeViewModel.createList(
                                            name = name,
                                            color = color,
                                            iconKey = iconKey,
                                        )
                                    },
                                )
                            } else {
                                HomeScreen(
                                    uiState = UnauthenticatedHomeUiState,
                                    onRefresh = {},
                                    onOpenToday = {},
                                    onOpenScheduled = {},
                                    onOpenAll = {},
                                    onOpenPriority = {},
                                    onOpenCompleted = {},
                                    onOpenCalendar = {},
                                    onOpenSettings = {},
                                    onOpenTaskFromSearch = {},
                                    onOpenList = { _, _ -> },
                                    onCreateTask = { _ -> },
                                    onParseTaskTitleNlp = { _, _, _ -> null },
                                    onCreateList = { _, _, _ -> },
                                )
                            }
                        }

                        if (showOnboardingWizard) {
                            OnboardingWizardOverlay(
                                initialServerUrl = appUiState.serverUrl,
                                serverErrorMessage = appUiState.error,
                                serverCanResetTrust = appUiState.canResetServerTrust,
                                pendingApprovalMessage = appUiState.pendingApprovalMessage,
                                authUiState = authUiState,
                                onConnectServer = { rawUrl, onResult ->
                                    appViewModel.saveServerUrl(
                                        rawUrl = rawUrl,
                                        onSuccess = { onResult(Result.success(Unit)) },
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
                                onLogin = { email, password ->
                                    authViewModel.login(email, password) {
                                        appViewModel.refreshSession()
                                    }
                                },
                                onRegister = { firstName, email, password, onSuccess ->
                                    authViewModel.register(
                                        firstName = firstName,
                                        lastName = "",
                                        email = email,
                                        password = password,
                                    ) {
                                        onSuccess()
                                        appViewModel.refreshSession()
                                    }
                                },
                                onClearAuthStatus = {
                                    authViewModel.clearStatus()
                                    appViewModel.clearPendingApprovalNotice()
                                },
                            )
                        }
                    }
                }

                composable(
                    route = AppRoute.TodayTodos.route,
                    deepLinks = listOf(navDeepLink { uriPattern = "tday://todos/today" }),
                ) {
                    TodosRoute(
                        mode = TodoListMode.TODAY,
                        onBack = { navController.popBackStack() },
                        onTaskDeleted = ::showTaskDeletedToast,
                    )
                }

                composable(
                    route = AppRoute.ScheduledTodos.route,
                    deepLinks = listOf(navDeepLink { uriPattern = "tday://todos/scheduled" }),
                ) {
                    TodosRoute(
                        mode = TodoListMode.SCHEDULED,
                        onBack = { navController.popBackStack() },
                        onTaskDeleted = ::showTaskDeletedToast,
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
                    val highlightTodoId = Uri.decode(
                        entry.arguments?.getString("highlightTodoId").orEmpty(),
                    ).ifBlank { null }
                    TodosRoute(
                        mode = TodoListMode.ALL,
                        highlightTodoId = highlightTodoId,
                        onBack = { navController.popBackStack() },
                        onTaskDeleted = ::showTaskDeletedToast,
                    )
                }

                composable(
                    route = AppRoute.PriorityTodos.route,
                    deepLinks = listOf(navDeepLink { uriPattern = "tday://todos/priority" }),
                ) {
                    TodosRoute(
                        mode = TodoListMode.PRIORITY,
                        onBack = { navController.popBackStack() },
                        onTaskDeleted = ::showTaskDeletedToast,
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
                        onTaskDeleted = ::showTaskDeletedToast,
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
                        onRefresh = viewModel::refresh,
                        onUncomplete = viewModel::uncomplete,
                        onDelete = { item ->
                            viewModel.delete(item) {
                                showTaskDeletedToast()
                            }
                        },
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
                        onRefresh = viewModel::refresh,
                        onCreateTask = viewModel::createTask,
                        onParseTaskTitleNlp = viewModel::parseTaskTitleNlp,
                        onCompleteTask = viewModel::complete,
                        onUncompleteTask = viewModel::uncomplete,
                        onUpdateTask = viewModel::updateTask,
                        onDelete = { todo ->
                            viewModel.delete(todo) {
                                showTaskDeletedToast()
                            }
                        },
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
                        appViewModel.refreshAdminAiSummarySetting()
                    }
                    SettingsScreen(
                        user = appUiState.user,
                        selectedThemeMode = appUiState.themeMode,
                        selectedReminder = appUiState.selectedReminder,
                        adminAiSummaryEnabled = appUiState.adminAiSummaryEnabled,
                        isAdminAiSummaryLoading = appUiState.isAdminAiSummaryLoading,
                        isAdminAiSummarySaving = appUiState.isAdminAiSummarySaving,
                        adminAiSummaryError = appUiState.adminAiSummaryError,
                        aiSummaryValidationError = appUiState.aiSummaryValidationError,
                        onThemeModeSelected = appViewModel::setThemeMode,
                        onReminderSelected = appViewModel::setDefaultReminder,
                        onToggleAdminAiSummary = appViewModel::setAdminAiSummaryEnabled,
                        onDismissAiValidationError = appViewModel::dismissAiSummaryValidationError,
                        onBack = { navController.popBackStack() },
                        onLogout = { appViewModel.logout() },
                        onOpenLatestRelease = { navController.navigate(AppRoute.LatestRelease.route) },
                    )
                }

                composable(
                    route = AppRoute.LatestRelease.route,
                    enterTransition = { settingsEnterTransition() },
                    exitTransition = { settingsExitTransition() },
                    popEnterTransition = { settingsEnterTransition() },
                    popExitTransition = { settingsExitTransition() },
                ) {
                    LatestReleaseScreen(
                        uiState = releaseUiState,
                        onBack = { navController.popBackStack() },
                        onRetry = releaseViewModel::load,
                    )
                }
            }

            OfflineBanner(
                visible = appUiState.isOffline && appUiState.authenticated,
                pendingMutationCount = appUiState.pendingMutationCount,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 60.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary,
                )
            }

            TdayToastHost(
                toast = activeToast,
                onDismiss = { activeToast = null },
            )
        }
    }
}

@Composable
private fun CollectAppSnackbars(
    appViewModel: AppViewModel,
    snackbarHostState: SnackbarHostState,
) {
    LaunchedEffect(Unit) {
        appViewModel.snackbarManager.events.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.actionLabel,
                duration = if (event.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                event.onAction?.invoke()
            }
        }
    }
}

@Composable
private fun HandleStartupNavigation(
    appUiState: AppUiState,
    currentRoute: String?,
    navController: NavHostController,
) {
    LaunchedEffect(
        appUiState.loading,
        appUiState.authenticated,
        currentRoute,
    ) {
        if (appUiState.loading) return@LaunchedEffect

        if (appUiState.authenticated) {
            val unauthenticatedRoutes = setOf(
                AppRoute.Splash.route,
                AppRoute.Login.route,
                AppRoute.ServerSetup.route,
            )
            if (currentRoute in unauthenticatedRoutes) {
                navigateHome(navController, currentRoute)
            }
            return@LaunchedEffect
        }

        if (currentRoute != AppRoute.Home.route) {
            navigateHome(navController, currentRoute)
        }
    }
}

private fun navigateHome(
    navController: NavHostController,
    currentRoute: String?,
) {
    navController.navigate(AppRoute.Home.route) {
        when (currentRoute) {
            AppRoute.Splash.route -> popUpTo(AppRoute.Splash.route) { inclusive = true }
            AppRoute.Login.route -> popUpTo(AppRoute.Login.route) { inclusive = true }
            AppRoute.Home.route -> popUpTo(AppRoute.Home.route) { inclusive = true }
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
    activeToast: AppToastMessage?,
    hasShownLaunchUpdateToast: Boolean,
    onToastShown: () -> Unit,
    onShowToast: (AppToastMessage) -> Unit,
    onClearToast: () -> Unit,
    onOpenLatestRelease: () -> Unit,
) {
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
            AppToastMessage(
                id = System.currentTimeMillis(),
                message = message,
                kind = AppToastKind.UpdateAvailable,
                autoDismissMillis = null,
                onTap = {
                    onClearToast()
                    onOpenLatestRelease()
                },
            ),
        )
    }

    LaunchedEffect(currentRoute, activeToast?.kind) {
        if (currentRoute == AppRoute.LatestRelease.route &&
            activeToast?.kind == AppToastKind.UpdateAvailable
        ) {
            onClearToast()
        }
    }
}

@Composable
private fun TodosRoute(
    mode: TodoListMode,
    onBack: () -> Unit,
    onTaskDeleted: () -> Unit,
    highlightTodoId: String? = null,
    listId: String? = null,
    listName: String? = null,
) {
    val viewModel: TodoListViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(mode, listId, listName) {
        viewModel.load(mode = mode, listId = listId, listName = listName)
    }
    OnRouteResume {
        viewModel.load(mode = mode, listId = listId, listName = listName)
    }

    TodoListScreen(
        uiState = uiState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        highlightedTodoId = highlightTodoId,
        onSummarize = viewModel::summarizeCurrentMode,
        onDismissSummaryConnectivityError = viewModel::dismissSummaryConnectivityError,
        onAddTask = viewModel::addTask,
        onParseTaskTitleNlp = viewModel::parseTaskTitleNlp,
        onUpdateTask = viewModel::updateTask,
        onComplete = viewModel::toggleComplete,
        onDelete = { todo ->
            viewModel.delete(todo) {
                onTaskDeleted()
            }
        },
        onUpdateListSettings = { targetListId, name, color, iconKey ->
            viewModel.updateListSettings(
                listId = targetListId,
                name = name,
                color = color,
                iconKey = iconKey,
            )
        },
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

private data class AppToastMessage(
    val id: Long,
    val message: String,
    val kind: AppToastKind = AppToastKind.Default,
    val autoDismissMillis: Long? = 2200L,
    val onTap: (() -> Unit)? = null,
)

private enum class AppToastKind {
    Default,
    UpdateAvailable,
}

private enum class AppToastDismissDirection {
    Left,
    Right,
    Up,
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
private fun TdayToastHost(
    toast: AppToastMessage?,
    onDismiss: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val accent = Color(0xFFDDB37D)
    val toastColor = if (isDark) {
        lerp(colorScheme.surfaceVariant, accent, 0.22f)
    } else {
        lerp(colorScheme.surfaceVariant, accent, 0.30f)
    }
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 72.dp.toPx() }

    LaunchedEffect(toast?.id, toast?.autoDismissMillis) {
        if (toast == null) return@LaunchedEffect
        val autoDismissMillis = toast.autoDismissMillis ?: return@LaunchedEffect
        kotlinx.coroutines.delay(autoDismissMillis)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(animationSpec = tween(durationMillis = 180)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = 220),
                    initialOffsetY = { -it / 2 },
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 160)) +
                slideOutVertically(
                    animationSpec = tween(durationMillis = 200),
                    targetOffsetY = { -it / 2 },
                ),
        ) {
            val visibleToast = toast ?: return@AnimatedVisibility
            val scope = rememberCoroutineScope()
            val offsetX = remember(visibleToast.id) { Animatable(0f) }
            val offsetY = remember(visibleToast.id) { Animatable(0f) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .offset {
                        IntOffset(
                            x = offsetX.value.roundToInt(),
                            y = offsetY.value.roundToInt(),
                        )
                    }
                    .pointerInput(visibleToast.id) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    offsetX.snapTo(offsetX.value + dragAmount.x)
                                    offsetY.snapTo(offsetY.value + dragAmount.y)
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    launch { offsetX.animateTo(0f) }
                                    launch { offsetY.animateTo(0f) }
                                }
                            },
                            onDragEnd = {
                                val dismissDirection = when {
                                    offsetY.value <= -dismissThresholdPx &&
                                        abs(offsetY.value) >= abs(offsetX.value) -> AppToastDismissDirection.Up
                                    offsetX.value <= -dismissThresholdPx -> AppToastDismissDirection.Left
                                    offsetX.value >= dismissThresholdPx -> AppToastDismissDirection.Right
                                    else -> null
                                }
                                scope.launch {
                                    when (dismissDirection) {
                                        AppToastDismissDirection.Left -> {
                                            offsetX.animateTo(-size.width.toFloat())
                                            onDismiss()
                                        }
                                        AppToastDismissDirection.Right -> {
                                            offsetX.animateTo(size.width.toFloat())
                                            onDismiss()
                                        }
                                        AppToastDismissDirection.Up -> {
                                            offsetY.animateTo(-size.height.toFloat() * 1.5f)
                                            onDismiss()
                                        }
                                        null -> {
                                            launch { offsetX.animateTo(0f) }
                                            launch { offsetY.animateTo(0f) }
                                        }
                                    }
                                }
                            },
                        )
                    }
                    .then(
                        if (visibleToast.onTap != null) {
                            Modifier.clickable { visibleToast.onTap.invoke() }
                        } else {
                            Modifier
                        },
                    ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = toastColor),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = accent.copy(alpha = if (isDark) 0.36f else 0.42f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 8.dp else 5.dp),
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = visibleToast.message,
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

private val splashTaglines = listOf(
    "Your server remembers, so you don\u2019t have to",
    "Hosted by you, haunted by deadlines",
    "Because \u2018I\u2019ll remember later\u2019 is always a lie",
    "Self-hosted sanity, one task at a time",
    "Nagging you from your own hardware",
    "Your data, your server, your no-excuse zone",
    "Making procrastination slightly harder since v0.1",
    "Running on your server, running your life",
    "Because sticky notes don\u2019t have push notifications",
    "Turning \u2018I forgot\u2019 into \u2018I got this\u2019",
    "Your personal nudge machine",
    "Self-hosted, self-organized\u2026 well, getting there",
    "Where forgotten tasks go to get found",
    "Adulting, but make it self-hosted",
    "Taming chaos from a server near you",
    "Future you says thanks in advance",
    "The cloud is just someone else\u2019s server. This one\u2019s yours.",
    "Organizing your life, no landlord required",
    "Zero trust\u2026 except your own server",
    "Syncing your tasks, judging your priorities",
)

@Composable
private fun SplashScreen() {
    val tagline = remember { splashTaglines.random() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Image(
                painter = painterResource(id = R.drawable.splash_icon),
                contentDescription = "T'Day",
                modifier = Modifier.size(160.dp),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "T\u2019Day",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val UnauthenticatedHomeUiState = HomeUiState(
    isLoading = false,
    summary = DashboardSummary(
        todayCount = 0,
        scheduledCount = 0,
        allCount = 0,
        priorityCount = 0,
        completedCount = 0,
        lists = listOf(
            ListSummary(
                id = "locked",
                name = "Sign in to load your lists",
                color = null,
                iconKey = null,
                todoCount = 0,
            ),
        ),
    ),
    errorMessage = null,
)
