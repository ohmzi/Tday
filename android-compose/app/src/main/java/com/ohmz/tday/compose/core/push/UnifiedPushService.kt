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
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * Receives UnifiedPush lifecycle callbacks. On a new endpoint we register it with the
 * backend (Server Mode only) as an `unifiedpush` transport; incoming messages carry the
 * same ID-only payload the web push path uses and are shown as a local notification.
 *
 * A [PushService] rather than the connector's deprecated `MessagingReceiver`: the library's own
 * broadcast receiver takes the distributor's broadcast off the main thread and hands each event
 * to this service, so the work below runs with a bound component instead of inside a bare
 * `onReceive`.
 */
class UnifiedPushService : PushService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val endpointUrl = endpoint.url
        val entry = entryPoint(applicationContext)
        // Only Server-Mode users have a backend to receive from.
        if (entry.serverConfigRepository().isLocalMode()) return
        entry.unifiedPushStore().setEndpoint(endpointUrl)
        // Sent from here as well as from UnifiedPushAutoRegistrar because this receiver may be
        // the only thing awake when a distributor rotates an endpoint. It cannot record WHICH
        // account the endpoint was accepted for — a broadcast receiver has no session in hand —
        // so the failure below is left as a warning: setEndpoint has already dropped the
        // confirmation marker, and the next foreground re-sends it under the signed-in user.
        scope.launch {
            runCatching {
                entry.apiService().subscribePush(
                    PushSubscribeRequest(endpoint = endpointUrl, transport = UNIFIEDPUSH_TRANSPORT),
                )
            }.onFailure { Log.w(TAG, "Failed to register UnifiedPush endpoint: ${it.message}") }
        }
    }

    override fun onUnregistered(instance: String) {
        val entry = entryPoint(applicationContext)
        val endpoint = entry.unifiedPushStore().getEndpoint() ?: return
        entry.unifiedPushStore().clear()
        scope.launch {
            runCatching {
                entry.apiService().unsubscribePush(PushUnsubscribeRequest(endpoint = endpoint))
            }.onFailure { Log.w(TAG, "Failed to unregister UnifiedPush endpoint: ${it.message}") }
        }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Log.w(TAG, "UnifiedPush registration failed for instance $instance: $reason")
    }

    override fun onMessage(message: PushMessage, instance: String) {
        // The backend posts plain JSON, so `message.decrypted` is false here and `content` is the
        // body as the distributor delivered it.
        val context = applicationContext
        val payload = runCatching {
            json.parseToJsonElement(message.content.toString(Charsets.UTF_8)).jsonObject
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

        // Gated below the data-changed branch on purpose: that ping posts nothing and keeps
        // the widgets fresh, so the notification switch has no business stopping it.
        if (!entryPoint(context).notificationPreferenceStore().isEnabled()) return

        val title = field("title") ?: context.getString(R.string.reminder_notification_default_title)
        val body = field("body").orEmpty()
        val todoId = field("todoId")
        val listId = field("listId")
        val listType = field("listType")
        val listName = field("listName")

        val deepLinkIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = when {
                todoId != null -> Uri.parse("tday://todos/all?highlightTodoId=${Uri.encode(todoId)}")
                // A list-shared push: open straight into the shared list. listType is only ever
                // "list" or "floaterList" (see ListType.wireName on the backend); default to the
                // scheduled-list route for any other/missing value rather than drop the link.
                listId != null && listType == "floaterList" ->
                    Uri.parse("tday://floater/list/${Uri.encode(listId)}/${Uri.encode(listName.orEmpty())}")
                listId != null ->
                    Uri.parse("tday://todos/list/${Uri.encode(listId)}/${Uri.encode(listName.orEmpty())}")
                else -> Uri.parse("tday://todos/all")
            }
        }
        val notificationKey = (todoId ?: listId ?: instance).hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationKey,
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
                .notify(notificationKey, notification)
        }.onFailure { Log.w(TAG, "Failed to post UnifiedPush notification: ${it.message}") }
    }

    private fun entryPoint(context: Context): UnifiedPushEntryPoint =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            UnifiedPushEntryPoint::class.java,
        )

    private companion object {
        const val TAG = "UnifiedPushService"
        const val DATA_CHANGED_TYPE = "data-changed"
    }
}
