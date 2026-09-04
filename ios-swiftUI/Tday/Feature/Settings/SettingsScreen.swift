import SwiftUI
import UIKit
import UserNotifications
#if canImport(WidgetKit)
import WidgetKit
#endif

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
    @FocusState private var searchFieldFocused: Bool
    @State private var searchExpanded = false
    @State private var searchQuery = ""
    /// Whether a notification would actually arrive — the OS permission AND the app's own
    /// switch. Owned here because the switch is in one card and the three settings it silences
    /// are in another; `SettingsNotificationsSection` is what keeps it current. Seeded from the
    /// preference alone so the common case doesn't flash dimmed before the OS status is read.
    @State private var notificationsDeliver = NotificationPreferenceStore().isEnabled

    private var titleCollapseProgress: CGFloat {
        rawTitleCollapseProgress
    }

    private var rawTitleCollapseProgress: CGFloat {
        let distance = TodoTimelineMetrics.titleCollapseDistance
        guard distance > 0 else { return 0 }
        return min(max(settingsScrollOffset / distance, 0), 1)
    }

    private var normalizedSearchQuery: String {
        searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(with: .current)
    }

    private var isSearching: Bool {
        searchExpanded && !normalizedSearchQuery.isEmpty
    }

    /// True when this card holds something the query is looking for.
    ///
    /// The unit is the card rather than the single row: every row here is either
    /// an editor that expands in place, a toggle with the paragraph that explains
    /// it, or a picker under the heading that says what it picks — pull one out
    /// on its own and it loses the thing that made it legible. A match therefore
    /// narrows to the card holding the row, and the row stays where it is.
    ///
    /// Terms go through `L()` exactly as the rows' own labels do, so the search
    /// matches whatever the screen is actually showing, in whatever language.
    private func matchesSearch(_ terms: [String]) -> Bool {
        guard isSearching else {
            return true
        }
        return terms.contains { term in
            L(term).lowercased(with: .current).contains(normalizedSearchQuery)
        }
    }

    private var showsProfileCard: Bool {
        !viewModel.isLocalMode && matchesSearch(["Name", "Username", "Password", "Security questions"])
    }

    private var showsAppearanceCard: Bool {
        matchesSearch(
            [
                "Appearance",
                "Behavior",
                "Default home screen",
                "Reminders",
                "Default reminder",
                "Day Ahead digest",
                "Quiet hours",
                "Language",
            ] + AppThemeMode.allCases.map(\.label) + [RootFeedTab.scheduledTaskHome.title, RootFeedTab.floaterTaskHome.title]
        )
    }

    /// The switches, in one card, as Android has them — its own card is
    /// `settings_feature_toggle` holding ai-summary, resting-floaters,
    /// calendar-sync and now the notification gate. Splitting them across two
    /// cards here meant a local workspace saw one titled card vanish entirely
    /// and the other appear untitled.
    private var showsFeatureTogglesCard: Bool {
        var terms = [
            "Feature toggle", "Resting floaters", "Add scheduled tasks to my calendar",
            "Notifications",
        ]
        if !viewModel.isLocalMode {
            terms += ["AI task summary", "Summary"]
        }
        return matchesSearch(terms)
    }

    private var showsPrivacyCard: Bool {
        matchesSearch(["Privacy", "Require Face ID to open T'Day"])
    }

    // The tail of the screen is four small cards rather than one large one, so
    // each term list has to describe only the card it gates: a term left behind
    // on the wrong list would surface a card whose matching row now sits two
    // cards further down.

    private var showsAboutCard: Bool {
        // The sync labels are here because they are the card's most prominent
        // visible text — "Sync now", "Last synced", "Up to date" — and "sync"
        // is close to the likeliest single word anyone types on this screen.
        // A term list that omits what the card actually says is the failure mode
        // this whole approach has. The mode split follows the same rule: the
        // card says "Local workspace" on a device with no server and "Server
        // sync" on one with, and never both.
        var terms = ["About", "App Version"]
        if viewModel.isLocalMode {
            terms += ["Local workspace", "On this device only"]
        } else {
            terms += ["Server sync", "Server", "Sync now", "Last synced", "Up to date"]
        }
        return matchesSearch(terms)
    }

    /// Server Mode only — see the web card's note: export and import move an
    /// account's data, and a local workspace has no account to move it between.
    private var showsDataCard: Bool {
        !viewModel.isLocalMode && matchesSearch(["Your data", "Download my data", "Import"])
    }

    private var showsGuideCard: Bool {
        matchesSearch(["How-To & Tips"])
    }

    /// The screen's last card is the exit. Local mode has no session to end, so it holds the
    /// only way out of a local workspace this app has instead.
    private var showsSignOutCard: Bool {
        matchesSearch(viewModel.isLocalMode ? ["Leave local workspace", "Delete local data"] : ["Sign out"])
    }

    private var hasSearchResults: Bool {
        showsProfileCard || showsAppearanceCard || showsFeatureTogglesCard ||
            showsPrivacyCard || showsAboutCard ||
            showsDataCard || showsGuideCard || showsSignOutCard
    }

    private var topBarActions: [TimelineTopBarAction] {
        [
            TimelineTopBarAction(
                systemName: "magnifyingglass",
                assetName: "NavSearch",
                usesCircularChrome: true,
                accessibilityLabel: L("Search"),
                action: openSearch
            ),
        ]
    }

    private func openSearch() {
        HapticManager.buttonTap()
        withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
            searchExpanded = true
        }
    }

    /// Leaving the search drops the query with it, so the whole of settings is
    /// back the next time the bar is opened — the same bargain web's close makes.
    private func closeSearch() {
        HapticManager.sheetDismiss()
        searchFieldFocused = false
        withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
            searchExpanded = false
        }
        searchQuery = ""
    }

    var body: some View {
        settingsContent
        .background(colors.background)
        // Tapping the content puts the field away, as it does on the root feeds.
        // Sits above the bar's own safe-area inset, so the gesture only ever
        // sees taps below the bar and never one on it.
        .tdayClosesSearchOnOutsideTap(isSearchOpen: searchExpanded) {
            closeSearch()
        }
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
                actions: topBarActions,
                searchActive: searchExpanded,
                searchText: $searchQuery,
                searchPlaceholder: L("Search in %@", L("Settings")),
                searchFieldFocused: $searchFieldFocused,
                onSearchClose: closeSearch
            )
        }
        // The field only joins the hierarchy once the bar has swapped its row
        // over, so focusing it in the same turn is dropped on the floor.
        .onChange(of: searchExpanded) { _, expanded in
            guard expanded else {
                searchFieldFocused = false
                return
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.30) {
                if searchExpanded {
                    searchFieldFocused = true
                }
            }
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
            await viewModel.refreshDefaultHomeScreen()
            await viewModel.refreshVersionInfo()
        }
        .animation(.spring(response: 0.24, dampingFraction: 0.9), value: showingReminderSelector)
        .animation(.spring(response: 0.24, dampingFraction: 0.9), value: showingLanguageSelector)
        .animation(.spring(response: 0.28, dampingFraction: 0.9), value: profileEditor)
    }

    private var settingsContent: some View {
        List {
            settingsHeroTitleRow

            if showsProfileCard {
                settingsListRow {
                    SettingsProfileCard(viewModel: viewModel, expansion: $profileEditor)
                }
            }

            if showsAppearanceCard {
                settingsListRow {
                    SettingsSectionCard {
                        SettingsSectionTitle("Appearance")
                        SettingsThemeSelector(
                            selectedMode: viewModel.themeMode,
                            onSelect: viewModel.setThemeMode
                        )
                        SettingsDivider()
                        SettingsSectionTitle("Behavior")
                        Text(L("Default home screen"))
                            .font(.tdayRounded(size: 15, weight: .heavy))
                            .foregroundStyle(colors.onSurface)
                        SettingsRootFeedTabSelector(
                            selectedTab: viewModel.defaultHomeScreen,
                            onSelect: { tab in
                                Task { await viewModel.setDefaultHomeScreen(tab) }
                            }
                        )
                        SettingsDivider()
                        HStack {
                            SettingsSectionTitle("Reminders")
                            Spacer()
                            // Outside the group below, which goes inert: `GuideHelpLink` is a
                            // tap gesture, so `.disabled` would silence the one thing on this
                            // card that still has something to say when reminders are off.
                            GuideHelpLink(topicId: "reminders")
                        }
                        // Grouped so the whole block can go inert together. Every one of these
                        // three schedules a notification, and the master switch lives two cards
                        // down: without this a 7am digest and a default offset could be picked
                        // and nothing would ever arrive, with nothing on screen saying why.
                        // Dimmed and untappable says it in the one language everyone reads.
                        VStack(alignment: .leading, spacing: 16) {
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
                        }
                        .opacity(notificationsDeliver ? 1 : 0.45)
                        .disabled(!notificationsDeliver)
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
            }

            if showsFeatureTogglesCard {
                settingsListRow {
                    SettingsSectionCard {
                        // Android's own card title, `settings_feature_toggle`.
                        // The literal it replaces, "AI task summary", rendered in
                        // English everywhere — not because this view skips L(),
                        // it does not, but because that string was never a key in
                        // the catalogue. This one is, in all nine locales.
                        HStack {
                            SettingsSectionTitle("Feature toggle")
                            Spacer()
                            // One "?" for the whole card rather than one per row, and it
                            // has to follow the card's own first row: `ai-summary` heads
                            // the guide's Integrations section but the row it explains is
                            // server-only, so in a local workspace the link would open a
                            // topic badged "Server mode" about a row that is not there.
                            GuideHelpLink(
                                topicId: viewModel.isLocalMode ? "resting-floaters" : "ai-summary"
                            )
                        }
                        // Server-only — a local workspace has no account to
                        // summarise. Its divider goes with it, or the card opens
                        // on a rule.
                        if !viewModel.isLocalMode {
                            SettingsAiSummaryRow(viewModel: viewModel)
                            SettingsDivider()
                        }
                        SettingsRestingFloatersSection()
                        SettingsDivider()
                        SettingsDeviceCalendarSection()
                        SettingsDivider()
                        SettingsNotificationsSection(
                            viewModel: viewModel,
                            deliversNotifications: $notificationsDeliver
                        )
                    }
                }
            }

            if showsPrivacyCard {
                settingsListRow {
                    SettingsSectionCard {
                        SettingsSectionTitle("Privacy")
                        SettingsAppLockSection()
                    }
                }
            }

            // A Group, not four more rows: List's ViewBuilder tops out at ten
            // children, and the header, the four cards above and the two
            // trailing rows already spend seven of them.
            Group {
                if showsAboutCard {
                    settingsListRow {
                        SettingsSectionCard {
                            SettingsAboutContent(
                                syncStatus: viewModel.syncStatus,
                                // The card is about the mode this install is in, and the
                                // guide has a topic per mode. Neither is server-only, so
                                // both open in the workspace they describe.
                                helpTopicId: viewModel.isLocalMode ? "local-mode" : "server-mode",
                                onSyncNow: {
                                    Task { await viewModel.manualSync() }
                                }
                            )

                            SettingsDivider()

                            SettingsListRow(
                                title: "App Version",
                                value: "v\(viewModel.currentVersionName)",
                                icon: "LucideInfo",
                                action: {
                                    viewModel.navigationPath.append(.latestRelease)
                                }
                            )

                            if viewModel.hasUpdate, let latestVersionName = viewModel.latestVersionName {
                                Text(L("v%@ available", latestVersionName))
                                    .font(.tdayRounded(size: 11, weight: .heavy))
                                    .foregroundStyle(colors.secondary)
                                    .padding(.leading, 34)
                            }

                            if !viewModel.isLocalMode, let backendVersion = viewModel.backendVersion {
                                SettingsServerVersionRow(
                                    backendVersion: backendVersion,
                                    versionCheckResult: viewModel.versionCheckResult
                                )
                            }
                        }
                    }
                }

                if showsDataCard {
                    settingsListRow {
                        DataTransferCard(viewModel: viewModel)
                    }
                }

                if showsGuideCard {
                    settingsListRow {
                        SettingsSectionCard {
                            SettingsListRow(
                                title: L("How-To & Tips"),
                                value: nil,
                                icon: "LucideCircleHelp",
                                action: {
                                    viewModel.navigationPath.append(.helpGuide(topic: nil))
                                }
                            )
                        }
                    }
                }

                if showsSignOutCard {
                    settingsListRow {
                        SettingsSectionCard {
                            if viewModel.isLocalMode {
                                SettingsLocalWorkspaceExit(viewModel: viewModel)
                            } else {
                                SettingsListRow(
                                    title: "Sign out",
                                    value: nil,
                                    titleColor: colors.error,
                                    showChevron: false,
                                    icon: "LucideLogOut",
                                    action: {
                                        Task { await viewModel.logout() }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if isSearching, !hasSearchResults {
                settingsListRow {
                    searchEmptyState
                }
            }

            // 24, as Android's settings list ends (`Spacer(24.dp)`), not the 258
            // this used to reserve — `titleCollapseDistance` (178) plus a bar row
            // plus 24. That much existed so the title could always finish
            // collapsing even on a short page, and it paid for that by leaving a
            // third of the screen blank under the last card on every page, short
            // or not. It is not needed: `onVerticalScrollSnap` already settles a
            // page that cannot collapse fully back to expanded, which is the
            // bargain Android makes too.
            Color.clear
                .frame(height: 24)
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

    /// Settings is never genuinely empty, so its only empty state is a search
    /// that found nothing: a no-results title over the shared search line,
    /// rather than an invitation to add a first something.
    private var searchEmptyState: some View {
        TdayEmptyState(
            assetName: "NavSearch",
            accentColor: colors.primary,
            title: L("No matching settings"),
            description: L("Try a different word, or clear the search."),
            action: AnyView(
                Button {
                    HapticManager.gentleTap()
                    searchQuery = ""
                    searchFieldFocused = true
                } label: {
                    Text(L("Clear search"))
                        .font(.tdayRounded(size: 15, weight: .bold))
                        .foregroundStyle(colors.primary)
                }
                .buttonStyle(.plain)
            )
        )
        .padding(.vertical, 24)
    }

    private var settingsHeroTitleRow: some View {
        TimelineExpandedTitleRow(
            title: L("Settings"),
            accentColor: colors.onSurface,
            collapseProgress: titleCollapseProgress,
            mark: Image("LucideSlidersHorizontal"),
            markAccentColor: colors.primary
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
            HStack(spacing: 14) {
                // Nothing to tap, so no glyph — but an empty slot keeps this label on the
                // same left edge as the rows above it.
                SettingsRowIcon(asset: nil)

                Text(L("Role: %@", viewModel.user?.role ?? "USER"))
                    .font(.tdayRounded(size: 13, weight: .bold))
                    .foregroundStyle(colors.onSurface.opacity(0.58))
            }
        }
    }
}

// MARK: - Resting floaters

/// Toggle for the "resting floaters" display cue (dim untouched Anytime tasks).
private struct SettingsRestingFloatersSection: View {
    @Environment(\.tdayColors) private var colors
    private let store = RestingFloatersStore()
    @State private var enabled: Bool

    init() {
        _enabled = State(initialValue: RestingFloatersStore().isEnabled)
    }

    var body: some View {
        Toggle(isOn: $enabled) {
            HStack(spacing: 14) {
                SettingsRowIcon(asset: "LucideWaves")

                Text(L("Resting floaters"))
                    .font(.body.weight(.heavy))
                    .foregroundStyle(colors.onSurface)
            }
        }
        .tint(colors.secondary)
        .onChange(of: enabled) { _, value in store.isEnabled = value }
    }
}

// MARK: - App lock

/// Opt-in biometric gate. DEFAULT OFF, and nothing else in the app changes until it is on.
/// The lock hides the UI only — it holds no key material. Home-screen widgets and the watch
/// complication also hide task content while it's on (see `WidgetAppLockStore` in
/// TdayWidget/TodayTasksWidget.swift), so this isn't just a screen gate for the app itself.
private struct SettingsAppLockSection: View {
    @Environment(\.tdayColors) private var colors
    private let store = AppLockStore()
    @State private var enabled: Bool

    init() {
        _enabled = State(initialValue: AppLockStore().isEnabled)
    }

    var body: some View {
        Toggle(isOn: $enabled) {
            HStack(spacing: 14) {
                SettingsRowIcon(asset: "LucideShield")

                Text(L("Require Face ID to open T'Day"))
                    .font(.body.weight(.heavy))
                    .foregroundStyle(colors.onSurface)
            }
        }
        .tint(colors.secondary)
        .onChange(of: enabled) { _, value in
            store.isEnabled = value
            // Widgets read this flag fresh on every timeline reload, but nothing else
            // would prompt one right now — without this they'd keep showing (or hiding)
            // task content until their next unrelated refresh.
            #if canImport(WidgetKit)
            WidgetCenter.shared.reloadAllTimelines()
            #endif
        }
    }
}

// MARK: - Device calendar

/// Opt-in one-way mirror of scheduled tasks into a dedicated "T'Day" calendar on this device.
///
/// Default off. Turning it on asks for calendar access first, and a refused prompt leaves the
/// toggle off rather than silently enabling a mirror that cannot write. Turning it off deletes the
/// calendar and everything T'Day put in it.
///
/// Anytime tasks are excluded by the sync itself: with no due date there is nothing to place on a
/// calendar.
private struct SettingsDeviceCalendarSection: View {
    @Environment(\.tdayColors) private var colors
    private let container = AppContainer.shared
    @State private var enabled: Bool
    @State private var showPermissionDenied = false

    init() {
        _enabled = State(initialValue: AppContainer.shared.calendarSyncManager.isEnabled)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Toggle(isOn: $enabled) {
                HStack(spacing: 14) {
                    SettingsRowIcon(asset: "LucideCalendar")

                    Text(L("Add scheduled tasks to my calendar"))
                        .font(.body.weight(.heavy))
                        .foregroundStyle(colors.onSurface)
                }
            }
            .tint(colors.secondary)
            .onChange(of: enabled) { _, value in
                Task { await apply(enabled: value) }
            }

            if showPermissionDenied {
                Text(L("T'Day needs calendar access to add your tasks. You can grant it in Settings."))
                    .font(.tdayRounded(size: 12, weight: .bold))
                    .foregroundStyle(colors.error)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.leading, 34)
            }
        }
    }

    @MainActor
    private func apply(enabled value: Bool) async {
        guard value else {
            showPermissionDenied = false
            await container.calendarSyncManager.disable()
            return
        }

        let tasks = container.todoRepository.fetchTodosSnapshot(mode: .all)
        let granted = await container.calendarSyncManager.enable(tasks: tasks)
        showPermissionDenied = !granted
        if !granted {
            // Snap the switch back: access was refused, so nothing is being mirrored.
            enabled = false
        }
    }
}

// MARK: - Notifications

/// The app's own notification switch — and, once iOS has stopped offering its prompt, the only
/// route from inside T'Day back to the OS permission.
///
/// Two independent pieces of state, never one: `UNAuthorizationStatus` (what iOS allows, which
/// the user can change in Settings while this app is suspended) and `NotificationPreferenceStore`
/// (what the user asked T'Day for). The switch shows the AND of them — a reminder needs both —
/// and drives whichever half is in the way. iOS shows its dialog only while the status is
/// `.notDetermined`; on `.denied` a second `requestAuthorization` returns silently, so that case
/// opens Settings instead of leaving the switch to snap back with no explanation.
private struct SettingsNotificationsSection: View {
    let viewModel: AppViewModel
    /// Mirrored out to the Appearance card, which holds the three settings this switch
    /// silences and has no other way to know that it is off.
    @Binding var deliversNotifications: Bool

    @Environment(\.tdayColors) private var colors
    @Environment(\.openURL) private var openURL

    private let store = NotificationPreferenceStore()
    @State private var preferenceEnabled: Bool
    @State private var showSystemSettingsHint = false

    init(viewModel: AppViewModel, deliversNotifications: Binding<Bool>) {
        self.viewModel = viewModel
        _deliversNotifications = deliversNotifications
        _preferenceEnabled = State(initialValue: NotificationPreferenceStore().isEnabled)
    }

    /// The OS half of the answer lives on the view model, not here: `AppRootView` re-reads it
    /// on every foreground return so a permission granted in iOS Settings takes effect from
    /// whatever screen the user came back to. This section reads the same value so the switch
    /// and the delivery it promises can never disagree.
    private var authorizationStatus: UNAuthorizationStatus? {
        viewModel.notificationAuthorizationStatus
    }

    private var isOn: Bool {
        (authorizationStatus?.allowsNotificationDelivery ?? false) && preferenceEnabled
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Label and switch built out rather than left as `Toggle { label }`: this row
            // carries a "?" of its own, and the card's single one leads on `ai-summary`, which
            // is a different section of the guide entirely. Inside a Toggle label the help
            // link would sit in the switch's own tap target and flip it instead of opening.
            HStack(spacing: 14) {
                SettingsRowIcon(asset: "LucideBell")

                Text(L("Notifications"))
                    .font(.body.weight(.heavy))
                    .foregroundStyle(colors.onSurface)

                Spacer(minLength: 4)

                GuideHelpLink(topicId: "notifications")

                Toggle(
                    "",
                    isOn: Binding(
                        get: { isOn },
                        set: { value in
                            Task {
                                await apply(enabled: value)
                            }
                        }
                    )
                )
                .labelsHidden()
                .tint(colors.secondary)
                .accessibilityLabel(Text(L("Notifications")))
            }

            if showSystemSettingsHint {
                Button {
                    openSystemSettings()
                } label: {
                    Text(L("Notifications are off for T'Day in iOS Settings. Open Settings to turn them back on."))
                        .font(.tdayRounded(size: 12, weight: .bold))
                        .foregroundStyle(colors.error)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.leading, 34)
                }
                .buttonStyle(.plain)
            }
        }
        .task {
            await refreshAuthorization()
        }
        .onChange(of: viewModel.notificationAuthorizationStatus) { _, _ in
            // `AppRootView` owns the foreground re-read and the reschedule that follows it;
            // all that is left here is to redraw the switch and the hint under it.
            syncDerivedState()
        }
    }

    @MainActor
    private func apply(enabled value: Bool) async {
        // Every exit below leaves the switch in a different place, and the Appearance card
        // dims off the same answer. A `defer` beats repeating this on five return paths.
        defer { deliversNotifications = isOn }

        guard value else {
            showSystemSettingsHint = false
            preferenceEnabled = false
            await viewModel.setNotificationsEnabled(false)
            return
        }

        let status: UNAuthorizationStatus
        if let known = authorizationStatus {
            status = known
        } else {
            status = await viewModel.refreshNotificationAuthorization()
        }

        switch status {
        case .notDetermined:
            // The only status where iOS actually puts its dialog on screen. The tap was the
            // opt-in, so a grant stores the preference itself rather than making the user flip
            // a switch that is already showing what they just asked for.
            let granted = await viewModel.container.reminderScheduler.requestAuthorization()
            await viewModel.refreshNotificationAuthorization()
            showSystemSettingsHint = !granted
            if granted {
                await storePreferenceOn()
            }
        case .denied:
            // iOS never shows the prompt twice, so the app's own Settings page is the only way
            // back. Nothing is stored on the way there: the switch reads off in both denied
            // cells, so its setter can only ever be called with `true`, and writing the
            // preference here would be a state the user has no way to leave again.
            showSystemSettingsHint = true
            openSystemSettings()
        default:
            showSystemSettingsHint = false
            await storePreferenceOn()
        }
    }

    @MainActor
    private func storePreferenceOn() async {
        preferenceEnabled = true
        await viewModel.setNotificationsEnabled(true)
    }

    /// First render of the section. The read (and the reschedule a newly granted permission
    /// needs) belongs to the view model; only the local halves are computed here.
    @MainActor
    private func refreshAuthorization() async {
        await viewModel.refreshNotificationAuthorization()
        syncDerivedState()
    }

    @MainActor
    private func syncDerivedState() {
        preferenceEnabled = store.isEnabled
        // On screen the moment the section renders, not only after a tap: a switch that will
        // not turn on has to say why. Only while the preference is on, though — with it off the
        // switch is off because the user said so, and iOS Settings is beside the point.
        showSystemSettingsHint = authorizationStatus == .denied && preferenceEnabled
        deliversNotifications = isOn
    }

    private func openSystemSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else {
            return
        }
        openURL(url)
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
                HStack(spacing: 14) {
                    SettingsRowIcon(asset: "LucideMoon")

                    Text(L("Quiet hours"))
                        .font(.body.weight(.heavy))
                        .foregroundStyle(colors.onSurface)
                }
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
                .padding(.leading, 34)
                HStack {
                    Text(L("End"))
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(colors.onSurfaceVariant)
                    Spacer()
                    DatePicker("", selection: $endTime, displayedComponents: .hourAndMinute)
                        .labelsHidden()
                        .onChange(of: endTime) { _, value in store.endMinute = Self.minute(from: value) }
                }
                .padding(.leading, 34)
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

// MARK: - Local workspace

/// The way out of a local workspace, and the last card on the screen in Local Mode — where
/// server mode keeps Sign out.
///
/// Two rows, the pair web and Android already offer. "Leave local workspace" is a mode switch:
/// it drops this session's hold and returns to mode selection, and every task stays on the
/// device so choosing Local Mode again finds the workspace where it was left. "Delete local
/// data" is the other thing entirely, and the only row on this screen with nothing behind it
/// to recover from — so it asks first, and points at the export two cards up.
///
/// Leaving deliberately does not go through `logout()`: that routes into
/// `clearAllLocalUserDataForUnauthenticatedState()`, which would make the row labelled Leave
/// wipe every task on the device without asking — the job of the row underneath it.
private struct SettingsLocalWorkspaceExit: View {
    let viewModel: AppViewModel

    @Environment(\.tdayColors) private var colors
    @State private var showConfirm = false

    var body: some View {
        SettingsListRow(
            title: "Leave local workspace",
            value: nil,
            iconTint: colors.error,
            showChevron: false,
            icon: "LucideLogOut",
            action: {
                Task {
                    await viewModel.leaveLocalWorkspace()
                }
            }
        )

        SettingsDivider()

        SettingsListRow(
            title: "Delete local data",
            value: nil,
            titleColor: colors.error,
            showChevron: false,
            icon: "LucideTrash2",
            action: {
                showConfirm = true
            }
        )
        .confirmationDialog(
            Text(L("Delete local data?")),
            isPresented: $showConfirm,
            titleVisibility: .visible
        ) {
            Button(L("Delete"), role: .destructive) {
                Task { await deleteLocalData() }
            }
            Button(L("Cancel"), role: .cancel) {}
        } message: {
            Text(L("This permanently removes every task, list and completed entry stored on this device. There is no copy on a server — download your data first if you want to keep it."))
        }
    }

    @MainActor
    private func deleteLocalData() async {
        // The reset the app already performs on logout and on entering Local Mode: cache,
        // cookies, keychain values, theme, reminder preferences and the stored data mode.
        // `logout()` then re-bootstraps, which lands on the setup screen because there is
        // nothing configured left to land on.
        viewModel.container.authRepository.clearAllLocalUserDataForUnauthenticatedState()
        viewModel.container.snackbarManager.show(L("Local data deleted."), kind: .success)
        await viewModel.logout()
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
            HStack(alignment: .center, spacing: 14) {
                SettingsRowIcon(asset: "LucideUser")

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
        HStack(alignment: .center, spacing: 14) {
            SettingsRowIcon(asset: "LucideAtSign")

            VStack(alignment: .leading, spacing: 2) {
                SettingsFieldLabel("Username")
                Text(username)
                    .font(.tdayRounded(size: 16, weight: .bold))
                    .foregroundStyle(colors.onSurface.opacity(0.72))
            }
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
            HStack(alignment: .center, spacing: 14) {
                SettingsRowIcon(asset: "LucideLock")

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
            HStack(alignment: .center, spacing: 14) {
                SettingsRowIcon(asset: "LucideShieldQuestion")

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

/// The one settings affordance shape: a continuous capsule filled with the secondary accent at
/// 12%, secondary-coloured content, a heavy 12pt glyph and a heavy 14pt label 5pt apart, 34 tall
/// with 14 of horizontal padding. The profile card's Edit and Change wear it, and so does every
/// row below that changes a value in place.
///
/// `lineLimit(1)` because the capsule's height is fixed: a label that wrapped would be clipped
/// rather than shown, which is worse than a truncation the row can be made wide enough to avoid.
private struct SettingsPillLabel: View {
    let title: String
    let systemImage: String

    @Environment(\.tdayColors) private var colors

    var body: some View {
        HStack(spacing: 5) {
            Image(systemName: systemImage)
                .font(.system(size: 12, weight: .heavy))
            Text(title)
                .font(.tdayRounded(size: 14, weight: .heavy))
                .lineLimit(1)
        }
        .foregroundStyle(colors.secondary)
        .padding(.horizontal, 14)
        .frame(height: 34)
        .background(Capsule(style: .continuous).fill(colors.secondary.opacity(0.12)))
    }
}

private struct SettingsInlineEditButton: View {
    let title: String
    let systemImage: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            SettingsPillLabel(title: L(title), systemImage: systemImage)
        }
        .buttonStyle(.plain)
    }
}

/// A row whose right-hand side is a value the user changes here and now, without leaving the
/// screen: the current value *is* the button, in the same capsule the profile card uses.
///
/// No chevron. A chevron on this screen means "this row goes somewhere else" — How-To, App
/// Version — and these three go nowhere; they open a picker over the settings they belong to.
///
/// The pill takes layout priority so the value stays whole and the label wraps instead. In
/// Spanish "Recordatorio predeterminado" already wraps next to today's 13pt value, so this is
/// the row's existing bargain rather than a new one, made explicit.
private struct SettingsValueRow: View {
    let title: String
    /// Already localized by whoever owns it — `ReminderOption.label`, `DayAheadOption.label`,
    /// `AppLanguage.endonym` — so it goes into the pill verbatim, never through `L()` twice.
    let value: String
    let icon: String
    let pillSystemImage: String
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: action) {
            HStack {
                HStack(spacing: 14) {
                    SettingsRowIcon(asset: icon)

                    Text(L(title))
                        .font(.tdayRounded(size: 17, weight: .heavy))
                        .foregroundStyle(colors.onSurface)
                }

                Spacer(minLength: 12)

                SettingsPillLabel(title: value, systemImage: pillSystemImage)
                    .layoutPriority(1)
            }
            .frame(maxWidth: .infinity, minHeight: 34, alignment: .center)
            .contentShape(Rectangle())
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

/// Heading and sync facts for the About card — what this install is and, in
/// server mode, how current it is.
private struct SettingsAboutContent: View {
    let syncStatus: MobileSyncStatus
    let helpTopicId: String
    let onSyncNow: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                SettingsSectionTitle("About")
                Spacer()
                GuideHelpLink(topicId: helpTopicId)
            }

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
        // Lifts the card off the page so its options read as a surface rather
        // than as an outlined region of the background.
        .shadow(
            color: Color.black.opacity(colors.isDark ? 0.42 : 0.10),
            radius: 16,
            x: 0,
            y: 8
        )
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

/// Which root feed (Scheduled or Floaters) opens on a fresh cold launch. Same
/// `TdayNativeSegmentedControl` shape as `SettingsThemeSelector` — a named,
/// mutually-exclusive choice, not a toggle — and reuses `RootFeedTab.title` so the wording
/// matches the in-app dock.
private struct SettingsRootFeedTabSelector: View {
    let selectedTab: RootFeedTab
    let onSelect: (RootFeedTab) -> Void

    private let tabs: [RootFeedTab] = [.scheduledTaskHome, .floaterTaskHome]

    var body: some View {
        TdayNativeSegmentedControl(
            labels: tabs.map(\.title),
            selectedIndex: tabs.firstIndex(of: selectedTab) ?? 0,
            accentColor: settingsSegmentedControlAccentColor,
            onSelect: { index in
                guard tabs.indices.contains(index) else {
                    return
                }
                onSelect(tabs[index])
            }
        )
        .frame(maxWidth: .infinity)
        .frame(height: TdayNativeSegmentedControlMetrics.height)
    }
}

private struct SettingsLanguageSelector: View {
    let currentLanguage: String
    let onOpen: () -> Void

    var body: some View {
        SettingsValueRow(
            title: "Language",
            value: Self.label(for: currentLanguage),
            icon: "LucideLanguages",
            pillSystemImage: "globe",
            action: onOpen
        )
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

    var body: some View {
        SettingsValueRow(
            title: "Default reminder",
            value: selectedReminder.label,
            icon: "LucideBell",
            // Not a second bell: the row's glyph already says "reminder", and what this
            // picks is how far ahead of the task it lands.
            pillSystemImage: "clock.fill",
            action: onOpen
        )
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
        case .tenMinutes:
            return Color(red: 0.40, green: 0.62, blue: 0.74)
        case .fifteenMinutes:
            return Color(red: 0.78, green: 0.58, blue: 0.40)
        case .thirtyMinutes:
            return Color(red: 0.80, green: 0.68, blue: 0.38)
        case .oneHour:
            return Color(red: 0.56, green: 0.70, blue: 0.48)
        case .twoHours:
            return Color(red: 0.48, green: 0.72, blue: 0.62)
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

    var body: some View {
        SettingsValueRow(
            title: "Day Ahead digest",
            value: selected.label,
            icon: "LucideBellRing",
            // What this picks is a morning hour, and every value it can show is one.
            pillSystemImage: "sunrise.fill",
            action: onOpen
        )
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
        HStack(spacing: 14) {
            SettingsRowIcon(asset: "LucideSparkles")

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
    }
}

private struct SettingsListRow: View {
    let title: String
    let value: String?
    var titleColor: Color?
    /// A glyph colour of its own, for a row whose label is deliberately the neutral
    /// foreground — Leave local workspace is an exit, but it destroys nothing, so only the
    /// glyph carries the warmth. Android and web spell the same distinction
    /// (`iconTint` / `destructiveIcon`).
    var iconTint: Color?
    var showChevron = true
    var icon: String?
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: action) {
            // titleColor stays optional on the way down: SettingsRowLabel falls back to
            // onSurface for the text, and only a row that asked for a colour of its own
            // (Sign out) hands that colour to its icon instead of the usual blue.
            SettingsRowLabel(
                title: title,
                value: value,
                titleColor: titleColor,
                valueColor: colors.onSurface.opacity(0.58),
                iconTint: iconTint,
                showChevron: showChevron,
                icon: icon
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
            HStack(spacing: 14) {
                // A fact rather than a button, but its neighbours in this card all carry a
                // glyph and an empty slot next to them read as a missing icon, not a
                // deliberate one. Same lucide "server" rack the web app uses on this row.
                SettingsRowIcon(asset: "LucideServer")

                Text("Server")
                    .font(.tdayRounded(size: 17, weight: .heavy))
                    .foregroundStyle(colors.onSurface)
            }

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

/// Leading glyph for a settings row: a 20pt Lucide asset in the accent blue, or — with a nil
/// asset — an equal-size empty slot that keeps a glyph-less row's label aligned with the rest of
/// its card. Rows leave 14pt between the slot and their label, so anything that has to line up
/// under the label rather than the icon is inset by 34pt.
private struct SettingsRowIcon: View {
    let asset: String?
    var tint: Color?

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Group {
            if let asset {
                Image(asset)
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .foregroundStyle(tint ?? colors.secondary)
            } else {
                Color.clear
            }
        }
        .frame(width: 20, height: 20)
        // Decorative — the row's own label carries the meaning. Without this the asset name
        // leaks into the VoiceOver label of the button or toggle wrapping the row.
        .accessibilityHidden(true)
    }
}

private struct SettingsRowLabel: View {
    let title: String
    let value: String?
    var titleColor: Color?
    var valueColor: Color?
    var iconTint: Color?
    var showChevron: Bool
    var icon: String?

    @Environment(\.tdayColors) private var colors

    init(
        title: String,
        value: String?,
        titleColor: Color? = nil,
        valueColor: Color? = nil,
        iconTint: Color? = nil,
        showChevron: Bool = true,
        icon: String? = nil
    ) {
        self.title = title
        self.value = value
        self.titleColor = titleColor
        self.valueColor = valueColor
        self.iconTint = iconTint
        self.showChevron = showChevron
        self.icon = icon
    }

    var body: some View {
        HStack {
            HStack(spacing: 14) {
                // A row that overrides its title colour (Sign out) tints its icon to match;
                // a row that asked for a glyph colour alone (Leave local workspace) gets that;
                // every other row gets the accent blue from SettingsRowIcon.
                SettingsRowIcon(asset: icon, tint: iconTint ?? titleColor)

                Text(L(title))
                    .font(.tdayRounded(size: 17, weight: .heavy))
                    .foregroundStyle(titleColor ?? colors.onSurface)
            }

            Spacer(minLength: 12)

            if let value {
                Text(L(value))
                    .font(.tdayRounded(size: 13, weight: .heavy))
                    .foregroundStyle(valueColor ?? colors.onSurface.opacity(0.58))
                    .lineLimit(1)
                    .minimumScaleFactor(0.78)
            }

            if showChevron {
                Image("LucideChevronRight")
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 16, height: 16)
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

            // 24, as Android's settings list ends (`Spacer(24.dp)`), not the 258
            // this used to reserve — `titleCollapseDistance` (178) plus a bar row
            // plus 24. That much existed so the title could always finish
            // collapsing even on a short page, and it paid for that by leaving a
            // third of the screen blank under the last card on every page, short
            // or not. It is not needed: `onVerticalScrollSnap` already settles a
            // page that cannot collapse fully back to expanded, which is the
            // bargain Android makes too.
            Color.clear
                .frame(height: 24)
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
            collapseProgress: titleCollapseProgress,
            mark: Image("LucideCloudDownload"),
            markAccentColor: colors.primary
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
    /// An English literal from the call site, so it goes through `L()` here — `Text(label)`
    /// on a `String` variable takes the verbatim initializer, not the localized one, and
    /// rendered "Installed Version" in every locale.
    let label: String
    let version: String
    let tint: Color

    @Environment(\.tdayColors) private var colors

    var body: some View {
        HStack(spacing: 10) {
            Text(L(label))
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
            Text(L("Published %@", formatIsoDate(publishedAt)))
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
            Text(L("What's new in %@", versionLabel))
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
            Text(L("%@ tasks · %@ lists · %@ completed", "\(taskCount)", "\(listCount)", "\(completedCount)"))
                .font(.tdayRounded(size: 13, weight: .semibold))
                .foregroundStyle(.secondary)

            SettingsListRow(title: "Download my data", value: nil, showChevron: false, icon: "LucideDownload") {
                startExport()
            }

            SettingsDivider()

            // Unconditional: the whole card is Server Mode only now, so the
            // "sign in to a server to import" line this used to sit opposite could
            // never be reached again.
            SettingsListRow(title: "Import", value: nil, showChevron: false, icon: "LucideUpload") {
                if !busy { showImporter = true }
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
                notify(L("Your data file was saved."), kind: .success)
            }
        }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.json]) { result in
            handleImportPick(result)
        }
        .confirmationDialog(Text(L("Import data?")), isPresented: $showConfirm, titleVisibility: .visible) {
            Button(L("Import")) {
                confirmImport()
            }
            Button(L("Cancel"), role: .cancel) {
                clearPending()
            }
        } message: {
            Text(L("This adds %@ items to your account. Nothing you already have is changed or removed.", "\(previewCount)"))
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
            notify(L("Could not read that file."), kind: .error)
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
                notify(L("That file isn't a valid T'Day export."), kind: .error)
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
                notify(L("Import complete — added %@ items.", "\(response.imported.total)"), kind: .success)
                loadCounts()
            } catch {
                notify(L("Could not import that file."), kind: .error)
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
