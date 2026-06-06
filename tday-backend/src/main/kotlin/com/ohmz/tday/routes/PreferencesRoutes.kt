package com.ohmz.tday.routes

import arrow.core.right
import arrow.core.raise.either
import com.ohmz.tday.db.enums.Direction
import com.ohmz.tday.db.enums.GroupBy
import com.ohmz.tday.db.enums.SortBy
import com.ohmz.tday.domain.validateOptionalEnumValue
import com.ohmz.tday.domain.validateOptionalValue
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.models.request.PreferencesPatchRequest
import com.ohmz.tday.services.PreferencesService
import io.ktor.server.request.*
import io.ktor.server.routing.*
import com.ohmz.tday.di.inject

fun Route.preferencesRoutes() {
    val preferencesService by inject<PreferencesService>()

    route("/preferences") {
        get {
            call.withAuth { user ->
                preferencesService.get(user.id)
            }
        }

        patch {
            call.withAuth { user ->
                either {
                    val body = call.receive<PreferencesPatchRequest>()
                    val sortBy = validateOptionalEnumValue<SortBy>(body.sortBy, "sortBy").bind()
                    val groupBy = validateOptionalValue(body.groupBy, "groupBy", ALLOWED_GROUP_BY_VALUES).bind()
                    val direction = validateOptionalEnumValue<Direction>(body.direction, "direction").bind()
                    preferencesService.update(user.id, sortBy, groupBy, direction, body.aiSummaryEnabled).bind()
                    mapOf("message" to "preferences updated")
                }
            }
        }
    }
}

private val ALLOWED_GROUP_BY_VALUES = enumValues<GroupBy>()
    .map { GroupBy.toApi(it) }
    .toSet()
