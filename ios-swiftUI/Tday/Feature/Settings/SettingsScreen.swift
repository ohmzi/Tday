import SwiftUI

private let settingsSegmentedControlAccentColor = Color.tdayTodayBlue

/// Which inline account editor is expanded. Only one may be open at a time.
private enum ProfileEditorExpansion: Equatable {
    case none
    case name
    case password
    case securityQuestions
}

struct SettingsScreen: View {
    let viewModel: AppViewModel

    @Environment(\.dismiss) private var dismiss
    @Environment(\.tdayColors) private var colors
    @State private var settingsScrollOffset: CGFloat = 0
    @State private var showingReminderSelector = false
    @State private var showingDayAheadSelector = false
    @State private var showingLanguageSelector = false
    @State private var profileEditor: ProfileEditorExpansion = .none

    private var titleCollapseProgress: CGFloat {
        rawTitleCollapseProgress
    }

    private var rawTitleCollapseProgress: CGFloat {
        let distance = TodoTimelineMetrics.titleCollapseDistance
        guard distance > 0 else { return 0 }
        return min(max(settingsScrollOffset / distance, 0), 1)
    }

    var body: some View {
        settingsContent
        .background(colors.background)
        .navigationBackButtonBehavior()
        .navigationTitleTypography(
            largeTitleColor: colors.onSurface,
            inlineTitleColor: colors.onSurface,
            backgroundColor: colors.background
        )
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .navigationBar)
        .safeAreaInset(edge: .top, spacing: 0) {
            TimelineTopBar(
                title: L("Settings"),
                accentColor: colors.onSurface,
                collapseProgress: titleCollapseProgress,
                onBack: { dismiss() },
                actions: []
            )
        }
        .overlay {
            if showingReminderSelector {
                SettingsReminderSelectorOverlay(
                    selectedReminder: viewModel.selectedReminder,
                    onSelect: viewModel.setDefaultReminder,
                    onDismiss: {
                        showingReminderSelector = false
                    }
                )
                .transition(.opacity.combined(with: .scale(scale: 0.97)))
            }
        }
        .overlay {
            if showingDayAheadSelector {
                SettingsDayAheadSelectorOverlay(
                    selected: viewModel.dayAheadOption,
                    onSelect: viewModel.setDayAhead,
                    onDismiss: {
                        showingDayAheadSelector = false
                    }
                )
                .transition(.opacity.combined(with: .scale(scale: 0.97)))
            }
        }
        .overlay {
            if showingLanguageSelector {
                SettingsLanguageSelectorOverlay(
                    current: viewModel.appLanguage,
                    onSelect: viewModel.setAppLanguage,
                    onDismiss: {
                        showingLanguageSelector = false
                    }
                )
                .transition(.opacity.combined(with: .scale(scale: 0.97)))
            }
        }
        .task {
            viewModel.refreshSyncStatusFromCache()
            await viewModel.refreshAiSummarySetting()
            await viewModel.refreshVersionInfo()
        }
        .animation(.spring(response: 0.24, dampingFraction: 0.9), value: showingReminderSelector)
        .animation(.spring(response: 0.24, dampingFraction: 0.9), value: showingLanguageSelector)
        .animation(.spring(response: 0.28, dampingFraction: 0.9), value: profileEditor)
    }

    private var settingsContent: some View {
        List {
            settingsHeroTitleRow

            if !viewModel.isLocalMode {
                settingsListRow {
                    SettingsProfileCard(viewModel: viewModel, expansion: $profileEditor)
                }
            }

            settingsListRow {
                SettingsSectionCard {
                    SettingsSectionTitle("Appearance")
                    SettingsThemeSelector(
                        selectedMode: viewModel.themeMode,
                        onSelect: viewModel.setThemeMode
                    )
                    SettingsDivider()
                    SettingsSectionTitle("Reminders")
                    SettingsReminderSelector(
                        selectedReminder: viewModel.selectedReminder,
                        onOpen: {
                            showingReminderSelector = true
                        }
                    )
                    SettingsDivider()
                    SettingsDayAheadSelector(
                        selected: viewModel.dayAheadOption,
                        onOpen: {
                            showingDayAheadSelector = true
                        }
                    )
                    SettingsDivider()
                    SettingsQuietHoursSection()
                    SettingsDivider()
                    SettingsSectionTitle("Language")
                    SettingsLanguageSelector(
                        currentLanguage: viewModel.appLanguage,
                        onOpen: {
                            showingLanguageSelector = true
                        }
                    )
                }
            }

            if !viewModel.isLocalMode {
                settingsListRow {
                    SettingsSectionCard {
                        SettingsSectionTitle("AI task summary")
                        SettingsAiSummaryRow(viewModel: viewModel)
                    }
                }
            }

            settingsListRow {
                SettingsSectionCard {
                    SettingsWorkspaceContent(
                        syncStatus: viewModel.syncStatus,
                        onSyncNow: {
                            Task { await viewModel.manualSync() }
                        }
                    )

                    SettingsDivider()

                    SettingsListRow(
                        title: "App Version",
                        value: "v\(viewModel.currentVersionName)",
                        action: {
                            viewModel.navigationPath.append(.latestRelease)
                        }
                    )

                    if viewModel.hasUpdate, let latestVersionName = viewModel.latestVersionName {
                        Text("v\(latestVersionName) available")
                            .font(.tdayRounded(size: 11, weight: .heavy))
                            .foregroundStyle(colors.secondary)
                    }

                    SettingsDivider()

                    SettingsListRow(
                        title: L("How-To & Tips"),
                        value: nil,
                        action: {
                            viewModel.navigationPath.append(.helpGuide(topic: nil))
                        }
                    )

                    if !viewModel.isLocalMode, let backendVersion = viewModel.backendVersion {
                        SettingsServerVersionRow(
                            backendVersion: backendVersion,
                            versionCheckResult: viewModel.versionCheckResult
                        )
                    }

                    if !viewModel.isLocalMode {
                        SettingsDivider()

                        SettingsListRow(
                            title: "Sign out",
                            value: nil,
                            titleColor: colors.error,
                            showChevron: false,
                            action: {
                                Task { await viewModel.logout() }
                            }
                        )
                    }
                }
            }

            settingsListRow {
                DataTransferCard(viewModel: viewModel)
            }

            Color.clear
                .frame(height: TodoTimelineMetrics.titleCollapseDistance + TodoTimelineMetrics.topBarRowHeight + 24)
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
                .disableVerticalScrollBounce()
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .contentMargins(.top, 0, for: .scrollContent)
        .listSectionSpacing(0)
        .environment(\.defaultMinListRowHeight, 1)
        .disableVerticalScrollBounce()
    }

    private var settingsHeroTitleRow: some View {
        TimelineExpandedTitleRow(
            title: L("Settings"),
            accentColor: colors.onSurface,
            collapseProgress: titleCollapseProgress
        )
        .background {
            TimelineScrollOffsetObserver { settingsScrollOffset = $0 }
                .frame(width: 0, height: 0)
        }
        .onVerticalScrollSnap(collapseDistance: TodoTimelineMetrics.titleCollapseDistance)
        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }

    private func settingsListRow<Content: View>(
        topInset: CGFloat = 0,
        @ViewBuilder content: () -> Content
    ) -> some View {
        content()
            .listRowInsets(
                EdgeInsets(
                    top: topInset,
                    leading: TodoTimelineMetrics.horizontalPadding,
                    bottom: 12,
                    trailing: TodoTimelineMetrics.horizontalPadding
                )
            )
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
    }
}

