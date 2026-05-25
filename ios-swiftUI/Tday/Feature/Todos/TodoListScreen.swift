import SwiftUI
import UIKit
import UniformTypeIdentifiers

private let todoDragContentTypes = [UTType.plainText.identifier, UTType.text.identifier]
private let todoTimelineDragCoordinateSpace = "todoTimelineDragCoordinateSpace"

private final class TodoTaskDragSession {
    static let shared = TodoTaskDragSession()
    var todo: TodoItem?
    var handledDropSignature: String?

    private init() {}
}

private struct TodoInAppDrag: Equatable {
    let todo: TodoItem
    var location: CGPoint
}

private struct TodoDropTargetFrame: Equatable {
    let sectionID: String
    let frame: CGRect
}

private struct TodoDropTargetFramePreferenceKey: PreferenceKey {
    static var defaultValue: [String: TodoDropTargetFrame] = [:]

    static func reduce(value: inout [String: TodoDropTargetFrame], nextValue: () -> [String: TodoDropTargetFrame]) {
        value.merge(nextValue(), uniquingKeysWith: { _, new in new })
    }
}

enum TodoTimelineMetrics {
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
    static let minimalRowTrailingIndicatorPadding: CGFloat = 24
    static let minimalRowVerticalPadding: CGFloat = 10
    static let titleCollapseDistance: CGFloat = 64
    static let topBarRowHeight: CGFloat = 56
    static let topBarButtonFrame: CGFloat = 56
    static let topBarButtonIconSize: CGFloat = 24
    static let expandedTitleHeight: CGFloat = 56
    static let expandedTitleLiftDistance: CGFloat = 14
    static let expandedTitleFadeStart: CGFloat = 0.08
    static let expandedTitleFadeEnd: CGFloat = 0.44
    static let collapsedTitleRevealDistance: CGFloat = 10
    static let collapsedTitleRevealStart: CGFloat = 0.68
    static let collapsedTitleRevealEnd: CGFloat = 1
    static let searchResultSectionExpandDelay: TimeInterval = 0.08
    static let searchResultScrollDelay: TimeInterval = 0.44
    static let searchResultScrollDuration: TimeInterval = 0.90
    static let searchResultFlashDelay: TimeInterval = 0.62
    static let searchResultPreScrollItemCount = 5

    static func smoothstep(_ value: CGFloat) -> CGFloat {
        let clamped = min(max(value, 0), 1)
        return clamped * clamped * (3 - (2 * clamped))
    }

    static func progress(_ value: CGFloat, from start: CGFloat, to end: CGFloat) -> CGFloat {
        guard end > start else { return value >= end ? 1 : 0 }
        return smoothstep((value - start) / (end - start))
    }
}

struct TimelinePinnedSectionHeaderBackground: ViewModifier {
    @Environment(\.tdayColors) private var colors

    func body(content: Content) -> some View {
        content
            .background(colors.background)
            .listRowBackground(colors.background)
            .zIndex(1)
    }
}

extension View {
    func timelinePinnedSectionHeaderBackground() -> some View {
        modifier(TimelinePinnedSectionHeaderBackground())
    }
}

struct TimelineRowDivider: View {
    @Environment(\.tdayColors) private var colors

    var body: some View {
        Rectangle()
            .fill(colors.onSurfaceVariant.opacity(0.18))
            .frame(height: 1)
            .padding(.horizontal, TodoTimelineMetrics.horizontalPadding)
            .listRowInsets(EdgeInsets())
            .listRowBackground(colors.background)
            .listRowSeparator(.hidden)
            .environment(\.defaultMinListRowHeight, 1)
            .allowsHitTesting(false)
    }
}

private struct TimelineTaskFlashHighlight: ViewModifier {
    let active: Bool

    @Environment(\.tdayColors) private var colors
    @State private var strength: CGFloat = 0

    func body(content: Content) -> some View {
        content
            .background(alignment: .leading) {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [
                                colors.primary.opacity(0.42 * strength),
                                colors.primary.opacity(0.28 * strength),
                                .clear
                            ],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
            }
            .onChange(of: active, initial: true) { _, isActive in
                guard isActive else { return }
                pulse()
            }
    }

    private func pulse() {
        Task { @MainActor in
            strength = 0
            for pulseIndex in 0..<2 {
                withAnimation(.easeInOut(duration: 0.42)) {
                    strength = 0.46
                }
                try? await Task.sleep(nanoseconds: 420_000_000)
                withAnimation(.easeInOut(duration: 0.62)) {
                    strength = 0
                }
                try? await Task.sleep(nanoseconds: pulseIndex == 0 ? 770_000_000 : 620_000_000)
            }
        }
    }
}

struct TimelineTopBarAction {
    let systemName: String
    let tint: Color?
    let usesCircularChrome: Bool
    let action: () -> Void

    init(
        systemName: String,
        tint: Color? = nil,
        usesCircularChrome: Bool = false,
        action: @escaping () -> Void
    ) {
        self.systemName = systemName
        self.tint = tint
        self.usesCircularChrome = usesCircularChrome
        self.action = action
    }
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
    @State private var inAppDrag: TodoInAppDrag?
    @State private var activeDropSectionId: String?
    @State private var dropTargetFrames: [String: TodoDropTargetFrame] = [:]
    @State private var pendingRescheduleDrop: TodoRescheduleDrop?
    @State private var collapsedSectionIDs: Set<String>
    @State private var timelineScrollOffset: CGFloat = 0
    @State private var completingTodoIDs: Set<String> = []
    @State private var flashTodoId: String?
    @State private var highlightedScrollRequestID = 0

    init(container: AppContainer, mode: TodoListMode, listId: String?, listName: String?, highlightedTodoId: String?) {
        self.highlightedTodoId = highlightedTodoId
        _viewModel = State(initialValue: TodoListViewModel(container: container, mode: mode, listId: listId, listName: listName))
        _collapsedSectionIDs = State(initialValue: mode == .priority || mode == .all ? ["earlier"] : [])
    }

    private var groupedSections: [TodoTimelineSection] {
        buildSections(
            items: viewModel.items,
            mode: viewModel.mode,
            includeEmptyEarlierTarget: viewModel.mode.supportsTaskReschedule && (draggedTodo != nil || inAppDrag != nil)
        )
    }

    private var isTodayMode: Bool {
        viewModel.mode == .today
    }

    private var isMinimalTimelineMode: Bool {
        viewModel.mode == .overdue ||
            viewModel.mode == .scheduled ||
            viewModel.mode == .priority ||
            viewModel.mode == .all ||
            viewModel.mode == .list
    }

    private var usesHeroTimelineMode: Bool {
        isTodayMode || isMinimalTimelineMode
    }

    private var modeAccentColor: Color {
        todoModeAccentColor(viewModel.mode, listColorKey: viewModel.lists.first(where: { $0.id == viewModel.listId })?.color)
    }

    private var emptyWatermarkSystemName: String {
        emptyTimelineSystemImage(
            for: viewModel.mode,
            listIconKey: viewModel.lists.first(where: { $0.id == viewModel.listId })?.iconKey
        )
    }

    private var titleCollapseProgress: CGFloat {
        let distance = TodoTimelineMetrics.titleCollapseDistance
        guard distance > 0 else { return 0 }
        return min(max(timelineScrollOffset / distance, 0), 1)
    }

    private var timelineItemAnimationKey: String {
        let itemIDs = viewModel.items.map(\.id).joined(separator: "|")
        let completingIDs = completingTodoIDs.sorted().joined(separator: "|")
        return "\(itemIDs)::\(completingIDs)"
    }

    private var canSummarizeCurrentMode: Bool {
        viewModel.mode != .list && viewModel.mode != .overdue && viewModel.aiSummaryEnabled
    }

