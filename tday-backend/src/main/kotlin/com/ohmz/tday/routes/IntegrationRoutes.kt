package com.ohmz.tday.routes

import com.ohmz.tday.di.inject
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.plugins.resolvedApiKey
import com.ohmz.tday.services.IntegrationContextService
import com.ohmz.tday.shared.model.IntegrationApiKeyDto
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/**
 * Grounding data for external integrations — see `docs/API_INTEGRATION.md`.
 *
 * One call answers the three questions a dashboard or AI assistant has to ask
 * before it can do anything useful: who am I acting as, what can this credential
 * do, and what lists exist in each of the two namespaces.
 */
fun Route.integrationRoutes() {
    val integrationContextService by inject<IntegrationContextService>()

    route("/integration") {
        get("/context") {
            val apiKey = call.resolvedApiKey()?.let {
                IntegrationApiKeyDto(
                    scope = it.scope.name,
                    label = it.label,
                    keyPreview = it.keyPreview.ifEmpty { null },
                )
            }
            call.withAuth { user ->
                integrationContextService.contextFor(user.id, user.timeZone, apiKey)
            }
        }
    }
}
