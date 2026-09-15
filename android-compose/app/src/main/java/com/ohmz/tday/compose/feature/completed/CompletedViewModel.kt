package com.ohmz.tday.compose.feature.completed

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.cache.FirstAnswerSignal
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.completed.CompletedRepository
import com.ohmz.tday.compose.core.data.list.FloaterListRepository
import com.ohmz.tday.compose.core.data.list.ListRepository
import com.ohmz.tday.compose.core.data.sync.SyncManager
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.notification.TaskReminderScheduler
import com.ohmz.tday.compose.core.ui.SnackbarManager
import com.ohmz.tday.compose.core.ui.userFacingMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompletedUiState(
    val isLoading: Boolean = false,
    // The first-load/refresh distinction, mirrored from `TodoListUiState`. This
    // screen has no pull-to-refresh of its own, so the flash is rarer here than
    // on the Anytime home -- but `showEmptyState` was the same
    // `items.isEmpty() && !isLoading` gate copy-pasted, and one gate is fixed
    // everywhere it was copied or nowhere. See [feedAnswer].
    val hasHydratedSnapshot: Boolean = false,
    val firstAnswerLanded: Boolean = false,
    // Todos and floaters merged into one browsable timeline; CompletedItem.isFloater
    // tells CompletedScreen which of the two it is rendering/acting on.
    val items: List<CompletedItem> = emptyList(),
    val lists: List<ListSummary> = emptyList(),
    // Floater lists are a separate namespace from `lists` (scheduled-task lists) —
    // needed to resolve a completed floater's list icon and to offer the right
    // list choices when editing one.
    val floaterLists: List<ListSummary> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class CompletedViewModel @Inject constructor(
    private val completedRepository: CompletedRepository,
    private val listRepository: ListRepository,
    private val floaterListRepository: FloaterListRepository,
    private val syncManager: SyncManager,
    private val cacheManager: OfflineCacheManager,
    private val firstAnswerSignal: FirstAnswerSignal,
    private val reminderScheduler: TaskReminderScheduler,
    private val snackbarManager: SnackbarManager,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        runCatching {
            CompletedUiState(
                isLoading = false,
                // This screen's cache read happens here, in the field
                // initializer, rather than in a `load()` -- so this is the line
                // that corresponds to `TodoListViewModel`'s
                // `hasHydratedSnapshot = true` inside `hydrateFromCache`.
                hasHydratedSnapshot = true,
                firstAnswerLanded = firstAnswerSignal.hasLanded(),
                items = mergedCompletedItems(
                    completedRepository.fetchCompletedItemsSnapshot(),
                    completedRepository.fetchCompletedFloaterItemsSnapshot(),
                ),
                lists = listRepository.fetchListsSnapshot(),
                floaterLists = floaterListRepository.fetchListsSnapshot(),
                errorMessage = null,
            )
        }.getOrElse {
            // Both terms again, for `TodoListViewModel.hydrateFromCache`'s stated
            // reason: a cache read that THREW still ended, and the screen may not
            // wait on it twice. `CompletedUiState()` alone defaults
            // `hasHydratedSnapshot` to false, and nothing re-hydrates this screen
            // until a cache version bump -- so the row placeholder that
            // AWAITING_FIRST draws would have been permanent, on a screen that
            // used to show its empty state here.
            //
            // `firstAnswerLanded` still decides between the scene and the
            // placeholder, so a device whose Room read is genuinely broken holds
            // the placeholder rather than claiming an empty history it could not
            // read. Local Mode is the exception by design: that half of
            // [FirstAnswerSignal.hasLanded] is a prefs read and is unaffected by
            // whatever broke above.
            CompletedUiState(
                hasHydratedSnapshot = true,
                firstAnswerLanded = firstAnswerSignal.hasLanded(),
            )
        },
    )
    val uiState: StateFlow<CompletedUiState> = _uiState.asStateFlow()
    private var hasLoadedScreen = false

    init {
        observeCacheChanges()
        observeFirstAnswer()
    }

    /**
     * The one path by which a first answer can land without any cached row
     * moving: a fresh install signing in to an account with no completed history
     * at all. `cacheDataVersion` only advances on `hasUiDataChanges`, so that
     * sync bumps nothing this screen otherwise watches. See
     * [FirstAnswerSignal.version].
     */
    private fun observeFirstAnswer() {
        viewModelScope.launch {
            firstAnswerSignal.version.collect {
                val landed = firstAnswerSignal.hasLanded()
                _uiState.update { current ->
                    if (current.firstAnswerLanded == landed) current
                    else current.copy(firstAnswerLanded = landed)
                }
            }
        }
    }

    private fun observeCacheChanges() {
        viewModelScope.launch {
            cacheManager.cacheDataVersion
                .collect {
                    if (!hasLoadedScreen) return@collect
                    hydrateFromCache()
                }
        }
    }

    fun load() {
        hasLoadedScreen = true
        hydrateFromCache()
    }

    fun refresh(userInitiated: Boolean = false) {
        hasLoadedScreen = true
        loadInternal(forceSync = true, showLoading = true, userInitiated = userInitiated)
    }

    private fun hydrateFromCache() {
        runCatching {
            CompletedHydration(
                items = mergedCompletedItems(
                    completedRepository.fetchCompletedItemsSnapshot(),
                    completedRepository.fetchCompletedFloaterItemsSnapshot(),
                ),
                lists = listRepository.fetchListsSnapshot(),
                floaterLists = floaterListRepository.fetchListsSnapshot(),
            )
        }.onSuccess { (items, lists, floaterLists) ->
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    hasHydratedSnapshot = true,
                    firstAnswerLanded = firstAnswerSignal.hasLanded(),
                    items = if (current.items == items) current.items else items,
                    lists = if (current.lists == lists) current.lists else lists,
                    floaterLists = if (current.floaterLists == floaterLists) current.floaterLists else floaterLists,
                    errorMessage = null,
                )
            }
        }.onFailure {
            // The same rule as the initializer above and as
            // `TodoListViewModel.hydrateFromCache`: a read that threw still
            // ANSWERED, so the screen stops waiting on it. Without this leg the
            // `load()` that follows a failed initializer read is a second chance
            // that changes nothing, and the placeholder stays up for the life of
            // the process.
            _uiState.update { current ->
                current.copy(
                    hasHydratedSnapshot = true,
                    firstAnswerLanded = firstAnswerSignal.hasLanded(),
                )
            }
        }
    }

    private fun loadInternal(forceSync: Boolean, showLoading: Boolean, userInitiated: Boolean = false) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { current ->
                    if (current.isLoading && current.errorMessage == null) current
                    else current.copy(isLoading = true, errorMessage = null)
                }
            } else {
                _uiState.update { current ->
                    if (current.errorMessage == null) current else current.copy(errorMessage = null)
                }
            }
            runCatching {
                if (forceSync) {
                    syncManager.syncCachedData(
                        force = true,
                        replayPendingMutations = false,
                        userInitiated = userInitiated,
                        connectionProbeTimeoutMs = SyncManager.USER_REFRESH_CONNECTION_TIMEOUT_MS,
                    )
                        .onFailure { /* fall back to local cache */ }
                }
                CompletedHydration(
                    items = mergedCompletedItems(
                        completedRepository.fetchCompletedItems(),
                        completedRepository.fetchCompletedFloaterItems(),
                    ),
                    lists = listRepository.fetchLists(),
                    floaterLists = floaterListRepository.fetchLists(),
                )
            }.onSuccess { (items, lists, floaterLists) ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        items = if (current.items == items) current.items else items,
                        lists = if (current.lists == lists) current.lists else lists,
                        floaterLists = if (current.floaterLists == floaterLists) current.floaterLists else floaterLists,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = error.userFacingMessage(appContext, R.string.error_load_failed),
                    )
                }
            }
        }
    }

    fun delete(item: CompletedItem) {
        viewModelScope.launch {
            runCatching {
                if (item.isFloater) {
                    completedRepository.deleteCompletedFloater(item)
                } else {
                    completedRepository.deleteCompletedTodo(item)
                }
            }
                .onSuccess {
                    // Completed-history deletes stay immediate (no staged undo
                    // window); the toast is shown here so the screen does not
                    // depend on navigation-layer wiring.
                    snackbarManager.showSuccess(
                        appContext.getString(R.string.task_deleted_toast),
                    )
                    loadInternal(forceSync = false, showLoading = false)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.userFacingMessage(appContext, R.string.error_delete_task_failed))
                    }
                }
        }
    }

    fun uncomplete(item: CompletedItem) {
        viewModelScope.launch {
            if (item.isFloater) {
                runCatching { completedRepository.uncompleteFloater(item) }
                    .onSuccess { outcome ->
                        // Only the "landed in a recreated list" case gets a toast —
                        // an ordinary restore is silent, same as a todo (see the
                        // unified toast policy: restore/edit success is not
                        // announced). Recreating a whole list the user thought
                        // they'd deleted is the one outcome here worth surfacing.
                        if (outcome.listRecreated && !outcome.listName.isNullOrBlank()) {
                            snackbarManager.showInfo(
                                appContext.getString(
                                    R.string.completed_floater_list_recreated_toast,
                                    outcome.listName,
                                ),
                            )
                        }
                        loadInternal(forceSync = false, showLoading = false)
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(errorMessage = error.userFacingMessage(appContext, R.string.error_restore_task_failed))
                        }
                    }
            } else {
                runCatching { completedRepository.uncomplete(item) }
                    .onSuccess {
                        rescheduleReminders()
                        loadInternal(forceSync = false, showLoading = false)
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(errorMessage = error.userFacingMessage(appContext, R.string.error_restore_task_failed))
                        }
                    }
            }
        }
    }

    fun update(item: CompletedItem, payload: CreateTaskPayload) {
        viewModelScope.launch {
            runCatching {
                if (item.isFloater) {
                    completedRepository.updateCompletedFloater(item, payload)
                } else {
                    completedRepository.updateCompletedTodo(item, payload)
                }
            }
                .onSuccess { loadInternal(forceSync = false, showLoading = false) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.userFacingMessage(appContext, R.string.error_update_task_failed))
                    }
                }
        }
    }

    private fun rescheduleReminders() {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { reminderScheduler.rescheduleAll() }
        }
    }
}

/** One fetch/hydrate round's worth of [CompletedUiState] source data. */
private data class CompletedHydration(
    val items: List<CompletedItem>,
    val lists: List<ListSummary>,
    val floaterLists: List<ListSummary>,
)

/**
 * Todos and floaters share one browsable timeline (CompletedScreen groups by
 * completed date regardless of type), sorted newest-first so a stable merge
 * order here does not depend on that downstream re-sort.
 */
private fun mergedCompletedItems(
    todoItems: List<CompletedItem>,
    floaterItems: List<CompletedItem>,
): List<CompletedItem> {
    if (floaterItems.isEmpty()) return todoItems
    if (todoItems.isEmpty()) return floaterItems
    return (todoItems + floaterItems).sortedByDescending {
        it.completedAt ?: it.due ?: java.time.Instant.EPOCH
    }
}