    private var heroTopBarAction: TimelineTopBarAction? {
        if canSummarizeCurrentMode {
            return TimelineTopBarAction(
                systemName: "sparkles",
                usesCircularChrome: true,
                action: presentSummary
            )
        }
        if viewModel.mode == .list {
            return TimelineTopBarAction(
                systemName: "ellipsis",
                usesCircularChrome: true,
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
        .coordinateSpace(name: todoTimelineDragCoordinateSpace)
        .background(colors.background)
        .onPreferenceChange(TodoDropTargetFramePreferenceKey.self) { frames in
            dropTargetFrames = frames
        }
        .overlay {
            if viewModel.items.isEmpty, !viewModel.isLoading {
                ZStack {
                    EmptyTaskWatermark(
                        systemName: emptyWatermarkSystemName,
                        accentColor: modeAccentColor
                    )
                    EmptyTaskBackgroundMessage(
                        message: emptyTimelineMessage(for: viewModel.mode)
                    )
                }
                .allowsHitTesting(false)
            }
        }
        .overlay(alignment: .topLeading) {
            GeometryReader { proxy in
                if let inAppDrag {
                    let rootFrame = proxy.frame(in: .global)
                    let previewLocation = CGPoint(
                        x: inAppDrag.location.x - rootFrame.minX,
                        y: inAppDrag.location.y - rootFrame.minY
                    )
                    TodoDragPreview(todo: inAppDrag.todo)
                        .position(x: previewLocation.x, y: previewLocation.y)
                        .zIndex(20)
                        .allowsHitTesting(false)
                }
            }
            .allowsHitTesting(false)
        }
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
        .confirmationDialog(
            "Move repeating task?",
            isPresented: Binding(
                get: { pendingRescheduleDrop != nil },
                set: { isPresented in
                    if !isPresented {
                        pendingRescheduleDrop = nil
                    }
                }
            ),
            titleVisibility: .visible
        ) {
            Button("This occurrence") {
                commitPendingReschedule(scope: .occurrence)
            }
            Button("Entire series") {
                commitPendingReschedule(scope: .series)
            }
            Button("Cancel", role: .cancel) {
                pendingRescheduleDrop = nil
            }
        } message: {
            Text("Choose whether to move only this task occurrence or the entire repeating series.")
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
        .onVerticalScrollSnap(collapseDistance: TodoTimelineMetrics.titleCollapseDistance)
        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
        .listRowBackground(colors.background)
        .listRowSeparator(.hidden)
    }

    private var floatingActionButtonDock: some View {
        TaskFloatingActionButtonDock(fillColor: modeAccentColor) {
            showingCreateTask = true
        }
    }

    private var createTaskSheetContent: some View {
        CreateTaskSheet(
            lists: viewModel.lists,
            titleText: "New task",
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
            titleText: "Edit task",
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
                            .font(.tdayRounded(.body, weight: .bold))
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
            .background(colors.bottomSheetBackground)
            .navigationTitle("AI Summary")
            .disableVerticalScrollBounce()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { showingSummary = false }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationBackground {
            colors.bottomSheetBackground
                .ignoresSafeArea(.container, edges: .bottom)
        }
    }

    private var listSettingsSheetContent: some View {
        ListSettingsSheet(list: viewModel.lists.first { $0.id == viewModel.listId }) { name, color, iconKey in
            Task { await viewModel.updateListSettings(name: name, color: color, iconKey: iconKey) }
        }
    }

    private func handleItemsChanged() {
        activeDropSectionId = nil
        draggedTodo = nil
        inAppDrag = nil
        dropTargetFrames = [:]
        TodoTaskDragSession.shared.todo = nil
        if viewModel.mode == .all, highlightedTodoId != nil {
            collapsedSectionIDs = []
        }
    }

    private func requestReschedule(_ todo: TodoItem, to targetDate: Date) {
        activeDropSectionId = nil
        draggedTodo = nil
        inAppDrag = nil
        dropTargetFrames = [:]
        TodoTaskDragSession.shared.todo = nil
        let targetDay = Calendar.current.startOfDay(for: targetDate)
        let dropSignature = "\(todo.id)|\(targetDay.timeIntervalSince1970)"
        guard TodoTaskDragSession.shared.handledDropSignature != dropSignature else {
            return
        }
        TodoTaskDragSession.shared.handledDropSignature = dropSignature
        guard !Calendar.current.isDate(todo.due, inSameDayAs: targetDate) else {
            return
        }

        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        if todo.isRecurring {
            pendingRescheduleDrop = TodoRescheduleDrop(todo: todo, targetDate: targetDate)
        } else {
            Task { await viewModel.moveTask(todo, toDay: targetDate, scope: .occurrence) }
        }
    }

    private func resolveTodoForDrop(id: String) -> TodoItem? {
        viewModel.items.first { $0.id == id || $0.canonicalId == id }
    }

    private func beginInAppDrag(_ todo: TodoItem, at location: CGPoint) {
        if draggedTodo?.id != todo.id {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
        draggedTodo = todo
        inAppDrag = TodoInAppDrag(todo: todo, location: location)
        updateInAppDrag(todo, to: location)
    }

    private func updateInAppDrag(_ todo: TodoItem, to location: CGPoint) {
        inAppDrag = TodoInAppDrag(todo: todo, location: location)
        activeDropSectionId = dropSectionID(at: location)
    }

    private func finishInAppDrag(_ todo: TodoItem, at location: CGPoint?) {
        let targetSectionID = location.flatMap(dropSectionID(at:)) ?? activeDropSectionId
        let targetDate = targetSectionID
            .flatMap { sectionID in groupedSections.first { $0.id == sectionID }?.targetDate }
        activeDropSectionId = nil
        draggedTodo = nil
        inAppDrag = nil
        dropTargetFrames = [:]
        if let targetDate {
            requestReschedule(todo, to: targetDate)
        }
    }

    private func cancelInAppDrag() {
        activeDropSectionId = nil
        draggedTodo = nil
        inAppDrag = nil
        dropTargetFrames = [:]
    }

    private func dropSectionID(at location: CGPoint) -> String? {
        dropTargetFrames.values
            .filter { $0.frame.contains(location) }
            .min { lhs, rhs in
                (lhs.frame.width * lhs.frame.height) < (rhs.frame.width * rhs.frame.height)
            }?
            .sectionID
    }

    private func commitPendingReschedule(scope: TaskRescheduleScope) {
        guard let drop = pendingRescheduleDrop else {
            return
        }
        pendingRescheduleDrop = nil
        Task {
            await viewModel.moveTask(drop.todo, toDay: drop.targetDate, scope: scope)
        }
    }

    private func matchesHighlightedTodo(_ todo: TodoItem, id: String) -> Bool {
        todo.id == id || todo.canonicalId == id
    }

    private struct HighlightedTodoTarget {
        let todo: TodoItem
        let preScrollTodo: TodoItem
    }

    private func highlightedTodoTarget(for id: String) -> HighlightedTodoTarget? {
        let orderedTodos = groupedSections.flatMap(\.items)
        guard let targetIndex = orderedTodos.firstIndex(where: { matchesHighlightedTodo($0, id: id) }) else {
            return nil
        }
        let preScrollIndex = max(0, targetIndex - TodoTimelineMetrics.searchResultPreScrollItemCount)
        return HighlightedTodoTarget(
            todo: orderedTodos[targetIndex],
            preScrollTodo: orderedTodos[preScrollIndex]
        )
    }

    private func timelineSectionScrollID(_ sectionID: String) -> String {
        "timeline-section-\(sectionID)"
    }

    private func timelineTodoScrollID(_ todoID: String) -> String {
        "timeline-todo-\(todoID)"
    }

    private func shouldFlashTodo(_ todo: TodoItem) -> Bool {
        guard let flashTodoId else {
            return false
        }
        return matchesHighlightedTodo(todo, id: flashTodoId)
    }

    private func scrollToHighlightedTodo(using proxy: ScrollViewProxy) {
        guard viewModel.mode == .all,
              let highlightedTodoId,
              !highlightedTodoId.isEmpty,
              let target = highlightedTodoTarget(for: highlightedTodoId) else {
            return
        }

        let hadCollapsedSections = !collapsedSectionIDs.isEmpty
        if !collapsedSectionIDs.isEmpty {
            withAnimation(.spring(response: 0.28, dampingFraction: 0.9)) {
                collapsedSectionIDs = []
            }
        }

        highlightedScrollRequestID += 1
        let requestID = highlightedScrollRequestID
        let preScrollID = timelineTodoScrollID(target.preScrollTodo.id)
        let targetScrollID = timelineTodoScrollID(target.todo.id)
        let preScrollDelay = hadCollapsedSections ? TodoTimelineMetrics.searchResultSectionExpandDelay : 0

        DispatchQueue.main.asyncAfter(deadline: .now() + preScrollDelay) {
            guard requestID == highlightedScrollRequestID else {
                return
            }
            proxy.scrollTo(preScrollID, anchor: .top)
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + preScrollDelay + TodoTimelineMetrics.searchResultScrollDelay) {
            guard requestID == highlightedScrollRequestID else {
                return
            }

            withAnimation(.easeInOut(duration: TodoTimelineMetrics.searchResultScrollDuration)) {
                proxy.scrollTo(targetScrollID, anchor: .center)
            }

            DispatchQueue.main.asyncAfter(deadline: .now() + TodoTimelineMetrics.searchResultFlashDelay) {
                guard requestID == highlightedScrollRequestID else {
                    return
                }
                flashTodoId = highlightedTodoId
            }

            DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                guard requestID == highlightedScrollRequestID else {
                    return
                }
                if flashTodoId == highlightedTodoId {
                    flashTodoId = nil
                }
            }
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
                    .listRowBackground(colors.background)
                }
            }
            ForEach(groupedSections) { section in
                Section {
                    ForEach(section.items) { todo in
                        todoRow(todo, in: section)
                            .todoInAppDropTargetFrame(
                                targetID: "standard-row-\(section.id)-\(todo.id)",
                                section: section,
                                enabled: viewModel.mode.supportsTaskReschedule && draggedTodo != nil
                            )
                            .listRowBackground(todo.id == highlightedTodoId ? colors.surfaceVariant : colors.surface)
                    }
                    if viewModel.mode.supportsTaskReschedule,
                       activeDropSectionId == section.id,
                       section.targetDate != nil {
                        TodoDropPlaceholder(isActive: activeDropSectionId == section.id)
                            .todoInAppDropTargetFrame(
                                targetID: "standard-placeholder-\(section.id)",
                                section: section,
                                enabled: true
                            )
                            .listRowInsets(EdgeInsets(top: 4, leading: 20, bottom: 6, trailing: 20))
                            .listRowBackground(colors.surface)
                            .scheduledTodoDropTarget(
                                section: section,
                                draggedTodo: draggedTodo,
                                resolveTodo: resolveTodoForDrop,
                                onMove: { todo, targetDate in
                                    requestReschedule(todo, to: targetDate)
                                },
                                onSectionChange: { sectionId in
                                    activeDropSectionId = sectionId
                                }
                            )
                    }
                    if viewModel.mode.supportsTaskReschedule, !section.items.isEmpty {
                        Color.clear
                            .frame(height: 8)
                            .todoInAppDropTargetFrame(
                                targetID: "standard-spacer-\(section.id)",
                                section: section,
                                enabled: draggedTodo != nil
                            )
                            .listRowInsets(EdgeInsets())
                            .scheduledTodoDropTarget(
                                section: section,
                                draggedTodo: draggedTodo,
                                resolveTodo: resolveTodoForDrop,
                                onMove: { todo, targetDate in
                                    requestReschedule(todo, to: targetDate)
                                },
                                onSectionChange: { sectionId in
                                    activeDropSectionId = sectionId
                                }
                            )
                    }
                } header: {
                    Text(section.title)
                        .foregroundStyle(activeDropSectionId == section.id ? colors.error : colors.onSurfaceVariant)
                        .frame(maxWidth: .infinity, minHeight: 38, alignment: .leading)
                        .contentShape(Rectangle())
                        .todoInAppDropTargetFrame(
                            targetID: "standard-header-\(section.id)",
                            section: section,
                            enabled: viewModel.mode.supportsTaskReschedule && draggedTodo != nil
                        )
                        .timelinePinnedSectionHeaderBackground()
                        .scheduledTodoDropTarget(
                            section: section,
                            draggedTodo: draggedTodo,
                            resolveTodo: resolveTodoForDrop,
                            onMove: { todo, targetDate in
                                requestReschedule(todo, to: targetDate)
                            },
                            onSectionChange: { sectionId in
                                activeDropSectionId = sectionId
                            }
                        )
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(colors.background)
        .disableVerticalScrollBounce()
        .animation(.easeInOut(duration: 0.22), value: timelineItemAnimationKey)
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
                        .listRowBackground(colors.background)
                        .listRowSeparator(.hidden)
                    }
                }

                ForEach(Array(groupedSections.enumerated()), id: \.element.id) { index, section in
                    Section {
                        if !section.items.isEmpty {
                            ForEach(Array(section.items.enumerated()), id: \.element.id) { itemIndex, todo in
                                minimalTimelineRow(todo, in: section)
                                    .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
                                    .listRowBackground(colors.background)
                                    .listRowSeparator(.hidden)
                                if shouldShowDateDivider(after: itemIndex, inSectionAt: index, sections: groupedSections) {
                                    TimelineRowDivider()
                                }
                            }
                        }
                    } header: {
                        TimelineSectionHeader(
                            title: section.title,
                            isActiveDropTarget: activeDropSectionId == section.id
                        )
                        .padding(.top, index == 0 ? 0 : 8)
                        .timelinePinnedSectionHeaderBackground()
                        .listRowInsets(
                            EdgeInsets(
                                top: 0,
                                leading: 0,
                                bottom: 0,
                                trailing: 0
                            )
                        )
                        .listRowSeparator(.hidden)
                    }
                }

                Color.clear
                    .frame(height: 120)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(colors.background)
                    .listRowSeparator(.hidden)
                    .disableVerticalScrollBounce()
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(colors.background)
            .contentMargins(.top, 0, for: .scrollContent)
            .listRowSpacing(0)
            .listSectionSpacing(0)
            .environment(\.defaultMinListRowHeight, 1)
            .animation(.easeInOut(duration: 0.22), value: timelineItemAnimationKey)

        }
    }

    private var minimalTimelineModeContent: some View {
        ScrollViewReader { scrollProxy in
            ZStack {
                List {
                    timelineHeroTitleRow

                    if let errorMessage = viewModel.errorMessage {
                        Section {
                            ErrorRetryView(message: errorMessage) {
                                Task { await viewModel.refresh() }
                            }
                            .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 18, trailing: TodoTimelineMetrics.horizontalPadding))
                            .listRowBackground(colors.background)
                            .listRowSeparator(.hidden)
                        }
                    }

                    ForEach(Array(groupedSections.enumerated()), id: \.element.id) { index, section in
                        minimalTimelineSection(
                            section,
                            sectionIndex: index,
                            sections: groupedSections,
                            isFirstSection: index == 0
                        )
                    }

                    Color.clear
                        .frame(height: 120)
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(colors.background)
                        .listRowSeparator(.hidden)
                        .disableVerticalScrollBounce()
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .background(colors.background)
                .contentMargins(.top, 0, for: .scrollContent)
                .listRowSpacing(0)
                .listSectionSpacing(0)
                .environment(\.defaultMinListRowHeight, 1)
                .animation(.easeInOut(duration: 0.22), value: timelineItemAnimationKey)

            }
            .onAppear {
                scrollToHighlightedTodo(using: scrollProxy)
            }
            .onChange(of: highlightedTodoId) {
                scrollToHighlightedTodo(using: scrollProxy)
            }
            .onChange(of: viewModel.items) {
                scrollToHighlightedTodo(using: scrollProxy)
            }
        }
    }

    private func todoRow(
        _ todo: TodoItem,
        in section: TodoTimelineSection
    ) -> some View {
        let isCompleting = completingTodoIDs.contains(todo.id)
        let rowContent = VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 10) {
                Circle()
                    .fill(priorityColor(todo.priority))
                    .frame(width: 10, height: 10)
                Text(todo.title)
                    .font(.tdayRounded(size: 15, weight: .bold))
                    .foregroundStyle(colors.onSurface)
                Spacer()
                if todo.pinned {
                    Image(systemName: "pin.fill")
                        .foregroundStyle(colors.tertiary)
                }
            }
            HStack(spacing: 6) {
                Text(todo.due.formatted(date: .abbreviated, time: .shortened))
                    .font(.tdayRounded(size: 12, weight: .semibold))
                    .foregroundStyle(colors.onSurfaceVariant)
            }
            if let description = todo.description, !description.isEmpty {
                Text(description)
                    .font(.tdayRounded(size: 12, weight: .semibold))
                    .foregroundStyle(colors.onSurfaceVariant)
                    .lineLimit(2)
            }
        }
        .opacity(isCompleting ? 0 : 1)
        .scaleEffect(isCompleting ? 0.985 : 1, anchor: .center)
        .animation(.easeInOut(duration: 0.16), value: isCompleting)
        .opacity(draggedTodo?.id == todo.id && activeDropSectionId != nil ? 0.55 : 1)
        .allowsHitTesting(!isCompleting)
        .todoTrailingSwipeActions(
            enabled: !isCompleting,
            onEdit: {
                editingTodo = todo
            },
            onDelete: {
                Task { await viewModel.delete(todo) }
            }
        )
        .swipeActions(edge: .leading, allowsFullSwipe: true) {
            Button {
                completeTodoWithoutReflow(todo)
            } label: {
                Label("Complete", systemImage: "checkmark")
            }
            .tint(.green)
        }

