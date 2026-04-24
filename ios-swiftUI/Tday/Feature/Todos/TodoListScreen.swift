import SwiftUI
import UniformTypeIdentifiers

private enum TodoTimelineMetrics {
    static let horizontalPadding: CGFloat = 18
    static let heroTitleSize: CGFloat = 32
    static let sectionTitleSize: CGFloat = 22
    static let sectionChevronSize: CGFloat = 14
    static let sectionSpacing: CGFloat = 10
    static let minimalRowToggleSize: CGFloat = 24
    static let minimalRowToggleFrame: CGFloat = 38
    static let minimalRowTitleSize: CGFloat = 18
    static let minimalRowSubtitleSize: CGFloat = 13
    static let minimalRowIndicatorSize: CGFloat = 14
    static let minimalRowVerticalPadding: CGFloat = 10
    static let todayCardPadding: CGFloat = 16
    static let todayCardCornerRadius: CGFloat = 20
    static let emptyStateSize: CGFloat = 28
    static let emptyStateOffset: CGFloat = 78
    static let titleCollapseDistance: CGFloat = 64
    static let topBarRowHeight: CGFloat = 44
    static let topBarButtonFrame: CGFloat = 44
    static let topBarButtonIconSize: CGFloat = 20
    static let expandedTitleHeight: CGFloat = 40
    static let titleTravelDistance: CGFloat = 18
    static let titleFadeOutEnd: CGFloat = 0.42
    static let titleFadeInStart: CGFloat = 0.58

    static func smoothstep(_ value: CGFloat) -> CGFloat {
        let clamped = min(max(value, 0), 1)
        return clamped * clamped * (3 - (2 * clamped))
    }
}

private struct TimelineTopBarAction {
    let systemName: String
    let action: () -> Void
}

struct TodoListScreen: View {
    let highlightedTodoId: String?
    @State private var viewModel: TodoListViewModel
    @Environment(\.tdayColors) private var colors
    @Environment(\.dismiss) private var dismiss
    @State private var showingCreateTask = false
    @State private var editingTodo: TodoItem?
    @State private var showingSummary = false
    @State private var showingListSettings = false
    @State private var draggedTodo: TodoItem?
    @State private var activeDropSectionId: String?
    @State private var collapsedSectionIDs: Set<String>
    @State private var timelineScrollOffset: CGFloat = 0

    init(container: AppContainer, mode: TodoListMode, listId: String?, listName: String?, highlightedTodoId: String?) {
        self.highlightedTodoId = highlightedTodoId
        _viewModel = State(initialValue: TodoListViewModel(container: container, mode: mode, listId: listId, listName: listName))
        _collapsedSectionIDs = State(initialValue: mode == .priority || mode == .all ? ["earlier"] : [])
    }

    private var groupedSections: [TodoTimelineSection] {
        buildSections(items: viewModel.items, mode: viewModel.mode)
    }

    private var isTodayMode: Bool {
        viewModel.mode == .today
    }

    private var isMinimalTimelineMode: Bool {
        viewModel.mode == .overdue || viewModel.mode == .scheduled || viewModel.mode == .priority || viewModel.mode == .all
    }

    private var usesHeroTimelineMode: Bool {
        isTodayMode || isMinimalTimelineMode
    }

    private var modeAccentColor: Color {
        todoModeAccentColor(viewModel.mode, listColorKey: viewModel.lists.first(where: { $0.id == viewModel.listId })?.color)
    }

    private var titleCollapseProgress: CGFloat {
        let distance = TodoTimelineMetrics.titleCollapseDistance
        guard distance > 0 else { return 0 }
        return min(max(timelineScrollOffset / distance, 0), 1)
    }

    private var canSummarizeCurrentMode: Bool {
        viewModel.mode != .list && viewModel.mode != .overdue && viewModel.aiSummaryEnabled
    }

    private var heroTopBarAction: TimelineTopBarAction? {
        if canSummarizeCurrentMode {
            return TimelineTopBarAction(
                systemName: "sparkles",
                action: presentSummary
            )
        }
        if viewModel.mode == .list {
            return TimelineTopBarAction(
                systemName: "slider.horizontal.3",
                action: { showingListSettings = true }
            )
        }
        return nil
    }

    private var modeContent: AnyView {
        if isTodayMode {
            return AnyView(todayModeContent)
        }
        if isMinimalTimelineMode {
            return AnyView(minimalTimelineModeContent)
        }
        return AnyView(standardModeContent)
    }