private struct SettingsProfileCard: View {
    let viewModel: AppViewModel
    @Binding var expansion: ProfileEditorExpansion

    @Environment(\.tdayColors) private var colors

    var body: some View {
        SettingsSectionCard {
            SettingsNameSection(viewModel: viewModel, expansion: $expansion)

            if let username = viewModel.user?.username, !username.isEmpty {
                SettingsDivider()
                SettingsUsernameRow(username: username)
            }

            SettingsDivider()
            SettingsPasswordSection(viewModel: viewModel, expansion: $expansion)

            SettingsDivider()
            SettingsSecurityQuestionsSection(viewModel: viewModel, expansion: $expansion)

            SettingsDivider()
            Text("Role: \(viewModel.user?.role ?? "USER")")
                .font(.tdayRounded(size: 13, weight: .bold))
                .foregroundStyle(colors.onSurface.opacity(0.58))
        }
    }
}

// MARK: - Quiet hours

/// "Hold reminders between HH:MM and HH:MM" — entirely local; the scheduler shifts any
/// reminder inside the window to the window end.
private struct SettingsQuietHoursSection: View {
    @Environment(\.tdayColors) private var colors
    private let store = QuietHoursStore()
    @State private var enabled: Bool
    @State private var startTime: Date
    @State private var endTime: Date

    init() {
        let store = QuietHoursStore()
        _enabled = State(initialValue: store.isEnabled)
        _startTime = State(initialValue: Self.date(fromMinute: store.startMinute))
        _endTime = State(initialValue: Self.date(fromMinute: store.endMinute))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Toggle(isOn: $enabled) {
                Text(L("Quiet hours"))
                    .font(.body.weight(.heavy))
                    .foregroundStyle(colors.onSurface)
            }
            .tint(colors.secondary)
            .onChange(of: enabled) { _, value in store.isEnabled = value }

            if enabled {
                HStack {
                    Text(L("Start"))
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(colors.onSurfaceVariant)
                    Spacer()
                    DatePicker("", selection: $startTime, displayedComponents: .hourAndMinute)
                        .labelsHidden()
                        .onChange(of: startTime) { _, value in store.startMinute = Self.minute(from: value) }
                }
                HStack {
                    Text(L("End"))
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(colors.onSurfaceVariant)
                    Spacer()
                    DatePicker("", selection: $endTime, displayedComponents: .hourAndMinute)
                        .labelsHidden()
                        .onChange(of: endTime) { _, value in store.endMinute = Self.minute(from: value) }
                }
            }
        }
    }

    private static func date(fromMinute minute: Int) -> Date {
        Calendar.current.date(bySettingHour: minute / 60, minute: minute % 60, second: 0, of: Date()) ?? Date()
    }

    private static func minute(from date: Date) -> Int {
        let comps = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (comps.hour ?? 0) * 60 + (comps.minute ?? 0)
    }
}

// MARK: - Account editors

private struct SettingsNameSection: View {
    let viewModel: AppViewModel
    @Binding var expansion: ProfileEditorExpansion

    @Environment(\.tdayColors) private var colors

    @State private var draft = ""
    @State private var isBusy = false
    @State private var errorMessage: String?

    private var isEditing: Bool { expansion == .name }
    private var trimmed: String { draft.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var canSave: Bool {
        !isBusy && !trimmed.isEmpty && trimmed != (viewModel.user?.name ?? "")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    SettingsFieldLabel("Name")
                    Text(viewModel.user?.name ?? L("Unknown user"))
                        .font(.tdayRounded(size: 20, weight: .heavy))
                        .foregroundStyle(colors.onSurface)
                }

                Spacer(minLength: 12)

                if !isEditing {
                    SettingsInlineEditButton(title: "Edit", systemImage: "pencil") {
                        beginEditing()
                    }
                }
            }

            if isEditing {
                VStack(alignment: .leading, spacing: 12) {
                    SettingsEditField(
                        title: "Name",
                        text: $draft,
                        submitLabel: .done,
                        onSubmit: { save() }
                    )

                    if let errorMessage {
                        SettingsEditorError(message: errorMessage)
                    }

                    SettingsEditorActions(
                        saveTitle: isBusy ? "Saving..." : "Save",
                        isBusy: isBusy,
                        canSave: canSave,
                        onCancel: cancel,
                        onSave: save
                    )
                }
            }
        }
    }

    private func beginEditing() {
        draft = viewModel.user?.name ?? ""
        errorMessage = nil
        expansion = .name
    }

    private func cancel() {
        errorMessage = nil
        expansion = .none
    }

    private func save() {
        guard canSave else { return }
        Task {
            isBusy = true
            errorMessage = nil
            let result = await viewModel.updateDisplayName(trimmed)
            isBusy = false
            switch result {
            case .success:
                expansion = .none
                viewModel.container.snackbarManager.show(L("Name updated successfully"), kind: .success)
            case let .failure(message):
                errorMessage = message
            }
        }
    }
}

private struct SettingsUsernameRow: View {
    let username: String

    @Environment(\.tdayColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            SettingsFieldLabel("Username")
            Text(username)
                .font(.tdayRounded(size: 16, weight: .bold))
                .foregroundStyle(colors.onSurface.opacity(0.72))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct SettingsPasswordSection: View {
    let viewModel: AppViewModel
    @Binding var expansion: ProfileEditorExpansion

    @Environment(\.tdayColors) private var colors

    @State private var currentPassword = ""
    @State private var newPassword = ""
    @State private var confirmPassword = ""
    @State private var isBusy = false
    @State private var errorMessage: String?

    private var isEditing: Bool { expansion == .password }
    private var fieldsFilled: Bool {
        !currentPassword.isEmpty && !newPassword.isEmpty && !confirmPassword.isEmpty
    }
    private var canSave: Bool { !isBusy && fieldsFilled }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    SettingsFieldLabel("Password")
                    Text(verbatim: "••••••••")
                        .font(.tdayRounded(size: 20, weight: .heavy))
                        .foregroundStyle(colors.onSurface.opacity(0.8))
                }

                Spacer(minLength: 12)

                if !isEditing {
                    SettingsInlineEditButton(title: "Change", systemImage: "key.fill") {
                        beginEditing()
                    }
                }
            }

