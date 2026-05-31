package com.ohmz.tday.compose.feature.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.AnimRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ohmz.tday.compose.MainActivity
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.feature.app.AppViewModel
import com.ohmz.tday.compose.feature.todos.TodoListViewModel
import com.ohmz.tday.compose.ui.component.CreateTaskBottomSheet
import com.ohmz.tday.compose.ui.theme.TdayTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WidgetCreateTaskActivity : ComponentActivity() {

    @Inject
    lateinit var widgetCreateTaskSubmitter: WidgetCreateTaskSubmitter

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Tday_WidgetCreate)
        super.onCreate(savedInstanceState)
        applyTransition(
            enter = R.anim.widget_create_enter,
            exit = R.anim.widget_create_hold,
        )
        enableEdgeToEdge()
        setContent {
            WidgetCreateTaskSurface(
                widgetCreateTaskSubmitter = widgetCreateTaskSubmitter,
                onExit = ::exitToLauncher,
                onOpenMainApp = ::openMainApp,
            )
        }
    }

    private fun exitToLauncher() {
        moveTaskToBack(true)
        finish()
        applyTransition(
            enter = R.anim.widget_create_hold,
            exit = R.anim.widget_create_exit,
        )
    }

    private fun openMainApp() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
        )
        finish()
        applyTransition(
            enter = R.anim.widget_create_hold,
            exit = R.anim.widget_create_exit,
        )
    }

    @Suppress("DEPRECATION")
    private fun applyTransition(
        @AnimRes enter: Int,
        @AnimRes exit: Int,
    ) {
        overridePendingTransition(enter, exit)
    }
}

@Composable
private fun WidgetCreateTaskSurface(
    widgetCreateTaskSubmitter: WidgetCreateTaskSubmitter,
    onExit: () -> Unit,
    onOpenMainApp: () -> Unit,
) {
    val appViewModel: AppViewModel = hiltViewModel()
    val appUiState by appViewModel.uiState.collectAsStateWithLifecycle()
    val todoViewModel: TodoListViewModel = hiltViewModel()
    val todoUiState by todoViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        todoViewModel.load(mode = TodoListMode.TODAY)
    }
    OnWidgetRouteResume {
        todoViewModel.load(mode = TodoListMode.TODAY)
        appViewModel.reconnectAfterForeground()
    }
    LaunchedEffect(appUiState.loading, appUiState.isWorkspaceAvailable) {
        if (!appUiState.loading && !appUiState.isWorkspaceAvailable) {
            onOpenMainApp()
        }
    }

    TdayTheme(themeMode = appUiState.themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            CreateTaskBottomSheet(
                lists = todoUiState.lists,
                autoFocusTitle = true,
                presentImmediately = true,
                onParseTaskTitleNlp = todoViewModel::parseTaskTitleNlp,
                onDismiss = onExit,
                onCreateTask = { payload ->
                    widgetCreateTaskSubmitter.submitTodayTask(payload)
                    onExit()
                },
            )
        }
    }
}

@Composable
private fun OnWidgetRouteResume(action: () -> Unit) {
    val currentAction by rememberUpdatedState(action)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var hasPaused = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
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