    var body: some View {
        modeContent
        .background(colors.background)
        .navigationBackButtonBehavior()
        .navigationTitleTypography(
            largeTitleColor: modeAccentColor,
            inlineTitleColor: colors.onSurface,
            backgroundColor: colors.background
        )
        .navigationTitle(usesHeroTimelineMode ? "" : viewModel.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(usesHeroTimelineMode ? .hidden : .visible, for: .navigationBar)
        .toolbar {
            navigationToolbarContent
        }
        .safeAreaInset(edge: .top, spacing: 0) {
            timelineTopInset
        }
        .onChange(of: viewModel.items) {
            handleItemsChanged()
        }
        .safeAreaInset(edge: .bottom) {
            floatingActionButtonDock
        }
        .sheet(isPresented: $showingCreateTask) {
            createTaskSheetContent
        }
        .sheet(item: $editingTodo) { todo in
            editTaskSheetContent(for: todo)
        }
        .sheet(isPresented: $showingSummary) {
            summarySheetContent
        }
        .sheet(isPresented: $showingListSettings) {
            listSettingsSheetContent
        }
    }

    @ToolbarContentBuilder
    private var navigationToolbarContent: some ToolbarContent {
        if !usesHeroTimelineMode {
            if viewModel.mode != .list && viewModel.mode != .overdue && viewModel.aiSummaryEnabled {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: presentSummary) {
                        Image(systemName: "sparkles")
                    }
                }
            }
            if viewModel.mode == .list {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showingListSettings = true
                    } label: {
                        Image(systemName: "slider.horizontal.3")
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var timelineTopInset: some View {
        if usesHeroTimelineMode {
            TimelineTopBar(
                title: viewModel.title,
                accentColor: modeAccentColor,
                collapseProgress: titleCollapseProgress,
                onBack: { dismiss() },
                action: heroTopBarAction
            )
        }
    }

    private var timelineHeroTitleRow: some View {
        TimelineExpandedTitleRow(
            title: viewModel.title,
            accentColor: modeAccentColor,
            collapseProgress: titleCollapseProgress
        )
        .background {
            TimelineScrollOffsetObserver { timelineScrollOffset = $0 }
                .frame(width: 0, height: 0)
        }
        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }

    private var floatingActionButtonDock: some View {
        TaskFloatingActionButtonDock {
            showingCreateTask = true
        }
    }

    private var createTaskSheetContent: some View {
        CreateTaskSheet(
            lists: viewModel.lists,
            titleText: "Create Task",
            submitText: "Create",
            initialPayload: CreateTaskPayload(title: "", description: nil, priority: viewModel.mode == .priority ? "High" : "Low", due: Date().addingTimeInterval(60 * 60), rrule: nil, listId: viewModel.listId),
            onParseTaskTitleNlp: { title, dueRef in
                await viewModel.parseTaskTitleNlp(text: title, referenceDueEpochMs: dueRef)
            },
            onDismiss: { showingCreateTask = false },
            onSubmit: { payload in
                await viewModel.addTask(payload)
            }
        )
    }

    private func editTaskSheetContent(for todo: TodoItem) -> some View {
        CreateTaskSheet(
            lists: viewModel.lists,
            titleText: "Edit Task",
            submitText: "Save",
            initialPayload: CreateTaskPayload(title: todo.title, description: todo.description, priority: todo.priority, due: todo.due, rrule: todo.rrule, listId: todo.listId),
            onParseTaskTitleNlp: { title, dueRef in
                await viewModel.parseTaskTitleNlp(text: title, referenceDueEpochMs: dueRef)
            },
            onDismiss: { editingTodo = nil },
            onSubmit: { payload in
                await viewModel.updateTask(todo, payload: payload)
            }
        )
    }

    private var summarySheetContent: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if viewModel.isSummarizing {
                        ProgressView()
                    } else if let summaryText = viewModel.summaryText {
                        Text(summaryText)
                            .font(.body)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    } else if viewModel.summaryConnectivityError {
                        ErrorRetryView(message: "Summary needs a network connection.") {
                            Task { await viewModel.summarizeCurrentMode() }
                        }
                    } else if let summaryError = viewModel.summaryError {
                        Text(summaryError)
                            .foregroundStyle(colors.error)
                    } else {
                        Text("No summary available.")
                    }
                }
                .padding(20)
            }
            .navigationTitle("AI Summary")
            .disableVerticalScrollBounce()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { showingSummary = false }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private var listSettingsSheetContent: some View {
        ListSettingsSheet(list: viewModel.lists.first { $0.id == viewModel.listId }) { name, color, iconKey in
            Task { await viewModel.updateListSettings(name: name, color: color, iconKey: iconKey) }
        }
    }

    private func handleItemsChanged() {
        activeDropSectionId = nil
        draggedTodo = nil
        if viewModel.mode == .all, highlightedTodoId != nil {
            collapsedSectionIDs = []
        }
    }

    private func presentSummary() {
        Task {
            await viewModel.summarizeCurrentMode()
            showingSummary = true
        }
    }

