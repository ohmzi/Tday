package com.ohmz.tday.routes

import arrow.core.Either
import com.ohmz.tday.di.inject
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.models.request.PushSubscribeRequest
import com.ohmz.tday.models.request.PushUnsubscribeRequest
import com.ohmz.tday.models.response.VapidPublicKeyResponse
import com.ohmz.tday.services.PushNotificationService
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.notificationRoutes() {
    val pushService by inject<PushNotificationService>()

    route("/notifications") {
        get("/vapid-public-key") {
            val key = pushService.getVapidPublicKey()
            if (key != null) {
                call.respond(VapidPublicKeyResponse(publicKey = key))
            } else {
                call.respond(mapOf("message" to "Push notifications not configured"))
            }
        }

        post("/subscribe") {
            call.withAuth { user ->
                val body = call.receive<PushSubscribeRequest>()
                pushService.subscribe(user.id, body.endpoint, body.p256dh, body.auth, body.transport)
                    .map { mapOf("message" to "subscribed") }
            }
        }

        delete("/unsubscribe") {
            call.withAuth { user ->
                val body = call.receive<PushUnsubscribeRequest>()
                pushService.unsubscribe(user.id, body.endpoint)
                    .map { mapOf("message" to "unsubscribed") }
            }
        }
    }
}