        return rowContent
            .transition(.opacity.combined(with: .scale(scale: 0.985)))
            .scheduledTodoDropTarget(
                section: section,
                draggedTodo: draggedTodo,
                resolveTodo: resolveTodoForDrop,
                onMove: { droppedTodo, targetDate in
                    requestReschedule(droppedTodo, to: targetDate)
                },
                onSectionChange: { sectionId in
                    activeDropSectionId = sectionId
                }
            )
            .modifier(
                TodoInAppDragModifier(
                    enabled: viewModel.mode.supportsTaskReschedule,
                    todo: todo,
                    onStart: beginInAppDrag,
                    onMove: updateInAppDrag,
                    onEnd: finishInAppDrag,
                    onCancel: cancelInAppDrag
                )
            )
    }

    private func minimalTimelineRow(_ todo: TodoItem, in section: TodoTimelineSection, flashHighlight: Bool = false) -> some View {
        let listMeta = todo.listId.flatMap { listId in
            viewModel.lists.first(where: { $0.id == listId })
        }
        let showListIndicator = listMeta != nil && viewModel.mode != .list
        let showPriorityFlag = todo.priority.lowercased() == "high"
        let subtitleText = minimalTimelineSubtitle(for: todo, in: section)
        let isOverdueTask = !todo.completed && todo.due < Date()
        let subtitleColor = isOverdueTask ? colors.error : colors.onSurfaceVariant.opacity(0.8)
        let isCompleting = completingTodoIDs.contains(todo.id)

        return VStack(spacing: 0) {
            HStack(alignment: .center, spacing: 12) {
                Button {
                    completeTodoWithoutReflow(todo)
                } label: {
                    Image(systemName: todo.completed ? "checkmark.circle.fill" : "circle")
                        .font(.system(size: TodoTimelineMetrics.minimalRowToggleSize, weight: .regular))
                        .foregroundStyle(todo.completed ? Color.green : colors.onSurfaceVariant.opacity(0.78))
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
                    Text(todo.title)
                        .font(.tdayRounded(size: TodoTimelineMetrics.minimalRowTitleSize, weight: .bold))
                        .foregroundStyle(colors.onSurface)
                        .lineLimit(2)

                    Text(subtitleText)
                        .font(.tdayRounded(size: TodoTimelineMetrics.minimalRowSubtitleSize, weight: .semibold))
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
                    .padding(.trailing, TodoTimelineMetrics.minimalRowTrailingIndicatorPadding)
                }
            }
            .padding(.vertical, TodoTimelineMetrics.minimalRowVerticalPadding)
            .contentShape(Rectangle())
        }
        .opacity(isCompleting ? 0 : (draggedTodo?.id == todo.id && activeDropSectionId != nil ? 0.55 : 1))
        .scaleEffect(isCompleting ? 0.985 : 1, anchor: .center)
        .animation(.easeInOut(duration: 0.16), value: isCompleting)
        .allowsHitTesting(!isCompleting)
        .transition(.opacity.combined(with: .scale(scale: 0.985)))
        .modifier(TimelineTaskFlashHighlight(active: flashHighlight))
        .todoTrailingSwipeActions(
            enabled: !isCompleting,
            onEdit: {
                editingTodo = todo
            },
            onDelete: {
                Task { await viewModel.delete(todo) }
            }
        )
        .scheduledTodoDropTarget(
            section: section,
            draggedTodo: draggedTodo,
            resolveTodo: resolveTodoForDrop,
            onMove: { droppedTodo, targetDate in
                requestReschedule(droppedTodo, to: targetDate)
            },
            onSectionChange: { sectionId in
                activeDropSectionId = sectionId
            }
        )
        .modifier(
            TodoInAppDragModifier(
                enabled: viewModel.mode.supportsTaskReschedule,
                todo: todo,
                onStart: beginInAppDrag,
                onMove: updateInAppDrag,
                onEnd: finishInAppDrag,
                onCancel: cancelInAppDrag
            )
        )
    }

    private func completeTodoWithoutReflow(_ todo: TodoItem) {
        guard !completingTodoIDs.contains(todo.id) else {
            return
        }
        withAnimation(.easeInOut(duration: 0.16)) {
            _ = completingTodoIDs.insert(todo.id)
        }
        Task {
            try? await Task.sleep(nanoseconds: 190_000_000)
            await viewModel.complete(todo)
            await MainActor.run {
                _ = completingTodoIDs.remove(todo.id)
            }
        }
    }

    @ViewBuilder
    private func minimalTimelineSection(
        _ section: TodoTimelineSection,
        sectionIndex: Int,
        sections: [TodoTimelineSection],
        isFirstSection: Bool
    ) -> some View {
        let canCollapseSection = canCollapseTimelineSection(section)
        let isCollapsed = canCollapseSection && collapsedSectionIDs.contains(section.id)

        Section {
            if viewModel.mode.supportsTaskReschedule,
               activeDropSectionId == section.id,
               section.targetDate != nil {
                TodoDropPlaceholder(isActive: activeDropSectionId == section.id)
                    .todoInAppDropTargetFrame(
                        targetID: "minimal-placeholder-\(section.id)",
                        section: section,
                        enabled: true
                    )
                    .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 8, trailing: TodoTimelineMetrics.horizontalPadding))
                    .listRowBackground(colors.background)
                    .listRowSeparator(.hidden)
                    .transition(timelineRowTransition())
                    .scheduledTodoDropTarget(
                        section: section,
                        draggedTodo: draggedTodo,
                        resolveTodo: resolveTodoForDrop,
                        onMove: { todo, targetDate in
                            requestReschedule(todo, to: targetDate)
                        },
                        onSectionChange: { sectionId in
                            activeDropSectionId = sectionId
                        }
                    )
            }
            if !isCollapsed {
                ForEach(Array(section.items.enumerated()), id: \.element.id) { itemIndex, todo in
                    minimalTimelineRow(todo, in: section, flashHighlight: shouldFlashTodo(todo))
                        .id(timelineTodoScrollID(todo.id))
                        .todoInAppDropTargetFrame(
                            targetID: "minimal-row-\(section.id)-\(todo.id)",
                            section: section,
                            enabled: viewModel.mode.supportsTaskReschedule && draggedTodo != nil
                        )
                        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
                        .listRowBackground(colors.background)
                        .listRowSeparator(.hidden)
                        .transition(timelineRowTransition())
                    if shouldShowDateDivider(after: itemIndex, inSectionAt: sectionIndex, sections: sections) {
                        TimelineRowDivider()
                            .transition(timelineRowTransition())
                    }
                }
            }
        } header: {
            TimelineSectionHeader(
                title: section.title,
                isActiveDropTarget: activeDropSectionId == section.id,
                isCollapsible: canCollapseSection,
                isCollapsed: isCollapsed,
                onTap: canCollapseSection ? {
                    toggleTimelineSection(section)
                } : nil
            )
            .id(timelineSectionScrollID(section.id))
            .padding(.top, isFirstSection ? 0 : 8)
            .frame(maxWidth: .infinity, minHeight: viewModel.mode.supportsTaskReschedule && draggedTodo != nil ? 44 : nil, alignment: .leading)
            .contentShape(Rectangle())
            .todoInAppDropTargetFrame(
                targetID: "minimal-header-\(section.id)",
                section: section,
                enabled: viewModel.mode.supportsTaskReschedule && draggedTodo != nil
            )
            .timelinePinnedSectionHeaderBackground()
            .scheduledTodoDropTarget(
                section: section,
                draggedTodo: draggedTodo,
                resolveTodo: resolveTodoForDrop,
                onMove: { todo, targetDate in
                    requestReschedule(todo, to: targetDate)
                },
                onSectionChange: { sectionId in
                    activeDropSectionId = sectionId
                }
            )
            .listRowInsets(
                EdgeInsets(
                    top: 0,
                    leading: 0,
                    bottom: 0,
                    trailing: 0
                )
            )
            .listRowSeparator(.hidden)
        }
    }

    private func canCollapseTimelineSection(_ section: TodoTimelineSection) -> Bool {
        guard !section.items.isEmpty else {
            return false
        }
        if viewModel.mode == .all {
            return true
        }
        if viewModel.mode == .overdue {
            return true
        }
        return viewModel.mode == .priority && section.isCollapsible
    }

    private func isTimelineSectionCollapsed(_ section: TodoTimelineSection) -> Bool {
        canCollapseTimelineSection(section) && collapsedSectionIDs.contains(section.id)
    }

    private func toggleTimelineSection(_ section: TodoTimelineSection) {
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
        sections: [TodoTimelineSection]
    ) -> Bool {
        guard sections.indices.contains(sectionIndex),
              sections[sectionIndex].items.indices.contains(itemIndex) else {
            return false
        }

        let currentTodo = sections[sectionIndex].items[itemIndex]
        let nextTodoInSection = sections[sectionIndex].items.dropFirst(itemIndex + 1).first
        if let nextTodoInSection {
            return !Calendar.current.isDate(currentTodo.due, inSameDayAs: nextTodoInSection.due)
        }

        let nextVisibleTodo = sections.dropFirst(sectionIndex + 1)
            .first { !isTimelineSectionCollapsed($0) && !$0.items.isEmpty }?
            .items.first

        guard let nextVisibleTodo else {
            return false
        }
        return !Calendar.current.isDate(currentTodo.due, inSameDayAs: nextVisibleTodo.due)
    }

    private func timelineRowTransition() -> AnyTransition {
        let insertion = AnyTransition.opacity
            .combined(with: .move(edge: .top))
            .animation(.easeOut(duration: 0.16))
        let removal = AnyTransition.opacity
            .combined(with: .move(edge: .top))
            .animation(.easeOut(duration: 0.1))
        return .asymmetric(insertion: insertion, removal: removal)
    }

    private func minimalTimelineSubtitle(for todo: TodoItem, in section: TodoTimelineSection) -> String {
        let timeText = todo.due.formatted(date: .omitted, time: .shortened)
        let dueBodyText = if viewModel.mode == .priority && section.id == "earlier" {
            timelineDateTimeText(todo.due)
        } else {
            timeText
        }

        switch viewModel.mode {
        case .today:
            if !todo.completed && todo.due < Date() {
                return "Overdue, \(dueBodyText)"
            }
            return "Due \(dueBodyText)"
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
        case .list:
            if !todo.completed && todo.due < Date() {
                return "Overdue, \(dueBodyText)"
            }
            return "Due \(dueBodyText)"
        default:
            return dueBodyText
        }
    }
}

