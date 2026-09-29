package com.ohmz.tday.compose.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.MainActivity
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.applyScreenshotProtection
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.ui.TdayHaptics
import com.ohmz.tday.compose.core.ui.TdayMotionTokens
import com.ohmz.tday.compose.core.ui.tdayPressable
import com.ohmz.tday.compose.feature.app.AppViewModel
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetListType
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotWriter
import com.ohmz.tday.compose.ui.theme.TdayDimens
import com.ohmz.tday.compose.ui.theme.TdayTheme
import com.ohmz.tday.compose.ui.theme.tdayListAccentColor
import com.ohmz.tday.compose.ui.theme.tdayListIconForList
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Where a List widget gets its list: a full-screen T'Day screen listing every scheduled list and
 * floater list, reached two ways for one `appWidgetId`.
 *
 * - A tap on the widget while it has no list ([ACTION_PICK_LIST], from [pickListIntent]) — how
 *   every List widget starts, since it is placed unconfigured.
 * - The launcher's reconfigure affordance (`ACTION_APPWIDGET_CONFIGURE`), to change the list later.
 *   On Android 12+ the provider is `configuration_optional`, so the launcher does not open this on
 *   placement; older launchers ignore that flag and open it as the classic configure step, which
 *   is why the result contract below is kept.
 *
 * Picking writes the selection, seeds and paints the widget's snapshot, and closes, so the user
 * is back on the home screen with the widget already showing the list. It runs in its own task
 * (see the manifest) so closing lands on the home screen rather than on T'Day.
 */
@AndroidEntryPoint
class WidgetListPickerActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The configure contract: a launcher that opened this as a configure step must hear
        // RESULT_OK, or it treats the placement as cancelled. Set up front so every other way out
        // reads as a cancel; a tap from the widget ignores the result either way.
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        enableEdgeToEdge()
        val currentListId = WidgetListSelectionStore(applicationContext).selectionFor(appWidgetId)?.listId

        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val appUiState by appViewModel.uiState.collectAsStateWithLifecycle()
            val pickerViewModel: WidgetListPickerViewModel = hiltViewModel()
            val pickerUiState by pickerViewModel.uiState.collectAsStateWithLifecycle()

            TdayTheme(themeMode = appUiState.themeMode) {
                WidgetListPickerScreen(
                    uiState = pickerUiState,
                    currentListId = currentListId,
                    onPick = { option ->
                        pickerViewModel.selectList(appWidgetId, option) {
                            setResult(
                                RESULT_OK,
                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                            )
                            finish()
                        }
                    },
                    onClose = ::finish,
                    onOpenApp = {
                        startActivity(
                            Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            },
                        )
                        finish()
                    },
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

    companion object {
        /** A tap on an unconfigured List widget (see [pickListIntent]). */
        const val ACTION_PICK_LIST = "com.ohmz.tday.compose.widget.action.PICK_LIST"
    }
}

/** One list the picker offers: which list, of which type, and how many open tasks it holds. */
internal data class WidgetListOption(
    val id: String,
    val type: WidgetListType,
    val name: String,
    val colorKey: String?,
    val iconKey: String?,
    val openCount: Int,
)

internal data class WidgetListPickerUiState(
    val loading: Boolean = true,
    val todoLists: List<WidgetListOption> = emptyList(),
    val floaterLists: List<WidgetListOption> = emptyList(),
)

