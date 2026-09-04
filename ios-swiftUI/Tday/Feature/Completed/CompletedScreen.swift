import SwiftUI
import UIKit

private enum CompletedRestorePhase {
    case completed
    case unchecked
    case unstruck
    case fading
}

struct CompletedScreen: View {
    private let pullRefreshEnabled: Bool
    @State private var viewModel: CompletedViewModel
    @Environment(\.tdayColors) private var colors
    @Environment(\.dismiss) private var dismiss
    @State private var editingItem: CompletedItem?
    @State private var timelineScrollOffset: CGFloat = 0
    @State private var collapsedSectionIDs: Set<String> = []
    @State private var openSwipeTaskID: String?
    @FocusState private var searchFieldFocused: Bool
    @State private var searchExpanded = false
    @State private var searchQuery = ""

    init(container: AppContainer, pullRefreshEnabled: Bool = false) {
        self.pullRefreshEnabled = pullRefreshEnabled
        _viewModel = State(initialValue: CompletedViewModel(container: container))
    }

    private var groupedItems: [TimelineSection<CompletedItem>] {
        buildCompletedTimelineSections(items: searchedItems)
    }

    private var normalizedSearchQuery: String {
        searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(with: .current)
    }

    private var isSearching: Bool {
        searchExpanded && !normalizedSearchQuery.isEmpty
    }

    /// The history and nothing else: this screen searches what it is showing,
    /// the way each web page searches its own list.
    private var searchedItems: [CompletedItem] {
        guard isSearching else {
            return viewModel.items
        }
        return viewModel.items.filter { item in
            item.title.lowercased(with: .current).contains(normalizedSearchQuery) ||
                flattenNotesToPlainText(item.description)
                    .lowercased(with: .current)
                    .contains(normalizedSearchQuery)
        }
    }

    private var searchPlaceholder: String {
        L("Search in %@", L("Completed"))
    }

    /// No magnifier over an empty history: there is no set for a query to
    /// narrow, and the button would only raise a keyboard over the empty-state
    /// scene, which is the whole of what the screen has to say. Gates the
    /// button, not the bar — a search already open stays open.
    private var topBarActions: [TimelineTopBarAction] {
        guard !viewModel.items.isEmpty else {
            return []
        }
        return [
            TimelineTopBarAction(
                systemName: "magnifyingglass",
                assetName: "NavSearch",
                usesCircularChrome: true,
                accessibilityLabel: L("Search"),
                action: openSearch
            ),
        ]
    }

    private var completedAccentColor: Color {
        Color(.sRGB, red: 94.0 / 255.0, green: 104.0 / 255.0, blue: 120.0 / 255.0, opacity: 1)
    }

    private var completedCheckmarkColor: Color {
        Color(.sRGB, red: 111.0 / 255.0, green: 191.0 / 255.0, blue: 134.0 / 255.0, opacity: 1)
    }

    private var titleCollapseProgress: CGFloat {
        let distance = TodoTimelineMetrics.titleCollapseDistance
        guard distance > 0 else { return 0 }
        return min(max(timelineScrollOffset / distance, 0), 1)
    }

    private var completedTimelineAnimationKey: String {
        searchedItems.map(\.id).joined(separator: "|")
    }