struct TimelineTopBar: View {
    let title: String
    let accentColor: Color
    let collapseProgress: CGFloat
    let onBack: () -> Void
    let action: TimelineTopBarAction?
    let titleRevealStart: CGFloat
    let titleRevealEnd: CGFloat
    let titleRevealDistance: CGFloat

    @Environment(\.tdayColors) private var colors

    init(
        title: String,
        accentColor: Color,
        collapseProgress: CGFloat,
        onBack: @escaping () -> Void,
        action: TimelineTopBarAction?,
        titleRevealStart: CGFloat = TodoTimelineMetrics.collapsedTitleRevealStart,
        titleRevealEnd: CGFloat = TodoTimelineMetrics.collapsedTitleRevealEnd,
        titleRevealDistance: CGFloat = TodoTimelineMetrics.collapsedTitleRevealDistance
    ) {
        self.title = title
        self.accentColor = accentColor
        self.collapseProgress = collapseProgress
        self.onBack = onBack
        self.action = action
        self.titleRevealStart = titleRevealStart
        self.titleRevealEnd = titleRevealEnd
        self.titleRevealDistance = titleRevealDistance
    }

    private var progress: CGFloat {
        min(max(collapseProgress, 0), 1)
    }

    private var revealProgress: CGFloat {
        TodoTimelineMetrics.progress(
            progress,
            from: titleRevealStart,
            to: titleRevealEnd
        )
    }

