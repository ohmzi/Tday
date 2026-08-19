package com.ohmz.tday.compose.core.notification

import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.feature.widget.FloaterTasksWidgetRefresher
import com.ohmz.tday.compose.feature.widget.TodayTasksWidgetRefresher
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotWriter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Boot-time-only access to the cache, for [BootRescheduleReceiver]'s `MY_PACKAGE_REPLACED`
 * backfill. Deliberately separate from `feature.widget.WidgetEntryPoint`, which must never expose
 * [OfflineCacheManager] to a widget's render path (`provideGlance`) — this interface exists for
 * the one other legitimate caller with the same shape as `WidgetHydrateWorker`: a boot-time
 * backfill that runs once, off the render path, right after an app update.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BootBackfillEntryPoint {
    fun offlineCacheManager(): OfflineCacheManager
    fun widgetSnapshotWriter(): WidgetSnapshotWriter
    fun todayTasksWidgetRefresher(): TodayTasksWidgetRefresher
    fun floaterTasksWidgetRefresher(): FloaterTasksWidgetRefresher
}