            if isEditing {
                VStack(alignment: .leading, spacing: 12) {
                    SettingsEditField(
                        title: "Current password",
                        text: $currentPassword,
                        isSecure: true,
                        textContentType: .password,
                        submitLabel: .next
                    )
                    SettingsEditField(
                        title: "New password",
                        text: $newPassword,
                        isSecure: true,
                        textContentType: .newPassword,
                        submitLabel: .next
                    )
                    SettingsEditField(
                        title: "Confirm new password",
                        text: $confirmPassword,
                        isSecure: true,
                        textContentType: .newPassword,
                        submitLabel: .done,
                        onSubmit: { save() }
                    )

                    Text("Password must be at least 8 characters, with an uppercase letter and a special character.")
                        .font(.tdayRounded(size: 12, weight: .bold))
                        .foregroundStyle(colors.onSurface.opacity(0.5))
                        .fixedSize(horizontal: false, vertical: true)

                    if let errorMessage {
                        SettingsEditorError(message: errorMessage)
                    }

                    Button {
                        viewModel.navigate(to: .forgotPassword)
                    } label: {
                        Text(L("Forgot password?"))
                            .font(.tdayRounded(size: 13, weight: .heavy))
                            .foregroundStyle(colors.secondary)
                    }
                    .buttonStyle(.plain)

                    SettingsEditorActions(
                        saveTitle: isBusy ? "Saving..." : "Save",
                        isBusy: isBusy,
                        canSave: canSave,
                        onCancel: cancel,
                        onSave: save
                    )
                }
            }
        }
    }

    private func beginEditing() {
        resetFields()
        expansion = .password
    }

    private func cancel() {
        resetFields()
        expansion = .none
    }

    private func resetFields() {
        currentPassword = ""
        newPassword = ""
        confirmPassword = ""
        errorMessage = nil
    }

    /// Client-side mirror of the server password rules (≥8 chars, ≥1 uppercase,
    /// ≥1 special character) plus the confirmation match.
    private func validationError() -> String? {
        guard newPassword.count >= 8 else {
            return L("Password must be at least 8 characters")
        }
        guard newPassword.contains(where: \.isUppercase) else {
            return L("Password must include at least one uppercase letter")
        }
        guard newPassword.contains(where: { !$0.isLetter && !$0.isNumber }) else {
            return L("Password must include at least one special character")
        }
        guard newPassword == confirmPassword else {
            return L("Passwords do not match")
        }
        return nil
    }

    private func save() {
        guard canSave else { return }
        if let validation = validationError() {
            errorMessage = validation
            return
        }
        Task {
            isBusy = true
            errorMessage = nil
            let result = await viewModel.changePassword(
                currentPassword: currentPassword,
                newPassword: newPassword
            )
            isBusy = false
            switch result {
            case .success:
                resetFields()
                expansion = .none
                viewModel.container.snackbarManager.show(L("Password changed successfully"), kind: .success)
            case let .failure(message):
                errorMessage = message
            }
        }
    }
}

private struct SettingsSecurityQuestionsSection: View {
    let viewModel: AppViewModel
    @Binding var expansion: ProfileEditorExpansion

    @Environment(\.tdayColors) private var colors

    @State private var status: SecurityQuestionStatusResponse?
    @State private var questions: [SecurityQuestion] = []
    @State private var questionId1: Int?
    @State private var questionId2: Int?
    @State private var questionId3: Int?
    @State private var answer1 = ""
    @State private var answer2 = ""
    @State private var answer3 = ""
    @State private var currentPassword = ""
    @State private var isBusy = false
    @State private var errorMessage: String?

    private var isEditing: Bool { expansion == .securityQuestions }
    // Already-configured accounts confirm with their password; legacy accounts that
    // never set questions can do so here without one.
    private var configured: Bool { status.map { !$0.requireSecurityQuestions } ?? false }

    private var summary: String {
        guard let status else { return "—" }
        return status.requireSecurityQuestions ? L("Not configured") : L("Configured")
    }

    private var canSave: Bool {
        guard let id1 = questionId1, let id2 = questionId2, let id3 = questionId3,
              Set([id1, id2, id3]).count == 3 else {
            return false
        }
        let answersFilled = ![answer1, answer2, answer3].contains {
            $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }
        let passwordOk = !configured || !currentPassword.isEmpty
        return !isBusy && answersFilled && passwordOk
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    SettingsFieldLabel("Security questions")
                    Text(summary)
                        .font(.tdayRounded(size: 20, weight: .heavy))
                        .foregroundStyle(colors.onSurface.opacity(0.8))
                }

                Spacer(minLength: 12)

                if !isEditing {
                    SettingsInlineEditButton(title: "Change", systemImage: "lock.shield.fill") {
                        beginEditing()
                    }
                }
            }

            if isEditing {
                VStack(alignment: .leading, spacing: 12) {
                    if configured {
                        SettingsEditField(
                            title: "Current password",
                            text: $currentPassword,
                            isSecure: true,
                            textContentType: .password,
                            submitLabel: .next
                        )
                    }

                    SecurityQuestionMenu(
                        title: "Question 1",
                        selection: $questionId1,
                        options: questions.filter { $0.id != questionId2 && $0.id != questionId3 }
                    )
                    SettingsEditField(title: "Answer", text: $answer1, submitLabel: .next)

                    SecurityQuestionMenu(
                        title: "Question 2",
                        selection: $questionId2,
                        options: questions.filter { $0.id != questionId1 && $0.id != questionId3 }
                    )
                    SettingsEditField(title: "Answer", text: $answer2, submitLabel: .next)

                    SecurityQuestionMenu(
                        title: "Question 3",
                        selection: $questionId3,
                        options: questions.filter { $0.id != questionId1 && $0.id != questionId2 }
                    )
                    SettingsEditField(title: "Answer", text: $answer3, submitLabel: .done, onSubmit: { save() })

                    if let errorMessage {
                        SettingsEditorError(message: errorMessage)
                    }

                    SettingsEditorActions(
                        saveTitle: isBusy ? "Saving..." : "Save",
                        isBusy: isBusy,
                        canSave: canSave,
                        onCancel: cancel,
                        onSave: save
                    )
                }
            }
        }
        .task { await loadStatus() }
    }

    private func loadStatus() async {
        if status == nil {
            status = await viewModel.securityQuestionStatus()
        }
    }

    private func beginEditing() {
        Task {
            if questions.isEmpty {
                questions = await viewModel.loadAllSecurityQuestions()
            }
            seedSelections()
            currentPassword = ""
            answer1 = ""
            answer2 = ""
            answer3 = ""
            errorMessage = nil
            expansion = .securityQuestions
        }
    }

    // Seed the three selects from the user's existing questions, filling gaps with the
    // first unused catalogue entries.
    private func seedSelections() {
        let preferred = (status?.questionIds ?? []).filter { id in questions.contains { $0.id == id } }
        let filler = questions.map(\.id).filter { !preferred.contains($0) }
        var seeded: [Int] = []
        for id in preferred + filler where !seeded.contains(id) {
            seeded.append(id)
            if seeded.count == 3 { break }
        }
        questionId1 = seeded.indices.contains(0) ? seeded[0] : nil
        questionId2 = seeded.indices.contains(1) ? seeded[1] : nil
        questionId3 = seeded.indices.contains(2) ? seeded[2] : nil
    }

    private func cancel() {
        currentPassword = ""
        answer1 = ""
        answer2 = ""
        answer3 = ""
        errorMessage = nil
        expansion = .none
    }

    private func save() {
        guard canSave else { return }
        guard let id1 = questionId1, let id2 = questionId2, let id3 = questionId3,
              Set([id1, id2, id3]).count == 3 else {
            errorMessage = L("Choose three different questions")
            return
        }
        let t1 = answer1.trimmingCharacters(in: .whitespacesAndNewlines)
        let t2 = answer2.trimmingCharacters(in: .whitespacesAndNewlines)
        let t3 = answer3.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t1.isEmpty, !t2.isEmpty, !t3.isEmpty else {
            errorMessage = L("Please answer all three questions")
            return
        }
        Task {
            isBusy = true
            errorMessage = nil
            let result = await viewModel.updateSecurityQuestions(
                currentPassword: configured ? currentPassword : "",
                answers: [
                    SecurityAnswerInput(questionId: id1, answer: t1),
                    SecurityAnswerInput(questionId: id2, answer: t2),
                    SecurityAnswerInput(questionId: id3, answer: t3),
                ]
            )
            isBusy = false
            switch result {
            case .success:
                status = SecurityQuestionStatusResponse(
                    questionIds: [id1, id2, id3],
                    requireSecurityQuestions: false
                )
                cancel()
                viewModel.container.snackbarManager.show(L("Security questions updated"), kind: .success)
            case let .failure(message):
                errorMessage = message
            }
        }
    }
}