    private var titleOffsetY: CGFloat {
        titleRevealDistance * (1 - revealProgress)
    }

    private var titleContent: some View {
        Text(title)
            .font(.tdayRounded(size: TodoTimelineMetrics.heroTitleSize, weight: .heavy))
            .foregroundStyle(accentColor)
            .lineLimit(1)
    }

    var body: some View {
        ZStack {
            HStack(spacing: 0) {
                TimelineTopBarButton(systemName: "chevron.left", chrome: .filled, action: onBack)
                Spacer(minLength: 0)
                if let action {
                    TimelineTopBarButton(
                        systemName: action.systemName,
                        chrome: action.usesCircularChrome ? .outlined : .plain,
                        tint: action.tint,
                        action: action.action
                    )
                } else {
                    Color.clear
                        .frame(width: TodoTimelineMetrics.topBarButtonFrame, height: TodoTimelineMetrics.topBarButtonFrame)
                }
            }

            titleContent
                .opacity(revealProgress)
                .offset(y: titleOffsetY)
                .scaleEffect(0.985 + (0.015 * revealProgress))
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

struct TimelineExpandedTitleRow: View {
    let title: String
    let accentColor: Color
    let collapseProgress: CGFloat

    private var progress: CGFloat {
        min(max(collapseProgress, 0), 1)
    }

    private var fadeProgress: CGFloat {
        TodoTimelineMetrics.progress(
            progress,
            from: TodoTimelineMetrics.expandedTitleFadeStart,
            to: TodoTimelineMetrics.expandedTitleFadeEnd
        )
    }

    private var titleOffsetY: CGFloat {
        -TodoTimelineMetrics.expandedTitleLiftDistance * fadeProgress
    }

    private var titleOpacity: Double {
        Double(1 - fadeProgress)
    }

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Text(title)
                .font(.tdayRounded(size: TodoTimelineMetrics.heroTitleSize, weight: .heavy))
                .foregroundStyle(accentColor)
                .lineLimit(1)
                .frame(
                    maxWidth: .infinity,
                    minHeight: TodoTimelineMetrics.expandedTitleHeight,
                    maxHeight: TodoTimelineMetrics.expandedTitleHeight,
                    alignment: .bottomLeading
                )
                .opacity(titleOpacity)
                .offset(y: titleOffsetY)
        }
        .frame(
            maxWidth: .infinity,
            minHeight: TodoTimelineMetrics.titleCollapseDistance,
            maxHeight: TodoTimelineMetrics.titleCollapseDistance,
            alignment: .bottomLeading
        )
        .clipped()
        .allowsHitTesting(false)
    }
}

private struct TimelineTopBarButton: View {
    enum Chrome {
        case plain
        case filled
        case outlined
    }

