package com.ohmz.tday.shared.model

import kotlinx.serialization.Serializable

/**
 * Access level a user has on a shared list. OWNER is implicit (the list's
 * `userID`); share rows only ever hold EDITOR or VIEWER.
 */
@Serializable
enum class ShareRole {
    OWNER,
    EDITOR,
    VIEWER;

    val canEdit: Boolean get() = this != VIEWER

    companion object {
        fun fromString(value: String?): ShareRole? =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
    }
}

@Serializable
data class ListMemberDto(
    val userId: String,
    val username: String,
    val name: String? = null,
    val role: String,
    val addedAt: String? = null,
)

@Serializable
data class ListMembersResponse(
    val owner: ListMemberDto,
    val members: List<ListMemberDto> = emptyList(),
)

@Serializable
data class AddMemberRequest(
    val username: String,
    val role: String = ShareRole.EDITOR.name,
)

@Serializable
data class UpdateMemberRoleRequest(
    val userId: String,
    val role: String,
)

@Serializable
data class RemoveMemberRequest(
    val userId: String,
)

@Serializable
data class AddMemberResponse(
    val message: String? = null,
    val member: ListMemberDto? = null,
)

@Serializable
data class UserSearchResultDto(
    val id: String,
    val username: String,
    val name: String? = null,
)

@Serializable
data class UserSearchResponse(
    val users: List<UserSearchResultDto> = emptyList(),
)