@HiltViewModel
internal class WidgetListPickerViewModel @Inject constructor(
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
            val openTodos = state.todos.filterNot { it.completed }.groupingBy { it.listId }.eachCount()
            val openFloaters = state.floaters.filterNot { it.completed }.groupingBy { it.listId }.eachCount()
            _uiState.value = WidgetListPickerUiState(
                loading = false,
                todoLists = state.lists.map { list ->
                    WidgetListOption(
                        id = list.id,
                        type = WidgetListType.TODO,
                        name = list.name,
                        colorKey = list.color,
                        iconKey = list.iconKey,
                        openCount = openTodos[list.id] ?: 0,
                    )
                },
                floaterLists = state.floaterLists.map { list ->
                    WidgetListOption(
                        id = list.id,
                        type = WidgetListType.FLOATER,
                        name = list.name,
                        colorKey = list.color,
                        iconKey = list.iconKey,
                        openCount = openFloaters[list.id] ?: 0,
                    )
                },
            )
        }
    }

    /**
     * Persists [appWidgetId]'s choice, then seeds and paints its snapshot before calling [onDone],
     * so the widget is already showing the list when the home screen comes back. The seed is
     * best-effort: the selection write is durable either way, and a failure falls back to the
     * same `LOADING` -> `WidgetHydrateWorker` path any snapshot-less widget has.
     */
    fun selectList(appWidgetId: Int, option: WidgetListOption, onDone: () -> Unit) {
        WidgetListSelectionStore(appContext).setSelection(
            appWidgetId,
            WidgetListSelection(option.id, option.type, option.name),
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

// The picker's own geometry. A Compose screen of our own, not something a launcher composites.
private val PickerArtWidth = 150.dp
private val PickerArtHeight = 125.dp
private val PickerCloseButtonSize = 44.dp
private val PickerRowHeight = 64.dp
private val PickerRowIconDiscSize = 38.dp

/** How far a row's container leans from the surface toward the list's colour — the home rows' blend, softened. */
private const val PICKER_ROW_ACCENT_WEIGHT = 0.18f

@Composable
internal fun WidgetListPickerScreen(
    uiState: WidgetListPickerUiState,
    currentListId: String?,
    onPick: (WidgetListOption) -> Unit,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            when {
                uiState.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                uiState.todoLists.isEmpty() && uiState.floaterLists.isEmpty() -> PickerEmptyState(
                    onOpenApp = onOpenApp,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = TdayDimens.ContentPaddingHorizontal,
                        end = TdayDimens.ContentPaddingHorizontal,
                        top = TdayDimens.Spacing4xl,
                        bottom = TdayDimens.Spacing4xl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(TdayDimens.ListItemSpacing),
                ) {
                    item { PickerHeader() }
                    if (uiState.todoLists.isNotEmpty()) {
                        item { PickerSectionLabel(stringResource(R.string.widget_list_picker_section_todo)) }
                        items(uiState.todoLists, key = { "todo-${it.id}" }) { option ->
                            PickerListRow(option, selected = option.id == currentListId, onClick = { onPick(option) })
                        }
                    }
                    if (uiState.floaterLists.isNotEmpty()) {
                        item { PickerSectionLabel(stringResource(R.string.widget_list_picker_section_floater)) }
                        items(uiState.floaterLists, key = { "floater-${it.id}" }) { option ->
                            PickerListRow(option, selected = option.id == currentListId, onClick = { onPick(option) })
                        }
                    }
                }
            }

            PickerCloseButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = TdayDimens.ContentPaddingHorizontal, top = TdayDimens.SpacingMd),
            )
        }
    }
}

@Composable
private fun PickerHeader() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = TdayDimens.SpacingLg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.widget_list_setup_art),
            contentDescription = null,
            modifier = Modifier.width(PickerArtWidth).height(PickerArtHeight),
        )
        Spacer(modifier = Modifier.height(TdayDimens.SpacingLg))
        Text(
            text = stringResource(R.string.widget_list_picker_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TdayDimens.SpacingSm))
        Text(
            text = stringResource(R.string.widget_list_picker_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = TdayDimens.Spacing3xl),
        )
    }
}

@Composable
private fun PickerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = TdayDimens.SpacingXs,
            top = TdayDimens.SpacingLg,
            bottom = TdayDimens.SpacingXs,
        ),
    )
}

@Composable
private fun PickerListRow(option: WidgetListOption, selected: Boolean, onClick: () -> Unit) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val accent = tdayListAccentColor(option.colorKey)
    val container = lerp(MaterialTheme.colorScheme.surfaceVariant, accent, PICKER_ROW_ACCENT_WEIGHT)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PickerRowHeight)
            .tdayPressable(interactionSource, scale = TdayMotionTokens.PressScales.Row)
            .clip(RoundedCornerShape(TdayDimens.RadiusRow))
            .background(container)
            .clickable(interactionSource = interactionSource, indication = null) {
                TdayHaptics.buttonPress(view)
                onClick()
            }
            .padding(horizontal = TdayDimens.SpacingXl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(PickerRowIconDiscSize).clip(CircleShape).background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tdayListIconForList(option.iconKey, option.name),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(TdayDimens.IconSm),
            )
        }
        Spacer(modifier = Modifier.width(TdayDimens.SpacingLg))
        Text(
            text = option.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = String.format(
                Locale.getDefault(),
                stringResource(R.string.widget_floater_tasks_count),
                option.openCount,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (selected) {
            Spacer(modifier = Modifier.width(TdayDimens.SpacingMd))
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_check),
                contentDescription = stringResource(R.string.widget_list_picker_selected),
                tint = accent,
                modifier = Modifier.size(TdayDimens.IconSm),
            )
        }
    }
}

@Composable
private fun PickerEmptyState(onOpenApp: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = TdayDimens.Spacing4xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.widget_list_setup_art),
            contentDescription = null,
            modifier = Modifier.width(PickerArtWidth).height(PickerArtHeight),
        )
        Spacer(modifier = Modifier.height(TdayDimens.SpacingLg))
        Text(
            text = stringResource(R.string.widget_list_picker_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TdayDimens.SpacingSm))
        Text(
            text = stringResource(R.string.widget_list_picker_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TdayDimens.Spacing3xl))
        Button(onClick = onOpenApp) {
            Text(stringResource(R.string.widget_today_tasks_setup_title))
        }
    }
}

@Composable
private fun PickerCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    Surface(
        onClick = {
            TdayHaptics.buttonPress(view)
            onClick()
        },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = TdayDimens.BarButtonElevation,
        modifier = modifier.size(PickerCloseButtonSize),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_x),
                contentDescription = stringResource(R.string.action_cancel),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(TdayDimens.IconSm),
            )
        }
    }
}