// MARK: - Account editor building blocks

private struct SettingsFieldLabel: View {
    let title: String

    @Environment(\.tdayColors) private var colors

    init(_ title: String) {
        self.title = title
    }

    var body: some View {
        Text(L(title))
            .font(.tdayRounded(size: 13, weight: .heavy))
            .foregroundStyle(colors.onSurface.opacity(0.5))
    }
}

private struct SettingsInlineEditButton: View {
    let title: String
    let systemImage: String
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: action) {
            HStack(spacing: 5) {
                Image(systemName: systemImage)
                    .font(.system(size: 12, weight: .heavy))
                Text(L(title))
                    .font(.tdayRounded(size: 14, weight: .heavy))
            }
            .foregroundStyle(colors.secondary)
            .padding(.horizontal, 14)
            .frame(height: 34)
            .background(Capsule(style: .continuous).fill(colors.secondary.opacity(0.12)))
        }
        .buttonStyle(.plain)
    }
}

private struct SettingsEditorError: View {
    let message: String

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Text(message)
            .font(.tdayRounded(size: 13, weight: .bold))
            .foregroundStyle(colors.error)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct SettingsEditorActions: View {
    let saveTitle: String
    let isBusy: Bool
    let canSave: Bool
    let onCancel: () -> Void
    let onSave: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        HStack(spacing: 10) {
            Button(action: onCancel) {
                Text(L("Cancel"))
                    .font(.tdayRounded(size: 15, weight: .heavy))
                    .foregroundStyle(colors.onSurface.opacity(0.7))
                    .frame(maxWidth: .infinity)
                    .frame(height: 46)
                    .background(Capsule(style: .continuous).fill(colors.onSurface.opacity(0.06)))
            }
            .buttonStyle(.plain)
            .disabled(isBusy)

            Button(action: onSave) {
                Text(L(saveTitle))
                    .font(.tdayRounded(size: 15, weight: .heavy))
                    .foregroundStyle(canSave ? colors.onPrimary : colors.onSurfaceVariant.opacity(0.65))
                    .frame(maxWidth: .infinity)
                    .frame(height: 46)
                    .background(Capsule(style: .continuous).fill(canSave ? colors.primary : colors.surfaceVariant.opacity(0.95)))
            }
            .buttonStyle(.plain)
            .opacity(canSave ? 1 : 0.72)
            .disabled(!canSave)
        }
    }
}

private struct SettingsEditField: View {
    let title: String
    @Binding var text: String
    var isSecure = false
    var textContentType: UITextContentType?
    var submitLabel: SubmitLabel = .done
    var onSubmit: (() -> Void)? = nil

    @Environment(\.tdayColors) private var colors
    @FocusState private var isFocused: Bool
    @State private var isRevealed = false

