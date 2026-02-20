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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.core.navigation.AppRoute
import com.ohmz.tday.compose.feature.app.AppViewModel
import com.ohmz.tday.compose.feature.auth.AuthScreen
import com.ohmz.tday.compose.feature.auth.AuthViewModel
import com.ohmz.tday.compose.feature.auth.RegisterScreen
import com.ohmz.tday.compose.feature.calendar.CalendarScreen
import com.ohmz.tday.compose.feature.completed.CompletedScreen
import com.ohmz.tday.compose.feature.completed.CompletedViewModel
import com.ohmz.tday.compose.feature.home.HomeScreen
import com.ohmz.tday.compose.feature.home.HomeViewModel
import com.ohmz.tday.compose.feature.notes.NotesScreen
import com.ohmz.tday.compose.feature.notes.NotesViewModel
import com.ohmz.tday.compose.feature.server.ServerSetupScreen
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
        appUiState.requiresServerSetup,
        currentRoute,
    ) {
        if (appUiState.loading) return@LaunchedEffect

        if (appUiState.requiresServerSetup) {
            if (currentRoute != AppRoute.ServerSetup.route) {
                navController.navigate(AppRoute.ServerSetup.route) {
                    popUpTo(AppRoute.Splash.route) { inclusive = true }
                    launchSingleTop = true
                }
            }
            return@LaunchedEffect
        }

        if (appUiState.authenticated) {
            val unauthenticatedRoutes = setOf(
                AppRoute.Splash.route,
                AppRoute.Login.route,
                AppRoute.Register.route,
                AppRoute.ServerSetup.route,
            )
            if (currentRoute in unauthenticatedRoutes) {
                navController.navigate(AppRoute.Home.route) {
                    when (currentRoute) {
                        AppRoute.Splash.route -> popUpTo(AppRoute.Splash.route) { inclusive = true }
                        AppRoute.Login.route -> popUpTo(AppRoute.Login.route) { inclusive = true }
                        AppRoute.Register.route -> popUpTo(AppRoute.Register.route) { inclusive = true }
                        AppRoute.ServerSetup.route -> popUpTo(AppRoute.ServerSetup.route) { inclusive = true }
                    }
                    launchSingleTop = true
                }
            }
            return@LaunchedEffect
        }

        val authRoutes = setOf(AppRoute.Login.route, AppRoute.Register.route)
        if (currentRoute !in authRoutes) {
            navController.navigate(AppRoute.Login.route) {
                when (currentRoute) {
                    AppRoute.Splash.route -> popUpTo(AppRoute.Splash.route) { inclusive = true }
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
                ServerSetupScreen(
                    errorMessage = appUiState.error,
                    onSave = { rawUrl ->
                        appViewModel.saveServerUrl(rawUrl) {
                            appViewModel.refreshSession()
                        }
                    },
                )
            }

            composable(AppRoute.Login.route) {
                val authViewModel: AuthViewModel = hiltViewModel()
                val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
                AuthScreen(
                    uiState = authUiState,
                    onLogin = { email, password ->
                        authViewModel.login(email, password) {
                            appViewModel.refreshSession()
                        }
                    },
                    onNavigateRegister = {
                        authViewModel.clearStatus()
                        navController.navigate(AppRoute.Register.route)
                    },
                )
            }

            composable(AppRoute.Register.route) {
                val authViewModel: AuthViewModel = hiltViewModel()
                val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
                RegisterScreen(
                    uiState = authUiState,
                    onBack = { navController.popBackStack() },
                    onRegister = { firstName, lastName, email, password, confirmPassword ->
                        if (password != confirmPassword) {
                            authViewModel.setError("Passwords do not match")
                        } else {
                            authViewModel.register(firstName, lastName, email, password) {
                                navController.popBackStack()
                            }
                        }
                    },
                )
            }

            composable(AppRoute.Home.route) {
                val homeViewModel: HomeViewModel = hiltViewModel()
                val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    uiState = homeUiState,
                    onRefresh = homeViewModel::refresh,
                    onOpenToday = { navController.navigate(AppRoute.TodayTodos.route) },
                    onOpenScheduled = { navController.navigate(AppRoute.ScheduledTodos.route) },
                    onOpenAll = { navController.navigate(AppRoute.AllTodos.route) },
                    onOpenFlagged = { navController.navigate(AppRoute.FlaggedTodos.route) },
                    onOpenCompleted = { navController.navigate(AppRoute.Completed.route) },
                    onOpenNotes = { navController.navigate(AppRoute.Notes.route) },
                    onOpenCalendar = { navController.navigate(AppRoute.Calendar.route) },
                    onOpenSettings = { navController.navigate(AppRoute.Settings.route) },
                    onOpenProject = { id, name ->
                        navController.navigate(AppRoute.ProjectTodos.create(id, name))
                    },
                    onCreateProject = homeViewModel::createList,
                )
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

            composable(AppRoute.FlaggedTodos.route) {
                TodosRoute(
                    mode = TodoListMode.FLAGGED,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = AppRoute.ProjectTodos.route,
                arguments = listOf(
                    navArgument("projectId") { type = NavType.StringType },
                    navArgument("projectName") { type = NavType.StringType },
                ),
            ) { entry ->
                val projectId = entry.arguments?.getString("projectId").orEmpty()
                val projectName = Uri.decode(entry.arguments?.getString("projectName").orEmpty())
                TodosRoute(
                    mode = TodoListMode.PROJECT,
                    projectId = projectId,
                    projectName = projectName,
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
                    onRefresh = viewModel::load,
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
                    onRefresh = viewModel::load,
                    onCreate = viewModel::create,
                    onDelete = viewModel::delete,
                )
            }

            composable(AppRoute.Calendar.route) {
                CalendarScreen(
                    onBack = { navController.popBackStack() },
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
    projectId: String? = null,
    projectName: String? = null,
) {
    val viewModel: TodoListViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(mode, projectId, projectName) {
        viewModel.load(mode = mode, projectId = projectId, projectName = projectName)
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
