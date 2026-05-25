import SwiftUI
import UIKit

private enum CompletedRestorePhase {
    case completed
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

    init(container: AppContainer) {
        _viewModel = State(initialValue: CompletedViewModel(container: container))
    }

    private var groupedItems: [TimelineSection<CompletedItem>] {
        buildCompletedTimelineSections(items: viewModel.items)
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
        viewModel.items.map(\.id).joined(separator: "|")
    }

    var body: some View {
        completedTimelineContent
            .background(colors.background)
            .overlay {
                if viewModel.items.isEmpty, !viewModel.isLoading {
                    ZStack {
                        EmptyTaskWatermark(
                            systemName: "checkmark",
                            accentColor: completedAccentColor
                        )
                        EmptyTaskBackgroundMessage(message: "No completed tasks")
                    }
                    .allowsHitTesting(false)
                }
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
    private func completedTimelineSection(
        _ section: TimelineSection<CompletedItem>,
        sectionIndex: Int,
        sections: [TimelineSection<CompletedItem>],
        isFirstSection: Bool
    ) -> some View {
        let isCollapsed = collapsedSectionIDs.contains(section.id)

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
                isCollapsible: true,
                isCollapsed: isCollapsed,
                onTap: {
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
        let currentDate = currentItem.completedAt ?? currentItem.due
        let nextItemInSection = sections[sectionIndex].items.dropFirst(itemIndex + 1).first
        if let nextItemInSection {
            let nextDate = nextItemInSection.completedAt ?? nextItemInSection.due
            return !Calendar.current.isDate(currentDate, inSameDayAs: nextDate)
        }

        let nextVisibleItem = sections.dropFirst(sectionIndex + 1)
            .first { !collapsedSectionIDs.contains($0.id) && !$0.items.isEmpty }?
            .items.first

        guard let nextVisibleItem else {
            return false
        }
        let nextDate = nextVisibleItem.completedAt ?? nextVisibleItem.due
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
            }
        )
    }
}

private struct CompletedTimelineRow: View {
    let item: CompletedItem
    let completedCheckmarkColor: Color
    let onUncomplete: () async -> Void
    let onDelete: () async -> Void
    let onEdit: () -> Void

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
        let completedDate = item.completedAt ?? item.due
        let completedTimeText = completedDate.formatted(date: .omitted, time: .shortened)
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
                    Text(item.title)
                        .font(.tdayRounded(size: TodoTimelineMetrics.minimalRowTitleSize, weight: .bold))
                        .foregroundStyle(titleColor)
                        .strikethrough(showStrikethrough, color: colors.onSurface.opacity(0.65))
                        .lineLimit(2)
                        .animation(.easeInOut(duration: 0.16), value: showStrikethrough)

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
        .animation(.easeInOut(duration: 0.22), value: isFading)
        .transition(.opacity.combined(with: .scale(scale: 0.985)))
        .allowsHitTesting(!isRestoring)
        .todoTrailingSwipeActions(
            enabled: !isRestoring,
            onEdit: onEdit,
            onDelete: {
                Task { await onDelete() }
            }
        )
    }

    private func startRestore() {
        guard restorePhase == .completed else {
            return
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
            withAnimation(.easeInOut(duration: 0.22)) {
                restorePhase = .fading
            }
            try? await Task.sleep(nanoseconds: 220_000_000)
            await onUncomplete()
        }
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
