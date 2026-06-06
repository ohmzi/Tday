package com.ohmz.tday.compose.feature.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.AnimRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WidgetCreateTaskActivity : AppCompatActivity() {

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
        val createTarget = WidgetCreateTarget.from(intent)
        setContent {
            WidgetCreateTaskSurface(
                createTarget = createTarget,
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
    createTarget: WidgetCreateTarget,
    widgetCreateTaskSubmitter: WidgetCreateTaskSubmitter,
    onExit: () -> Unit,
    onOpenMainApp: () -> Unit,
) {
    val appViewModel: AppViewModel = hiltViewModel()
    val appUiState by appViewModel.uiState.collectAsStateWithLifecycle()
    val todoViewModel: TodoListViewModel = hiltViewModel()
    val todoUiState by todoViewModel.uiState.collectAsStateWithLifecycle()
    val submitScope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }

    val mode = createTarget.mode

    LaunchedEffect(mode) {
        todoViewModel.load(mode = mode)
    }
    OnWidgetRouteResume {
        todoViewModel.load(mode = mode)
        appViewModel.reconnectAfterForeground()
    }
    LaunchedEffect(appUiState.loading, appUiState.isWorkspaceAvailable) {
        if (!appUiState.loading && !appUiState.isWorkspaceAvailable) {
            onOpenMainApp()
        }
    }
    BackHandler(enabled = !submitting) {
        onExit()
    }

    TdayTheme(themeMode = appUiState.themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            CreateTaskBottomSheet(
                lists = todoUiState.lists,
                defaultScheduled = createTarget.showScheduleControls,
                showScheduleControls = createTarget.showScheduleControls,
                presentImmediately = true,
                onParseTaskTitleNlp = if (createTarget.showScheduleControls) {
                    todoViewModel::parseTaskTitleNlp
                } else {
                    null
                },
                onDismiss = {
                    if (!submitting) {
                        onExit()
                    }
                },
                onCreateTask = { payload ->
                    if (!submitting) {
                        submitting = true
                        submitScope.launch {
                            when (createTarget) {
                                WidgetCreateTarget.TODAY -> {
                                    widgetCreateTaskSubmitter.submitTodayTask(payload)
                                }

                                WidgetCreateTarget.FLOATER -> {
                                    widgetCreateTaskSubmitter.submitFloaterTask(payload)
                                }
                            }
                            onExit()
                        }
                    }
                },
            )
        }
    }
}

private enum class WidgetCreateTarget(
    val mode: TodoListMode,
    val showScheduleControls: Boolean,
) {
    TODAY(TodoListMode.TODAY, true),
    FLOATER(TodoListMode.FLOATER, false);

    companion object {
        fun from(intent: Intent): WidgetCreateTarget {
            return when (intent.data?.getQueryParameter("target")?.lowercase()) {
                "floater" -> FLOATER
                else -> TODAY
            }
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