    private var standardModeContent: some View {
        List {
            if let errorMessage = viewModel.errorMessage {
                Section {
                    ErrorRetryView(message: errorMessage) {
                        Task { await viewModel.refresh() }
                    }
                    .listRowBackground(Color.clear)
                }
            }
            ForEach(groupedSections) { section in
                Section {
                    ForEach(section.items) { todo in
                        todoRow(todo, in: section)
                            .listRowBackground(todo.id == highlightedTodoId ? colors.surfaceVariant : colors.surface)
                    }
                    if viewModel.mode == .scheduled, !section.items.isEmpty {
                        Color.clear
                            .frame(height: 8)
                            .listRowInsets(EdgeInsets())
                            .onDrop(
                                of: [UTType.plainText.identifier],
                                delegate: ScheduledTodoDropDelegate(
                                    section: section,
                                    draggedTodo: draggedTodo,
                                    onMove: { todo, targetDate in
                                        activeDropSectionId = nil
                                        draggedTodo = nil
                                        Task { await viewModel.moveTask(todo, toDay: targetDate) }
                                    },
                                    onSectionChange: { sectionId in
                                        activeDropSectionId = sectionId
                                    }
                                )
                            )
                    }
                } header: {
                    Text(section.title)
                        .foregroundStyle(activeDropSectionId == section.id ? colors.primary : colors.onSurfaceVariant)
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .disableVerticalScrollBounce()
    }

    private var todayModeContent: some View {
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

                ForEach(Array(groupedSections.enumerated()), id: \.element.id) { index, section in
                    Section {
                        TimelineSectionHeader(
                            title: section.title,
                            isActiveDropTarget: activeDropSectionId == section.id
                        )
                        .listRowInsets(EdgeInsets(top: index == 0 ? 0 : 8, leading: 0, bottom: 0, trailing: 0))
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)

                        if section.items.isEmpty {
                            Color.clear
                                .frame(height: 42)
                                .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
                                .listRowBackground(Color.clear)
                                .listRowSeparator(.hidden)
                                .allowsHitTesting(false)
                        } else {
                            ForEach(section.items) { todo in
                                todoRow(todo, in: section, useTodayCardStyle: true)
                                    .listRowInsets(EdgeInsets(top: 4, leading: TodoTimelineMetrics.horizontalPadding, bottom: 4, trailing: TodoTimelineMetrics.horizontalPadding))
                                    .listRowBackground(Color.clear)
                                    .listRowSeparator(.hidden)
                            }
                        }
                    }
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

            if viewModel.items.isEmpty {
                TimelineEmptyState(message: "No tasks for today")
                    .allowsHitTesting(false)
            }
        }
    }

    private var minimalTimelineModeContent: some View {
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

                ForEach(Array(groupedSections.enumerated()), id: \.element.id) { index, section in
                    minimalTimelineSection(section, isFirstSection: index == 0)
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

            if viewModel.items.isEmpty {
                TimelineEmptyState(message: emptyTimelineMessage(for: viewModel.mode))
                    .allowsHitTesting(false)
            }
        }
    }

    private func todoRow(
        _ todo: TodoItem,
        in section: TodoTimelineSection,
        useTodayCardStyle: Bool = false,
    ) -> some View {
        let rowContent = VStack(alignment: .leading, spacing: useTodayCardStyle ? 10 : 6) {
            HStack(spacing: 10) {
                Circle()
                    .fill(priorityColor(todo.priority))
                    .frame(width: 10, height: 10)
                Text(todo.title)
                    .font(useTodayCardStyle ? .system(size: 19, weight: .semibold) : .subheadline.weight(.semibold))
                    .foregroundStyle(colors.onSurface)
                Spacer()
                if todo.pinned {
                    Image(systemName: "pin.fill")
                        .foregroundStyle(colors.tertiary)
                }
            }
            HStack(spacing: 6) {
                if useTodayCardStyle {
                    Image(systemName: "clock")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(colors.primary.opacity(0.9))
                }
                Text(todo.due.formatted(date: useTodayCardStyle ? .omitted : .abbreviated, time: .shortened))
                    .font(.caption.weight(useTodayCardStyle ? .semibold : .regular))
                    .foregroundStyle(useTodayCardStyle ? colors.primary.opacity(0.9) : colors.onSurfaceVariant)
            }
            if let description = todo.description, !description.isEmpty {
                Text(description)
                    .font(.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
                    .lineLimit(2)
            }
        }
        .padding(useTodayCardStyle ? TodoTimelineMetrics.todayCardPadding : 0)
        .background(
            Group {
                if useTodayCardStyle {
                    RoundedRectangle(cornerRadius: TodoTimelineMetrics.todayCardCornerRadius, style: .continuous)
                        .fill(todo.id == highlightedTodoId ? colors.surfaceVariant : colors.surface)
                        .overlay(
                            RoundedRectangle(cornerRadius: TodoTimelineMetrics.todayCardCornerRadius, style: .continuous)
                                .stroke(colors.primary.opacity(0.10), lineWidth: 1)
                        )
                        .shadow(color: colors.onSurface.opacity(0.04), radius: 12, x: 0, y: 7)
                }
            }
        )
        .opacity(draggedTodo?.id == todo.id && activeDropSectionId != nil ? 0.55 : 1)
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button(role: .destructive) {
                Task { await viewModel.delete(todo) }
            } label: {
                Label("Delete", systemImage: "trash")
            }
            Button {
                editingTodo = todo
            } label: {
                Label("Edit", systemImage: "square.and.pencil")
            }
            .tint(colors.secondary)
        }
        .swipeActions(edge: .leading, allowsFullSwipe: true) {
            Button {
                Task { await viewModel.complete(todo) }
            } label: {
                Label("Complete", systemImage: "checkmark")
            }
            .tint(.green)
        }

        return rowContent
            .onDrop(
                of: [UTType.plainText.identifier],
                delegate: ScheduledTodoDropDelegate(
                    section: section,
                    draggedTodo: draggedTodo,
                    onMove: { droppedTodo, targetDate in
                        activeDropSectionId = nil
                        draggedTodo = nil
                        Task { await viewModel.moveTask(droppedTodo, toDay: targetDate) }
                    },
                    onSectionChange: { sectionId in
                        activeDropSectionId = sectionId
                    }
                )
            )
            .modifier(
                ScheduledDragModifier(
                    enabled: viewModel.mode == .scheduled,
                    todo: todo,
                    onDragStart: {
                        draggedTodo = todo
                    }
                )
            )
    }

    private func minimalTimelineRow(_ todo: TodoItem, in section: TodoTimelineSection) -> some View {
        let listMeta = todo.listId.flatMap { listId in
            viewModel.lists.first(where: { $0.id == listId })
        }
        let showListIndicator = listMeta != nil && viewModel.mode != .list
        let showPriorityFlag = todo.priority.lowercased() == "high"
        let subtitleText = minimalTimelineSubtitle(for: todo, in: section)
        let isOverdueTask = !todo.completed && todo.due < Date()
        let subtitleColor = isOverdueTask ? colors.error : colors.onSurfaceVariant.opacity(0.8)

        return VStack(spacing: 0) {
            HStack(alignment: .center, spacing: 12) {
                Button {
                    Task { await viewModel.complete(todo) }
                } label: {
                    Image(systemName: todo.completed ? "checkmark.circle.fill" : "circle")
                        .font(.system(size: TodoTimelineMetrics.minimalRowToggleSize, weight: .regular))
                        .foregroundStyle(todo.completed ? Color.green : colors.onSurfaceVariant.opacity(0.78))
                        .frame(width: TodoTimelineMetrics.minimalRowToggleFrame, height: TodoTimelineMetrics.minimalRowToggleFrame)
                }
                .buttonStyle(.plain)

                VStack(alignment: .leading, spacing: 4) {
                    Text(todo.title)
                        .font(.system(size: TodoTimelineMetrics.minimalRowTitleSize, weight: .semibold))
                        .foregroundStyle(colors.onSurface)
                        .lineLimit(2)

                    Text(subtitleText)
                        .font(.system(size: TodoTimelineMetrics.minimalRowSubtitleSize, weight: .medium))
                        .foregroundStyle(subtitleColor)
                }

                Spacer(minLength: 0)

                if showListIndicator || showPriorityFlag {
                    HStack(spacing: 8) {
                        if let listMeta, showListIndicator {
                            Image(systemName: todoListSymbolName(for: listMeta.iconKey))
                                .font(.system(size: TodoTimelineMetrics.minimalRowIndicatorSize, weight: .semibold))
                                .foregroundStyle(todoListAccentColor(for: listMeta.color))
                        }
                        if showPriorityFlag {
                            Image(systemName: "flag.fill")
                                .font(.system(size: TodoTimelineMetrics.minimalRowIndicatorSize, weight: .semibold))
                                .foregroundStyle(priorityColor(todo.priority))
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
        .opacity(draggedTodo?.id == todo.id && activeDropSectionId != nil ? 0.55 : 1)
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button(role: .destructive) {
                Task { await viewModel.delete(todo) }
            } label: {
                Label("Delete", systemImage: "trash")
            }
            Button {
                editingTodo = todo
            } label: {
                Label("Edit", systemImage: "square.and.pencil")
            }
            .tint(colors.secondary)
        }
        .onDrop(
            of: [UTType.plainText.identifier],
            delegate: ScheduledTodoDropDelegate(
                section: section,
                draggedTodo: draggedTodo,
                onMove: { droppedTodo, targetDate in
                    activeDropSectionId = nil
                    draggedTodo = nil
                    Task { await viewModel.moveTask(droppedTodo, toDay: targetDate) }
                },
                onSectionChange: { sectionId in
                    activeDropSectionId = sectionId
                }
            )
        )
        .modifier(
            ScheduledDragModifier(
                enabled: viewModel.mode == .scheduled,
                todo: todo,
                onDragStart: {
                    draggedTodo = todo
                }
            )
        )
    }

    @ViewBuilder
    private func minimalTimelineSection(_ section: TodoTimelineSection, isFirstSection: Bool) -> some View {
        let canCollapseSection = if viewModel.mode == .all {
            true
        } else {
            viewModel.mode == .priority && section.isCollapsible
        }
        let isCollapsed = canCollapseSection && collapsedSectionIDs.contains(section.id)

        Section {
            TimelineSectionHeader(
                title: section.title,
                isActiveDropTarget: activeDropSectionId == section.id,
                isCollapsible: canCollapseSection,
                isCollapsed: isCollapsed,
                onTap: canCollapseSection ? {
                    if isCollapsed {
                        collapsedSectionIDs.remove(section.id)
                    } else {
                        collapsedSectionIDs.insert(section.id)
                    }
                } : nil
            )
            .listRowInsets(EdgeInsets(top: isFirstSection ? 0 : 8, leading: 0, bottom: 0, trailing: 0))
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)

            if !isCollapsed {
                ForEach(section.items) { todo in
                    minimalTimelineRow(todo, in: section)
                        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                }
            }
        }
    }

    private func minimalTimelineSubtitle(for todo: TodoItem, in section: TodoTimelineSection) -> String {
        let timeText = todo.due.formatted(date: .omitted, time: .shortened)
        let dueBodyText = if viewModel.mode == .priority && section.id == "earlier" {
            timelineDateTimeText(todo.due)
        } else {
            timeText
        }

        switch viewModel.mode {
        case .overdue:
            return "Overdue, \(dueBodyText)"
        case .scheduled:
            return "Due \(dueBodyText)"
        case .all:
            if !todo.completed && todo.due < Date() {
                return "Overdue, \(dueBodyText)"
            }
            return "Due \(dueBodyText)"
        case .priority:
            if !todo.completed && todo.due < Date() {
                return "Overdue, \(dueBodyText)"
            }
            return "Due \(dueBodyText)"
        default:
            return dueBodyText
        }
    }
}

private struct TimelineTopBar: View {
    let title: String
    let accentColor: Color
    let collapseProgress: CGFloat
    let onBack: () -> Void
    let action: TimelineTopBarAction?

    @Environment(\.tdayColors) private var colors

    private var progress: CGFloat {
        min(max(collapseProgress, 0), 1)
    }

    private var titleOpacity: CGFloat {
        let fadeProgress = (progress - TodoTimelineMetrics.titleFadeInStart) / (1 - TodoTimelineMetrics.titleFadeInStart)
        return TodoTimelineMetrics.smoothstep(fadeProgress)
    }

    private var titleOffsetY: CGFloat {
        TodoTimelineMetrics.titleTravelDistance * (1 - progress)
    }

    private var titleContent: some View {
        Text(title)
            .font(.system(size: TodoTimelineMetrics.heroTitleSize, weight: .heavy, design: .rounded))
            .tracking(-0.9)
            .foregroundStyle(accentColor)
            .lineLimit(1)
    }

    var body: some View {
        ZStack {
            HStack(spacing: 0) {
                TimelineTopBarButton(systemName: "chevron.left", chrome: .filled, action: onBack)
                Spacer(minLength: 0)
                if let action {
                    TimelineTopBarButton(systemName: action.systemName, chrome: .plain, action: action.action)
                } else {
                    Color.clear
                        .frame(width: TodoTimelineMetrics.topBarButtonFrame, height: TodoTimelineMetrics.topBarButtonFrame)
                }
            }

            titleContent
                .opacity(titleOpacity)
                .offset(y: titleOffsetY)
                .padding(.horizontal, TodoTimelineMetrics.topBarButtonFrame + 12)
                .frame(maxWidth: .infinity)
                .allowsHitTesting(false)
        }
        .frame(height: TodoTimelineMetrics.topBarRowHeight)
        .padding(.horizontal, TodoTimelineMetrics.horizontalPadding)
        .padding(.top, 2)
        .padding(.bottom, 4)
        .background(colors.background)
    }
}

private struct TimelineExpandedTitleRow: View {
    let title: String
    let accentColor: Color
    let collapseProgress: CGFloat

    private var progress: CGFloat {
        min(max(collapseProgress, 0), 1)
    }

    private var titleOpacity: CGFloat {
        let fadeProgress = progress / TodoTimelineMetrics.titleFadeOutEnd
        return 1 - TodoTimelineMetrics.smoothstep(fadeProgress)
    }

    private var titleOffsetY: CGFloat {
        -TodoTimelineMetrics.titleTravelDistance * progress
    }

    private var rowHeight: CGFloat {
        TodoTimelineMetrics.expandedTitleHeight * titleOpacity
    }

    var body: some View {
        Text(title)
            .font(.system(size: TodoTimelineMetrics.heroTitleSize, weight: .heavy, design: .rounded))
            .tracking(-0.9)
            .foregroundStyle(accentColor)
            .lineLimit(1)
            .frame(maxWidth: .infinity, minHeight: rowHeight, maxHeight: rowHeight, alignment: .topLeading)
            .opacity(titleOpacity)
            .offset(y: titleOffsetY)
            .clipped()
            .allowsHitTesting(false)
    }
}

private struct TimelineTopBarButton: View {
    enum Chrome {
        case plain
        case filled
    }

    let systemName: String
    let chrome: Chrome
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: TodoTimelineMetrics.topBarButtonIconSize, weight: .semibold))
                .frame(width: TodoTimelineMetrics.topBarButtonFrame, height: TodoTimelineMetrics.topBarButtonFrame)
                .background {
                    if chrome == .filled {
                        Circle()
                            .fill(colors.surface)
                            .shadow(color: Color.black.opacity(0.08), radius: 10, x: 0, y: 4)
                    }
                }
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .foregroundStyle(chrome == .filled ? colors.onSurface : Color.accentColor)
    }
}

private struct TimelineScrollOffsetTrackingRow: View {
    let onChange: (CGFloat) -> Void

    var body: some View {
        TimelineScrollOffsetObserver(onChange: onChange)
            .frame(height: 0)
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
            .allowsHitTesting(false)
    }
}

private struct TimelineScrollOffsetObserver: UIViewRepresentable {
    let onChange: (CGFloat) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onChange: onChange)
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.onChange = onChange
        DispatchQueue.main.async {
            context.coordinator.attach(to: uiView)
        }
    }

