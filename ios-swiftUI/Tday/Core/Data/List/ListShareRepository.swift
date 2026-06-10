import Foundation

/// Whether a shared-list id refers to a scheduled list or a floater list.
enum ShareListKind {
    case scheduled
    case floater

    /// API path segment ("list" or "floaterList").
    var pathBase: String {
        switch self {
        case .scheduled: return "list"
        case .floater: return "floaterList"
        }
    }
}

/// Membership management for shared lists. Deliberately ONLINE-ONLY:
/// membership changes are rare, identity-sensitive operations with ugly
/// offline-replay failure modes (silently re-adding a removed member,
/// demote/remove races), so they never enqueue pending mutations —
/// connectivity errors surface directly.
@MainActor
final class ListShareRepository {
    private let api: TdayAPIService

    init(api: TdayAPIService) {
        self.api = api
    }

    func fetchMembers(kind: ShareListKind, listID: String) async throws -> ListMembersResponse {
        try await api.getListMembers(base: kind.pathBase, listID: listID)
    }

    func addMember(kind: ShareListKind, listID: String, username: String, role: String) async throws -> ListMemberDTO? {
        try await api.addListMember(
            base: kind.pathBase,
            listID: listID,
            payload: AddMemberRequest(username: username, role: role)
        ).member
    }

    func updateMemberRole(kind: ShareListKind, listID: String, userId: String, role: String) async throws {
        _ = try await api.updateListMemberRole(
            base: kind.pathBase,
            listID: listID,
            payload: UpdateMemberRoleRequest(userId: userId, role: role)
        )
    }

    func removeMember(kind: ShareListKind, listID: String, userId: String) async throws {
        _ = try await api.removeListMember(
            base: kind.pathBase,
            listID: listID,
            payload: RemoveMemberRequest(userId: userId)
        )
    }

    func leaveList(kind: ShareListKind, listID: String) async throws {
        _ = try await api.leaveList(base: kind.pathBase, listID: listID)
    }

    func searchUsers(query: String) async throws -> [UserSearchResultDTO] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 2 else { return [] }
        return try await api.searchUsers(query: trimmed).users
    }
}