    var body: some View {
        HStack(spacing: 8) {
            Group {
                if isSecure && !isRevealed {
                    SecureField("", text: $text, prompt: prompt)
                        .textContentType(textContentType)
                } else {
                    TextField("", text: $text, prompt: prompt)
                        .textContentType(textContentType)
                        .textInputAutocapitalization(isSecure ? .never : .words)
                        .autocorrectionDisabled(isSecure)
                }
            }
            .focused($isFocused)
            .submitLabel(submitLabel)
            .onSubmit { onSubmit?() }
            .font(.tdayRounded(size: 15, weight: .bold))
            .foregroundStyle(colors.onSurface)
            .tint(colors.primary)

            if isSecure && !text.isEmpty {
                Button {
                    isRevealed.toggle()
                } label: {
                    Image(systemName: isRevealed ? "eye.slash" : "eye")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(colors.onSurface.opacity(0.4))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .frame(height: 50)
        .background {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(colors.onSurface.opacity(0.04))
                .overlay(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .stroke(
                            isFocused ? colors.primary.opacity(0.82) : colors.onSurface.opacity(0.12),
                            lineWidth: isFocused ? 1.1 : 1
                        )
                )
        }
        .accessibilityLabel(L(title))
    }

    private var prompt: Text {
        Text(L(title)).foregroundStyle(colors.onSurface.opacity(0.42))
    }
}

private struct SettingsWorkspaceContent: View {
    let syncStatus: MobileSyncStatus
    let onSyncNow: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            SettingsSectionTitle("Workspace")

            if syncStatus.isLocalMode {
                VStack(alignment: .leading, spacing: 6) {
                    Text(syncStatus.title)
                        .font(.tdayRounded(size: 17, weight: .heavy))
                        .foregroundStyle(colors.onSurface)

                    Text(syncStatus.statusText)
                        .font(.tdayRounded(size: 13, weight: .bold))
                        .foregroundStyle(colors.onSurface.opacity(0.62))
                        .fixedSize(horizontal: false, vertical: true)
                }
            } else {
                HStack(spacing: 12) {
                    Text(syncStatus.title)
                        .font(.tdayRounded(size: 17, weight: .heavy))
                        .foregroundStyle(colors.onSurface)

                    Spacer(minLength: 12)

                    Text(syncStatus.isOffline ? L("Offline") : L("Up to date"))
                        .font(.tdayRounded(size: 14, weight: .heavy))
                        .foregroundStyle(syncStatus.isOffline ? colors.error : Color.tdayFloaterGreen)
                }

                if syncStatus.isOffline {
                    SettingsDivider()

                    SettingsSyncFactRow(label: L("Last synced"), value: syncStatus.lastSyncedText())

                    Text(L("Changes will sync when connection returns."))
                        .font(.tdayRounded(size: 13, weight: .bold))
                        .foregroundStyle(colors.onSurface.opacity(0.62))
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    Button(action: onSyncNow) {
                        Text(syncStatus.isManualSyncing ? L("Syncing...") : L("Sync now"))
                            .font(.tdayRounded(size: 14, weight: .heavy))
                            .foregroundStyle(syncStatus.isManualSyncing ? colors.onSurface.opacity(0.45) : colors.secondary)
                            .frame(maxWidth: .infinity, alignment: .trailing)
                    }
                    .buttonStyle(.plain)
                    .disabled(syncStatus.isManualSyncing)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct SettingsSyncFactRow: View {
    let label: String
    let value: String

    @Environment(\.tdayColors) private var colors

    var body: some View {
        HStack {
            Text(label)
                .font(.tdayRounded(size: 13, weight: .bold))
                .foregroundStyle(colors.onSurface.opacity(0.56))

            Spacer(minLength: 12)

            Text(value)
                .font(.tdayRounded(size: 13, weight: .heavy))
                .foregroundStyle(colors.onSurface.opacity(0.72))
                .lineLimit(1)
                .minimumScaleFactor(0.78)
        }
        .frame(minHeight: 24)
    }
}

private struct SettingsSectionCard<Content: View>: View {
    @ViewBuilder let content: Content

    @Environment(\.tdayColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 18)
        .padding(.vertical, 18)
        .background(colors.surface, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(colors.onSurface.opacity(0.05), lineWidth: 1)
        }
    }
}

private struct SettingsSectionTitle: View {
    let title: String

    @Environment(\.tdayColors) private var colors

    init(_ title: String) {
        self.title = title
    }

    var body: some View {
        // L() routes the literal through the in-app language bundle; non-catalog
        // (dynamic) strings pass through unchanged.
        Text(L(title))
            .font(.tdayRounded(size: 22, weight: .heavy))
            .foregroundStyle(colors.onSurface)
    }
}

private struct SettingsThemeSelector: View {
    let selectedMode: AppThemeMode
    let onSelect: (AppThemeMode) -> Void

    var body: some View {
        let modes = AppThemeMode.allCases

        TdayNativeSegmentedControl(
            labels: modes.map(\.label),
            selectedIndex: modes.firstIndex(of: selectedMode) ?? 0,
            accentColor: settingsSegmentedControlAccentColor,
            onSelect: { index in
                guard modes.indices.contains(index) else {
                    return
                }
                onSelect(modes[index])
            }
        )
        .frame(maxWidth: .infinity)
        .frame(height: TdayNativeSegmentedControlMetrics.height)
    }
}

private struct SettingsLanguageSelector: View {
    let currentLanguage: String
    let onOpen: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: onOpen) {
            SettingsRowLabel(
                title: "Language",
                value: Self.label(for: currentLanguage),
                valueColor: colors.secondary,
                showChevron: true
            )
        }
        .buttonStyle(.plain)
    }

    static func label(for stored: String) -> String {
        let lang = AppLanguage(rawValue: stored) ?? .system
        return lang == .system ? L("System default") : lang.endonym
    }
}

private struct SettingsLanguageSelectorOverlay: View {
    let current: String
    let onSelect: (String) -> Void
    let onDismiss: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        ZStack {
            colors.bottomSheetScrim
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)

            TdayCenteredSelectorCard(title: L("Language")) {
                ForEach(Array(AppLanguage.allCases.enumerated()), id: \.element.id) { index, lang in
                    if index > 0 {
                        TdaySheetDivider(horizontalPadding: 20, opacity: 0.16)
                    }

                    TdayCenteredSelectorRow(
                        title: lang == .system ? L("System default") : lang.endonym,
                        swatchColor: .clear,
                        selected: lang.rawValue == current
                    ) {
                        onSelect(lang.rawValue)
                        onDismiss()
                    }
                }
            }
            .padding(.horizontal, 54)
        }
    }
}

private struct SettingsReminderSelector: View {
    let selectedReminder: ReminderOption
    let onOpen: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: onOpen) {
            SettingsRowLabel(
                title: "Default reminder",
                value: selectedReminder.label,
                valueColor: colors.secondary,
                showChevron: true
            )
        }
        .buttonStyle(.plain)
    }
}

private struct SettingsReminderSelectorOverlay: View {
    let selectedReminder: ReminderOption
    let onSelect: (ReminderOption) -> Void
    let onDismiss: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        ZStack {
            colors.bottomSheetScrim
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)

            TdayCenteredSelectorCard(title: L("Default reminder")) {
                ForEach(Array(ReminderOption.allCases.enumerated()), id: \.element.id) { index, option in
                    if index > 0 {
                        TdaySheetDivider(horizontalPadding: 20, opacity: 0.16)
                    }

                    TdayCenteredSelectorRow(
                        title: option.label,
                        swatchColor: reminderSwatchColor(option, colors: colors),
                        selected: option == selectedReminder
                    ) {
                        onSelect(option)
                        onDismiss()
                    }
                }
            }
            .padding(.horizontal, 54)
        }
    }

    private func reminderSwatchColor(_ option: ReminderOption, colors: TdayColors) -> Color {
        switch option {
        case .none:
            return colors.onSurfaceVariant.opacity(0.35)
        case .atTime:
            return Color.tdayTodayBlue
        case .fiveMinutes:
            return Color(red: 0.44, green: 0.53, blue: 0.78)
        case .fifteenMinutes:
            return Color(red: 0.78, green: 0.58, blue: 0.40)
        case .oneHour:
            return Color(red: 0.56, green: 0.70, blue: 0.48)
        case .oneDay:
            return Color(red: 0.61, green: 0.54, blue: 0.82)
        case .twoDays:
            return Color(red: 0.78, green: 0.48, blue: 0.58)
        }
    }
}

private struct SettingsDayAheadSelector: View {
    let selected: DayAheadOption
    let onOpen: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: onOpen) {
            SettingsRowLabel(
                title: "Day Ahead digest",
                value: selected.label,
                valueColor: colors.secondary,
                showChevron: true
            )
        }
        .buttonStyle(.plain)
    }
}

private struct SettingsDayAheadSelectorOverlay: View {
    let selected: DayAheadOption
    let onSelect: (DayAheadOption) -> Void
    let onDismiss: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        ZStack {
            colors.bottomSheetScrim
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)

            TdayCenteredSelectorCard(title: L("Day Ahead digest")) {
                ForEach(Array(DayAheadOption.allCases.enumerated()), id: \.element.id) { index, option in
                    if index > 0 {
                        TdaySheetDivider(horizontalPadding: 20, opacity: 0.16)
                    }

                    TdayCenteredSelectorRow(
                        title: option.label,
                        swatchColor: option == .off ? colors.onSurfaceVariant.opacity(0.4) : colors.secondary,
                        selected: option == selected
                    ) {
                        onSelect(option)
                        onDismiss()
                    }
                }
            }
            .padding(.horizontal, 54)
        }
    }
}

private struct SettingsAiSummaryRow: View {
    let viewModel: AppViewModel

    @Environment(\.tdayColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Summary")
                    .font(.tdayRounded(size: 17, weight: .heavy))
                    .foregroundStyle(colors.onSurface)

                Spacer()

                Toggle(
                    "",
                    isOn: Binding(
                        get: { viewModel.aiSummaryEnabled },
                        set: { newValue in
                            Task { await viewModel.setAiSummaryEnabled(newValue) }
                        }
                    )
                )
                .labelsHidden()
                .disabled(viewModel.isAiSummarySaving)
                .tint(colors.secondary)
            }