    final class Coordinator {
        var onChange: (CGFloat) -> Void
        private weak var observedScrollView: UIScrollView?
        private var observation: NSKeyValueObservation?

        init(onChange: @escaping (CGFloat) -> Void) {
            self.onChange = onChange
        }

        func attach(to view: UIView) {
            guard let scrollView = view.enclosingScrollView() else {
                return
            }

            guard observedScrollView !== scrollView else {
                return
            }

            observedScrollView = scrollView
            observation = scrollView.observe(\.contentOffset, options: [.initial, .new]) { [weak self] scrollView, _ in
                let offset = max(scrollView.contentOffset.y + scrollView.adjustedContentInset.top, 0)
                self?.onChange(offset)
            }
        }
    }
}

private extension UIView {
    func enclosingScrollView() -> UIScrollView? {
        var current: UIView? = self
        while let view = current {
            if let scrollView = view as? UIScrollView {
                return scrollView
            }
            current = view.superview
        }
        return nil
    }
}

private struct TimelineSectionHeader: View {
    let title: String
    let isActiveDropTarget: Bool
    let isCollapsible: Bool
    let isCollapsed: Bool
    let onTap: (() -> Void)?

    @Environment(\.tdayColors) private var colors

    init(
        title: String,
        isActiveDropTarget: Bool,
        isCollapsible: Bool = false,
        isCollapsed: Bool = false,
        onTap: (() -> Void)? = nil
    ) {
        self.title = title
        self.isActiveDropTarget = isActiveDropTarget
        self.isCollapsible = isCollapsible
        self.isCollapsed = isCollapsed
        self.onTap = onTap
    }

