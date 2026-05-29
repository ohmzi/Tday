package com.ohmz.tday.routes

import arrow.core.Either
import arrow.core.raise.either
import com.ohmz.tday.di.inject
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.validateCreateFloaterList
import com.ohmz.tday.domain.validateOrFail
import com.ohmz.tday.domain.validatePatchFloaterList
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.models.request.FloaterListCreateRequest
import com.ohmz.tday.models.request.FloaterListDeleteRequest
import com.ohmz.tday.models.request.FloaterListPatchRequest
import com.ohmz.tday.models.response.CreateFloaterListResponse
import com.ohmz.tday.models.response.DeleteFloaterListResponse
import com.ohmz.tday.models.response.FloaterListDetailResponse
import com.ohmz.tday.services.FloaterListService
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.floaterListRoutes() {
    val floaterListService by inject<FloaterListService>()

    route("/floaterList") {
        get {
            call.withAuth { user ->
                floaterListService.getAll(user.id).map { mapOf("lists" to it) }
            }
        }

        post {
            call.withAuth { user ->
                either {
                    val body = call.receive<FloaterListCreateRequest>()
                    validateCreateFloaterList.validateOrFail(body).bind()
                    val list = floaterListService.create(user.id, body.name, body.color, body.iconKey).bind()
                    CreateFloaterListResponse(message = "floater list created", list = list)
                }
            }
        }

        patch {
            call.withAuth { user ->
                either {
                    val body = call.receive<FloaterListPatchRequest>()
                    validatePatchFloaterList.validateOrFail(body).bind()
                    floaterListService.update(user.id, body.id, body.name, body.color, body.iconKey).bind()
                    mapOf("message" to "floater list updated")
                }
            }
        }

        delete {
            call.withAuth { user ->
                either<AppError, DeleteFloaterListResponse> {
                    val body = call.receive<FloaterListDeleteRequest>()
                    val ids = body.normalizedIds().bind()
                    val deletedIds = floaterListService.deleteMany(user.id, ids).bind()
                    DeleteFloaterListResponse(
                        message = if (ids.size == 1) {
                            if (deletedIds.isEmpty()) "floater list already deleted" else "floater list deleted"
                        } else {
                            "${deletedIds.size} floater lists deleted"
                        },
                        deletedIds = deletedIds,
                    )
                }
            }
        }

        get("/{id}") {
            call.withAuth { user ->
                val listId = call.parameters["id"]
                    ?: return@withAuth Either.Left(AppError.BadRequest("floater list id is required"))
                either<AppError, FloaterListDetailResponse> {
                    val list = floaterListService.getById(user.id, listId).bind()
                    val floaters = floaterListService.getFloatersForList(user.id, listId).bind()
                    FloaterListDetailResponse(list = list, floaters = floaters)
                }
            }
        }
    }
}

private fun FloaterListDeleteRequest.normalizedIds(): Either<AppError, List<String>> {
    val normalizedIds = buildList {
        id?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
        ids.map(String::trim).filter(String::isNotEmpty).forEach(::add)
    }.distinct()

    return if (normalizedIds.isEmpty()) {
        Either.Left(AppError.BadRequest("at least one floater list id is required"))
    } else {
        Either.Right(normalizedIds)
    }
}
