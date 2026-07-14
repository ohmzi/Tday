package com.ohmz.tday.routes

import arrow.core.Either
import com.ohmz.tday.di.inject
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.models.request.CreateWebhookRequest
import com.ohmz.tday.services.CreateWebhookResponse
import com.ohmz.tday.services.WEBHOOK_EVENT_TYPES
import com.ohmz.tday.services.WebhookService
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.webhookRoutes() {
    val webhookService by inject<WebhookService>()

    route("/webhook") {
        // The set of event types a subscription may filter on (for building the UI).
        get("/event-types") {
            call.withAuth {
                Either.Right(mapOf("eventTypes" to WEBHOOK_EVENT_TYPES))
            }
        }

        // List the caller's webhook subscriptions (secrets are never returned).
        get {
            call.withAuth { user ->
                webhookService.list(user.id).map { mapOf("webhooks" to it) }
            }
        }

        // Register a new webhook. The signing secret is returned once.
        post {
            call.withAuth { user ->
                val body = call.receive<CreateWebhookRequest>()
                webhookService.create(user.id, body.url, body.events)
                    .map { CreateWebhookResponse(message = "webhook created", webhook = it) }
            }
        }

        // Delete a subscription by id.
        delete("/{id}") {
            call.withAuth { user ->
                val id = call.parameters["id"].orEmpty()
                if (id.isBlank()) {
                    Either.Left(AppError.BadRequest("webhook id is required"))
                } else {
                    webhookService.delete(user.id, id).map { mapOf("message" to "webhook deleted") }
                }
            }
        }
    }
}