    var body: some View {
        completedTimelineContent
            .tdayPullToRefresh(isRefreshing: viewModel.isLoading, isEnabled: pullRefreshEnabled) {
                await viewModel.refresh(userInitiated: true)
            }
            .background(colors.background)
            .overlay {
                // No blanket `allowsHitTesting(false)` here any more: the watermark
                // turns its own hits off, and the search empty state has a button
                // that has to stay tappable.
                ZStack {
                    EmptyTaskWatermark(
                        systemName: "checkmark",
                        accentColor: completedAccentColor,
                        assetName: "TileComplete"
                    )
                    if searchedItems.isEmpty, !viewModel.isLoading {
                        if isSearching {
                            searchEmptyState
                        } else {
                            TdayEmptyState(
                                assetName: "TileComplete",
                                accentColor: completedAccentColor,
                                title: L("No completed tasks"),
                                description: L("Tick something off and it will land here.")
                            )
                            // Pull-to-refresh still has to work on an empty
                            // history, and a Text sitting in an overlay would
                            // swallow the drag before the list ever saw it.
                            .allowsHitTesting(false)
                        }
                    }
                }
            }
            // Tapping the content puts the field away, as it does on the root feeds.
            // Sits above the bar's own safe-area inset, so the gesture only ever
            // sees taps below the bar and never one on it.
            .tdayClosesSearchOnOutsideTap(isSearchOpen: searchExpanded) {
                closeSearch()
            }
            .navigationBackButtonBehavior()
            .navigationTitleTypography(
                largeTitleColor: completedAccentColor,
                inlineTitleColor: colors.onSurface,
                backgroundColor: colors.background
            )
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(.hidden, for: .navigationBar)
            .safeAreaInset(edge: .top, spacing: 0) {
                TimelineTopBar(
                    title: L("Completed"),
                    accentColor: completedAccentColor,
                    collapseProgress: titleCollapseProgress,
                    onBack: { dismiss() },
                    actions: topBarActions,
                    searchActive: searchExpanded,
                    searchText: $searchQuery,
                    searchPlaceholder: searchPlaceholder,
                    searchFieldFocused: $searchFieldFocused,
                    onSearchClose: closeSearch
                )
            }
            .onChange(of: viewModel.items.map(\.id)) { _, ids in
                guard let openSwipeTaskID, !ids.contains(openSwipeTaskID) else { return }
                self.openSwipeTaskID = nil
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
            .createTaskSheet(item: $editingItem) { item in
                CreateTaskSheet(
                    lists: item.isFloater ? viewModel.floaterLists : viewModel.lists,
                    titleText: L("Edit task"),
                    submitText: L("Save"),
                    initialPayload: CreateTaskPayload(title: item.title, description: item.description, priority: item.priority, due: item.due, rrule: item.rrule, listId: nil),
                    onParseTaskTitleNlp: nil,
                    onDismiss: { editingItem = nil },
                    onSubmit: { payload in
                        await viewModel.update(item, payload: payload)
                    }
                )
            }
            // Same bargain as the task timeline: the only field on this screen is
            // the search box in the top safe-area inset, which the keyboard cannot
            // reach, so keyboard avoidance had nothing to protect and only shortened
            // the history's region — carrying the centred empty scene up with it.
            .ignoresSafeArea(.keyboard, edges: .bottom)
    }

    private var completedTimelineContent: some View {
        ZStack {
            List {
                timelineHeroTitleRow

                if let errorMessage = viewModel.errorMessage {
                    Section {
                        ErrorRetryView(message: errorMessage) {
                            Task { await viewModel.refresh() }
                        }
                        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 18, trailing: TodoTimelineMetrics.horizontalPadding))
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                    }
                }

                ForEach(Array(groupedItems.enumerated()), id: \.element.id) { index, section in
                    completedTimelineSection(
                        section,
                        sectionIndex: index,
                        sections: groupedItems,
                        isFirstSection: index == 0
                    )
                }

                Color.clear
                    .frame(height: 120)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                    .disableVerticalScrollBounce()
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .contentMargins(.top, 0, for: .scrollContent)
            .listRowSpacing(0)
            .listSectionSpacing(0)
            .environment(\.defaultMinListRowHeight, 1)
            .disableVerticalScrollBounce()
            .animation(.easeInOut(duration: 0.24), value: completedTimelineAnimationKey)

        }
    }

    private var timelineHeroTitleRow: some View {
        TimelineExpandedTitleRow(
            title: L("Completed"),
            accentColor: completedAccentColor,
            collapseProgress: titleCollapseProgress,
            mark: Image("TileComplete")
        )
        .background {
            TimelineScrollOffsetObserver { timelineScrollOffset = $0 }
                .frame(width: 0, height: 0)
        }
        .onVerticalScrollSnap(collapseDistance: TodoTimelineMetrics.titleCollapseDistance)
        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }

