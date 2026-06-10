import SwiftUI

/// Member management for a shared list: the owner sees role controls, member
/// removal, and a username typeahead to add people; members see the roster
/// plus share-as-text and leave actions.
struct ManageMembersSheet: View {
    let listId: String
    let listName: String
    let kind: ShareListKind
    let myRole: String
    let shareText: String
    let onLeftList: () -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.tdayColors) private var colors

    private let repository = AppContainer.shared.listShareRepository

    @State private var isLoading = true
    @State private var owner: ListMemberDTO?
    @State private var members: [ListMemberDTO] = []
    @State private var searchQuery = ""
    @State private var searchResults: [UserSearchResultDTO] = []
    @State private var errorMessage: String?
    @State private var isWorking = false
    @State private var searchTask: Task<Void, Never>?

    private var isOwner: Bool {
        myRole.caseInsensitiveCompare("OWNER") == .orderedSame
    }

    var body: some View {
        VStack(spacing: 0) {
            TdaySheetHeader(
                title: L("Members"),
                closeAccessibilityLabel: "Close",
                confirmSystemName: nil,
                onClose: { dismiss() }
            )

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 14) {
                    TdaySheetSectionTitle(text: listName)
                    TdaySheetCard {
                        VStack(alignment: .leading, spacing: 0) {
                            if isLoading {
                                HStack {
                                    Spacer()
                                    ProgressView()
                                    Spacer()
                                }
                                .padding(.vertical, 18)
                            } else {
                                if let owner {
                                    memberRow(owner, isOwnerRow: true)
                                }
                                ForEach(members) { member in
                                    memberRow(member, isOwnerRow: false)
                                }
                            }
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                    }

                    if let errorMessage {
                        Text(errorMessage)
                            .font(.tdayRounded(size: 15, weight: .bold))
                            .foregroundStyle(colors.error)
                            .padding(.horizontal, 4)
                    }

                    if isOwner {
                        TdaySheetSectionTitle(text: L("Add member"))
                        TdaySheetCard {
                            VStack(alignment: .leading, spacing: 10) {
                                TextField(
                                    "",
                                    text: $searchQuery,
                                    prompt: Text(L("Search by username"))
                                        .foregroundStyle(colors.onSurfaceVariant.opacity(0.78))
                                )
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .font(.tdayRounded(size: 17, weight: .bold))
                                .padding(.horizontal, 14)
                                .frame(height: 48)
                                .background(
                                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                                        .fill(colors.bottomSheetControlSurface)
                                )
                                .onChange(of: searchQuery) { _, newValue in
                                    scheduleSearch(for: newValue)
                                }

                                if searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).count >= 2 {
                                    if searchResults.isEmpty {
                                        Text(L("No users found"))
                                            .font(.tdayRounded(size: 15, weight: .bold))
                                            .foregroundStyle(colors.onSurfaceVariant)
                                            .padding(.horizontal, 4)
                                    } else {
                                        ForEach(searchResults) { user in
                                            searchResultRow(user)
                                        }
                                    }
                                }
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 14)
                        }
                    } else {
                        actionButton(
                            systemName: "square.and.arrow.up",
                            label: L("Share list"),
                            tint: colors.onSurface,
                            border: colors.onSurfaceVariant.opacity(0.3),
                            background: colors.bottomSheetControlSurface
                        ) {
                            dismiss()
                            presentShareSheet()
                        }

                        actionButton(
                            systemName: "rectangle.portrait.and.arrow.right",
                            label: L("Leave list"),
                            tint: colors.error,
                            border: colors.error.opacity(0.45),
                            background: colors.error.opacity(colors.isDark ? 0.14 : 0.04)
                        ) {
                            leave()
                        }
                    }
                }
                .padding(.horizontal, 18)
                .padding(.top, 14)
                .padding(.bottom, 24)
            }
            .disableVerticalScrollBounce()
        }
        .frame(maxWidth: .infinity, alignment: .top)
        .background(colors.bottomSheetBackground.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.hidden)
        .presentationCornerRadius(34)
        .presentationBackground {
            colors.bottomSheetBackground
                .ignoresSafeArea(.container, edges: .bottom)
        }
        .task {
            await refresh()
        }
    }

    @ViewBuilder
    private func memberRow(_ member: ListMemberDTO, isOwnerRow: Bool) -> some View {
        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(colors.primary.opacity(0.15))
                    .frame(width: 40, height: 40)
                Text(displayName(member.name, member.username).prefix(1).uppercased())
                    .font(.tdayRounded(size: 17, weight: .heavy))
                    .foregroundStyle(colors.primary)
            }

            VStack(alignment: .leading, spacing: 1) {
                Text(displayName(member.name, member.username))
                    .font(.tdayRounded(size: 16, weight: .heavy))
                    .foregroundStyle(colors.onSurface)
                    .lineLimit(1)
                Text("@\(member.username)")
                    .font(.tdayRounded(size: 13, weight: .bold))
                    .foregroundStyle(colors.onSurfaceVariant)
                    .lineLimit(1)
            }

            Spacer(minLength: 8)

            if isOwnerRow {
                rolePill(text: L("Owner"), selected: true, action: nil)
            } else if isOwner {
                let isEditor = member.role.caseInsensitiveCompare("EDITOR") == .orderedSame
                HStack(spacing: 6) {
                    rolePill(text: L("Editor"), selected: isEditor) {
                        guard !isEditor else { return }
                        updateRole(member, role: "EDITOR")
                    }
                    rolePill(text: L("Viewer"), selected: !isEditor) {
                        guard isEditor else { return }
                        updateRole(member, role: "VIEWER")
                    }
                }
                Button {
                    remove(member)
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(colors.error)
                        .frame(width: 28, height: 28)
                        .background(Circle().fill(colors.bottomSheetControlSurface))
                }
                .disabled(isWorking)
                .accessibilityLabel(L("Remove member"))
            } else {
                let isEditor = member.role.caseInsensitiveCompare("EDITOR") == .orderedSame
                rolePill(text: isEditor ? L("Editor") : L("Viewer"), selected: false, action: nil)
            }
        }
        .padding(.vertical, 8)
    }

    @ViewBuilder
    private func rolePill(text: String, selected: Bool, action: (() -> Void)?) -> some View {
        let label = Text(text)
            .font(.tdayRounded(size: 13, weight: .heavy))
            .foregroundStyle(selected ? colors.primary : colors.onSurfaceVariant)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(
                Capsule().fill(selected ? colors.primary.opacity(0.15) : colors.bottomSheetControlSurface)
            )
            .overlay(
                Capsule().stroke(
                    selected ? colors.primary.opacity(0.55) : colors.onSurfaceVariant.opacity(0.3),
                    lineWidth: 1.5
                )
            )
        if let action {
            Button(action: action, label: { label })
                .buttonStyle(.plain)
                .disabled(isWorking)
        } else {
            label
        }
    }

    @ViewBuilder
    private func searchResultRow(_ user: UserSearchResultDTO) -> some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 1) {
                Text(displayName(user.name, user.username))
                    .font(.tdayRounded(size: 16, weight: .heavy))
                    .foregroundStyle(colors.onSurface)
                    .lineLimit(1)
                Text("@\(user.username)")
                    .font(.tdayRounded(size: 13, weight: .bold))
                    .foregroundStyle(colors.onSurfaceVariant)
                    .lineLimit(1)
            }

            Spacer(minLength: 8)

            Button {
                add(user)
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "plus")
                        .font(.system(size: 12, weight: .bold))
                    Text(L("Add"))
                        .font(.tdayRounded(size: 13, weight: .heavy))
                }
                .foregroundStyle(colors.primary)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Capsule().fill(colors.primary.opacity(0.15)))
            }
            .buttonStyle(.plain)
            .disabled(isWorking)
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private func actionButton(
        systemName: String,
        label: String,
        tint: Color,
        border: Color,
        background: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            action()
        } label: {
            HStack(spacing: 12) {
                Image(systemName: systemName)
                    .font(.system(size: 20, weight: .semibold))
                    .frame(width: 28, height: 28)
                Text(label)
                    .font(.tdayRounded(size: 18, weight: .heavy))
                Spacer(minLength: 0)
            }
            .foregroundStyle(tint)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity)
            .background(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(background)
            )
            .overlay {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(border, lineWidth: 1.5)
            }
        }
        .buttonStyle(.plain)
        .disabled(isWorking)
    }

    private func displayName(_ name: String?, _ username: String) -> String {
        let trimmed = name?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? username : trimmed
    }

    private func refresh() async {
        do {
            let response = try await repository.fetchMembers(kind: kind, listID: listId)
            owner = response.owner
            members = response.members
            isLoading = false
        } catch {
            errorMessage = error.localizedDescription
            isLoading = false
        }
    }

    private func scheduleSearch(for query: String) {
        searchTask?.cancel()
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 2 else {
            searchResults = []
            return
        }
        searchTask = Task {
            try? await Task.sleep(nanoseconds: 250_000_000)
            guard !Task.isCancelled else { return }
            let users = (try? await repository.searchUsers(query: trimmed)) ?? []
            guard !Task.isCancelled else { return }
            let existingIds = Set([owner?.userId].compactMap { $0 } + members.map(\.userId))
            searchResults = users.filter { !existingIds.contains($0.id) }
        }
    }

    private func add(_ user: UserSearchResultDTO) {
        runMembershipAction {
            _ = try await repository.addMember(kind: kind, listID: listId, username: user.username, role: "EDITOR")
            searchQuery = ""
            searchResults = []
        }
    }

    private func updateRole(_ member: ListMemberDTO, role: String) {
        runMembershipAction {
            try await repository.updateMemberRole(kind: kind, listID: listId, userId: member.userId, role: role)
        }
    }

    private func remove(_ member: ListMemberDTO) {
        runMembershipAction {
            try await repository.removeMember(kind: kind, listID: listId, userId: member.userId)
        }
    }

    private func leave() {
        Task {
            isWorking = true
            errorMessage = nil
            do {
                try await repository.leaveList(kind: kind, listID: listId)
                isWorking = false
                dismiss()
                onLeftList()
            } catch {
                isWorking = false
                errorMessage = error.localizedDescription
            }
        }
    }

    private func runMembershipAction(_ action: @escaping () async throws -> Void) {
        Task {
            isWorking = true
            errorMessage = nil
            do {
                try await action()
                isWorking = false
                await refresh()
            } catch {
                isWorking = false
                errorMessage = error.localizedDescription
            }
        }
    }

    // The share sheet is presented after this sheet dismisses, so it anchors to
    // the screen underneath instead of a view that is going away.
    private func presentShareSheet() {
        let activityController = UIActivityViewController(
            activityItems: [shareText],
            applicationActivities: nil
        )
        guard
            let scene = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene })
                .first(where: { $0.activationState == .foregroundActive }),
            let rootController = scene.keyWindow?.rootViewController
        else {
            return
        }
        var presenter = rootController
        while let presented = presenter.presentedViewController {
            presenter = presented
        }
        activityController.popoverPresentationController?.sourceView = presenter.view
        presenter.present(activityController, animated: true)
    }
}
