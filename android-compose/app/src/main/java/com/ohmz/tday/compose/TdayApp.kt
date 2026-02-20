package com.ohmz.tday.compose

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                            HomeScreen(
                                uiState = homeUiState,
                                onRefresh = homeViewModel::refresh,
                                onOpenToday = { navController.navigate(AppRoute.TodayTodos.route) },
                                onOpenScheduled = { navController.navigate(AppRoute.ScheduledTodos.route) },
                                onOpenAll = { navController.navigate(AppRoute.AllTodos.route) },
                                onOpenPriority = { navController.navigate(AppRoute.PriorityTodos.route) },
                                onOpenCompleted = { navController.navigate(AppRoute.Completed.route) },
                                onOpenCalendar = { navController.navigate(AppRoute.Calendar.route) },
                                onOpenSettings = { navController.navigate(AppRoute.Settings.route) },
                                onOpenList = { id, name ->
                                    navController.navigate(AppRoute.ListTodos.create(id, name))
                                },
                                onCreateTask = { title, listId ->
                                    homeViewModel.createTask(title, listId)
                                },
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
                                onOpenList = { _, _ -> },
                                onCreateTask = { _, _ -> },
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
                )
            }

            composable(AppRoute.ScheduledTodos.route) {
                TodosRoute(
                    mode = TodoListMode.SCHEDULED,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(AppRoute.AllTodos.route) {
                TodosRoute(
                    mode = TodoListMode.ALL,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(AppRoute.PriorityTodos.route) {
                TodosRoute(
                    mode = TodoListMode.PRIORITY,
                    onBack = { navController.popBackStack() },
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
                )
            }

            composable(AppRoute.Completed.route) {
                val viewModel: CompletedViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { viewModel.load() }
                CompletedScreen(
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onRefresh = viewModel::refresh,
                    onUncomplete = viewModel::uncomplete,
                )
            }

            composable(AppRoute.Notes.route) {
                val viewModel: NotesViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { viewModel.load() }
                NotesScreen(
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onRefresh = viewModel::refresh,
                    onCreate = viewModel::create,
                    onDelete = viewModel::delete,
                )
            }

            composable(AppRoute.Calendar.route) {
                CalendarScreen(
                    onBack = { navController.popBackStack() },
                    isRefreshing = appUiState.isManualSyncing,
                    onRefresh = appViewModel::syncNow,
                    onOpenScheduled = {
                        navController.navigate(AppRoute.ScheduledTodos.route)
                    },
                )
            }

            composable(AppRoute.Settings.route) {
                SettingsScreen(
                    user = appUiState.user,
                    selectedThemeMode = appUiState.themeMode,
                    onThemeModeSelected = appViewModel::setThemeMode,
                    onBack = { navController.popBackStack() },
                    onLogout = { appViewModel.logout() },
                )
            }
        }
    }
}

@Composable
private fun TodosRoute(
    mode: TodoListMode,
    onBack: () -> Unit,
    listId: String? = null,
    listName: String? = null,
) {
    val viewModel: TodoListViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(mode, listId, listName) {
        viewModel.load(mode = mode, listId = listId, listName = listName)
    }

    TodoListScreen(
        uiState = uiState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onAddTask = viewModel::addTask,
        onComplete = viewModel::toggleComplete,
        onDelete = viewModel::delete,
        onTogglePin = viewModel::togglePin,
    )
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                text = "Tday",
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
