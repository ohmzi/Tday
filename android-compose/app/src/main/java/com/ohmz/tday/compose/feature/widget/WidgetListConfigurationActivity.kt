package com.ohmz.tday.compose.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.CachedFloaterListRecord
import com.ohmz.tday.compose.core.data.CachedListRecord
import com.ohmz.tday.compose.core.data.applyScreenshotProtection
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.feature.app.AppViewModel
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetListType
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotWriter
import com.ohmz.tday.compose.ui.theme.TdayTheme
import com.ohmz.tday.compose.ui.theme.tdayListAccentColorOrNull
import com.ohmz.tday.compose.ui.theme.tdayListIconForKey
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The `android:configure` target for every [ListTasksWidget] receiver (widgets v3) — this app's
 * first `ACTION_APPWIDGET_CONFIGURE` activity; Today/Floater have no configuration precedent to
 * extend. The launcher starts this for a NEW placement (before the widget's first `onUpdate`) and
 * again for an EXISTING one when the user chooses "Edit"/reconfigure — both paths land here
 * unchanged, since [onCreate] always re-reads whatever is already stored for [appWidgetId] to
 * preselect it, rather than assuming a fresh instance.
 *
 * Unlike a widget's own render path, this is a normal foreground `Activity` (`@AndroidEntryPoint`,
 * exactly like [WidgetCreateTaskActivity]) — it has full Hilt/Room access, so it can read the list
 * catalog straight off [OfflineCacheManager] and, once the user picks, write and seed that
 * instance's snapshot SYNCHRONOUSLY before returning `RESULT_OK`. That is deliberate: it is what
 * lets a freshly placed widget skip the `LOADING` state entirely instead of waiting on the next
 * unrelated cache write to seed it.
 */
@AndroidEntryPoint
class WidgetListConfigurationActivity : AppCompatActivity() {

    // No @Inject lateinit var fields: every dependency this screen needs (the cache, the
    // snapshot writer, the refresher) is constructor-injected into WidgetListConfigurationViewModel
    // instead, and reached through it — see that class below.
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Tday_WidgetCreate)
        super.onCreate(savedInstanceState)

        // Standard AppWidgetHost contract: if the user backs out (or this finishes some other
        // way) without an explicit RESULT_OK below, the host must treat it as cancelled and never
        // place the widget.
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        enableEdgeToEdge()
        val currentSelection = WidgetListSelectionStore(applicationContext).selectionFor(appWidgetId)

        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val appUiState by appViewModel.uiState.collectAsStateWithLifecycle()
            val pickerViewModel: WidgetListConfigurationViewModel = hiltViewModel()
            val pickerUiState by pickerViewModel.uiState.collectAsStateWithLifecycle()

            TdayTheme(themeMode = appUiState.themeMode) {
                WidgetListPickerScreen(
                    uiState = pickerUiState,
                    currentListId = currentSelection?.listId,
                    onPick = { listId, listType, listName ->
                        pickerViewModel.selectList(appWidgetId, listId, listType, listName) {
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                            finish()
                        }
                    },
                    onCancel = { finish() },
                )
            }
        }
    }

    // This screen shows list NAMES, which — like the create-task sheet — count as task content
    // for the screenshot-protection setting.
    override fun onStart() {
        super.onStart()
        applyScreenshotProtection()
    }
}

@HiltViewModel
internal class WidgetListConfigurationViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val offlineCacheManager: OfflineCacheManager,
    private val widgetSnapshotWriter: WidgetSnapshotWriter,
    private val widgetRefresher: WidgetRefresher,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WidgetListPickerUiState())
    val uiState: StateFlow<WidgetListPickerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val state = offlineCacheManager.loadOfflineState()
            _uiState.value = WidgetListPickerUiState(
                loading = false,
                todoLists = state.lists,
                floaterLists = state.floaterLists,
            )
        }
    }

    /**
     * Persists [appWidgetId]'s choice, then seeds and paints its snapshot before calling
     * [onDone] — the Activity finishes (with `RESULT_OK`) only after that, so a freshly placed
     * widget never has to wait on an unrelated cache write to leave `LOADING`. The seed/paint
     * step is best-effort: the selection write below is durable either way, and a failure here
     * just falls back to the same `LOADING` -> `WidgetHydrateWorker` path any other cold
     * snapshot-less widget already has.
     */
    fun selectList(appWidgetId: Int, listId: String, listType: WidgetListType, listName: String, onDone: () -> Unit) {
        WidgetListSelectionStore(appContext).setSelection(
            appWidgetId,
            WidgetListSelection(listId, listType, listName),
        )
        viewModelScope.launch {
            runCatching {
                val state = offlineCacheManager.loadOfflineState()
                widgetSnapshotWriter.write(state)
                widgetRefresher.refreshNow(firstAppWidgetId = appWidgetId)
            }
            onDone()
        }
    }
}

internal data class WidgetListPickerUiState(
    val loading: Boolean = true,
    val todoLists: List<CachedListRecord> = emptyList(),
    val floaterLists: List<CachedFloaterListRecord> = emptyList(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetListPickerScreen(
    uiState: WidgetListPickerUiState,
    currentListId: String?,
    onPick: (listId: String, listType: WidgetListType, listName: String) -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_list_picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_x),
                            contentDescription = stringResource(R.string.action_cancel),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            uiState.todoLists.isEmpty() && uiState.floaterLists.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.widget_list_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (uiState.todoLists.isNotEmpty()) {
                    item { WidgetListPickerSectionHeader(stringResource(R.string.widget_list_picker_section_todo)) }
                    items(uiState.todoLists, key = { "todo-${it.id}" }) { list ->
                        WidgetListPickerRow(
                            name = list.name,
                            color = list.color,
                            iconKey = list.iconKey,
                            isShared = list.isShared,
                            selected = list.id == currentListId,
                            onClick = { onPick(list.id, WidgetListType.TODO, list.name) },
                        )
                    }
                }
                if (uiState.floaterLists.isNotEmpty()) {
                    item { WidgetListPickerSectionHeader(stringResource(R.string.widget_list_picker_section_floater)) }
                    items(uiState.floaterLists, key = { "floater-${it.id}" }) { list ->
                        WidgetListPickerRow(
                            name = list.name,
                            color = list.color,
                            iconKey = list.iconKey,
                            isShared = list.isShared,
                            selected = list.id == currentListId,
                            onClick = { onPick(list.id, WidgetListType.FLOATER, list.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetListPickerSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetListPickerRow(
    name: String,
    color: String?,
    iconKey: String?,
    isShared: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = tdayListAccentColorOrNull(color) ?: MaterialTheme.colorScheme.primary
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.16f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = tdayListIconForKey(iconKey),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        headlineContent = { Text(name) },
        supportingContent = if (isShared) {
            { Text(stringResource(R.string.widget_list_picker_shared_label)) }
        } else {
            null
        },
        trailingContent = if (selected) {
            {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_check),
                    contentDescription = stringResource(R.string.widget_list_picker_selected),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        },
    )
}