    var body: some View {
        let content = VStack(alignment: .leading, spacing: TodoTimelineMetrics.sectionSpacing) {
            HStack(spacing: 8) {
                Text(title)
                    .font(.system(size: TodoTimelineMetrics.sectionTitleSize, weight: .bold, design: .rounded))
                    .foregroundStyle(isActiveDropTarget ? colors.primary : colors.onSurfaceVariant.opacity(0.78))
                    .textCase(nil)

                if isCollapsible {
                    Image(systemName: "chevron.down")
                        .font(.system(size: TodoTimelineMetrics.sectionChevronSize, weight: .semibold))
                        .foregroundStyle(colors.onSurfaceVariant.opacity(0.72))
                        .rotationEffect(.degrees(isCollapsed ? -90 : 0))
                        .animation(.easeInOut(duration: 0.18), value: isCollapsed)
                }
            }
        }
        .padding(.top, 2)
        .padding(.horizontal, TodoTimelineMetrics.horizontalPadding)
        .padding(.bottom, 4)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colors.background)

        if let onTap {
            Button(action: onTap) {
                content
            }
            .buttonStyle(.plain)
        } else {
            content
        }
    }
}

private struct TimelineEmptyState: View {
    let message: String

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Text(message)
            .font(.system(size: TodoTimelineMetrics.emptyStateSize, weight: .semibold, design: .rounded))
            .foregroundStyle(colors.onSurfaceVariant.opacity(0.54))
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
            .offset(y: TodoTimelineMetrics.emptyStateOffset)
            .padding(.horizontal, 32)
    }
}

