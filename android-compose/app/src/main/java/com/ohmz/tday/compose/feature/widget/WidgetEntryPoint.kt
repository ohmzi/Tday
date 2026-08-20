package com.ohmz.tday.compose.feature.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Deliberately does NOT expose `OfflineCacheManager` or `SecureConfigStore`: both are Keystore-
 * or SQLCipher-backed, and reaching either from a widget's `provideGlance` is what used to cost
 * ~9.5s cold (the encrypted cache open). A widget's render path must never call
 * `EntryPointAccessors` at all — it reads `feature.widget.snapshot.WidgetSnapshotStore` directly,
 * constructed the same way `AppSecurityPreferenceStore` already is. `WidgetHydrateWorker` is the
 * one place still allowed to open the cache, and it does so off a Hilt-injected `CoroutineWorker`,
 * never through this entry point.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetCompleteTaskSubmitter(): WidgetCompleteTaskSubmitter
    fun todayTasksWidgetRefresher(): TodayTasksWidgetRefresher
    fun floaterTasksWidgetRefresher(): FloaterTasksWidgetRefresher
}