    /// Shown in place of the history when a search matches nothing. Same three
    /// beats as web's panel: the scene, the line, and a way out of the query
    /// that does not need the keyboard back.
    private var searchEmptyState: some View {
        TdayEmptyState(
            assetName: "NavSearch",
            accentColor: completedAccentColor,
            title: L("No matching tasks"),
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
    }

    private func openSearch() {
        HapticManager.buttonTap()
        withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
            searchExpanded = true
        }
    }

    /// Leaving the search drops the query with it, so the history is whole again
    /// the next time the bar is opened — the same bargain web's close makes.
    private func closeSearch() {
        HapticManager.sheetDismiss()
        searchFieldFocused = false
        withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
            searchExpanded = false
        }
        searchQuery = ""
    }

    @ViewBuilder
    private func completedTimelineSection(
        _ section: TimelineSection<CompletedItem>,
        sectionIndex: Int,
        sections: [TimelineSection<CompletedItem>],
        isFirstSection: Bool
    ) -> some View {
        // A live query outranks a shut month: history opens with older months
        // collapsed, and a task the search turned up inside one must not stay
        // hidden behind its header. The month is therefore not the reader's to
        // shut while the query stands — a header that still took a tap would
        // flip the stored state behind a screen that could not show it. Same
        // call as `canCollapseTimelineSection` on the timeline screens.
        let isCollapsible = !isSearching
        let isCollapsed = isCollapsible && collapsedSectionIDs.contains(section.id)

        Section {
            if !isCollapsed {
                ForEach(Array(section.items.enumerated()), id: \.element.id) { itemIndex, item in
                    completedTimelineRow(item)
                        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                        .transition(completedRowTransition())
                    if shouldShowDateDivider(after: itemIndex, inSectionAt: sectionIndex, sections: sections) {
                        TimelineRowDivider()
                            .transition(completedRowTransition())
                    }
                }
            }
        } header: {
            TimelineSectionHeader(
                title: section.title,
                isActiveDropTarget: false,
                isCollapsible: isCollapsible,
                isCollapsed: isCollapsed,
                onTap: {
                    guard isCollapsible else { return }
                    toggleCompletedSection(section)
                }
            )
            .listRowInsets(
                EdgeInsets(
                    top: isFirstSection ? 0 : TodoTimelineMetrics.sectionTopSpacing,
                    leading: 0,
                    bottom: 0,
                    trailing: 0
                )
            )
            .timelinePinnedSectionHeaderBackground()
            .listRowSeparator(.hidden)
        }
    }

    private func toggleCompletedSection(_ section: TimelineSection<CompletedItem>) {
        let id = section.id
        withAnimation(.spring(response: 0.28, dampingFraction: 0.9)) {
            if collapsedSectionIDs.contains(id) {
                collapsedSectionIDs.remove(id)
            } else {
                collapsedSectionIDs.insert(id)
            }
        }
    }

    private func shouldShowDateDivider(
        after itemIndex: Int,
        inSectionAt sectionIndex: Int,
        sections: [TimelineSection<CompletedItem>]
    ) -> Bool {
        guard sections.indices.contains(sectionIndex),
              sections[sectionIndex].items.indices.contains(itemIndex) else {
            return false
        }

        let currentItem = sections[sectionIndex].items[itemIndex]
        let currentDate = currentItem.completedAt ?? currentItem.due ?? .distantPast
        let nextItemInSection = sections[sectionIndex].items.dropFirst(itemIndex + 1).first
        if let nextItemInSection {
            let nextDate = nextItemInSection.completedAt ?? nextItemInSection.due ?? .distantPast
            return !Calendar.current.isDate(currentDate, inSameDayAs: nextDate)
        }

        let nextVisibleItem = sections.dropFirst(sectionIndex + 1)
            .first { !collapsedSectionIDs.contains($0.id) && !$0.items.isEmpty }?
            .items.first

        guard let nextVisibleItem else {
            return false
        }
        let nextDate = nextVisibleItem.completedAt ?? nextVisibleItem.due ?? .distantPast
        return !Calendar.current.isDate(currentDate, inSameDayAs: nextDate)
    }

    private func completedRowTransition() -> AnyTransition {
        let insertion = AnyTransition.opacity
            .combined(with: .move(edge: .top))
            .animation(.easeOut(duration: 0.16))
        let removal = AnyTransition.opacity
            .combined(with: .move(edge: .top))
            .animation(.easeOut(duration: 0.1))
        return .asymmetric(insertion: insertion, removal: removal)
    }

    private func completedTimelineRow(_ item: CompletedItem) -> some View {
        CompletedTimelineRow(
            item: item,
            completedCheckmarkColor: completedCheckmarkColor,
            onUncomplete: {
                await viewModel.uncomplete(item)
            },
            onDelete: {
                await viewModel.delete(item)
            },
            onEdit: {
                editingItem = item
            },
            onCopy: {
                viewModel.copyToClipboard(item)
            },
            openSwipeTaskID: $openSwipeTaskID
        )
    }
}

