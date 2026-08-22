package com.ohmz.tday.compose.core.calendar

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Lets the Settings toggle reach the calendar mirror without threading it through a ViewModel.
 *
 * The device-calendar rows are self-contained, like `AppLockRow` and `ScreenshotProtectionRow`
 * next to them, so they read their own state rather than expanding the Settings state object.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CalendarEntryPoint {
    fun calendarSyncManager(): CalendarSyncManager
    fun calendarSyncPreferenceStore(): CalendarSyncPreferenceStore
}