            Text("Summarize your day with one tap. Turn it off to hide the summary button.")
                .font(.tdayRounded(size: 12, weight: .bold))
                .foregroundStyle(colors.onSurface.opacity(0.58))
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

private struct SettingsListRow: View {
    let title: String
    let value: String?
    var titleColor: Color?
    var showChevron = true
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: action) {
            SettingsRowLabel(
                title: title,
                value: value,
                titleColor: titleColor ?? colors.onSurface,
                valueColor: colors.onSurface.opacity(0.58),
                showChevron: showChevron
            )
        }
        .buttonStyle(.plain)
    }
}

private struct SettingsServerVersionRow: View {
    let backendVersion: String
    let versionCheckResult: VersionCheckResult

    @Environment(\.tdayColors) private var colors

    var body: some View {
        HStack {
            Text("Server")
                .font(.tdayRounded(size: 17, weight: .heavy))
                .foregroundStyle(colors.onSurface)

            Spacer(minLength: 12)

            Text("v\(backendVersion)")
                .font(.tdayRounded(size: 13, weight: .bold))
                .foregroundStyle(colors.onSurface.opacity(0.58))

            Text(versionCheckResult == .compatible ? L("Compatible") : L("Incompatible"))
                .font(.tdayRounded(size: 11, weight: .heavy))
                .foregroundStyle(versionCheckResult == .compatible ? Color.green : colors.error)
        }
        .frame(minHeight: 28)
    }
}

private struct SettingsRowLabel: View {
    let title: String
    let value: String?
    var titleColor: Color?
    var valueColor: Color?
    var showChevron: Bool

    @Environment(\.tdayColors) private var colors

    init(
        title: String,
        value: String?,
        titleColor: Color? = nil,
        valueColor: Color? = nil,
        showChevron: Bool = true
    ) {
        self.title = title
        self.value = value
        self.titleColor = titleColor
        self.valueColor = valueColor
        self.showChevron = showChevron
    }

    var body: some View {
        HStack {
            Text(L(title))
                .font(.tdayRounded(size: 17, weight: .heavy))
                .foregroundStyle(titleColor ?? colors.onSurface)

            Spacer(minLength: 12)

            if let value {
                Text(L(value))
                    .font(.tdayRounded(size: 13, weight: .heavy))
                    .foregroundStyle(valueColor ?? colors.onSurface.opacity(0.58))
                    .lineLimit(1)
                    .minimumScaleFactor(0.78)
            }

            if showChevron {
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .heavy))
                    .foregroundStyle(colors.onSurface.opacity(0.42))
            }
        }
        .frame(maxWidth: .infinity, minHeight: 28, alignment: .center)
        .contentShape(Rectangle())
    }
}

private struct SettingsDivider: View {
    @Environment(\.tdayColors) private var colors

    var body: some View {
        Rectangle()
            .fill(colors.onSurface.opacity(0.06))
            .frame(height: 1)
    }
}

struct LatestReleaseScreen: View {
    let viewModel: AppViewModel

    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @Environment(\.tdayColors) private var colors
    @State private var releaseScrollOffset: CGFloat = 0

    private var titleCollapseProgress: CGFloat {
        rawTitleCollapseProgress
    }

    private var rawTitleCollapseProgress: CGFloat {
        let distance = TodoTimelineMetrics.titleCollapseDistance
        guard distance > 0 else { return 0 }
        return min(max(releaseScrollOffset / distance, 0), 1)
    }

    var body: some View {
        releaseContent
        .background(colors.background)
        .navigationBackButtonBehavior()
        .navigationTitleTypography(
            largeTitleColor: colors.onSurface,
            inlineTitleColor: colors.onSurface,
            backgroundColor: colors.background
        )
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .navigationBar)
        .safeAreaInset(edge: .top, spacing: 0) {
            TimelineTopBar(
                title: L("App Version"),
                accentColor: colors.onSurface,
                collapseProgress: titleCollapseProgress,
                onBack: { dismiss() },
                actions: []
            )
        }
        .task {
            await viewModel.refreshVersionInfo()
        }
    }

    private var releaseContent: some View {
        List {
            releaseHeroTitleRow

            if viewModel.isReleaseLoading && viewModel.currentRelease == nil && viewModel.latestRelease == nil {
                releaseListRow {
                    HStack {
                        Spacer()
                        ProgressView()
                            .controlSize(.large)
                            .padding(.top, 48)
                        Spacer()
                    }
                }
            } else {
                let hasInitialReleaseError = viewModel.releaseError != nil &&
                    viewModel.currentRelease == nil &&
                    viewModel.latestRelease == nil

                if hasInitialReleaseError {
                    releaseListRow {
                        ReleaseErrorCard {
                            Task { await viewModel.refreshVersionInfo() }
                        }
                    }
                }

                releaseListRow {
                    ReleaseOverviewCard(viewModel: viewModel)
                }

                if viewModel.hasUpdate, let latestRelease = viewModel.latestRelease {
                    releaseListRow {
                        UpdateAvailableCard(release: latestRelease, updateURL: viewModel.iosUpdateURL) { url in
                            openURL(url)
                        }
                    }
                }

                if !viewModel.hasUpdate {
                    releaseListRow {
                        InstalledVersionCard(
                            currentVersion: viewModel.currentVersionName,
                            currentRelease: viewModel.currentRelease
                        )
                    }
                }

                if let browseUrl = viewModel.latestRelease?.htmlUrl ?? viewModel.currentRelease?.htmlUrl,
                   let url = URL(string: browseUrl) {
                    releaseListRow {
                        ReleaseBrowserButton {
                            openURL(url)
                        }
                    }
                }
            }

            Color.clear
                .frame(height: TodoTimelineMetrics.titleCollapseDistance + TodoTimelineMetrics.topBarRowHeight + 24)
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
                .disableVerticalScrollBounce()
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .contentMargins(.top, 0, for: .scrollContent)
        .listSectionSpacing(0)
        .environment(\.defaultMinListRowHeight, 1)
        .disableVerticalScrollBounce()
    }

    private var releaseHeroTitleRow: some View {
        TimelineExpandedTitleRow(
            title: L("App Version"),
            accentColor: colors.onSurface,
            collapseProgress: titleCollapseProgress
        )
        .background {
            TimelineScrollOffsetObserver { releaseScrollOffset = $0 }
                .frame(width: 0, height: 0)
        }
        .onVerticalScrollSnap(collapseDistance: TodoTimelineMetrics.titleCollapseDistance)
        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }

    private func releaseListRow<Content: View>(
        topInset: CGFloat = 0,
        @ViewBuilder content: () -> Content
    ) -> some View {
        content()
            .listRowInsets(
                EdgeInsets(
                    top: topInset,
                    leading: TodoTimelineMetrics.horizontalPadding,
                    bottom: 12,
                    trailing: TodoTimelineMetrics.horizontalPadding
                )
            )
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
    }
}