    let systemName: String
    let chrome: Chrome
    let tint: Color?
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    init(systemName: String, chrome: Chrome, tint: Color? = nil, action: @escaping () -> Void) {
        self.systemName = systemName
        self.chrome = chrome
        self.tint = tint
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: iconSize, weight: .semibold))
                .frame(width: TodoTimelineMetrics.topBarButtonFrame, height: TodoTimelineMetrics.topBarButtonFrame)
                .background {
                    if chrome == .filled {
                        Circle()
                            .fill(colors.surface)
                    } else if chrome == .outlined {
                        Circle()
                            .fill(outlinedFillColor)
                            .overlay {
                                Circle()
                                    .stroke(outlinedBorderColor, lineWidth: 1)
                            }
                    }
                }
                .contentShape(Circle())
        }
        .buttonStyle(TimelineTopBarButtonStyle(isFilled: chrome == .filled))
        .foregroundStyle(foregroundColor)
    }

    private var iconSize: CGFloat {
        chrome == .filled ? TodoTimelineMetrics.topBarButtonIconSize : 28
    }

    private var foregroundColor: Color {
        switch chrome {
        case .filled:
            return colors.onSurface
        case .outlined:
            return tint ?? colors.onSurface
        case .plain:
            return tint ?? Color.accentColor
        }
    }

    private var outlinedFillColor: Color {
        if let tint {
            return tint.opacity(0.12)
        }
        return colors.background
    }

    private var outlinedBorderColor: Color {
        if let tint {
            return tint.opacity(0.48)
        }
        return colors.onSurfaceVariant.opacity(0.28)
    }
}

private struct TimelineTopBarButtonStyle: ButtonStyle {
    let isFilled: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .tdayRippleEffect(isPressed: configuration.isPressed)
            .scaleEffect(configuration.isPressed ? 0.95 : 1)
            .offset(y: configuration.isPressed ? 1 : 0)
            .shadow(
                color: Color.black.opacity(shadowOpacity(isPressed: configuration.isPressed)),
                radius: shadowRadius(isPressed: configuration.isPressed),
                x: 0,
                y: shadowOffsetY(isPressed: configuration.isPressed)
            )
            .animation(.easeOut(duration: 0.14), value: configuration.isPressed)
    }

    private func shadowOpacity(isPressed: Bool) -> Double {
        guard isFilled else {
            return 0
        }

        return isPressed ? 0.04 : 0.08
    }

    private func shadowRadius(isPressed: Bool) -> CGFloat {
        guard isFilled else {
            return 0
        }

        return isPressed ? 3 : 7
    }

    private func shadowOffsetY(isPressed: Bool) -> CGFloat {
        guard isFilled else {
            return 0
        }

        return isPressed ? 1 : 3
    }
}

private struct TimelineScrollOffsetTrackingRow: View {
    let onChange: (CGFloat) -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        TimelineScrollOffsetObserver(onChange: onChange)
            .frame(height: 0)
            .listRowInsets(EdgeInsets())
            .listRowBackground(colors.background)
            .listRowSeparator(.hidden)
            .allowsHitTesting(false)
    }
}

struct TimelineScrollOffsetObserver: UIViewRepresentable {
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

struct TimelineSectionHeader: View {
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
                    .font(.tdayRounded(size: TodoTimelineMetrics.sectionTitleSize, weight: .bold))
                    .foregroundStyle(isActiveDropTarget ? colors.error : colors.onSurfaceVariant.opacity(0.78))
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
        .contentShape(Rectangle())

        if let onTap {
            Button(action: onTap) {
                content
            }
            .buttonStyle(TimelineSectionHeaderButtonStyle())
        } else {
            content
        }
    }
}

private struct TodoDragPreview: View {
    let todo: TodoItem

    @Environment(\.tdayColors) private var colors

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "circle")
                .font(.system(size: 22, weight: .regular))
                .foregroundStyle(colors.onSurfaceVariant.opacity(0.76))

            VStack(alignment: .leading, spacing: 3) {
                Text(todo.title)
                    .font(.tdayRounded(size: 16, weight: .bold))
                    .foregroundStyle(colors.onSurface)
                    .lineLimit(1)
                Text(todo.due.formatted(date: .omitted, time: .shortened))
                    .font(.tdayRounded(size: 12, weight: .semibold))
                    .foregroundStyle(colors.onSurfaceVariant)
                    .lineLimit(1)
            }

            Spacer(minLength: 0)

            if todo.priority.lowercased() == "high" {
                Image(systemName: "flag.fill")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(priorityColor(todo.priority))
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .frame(width: 260, alignment: .leading)
        .background(colors.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(colors.onSurfaceVariant.opacity(0.14), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.18), radius: 16, x: 0, y: 8)
        .opacity(0.96)
    }
}

private struct TodoDropPlaceholder: View {
    let isActive: Bool

    @Environment(\.tdayColors) private var colors

    var body: some View {
        RoundedRectangle(cornerRadius: 16, style: .continuous)
            .fill(isActive ? colors.error.opacity(0.10) : colors.surfaceVariant.opacity(0.18))
            .overlay(
                placeholderStroke
            )
            .frame(height: isActive ? 70 : 52)
            .animation(.easeInOut(duration: 0.18), value: isActive)
            .accessibilityHidden(true)
    }

    @ViewBuilder
    private var placeholderStroke: some View {
        if isActive {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(colors.error.opacity(0.72), lineWidth: 1.5)
        } else {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(
                    colors.onSurfaceVariant.opacity(0.18),
                    style: StrokeStyle(lineWidth: 1, dash: [7, 7])
                )
        }
    }
}

private struct TodoInAppDragModifier: ViewModifier {
    let enabled: Bool
    let todo: TodoItem
    let onStart: (TodoItem, CGPoint) -> Void
    let onMove: (TodoItem, CGPoint) -> Void
    let onEnd: (TodoItem, CGPoint?) -> Void
    let onCancel: () -> Void

    func body(content: Content) -> some View {
        if enabled {
            content
                .background {
                    GeometryReader { _ in
                        TodoInAppLongPressBridge(
                            enabled: enabled,
                            todo: todo,
                            onStart: onStart,
                            onMove: onMove,
                            onEnd: onEnd,
                            onCancel: onCancel
                        )
                        .allowsHitTesting(false)
                    }
                }
        } else {
            content
        }
    }
}

