import SwiftUI

private enum CompletedRestorePhase: String, Hashable {
    case unchecked
    case unstruck
    case fading
}

struct CompletedScreen: View {
    @State private var viewModel: CompletedViewModel
    @Environment(\.tdayColors) private var colors
    @Environment(\.dismiss) private var dismiss
    @State private var editingItem: CompletedItem?
    @State private var timelineScrollOffset: CGFloat = 0
    @State private var collapsedSectionIDs: Set<String> = []
    @State private var collapsingSectionIDs: Set<String> = []
    @State private var revealingSectionIDs: Set<String> = []
    @State private var restorePhases: [String: CompletedRestorePhase] = [:]

    init(container: AppContainer) {
        _viewModel = State(initialValue: CompletedViewModel(container: container))
    }

    private var groupedItems: [TimelineSection<CompletedItem>] {
        buildCompletedTimelineSections(items: viewModel.items)
    }

    private var completedAccentColor: Color {
        Color(.sRGB, red: 94.0 / 255.0, green: 104.0 / 255.0, blue: 120.0 / 255.0, opacity: 1)
    }

    private var titleCollapseProgress: CGFloat {
        let distance = TodoTimelineMetrics.titleCollapseDistance
        guard distance > 0 else { return 0 }
        return min(max(timelineScrollOffset / distance, 0), 1)
    }

    private var completedTimelineAnimationKey: String {
        let itemIDs = viewModel.items.map(\.id).joined(separator: "|")
        let phaseIDs = restorePhases
            .sorted { $0.key < $1.key }
            .map { "\($0.key):\($0.value.rawValue)" }
            .joined(separator: "|")
        return "\(itemIDs)::\(phaseIDs)"
    }

    private var firstVisibleExpandedCompletedSectionID: String? {
        groupedItems.first { section in
            !section.items.isEmpty && !collapsedSectionIDs.contains(section.id)
        }?.id
    }

