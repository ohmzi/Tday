package com.ohmz.tday.compose.core.push

import com.ohmz.tday.compose.core.data.server.ServerConfigRepository
import com.ohmz.tday.compose.core.network.TdayApiService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Hilt access for the UnifiedPush BroadcastReceiver (receivers can't use field injection here). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface UnifiedPushEntryPoint {
    fun apiService(): TdayApiService
    fun serverConfigRepository(): ServerConfigRepository
    fun unifiedPushStore(): UnifiedPushPreferenceStore
}