private struct TodoInAppLongPressBridge: UIViewRepresentable {
    let enabled: Bool
    let todo: TodoItem
    let onStart: (TodoItem, CGPoint) -> Void
    let onMove: (TodoItem, CGPoint) -> Void
    let onEnd: (TodoItem, CGPoint?) -> Void
    let onCancel: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(
            enabled: enabled,
            todo: todo,
            onStart: onStart,
            onMove: onMove,
            onEnd: onEnd,
            onCancel: onCancel
        )
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = false
        context.coordinator.markerView = view
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.enabled = enabled
        context.coordinator.todo = todo
        context.coordinator.onStart = onStart
        context.coordinator.onMove = onMove
        context.coordinator.onEnd = onEnd
        context.coordinator.onCancel = onCancel
        DispatchQueue.main.async {
            context.coordinator.attach(to: uiView.enclosingScrollView() ?? uiView.superview, markerView: uiView)
        }
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        coordinator.detach()
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var enabled: Bool
        var todo: TodoItem
        var onStart: (TodoItem, CGPoint) -> Void
        var onMove: (TodoItem, CGPoint) -> Void
        var onEnd: (TodoItem, CGPoint?) -> Void
        var onCancel: () -> Void

        weak var markerView: UIView?
        private weak var attachedView: UIView?
        private let recognizer: UILongPressGestureRecognizer
        private var isDragging = false

        init(
            enabled: Bool,
            todo: TodoItem,
            onStart: @escaping (TodoItem, CGPoint) -> Void,
            onMove: @escaping (TodoItem, CGPoint) -> Void,
            onEnd: @escaping (TodoItem, CGPoint?) -> Void,
            onCancel: @escaping () -> Void
        ) {
            self.enabled = enabled
            self.todo = todo
            self.onStart = onStart
            self.onMove = onMove
            self.onEnd = onEnd
            self.onCancel = onCancel
            self.recognizer = UILongPressGestureRecognizer()
            super.init()

            recognizer.minimumPressDuration = 0.22
            recognizer.allowableMovement = 24
            recognizer.cancelsTouchesInView = false
            recognizer.delaysTouchesBegan = false
            recognizer.delaysTouchesEnded = false
            recognizer.delegate = self
            recognizer.addTarget(self, action: #selector(handleLongPress(_:)))
        }

        func attach(to view: UIView?, markerView: UIView) {
            self.markerView = markerView
            guard enabled, let view else {
                detach()
                return
            }

            guard attachedView !== view else {
                return
            }

            detach()
            attachedView = view
            view.addGestureRecognizer(recognizer)
        }

        func detach() {
            if isDragging {
                isDragging = false
                onCancel()
            }
            attachedView?.removeGestureRecognizer(recognizer)
            attachedView = nil
        }

        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            guard enabled, let markerView else {
                return false
            }

            let localPoint = gestureRecognizer.location(in: markerView)
            return markerView.bounds.insetBy(dx: -6, dy: -6).contains(localPoint)
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
            guard enabled, let markerView else {
                return false
            }

            let localPoint = touch.location(in: markerView)
            return markerView.bounds.insetBy(dx: -6, dy: -6).contains(localPoint)
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }

        @objc private func handleLongPress(_ recognizer: UILongPressGestureRecognizer) {
            let location = globalLocation(for: recognizer)
            switch recognizer.state {
            case .began:
                guard enabled else {
                    return
                }
                isDragging = true
                onStart(todo, location)
            case .changed:
                guard isDragging else {
                    return
                }
                onMove(todo, location)
            case .ended:
                guard isDragging else {
                    return
                }
                isDragging = false
                onEnd(todo, location)
            case .cancelled, .failed:
                guard isDragging else {
                    return
                }
                isDragging = false
                onCancel()
            default:
                break
            }
        }

        private func globalLocation(for recognizer: UILongPressGestureRecognizer) -> CGPoint {
            guard let view = recognizer.view else {
                return .zero
            }

            return view.convert(recognizer.location(in: view), to: nil)
        }
    }
}

private struct TodoInAppDropTargetFrameModifier: ViewModifier {
    let targetID: String
    let section: TodoTimelineSection
    let enabled: Bool

    func body(content: Content) -> some View {
        content.background {
            if enabled, section.targetDate != nil {
                GeometryReader { proxy in
                    Color.clear.preference(
                        key: TodoDropTargetFramePreferenceKey.self,
                        value: [
                            targetID: TodoDropTargetFrame(
                                sectionID: section.id,
                                frame: proxy.frame(in: .global)
                            )
                        ]
                    )
                }
            }
        }
    }
}

private extension View {
    func todoInAppDropTargetFrame(
        targetID: String,
        section: TodoTimelineSection,
        enabled: Bool
    ) -> some View {
        modifier(
            TodoInAppDropTargetFrameModifier(
                targetID: targetID,
                section: section,
                enabled: enabled
            )
        )
    }
}

private struct TimelineSectionHeaderButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .brightness(configuration.isPressed ? -0.055 : 0)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

private struct ListSettingsSheet: View {
    let list: ListSummary?
    let onSubmit: (String, String?, String?) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.tdayColors) private var tdayColors

    @State private var name = ""
    @State private var color = "BLUE"
    @State private var iconKey = "inbox"

    private let colors = ["BLUE", "GREEN", "ORANGE", "PINK", "PURPLE", "GRAY"]
    private let icons = ["inbox", "briefcase", "calendar", "list.bullet", "star", "heart"]
    private var canSave: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ListSettingsSheetHeader(
                    canSave: canSave,
                    onClose: { dismiss() },
                    onConfirm: {
                        onSubmit(name.trimmingCharacters(in: .whitespacesAndNewlines), color, iconKey)
                        dismiss()
                    }
                )

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
                .scrollContentBackground(.hidden)
                .background(tdayColors.bottomSheetBackground)
            }
            .background(tdayColors.bottomSheetBackground)
            .disableVerticalScrollBounce()
            .toolbar(.hidden, for: .navigationBar)
            .task {
                name = list?.name ?? ""
                color = list?.color ?? "BLUE"
                iconKey = list?.iconKey ?? "inbox"
            }
        }
        .presentationBackground {
            tdayColors.bottomSheetBackground
                .ignoresSafeArea(.container, edges: .bottom)
        }
    }
}

private struct ListSettingsSheetHeader: View {
    let canSave: Bool
    let onClose: () -> Void
    let onConfirm: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        HStack {
            ListSettingsSheetActionButton(
                icon: "xmark",
                accessibilityLabel: "Cancel",
                accentColor: Color(red: 227.0 / 255.0, green: 90.0 / 255.0, blue: 90.0 / 255.0),
                enabled: true,
                action: onClose
            )

            Spacer(minLength: 0)

            Text("List Settings")
                .font(.tdayRounded(size: 28, weight: .heavy))
                .foregroundStyle(colors.onSurface)
                .lineLimit(1)
                .minimumScaleFactor(0.78)

            Spacer(minLength: 0)

            ListSettingsSheetActionButton(
                icon: "checkmark",
                accessibilityLabel: "Save",
                accentColor: Color(red: 47.0 / 255.0, green: 163.0 / 255.0, blue: 91.0 / 255.0),
                enabled: canSave,
                action: onConfirm
            )
        }
        .padding(.horizontal, 18)
        .padding(.top, 14)
        .padding(.bottom, 14)
        .background(colors.bottomSheetBackground)
    }
}

