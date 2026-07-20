package com.ohmz.tday.compose.core.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ohmz.tday.compose.MainActivity
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.PushSubscribeRequest
import com.ohmz.tday.compose.core.model.PushUnsubscribeRequest
import com.ohmz.tday.compose.core.notification.TaskReminderReceiver
import com.ohmz.tday.compose.feature.widget.WidgetSyncWorker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.unifiedpush.android.connector.MessagingReceiver

/**
 * Receives UnifiedPush lifecycle callbacks. On a new endpoint we register it with the
 * backend (Server Mode only) as an `unifiedpush` transport; incoming messages carry the
 * same ID-only payload the web push path uses and are shown as a local notification.
 */
class UnifiedPushReceiver : MessagingReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        val entry = entryPoint(context)
        // Only Server-Mode users have a backend to receive from.
        if (entry.serverConfigRepository().isLocalMode()) return
        entry.unifiedPushStore().setEndpoint(endpoint)
        scope.launch {
            runCatching {
                entry.apiService().subscribePush(
                    PushSubscribeRequest(endpoint = endpoint, transport = "unifiedpush"),
                )
            }.onFailure { Log.w(TAG, "Failed to register UnifiedPush endpoint: ${it.message}") }
        }
    }

    override fun onUnregistered(context: Context, instance: String) {
        val entry = entryPoint(context)
        val endpoint = entry.unifiedPushStore().getEndpoint() ?: return
        entry.unifiedPushStore().clear()
        scope.launch {
            runCatching {
                entry.apiService().unsubscribePush(PushUnsubscribeRequest(endpoint = endpoint))
            }.onFailure { Log.w(TAG, "Failed to unregister UnifiedPush endpoint: ${it.message}") }
        }
    }

    override fun onRegistrationFailed(context: Context, instance: String) {
        Log.w(TAG, "UnifiedPush registration failed for instance $instance")
    }

    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        val payload = runCatching {
            json.parseToJsonElement(message.toString(Charsets.UTF_8)).jsonObject
        }.getOrNull() ?: return

        fun field(name: String): String? =
            runCatching { payload[name]?.jsonPrimitive?.content }.getOrNull()

        // Silent "data changed" ping: the backend fires this on every mutation so a backgrounded
        // device refreshes its home-screen widgets even with the app process dead. No notification —
        // just kick the widget sync worker (it syncs the cache, which re-renders both widgets).
        if (field("type") == DATA_CHANGED_TYPE) {
            runCatching { WidgetSyncWorker.runOnce(context) }
                .onFailure { Log.w(TAG, "Failed to trigger widget sync from push: ${it.message}") }
            return
        }

        val title = field("title") ?: context.getString(R.string.reminder_notification_default_title)
        val body = field("body").orEmpty()
        val todoId = field("todoId")

        val deepLinkIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = if (todoId != null) {
                Uri.parse("tday://todos/all?highlightTodoId=${Uri.encode(todoId)}")
            } else {
                Uri.parse("tday://todos/all")
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (todoId ?: instance).hashCode(),
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, TaskReminderReceiver.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify((todoId ?: instance).hashCode(), notification)
        }.onFailure { Log.w(TAG, "Failed to post UnifiedPush notification: ${it.message}") }
    }

    private fun entryPoint(context: Context): UnifiedPushEntryPoint =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            UnifiedPushEntryPoint::class.java,
        )

    private companion object {
        const val TAG = "UnifiedPushReceiver"
        const val DATA_CHANGED_TYPE = "data-changed"
    }
}