private struct ReleaseErrorCard: View {
    let onRetry: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        ReleaseSurfaceCard(borderColor: colors.error.opacity(0.16)) {
            Text("Unable to fetch release information. Please check your connection and try again.")
                .font(.tdayRounded(size: 15, weight: .bold))
                .foregroundStyle(colors.error)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)

            Button("Retry", action: onRetry)
                .font(.tdayRounded(size: 15, weight: .heavy))
                .frame(maxWidth: .infinity)
        }
    }
}

private struct ReleaseOverviewCard: View {
    let viewModel: AppViewModel

    @Environment(\.tdayColors) private var colors

    private var isIncompatible: Bool {
        viewModel.versionCheckResult != .compatible
    }

    private var accent: Color {
        if isIncompatible { return colors.error }
        if viewModel.hasUpdate { return colors.primary }
        return colors.onSurface
    }

    private var title: String {
        if isIncompatible { return "Version Mismatch" }
        if viewModel.hasUpdate { return "Update Available" }
        return "Latest"
    }

    private var summary: String {
        switch viewModel.versionCheckResult {
        case let .appUpdateRequired(requiredVersion):
            return L("The server requires v%@. Update the app to continue.", requiredVersion)
        case let .serverUpdateRequired(serverVersion):
            return L("This app requires the server to be on v%@, but the server is on v%@.", viewModel.currentVersionName, serverVersion)
        case .compatible:
            if viewModel.hasUpdate {
                if let latestTag = viewModel.latestRelease?.tagName {
                    return L("Version %@ is ready to install.", latestTag)
                }
                return L("A newer version is ready to install.")
            }
            return L("You're running the latest version")
        }
    }

    private var serverVersionText: String {
        if let backendVersion = viewModel.backendVersion {
            return L("v%@", backendVersion)
        }
        if viewModel.serverURL == nil {
            return L("Not connected")
        }
        return L("Unavailable")
    }

    private var serverVersionTint: Color {
        guard viewModel.backendVersion != nil else {
            return colors.onSurface.opacity(0.58)
        }
        return viewModel.versionCheckResult == .compatible ? Color.green : colors.error
    }

    var body: some View {
        ReleaseSurfaceCard(borderColor: accent.opacity(isIncompatible || viewModel.hasUpdate ? 0.12 : 0.05)) {
            ReleaseSectionTitle(title, color: accent)

            Text(summary)
                .font(.tdayRounded(size: 15, weight: .bold))
                .foregroundStyle(colors.onSurface.opacity(0.62))

            ReleasePublishedDate(
                publishedAt: viewModel.latestRelease?.publishedAt ?? viewModel.currentRelease?.publishedAt
            )

            if !viewModel.isLocalMode {
                ReleaseVersionLine(
                    label: "Server",
                    version: serverVersionText,
                    tint: serverVersionTint
                )
            }

            ReleaseVersionLine(
                label: viewModel.hasUpdate ? "Installed" : "Installed Version",
                version: "v\(viewModel.currentVersionName)",
                tint: colors.primary
            )

            if viewModel.hasUpdate, let latestRelease = viewModel.latestRelease {
                ReleaseVersionLine(
                    label: "Latest",
                    version: latestRelease.tagName,
                    tint: colors.tertiary
                )
            }
        }
    }
}

private struct InstalledVersionCard: View {
    let currentVersion: String
    let currentRelease: GitHubRelease?

    @Environment(\.tdayColors) private var colors

    var body: some View {
        ReleaseSurfaceCard {
            ReleaseSectionTitle("Installed Version")

            HStack(spacing: 10) {
                ReleaseVersionBadge(text: "v\(currentVersion)")
                Text("Latest")
                    .font(.tdayRounded(size: 13, weight: .heavy))
                    .foregroundStyle(colors.onSurface.opacity(0.6))
            }

            ReleasePublishedDate(publishedAt: currentRelease?.publishedAt)

            ReleaseNotesSection(
                versionLabel: "v\(currentVersion)",
                changelog: parseChangelog(currentRelease?.body),
                emptyMessage: currentRelease == nil ? L("No release notes available for this version") : nil
            )
        }
    }
}

private struct UpdateAvailableCard: View {
    let release: GitHubRelease
    let updateURL: URL?
    let onOpen: (URL) -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        ReleaseSurfaceCard(borderColor: colors.primary.opacity(0.12)) {
            ReleaseSectionTitle("Update Available", color: colors.primary)
            ReleaseVersionBadge(text: release.tagName)
            ReleasePublishedDate(publishedAt: release.publishedAt)
            ReleaseNotesSection(
                versionLabel: release.tagName,
                changelog: parseChangelog(release.body),
                emptyMessage: nil
            )

            if let updateURL {
                Button {
                    onOpen(updateURL)
                } label: {
                    HStack {
                        Image(systemName: "arrow.up.forward.square")
                        Text("Open Update")
                    }
                    .font(.tdayRounded(size: 15, weight: .heavy))
                    .foregroundStyle(colors.onPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(colors.primary, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
                .buttonStyle(.plain)
            } else {
                Text("No App Store or TestFlight update link is configured for this build.")
                    .font(.tdayRounded(size: 13, weight: .bold))
                    .foregroundStyle(colors.onSurface.opacity(0.6))
            }
        }
    }
}

private struct ReleaseSurfaceCard<Content: View>: View {
    var borderColor: Color?
    @ViewBuilder let content: Content

    @Environment(\.tdayColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 18)
        .padding(.vertical, 18)
        .background(colors.surface, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(borderColor ?? colors.onSurface.opacity(0.08), lineWidth: 1)
        }
    }
}

private struct ReleaseSectionTitle: View {
    let title: String
    var color: Color?

    @Environment(\.tdayColors) private var colors

    init(_ title: String, color: Color? = nil) {
        self.title = title
        self.color = color
    }

    var body: some View {
        Text(L(title))
            .font(.tdayRounded(size: 22, weight: .heavy))
            .foregroundStyle(color ?? colors.onSurface)
    }
}

private struct ReleaseVersionLine: View {
    let label: String
    let version: String
    let tint: Color

    @Environment(\.tdayColors) private var colors

    var body: some View {
        HStack(spacing: 10) {
            Text(label)
                .font(.tdayRounded(size: 13, weight: .heavy))
                .foregroundStyle(colors.onSurface.opacity(0.58))
            ReleaseVersionBadge(text: version, tint: tint)
        }
    }
}

private struct ReleaseVersionBadge: View {
    let text: String
    var tint: Color?

    @Environment(\.tdayColors) private var colors

