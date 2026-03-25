package com.ohmz.tday.compose.core.domain

import com.ohmz.tday.compose.core.data.auth.AuthRepository
import com.ohmz.tday.compose.core.data.sync.SyncManager
import com.ohmz.tday.compose.core.model.SessionUser
import com.ohmz.tday.compose.core.notification.TaskReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BootstrapSessionUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncManager: SyncManager,
    private val reminderScheduler: TaskReminderScheduler,
) {
    /**
     * Restores the user session, syncs data from the remote, aligns the
     * device timezone, and reschedules local reminders.
     *
     * @return the restored [SessionUser], or `null` when no valid session exists.
     */
    suspend operator fun invoke(): SessionUser? {
        val user = runCatching { authRepository.restoreSession() }.getOrNull()
            ?: return null
        if (user.id == null) return null

        coroutineScope {
            launch { runCatching { syncManager.syncCachedData(force = true) } }
            launch { runCatching { authRepository.syncTimezone() } }
            launch(Dispatchers.Default) { runCatching { reminderScheduler.rescheduleAll() } }
        }
        return user
    }
}