private struct CompletedTimelineRow: View {
    let item: CompletedItem
    let completedCheckmarkColor: Color
    let onUncomplete: () async -> Void
    let onDelete: () async -> Void
    let onEdit: () -> Void
    let onCopy: () -> Void
    @Binding var openSwipeTaskID: String?

    @Environment(\.tdayColors) private var colors
    @State private var restorePhase = CompletedRestorePhase.completed

    private var showCompletedCheckmark: Bool {
        restorePhase == .completed
    }

    private var showStrikethrough: Bool {
        restorePhase == .completed || restorePhase == .unchecked
    }

    private var isRestoring: Bool {
        restorePhase != .completed
    }

    private var isFading: Bool {
        restorePhase == .fading
    }

    private var toggleColor: Color {
        showCompletedCheckmark ? completedCheckmarkColor : colors.onSurfaceVariant.opacity(0.78)
    }

    private var titleColor: Color {
        showStrikethrough ? colors.onSurface.opacity(0.78) : colors.onSurface
    }

    var body: some View {
        let completedDate = item.completedAt ?? item.due ?? .distantPast
        let completedTimeText = completedDate.formatted(.dateTime.hour().minute().locale(AppLocale.current))
        let showListIndicator = item.listName?.isEmpty == false
        let priorityIcon = priorityIndicatorSymbolName(item.priority)

        VStack(spacing: 0) {
            HStack(alignment: .center, spacing: 12) {
                Button {
                    startRestore()
                } label: {
                    Image(systemName: showCompletedCheckmark ? "checkmark.circle.fill" : "circle")
                        .font(.system(size: TodoTimelineMetrics.minimalRowToggleSize, weight: .regular))
                        .foregroundStyle(toggleColor)
                        .frame(
                            width: TodoTimelineMetrics.minimalRowToggleFrame,
                            height: TodoTimelineMetrics.minimalRowToggleFrame
                        )
                }
                .buttonStyle(
                    TdayPressButtonStyle(
                        shadowColor: Color.black,
                        pressedShadowOpacity: 0,
                        normalShadowOpacity: 0
                    )
                )
                .disabled(isRestoring)
                .accessibilityLabel("Undo complete")

                VStack(alignment: .leading, spacing: 4) {
                    TodoTimelineTaskTitle(
                        text: item.title,
                        isCompleted: showStrikethrough,
                        titleColor: titleColor,
                        strikeColor: colors.onSurface.opacity(0.65)
                    )

                    HStack(spacing: 5) {
                        Image(systemName: "clock")
                            .font(.system(size: 10, weight: .bold))
                        Text(completedTimeText)
                            .font(.tdayRounded(size: TodoTimelineMetrics.minimalRowSubtitleSize, weight: .semibold))
                    }
                    .foregroundStyle(colors.onSurfaceVariant.opacity(0.78))
                }

                Spacer(minLength: 0)

                if showListIndicator || priorityIcon != nil {
                    HStack(spacing: 8) {
                        if showListIndicator {
                            Image(systemName: "tray.fill")
                                .font(.system(size: TodoTimelineMetrics.minimalRowIndicatorSize, weight: .semibold))
                                .foregroundStyle(todoListAccentColor(for: item.listColor))
                        }
                        if let priorityIcon {
                            Image(systemName: priorityIcon)
                                .font(.system(size: TodoTimelineMetrics.minimalRowIndicatorSize, weight: .semibold))
                                .foregroundStyle(priorityColor(item.priority))
                        }
                    }
                    .padding(.trailing, TodoTimelineMetrics.minimalRowTrailingIndicatorPadding)
                }
            }
            .padding(.vertical, TodoTimelineMetrics.minimalRowVerticalPadding)
            .contentShape(Rectangle())
        }
        .opacity(isFading ? 0 : 1)
        .scaleEffect(isFading ? 0.985 : 1, anchor: .center)
        .offset(y: isFading ? -10 : 0)
        .animation(.easeInOut(duration: 0.26), value: isFading)
        .transition(.opacity.combined(with: .scale(scale: 0.985)))
        .allowsHitTesting(!isRestoring)
        .todoTrailingSwipeActions(
            rowID: item.id,
            openRowID: $openSwipeTaskID,
            enabled: !isRestoring,
            onEdit: onEdit,
            onCopy: onCopy,
            onDelete: {
                Task { await onDelete() }
            }
        )
    }

