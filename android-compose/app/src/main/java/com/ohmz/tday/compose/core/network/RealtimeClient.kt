package com.ohmz.tday.compose.core.network

import android.util.Log
import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RealtimeEvent {
    data object TodoChanged : RealtimeEvent
    data object FloaterChanged : RealtimeEvent
    data object ListChanged : RealtimeEvent
    data object CompletedChanged : RealtimeEvent
    data class Unknown(val type: String) : RealtimeEvent
    data object Connected : RealtimeEvent
    data object Disconnected : RealtimeEvent
}

@Singleton
class RealtimeClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val secureConfigStore: SecureConfigStore,
) {
    private val _events = MutableSharedFlow<RealtimeEvent>(
        extraBufferCapacity = 20,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    private var socket: WebSocket? = null
    @Volatile
    private var connected = false

    val isConnected: Boolean get() = connected

    // Dedicated WebSocket client with a keepalive ping. It keeps the socket warm (so idle/NAT
    // timeouts don't silently kill live updates) AND detects a dead socket: if no pong arrives,
    // OkHttp fails the connection -> onFailure -> Disconnected -> the app reconnects. The shared
    // OkHttpClient has no pingInterval, so without this a half-open socket wedges "connected"
    // and Android silently receives nothing.
    private val wsClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .pingInterval(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    fun connect() {
        if (socket != null) return

        val baseUrl = secureConfigStore.getServerUrl()?.toHttpUrlOrNull() ?: return
        val wsUrl = baseUrl.newBuilder()
            .addPathSegments("ws")
            .build()

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        socket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                TdayTelemetry.addBreadcrumb("realtime.connect", data = mapOf("status" to "open"))
                Log.d(LOG_TAG, "WebSocket connected")
                _events.tryEmit(RealtimeEvent.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = parseRealtimeEvent(text)
                if (event != null) _events.tryEmit(event)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                handleDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                TdayTelemetry.addBreadcrumb(
                    "realtime.connect",
                    level = io.sentry.SentryLevel.WARNING,
                    data = mapOf("status" to "failure", "code" to response?.code),
                )
                Log.d(LOG_TAG, "WebSocket failure: ${t.javaClass.simpleName}")
                handleDisconnect()
            }
        })
    }

    fun disconnect() {
        socket?.close(1000, "Client disconnecting")
        socket = null
        if (connected) {
            connected = false
            _events.tryEmit(RealtimeEvent.Disconnected)
        }
    }

    private fun handleDisconnect() {
        socket = null
        if (connected) {
            connected = false
            TdayTelemetry.addBreadcrumb("realtime.disconnect")
            _events.tryEmit(RealtimeEvent.Disconnected)
        }
    }

    private companion object {
        const val LOG_TAG = "RealtimeClient"
    }
}

internal fun parseRealtimeEvent(raw: String): RealtimeEvent? {
    val type = extractRealtimeEventName(raw).trim().lowercase()
    return when {
        type.startsWith("todo.") ||
            type.contains("todocreated") ||
            type.contains("todoupdated") ||
            type.contains("tododeleted") -> RealtimeEvent.TodoChanged
        type.startsWith("floaterlist.") -> RealtimeEvent.ListChanged
        type.startsWith("floater.") -> RealtimeEvent.FloaterChanged
        // "list.members" (membership changes) intentionally lands here too.
        type.startsWith("list.") ||
            type.contains("listchanged") -> RealtimeEvent.ListChanged
        type.startsWith("completed.") ||
            type.startsWith("completedtodo.") ||
            type.contains("completedchanged") ||
            type.contains("completedtodo") -> RealtimeEvent.CompletedChanged
        type.isNotBlank() -> RealtimeEvent.Unknown(type)
        else -> null
    }
}

private fun extractRealtimeEventName(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return trimmed

    return runCatching {
        val jsonObject = Json.parseToJsonElement(trimmed) as? JsonObject
        jsonObject?.let { eventObject ->
            eventObject["event"]?.jsonPrimitive?.contentOrNull
                ?: eventObject["type"]?.jsonPrimitive?.contentOrNull
        } ?: trimmed
    }.getOrDefault(trimmed)
}
