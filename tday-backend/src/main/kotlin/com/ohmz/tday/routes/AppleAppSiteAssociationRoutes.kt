package com.ohmz.tday.routes

import com.ohmz.tday.config.AppConfig
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

fun Route.appleAppSiteAssociationRoutes(config: AppConfig) {
    get("/.well-known/apple-app-site-association") {
        call.respondText(
            text = appleAppSiteAssociationJson(config),
            contentType = ContentType.Application.Json,
        )
    }

    get("/apple-app-site-association") {
        call.respondText(
            text = appleAppSiteAssociationJson(config),
            contentType = ContentType.Application.Json,
        )
    }
}

fun appleAppSiteAssociationJson(config: AppConfig): String {
    val appIds = config.appleTeamId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { teamId -> listOf("$teamId.${config.iosBundleId}") }
        .orEmpty()

    val payload = buildJsonObject {
        putJsonObject("webcredentials") {
            put("apps", JsonArray(appIds.map(::JsonPrimitive)))
        }
    }

    return payload.toString()
}
