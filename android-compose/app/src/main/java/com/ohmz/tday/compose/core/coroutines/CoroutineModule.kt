package com.ohmz.tday.compose.core.coroutines

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/**
 * The dispatcher for CPU-bound background work that must stay off the main thread
 * (alarm rescheduling, preference writes). Injected rather than referenced as
 * [Dispatchers.Default] directly so unit tests can hand in a test dispatcher: work
 * posted to the real thread pool is invisible to `TestCoroutineScheduler`, so
 * `runCurrent()`/`advanceUntilIdle()` return while it is still in flight and the
 * coroutine that awaits it resumes on a pool thread, racing the test thread.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackgroundDispatcher

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides
    @BackgroundDispatcher
    fun provideBackgroundDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
