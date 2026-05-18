import SwiftUI

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

    private var titleCollapseProgress: CGFloat {
        let distance = TodoTimelineMetrics.titleCollapseDistance
        guard distance > 0 else { return 0 }
        return min(max(timelineScrollOffset / distance, 0), 1)
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
                    titleText: "Edit Completed Task",
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
            .disableVerticalScrollBounce()

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

        Section {
            if !isCollapsed {
                ForEach(section.items) { item in
                    completedTimelineRow(item)
                        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                }
            }
        } header: {
            TimelineSectionHeader(
                title: section.title,
                isActiveDropTarget: false,
                isCollapsible: true,
                isCollapsed: isCollapsed,
                onTap: {
                    if isCollapsed {
                        collapsedSectionIDs.remove(section.id)
                    } else {
                        collapsedSectionIDs.insert(section.id)
                    }
                }
            )
                .listRowInsets(EdgeInsets(top: isFirstSection ? 0 : 8, leading: 0, bottom: 0, trailing: 0))
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
        }
    }

    private func completedTimelineRow(_ item: CompletedItem) -> some View {
        let completedDate = item.completedAt ?? item.due
        let showListIndicator = item.listName?.isEmpty == false
        let showPriorityFlag = item.priority.lowercased() == "high"

        return VStack(spacing: 0) {
            HStack(alignment: .center, spacing: 12) {
                Button {
                    Task { await viewModel.uncomplete(item) }
                } label: {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: TodoTimelineMetrics.minimalRowToggleSize, weight: .regular))
                        .foregroundStyle(Color.green)
                        .frame(width: TodoTimelineMetrics.minimalRowToggleFrame, height: TodoTimelineMetrics.minimalRowToggleFrame)
                }
                .buttonStyle(
                    TdayPressButtonStyle(
                        shadowColor: Color.black,
                        pressedShadowOpacity: 0,
                        normalShadowOpacity: 0
                    )
                )

                VStack(alignment: .leading, spacing: 4) {
                    Text(item.title)
                        .font(.system(size: TodoTimelineMetrics.minimalRowTitleSize, weight: .semibold))
                        .foregroundStyle(colors.onSurface)
                        .lineLimit(2)

                    Text("Completed, \(completedDate.formatted(date: .omitted, time: .shortened))")
                        .font(.system(size: TodoTimelineMetrics.minimalRowSubtitleSize, weight: .medium))
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
                }
            }
            .padding(.vertical, TodoTimelineMetrics.minimalRowVerticalPadding)
            .contentShape(Rectangle())

            Rectangle()
                .fill(colors.onSurfaceVariant.opacity(0.18))
                .frame(height: 1)
        }
        .swipeActions(edge: .leading, allowsFullSwipe: true) {
            Button {
                Task { await viewModel.uncomplete(item) }
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
