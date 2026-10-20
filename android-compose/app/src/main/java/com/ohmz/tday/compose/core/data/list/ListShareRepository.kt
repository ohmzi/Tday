package com.ohmz.tday.compose.core.data.list

import com.ohmz.tday.compose.core.data.requireApiBody
import com.ohmz.tday.compose.core.model.AddMemberRequest
import com.ohmz.tday.compose.core.model.ListMemberDto
import com.ohmz.tday.compose.core.model.ListMembersResponse
import com.ohmz.tday.compose.core.model.RemoveMemberRequest
import com.ohmz.tday.compose.core.model.UpdateMemberRoleRequest
import com.ohmz.tday.compose.core.model.UserSearchResultDto
import com.ohmz.tday.compose.core.network.TdayApiService
import javax.inject.Inject
import javax.inject.Singleton

/** Whether a shared-list id refers to a scheduled list or a floater list. */
enum class ShareListKind { SCHEDULED, FLOATER }

/**
 * Membership management for shared lists. Deliberately ONLINE-ONLY: membership
 * changes are rare, identity-sensitive operations with ugly offline-replay
 * failure modes (silently re-adding a removed member, demote/remove races), so
 * they never enqueue pending mutations — connectivity errors surface directly.
 */
@Singleton
class ListShareRepository @Inject constructor(
    private val api: TdayApiService,
) {
    suspend fun fetchMembers(kind: ShareListKind, listId: String): ListMembersResponse =
        requireApiBody(
            when (kind) {
                ShareListKind.SCHEDULED -> api.getListMembers(listId)
                ShareListKind.FLOATER -> api.getFloaterListMembers(listId)
            },
            "Could not load members",
        )

    suspend fun addMember(
        kind: ShareListKind,
        listId: String,
        username: String,
        role: String
    ): ListMemberDto? =
        requireApiBody(
            when (kind) {
                ShareListKind.SCHEDULED -> api.addListMember(
                    listId,
                    AddMemberRequest(username = username, role = role)
                )

                ShareListKind.FLOATER -> api.addFloaterListMember(
                    listId,
                    AddMemberRequest(username = username, role = role)
                )
            },
            "Could not add member",
        ).member

    suspend fun updateMemberRole(
        kind: ShareListKind,
        listId: String,
        userId: String,
        role: String
    ) {
        requireApiBody(
            when (kind) {
                ShareListKind.SCHEDULED -> api.updateListMemberRole(
                    listId,
                    UpdateMemberRoleRequest(userId = userId, role = role)
                )

                ShareListKind.FLOATER -> api.updateFloaterListMemberRole(
                    listId,
                    UpdateMemberRoleRequest(userId = userId, role = role)
                )
            },
            "Could not update member role",
        )
    }

    suspend fun removeMember(kind: ShareListKind, listId: String, userId: String) {
        requireApiBody(
            when (kind) {
                ShareListKind.SCHEDULED -> api.removeListMember(
                    listId,
                    RemoveMemberRequest(userId = userId)
                )

                ShareListKind.FLOATER -> api.removeFloaterListMember(
                    listId,
                    RemoveMemberRequest(userId = userId)
                )
            },
            "Could not remove member",
        )
    }

    suspend fun leaveList(kind: ShareListKind, listId: String) {
        requireApiBody(
            when (kind) {
                ShareListKind.SCHEDULED -> api.leaveList(listId)
                ShareListKind.FLOATER -> api.leaveFloaterList(listId)
            },
            "Could not leave list",
        )
    }

    suspend fun searchUsers(query: String): List<UserSearchResultDto> {
        if (query.trim().length < 2) return emptyList()
        return requireApiBody(api.searchUsers(query.trim()), "Could not search users").users
    }
}
