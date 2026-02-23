package com.ohmz.tday.compose

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
import androidx.navigation.navArgument
import com.ohmz.tday.compose.core.model.DashboardSummary
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.core.navigation.AppRoute
import com.ohmz.tday.compose.feature.app.AppViewModel
import com.ohmz.tday.compose.feature.auth.AuthViewModel
import com.ohmz.tday.compose.feature.calendar.CalendarViewModel
import com.ohmz.tday.compose.feature.calendar.CalendarScreen
import com.ohmz.tday.compose.feature.completed.CompletedScreen
import com.ohmz.tday.compose.feature.completed.CompletedViewModel
import com.ohmz.tday.compose.feature.home.HomeScreen
import com.ohmz.tday.compose.feature.home.HomeUiState
import com.ohmz.tday.compose.feature.home.HomeViewModel
import com.ohmz.tday.compose.feature.notes.NotesScreen
import com.ohmz.tday.compose.feature.notes.NotesViewModel
import com.ohmz.tday.compose.feature.onboarding.OnboardingWizardOverlay
import com.ohmz.tday.compose.feature.settings.SettingsScreen
import com.ohmz.tday.compose.feature.todos.TodoListScreen
import com.ohmz.tday.compose.feature.todos.TodoListViewModel
import com.ohmz.tday.compose.ui.theme.TdayTheme

@Composable
fun TdayApp() {
    val navController = rememberNavController()
    val appViewModel: AppViewModel = hiltViewModel()
    val appUiState by appViewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var activeToast by remember { mutableStateOf<AppToastMessage?>(null) }

    fun showTaskDeletedToast() {
        activeToast = AppToastMessage(
            id = System.currentTimeMillis(),
            message = "Task deleted",
        )
    }

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
                navController.navigate(AppRoute.Home.route) {
                    when (currentRoute) {
                        AppRoute.Splash.route -> popUpTo(AppRoute.Splash.route) { inclusive = true }
                        AppRoute.Login.route -> popUpTo(AppRoute.Login.route) { inclusive = true }
                        AppRoute.ServerSetup.route -> popUpTo(AppRoute.ServerSetup.route) { inclusive = true }
                    }
                    launchSingleTop = true
                }
            }
            return@LaunchedEffect
        }

        val onboardingRoutes = setOf(AppRoute.Home.route)
        if (currentRoute !in onboardingRoutes) {
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
    }

    TdayTheme(themeMode = appUiState.themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = AppRoute.Splash.route,
            ) {
                composable(AppRoute.Splash.route) {
                    SplashScreen()
                }

                composable(AppRoute.ServerSetup.route) {
                    SplashScreen()
                }

                composable(AppRoute.Login.route) {
                    SplashScreen()
                }

                composable(AppRoute.Home.route) {
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

                composable(AppRoute.TodayTodos.route) {
                    TodosRoute(
                        mode = TodoListMode.TODAY,
                        onBack = { navController.popBackStack() },
                        onTaskDeleted = ::showTaskDeletedToast,
                    )
                }

                composable(AppRoute.ScheduledTodos.route) {
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

                composable(AppRoute.PriorityTodos.route) {
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

                composable(AppRoute.Completed.route) {
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

                composable(AppRoute.Notes.route) {
                    val viewModel: NotesViewModel = hiltViewModel()
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    OnRouteResume { viewModel.load() }
                    NotesScreen(
                        uiState = uiState,
                        onBack = { navController.popBackStack() },
                        onRefresh = viewModel::refresh,
                        onCreate = viewModel::create,
                        onDelete = viewModel::delete,
                    )
                }

                composable(AppRoute.Calendar.route) {
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

                composable(AppRoute.Settings.route) {
                    OnRouteResume {
                        appViewModel.refreshAdminAiSummarySetting()
                    }
                    SettingsScreen(
                        user = appUiState.user,
                        selectedThemeMode = appUiState.themeMode,
                        adminAiSummaryEnabled = appUiState.adminAiSummaryEnabled,
                        isAdminAiSummaryLoading = appUiState.isAdminAiSummaryLoading,
                        isAdminAiSummarySaving = appUiState.isAdminAiSummarySaving,
                        adminAiSummaryError = appUiState.adminAiSummaryError,
                        onThemeModeSelected = appViewModel::setThemeMode,
                        onToggleAdminAiSummary = appViewModel::setAdminAiSummaryEnabled,
                        onBack = { navController.popBackStack() },
                        onLogout = { appViewModel.logout() },
                    )
                }
            }

            TdayBottomToastHost(
                toast = activeToast,
                onDismiss = { activeToast = null },
            )
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
)

@Composable
private fun TdayBottomToastHost(
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

    LaunchedEffect(toast?.id) {
        if (toast == null) return@LaunchedEffect
        kotlinx.coroutines.delay(2200)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(bottom = 20.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(animationSpec = tween(durationMillis = 180)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = 220),
                    initialOffsetY = { it / 2 },
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 160)) +
                slideOutVertically(
                    animationSpec = tween(durationMillis = 200),
                    targetOffsetY = { it / 2 },
                ),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = toastColor),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = accent.copy(alpha = if (isDark) 0.36f else 0.42f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 8.dp else 5.dp),
            ) {
                Text(
                    text = toast?.message.orEmpty(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                text = "T'Day",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "Loading native workspace...",
                style = MaterialTheme.typography.bodyMedium,
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
