package com.ohmz.tday.compose.feature.widget

import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun offlineCacheManager(): OfflineCacheManager
}
