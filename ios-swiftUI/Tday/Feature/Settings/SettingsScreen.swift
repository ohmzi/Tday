import SwiftUI

private let settingsSegmentedControlAccentColor = Color.tdayTodayBlue

struct SettingsScreen: View {
    let viewModel: AppViewModel

    @Environment(\.dismiss) private var dismiss
    @Environment(\.tdayColors) private var colors
    @State private var settingsScrollOffset: CGFloat = 0
    @State private var showingReminderSelector = false

    private var isAdminUser: Bool {
        !viewModel.isLocalMode && (viewModel.user?.role ?? "").uppercased() == "ADMIN"
    }

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
                title: "Settings",
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
        .task {
            await viewModel.refreshAdminAiSummarySetting()
            await viewModel.refreshVersionInfo()
        }
        .alert(
            "Summary unavailable",
            isPresented: Binding(
                get: { viewModel.aiSummaryValidationError != nil },
                set: { visible in
                    if !visible {
                        viewModel.dismissAiSummaryValidationError()
                    }
                }
            )
        ) {
            Button("OK", role: .cancel) {
                viewModel.dismissAiSummaryValidationError()
            }
        } message: {
            Text(viewModel.aiSummaryValidationError ?? "")
        }
        .animation(.spring(response: 0.24, dampingFraction: 0.9), value: showingReminderSelector)
    }

    private var settingsContent: some View {
        List {
            settingsHeroTitleRow

            if !viewModel.isLocalMode {
                settingsListRow {
                    SettingsProfileCard(user: viewModel.user)
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
                }
            }

            if isAdminUser {
                settingsListRow {
                    SettingsSectionCard {
                        SettingsSectionTitle("Feature toggle")
                        SettingsAiSummaryRow(viewModel: viewModel)
                    }
                }
            }

            settingsListRow {
                SettingsSectionCard {
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
            title: "Settings",
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
    let user: SessionUser?

    @Environment(\.tdayColors) private var colors

    var body: some View {
        SettingsSectionCard {
            Text(user?.name ?? "Unknown user")
                .font(.tdayRounded(size: 22, weight: .heavy))
                .foregroundStyle(colors.onSurface)

            if let email = user?.email, !email.isEmpty {
                Text(email)
                    .font(.tdayRounded(size: 15, weight: .bold))
                    .foregroundStyle(colors.onSurface.opacity(0.72))
            }

            Text("Role: \(user?.role ?? "USER")")
                .font(.tdayRounded(size: 13, weight: .bold))
                .foregroundStyle(colors.onSurface.opacity(0.58))
        }
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
        Text(title)
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

            TdayCenteredSelectorCard(title: "Default reminder") {
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

                if viewModel.adminAiSummaryEnabled == nil {
                    ProgressView()
                        .controlSize(.small)
                } else {
                    Toggle(
                        "",
                        isOn: Binding(
                            get: { viewModel.adminAiSummaryEnabled ?? false },
                            set: { newValue in
                                Task { await viewModel.setAdminAiSummaryEnabled(newValue) }
                            }
                        )
                    )
                    .labelsHidden()
                    .disabled(viewModel.isAdminAiSummaryLoading || viewModel.isAdminAiSummarySaving)
                    .tint(colors.secondary)
                }
            }

            if viewModel.isAdminAiSummarySaving {
                Text("Saving admin setting...")
                    .font(.tdayRounded(size: 12, weight: .bold))
                    .foregroundStyle(colors.onSurface.opacity(0.58))
            }

            if let error = viewModel.adminAiSummaryError,
               viewModel.adminAiSummaryEnabled == true {
                Text(error)
                    .font(.tdayRounded(size: 12, weight: .bold))
                    .foregroundStyle(colors.error)
            }
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

            Text(versionCheckResult == .compatible ? "Compatible" : "Incompatible")
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
            Text(title)
                .font(.tdayRounded(size: 17, weight: .heavy))
                .foregroundStyle(titleColor ?? colors.onSurface)

            Spacer(minLength: 12)

            if let value {
                Text(value)
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
                title: "App Version",
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
            title: "App Version",
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
            return "The server requires v\(requiredVersion). Update the app to continue."
        case let .serverUpdateRequired(serverVersion):
            return "This app requires the server to be on v\(viewModel.currentVersionName), but the server is on v\(serverVersion)."
        case .compatible:
            if viewModel.hasUpdate {
                if let latestTag = viewModel.latestRelease?.tagName {
                    return "Version \(latestTag) is ready to install."
                }
                return "A newer version is ready to install."
            }
            return "You're running the latest version"
        }
    }

    private var serverVersionText: String {
        if let backendVersion = viewModel.backendVersion {
            return "v\(backendVersion)"
        }
        if viewModel.serverURL == nil {
            return "Not connected"
        }
        return "Unavailable"
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
                emptyMessage: currentRelease == nil ? "No release notes available for this version" : nil
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
        Text(title)
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
    return date.formatted(.dateTime.month(.wide).day().year())
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