    private func startRestore() {
        guard restorePhase == .completed else {
            return
        }
        if openSwipeTaskID == item.id {
            openSwipeTaskID = nil
        }

        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        Task { @MainActor in
            withAnimation(.easeInOut(duration: 0.16)) {
                restorePhase = .unchecked
            }
            try? await Task.sleep(nanoseconds: 180_000_000)
            withAnimation(.easeInOut(duration: 0.16)) {
                restorePhase = .unstruck
            }
            try? await Task.sleep(nanoseconds: 180_000_000)
            withAnimation(.easeInOut(duration: 0.26)) {
                restorePhase = .fading
            }
            try? await Task.sleep(nanoseconds: 260_000_000)
            await onUncomplete()
        }
    }
}

private func buildCompletedTimelineSections(items: [CompletedItem]) -> [TimelineSection<CompletedItem>] {
    let calendar = Calendar.current
    let grouped = Dictionary(grouping: items) { item in
        calendar.startOfDay(for: item.completedAt ?? item.due ?? .distantPast)
    }

    return grouped.keys.sorted(by: >).map { date in
        let sectionItems = (grouped[date] ?? []).sorted { lhs, rhs in
            let lhsCompletedAt = lhs.completedAt ?? lhs.due ?? .distantPast
            let rhsCompletedAt = rhs.completedAt ?? rhs.due ?? .distantPast
            if lhsCompletedAt != rhsCompletedAt {
                return lhsCompletedAt > rhsCompletedAt
            }
            return lhs.title.localizedCaseInsensitiveCompare(rhs.title) == .orderedAscending
        }

        return TimelineSection(
            id: "completed-\(date.timeIntervalSince1970)",
            title: completedTimelineSectionTitle(for: date),
            items: sectionItems,
            isCollapsible: false
        )
    }
}

private func completedTimelineSectionTitle(for date: Date) -> String {
    CompletedTimelineFormatters.sectionTitle().string(from: date)
}

private enum CompletedTimelineFormatters {
    static func sectionTitle() -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = AppLocale.current
        formatter.setLocalizedDateFormatFromTemplate("EEEE MMM d")
        return formatter
    }
}
