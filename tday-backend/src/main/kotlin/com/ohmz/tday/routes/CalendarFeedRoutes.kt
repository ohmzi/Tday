package com.ohmz.tday.routes

import com.ohmz.tday.di.inject
import com.ohmz.tday.services.CalendarFeedService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Public, unauthenticated iCalendar feed. Mounted OUTSIDE the `/api` tree (and thus
 * outside the session/API-key auth flow): the opaque path token is the only credential,
 * so calendar clients that cannot send headers (Apple Calendar, Google Calendar) can
 * still subscribe. The token itself scopes the response to one user's data, read-only.
 */
fun Route.calendarFeedRoutes() {
    val calendarFeedService by inject<CalendarFeedService>()

    get("/calendar/{token}") {
        val token = call.parameters["token"].orEmpty().removeSuffix(".ics")
        if (token.isBlank()) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val ics = calendarFeedService.renderIcs(token)
        if (ics == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        call.respondText(ics, ContentType("text", "calendar"))
    }
}
