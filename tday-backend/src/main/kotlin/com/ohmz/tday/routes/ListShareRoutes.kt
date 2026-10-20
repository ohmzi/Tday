package com.ohmz.tday.routes

import arrow.core.Either
import arrow.core.raise.either
import com.ohmz.tday.di.inject
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.services.ListShareService
import com.ohmz.tday.services.ListType
import com.ohmz.tday.shared.model.AddMemberRequest
import com.ohmz.tday.shared.model.AddMemberResponse
import com.ohmz.tday.shared.model.RemoveMemberRequest
import com.ohmz.tday.shared.model.UpdateMemberRoleRequest
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Member management for shared lists, mounted for both list domains:
 * GET/POST/PATCH/DELETE /api/{list|floaterList}/{id}/members and
 * POST /api/{list|floaterList}/{id}/leave.
 */
fun Route.listShareRoutes() {
    val shareService by inject<ListShareService>()
    memberRoutes("/list", ListType.SCHEDULED, shareService)
    memberRoutes("/floaterList", ListType.FLOATER, shareService)
}

private fun Route.memberRoutes(basePath: String, type: ListType, shareService: ListShareService) {
    route(basePath) {
        route("/{id}/members") {
            get {
                call.withAuth { user ->
                    either {
                        val listId = requireListId().bind()
                        shareService.members(user.id, listId, type).bind()
                    }
                }
            }

            post {
                call.withAuth { user ->
                    either {
                        val listId = requireListId().bind()
                        val body = call.receive<AddMemberRequest>()
                        val member = shareService.addMember(user.id, listId, type, body.username, body.role).bind()
                        AddMemberResponse(message = "member added", member = member)
                    }
                }
            }

            patch {
                call.withAuth { user ->
                    either {
                        val listId = requireListId().bind()
                        val body = call.receive<UpdateMemberRoleRequest>()
                        shareService.updateRole(user.id, listId, type, body.userId, body.role).bind()
                        mapOf("message" to "member role updated")
                    }
                }
            }

            delete {
                call.withAuth { user ->
                    either {
                        val listId = requireListId().bind()
                        val body = call.receive<RemoveMemberRequest>()
                        shareService.removeMember(user.id, listId, type, body.userId).bind()
                        mapOf("message" to "member removed")
                    }
                }
            }
        }

        post("/{id}/leave") {
            call.withAuth { user ->
                either {
                    val listId = requireListId().bind()
                    shareService.leave(user.id, listId, type).bind()
                    mapOf("message" to "left list")
                }
            }
        }
    }
}

private fun RoutingContext.requireListId(): Either<AppError, String> {
    val listId = call.parameters["id"]?.trim()
    return if (listId.isNullOrEmpty()) {
        Either.Left(AppError.BadRequest("list id is required"))
    } else {
        Either.Right(listId)
    }
}
