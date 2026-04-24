package com.ohmz.tday.routes

import arrow.core.Either
import arrow.core.raise.either
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.validateCreateList
import com.ohmz.tday.domain.validateOrFail
import com.ohmz.tday.domain.validatePatchList
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.models.request.*
import com.ohmz.tday.models.response.CreateListResponse
import com.ohmz.tday.models.response.DeleteListResponse
import com.ohmz.tday.models.response.ListDetailResponse
import com.ohmz.tday.services.ListService
import com.ohmz.tday.shared.model.DeleteListRequest
import io.ktor.server.request.*
import io.ktor.server.routing.*
import com.ohmz.tday.di.inject

fun Route.listRoutes() {
    val listService by inject<ListService>()

    route("/list") {
        get {
            call.withAuth { user ->
                listService.getAll(user.id).map { mapOf("lists" to it) }
            }
        }

        post {
            call.withAuth { user ->
                either {
                    val body = call.receive<ListCreateRequest>()
                    validateCreateList.validateOrFail(body).bind()
                    val list = listService.create(user.id, body.name, body.color, body.iconKey).bind()
                    CreateListResponse(message = "list created", list = list)
                }
            }
        }

        patch {
            call.withAuth { user ->
                either {
                    val body = call.receive<ListPatchRequest>()
                    validatePatchList.validateOrFail(body).bind()
                    listService.update(user.id, body.id, body.name, body.color, body.iconKey).bind()
                    mapOf("message" to "list updated")
                }
            }
        }

        delete {
            call.withAuth { user ->
                either<AppError, DeleteListResponse> {
                    val body = call.receive<ListDeleteRequest>()
                    val ids = body.normalizedIds().bind()
                    val deletedIds = listService.deleteMany(user.id, ids).bind()
                    DeleteListResponse(
                        message = if (ids.size == 1) {
                            "list deleted"
                        } else {
                            "${deletedIds.size} lists deleted"
                        },
                        deletedIds = deletedIds,
                    )
                }
            }
        }

        get("/{id}") {
            call.withAuth { user ->
                val listId = call.parameters["id"]
                    ?: return@withAuth Either.Left(AppError.BadRequest("list id is required"))
                either<AppError, ListDetailResponse> {
                    val list = listService.getById(user.id, listId).bind()
                    val todos = listService.getTodosForList(user.id, listId).bind()
                    ListDetailResponse(list = list, todos = todos)
                }
            }
        }
    }
}

private fun DeleteListRequest.normalizedIds(): Either<AppError, List<String>> {
    val normalizedIds = buildList {
        id?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
        ids.map(String::trim).filter(String::isNotEmpty).forEach(::add)
    }.distinct()

    return if (normalizedIds.isEmpty()) {
        Either.Left(AppError.BadRequest("at least one list id is required"))
    } else {
        Either.Right(normalizedIds)
    }
}
