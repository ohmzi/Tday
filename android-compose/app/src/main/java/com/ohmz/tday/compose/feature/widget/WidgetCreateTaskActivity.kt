package com.ohmz.tday.compose.feature.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.AnimRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ohmz.tday.compose.MainActivity
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.feature.app.AppViewModel
import com.ohmz.tday.compose.feature.todos.TodoListScreen
import com.ohmz.tday.compose.feature.todos.TodoListViewModel
import com.ohmz.tday.compose.ui.theme.TdayTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
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
) {
    val context = LocalContext.current
    val appViewModel: AppViewModel = hiltViewModel()
    val appUiState by appViewModel.uiState.collectAsStateWithLifecycle()
    val todoViewModel: TodoListViewModel = hiltViewModel()
    val todoUiState by todoViewModel.uiState.collectAsStateWithLifecycle()
    val todayTitle = stringResource(R.string.todos_title_today)
    var releaseBackdrop by remember { mutableStateOf(false) }
    val stableInitialBackdrop = todoUiState.copy(
        mode = TodoListMode.TODAY,
        title = todayTitle,
        hasHydratedSnapshot = false,
        items = emptyList(),
    )
    val displayTodoUiState = if (!releaseBackdrop) {
        stableInitialBackdrop
    } else if (todoUiState.mode == TodoListMode.TODAY) {
        todoUiState
    } else {
        todoUiState.copy(mode = TodoListMode.TODAY, title = todayTitle)
    }
    val taskDeletedToastMessage = stringResource(R.string.task_deleted_toast)

    LaunchedEffect(Unit) {
        todoViewModel.load(mode = TodoListMode.TODAY)
        delay(WIDGET_CREATE_BACKDROP_HOLD_MS)
        releaseBackdrop = true
    }
    OnWidgetRouteResume {
        todoViewModel.load(mode = TodoListMode.TODAY)
        appViewModel.reconnectAfterForeground()
    }
    LaunchedEffect(appUiState.loading, appUiState.isWorkspaceAvailable) {
        if (!appUiState.loading && !appUiState.isWorkspaceAvailable) {
            context.openMainActivity()
            onExit()
        }
    }

    TdayTheme(themeMode = appUiState.themeMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            TodoListScreen(
                uiState = displayTodoUiState,
                onBack = onExit,
                onRefresh = todoViewModel::refresh,
                onSummarize = todoViewModel::summarizeCurrentMode,
                onDismissSummaryConnectivityError = todoViewModel::dismissSummaryConnectivityError,
                onAddTask = { payload ->
                    widgetCreateTaskSubmitter.submitTodayTask(payload)
                    onExit()
                },
                onParseTaskTitleNlp = todoViewModel::parseTaskTitleNlp,
                onUpdateTask = todoViewModel::updateTask,
                onMoveTask = todoViewModel::moveTask,
                onComplete = todoViewModel::toggleComplete,
                onDelete = { todo ->
                    todoViewModel.delete(todo) {
                        Toast.makeText(
                            context,
                            taskDeletedToastMessage,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onUpdateListSettings = { listId, name, color, iconKey ->
                    todoViewModel.updateListSettings(
                        listId = listId,
                        name = name,
                        color = color,
                        iconKey = iconKey,
                    )
                },
                onDeleteList = { listId ->
                    todoViewModel.deleteList(listId = listId, onOptimisticDelete = onExit)
                },
                onCreateList = todoViewModel::createList,
                showCreateTaskButton = false,
                openCreateTaskOnStart = true,
                exitToLauncherOnBack = true,
                exitOnCreateTaskSheetDismiss = true,
                pullRefreshEnabled = !appUiState.isLocalMode,
                summaryAvailable = !appUiState.isLocalMode && !appUiState.isOffline,
            )
        }
    }
}

private const val WIDGET_CREATE_BACKDROP_HOLD_MS = 520L

private fun Context.openMainActivity() {
    startActivity(
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
    )
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