    var body: some View {
        completedTimelineContent
            .background(colors.background)
            .navigationBackButtonBehavior()
            .navigationTitleTypography(
                largeTitleColor: completedAccentColor,
                inlineTitleColor: colors.onSurface,
                backgroundColor: colors.background
            )
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(.hidden, for: .navigationBar)
            .onChange(of: viewModel.items) {
                pruneRestorePhases()
                collapsingSectionIDs = []
                revealingSectionIDs = []
            }
            .safeAreaInset(edge: .top, spacing: 0) {
                TimelineTopBar(
                    title: "Completed",
                    accentColor: completedAccentColor,
                    collapseProgress: titleCollapseProgress,
                    onBack: { dismiss() },
                    action: nil
                )
            }
            .sheet(item: $editingItem) { item in
                CreateTaskSheet(
                    lists: viewModel.lists,
                    titleText: "Edit task",
                    submitText: "Save",
                    initialPayload: CreateTaskPayload(title: item.title, description: item.description, priority: item.priority, due: item.due, rrule: item.rrule, listId: nil),
                    onParseTaskTitleNlp: nil,
                    onDismiss: { editingItem = nil },
                    onSubmit: { payload in
                        await viewModel.update(item, payload: payload)
                    }
                )
            }
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
                    completedTimelineSection(section, isFirstSection: index == 0)
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
            .listSectionSpacing(0)
            .environment(\.defaultMinListRowHeight, 1)
            .disableVerticalScrollBounce()
            .animation(.easeInOut(duration: 0.24), value: completedTimelineAnimationKey)

            if viewModel.items.isEmpty {
                TimelineEmptyState(message: "No completed tasks")
                    .allowsHitTesting(false)
            }
        }
    }

    private var timelineHeroTitleRow: some View {
        TimelineExpandedTitleRow(
            title: "Completed",
            accentColor: completedAccentColor,
            collapseProgress: titleCollapseProgress
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

    @ViewBuilder
    private func completedTimelineSection(_ section: TimelineSection<CompletedItem>, isFirstSection: Bool) -> some View {
        let isCollapsed = collapsedSectionIDs.contains(section.id)
        let shouldRenderBody = shouldRenderCompletedSectionBody(section)
        let bodyOpacity = completedSectionBodyOpacity(section)

        Section {
            if shouldRenderBody {
                ForEach(Array(section.items.enumerated()), id: \.element.id) { itemIndex, item in
                    completedTimelineRow(item)
                        .padding(.top, firstPinnedRowElasticTopInset(isFirstVisibleExpandedSection: section.id == firstVisibleExpandedCompletedSectionID, itemIndex: itemIndex))
                        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                        .opacity(bodyOpacity)
                        .animation(.easeOut(duration: 0.14), value: bodyOpacity)
                    TimelineRowDivider()
                        .opacity(bodyOpacity)
                        .animation(.easeOut(duration: 0.14), value: bodyOpacity)
                }
            }
        } header: {
            TimelineSectionHeader(
                title: section.title,
                isActiveDropTarget: false,
                isCollapsible: true,
                isCollapsed: isCollapsed,
                onTap: {
                    toggleCompletedSection(section)
                }
            )
            .listRowInsets(
                EdgeInsets(
                    top: isFirstSection ? 0 : 8,
                    leading: 0,
                    bottom: 0,
                    trailing: 0
                )
            )
            .timelinePinnedSectionHeaderBackground()
            .listRowSeparator(.hidden)
        }
    }

    private func firstPinnedRowElasticTopInset(isFirstVisibleExpandedSection: Bool, itemIndex: Int) -> CGFloat {
        guard isFirstVisibleExpandedSection, itemIndex == 0 else {
            return 0
        }

        let elasticProgress = TodoTimelineMetrics.progress(
            titleCollapseProgress,
            from: TodoTimelineMetrics.firstPinnedRowElasticStart,
            to: TodoTimelineMetrics.firstPinnedRowElasticEnd
        )
        return TodoTimelineMetrics.firstPinnedRowElasticClearance * elasticProgress
    }

    private func shouldRenderCompletedSectionBody(_ section: TimelineSection<CompletedItem>) -> Bool {
        !section.items.isEmpty &&
            (!collapsedSectionIDs.contains(section.id) ||
                collapsingSectionIDs.contains(section.id) ||
                revealingSectionIDs.contains(section.id))
    }

    private func completedSectionBodyOpacity(_ section: TimelineSection<CompletedItem>) -> Double {
        if revealingSectionIDs.contains(section.id) || collapsedSectionIDs.contains(section.id) {
            return 0
        }
        return 1
    }

    private func toggleCompletedSection(_ section: TimelineSection<CompletedItem>) {
        let id = section.id
        if collapsedSectionIDs.contains(id) {
            withAnimation(.easeInOut(duration: 0.24)) {
                collapsedSectionIDs.remove(id)
                collapsingSectionIDs.remove(id)
                revealingSectionIDs.insert(id)
            }
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 90_000_000)
                withAnimation(.easeOut(duration: 0.16)) {
                    revealingSectionIDs.remove(id)
                }
            }
        } else {
            withAnimation(.easeOut(duration: 0.12)) {
                revealingSectionIDs.remove(id)
                collapsingSectionIDs.insert(id)
                collapsedSectionIDs.insert(id)
            }
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 140_000_000)
                withAnimation(.easeInOut(duration: 0.24)) {
                    collapsingSectionIDs.remove(id)
                }
            }
        }
    }

    private func completedTimelineRow(_ item: CompletedItem) -> some View {
        let completedDate = item.completedAt ?? item.due
        let showListIndicator = item.listName?.isEmpty == false
        let showPriorityFlag = item.priority.lowercased() == "high"
        let restorePhase = restorePhases[item.id]
        let isRestoring = restorePhase != nil
        let showsCompletedCheckmark = restorePhase == nil
        let showsStrikethrough = restorePhase == nil || restorePhase == .unchecked
        let isFading = restorePhase == .fading

        return VStack(spacing: 0) {
            HStack(alignment: .center, spacing: 12) {
                Button {
                    restoreCompletedItem(item)
                } label: {
                    Image(systemName: showsCompletedCheckmark ? "checkmark.circle.fill" : "circle")
                        .font(.system(size: TodoTimelineMetrics.minimalRowToggleSize, weight: .regular))
                        .foregroundStyle(showsCompletedCheckmark ? Color.green : colors.onSurfaceVariant.opacity(0.78))
                        .frame(width: TodoTimelineMetrics.minimalRowToggleFrame, height: TodoTimelineMetrics.minimalRowToggleFrame)
                }
                .disabled(isRestoring)
                .buttonStyle(
                    TdayPressButtonStyle(
                        shadowColor: Color.black,
                        pressedShadowOpacity: 0,
                        normalShadowOpacity: 0
                    )
                )

                VStack(alignment: .leading, spacing: 4) {
                    Text(item.title)
                        .font(.tdayRounded(size: TodoTimelineMetrics.minimalRowTitleSize, weight: .bold))
                        .foregroundStyle(showsStrikethrough ? colors.onSurface.opacity(0.78) : colors.onSurface)
                        .strikethrough(showsStrikethrough, color: colors.onSurface.opacity(0.65))
                        .lineLimit(2)
                        .animation(.easeInOut(duration: 0.16), value: showsStrikethrough)

                    Text("Completed, \(completedDate.formatted(date: .omitted, time: .shortened))")
                        .font(.tdayRounded(size: TodoTimelineMetrics.minimalRowSubtitleSize, weight: .semibold))
                        .foregroundStyle(colors.onSurfaceVariant.opacity(0.8))
                }

                Spacer(minLength: 0)

                if showListIndicator || showPriorityFlag {
                    HStack(spacing: 8) {
                        if showListIndicator {
                            Image(systemName: "tray.fill")
                                .font(.system(size: TodoTimelineMetrics.minimalRowIndicatorSize, weight: .semibold))
                                .foregroundStyle(todoListAccentColor(for: item.listColor))
                        }
                        if showPriorityFlag {
                            Image(systemName: "flag.fill")
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
        .animation(.easeInOut(duration: 0.22), value: restorePhase)
        .allowsHitTesting(!isRestoring)
        .transition(.opacity.combined(with: .scale(scale: 0.985)))
        .swipeRevealHintOnTap(enabled: !isRestoring)
        .swipeActions(edge: .leading, allowsFullSwipe: true) {
            Button {
                restoreCompletedItem(item)
            } label: {
                Label("Restore", systemImage: "arrow.uturn.backward")
            }
            .tint(.blue)
        }
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button(role: .destructive) {
                Task { await viewModel.delete(item) }
            } label: {
                Label("Delete", systemImage: "trash")
            }
            Button {
                editingItem = item
            } label: {
                Label("Edit", systemImage: "square.and.pencil")
            }
            .tint(colors.secondary)
        }
    }

    private func restoreCompletedItem(_ item: CompletedItem) {
        guard restorePhases[item.id] == nil else {
            return
        }

        Task { @MainActor in
            withAnimation(.easeInOut(duration: 0.14)) {
                restorePhases[item.id] = .unchecked
            }
            try? await Task.sleep(nanoseconds: 180_000_000)

            withAnimation(.easeInOut(duration: 0.16)) {
                restorePhases[item.id] = .unstruck
            }
            try? await Task.sleep(nanoseconds: 180_000_000)

            withAnimation(.easeInOut(duration: 0.22)) {
                restorePhases[item.id] = .fading
            }
            try? await Task.sleep(nanoseconds: 220_000_000)

            let didRestore = await viewModel.uncomplete(item)
            if !didRestore {
                withAnimation(.easeInOut(duration: 0.18)) {
                    _ = restorePhases.removeValue(forKey: item.id)
                }
            }
        }
    }

    private func pruneRestorePhases() {
        let visibleIDs = Set(viewModel.items.map(\.id))
        restorePhases = restorePhases.filter { visibleIDs.contains($0.key) }
    }
}

private func buildCompletedTimelineSections(items: [CompletedItem]) -> [TimelineSection<CompletedItem>] {
    let calendar = Calendar.current
    let grouped = Dictionary(grouping: items) { item in
        calendar.startOfDay(for: item.completedAt ?? item.due)
    }

    return grouped.keys.sorted(by: >).map { date in
        let sectionItems = (grouped[date] ?? []).sorted { lhs, rhs in
            let lhsCompletedAt = lhs.completedAt ?? lhs.due
            let rhsCompletedAt = rhs.completedAt ?? rhs.due
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
    let formatter = DateFormatter()
    formatter.locale = Locale.current
    formatter.dateFormat = "EEEE, MMM d"
    return formatter.string(from: date)
}