private struct ListSettingsSheet: View {
    let list: ListSummary?
    let onSubmit: (String, String?, String?) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var color = "BLUE"
    @State private var iconKey = "inbox"

    private let colors = ["BLUE", "GREEN", "ORANGE", "PINK", "PURPLE", "GRAY"]
    private let icons = ["inbox", "briefcase", "calendar", "list.bullet", "star", "heart"]

    var body: some View {
        NavigationStack {
            Form {
                TextField("Name", text: $name)
                Picker("Color", selection: $color) {
                    ForEach(colors, id: \.self) { value in
                        Text(value.capitalized).tag(value)
                    }
                }
                Picker("Icon", selection: $iconKey) {
                    ForEach(icons, id: \.self) { value in
                        Label(value.replacingOccurrences(of: ".", with: " "), systemImage: value).tag(value)
                    }
                }
            }
            .disableVerticalScrollBounce()
            .navigationTitle("List Settings")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        onSubmit(name, color, iconKey)
                        dismiss()
                    }
                }
            }
            .task {
                name = list?.name ?? ""
                color = list?.color ?? "BLUE"
                iconKey = list?.iconKey ?? "inbox"
            }
        }
    }
}

private struct TodoTimelineSection: Identifiable, Hashable {
    let id: String
    let title: String
    let items: [TodoItem]
    let isCollapsible: Bool
    let targetDate: Date?
}

private struct ScheduledDragModifier: ViewModifier {
    let enabled: Bool
    let todo: TodoItem
    let onDragStart: () -> Void

    @ViewBuilder
    func body(content: Content) -> some View {
        if enabled {
            content.onDrag {
                onDragStart()
                return NSItemProvider(object: todo.id as NSString)
            }
        } else {
            content
        }
    }
}

private struct ScheduledTodoDropDelegate: DropDelegate {
    let section: TodoTimelineSection
    let draggedTodo: TodoItem?
    let onMove: (TodoItem, Date) -> Void
    let onSectionChange: (String?) -> Void

    func validateDrop(info: DropInfo) -> Bool {
        draggedTodo != nil && section.targetDate != nil
    }

    func dropEntered(info: DropInfo) {
        onSectionChange(section.id)
    }

    func dropExited(info: DropInfo) {
        onSectionChange(nil)
    }

    func dropUpdated(info: DropInfo) -> DropProposal? {
        DropProposal(operation: .move)
    }

    func performDrop(info: DropInfo) -> Bool {
        defer {
            onSectionChange(nil)
        }
        guard let todo = draggedTodo, let targetDate = section.targetDate else {
            return false
        }
        onMove(todo, targetDate)
        return true
    }
}

private func buildSections(items: [TodoItem], mode: TodoListMode) -> [TodoTimelineSection] {
    let calendar = Calendar.current
    switch mode {
    case .today:
        let grouped = Dictionary(grouping: items) { item -> String in
            let hour = calendar.component(.hour, from: item.due)
            if hour < 12 { return "Morning" }
            if hour < 18 { return "Afternoon" }
            return "Tonight"
        }
        return ["Morning", "Afternoon", "Tonight"].map { key in
            return TodoTimelineSection(
                id: key,
                title: key,
                items: grouped[key, default: []].sorted(by: { $0.due < $1.due }),
                isCollapsible: false,
                targetDate: nil
            )
        }
    case .overdue:
        let now = Date()
        let startOfToday = calendar.startOfDay(for: now)
        let overdueItems = items.filter { $0.due < now }
        let grouped = Dictionary(grouping: overdueItems) { item in
            calendar.startOfDay(for: item.due)
        }

        var sections: [TodoTimelineSection] = []
        if let todaysItems = grouped[startOfToday], !todaysItems.isEmpty {
            sections.append(
                TodoTimelineSection(
                    id: "today",
                    title: "Today",
                    items: todaysItems.sorted(by: { $0.due < $1.due }),
                    isCollapsible: false,
                    targetDate: nil
                )
            )
        }

        let pastDates = grouped.keys
            .filter { $0 < startOfToday }
            .sorted(by: >)

        sections.append(
            contentsOf: pastDates.map { date in
                TodoTimelineSection(
                    id: "overdue-\(date.timeIntervalSince1970)",
                    title: date.formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day()),
                    items: grouped[date]?.sorted(by: { $0.due < $1.due }) ?? [],
                    isCollapsible: false,
                    targetDate: nil
                )
            }
        )

        return sections
    case .scheduled:
        let startOfToday = calendar.startOfDay(for: Date())
        let grouped = Dictionary(grouping: items.filter { $0.due >= startOfToday }) { item in
            calendar.startOfDay(for: item.due)
        }
        return grouped.keys.sorted().map { date in
            TodoTimelineSection(
                id: "scheduled-\(date.timeIntervalSince1970)",
                title: scheduledSectionTitle(for: date, calendar: calendar),
                items: grouped[date]?.sorted(by: { $0.due < $1.due }) ?? [],
                isCollapsible: false,
                targetDate: date
            )
        }
    case .all, .priority:
        return buildFutureTimelineSections(items: items, calendar: calendar)
    case .list:
        let grouped = Dictionary(grouping: items) { item -> String in
            if item.due < calendar.startOfDay(for: Date()) {
                return "Earlier"
            }
            return item.due.formatted(.dateTime.weekday(.wide).month(.abbreviated).day())
        }
        return grouped.keys.sorted().map { key in
            TodoTimelineSection(
                id: key,
                title: key,
                items: grouped[key]?.sorted(by: { $0.due < $1.due }) ?? [],
                isCollapsible: key == "Earlier",
                targetDate: nil
            )
        }
    }
}

