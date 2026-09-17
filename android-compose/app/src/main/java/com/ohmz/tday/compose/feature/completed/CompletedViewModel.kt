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
import com.ohmz.tday.compose.core.navigation.CompletedScope
import com.ohmz.tday.compose.core.notification.TaskReminderScheduler
import com.ohmz.tday.compose.core.ui.SnackbarManager
import com.ohmz.tday.compose.core.ui.userFacingMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    // The completion history's two tabs, kept apart rather than merged into one
    // timeline: the screen renders one of them at a time and each tab's rows, its
    // count and its empty state all come from its own list. The two are fetched
    // separately already (they are two fields of the offline cache), so nothing
    // here costs a second round trip. CompletedItem.isFloater still says which of
    // the two a row is, and every write path still routes on it.
    val todoItems: List<CompletedItem> = emptyList(),
    val floaterItems: List<CompletedItem> = emptyList(),
    val lists: List<ListSummary> = emptyList(),
    // Floater lists are a separate namespace from `lists` (scheduled-task lists) —
    // needed to resolve a completed floater's list icon and to offer the right
    // list choices when editing one.
    val floaterLists: List<ListSummary> = emptyList(),
    val errorMessage: String? = null,
) {
    /** One tab's rows. The tab is the only thing that decides which list is drawn. */
    fun itemsFor(scope: CompletedScope): List<CompletedItem> = when (scope) {
        CompletedScope.Tasks -> todoItems
        CompletedScope.Floater -> floaterItems
    }
}

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
                todoItems = completedRepository.fetchCompletedItemsSnapshot(),
                floaterItems = completedRepository.fetchCompletedFloaterItemsSnapshot(),
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
        viewModelScope.launch { hydrateFromCache() }
    }

    fun refresh(userInitiated: Boolean = false) {
        hasLoadedScreen = true
        loadInternal(forceSync = true, showLoading = true, userInitiated = userInitiated)
    }

    /**
     * Re-reads this screen's four lists from the cache.
     *
     * Suspending and on [Dispatchers.IO], mirroring [loadInternal]'s own `CompletedHydration`
     * read, and for a reason this screen found the hard way: this used to run inline on the
     * caller's dispatcher, and every restore used to call it through `loadInternal` — so the
     * cost was hidden behind a network round trip that dominated the frame anyway. Once the
     * restore path stopped syncing (see [uncomplete]) this became the whole of the work, and
     * four blocking Room reads on the main thread is exactly the jank this change exists to
     * remove. The two `*Snapshot` accessors are the only pair that can also be reached from a
     * widget's non-suspending context, which is why they exist at all; nothing here needs them.
     */
    private suspend fun hydrateFromCache() {
        withContext(Dispatchers.IO) {
            runCatching {
                CompletedHydration(
                    todoItems = completedRepository.fetchCompletedItemsSnapshot(),
                    floaterItems = completedRepository.fetchCompletedFloaterItemsSnapshot(),
                    lists = listRepository.fetchListsSnapshot(),
                    floaterLists = floaterListRepository.fetchListsSnapshot(),
                )
            }.onSuccess { (todoItems, floaterItems, lists, floaterLists) ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        hasHydratedSnapshot = true,
                        firstAnswerLanded = firstAnswerSignal.hasLanded(),
                        todoItems = if (current.todoItems == todoItems) current.todoItems else todoItems,
                        floaterItems = if (current.floaterItems == floaterItems) {
                            current.floaterItems
                        } else {
                            floaterItems
                        },
                        lists = if (current.lists == lists) current.lists else lists,
                        floaterLists = if (current.floaterLists == floaterLists) {
                            current.floaterLists
                        } else {
                            floaterLists
                        },
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
                    todoItems = completedRepository.fetchCompletedItems(),
                    floaterItems = completedRepository.fetchCompletedFloaterItems(),
                    lists = listRepository.fetchLists(),
                    floaterLists = floaterListRepository.fetchLists(),
                )
            }.onSuccess { (todoItems, floaterItems, lists, floaterLists) ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        todoItems = if (current.todoItems == todoItems) current.todoItems else todoItems,
                        floaterItems = if (current.floaterItems == floaterItems) {
                            current.floaterItems
                        } else {
                            floaterItems
                        },
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

    /**
     * Restores a completed item.
     *
     * Neither branch syncs on the way out, and that is the point. Both repositories
     * apply the restore to the cache themselves before they touch the network, so by
     * the time either returns the local answer is already what the screen should draw —
     * re-reading it is all that is left to do, and it costs no round trip.
     *
     * This used to end both branches in `loadInternal(forceSync = false, ...)`, which
     * runs a full [SyncManager.syncCachedData] — seven parallel GETs per tap, with no
     * throttle at all on the unforced path. Restoring a handful of rows was therefore a
     * burst of dozens of requests, enough to trip the backend's own limiter and surface
     * to the user as a rate-limit error; worse, each of those GETs took its snapshot
     * before the tap's write had reached the server, so a merge landing afterwards wrote
     * the restored row straight back over the removal. See `mergeRemoteWithLocal`'s
     * `pendingUncompletedTodoIds` for the other half of that: the row is now protected
     * for exactly as long as its mutation is unacknowledged, which is the window a sync
     * started before the tap can still land in.
     *
     * Nothing is lost by dropping the sync. The server's own `completed` event comes
     * back to this device over the realtime socket and `AppViewModel` coalesces it into
     * a sync ~400 ms later, so the two clients converge on the same state anyway — and
     * the write itself is still durable either way: a restore whose PATCH fails leaves
     * its `UNCOMPLETE_TODO` queued, and the next sync replays it.
     */
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
                        hydrateFromCache()
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(errorMessage = error.userFacingMessage(appContext, R.string.error_restore_task_failed))
                        }
                        // The floater path has no local-only half — the row stays on this
                        // screen until the server answers — so this re-read changes nothing
                        // about the row and everything about the message: `hydrateFromCache`
                        // clears `errorMessage`, which is what makes the set above a notice
                        // that expires rather than one that sticks and blocks every later
                        // refresh (see `loadInternal`'s `showLoading = false` branch).
                        hydrateFromCache()
                    }
            } else {
                runCatching { completedRepository.uncomplete(item) }
                    .onSuccess {
                        rescheduleReminders()
                        hydrateFromCache()
                    }
                    .onFailure {
                        // Deliberately quiet, and it is the queue that makes that honest
                        // rather than optimistic. `uncomplete` writes the local half —
                        // timeline row pending, completion record pruned, UNCOMPLETE_TODO
                        // queued — BEFORE it calls the server, so a failed call is a
                        // deferred restore, not a lost one: the next sync replays it. The
                        // restore that just queued itself will therefore happen, and an
                        // error here would announce a failure the user is about to be
                        // wrong about. The re-read is what draws that state; without it
                        // the screen would keep showing the row the cache already moved.
                        hydrateFromCache()
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
    val todoItems: List<CompletedItem>,
    val floaterItems: List<CompletedItem>,
    val lists: List<ListSummary>,
    val floaterLists: List<ListSummary>,
)