    var body: some View {
        let accent = tint ?? colors.primary

        Text(text)
            .font(.tdayRounded(size: 13, weight: .heavy))
            .foregroundStyle(accent)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(accent.opacity(0.08), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

private struct ReleasePublishedDate: View {
    let publishedAt: String?

    @Environment(\.tdayColors) private var colors

    var body: some View {
        if let publishedAt {
            Text("Published \(formatIsoDate(publishedAt))")
                .font(.tdayRounded(size: 13, weight: .bold))
                .foregroundStyle(colors.onSurface.opacity(0.62))
        }
    }
}

private struct ReleaseNotesSection: View {
    let versionLabel: String
    let changelog: [String]
    let emptyMessage: String?

    @Environment(\.tdayColors) private var colors

    var body: some View {
        if !changelog.isEmpty {
            Text("What's new in \(versionLabel)")
                .font(.tdayRounded(size: 17, weight: .heavy))
                .foregroundStyle(colors.onSurface)

            VStack(alignment: .leading, spacing: 10) {
                ForEach(changelog, id: \.self) { item in
                    HStack(alignment: .top, spacing: 10) {
                        Circle()
                            .fill(colors.onSurface.opacity(0.3))
                            .frame(width: 5, height: 5)
                            .padding(.top, 8)
                        Text(item)
                            .font(.tdayRounded(size: 15, weight: .bold))
                            .foregroundStyle(colors.onSurface)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 14)
            .padding(.vertical, 14)
            .background(colors.surfaceVariant.opacity(0.6), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        } else if let emptyMessage {
            Text(emptyMessage)
                .font(.tdayRounded(size: 15, weight: .bold))
                .foregroundStyle(colors.onSurface.opacity(0.6))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 14)
                .padding(.vertical, 14)
                .background(colors.surfaceVariant.opacity(0.6), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
    }
}

private struct ReleaseBrowserButton: View {
    let onOpen: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: 12) {
                Image(systemName: "arrow.up.forward.square")
                    .font(.system(size: 18, weight: .heavy))
                    .foregroundStyle(colors.primary)

                Text("View on GitHub")
                    .font(.tdayRounded(size: 17, weight: .heavy))
                    .foregroundStyle(colors.onSurface)
                    .frame(maxWidth: .infinity, alignment: .leading)

                Image(systemName: "arrow.up.forward.square")
                    .font(.system(size: 18, weight: .heavy))
                    .foregroundStyle(colors.primary)
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 15)
            .background(colors.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .stroke(colors.onSurface.opacity(0.06), lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
    }
}

private func parseChangelog(_ body: String?) -> [String] {
    guard let body else { return [] }
    return body
        .components(separatedBy: .newlines)
        .map { line -> String in
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.hasPrefix("- ") || trimmed.hasPrefix("* ") {
                return String(trimmed.dropFirst(2)).trimmingCharacters(in: .whitespacesAndNewlines)
            }
            return ""
        }
        .filter { !$0.isEmpty }
}

private func formatIsoDate(_ value: String) -> String {
    let date = ReleaseDateFormatters.internetDateTimeWithFraction.date(from: value)
        ?? ReleaseDateFormatters.internetDateTime.date(from: value)

    guard let date else { return value }
    return date.formatted(.dateTime.month(.wide).day().year().locale(AppLocale.current))
}

private enum ReleaseDateFormatters {
    static let internetDateTimeWithFraction: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    static let internetDateTime: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()
}

/// "Your data" trust card: shows what lives in the account, exports it to a JSON
/// file, and imports one back (Server Mode) after an additive-merge preview.
private struct DataTransferCard: View {
    let viewModel: AppViewModel

    @State private var taskCount = 0
    @State private var listCount = 0
    @State private var completedCount = 0
    @State private var busy = false
    @State private var exportDocument: DataExportDocument?
    @State private var showExporter = false
    @State private var showImporter = false
    @State private var pendingImportData: Data?
    @State private var previewCount = 0
    @State private var showConfirm = false

    private var repository: DataExportRepository { viewModel.container.dataExportRepository }

    var body: some View {
        SettingsSectionCard {
            HStack {
                SettingsSectionTitle("Your data")
                Spacer()
                GuideHelpLink(topicId: "export-your-data")
            }
            Text("\(taskCount) tasks · \(listCount) lists · \(completedCount) completed")
                .font(.tdayRounded(size: 13, weight: .semibold))
                .foregroundStyle(.secondary)

            SettingsListRow(title: "Download my data", value: nil, showChevron: false) {
                startExport()
            }

            SettingsDivider()

            if viewModel.isLocalMode {
                Text("Sign in to a server to import a file.")
                    .font(.tdayRounded(size: 12, weight: .semibold))
                    .foregroundStyle(.secondary)
            } else {
                SettingsListRow(title: "Import", value: nil, showChevron: false) {
                    if !busy { showImporter = true }
                }
            }
        }
        .task { loadCounts() }
        .fileExporter(
            isPresented: $showExporter,
            document: exportDocument,
            contentType: .json,
            defaultFilename: exportFilename()
        ) { result in
            if case .failure(let error) = result {
                notify(error.localizedDescription, kind: .error)
            } else {
                notify("Your data file was saved.", kind: .success)
            }
        }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.json]) { result in
            handleImportPick(result)
        }
        .confirmationDialog("Import data?", isPresented: $showConfirm, titleVisibility: .visible) {
            Button("Import \(previewCount) items") { confirmImport() }
            Button("Cancel", role: .cancel) { clearPending() }
        } message: {
            Text("This adds \(previewCount) items to your account. Nothing you already have is changed or removed.")
        }
    }

    private func loadCounts() {
        let state = viewModel.container.cacheManager.loadOfflineState()
        taskCount = state.todos.count + state.floaters.count
        listCount = state.lists.count + state.floaterLists.count
        completedCount = state.completedItems.count + state.completedFloaters.count
    }

    private func startExport() {
        guard !busy else { return }
        busy = true
        Task {
            do {
                let data = try await repository.buildExportData()
                exportDocument = DataExportDocument(data: data)
                showExporter = true
            } catch {
                notify(error.localizedDescription, kind: .error)
            }
            busy = false
        }
    }

    private func handleImportPick(_ result: Result<URL, Error>) {
        guard case .success(let url) = result else {
            if case .failure(let error) = result { notify(error.localizedDescription, kind: .error) }
            return
        }
        guard url.startAccessingSecurityScopedResource(), let data = try? Data(contentsOf: url) else {
            notify("Could not read that file.", kind: .error)
            return
        }
        url.stopAccessingSecurityScopedResource()
        pendingImportData = data
        busy = true
        Task {
            do {
                let response = try await repository.preview(fileData: data)
                previewCount = response.imported.total
                showConfirm = true
            } catch {
                notify("That file isn't a valid T'Day export.", kind: .error)
                clearPending()
            }
            busy = false
        }
    }

    private func confirmImport() {
        guard let data = pendingImportData else { return }
        busy = true
        Task {
            do {
                let response = try await repository.commit(fileData: data)
                notify("Import complete — added \(response.imported.total) items.", kind: .success)
                loadCounts()
            } catch {
                notify("Could not import that file.", kind: .error)
            }
            clearPending()
            busy = false
        }
    }

    private func clearPending() {
        pendingImportData = nil
        previewCount = 0
    }

    private func exportFilename() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return "tday-export-\(formatter.string(from: Date())).json"
    }

    private func notify(_ message: String, kind: SnackbarKind) {
        viewModel.container.snackbarManager.show(message, kind: kind)
    }
}