private func scheduledSectionTitle(for date: Date, calendar: Calendar) -> String {
    if calendar.isDateInToday(date) {
        return "Today"
    }
    if calendar.isDateInTomorrow(date) {
        return "Tomorrow"
    }
    return timelineDayTitle(for: date)
}

private func buildFutureTimelineSections(items: [TodoItem], calendar: Calendar) -> [TodoTimelineSection] {
    let now = Date()
    let today = calendar.startOfDay(for: now)
    let groupedByDate = Dictionary(grouping: items.sorted(by: { $0.due < $1.due })) { item in
        calendar.startOfDay(for: item.due)
    }
    let currentYear = calendar.component(.year, from: today)
    let currentMonth = calendar.component(.month, from: today)
    let currentMonthIndex = monthIndex(for: today, calendar: calendar)
    let horizonStart = calendar.date(byAdding: .day, value: 7, to: today) ?? today

    func daySection(for date: Date, title: String) -> TodoTimelineSection {
        TodoTimelineSection(
            id: "priority-\(date.timeIntervalSince1970)",
            title: title,
            items: groupedByDate[date] ?? [],
            isCollapsible: false,
            targetDate: nil
        )
    }

    var sections: [TodoTimelineSection] = []

    let earlierItems = groupedByDate.keys
        .filter { $0 < today }
        .sorted()
        .flatMap { groupedByDate[$0] ?? [] }

    if !earlierItems.isEmpty {
        sections.append(
            TodoTimelineSection(
                id: "earlier",
                title: "Earlier",
                items: earlierItems,
                isCollapsible: true,
                targetDate: nil
            )
        )
    }

    sections.append(daySection(for: today, title: "Today"))

    if let tomorrow = calendar.date(byAdding: .day, value: 1, to: today) {
        sections.append(daySection(for: tomorrow, title: "Tomorrow"))
    }

    for offset in 2...6 {
        guard let date = calendar.date(byAdding: .day, value: offset, to: today) else { continue }
        sections.append(daySection(for: date, title: timelineDayTitle(for: date)))
    }

    let restOfCurrentMonthItems = groupedByDate.keys
        .filter { $0 >= horizonStart && monthIndex(for: $0, calendar: calendar) == currentMonthIndex }
        .sorted()
        .flatMap { groupedByDate[$0] ?? [] }

    if let currentMonthStart = calendar.date(from: DateComponents(year: currentYear, month: currentMonth, day: 1)) {
        sections.append(
            TodoTimelineSection(
                id: "rest-\(currentMonthIndex)",
                title: "Rest of \(monthTitle(for: currentMonthStart, currentYear: currentYear, calendar: calendar))",
                items: restOfCurrentMonthItems,
                isCollapsible: false,
                targetDate: nil
            )
        )
    }

    let futureMonthIndexes = Set(
        groupedByDate.keys
            .filter { $0 >= horizonStart }
            .map { monthIndex(for: $0, calendar: calendar) }
    )
    let minimumFinalMonthIndex = currentYear * 12 + 12
    let finalMonthIndex = max(minimumFinalMonthIndex, futureMonthIndexes.max() ?? minimumFinalMonthIndex)

    var targetYear = currentYear
    var targetMonth = currentMonth + 1

    while (targetYear * 12 + targetMonth) <= finalMonthIndex {
        guard let monthStart = calendar.date(from: DateComponents(year: targetYear, month: targetMonth, day: 1)) else {
            break
        }

        let targetMonthIndex = monthIndex(for: monthStart, calendar: calendar)
        let monthItems = groupedByDate.keys
            .filter { $0 >= horizonStart && monthIndex(for: $0, calendar: calendar) == targetMonthIndex }
            .sorted()
            .flatMap { groupedByDate[$0] ?? [] }

        sections.append(
            TodoTimelineSection(
                id: "month-\(targetMonthIndex)",
                title: monthTitle(for: monthStart, currentYear: currentYear, calendar: calendar),
                items: monthItems,
                isCollapsible: false,
                targetDate: nil
            )
        )

        if targetMonth == 12 {
            targetYear += 1
            targetMonth = 1
        } else {
            targetMonth += 1
        }
    }

    return sections
}

private func timelineDayTitle(for date: Date) -> String {
    let formatter = DateFormatter()
    formatter.locale = Locale.current
    formatter.dateFormat = "EEE MMM d"
    return formatter.string(from: date)
}

private func timelineDateTimeText(_ date: Date) -> String {
    let formatter = DateFormatter()
    formatter.locale = Locale.current
    formatter.dateFormat = "MMM d, h:mm a"
    return formatter.string(from: date)
}

private func monthTitle(for date: Date, currentYear: Int, calendar: Calendar) -> String {
    let formatter = DateFormatter()
    formatter.locale = Locale.current
    formatter.dateFormat = calendar.component(.year, from: date) == currentYear ? "LLLL" : "LLLL yyyy"
    return formatter.string(from: date)
}

private func monthIndex(for date: Date, calendar: Calendar) -> Int {
    let year = calendar.component(.year, from: date)
    let month = calendar.component(.month, from: date)
    return year * 12 + month
}

