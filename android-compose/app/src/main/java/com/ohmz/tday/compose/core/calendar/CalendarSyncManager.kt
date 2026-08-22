package com.ohmz.tday.compose.core.calendar

import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.todo.TodoRepository
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors pending scheduled tasks into T'Day's own device calendar.
 *
 * One-way by design: T'Day is the source of truth and the calendar is a projection of it. Nothing
 * is ever read back, so there is no conflict resolution and no chance of an edit loop.
 *
 * Reconciles the whole calendar rather than tracking per-task mutations, mirroring
 * `TaskReminderScheduler.rescheduleAll()`. That keeps the trigger surface to a single observation
 * of [OfflineCacheManager.cacheDataVersion] instead of the eight scattered call sites the reminder
 * scheduler needs, and it self-heals when the calendar drifts.
 *
 * Floaters are excluded: they have no due date, and the calendar contract keeps them off calendar
 * surfaces.
 */
@Singleton
class CalendarSyncManager @Inject constructor(
    private val repository: TodoRepository,
    private val cacheManager: OfflineCacheManager,
    private val deviceCalendarStore: DeviceCalendarStore,
    private val preferenceStore: CalendarSyncPreferenceStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val syncMutex = Mutex()
    private val observing = AtomicBoolean(false)

    /**
     * Starts mirroring on every cache-data change. Safe to call more than once; only the first
     * call attaches the observer.
     */
    fun start() {
        if (!observing.compareAndSet(false, true)) return
        scope.launch {
            // drop(1): the StateFlow replays its current value on collect, and the explicit
            // startup sync below already covers that first emission.
            cacheManager.cacheDataVersion.drop(1).collect {
                runCatching { syncNow() }
            }
        }
        scope.launch { runCatching { syncNow() } }
    }

    /**
     * Turns the mirror on and performs the first sync. The caller owns the permission grant.
     */
    suspend fun enable() {
        preferenceStore.setEnabled(true)
        // Force a write even when the task set matches the stored fingerprint: the calendar may
        // have been deleted while the feature was off.
        preferenceStore.setLastSyncedFingerprint(null)
        syncNow()
    }

    /** Turns the mirror off and removes the calendar and every event in it. */
    suspend fun disable() {
        preferenceStore.setEnabled(false)
        preferenceStore.setLastSyncedFingerprint(null)
        syncMutex.withLock {
            runCatching { deviceCalendarStore.deleteCalendar() }
                .onFailure { TdayTelemetry.capture(it, "calendar.sync.delete_calendar") }
        }
        TdayTelemetry.addBreadcrumb("calendar.sync.disabled")
    }

    /**
     * Reconciles the device calendar against the current task cache. A no-op when the feature is
     * off, when calendar permission is missing, or when nothing this mirror renders has changed.
     */
    suspend fun syncNow() {
        if (!preferenceStore.isEnabled()) return
        if (!deviceCalendarStore.hasPermissions()) return

        syncMutex.withLock {
            val events = buildEvents()
            val fingerprint = fingerprintOf(events)
            if (fingerprint == preferenceStore.getLastSyncedFingerprint()) return

            val calendarId = deviceCalendarStore.ensureCalendarId()
            if (calendarId == null) {
                TdayTelemetry.addBreadcrumb("calendar.sync.calendar_unavailable")
                return
            }

            runCatching { deviceCalendarStore.replaceEvents(calendarId, events) }
                .onSuccess {
                    preferenceStore.setLastSyncedFingerprint(fingerprint)
                    TdayTelemetry.addBreadcrumb(
                        "calendar.sync.reconcile",
                        data = mapOf("eventCount" to events.size),
                    )
                }
                .onFailure {
                    // Leave the fingerprint untouched so the next pass retries.
                    TdayTelemetry.capture(it, "calendar.sync.reconcile")
                }
        }
    }

    private fun buildEvents(): List<DeviceCalendarEvent> {
        val timeZoneId = TimeZone.getDefault().id
        return runCatching { repository.fetchTodosSnapshot(TodoListMode.ALL) }
            .getOrElse { emptyList() }
            .filterNot { it.completed }
            .mapNotNull { task -> task.toDeviceEvent(timeZoneId) }
    }

    private fun TodoItem.toDeviceEvent(timeZoneId: String): DeviceCalendarEvent? {
        val startMillis = due?.toEpochMilli() ?: return null
        if (startMillis <= 0) return null
        return DeviceCalendarEvent(
            taskId = id,
            title = title,
            description = description,
            startEpochMillis = startMillis,
            rrule = rrule?.takeIf { it.isNotBlank() },
            timeZoneId = timeZoneId,
        )
    }

    /**
     * Content fingerprint of the projected calendar. Cheap guard against rewriting the calendar
     * for cache changes that touched nothing this mirror renders — a floater edit, a completed-item
     * refresh, a preference sync.
     */
    private fun fingerprintOf(events: List<DeviceCalendarEvent>): String {
        if (events.isEmpty()) return "empty"
        return events
            .sortedWith(compareBy({ it.startEpochMillis }, { it.taskId }))
            .joinToString("|") { event ->
                listOf(
                    event.taskId,
                    event.startEpochMillis.toString(),
                    event.title,
                    event.description.orEmpty(),
                    event.rrule.orEmpty(),
                    event.timeZoneId,
                ).joinToString(RECORD_SEPARATOR)
            }
            .hashCode()
            .toString()
    }

    private companion object {
        /** Field separator that cannot appear in a task title, so fields cannot alias. */
        const val RECORD_SEPARATOR = "\u001F"
    }
}