private struct ListSettingsSheetActionButton: View {
    let icon: String
    let accessibilityLabel: String
    let accentColor: Color
    let enabled: Bool
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(colors.onSurface.opacity(enabled ? 1 : 0.55))
                .frame(width: 56, height: 56)
                .background(colors.bottomSheetControlSurface, in: Circle())
                .overlay {
                    Circle()
                        .stroke(accentColor.opacity(enabled ? 0.55 : 0.3), lineWidth: 1.5)
                }
                .contentShape(Circle())
        }
        .buttonStyle(
            TdayPressButtonStyle(
                shadowColor: Color.black,
                pressedShadowOpacity: 0.04,
                normalShadowOpacity: enabled ? 0.16 : 0.06
            )
        )
        .disabled(!enabled)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityAddTraits(.isButton)
    }
}

private struct TodoTimelineSection: Identifiable, Hashable {
    let id: String
    let title: String
    let items: [TodoItem]
    let isCollapsible: Bool
    let targetDate: Date?
}

private struct TodoRescheduleDrop: Equatable {
    let todo: TodoItem
    let targetDate: Date
}

private struct ScheduledDragModifier: ViewModifier {
    let enabled: Bool
    let todo: TodoItem
    let onDragStart: () -> Void

    @ViewBuilder
    func body(content: Content) -> some View {
        if enabled {
            content.onDrag {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                onDragStart()
                TodoTaskDragSession.shared.todo = todo
                TodoTaskDragSession.shared.handledDropSignature = nil
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
    let resolveTodo: (String) -> TodoItem?
    let onMove: (TodoItem, Date) -> Void
    let onSectionChange: (String?) -> Void

    func validateDrop(info: DropInfo) -> Bool {
        section.targetDate != nil && info.hasItemsConforming(to: todoDragContentTypes)
    }

    func dropEntered(info: DropInfo) {
        if validateDrop(info: info) {
            onSectionChange(section.id)
        }
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
        guard let todo = draggedTodo ?? TodoTaskDragSession.shared.todo,
              let targetDate = section.targetDate else {
            return performProviderDrop(info: info)
        }
        onMove(todo, targetDate)
        return true
    }

    private func performProviderDrop(info: DropInfo) -> Bool {
        guard let targetDate = section.targetDate,
              let provider = info.itemProviders(for: todoDragContentTypes).first else {
            return false
        }
        provider.loadObject(ofClass: NSString.self) { object, _ in
            guard let rawId = object as? NSString else {
                return
            }
            let todoId = rawId as String
            DispatchQueue.main.async {
                if let todo = resolveTodo(todoId) {
                    onMove(todo, targetDate)
                }
            }
        }
        return true
    }
}

private extension View {
    func scheduledTodoDropTarget(
        section: TodoTimelineSection,
        draggedTodo: TodoItem?,
        resolveTodo: @escaping (String) -> TodoItem?,
        onMove: @escaping (TodoItem, Date) -> Void,
        onSectionChange: @escaping (String?) -> Void
    ) -> some View {
        self
            .onDrop(
                of: todoDragContentTypes,
                delegate: ScheduledTodoDropDelegate(
                    section: section,
                    draggedTodo: draggedTodo,
                    resolveTodo: resolveTodo,
                    onMove: onMove,
                    onSectionChange: onSectionChange
                )
            )
            .dropDestination(for: String.self) { ids, _ in
                guard let targetDate = section.targetDate else {
                    onSectionChange(nil)
                    return false
                }
                let todo = draggedTodo
                    ?? TodoTaskDragSession.shared.todo
                    ?? ids.compactMap(resolveTodo).first
                guard let todo else {
                    onSectionChange(nil)
                    return false
                }
                onSectionChange(nil)
                onMove(todo, targetDate)
                return true
            } isTargeted: { active in
                guard section.targetDate != nil else {
                    if !active {
                        onSectionChange(nil)
                    }
                    return
                }
                onSectionChange(active ? section.id : nil)
            }
    }
}

private func buildSections(
    items: [TodoItem],
    mode: TodoListMode,
    includeEmptyEarlierTarget: Bool = false
) -> [TodoTimelineSection] {
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
                items: grouped[key, default: []].sorted(by: todoTimelineSortPrecedes),
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
                    items: todaysItems.sorted(by: todoTimelineSortPrecedes),
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
                    items: grouped[date]?.sorted(by: todoTimelineSortPrecedes) ?? [],
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
                    items: grouped[date]?.sorted(by: todoTimelineSortPrecedes) ?? [],
                    isCollapsible: false,
                    targetDate: timelineRescheduleTargetDate(
                        sectionId: "scheduled-\(date.timeIntervalSince1970)",
                        calendar: calendar
                    )
                )
            }
    case .all:
        return buildFutureTimelineSections(
            items: items,
            calendar: calendar,
            placesEarlierBeforeToday: true,
            includeEmptyEarlierTarget: includeEmptyEarlierTarget
        )
    case .priority, .list:
        return buildFutureTimelineSections(
            items: items,
            calendar: calendar,
            placesEarlierBeforeToday: false,
            includeEmptyEarlierTarget: includeEmptyEarlierTarget
        )
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

private func buildFutureTimelineSections(
    items: [TodoItem],
    calendar: Calendar,
    placesEarlierBeforeToday: Bool,
    includeEmptyEarlierTarget: Bool
) -> [TodoTimelineSection] {
    let now = Date()
    let today = calendar.startOfDay(for: now)
    let groupedByDate = Dictionary(grouping: items.sorted(by: todoTimelineSortPrecedes)) { item in
        calendar.startOfDay(for: item.due)
    }
    let currentYear = calendar.component(.year, from: today)
    let currentMonth = calendar.component(.month, from: today)
    let currentMonthIndex = monthIndex(for: today, calendar: calendar)
    let horizonStart = calendar.date(byAdding: .day, value: 7, to: today) ?? today

    func daySection(for date: Date, title: String) -> TodoTimelineSection {
        let sectionId = "priority-\(date.timeIntervalSince1970)"
        return TodoTimelineSection(
            id: sectionId,
            title: title,
            items: groupedByDate[date] ?? [],
            isCollapsible: false,
            targetDate: timelineRescheduleTargetDate(sectionId: sectionId, calendar: calendar)
        )
    }

    var sections: [TodoTimelineSection] = []

    let earlierItems = groupedByDate.keys
        .filter { $0 < today }
        .sorted()
        .flatMap { groupedByDate[$0] ?? [] }

    let earlierSection: TodoTimelineSection?
    if !earlierItems.isEmpty || includeEmptyEarlierTarget {
        earlierSection = TodoTimelineSection(
            id: "earlier",
            title: "Earlier",
            items: earlierItems,
            isCollapsible: !earlierItems.isEmpty,
            targetDate: timelineRescheduleTargetDate(sectionId: "earlier", today: today, calendar: calendar)
        )
    } else {
        earlierSection = nil
    }

    if placesEarlierBeforeToday, let earlierSection {
        sections.append(earlierSection)
    }

    sections.append(daySection(for: today, title: "Today"))

    if !placesEarlierBeforeToday, let earlierSection {
        sections.append(earlierSection)
    }

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
                targetDate: timelineRescheduleTargetDate(
                    sectionId: "rest-\(currentMonthIndex)",
                    calendar: calendar
                )
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
                targetDate: timelineRescheduleTargetDate(
                    sectionId: "month-\(targetMonthIndex)",
                    calendar: calendar
                )
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

func priorityColor(_ priority: String) -> Color {
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

private func emptyTimelineSystemImage(for mode: TodoListMode, listIconKey: String?) -> String {
    switch mode {
    case .today:
        return "sun.max.fill"
    case .overdue:
        return "exclamationmark.circle"
    case .scheduled:
        return "clock"
    case .all:
        return "tray.fill"
    case .priority:
        return "flag.fill"
    case .list:
        return todoListSymbolName(for: listIconKey)
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

func todoListAccentColor(for key: String?) -> Color {
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