private func priorityColor(_ priority: String) -> Color {
    switch priority.lowercased() {
    case "high":
        return .red
    case "medium":
        return .orange
    default:
        return .blue
    }
}

private func emptyTimelineMessage(for mode: TodoListMode) -> String {
    switch mode {
    case .today:
        return "No tasks for today"
    case .overdue:
        return "No overdue tasks"
    case .scheduled:
        return "No scheduled tasks"
    case .all:
        return "No tasks yet"
    case .priority:
        return "No priority tasks"
    case .list:
        return "No tasks in this list"
    }
}

private func todoModeAccentColor(_ mode: TodoListMode, listColorKey: String?) -> Color {
    switch mode {
    case .today:
        return todoHexColor(0x5C9FE7)
    case .overdue:
        return todoHexColor(0xDA7661)
    case .scheduled:
        return todoHexColor(0xF29F38)
    case .all:
        return todoHexColor(0x5E6878)
    case .priority:
        return todoHexColor(0xE65E52)
    case .list:
        return todoListAccentColor(for: listColorKey)
    }
}

private func todoListAccentColor(for key: String?) -> Color {
    switch key {
    case "RED":
        return todoHexColor(0xE65E52)
    case "ORANGE":
        return todoHexColor(0xF29F38)
    case "YELLOW":
        return todoHexColor(0xF3D04A)
    case "LIME":
        return todoHexColor(0x8ACF56)
    case "BLUE":
        return todoHexColor(0x5C9FE7)
    case "PURPLE":
        return todoHexColor(0x8D6CE2)
    case "PINK":
        return todoHexColor(0xDF6DAA)
    case "TEAL":
        return todoHexColor(0x4EB5B0)
    case "CORAL":
        return todoHexColor(0xE3876D)
    case "GOLD":
        return todoHexColor(0xCFAB57)
    case "DEEP_BLUE":
        return todoHexColor(0x4B73D6)
    case "ROSE":
        return todoHexColor(0xD9799A)
    case "LIGHT_RED":
        return todoHexColor(0xE48888)
    case "BRICK":
        return todoHexColor(0xB86A5C)
    case "SLATE":
        return todoHexColor(0x7B8593)
    default:
        return todoHexColor(0x5C9FE7)
    }
}

private func todoListSymbolName(for key: String?) -> String {
    switch key {
    case "sun":
        return "sun.max.fill"
    case "calendar":
        return "calendar"
    case "schedule":
        return "clock"
    case "flag":
        return "flag.fill"
    case "check":
        return "checkmark"
    case "smile":
        return "face.smiling"
    case "list":
        return "list.bullet"
    case "bookmark":
        return "bookmark.fill"
    case "key":
        return "key.fill"
    case "gift":
        return "gift.fill"
    case "cake":
        return "birthday.cake.fill"
    case "school":
        return "graduationcap.fill"
    case "bag":
        return "backpack.fill"
    case "edit":
        return "pencil"
    case "document":
        return "doc.text.fill"
    case "book":
        return "book.closed.fill"
    case "work":
        return "briefcase.fill"
    case "wallet":
        return "wallet.pass.fill"
    case "money":
        return "dollarsign.circle.fill"
    case "fitness":
        return "dumbbell.fill"
    case "run":
        return "figure.run"
    case "food":
        return "fork.knife"
    case "drink":
        return "wineglass.fill"
    case "health":
        return "cross.case.fill"
    case "monitor":
        return "display"
    case "music":
        return "music.note"
    case "computer":
        return "desktopcomputer"
    case "game":
        return "gamecontroller.fill"
    case "headphones":
        return "headphones"
    case "eco":
        return "leaf.fill"
    case "pets":
        return "pawprint.fill"
    case "child":
        return "figure.2.and.child.holdinghands"
    case "family":
        return "person.3.fill"
    case "basket":
        return "basket.fill"
    case "cart":
        return "cart.fill"
    case "mall":
        return "bag.fill"
    case "inventory":
        return "archivebox.fill"
    case "soccer":
        return "soccerball"
    case "baseball":
        return "baseball.fill"
    case "basketball":
        return "basketball.fill"
    case "football":
        return "football.fill"
    case "tennis":
        return "tennis.racket"
    case "train":
        return "tram.fill"
    case "flight":
        return "airplane"
    case "boat":
        return "ferry.fill"
    case "car":
        return "car.fill"
    case "umbrella":
        return "umbrella.fill"
    case "drop":
        return "drop.fill"
    case "snow":
        return "snowflake"
    case "fire":
        return "flame.fill"
    case "tools":
        return "hammer.fill"
    case "scissors":
        return "scissors"
    case "architecture", "bank":
        return "building.columns.fill"
    case "code":
        return "chevron.left.forwardslash.chevron.right"
    case "idea":
        return "lightbulb.fill"
    case "chat":
        return "bubble.left.fill"
    case "alert":
        return "exclamationmark.triangle.fill"
    case "star":
        return "star.fill"
    case "heart":
        return "heart.fill"
    case "circle":
        return "circle.fill"
    case "square":
        return "square.fill"
    case "triangle":
        return "triangle.fill"
    case "home":
        return "house.fill"
    case "city":
        return "building.2.fill"
    case "camera":
        return "camera.fill"
    case "palette":
        return "paintpalette.fill"
    default:
        return "tray.fill"
    }
}

private func todoHexColor(_ hex: UInt) -> Color {
    Color(
        .sRGB,
        red: Double((hex >> 16) & 0xFF) / 255,
        green: Double((hex >> 8) & 0xFF) / 255,
        blue: Double(hex & 0xFF) / 255,
        opacity: 1
    )
}
