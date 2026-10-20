package com.ohmz.tday.compose.core.calendar

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** One scheduled task rendered as a device-calendar event. */
data class DeviceCalendarEvent(
    val taskId: String,
    val title: String,
    val description: String?,
    val startEpochMillis: Long,
    /** RFC 5545 rule for a repeating task, or null for a one-off. */
    val rrule: String?,
    val timeZoneId: String,
)

/**
 * Owns T'Day's own calendar in the system calendar provider.
 *
 * Everything here is scoped to a single local calendar that this app creates. Writes never touch
 * the user's other calendars, and removing the sync removes the calendar wholesale.
 *
 * The calendar is created with [CalendarContract.ACCOUNT_TYPE_LOCAL] so it needs no Google (or
 * other) account and is never uploaded anywhere by the platform — which matters for a
 * privacy-first, self-hosted app and keeps the feature working in Local Mode.
 */
@Singleton
class DeviceCalendarStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasPermissions(): Boolean =
        isGranted(Manifest.permission.READ_CALENDAR) && isGranted(Manifest.permission.WRITE_CALENDAR)

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Existing T'Day calendar id, or null when it has not been created yet. */
    fun findCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND " +
            "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND " +
            "${CalendarContract.Calendars.NAME} = ?"
        val args = arrayOf(ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL, CALENDAR_NAME)

        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            args,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
    }

    fun ensureCalendarId(): Long? = findCalendarId() ?: createCalendar()

    private fun createCalendar(): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_DISPLAY_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, CALENDAR_COLOR)
            put(
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CAL_ACCESS_OWNER,
            )
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.VISIBLE, 1)
        }
        val uri = context.contentResolver.insert(syncAdapterUri(CalendarContract.Calendars.CONTENT_URI), values)
        return uri?.let { ContentUris.parseId(it) }
    }

    /** Drops the calendar and, with it, every event T'Day created. */
    fun deleteCalendar() {
        val calendarId = findCalendarId() ?: return
        context.contentResolver.delete(
            syncAdapterUri(ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId)),
            null,
            null,
        )
    }

    /**
     * Replaces the calendar's contents with [events] in a single batch.
     *
     * Wholesale replacement rather than a per-task diff: T'Day tasks have no stable device-event
     * identity, and a rewrite is self-healing — if the user edits or deletes an event in their
     * calendar app, the next pass restores the task's real state.
     */
    fun replaceEvents(calendarId: Long, events: List<DeviceCalendarEvent>) {
        val operations = ArrayList<ContentProviderOperation>()

        operations.add(
            ContentProviderOperation.newDelete(syncAdapterUri(CalendarContract.Events.CONTENT_URI))
                .withSelection("${CalendarContract.Events.CALENDAR_ID} = ?", arrayOf(calendarId.toString()))
                .build(),
        )

        for (event in events) {
            operations.add(
                ContentProviderOperation.newInsert(syncAdapterUri(CalendarContract.Events.CONTENT_URI))
                    .withValues(eventValues(calendarId, event))
                    .build(),
            )
        }

        // Chunked: the provider rejects oversized batches, and a task list can be long.
        operations.chunked(BATCH_SIZE).forEach { chunk ->
            context.contentResolver.applyBatch(CalendarContract.AUTHORITY, ArrayList(chunk))
        }
    }

    private fun eventValues(calendarId: Long, event: DeviceCalendarEvent): ContentValues =
        ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, event.title)
            event.description?.takeIf { it.isNotBlank() }?.let {
                put(CalendarContract.Events.DESCRIPTION, it)
            }
            put(CalendarContract.Events.DTSTART, event.startEpochMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, event.timeZoneId)
            put(CalendarContract.Events.HAS_ALARM, 0)
            // T'Day owns this mirror; edits belong in the app, not the calendar UI.
            put(CalendarContract.Events.GUESTS_CAN_MODIFY, 0)

            if (event.rrule.isNullOrBlank()) {
                put(
                    CalendarContract.Events.DTEND,
                    event.startEpochMillis + DEFAULT_EVENT_MINUTES * 60_000L,
                )
            } else {
                // CalendarContract requires DURATION (and forbids DTEND) on recurring events.
                put(CalendarContract.Events.RRULE, event.rrule)
                put(CalendarContract.Events.DURATION, "PT${DEFAULT_EVENT_MINUTES}M")
            }
        }

    /**
     * Sync-adapter URIs are what allow this app to create and drop a calendar it owns; the plain
     * URIs only permit event-level edits.
     */
    private fun syncAdapterUri(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    companion object {
        const val ACCOUNT_NAME = "T'Day"
        const val CALENDAR_NAME = "tday_scheduled_tasks"
        const val CALENDAR_DISPLAY_NAME = "T'Day"

        /**
         * T'Day tasks are point-in-time — the ICS feed drops durations entirely
         * (`CalendarIcs.kt`) — but native calendars need an end, and a zero-length event is
         * unreadable in a day grid.
         */
        const val DEFAULT_EVENT_MINUTES = 30

        private const val CALENDAR_COLOR = 0xFF4C6EF5.toInt()
        private const val BATCH_SIZE = 200
    }
}
