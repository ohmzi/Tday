import SwiftUI

struct SettingsScreen: View {
    let viewModel: AppViewModel

    @Environment(\.dismiss) private var dismiss
    @Environment(\.tdayColors) private var colors

    private var isAdminUser: Bool {
        (viewModel.user?.role ?? "").uppercased() == "ADMIN"
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 12) {
                SettingsPageHeader(title: "Settings") {
                    dismiss()
                }

                SettingsProfileCard(user: viewModel.user)

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
                        onSelect: viewModel.setDefaultReminder
                    )
                }

                if isAdminUser {
                    SettingsSectionCard {
                        SettingsSectionTitle("Feature toggle")
                        SettingsAiSummaryRow(viewModel: viewModel)
                    }
                }

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
                            .foregroundStyle(colors.primary)
                    }

                    if let backendVersion = viewModel.backendVersion {
                        SettingsServerVersionRow(
                            backendVersion: backendVersion,
                            versionCheckResult: viewModel.versionCheckResult
                        )
                    }

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

                Spacer(minLength: 24)
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 24)
        }
        .background(colors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .navigationBackButtonBehavior()
        .task {
            await viewModel.refreshAdminAiSummarySetting()
            await viewModel.refreshVersionInfo()
        }
        .alert(
            "AI Summary Unavailable",
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
    }
}

struct SettingsPageHeader: View {
    let title: String
    let onBack: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.system(size: TodoTimelineMetrics.topBarButtonIconSize, weight: .semibold))
                    .foregroundStyle(colors.onSurface)
                    .frame(
                        width: TodoTimelineMetrics.topBarButtonFrame,
                        height: TodoTimelineMetrics.topBarButtonFrame
                    )
                    .background(colors.surface, in: Circle())
                    .contentShape(Circle())
            }
            .buttonStyle(SettingsBackButtonStyle())
            .accessibilityLabel("Back")

            Text(title)
                .font(.tdayRounded(size: 32, weight: .heavy))
                .foregroundStyle(colors.onSurface)
        }
        .padding(.top, 6)
    }
}

private struct SettingsBackButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .tdayRippleEffect(isPressed: configuration.isPressed)
            .scaleEffect(configuration.isPressed ? 0.95 : 1)
            .offset(y: configuration.isPressed ? 1 : 0)
            .shadow(
                color: Color.black.opacity(configuration.isPressed ? 0.04 : 0.08),
                radius: configuration.isPressed ? 3 : 7,
                x: 0,
                y: configuration.isPressed ? 1 : 3
            )
            .animation(.easeOut(duration: 0.14), value: configuration.isPressed)
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

    @Environment(\.tdayColors) private var colors

    var body: some View {
        HStack(spacing: 4) {
            ForEach(AppThemeMode.allCases, id: \.self) { mode in
                let selected = selectedMode == mode
                Button {
                    onSelect(mode)
                } label: {
                    Text(mode.label)
                        .font(.tdayRounded(size: 13, weight: .heavy))
                        .foregroundStyle(selected ? colors.onSurface : colors.onSurface.opacity(0.58))
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(
                            selected ? colors.surface : Color.clear,
                            in: RoundedRectangle(cornerRadius: 14, style: .continuous)
                        )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .background(
            colors.surfaceVariant.opacity(0.76),
            in: RoundedRectangle(cornerRadius: 18, style: .continuous)
        )
    }
}

private struct SettingsReminderSelector: View {
    let selectedReminder: ReminderOption
    let onSelect: (ReminderOption) -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Menu {
            ForEach(ReminderOption.allCases) { option in
                Button {
                    onSelect(option)
                } label: {
                    if option == selectedReminder {
                        Label(option.label, systemImage: "checkmark")
                    } else {
                        Text(option.label)
                    }
                }
            }
        } label: {
            SettingsRowLabel(
                title: "Default reminder",
                value: selectedReminder.label,
                valueColor: colors.primary,
                showChevron: true
            )
        }
        .buttonStyle(.plain)
    }
}

private struct SettingsAiSummaryRow: View {
    let viewModel: AppViewModel

    @Environment(\.tdayColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("AI task summary")
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

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 12) {
                SettingsPageHeader(title: "App Version") {
                    dismiss()
                }

                if viewModel.isReleaseLoading && viewModel.currentRelease == nil && viewModel.latestRelease == nil {
                    HStack {
                        Spacer()
                        ProgressView()
                            .controlSize(.large)
                            .padding(.top, 48)
                        Spacer()
                    }
                } else {
                    if viewModel.releaseError != nil &&
                        viewModel.currentRelease == nil &&
                        viewModel.latestRelease == nil {
                        ReleaseErrorCard {
                            Task { await viewModel.refreshVersionInfo() }
                        }
                    }

                    ReleaseOverviewCard(viewModel: viewModel)

                    if viewModel.hasUpdate, let latestRelease = viewModel.latestRelease {
                        UpdateAvailableCard(release: latestRelease) {
                            if let url = URL(string: latestRelease.htmlUrl) {
                                openURL(url)
                            }
                        }
                    }

                    if !viewModel.hasUpdate {
                        InstalledVersionCard(
                            currentVersion: viewModel.currentVersionName,
                            currentRelease: viewModel.currentRelease
                        )
                    }

                    if let browseUrl = viewModel.latestRelease?.htmlUrl ?? viewModel.currentRelease?.htmlUrl,
                       let url = URL(string: browseUrl) {
                        ReleaseBrowserButton {
                            openURL(url)
                        }
                    }
                }

                Spacer(minLength: 24)
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 24)
        }
        .background(colors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .navigationBackButtonBehavior()
        .task {
            await viewModel.refreshVersionInfo()
        }
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

    var body: some View {
        ReleaseSurfaceCard(borderColor: accent.opacity(isIncompatible || viewModel.hasUpdate ? 0.12 : 0.05)) {
            ReleaseSectionTitle(title, color: accent)

            Text(summary)
                .font(.tdayRounded(size: 15, weight: .bold))
                .foregroundStyle(colors.onSurface.opacity(0.62))

            ReleasePublishedDate(
                publishedAt: viewModel.latestRelease?.publishedAt ?? viewModel.currentRelease?.publishedAt
            )

            ReleaseVersionLine(
                label: viewModel.hasUpdate ? "Installed" : "Installed Version",
                version: "v\(viewModel.currentVersionName)",
                tint: colors.primary
            )

            if let backendVersion = viewModel.backendVersion {
                ReleaseVersionLine(
                    label: "Server",
                    version: "v\(backendVersion)",
                    tint: viewModel.versionCheckResult == .compatible ? Color.green : colors.error
                )
            }

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
    let onOpen: () -> Void

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

            Button(action: onOpen) {
                HStack {
                    Image(systemName: "arrow.up.forward.square")
                    Text("Open GitHub release")
                }
                .font(.tdayRounded(size: 15, weight: .heavy))
                .foregroundStyle(colors.onPrimary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(colors.primary, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
            .buttonStyle(.plain)
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
    let parser = ISO8601DateFormatter()
    parser.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    let date = parser.date(from: value) ?? {
        let fallback = ISO8601DateFormatter()
        fallback.formatOptions = [.withInternetDateTime]
        return fallback.date(from: value)
    }()

    guard let date else { return value }
    return date.formatted(.dateTime.month(.wide).day().year())
}
