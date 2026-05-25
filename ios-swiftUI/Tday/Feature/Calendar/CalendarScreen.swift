import SwiftUI
import UIKit
import UniformTypeIdentifiers

private let calendarTaskDragContentTypes = [UTType.plainText.identifier, UTType.text.identifier]

private final class CalendarTaskDragSession {
    static let shared = CalendarTaskDragSession()
    var todo: TodoItem?
    var handledDropSignature: String?

    private init() {}
}

private struct CalendarInAppDrag: Equatable {
    let todo: TodoItem
    var location: CGPoint
}

private enum CalendarTaskCompletionPhase {
    case active
    case checked
    case struck
    case fading
}

private struct CalendarDateDropTargetFrame: Equatable {
    let date: Date
    let frame: CGRect
}

private struct CalendarDateDropTargetFramePreferenceKey: PreferenceKey {
    static var defaultValue: [String: CalendarDateDropTargetFrame] = [:]

    static func reduce(value: inout [String: CalendarDateDropTargetFrame], nextValue: () -> [String: CalendarDateDropTargetFrame]) {
        value.merge(nextValue(), uniquingKeysWith: { _, newValue in newValue })
    }
}

private func calendarTaskAlreadyDueOnDate(_ todo: TodoItem, _ date: Date) -> Bool {
    Calendar.current.isDate(todo.due, inSameDayAs: date)
}

private enum CalendarTitleHandoff {
    static let collapseDistance: CGFloat = 180
    static let expandedTitleHeight: CGFloat = 56
    static let expandedTitleSpacerHeight: CGFloat = 14
    static let expandedFadeStart: CGFloat = 0.62
    static let expandedFadeEnd: CGFloat = 0.86
    static let collapsedFadeStart: CGFloat = 0.72
    static let collapsedFadeEnd: CGFloat = 0.96
    static let collapsedTitleRevealDistance: CGFloat = 10
    static let expandedTitleLiftDistance: CGFloat = 18
    static let sliderPartialSnapDistance: CGFloat = 58
}

private enum CalendarPeriodCardMetrics {
    static let contentSpacing: CGFloat = 14
    static let horizontalPadding: CGFloat = 16
    static let headerHeight: CGFloat = 36
    static let headerHorizontalPadding: CGFloat = 6
    static let pageHeight: CGFloat = 78
    static let weekDayCellHeight: CGFloat = 72
    static let pageHorizontalGutter: CGFloat = 2
    static let topPadding: CGFloat = 16
    static let bottomPadding: CGFloat = 18
}

private enum CalendarMonthGridMetrics {
    static let spacing: CGFloat = 8
    static let height: CGFloat = 292
    static let weekdayHeight: CGFloat = 18
    static let cardBottomPadding: CGFloat = 20
    static let dayCellHeight: CGFloat = 42
    static let dayHighlightWidth: CGFloat = 42
    static let dayHighlightHeight: CGFloat = 40
    static let dayNumberWidth: CGFloat = 34
    static let dayNumberHeight: CGFloat = 24
    static let taskCountHeight: CGFloat = 13
    static let taskDotSize: CGFloat = 4.6
    static let cellCornerRadius: CGFloat = 16
}

private enum CalendarTaskListMetrics {
    static let rowSpacing = TodoTimelineMetrics.sameDateTaskSpacing
    static let rowVerticalPadding = TodoTimelineMetrics.minimalRowVerticalPadding
}

private enum CalendarModeCardMetrics {
    static let shadowBleed: CGFloat = 12
    static let monthHeight = CalendarPeriodCardMetrics.topPadding
        + CalendarPeriodCardMetrics.headerHeight
        + CalendarPeriodCardMetrics.contentSpacing
        + CalendarMonthGridMetrics.weekdayHeight
        + CalendarMonthGridMetrics.spacing
        + CalendarMonthGridMetrics.height
        + CalendarMonthGridMetrics.cardBottomPadding
    static let periodHeight = CalendarPeriodCardMetrics.topPadding
        + CalendarPeriodCardMetrics.headerHeight
        + CalendarPeriodCardMetrics.contentSpacing
        + CalendarPeriodCardMetrics.pageHeight
        + CalendarPeriodCardMetrics.bottomPadding
}

private let calendarTodayTintColor = Color(red: 80.0 / 255.0, green: 154.0 / 255.0, blue: 230.0 / 255.0)
private let calendarModeResizeAnimation = Animation.spring(response: 0.34, dampingFraction: 0.92, blendDuration: 0.02)
private let calendarModeContentFadeAnimation = Animation.easeInOut(duration: 0.12)

private struct CalendarCardChromeModifier: ViewModifier {
    @Environment(\.tdayColors) private var colors

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: 24, style: .continuous)

        content
            .background(colors.surface, in: shape)
            .overlay {
                shape.stroke(cardStrokeColor, lineWidth: 1)
            }
            .shadow(color: ambientShadowColor, radius: 18, x: 0, y: 9)
            .shadow(color: keyShadowColor, radius: 4, x: 0, y: 2)
    }

    private var cardStrokeColor: Color {
        colors.isDark ? Color.white.opacity(0.08) : Color.black.opacity(0.035)
    }

    private var ambientShadowColor: Color {
        Color.black.opacity(colors.isDark ? 0.24 : 0.045)
    }

    private var keyShadowColor: Color {
        Color.black.opacity(colors.isDark ? 0.18 : 0.04)
    }
}

private struct CalendarTodayJumpRequest: Equatable {
    let id: Int
    let targetDate: Date
}

private struct CalendarTaskRescheduleDrop: Equatable {
    let todo: TodoItem
    let targetDate: Date
}

struct CalendarScreen: View {
    @State private var viewModel: CalendarViewModel
    @Environment(\.tdayColors) private var colors
    @Environment(\.dismiss) private var dismiss
    private let calendarAccentColor = Color(red: 125.0 / 255.0, green: 103.0 / 255.0, blue: 182.0 / 255.0)

    @State private var selectedDate = Date()
    @State private var visibleMonth = calendarMonthStart(for: Date())
    @State private var displayMode: CalendarDisplayMode = .month
    @State private var showingCreateTask = false
    @State private var editingTodo: TodoItem?
    @State private var calendarTitleCollapseOffset: CGFloat = 0
    @State private var todayJumpRequestID = 0
    @State private var todayJumpRequest: CalendarTodayJumpRequest?
    @State private var draggedTodo: TodoItem?
    @State private var inAppDrag: CalendarInAppDrag?
    @State private var activeDropDate: Date?
    @State private var dateDropTargetFrames: [String: CalendarDateDropTargetFrame] = [:]
    @State private var pendingRescheduleDrop: CalendarTaskRescheduleDrop?

    init(container: AppContainer) {
        _viewModel = State(initialValue: CalendarViewModel(container: container))
    }

    private var pendingItems: [TodoItem] {
        viewModel.items.filter { isSelectedDay($0.due) }.sorted(by: { $0.due < $1.due })
    }

    private var pendingItemsByDay: [Date: [TodoItem]] {
        Dictionary(grouping: viewModel.items) { todo in
            Calendar.current.startOfDay(for: todo.due)
        }
    }

    private var calendarTaskRescheduleEnabled: Bool {
        displayMode != .day
    }

    private var selectedDateHeaderText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "EEE, MMM d"
        return formatter.string(from: selectedDate)
    }

    private var calendarModeCardHeight: CGFloat {
        switch displayMode {
        case .month:
            return CalendarModeCardMetrics.monthHeight
        case .week, .day:
            return CalendarModeCardMetrics.periodHeight
        }
    }

    private var titleCollapseProgress: CGFloat {
        let distance = CalendarTitleHandoff.collapseDistance
        guard distance > 0 else { return 0 }
        return min(max(calendarTitleCollapseOffset / distance, 0), 1)
    }

    private var minimumNavigableMonth: Date {
        calendarMonthStart(for: Date())
    }

    private var canGoPreviousMonth: Bool {
        visibleMonth > minimumNavigableMonth
    }

    private var canGoPreviousWeek: Bool {
        guard let previousWeek = Calendar.current.date(byAdding: .day, value: -7, to: selectedDate) else {
            return false
        }
        return canNavigate(to: previousWeek)
    }

    private var canGoPreviousDay: Bool {
        guard let previousDay = Calendar.current.date(byAdding: .day, value: -1, to: selectedDate) else {
            return false
        }
        return canNavigate(to: previousDay)
    }

    var body: some View {
        List {
            Section {
                CalendarViewModeTabs(
                    selectedMode: displayMode,
                    accentColor: calendarAccentColor,
                    onSelect: { mode in
                        guard mode != displayMode else { return }
                        withAnimation(calendarModeResizeAnimation) {
                            displayMode = mode
                            if mode != .month {
                                visibleMonth = calendarMonthStart(for: selectedDate)
                            }
                        }
                    }
                )
                .background {
                    CalendarTitleCollapseScrollObserver(
                        collapseOffset: $calendarTitleCollapseOffset,
                        collapseDistance: CalendarTitleHandoff.collapseDistance,
                        sliderPartialSnapDistance: CalendarTitleHandoff.sliderPartialSnapDistance
                    )
                    .frame(width: 0, height: 0)
                }
                .listRowInsets(
                    EdgeInsets(
                        top: 0,
                        leading: TodoTimelineMetrics.horizontalPadding,
                        bottom: 14,
                        trailing: TodoTimelineMetrics.horizontalPadding
                    )
                )
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)

                animatedCalendarModeCard
                .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: CalendarModeCardMetrics.shadowBleed, trailing: TodoTimelineMetrics.horizontalPadding))
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
            }

            if let errorMessage = viewModel.errorMessage {
                Section {
                    ErrorRetryView(message: errorMessage) {
                        Task { await viewModel.refresh() }
                    }
                    .listRowBackground(Color.clear)
                }
            }

            Text("Tasks due \(selectedDateHeaderText)")
                .font(.tdayRounded(size: 22, weight: .heavy))
                .foregroundStyle(colors.onSurface)
                .textCase(nil)
                .listRowInsets(EdgeInsets(top: 8, leading: TodoTimelineMetrics.horizontalPadding, bottom: 4, trailing: TodoTimelineMetrics.horizontalPadding))
                .timelinePinnedSectionHeaderBackground()

            if !pendingItems.isEmpty {
                VStack(spacing: CalendarTaskListMetrics.rowSpacing) {
                    ForEach(pendingItems) { todo in
                        CalendarPendingTaskRow(
                            todo: todo,
                            list: todo.listId.flatMap { listId in
                                viewModel.lists.first(where: { $0.id == listId })
                            },
                            onComplete: { Task { await viewModel.complete(todo) } }
                        )
                        .opacity(draggedTodo?.id == todo.id && activeDropDate != nil ? 0.55 : 1)
                        .background(colors.background)
                        .modifier(
                            CalendarInAppDragModifier(
                                enabled: calendarTaskRescheduleEnabled,
                                todo: todo,
                                onStart: beginInAppDrag,
                                onMove: updateInAppDrag,
                                onEnd: finishInAppDrag,
                                onCancel: cancelInAppDrag
                            )
                        )
                        .todoTrailingSwipeActions(
                            onEdit: {
                                editingTodo = todo
                            },
                            onDelete: {
                                Task { await viewModel.delete(todo) }
                            }
                        )
                        .transition(.opacity.combined(with: .move(edge: .top)))
                    }
                }
                .animation(
                    .spring(response: 0.34, dampingFraction: 0.9),
                    value: pendingItems.map(\.id)
                )
                .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
                .listRowBackground(colors.background)
                .listRowSeparator(.hidden)
                .listSectionSeparator(.hidden)
            }
        }
        .listRowBackground(colors.background)
        .listRowSeparator(.hidden)
        .listSectionSeparator(.hidden)
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .contentMargins(.top, 0, for: .scrollContent)
        .listRowSpacing(0)
        .listSectionSpacing(0)
        .environment(\.defaultMinListRowHeight, 1)
        .animation(calendarModeResizeAnimation, value: calendarModeCardHeight)
        .disableVerticalScrollBounce()
        .background(colors.background)
        .onPreferenceChange(CalendarDateDropTargetFramePreferenceKey.self) { frames in
            dateDropTargetFrames = frames
        }
        .onChange(of: displayMode) { _, mode in
            if mode == .day {
                cancelInAppDrag()
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
                    CalendarTaskDragPreview(todo: inAppDrag.todo)
                        .position(x: previewLocation.x, y: previewLocation.y)
                        .zIndex(20)
                        .allowsHitTesting(false)
                }
            }
            .allowsHitTesting(false)
        }
        .navigationBackButtonBehavior()
        .navigationTitleTypography(
            largeTitleColor: calendarAccentColor,
            inlineTitleColor: colors.onSurface,
            backgroundColor: colors.background
        )
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .navigationBar)
        .safeAreaInset(edge: .top, spacing: 0) {
            calendarTopInset
        }
        .safeAreaInset(edge: .bottom) {
            TaskFloatingActionButtonDock(
                fillColor: calendarAccentColor,
                pressedShadowOpacity: 0.09,
                normalShadowOpacity: 0.16
            ) {
                showingCreateTask = true
            }
        }
        .sheet(isPresented: $showingCreateTask) {
            CreateTaskSheet(
                lists: viewModel.lists,
                titleText: "New task",
                submitText: "Create",
                initialPayload: CreateTaskPayload(title: "", description: nil, priority: "Low", due: selectedDate, rrule: nil, listId: nil),
                onParseTaskTitleNlp: { title, dueRef in
                    await viewModel.parseTaskTitleNlp(text: title, referenceDueEpochMs: dueRef)
                },
                onDismiss: { showingCreateTask = false },
                onSubmit: { payload in
                    await viewModel.createTask(payload)
                }
            )
        }
        .sheet(item: $editingTodo) { todo in
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

    private var calendarTopInset: some View {
        CalendarElasticTopBar(
            title: "Calendar",
            accentColor: calendarAccentColor,
            collapseProgress: titleCollapseProgress,
            onBack: { dismiss() },
            action: TimelineTopBarAction(
                systemName: "calendar",
                tint: calendarAccentColor,
                usesCircularChrome: true,
                action: jumpToToday
            ),
        )
    }

    private var animatedCalendarModeCard: some View {
        ZStack(alignment: .top) {
            calendarModeCard
                .id(displayMode)
                .transition(.opacity.animation(calendarModeContentFadeAnimation))
        }
        .frame(maxWidth: .infinity)
        .frame(height: calendarModeCardHeight, alignment: .top)
        .clipped()
        .modifier(CalendarCardChromeModifier())
        .animation(calendarModeResizeAnimation, value: calendarModeCardHeight)
    }

    private func isSelectedDay(_ date: Date) -> Bool {
        Calendar.current.isDate(date, inSameDayAs: selectedDate)
    }

    @ViewBuilder
    private var calendarModeCard: some View {
        switch displayMode {
        case .month:
            CalendarMonthGrid(
                visibleMonth: visibleMonth,
                selectedDate: selectedDate,
                tasksByDay: pendingItemsByDay,
                accentColor: calendarAccentColor,
                draggedTodo: draggedTodo,
                activeDropDate: activeDropDate,
                canGoPreviousMonth: canGoPreviousMonth,
                minimumNavigableMonth: minimumNavigableMonth,
                todayJumpRequest: todayJumpRequest,
                onPreviousMonth: { navigateMonth(by: -1) },
                onNextMonth: { navigateMonth(by: 1) },
                onSelectDate: { selectDate($0) },
                onDropDateChange: { activeDropDate = $0 },
                onMoveTaskToDate: { todo, date in requestReschedule(todo, to: date) },
                resolveTodo: resolveTodoForDrop
            )
        case .week:
            CalendarWeekCard(
                selectedDate: selectedDate,
                today: Date(),
                tasksByDay: pendingItemsByDay,
                accentColor: calendarAccentColor,
                draggedTodo: draggedTodo,
                activeDropDate: activeDropDate,
                canGoPreviousWeek: canGoPreviousWeek,
                canSelectDate: { canNavigate(to: $0) },
                todayJumpRequest: todayJumpRequest,
                onPreviousWeek: { navigateDay(by: -7) },
                onNextWeek: { navigateDay(by: 7) },
                onSelectDate: { selectDate($0) },
                onDropDateChange: { activeDropDate = $0 },
                onMoveTaskToDate: { todo, date in requestReschedule(todo, to: date) },
                resolveTodo: resolveTodoForDrop
            )
        case .day:
            CalendarDayCard(
                selectedDate: selectedDate,
                today: Date(),
                tasksByDay: pendingItemsByDay,
                accentColor: calendarAccentColor,
                canGoPreviousDay: canGoPreviousDay,
                canSelectDate: { canNavigate(to: $0) },
                todayJumpRequest: todayJumpRequest,
                onPreviousDay: { navigateDay(by: -1) },
                onNextDay: { navigateDay(by: 1) },
                onSelectDate: { selectDate($0) }
            )
        }
    }

    private func canNavigate(to date: Date) -> Bool {
        calendarMonthStart(for: date) >= minimumNavigableMonth
    }

    private func selectDate(_ date: Date) {
        guard canNavigate(to: date) else { return }
        selectedDate = date
        visibleMonth = calendarMonthStart(for: date)
    }

    private func navigateMonth(by value: Int) {
        guard value >= 0 || canGoPreviousMonth else { return }
        guard let targetMonth = Calendar.current.date(byAdding: .month, value: value, to: visibleMonth) else {
            return
        }
        visibleMonth = calendarMonthStart(for: targetMonth)
    }

    private func navigateDay(by value: Int) {
        guard let targetDate = Calendar.current.date(byAdding: .day, value: value, to: selectedDate) else {
            return
        }
        selectDate(targetDate)
    }

    private func jumpToToday() {
        todayJumpRequestID += 1
        todayJumpRequest = CalendarTodayJumpRequest(id: todayJumpRequestID, targetDate: Date())
    }

    private func requestReschedule(_ todo: TodoItem, to targetDate: Date) {
        draggedTodo = nil
        inAppDrag = nil
        activeDropDate = nil
        CalendarTaskDragSession.shared.todo = nil
        let targetDay = Calendar.current.startOfDay(for: targetDate)
        let dropSignature = "\(todo.id)|\(targetDay.timeIntervalSince1970)"
        guard CalendarTaskDragSession.shared.handledDropSignature != dropSignature else {
            return
        }
        CalendarTaskDragSession.shared.handledDropSignature = dropSignature
        guard !calendarTaskAlreadyDueOnDate(todo, targetDay) else {
            return
        }

        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        if todo.isRecurring {
            pendingRescheduleDrop = CalendarTaskRescheduleDrop(todo: todo, targetDate: targetDay)
        } else {
            Task {
                await viewModel.moveTask(todo, toDay: targetDay, scope: .occurrence)
                await MainActor.run {
                    selectDate(targetDay)
                }
            }
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
        CalendarTaskDragSession.shared.todo = todo
        CalendarTaskDragSession.shared.handledDropSignature = nil
        inAppDrag = CalendarInAppDrag(todo: todo, location: location)
        updateInAppDrag(todo, to: location)
    }

    private func updateInAppDrag(_ todo: TodoItem, to location: CGPoint) {
        inAppDrag = CalendarInAppDrag(todo: todo, location: location)
        activeDropDate = dropDate(at: location, for: todo)
    }

    private func finishInAppDrag(_ todo: TodoItem, at location: CGPoint?) {
        let fallbackDate = activeDropDate.flatMap { date in
            calendarTaskAlreadyDueOnDate(todo, date) ? nil : date
        }
        let targetDate = location.flatMap { dropDate(at: $0, for: todo) } ?? fallbackDate
        activeDropDate = nil
        draggedTodo = nil
        inAppDrag = nil
        if let targetDate {
            requestReschedule(todo, to: targetDate)
        } else {
            CalendarTaskDragSession.shared.todo = nil
        }
    }

    private func cancelInAppDrag() {
        activeDropDate = nil
        draggedTodo = nil
        inAppDrag = nil
        CalendarTaskDragSession.shared.todo = nil
    }

    private func dropDate(at location: CGPoint, for todo: TodoItem?) -> Date? {
        dateDropTargetFrames.values
            .filter { $0.frame.contains(location) }
            .filter { target in
                guard let todo else { return true }
                return !calendarTaskAlreadyDueOnDate(todo, target.date)
            }
            .min { lhs, rhs in
                (lhs.frame.width * lhs.frame.height) < (rhs.frame.width * rhs.frame.height)
            }
            .map { Calendar.current.startOfDay(for: $0.date) }
    }

    private func commitPendingReschedule(scope: TaskRescheduleScope) {
        guard let drop = pendingRescheduleDrop else {
            return
        }
        pendingRescheduleDrop = nil
        Task {
            await viewModel.moveTask(drop.todo, toDay: drop.targetDate, scope: scope)
            await MainActor.run {
                selectDate(drop.targetDate)
            }
        }
    }
}

private struct CalendarViewModeTabs: View {
    let selectedMode: CalendarDisplayMode
    let accentColor: Color
    let onSelect: (CalendarDisplayMode) -> Void

    var body: some View {
        let modes = CalendarDisplayMode.allCases

        TdayNativeSegmentedControl(
            labels: modes.map { $0.rawValue.capitalized },
            selectedIndex: modes.firstIndex(of: selectedMode) ?? 0,
            accentColor: accentColor,
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

private struct CalendarMonthGrid: View {
    let visibleMonth: Date
    let selectedDate: Date
    let tasksByDay: [Date: [TodoItem]]
    let accentColor: Color
    let draggedTodo: TodoItem?
    let activeDropDate: Date?
    let canGoPreviousMonth: Bool
    let minimumNavigableMonth: Date
    let todayJumpRequest: CalendarTodayJumpRequest?
    let onPreviousMonth: () -> Void
    let onNextMonth: () -> Void
    let onSelectDate: (Date) -> Void
    let onDropDateChange: (Date?) -> Void
    let onMoveTaskToDate: (TodoItem, Date) -> Void
    let resolveTodo: (String) -> TodoItem?

    @Environment(\.tdayColors) private var colors
    @State private var pageSelection = calendarNativePagerCenterIndex
    @State private var pendingTodayJump: CalendarTodayJumpRequest?

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 0), count: 7)

    private func monthTitle(for month: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMMM yyyy"
        return formatter.string(from: month)
    }

    private var weekdaySymbols: [String] {
        Calendar.current.veryShortStandaloneWeekdaySymbols.map { $0.uppercased() }
    }

    var body: some View {
        let displayMonth = calendarMonthStart(for: visibleMonth)
        monthContent(for: displayMonth)
            .onChange(of: todayJumpRequest) { _, request in handleTodayJump(request, from: displayMonth) }
            .onChange(of: displayMonth) { _, _ in resetPageSelection() }
    }

    private func monthContent(for displayMonth: Date) -> some View {
        let canGoPrevious = calendarMonthStart(for: displayMonth) > minimumNavigableMonth
        let previousMonth = canGoPrevious ? calendarMonth(byAdding: -1, to: displayMonth) : nil
        let nextMonth = calendarMonth(byAdding: 1, to: displayMonth)
        let jumpDirection = todayJumpDirection(from: displayMonth)
        let previousPageMonth = jumpDirection == .previous ? pendingTodayJump.map { calendarMonthStart(for: $0.targetDate) } : previousMonth
        let nextPageMonth = jumpDirection == .next ? pendingTodayJump.map { calendarMonthStart(for: $0.targetDate) } : nextMonth
        let isPagingAtRest = pageSelection == calendarNativePagerCenterIndex
        let isPreviousEnabled = canGoPrevious && isPagingAtRest
        let isNextEnabled = isPagingAtRest
        return VStack(spacing: CalendarPeriodCardMetrics.contentSpacing) {
            HStack {
                CalendarNavButton(
                    systemName: "chevron.left",
                    isEnabled: isPreviousEnabled,
                    color: colors.onSurfaceVariant,
                    action: goToPreviousPage
                )

                Spacer(minLength: 0)

                Text(monthTitle(for: displayMonth))
                    .font(.tdayRounded(size: 21, weight: .heavy))
                    .foregroundStyle(colors.onSurface)

                Spacer(minLength: 0)

                CalendarNavButton(
                    systemName: "chevron.right",
                    isEnabled: isNextEnabled,
                    color: colors.onSurfaceVariant,
                    action: goToNextPage
                )
            }
            .frame(height: CalendarPeriodCardMetrics.headerHeight)
            .padding(.horizontal, CalendarPeriodCardMetrics.headerHorizontalPadding)

            VStack(spacing: CalendarMonthGridMetrics.spacing) {
                HStack(spacing: 0) {
                    ForEach(Array(weekdaySymbols.enumerated()), id: \.offset) { _, symbol in
                        Text(symbol)
                            .font(.tdayRounded(size: 12, weight: .heavy))
                            .foregroundStyle(colors.onSurfaceVariant.opacity(0.48))
                            .frame(maxWidth: .infinity)
                            .frame(height: CalendarMonthGridMetrics.weekdayHeight)
                    }
                }

                CalendarPagingScrollView(
                    pages: monthPages(
                        previousMonth: previousPageMonth,
                        displayMonth: displayMonth,
                        nextMonth: nextPageMonth
                    ),
                    selection: $pageSelection,
                    onSettledSelection: settlePageSelection
                )
                .frame(height: CalendarMonthGridMetrics.height)
            }
        }
        .padding(.horizontal, CalendarPeriodCardMetrics.horizontalPadding)
        .padding(.top, CalendarPeriodCardMetrics.topPadding)
        .padding(.bottom, CalendarMonthGridMetrics.cardBottomPadding)
        .frame(maxWidth: .infinity)
    }

    private func monthGrid(for displayMonth: Date) -> some View {
        LazyVGrid(columns: columns, spacing: CalendarMonthGridMetrics.spacing) {
            ForEach(Self.makeDays(for: displayMonth)) { day in
                let dayTasks = tasksByDay[Calendar.current.startOfDay(for: day.date)].orEmpty
                let dropEligibleDraggedTodo = draggedTodo.flatMap { todo in
                    calendarTaskAlreadyDueOnDate(todo, day.date) ? nil : todo
                }
                CalendarMonthDayCell(
                    day: day,
                    isSelected: Calendar.current.isDate(day.date, inSameDayAs: selectedDate),
                    isToday: Calendar.current.isDateInToday(day.date),
                    isEnabled: canSelectDate(day.date),
                    isDropTarget: (activeDropDate.map { Calendar.current.isDate($0, inSameDayAs: day.date) } ?? false) &&
                        (draggedTodo == nil || dropEligibleDraggedTodo != nil),
                    taskCount: dayTasks.count,
                    accentColor: accentColor,
                    draggedTodo: dropEligibleDraggedTodo,
                    onSelectDate: onSelectDate,
                    onDropDateChange: onDropDateChange,
                    onMoveTaskToDate: onMoveTaskToDate,
                    resolveTodo: resolveTodo
                )
            }
        }
    }

    private func canSelectDate(_ date: Date) -> Bool {
        calendarMonthStart(for: date) >= minimumNavigableMonth
    }

    private func monthPages(previousMonth: Date?, displayMonth: Date, nextMonth: Date?) -> [CalendarPagerPage] {
        var pages: [CalendarPagerPage] = []

        if let previousMonth {
            pages.append(CalendarPagerPage(id: 0, content: AnyView(monthGrid(for: previousMonth))))
        }

        pages.append(CalendarPagerPage(id: calendarNativePagerCenterIndex, content: AnyView(monthGrid(for: displayMonth))))

        if let nextMonth {
            pages.append(CalendarPagerPage(id: 2, content: AnyView(monthGrid(for: nextMonth))))
        }

        return pages
    }

    private func resetPageSelection() {
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            pageSelection = calendarNativePagerCenterIndex
        }
    }

    private func goToPreviousPage() {
        guard pageSelection == calendarNativePagerCenterIndex else { return }
        pageSelection = 0
    }

    private func goToNextPage() {
        guard pageSelection == calendarNativePagerCenterIndex else { return }
        pageSelection = 2
    }

    private func settlePageSelection(_ selection: Int) {
        guard selection != calendarNativePagerCenterIndex else { return }
        if let pendingTodayJump {
            self.pendingTodayJump = nil
            onSelectDate(pendingTodayJump.targetDate)
            resetPageSelection()
            return
        }

        if selection < calendarNativePagerCenterIndex {
            onPreviousMonth()
        } else {
            onNextMonth()
        }
        resetPageSelection()
    }

    private func handleTodayJump(_ request: CalendarTodayJumpRequest?, from displayMonth: Date) {
        guard let request else { return }
        guard pageSelection == calendarNativePagerCenterIndex else { return }

        let targetMonth = calendarMonthStart(for: request.targetDate)
        guard targetMonth != displayMonth else {
            onSelectDate(request.targetDate)
            resetPageSelection()
            return
        }

        pendingTodayJump = request
        pageSelection = targetMonth < displayMonth ? CalendarPagerDirection.previous.pageIndex : CalendarPagerDirection.next.pageIndex
    }

    private func todayJumpDirection(from displayMonth: Date) -> CalendarPagerDirection? {
        guard let pendingTodayJump else { return nil }
        let targetMonth = calendarMonthStart(for: pendingTodayJump.targetDate)
        if targetMonth < displayMonth {
            return .previous
        }
        if targetMonth > displayMonth {
            return .next
        }
        return nil
    }

    private static func makeDays(for month: Date) -> [CalendarMonthDay] {
        let calendar = Calendar.current
        let monthStartComponents = calendar.dateComponents([.year, .month], from: month)
        let monthStart = calendar.date(from: monthStartComponents) ?? month
        let firstWeekday = calendar.component(.weekday, from: monthStart)
        let leadingDays = firstWeekday - 1
        let gridStart = calendar.date(byAdding: .day, value: -leadingDays, to: monthStart) ?? monthStart

        return (0..<42).compactMap { offset in
            guard let date = calendar.date(byAdding: .day, value: offset, to: gridStart) else {
                return nil
            }
            return CalendarMonthDay(
                date: date,
                isCurrentMonth: calendar.isDate(date, equalTo: monthStart, toGranularity: .month)
            )
        }
    }
}

private struct CalendarWeekCard: View {
    let selectedDate: Date
    let today: Date
    let tasksByDay: [Date: [TodoItem]]
    let accentColor: Color
    let draggedTodo: TodoItem?
    let activeDropDate: Date?
    let canGoPreviousWeek: Bool
    let canSelectDate: (Date) -> Bool
    let todayJumpRequest: CalendarTodayJumpRequest?
    let onPreviousWeek: () -> Void
    let onNextWeek: () -> Void
    let onSelectDate: (Date) -> Void
    let onDropDateChange: (Date?) -> Void
    let onMoveTaskToDate: (TodoItem, Date) -> Void
    let resolveTodo: (String) -> TodoItem?

    @Environment(\.tdayColors) private var colors
    @State private var pageSelection = calendarNativePagerCenterIndex
    @State private var pendingTodayJump: CalendarTodayJumpRequest?

    var body: some View {
        let displaySelectedDate = Calendar.current.startOfDay(for: selectedDate)
        weekContent(for: displaySelectedDate)
            .onChange(of: todayJumpRequest) { _, request in handleTodayJump(request, from: displaySelectedDate) }
            .onChange(of: displaySelectedDate) { _, _ in resetPageSelection() }
    }

    private func weekContent(for displaySelectedDate: Date) -> some View {
        let weekStart = calendarStartOfWeek(for: displaySelectedDate)
        let previousWeekDate = calendarDate(byAddingDays: -7, to: displaySelectedDate)
        let nextWeekDate = calendarDate(byAddingDays: 7, to: displaySelectedDate)
        let canGoPrevious = previousWeekDate.map(canSelectDate) ?? false
        let jumpDirection = todayJumpDirection(from: displaySelectedDate)
        let previousPageWeekDate = jumpDirection == .previous ? pendingTodayJump?.targetDate : previousWeekDate
        let nextPageWeekDate = jumpDirection == .next ? pendingTodayJump?.targetDate : nextWeekDate
        let isPagingAtRest = pageSelection == calendarNativePagerCenterIndex
        return VStack(spacing: CalendarPeriodCardMetrics.contentSpacing) {
            HStack {
                CalendarNavButton(
                    systemName: "chevron.left",
                    isEnabled: canGoPrevious && isPagingAtRest,
                    color: colors.onSurfaceVariant,
                    action: goToPreviousPage
                )

                Spacer(minLength: 0)

                Text(calendarWeekRangeText(from: weekStart))
                    .font(.tdayRounded(size: 21, weight: .heavy))
                    .foregroundStyle(colors.onSurface)
                    .lineLimit(1)
                    .minimumScaleFactor(0.82)

                Spacer(minLength: 0)

                CalendarNavButton(
                    systemName: "chevron.right",
                    isEnabled: isPagingAtRest,
                    color: colors.onSurfaceVariant,
                    action: goToNextPage
                )
            }
            .frame(height: CalendarPeriodCardMetrics.headerHeight)
            .padding(.horizontal, CalendarPeriodCardMetrics.headerHorizontalPadding)

            CalendarPagingScrollView(
                pages: weekPages(
                    previousWeekDate: jumpDirection == .previous ? previousPageWeekDate : (canGoPrevious ? previousPageWeekDate : nil),
                    displaySelectedDate: displaySelectedDate,
                    nextWeekDate: nextPageWeekDate
                ),
                selection: $pageSelection,
                onSettledSelection: settlePageSelection
            )
            .frame(height: CalendarPeriodCardMetrics.pageHeight)
        }
        .padding(.horizontal, CalendarPeriodCardMetrics.horizontalPadding)
        .padding(.top, CalendarPeriodCardMetrics.topPadding)
        .padding(.bottom, CalendarPeriodCardMetrics.bottomPadding)
        .frame(maxWidth: .infinity)
    }

    private func weekDaysRow(for displaySelectedDate: Date) -> some View {
        let weekStart = calendarStartOfWeek(for: displaySelectedDate)
        let weekDays = (0..<7).compactMap { Calendar.current.date(byAdding: .day, value: $0, to: weekStart) }

        return HStack(spacing: 6) {
            ForEach(weekDays, id: \.self) { date in
                let normalizedDate = Calendar.current.startOfDay(for: date)
                let taskCount = tasksByDay[normalizedDate].orEmpty.count
                let isEnabled = canSelectDate(date)
                let dropEligibleDraggedTodo = draggedTodo.flatMap { todo in
                    calendarTaskAlreadyDueOnDate(todo, date) ? nil : todo
                }
                CalendarWeekDayCell(
                    date: date,
                    taskCount: taskCount,
                    isSelected: Calendar.current.isDate(date, inSameDayAs: displaySelectedDate),
                    isToday: Calendar.current.isDate(date, inSameDayAs: today),
                    isEnabled: isEnabled,
                    accentColor: accentColor,
                    isDropTarget: (activeDropDate.map { Calendar.current.isDate($0, inSameDayAs: date) } ?? false) &&
                        (draggedTodo == nil || dropEligibleDraggedTodo != nil),
                    draggedTodo: dropEligibleDraggedTodo,
                    onSelect: { onSelectDate(date) },
                    onDropDateChange: onDropDateChange,
                    onMoveTaskToDate: onMoveTaskToDate,
                    resolveTodo: resolveTodo
                )
            }
        }
        .padding(.horizontal, CalendarPeriodCardMetrics.pageHorizontalGutter)
    }

    private func weekPages(previousWeekDate: Date?, displaySelectedDate: Date, nextWeekDate: Date?) -> [CalendarPagerPage] {
        var pages: [CalendarPagerPage] = []

        if let previousWeekDate {
            pages.append(CalendarPagerPage(id: 0, content: AnyView(weekDaysRow(for: previousWeekDate))))
        }

        pages.append(CalendarPagerPage(id: calendarNativePagerCenterIndex, content: AnyView(weekDaysRow(for: displaySelectedDate))))

        if let nextWeekDate {
            pages.append(CalendarPagerPage(id: 2, content: AnyView(weekDaysRow(for: nextWeekDate))))
        }

        return pages
    }

    private func resetPageSelection() {
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            pageSelection = calendarNativePagerCenterIndex
        }
    }

    private func goToPreviousPage() {
        guard pageSelection == calendarNativePagerCenterIndex else { return }
        pageSelection = 0
    }

    private func goToNextPage() {
        guard pageSelection == calendarNativePagerCenterIndex else { return }
        pageSelection = 2
    }

    private func settlePageSelection(_ selection: Int) {
        guard selection != calendarNativePagerCenterIndex else { return }
        if let pendingTodayJump {
            self.pendingTodayJump = nil
            onSelectDate(pendingTodayJump.targetDate)
            resetPageSelection()
            return
        }

        if selection < calendarNativePagerCenterIndex {
            onPreviousWeek()
        } else {
            onNextWeek()
        }
        resetPageSelection()
    }

    private func handleTodayJump(_ request: CalendarTodayJumpRequest?, from displaySelectedDate: Date) {
        guard let request else { return }
        guard pageSelection == calendarNativePagerCenterIndex else { return }

        let currentWeek = calendarStartOfWeek(for: displaySelectedDate)
        let targetWeek = calendarStartOfWeek(for: request.targetDate)
        guard targetWeek != currentWeek else {
            onSelectDate(request.targetDate)
            resetPageSelection()
            return
        }

        pendingTodayJump = request
        pageSelection = targetWeek < currentWeek ? CalendarPagerDirection.previous.pageIndex : CalendarPagerDirection.next.pageIndex
    }

    private func todayJumpDirection(from displaySelectedDate: Date) -> CalendarPagerDirection? {
        guard let pendingTodayJump else { return nil }
        let currentWeek = calendarStartOfWeek(for: displaySelectedDate)
        let targetWeek = calendarStartOfWeek(for: pendingTodayJump.targetDate)
        if targetWeek < currentWeek {
            return .previous
        }
        if targetWeek > currentWeek {
            return .next
        }
        return nil
    }
}

private struct CalendarWeekDayCell: View {
    let date: Date
    let taskCount: Int
    let isSelected: Bool
    let isToday: Bool
    let isEnabled: Bool
    let accentColor: Color
    let isDropTarget: Bool
    let draggedTodo: TodoItem?
    let onSelect: () -> Void
    let onDropDateChange: (Date?) -> Void
    let onMoveTaskToDate: (TodoItem, Date) -> Void
    let resolveTodo: (String) -> TodoItem?

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: onSelect) {
            VStack(spacing: 4) {
                Text(weekdayText)
                    .font(.tdayRounded(size: 13, weight: .heavy))
                    .foregroundStyle(colors.onSurfaceVariant.opacity(isEnabled ? 0.88 : 0.38))
                    .lineLimit(1)
                    .minimumScaleFactor(0.76)

                Text(dayText)
                    .font(.tdayRounded(size: 20, weight: .heavy))
                    .foregroundStyle(dayTextColor)

                Text(calendarTaskCountText(taskCount))
                    .font(.tdayRounded(size: 12, weight: .heavy))
                    .foregroundStyle(taskCount > 0 ? stateTint : colors.onSurfaceVariant.opacity(0.42))
            }
            .frame(maxWidth: .infinity)
            .frame(height: CalendarPeriodCardMetrics.weekDayCellHeight)
            .background(cellBackground, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(cellBorderColor, lineWidth: cellBorderWidth)
            }
            .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .frame(maxWidth: .infinity)
            .frame(height: CalendarPeriodCardMetrics.pageHeight)
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .calendarTaskDropTarget(
            date: date,
            canDrop: isEnabled,
            draggedTodo: draggedTodo,
            resolveTodo: resolveTodo,
            onMove: onMoveTaskToDate,
            onDateChange: onDropDateChange
        )
        .calendarInAppDateDropTargetFrame(date: date, enabled: isEnabled && draggedTodo != nil)
        .opacity(isEnabled ? 1 : 0.48)
    }

    private var weekdayText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "EEE"
        return formatter.string(from: date)
    }

    private var dayText: String {
        String(Calendar.current.component(.day, from: date))
    }

    private var cellBackground: Color {
        if isDropTarget {
            return colors.error.opacity(0.20)
        }
        if isSelected {
            return accentColor.opacity(0.24)
        }
        if isToday {
            return calendarTodayTintColor.opacity(0.16)
        }
        return colors.background
    }

    private var cellBorderColor: Color {
        if isDropTarget {
            return colors.error
        }
        if isSelected {
            return accentColor.opacity(0.95)
        }
        if isToday {
            return calendarTodayTintColor.opacity(0.74)
        }
        return .clear
    }

    private var cellBorderWidth: CGFloat {
        if isDropTarget {
            return 2
        }
        if isSelected {
            return 1.6
        }
        if isToday {
            return 1.4
        }
        return 0
    }

    private var dayTextColor: Color {
        if isDropTarget {
            return colors.error
        }
        if isSelected {
            return accentColor
        }
        if isToday {
            return calendarTodayTintColor
        }
        return colors.onSurface
    }

    private var stateTint: Color {
        if isDropTarget {
            return colors.error
        }
        if isSelected {
            return accentColor
        }
        if isToday {
            return calendarTodayTintColor
        }
        return accentColor
    }
}

private struct CalendarDateDropDelegate: DropDelegate {
    let date: Date
    let canDrop: Bool
    let draggedTodo: TodoItem?
    let resolveTodo: (String) -> TodoItem?
    let onMove: (TodoItem, Date) -> Void
    let onDateChange: (Date?) -> Void

    func validateDrop(info: DropInfo) -> Bool {
        guard canDrop,
              info.hasItemsConforming(to: calendarTaskDragContentTypes) else {
            return false
        }
        if let todo = draggedTodo ?? CalendarTaskDragSession.shared.todo {
            return canMove(todo)
        }
        return true
    }

    func dropEntered(info: DropInfo) {
        if validateDrop(info: info) {
            onDateChange(Calendar.current.startOfDay(for: date))
        }
    }

    func dropExited(info: DropInfo) {
        onDateChange(nil)
    }

    func dropUpdated(info: DropInfo) -> DropProposal? {
        DropProposal(operation: .move)
    }

    func performDrop(info: DropInfo) -> Bool {
        defer {
            onDateChange(nil)
        }
        guard let draggedTodo = draggedTodo ?? CalendarTaskDragSession.shared.todo else {
            return performProviderDrop(info: info)
        }
        guard canMove(draggedTodo) else {
            return false
        }
        onMove(draggedTodo, Calendar.current.startOfDay(for: date))
        return true
    }

    private func performProviderDrop(info: DropInfo) -> Bool {
        guard canDrop,
              let provider = info.itemProviders(for: calendarTaskDragContentTypes).first else {
            return false
        }
        let targetDate = Calendar.current.startOfDay(for: date)
        provider.loadObject(ofClass: NSString.self) { object, _ in
            guard let rawId = object as? NSString else {
                return
            }
            let todoId = rawId as String
            DispatchQueue.main.async {
                if let todo = resolveTodo(todoId), canMove(todo) {
                    onMove(todo, targetDate)
                }
            }
        }
        return true
    }

    private func canMove(_ todo: TodoItem) -> Bool {
        !calendarTaskAlreadyDueOnDate(todo, date)
    }
}

private struct CalendarInAppDateDropTargetFrameModifier: ViewModifier {
    let date: Date
    let enabled: Bool

    @ViewBuilder
    func body(content: Content) -> some View {
        content.background {
            if enabled {
                GeometryReader { proxy in
                    Color.clear.preference(
                        key: CalendarDateDropTargetFramePreferenceKey.self,
                        value: [
                            String(Calendar.current.startOfDay(for: date).timeIntervalSince1970): CalendarDateDropTargetFrame(
                                date: Calendar.current.startOfDay(for: date),
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
    func calendarInAppDateDropTargetFrame(date: Date, enabled: Bool) -> some View {
        modifier(CalendarInAppDateDropTargetFrameModifier(date: date, enabled: enabled))
    }

    func calendarTaskDropTarget(
        date: Date,
        canDrop: Bool,
        draggedTodo: TodoItem?,
        resolveTodo: @escaping (String) -> TodoItem?,
        onMove: @escaping (TodoItem, Date) -> Void,
        onDateChange: @escaping (Date?) -> Void
    ) -> some View {
        self
            .onDrop(
                of: calendarTaskDragContentTypes,
                delegate: CalendarDateDropDelegate(
                    date: date,
                    canDrop: canDrop,
                    draggedTodo: draggedTodo,
                    resolveTodo: resolveTodo,
                    onMove: onMove,
                    onDateChange: onDateChange
                )
            )
            .dropDestination(for: String.self) { ids, _ in
                guard canDrop else {
                    onDateChange(nil)
                    return false
                }
                let targetDate = Calendar.current.startOfDay(for: date)
                let todo = draggedTodo
                    ?? CalendarTaskDragSession.shared.todo
                    ?? ids.compactMap(resolveTodo).first
                guard let todo else {
                    onDateChange(nil)
                    return false
                }
                guard !calendarTaskAlreadyDueOnDate(todo, targetDate) else {
                    onDateChange(nil)
                    return false
                }
                onDateChange(nil)
                onMove(todo, targetDate)
                return true
            } isTargeted: { active in
                guard canDrop else {
                    if !active {
                        onDateChange(nil)
                    }
                    return
                }
                if active,
                   let todo = draggedTodo ?? CalendarTaskDragSession.shared.todo,
                   calendarTaskAlreadyDueOnDate(todo, date) {
                    onDateChange(nil)
                    return
                }
                onDateChange(active ? Calendar.current.startOfDay(for: date) : nil)
            }
    }
}

private struct CalendarDayCard: View {
    let selectedDate: Date
    let today: Date
    let tasksByDay: [Date: [TodoItem]]
    let accentColor: Color
    let canGoPreviousDay: Bool
    let canSelectDate: (Date) -> Bool
    let todayJumpRequest: CalendarTodayJumpRequest?
    let onPreviousDay: () -> Void
    let onNextDay: () -> Void
    let onSelectDate: (Date) -> Void

    @Environment(\.tdayColors) private var colors
    @State private var pageSelection = calendarNativePagerCenterIndex
    @State private var pendingTodayJump: CalendarTodayJumpRequest?

    var body: some View {
        let displayDate = Calendar.current.startOfDay(for: selectedDate)
        dayContent(for: displayDate)
            .onChange(of: todayJumpRequest) { _, request in handleTodayJump(request, from: displayDate) }
            .onChange(of: displayDate) { _, _ in resetPageSelection() }
    }

    private func dayContent(for displayDate: Date) -> some View {
        let previousDay = calendarDate(byAddingDays: -1, to: displayDate)
        let nextDay = calendarDate(byAddingDays: 1, to: displayDate)
        let canGoPrevious = previousDay.map(canSelectDate) ?? false
        let jumpDirection = todayJumpDirection(from: displayDate)
        let previousPageDay = jumpDirection == .previous ? pendingTodayJump.map { Calendar.current.startOfDay(for: $0.targetDate) } : previousDay
        let nextPageDay = jumpDirection == .next ? pendingTodayJump.map { Calendar.current.startOfDay(for: $0.targetDate) } : nextDay
        let isPagingAtRest = pageSelection == calendarNativePagerCenterIndex

        return VStack(alignment: .leading, spacing: CalendarPeriodCardMetrics.contentSpacing) {
            HStack {
                CalendarNavButton(
                    systemName: "chevron.left",
                    isEnabled: canGoPrevious && isPagingAtRest,
                    color: colors.onSurfaceVariant,
                    action: goToPreviousPage
                )

                Spacer(minLength: 0)

                Text(weekdayTitle(for: displayDate))
                    .font(.tdayRounded(size: 21, weight: .heavy))
                    .foregroundStyle(colors.onSurface)

                Spacer(minLength: 0)

                CalendarNavButton(
                    systemName: "chevron.right",
                    isEnabled: isPagingAtRest,
                    color: colors.onSurfaceVariant,
                    action: goToNextPage
                )
            }
            .frame(height: CalendarPeriodCardMetrics.headerHeight)
            .padding(.horizontal, CalendarPeriodCardMetrics.headerHorizontalPadding)

            CalendarPagingScrollView(
                pages: dayPages(
                    previousDay: jumpDirection == .previous ? previousPageDay : (canGoPrevious ? previousPageDay : nil),
                    displayDate: displayDate,
                    nextDay: nextPageDay
                ),
                selection: $pageSelection,
                onSettledSelection: settlePageSelection
            )
            .frame(height: CalendarPeriodCardMetrics.pageHeight)
        }
        .padding(.horizontal, CalendarPeriodCardMetrics.horizontalPadding)
        .padding(.top, CalendarPeriodCardMetrics.topPadding)
        .padding(.bottom, CalendarPeriodCardMetrics.bottomPadding)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func dayPages(previousDay: Date?, displayDate: Date, nextDay: Date?) -> [CalendarPagerPage] {
        var pages: [CalendarPagerPage] = []

        if let previousDay {
            pages.append(CalendarPagerPage(id: 0, content: AnyView(daySummary(for: previousDay))))
        }

        pages.append(CalendarPagerPage(id: calendarNativePagerCenterIndex, content: AnyView(daySummary(for: displayDate))))

        if let nextDay {
            pages.append(CalendarPagerPage(id: 2, content: AnyView(daySummary(for: nextDay))))
        }

        return pages
    }

    private func daySummary(for date: Date) -> some View {
        return VStack(alignment: .leading, spacing: 14) {
            Text(dateTitle(for: date))
                .font(.tdayRounded(size: 25, weight: .heavy))
                .foregroundStyle(
                    Calendar.current.isDate(date, inSameDayAs: today) ? accentColor : colors.onSurface
                )

            Text(taskCountText(for: date))
                .font(.tdayRounded(size: 18, weight: .heavy))
                .foregroundStyle(colors.onSurfaceVariant)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 4)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func resetPageSelection() {
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            pageSelection = calendarNativePagerCenterIndex
        }
    }

    private func goToPreviousPage() {
        guard pageSelection == calendarNativePagerCenterIndex else { return }
        pageSelection = 0
    }

    private func goToNextPage() {
        guard pageSelection == calendarNativePagerCenterIndex else { return }
        pageSelection = 2
    }

    private func settlePageSelection(_ selection: Int) {
        guard selection != calendarNativePagerCenterIndex else { return }
        if let pendingTodayJump {
            self.pendingTodayJump = nil
            onSelectDate(pendingTodayJump.targetDate)
            resetPageSelection()
            return
        }

        if selection < calendarNativePagerCenterIndex {
            onPreviousDay()
        } else {
            onNextDay()
        }
        resetPageSelection()
    }

    private func handleTodayJump(_ request: CalendarTodayJumpRequest?, from displayDate: Date) {
        guard let request else { return }
        guard pageSelection == calendarNativePagerCenterIndex else { return }

        let targetDate = Calendar.current.startOfDay(for: request.targetDate)
        guard targetDate != displayDate else {
            onSelectDate(request.targetDate)
            resetPageSelection()
            return
        }

        pendingTodayJump = request
        pageSelection = targetDate < displayDate ? CalendarPagerDirection.previous.pageIndex : CalendarPagerDirection.next.pageIndex
    }

    private func todayJumpDirection(from displayDate: Date) -> CalendarPagerDirection? {
        guard let pendingTodayJump else { return nil }
        let targetDate = Calendar.current.startOfDay(for: pendingTodayJump.targetDate)
        if targetDate < displayDate {
            return .previous
        }
        if targetDate > displayDate {
            return .next
        }
        return nil
    }

    private func weekdayTitle(for date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "EEEE"
        return formatter.string(from: date)
    }

    private func dateTitle(for date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMMM d, yyyy"
        return formatter.string(from: date)
    }

    private func taskCountText(for date: Date) -> String {
        let taskCount = tasksByDay[Calendar.current.startOfDay(for: date)].orEmpty.count
        return taskCount == 1 ? "1 task due" : "\(taskCount) tasks due"
    }
}

private struct CalendarNavButton: View {
    let systemName: String
    let isEnabled: Bool
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.tdayRounded(size: 19, weight: .heavy))
                .foregroundStyle(isEnabled ? color : color.opacity(0.34))
                .frame(width: 40, height: 36)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
    }
}

private struct CalendarMonthDayCell: View {
    let day: CalendarMonthDay
    let isSelected: Bool
    let isToday: Bool
    let isEnabled: Bool
    let isDropTarget: Bool
    let taskCount: Int
    let accentColor: Color
    let draggedTodo: TodoItem?
    let onSelectDate: (Date) -> Void
    let onDropDateChange: (Date?) -> Void
    let onMoveTaskToDate: (TodoItem, Date) -> Void
    let resolveTodo: (String) -> TodoItem?

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button {
            onSelectDate(day.date)
        } label: {
            VStack(spacing: 1) {
                Text(dayNumberText)
                    .font(.tdayRounded(size: 19, weight: .bold))
                    .foregroundStyle(dayTextColor)
                    .frame(
                        width: CalendarMonthGridMetrics.dayNumberWidth,
                        height: CalendarMonthGridMetrics.dayNumberHeight
                    )

                HStack(spacing: 2) {
                    if taskCount > 0 {
                        Circle()
                            .fill(stateTint)
                            .frame(
                                width: CalendarMonthGridMetrics.taskDotSize,
                                height: CalendarMonthGridMetrics.taskDotSize
                            )

                        Text(calendarTaskCountText(taskCount))
                            .font(.tdayRounded(size: 11, weight: .heavy))
                            .lineLimit(1)
                            .minimumScaleFactor(0.82)
                            .foregroundStyle(stateTint)
                    }
                }
                .frame(height: CalendarMonthGridMetrics.taskCountHeight)
            }
            .frame(
                width: CalendarMonthGridMetrics.dayHighlightWidth,
                height: CalendarMonthGridMetrics.dayHighlightHeight
            )
            .background(
                cellBackground,
                in: RoundedRectangle(cornerRadius: CalendarMonthGridMetrics.cellCornerRadius, style: .continuous)
            )
            .overlay {
                RoundedRectangle(cornerRadius: CalendarMonthGridMetrics.cellCornerRadius, style: .continuous)
                    .stroke(cellBorderColor, lineWidth: cellBorderWidth)
            }
            .contentShape(RoundedRectangle(cornerRadius: CalendarMonthGridMetrics.cellCornerRadius, style: .continuous))
            .frame(maxWidth: .infinity)
            .frame(height: CalendarMonthGridMetrics.dayCellHeight)
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .calendarTaskDropTarget(
            date: day.date,
            canDrop: isEnabled,
            draggedTodo: draggedTodo,
            resolveTodo: resolveTodo,
            onMove: onMoveTaskToDate,
            onDateChange: onDropDateChange
        )
        .calendarInAppDateDropTargetFrame(date: day.date, enabled: isEnabled && draggedTodo != nil)
        .opacity(day.isCurrentMonth ? 1 : 0.45)
    }

    private var dayNumberText: String {
        String(Calendar.current.component(.day, from: day.date))
    }

    private var dayTextColor: Color {
        if isDropTarget {
            return colors.error
        }
        if !day.isCurrentMonth {
            return colors.onSurfaceVariant.opacity(0.48)
        }
        if isSelected || isToday {
            return stateTint
        }
        return colors.onSurface
    }

    private var cellBackground: Color {
        if isDropTarget {
            return colors.error.opacity(0.20)
        }
        if isSelected {
            return accentColor.opacity(0.24)
        }
        if isToday {
            return calendarTodayTintColor.opacity(0.16)
        }
        return .clear
    }

    private var cellBorderColor: Color {
        if isDropTarget {
            return colors.error
        }
        if isSelected {
            return accentColor.opacity(0.95)
        }
        if isToday {
            return calendarTodayTintColor.opacity(0.74)
        }
        return .clear
    }

    private var cellBorderWidth: CGFloat {
        if isDropTarget {
            return 2
        }
        if isSelected {
            return 1.6
        }
        if isToday {
            return 1.4
        }
        return 0
    }

    private var stateTint: Color {
        if isDropTarget {
            return colors.error
        }
        if isSelected {
            return accentColor
        }
        if isToday {
            return calendarTodayTintColor
        }
        return accentColor
    }
}

private struct CalendarMonthDay: Identifiable {
    let date: Date
    let isCurrentMonth: Bool

    var id: Date { date }
}

private func calendarMonthStart(for date: Date) -> Date {
    let calendar = Calendar.current
    let components = calendar.dateComponents([.year, .month], from: date)
    return calendar.date(from: components) ?? calendar.startOfDay(for: date)
}

private func calendarMonth(byAdding value: Int, to month: Date) -> Date? {
    Calendar.current
        .date(byAdding: .month, value: value, to: calendarMonthStart(for: month))
        .map { calendarMonthStart(for: $0) }
}

private func calendarDate(byAddingDays value: Int, to date: Date) -> Date? {
    Calendar.current.date(byAdding: .day, value: value, to: Calendar.current.startOfDay(for: date))
}

private func calendarStartOfWeek(for date: Date) -> Date {
    let calendar = Calendar.current
    let dayStart = calendar.startOfDay(for: date)
    let sundayOffset = calendar.component(.weekday, from: dayStart) - 1
    return calendar.date(byAdding: .day, value: -sundayOffset, to: dayStart) ?? dayStart
}

private func calendarWeekRangeText(from weekStart: Date) -> String {
    let calendar = Calendar.current
    let weekEnd = calendar.date(byAdding: .day, value: 6, to: weekStart) ?? weekStart
    let sameMonth = calendar.isDate(weekStart, equalTo: weekEnd, toGranularity: .month)
    let sameYear = calendar.isDate(weekStart, equalTo: weekEnd, toGranularity: .year)

    let monthDayFormatter = DateFormatter()
    monthDayFormatter.dateFormat = "MMM d"

    if sameMonth, sameYear {
        let monthFormatter = DateFormatter()
        monthFormatter.dateFormat = "MMM"
        let yearFormatter = DateFormatter()
        yearFormatter.dateFormat = "yyyy"
        let month = monthFormatter.string(from: weekStart)
        let startDay = calendar.component(.day, from: weekStart)
        let endDay = calendar.component(.day, from: weekEnd)
        return "\(month) \(startDay)-\(endDay), \(yearFormatter.string(from: weekEnd))"
    }

    if sameYear {
        let yearFormatter = DateFormatter()
        yearFormatter.dateFormat = "yyyy"
        return "\(monthDayFormatter.string(from: weekStart)) - \(monthDayFormatter.string(from: weekEnd)), \(yearFormatter.string(from: weekEnd))"
    }

    let fullFormatter = DateFormatter()
    fullFormatter.dateFormat = "MMM d, yyyy"
    return "\(fullFormatter.string(from: weekStart)) - \(fullFormatter.string(from: weekEnd))"
}

private func calendarTaskCountText(_ count: Int) -> String {
    count > 9 ? "9+" : "\(count)"
}

private extension Optional where Wrapped == [TodoItem] {
    var orEmpty: [TodoItem] {
        self ?? []
    }
}

private struct CalendarElasticTopBar: View {
    let title: String
    let accentColor: Color
    let collapseProgress: CGFloat
    let onBack: () -> Void
    let action: TimelineTopBarAction?

    private var progress: CGFloat {
        min(max(collapseProgress, 0), 1)
    }

    private var expandedTitleHeight: CGFloat {
        CalendarTitleHandoff.expandedTitleHeight * (1 - progress)
    }

    private var expandedSpacerHeight: CGFloat {
        CalendarTitleHandoff.expandedTitleSpacerHeight * (1 - progress)
    }

    private var expandedTitleOpacity: Double {
        let fade = linearProgress(
            progress,
            from: CalendarTitleHandoff.expandedFadeStart,
            to: CalendarTitleHandoff.expandedFadeEnd
        )
        return Double(1 - fade)
    }

    private var collapsedTitleOpacity: Double {
        Double(
            linearProgress(
                progress,
                from: CalendarTitleHandoff.collapsedFadeStart,
                to: CalendarTitleHandoff.collapsedFadeEnd
            )
        )
    }

    private var collapsedTitleOffsetY: CGFloat {
        CalendarTitleHandoff.collapsedTitleRevealDistance * (1 - CGFloat(collapsedTitleOpacity))
    }

    private var expandedTitleOffsetY: CGFloat {
        -CalendarTitleHandoff.expandedTitleLiftDistance * progress
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack {
                HStack(spacing: 0) {
                    CalendarTopBarButton(systemName: "chevron.left", chrome: .filled, action: onBack)
                    Spacer(minLength: 0)
                    if let action {
                        CalendarTopBarButton(
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

                Text(title)
                    .font(.tdayRounded(size: TodoTimelineMetrics.heroTitleSize, weight: .heavy))
                    .foregroundStyle(accentColor)
                    .lineLimit(1)
                    .opacity(collapsedTitleOpacity)
                    .offset(y: collapsedTitleOffsetY)
                    .scaleEffect(0.985 + (0.015 * CGFloat(collapsedTitleOpacity)))
                    .padding(.horizontal, TodoTimelineMetrics.topBarButtonFrame + 12)
                    .frame(maxWidth: .infinity)
                    .allowsHitTesting(false)
            }
            .frame(height: TodoTimelineMetrics.topBarRowHeight)

            Color.clear
                .frame(height: expandedSpacerHeight)

            ZStack(alignment: .bottomLeading) {
                Text(title)
                    .font(.tdayRounded(size: TodoTimelineMetrics.heroTitleSize, weight: .heavy))
                    .foregroundStyle(accentColor)
                    .lineLimit(1)
                    .opacity(expandedTitleOpacity)
                    .offset(y: expandedTitleOffsetY)
            }
            .frame(
                maxWidth: .infinity,
                minHeight: expandedTitleHeight,
                maxHeight: expandedTitleHeight,
                alignment: .bottomLeading
            )
            .clipped()
        }
        .padding(.horizontal, TodoTimelineMetrics.horizontalPadding)
        .padding(.top, 2)
        .padding(.bottom, 2)
        .background(colors.background)
    }

    @Environment(\.tdayColors) private var colors

    private func linearProgress(_ value: CGFloat, from start: CGFloat, to end: CGFloat) -> CGFloat {
        guard end > start else { return value >= end ? 1 : 0 }
        return min(max((value - start) / (end - start), 0), 1)
    }
}

private struct CalendarTopBarButton: View {
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
        .buttonStyle(
            TdayPressButtonStyle(
                shadowColor: .black,
                pressedShadowOpacity: chrome == .filled ? 0.09 : 0,
                normalShadowOpacity: chrome == .filled ? 0.15 : 0
            )
        )
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
        return colors.onSurface.opacity(0.2)
    }
}

private struct CalendarTitleCollapseScrollObserver: UIViewRepresentable {
    @Binding var collapseOffset: CGFloat
    let collapseDistance: CGFloat
    let sliderPartialSnapDistance: CGFloat

    func makeCoordinator() -> Coordinator {
        Coordinator(collapseOffset: collapseOffset)
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.update(
            collapseOffset: collapseOffset,
            collapseDistance: collapseDistance,
            sliderPartialSnapDistance: sliderPartialSnapDistance,
            onChange: { value, animated in
                if animated {
                    withAnimation(.spring(response: 0.34, dampingFraction: 0.88)) {
                        collapseOffset = value
                    }
                } else {
                    collapseOffset = value
                }
            }
        )
        DispatchQueue.main.async {
            context.coordinator.attach(to: uiView)
        }
    }

    final class Coordinator: NSObject {
        private var collapseOffset: CGFloat
        private var collapseDistance: CGFloat = 0
        private var sliderPartialSnapDistance: CGFloat = 0
        private var onChange: ((CGFloat, Bool) -> Void)?
        private weak var observedScrollView: UIScrollView?
        private var offsetObservation: NSKeyValueObservation?
        private var snapTimer: Timer?
        private var lastTranslationY: CGFloat = 0
        private var releaseVelocityY: CGFloat = 0
        private var isAdjustingScrollOffset = false

        init(collapseOffset: CGFloat) {
            self.collapseOffset = collapseOffset
        }

        deinit {
            snapTimer?.invalidate()
        }

        func update(
            collapseOffset: CGFloat,
            collapseDistance: CGFloat,
            sliderPartialSnapDistance: CGFloat,
            onChange: @escaping (CGFloat, Bool) -> Void
        ) {
            self.collapseOffset = collapseOffset
            self.collapseDistance = collapseDistance
            self.sliderPartialSnapDistance = sliderPartialSnapDistance
            self.onChange = onChange
        }

        func attach(to view: UIView) {
            guard let scrollView = view.calendarEnclosingScrollView() else {
                return
            }

            guard observedScrollView !== scrollView else {
                return
            }

            observedScrollView = scrollView
            scrollView.panGestureRecognizer.addTarget(self, action: #selector(handlePan(_:)))
            offsetObservation = scrollView.observe(\.contentOffset, options: .new) { [weak self] scrollView, _ in
                self?.clampListScrollIfTitleIsConsuming(scrollView)
            }
        }

        @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
            guard let scrollView = observedScrollView else {
                return
            }

            let translationY = gesture.translation(in: scrollView).y

            switch gesture.state {
            case .began:
                snapTimer?.invalidate()
                lastTranslationY = translationY
                releaseVelocityY = 0
            case .changed:
                let deltaY = translationY - lastTranslationY
                lastTranslationY = translationY
                consume(deltaY: deltaY, in: scrollView)
            case .ended, .cancelled, .failed:
                releaseVelocityY = gesture.velocity(in: scrollView).y
                scheduleSnapCheck(for: scrollView)
            default:
                break
            }
        }

        private func consume(deltaY: CGFloat, in scrollView: UIScrollView) {
            guard collapseDistance > 0 else {
                return
            }

            if deltaY < 0 {
                let previous = collapseOffset
                let next = min(max(previous - deltaY, 0), collapseDistance)
                if next > previous {
                    setCollapseOffset(next, animated: false)
                    holdListAtTop(scrollView)
                }
                return
            }

            if deltaY > 0 {
                guard isListAtTop(scrollView), collapseOffset > 0 else {
                    return
                }
                let previous = collapseOffset
                let next = min(max(previous - deltaY, 0), collapseDistance)
                if next < previous {
                    setCollapseOffset(next, animated: false)
                    holdListAtTop(scrollView)
                }
            }
        }

        private func scheduleSnapCheck(for scrollView: UIScrollView) {
            snapTimer?.invalidate()
            snapTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self, weak scrollView] timer in
                guard let self, let scrollView else {
                    timer.invalidate()
                    return
                }
                if !scrollView.isTracking && !scrollView.isDragging && !scrollView.isDecelerating {
                    timer.invalidate()
                    self.snapTitleCollapseAndSlider(in: scrollView)
                }
            }
        }

        private func snapTitleCollapseAndSlider(in scrollView: UIScrollView) {
            snapTitleCollapse(in: scrollView)
            snapSliderIfPartiallyVisible(in: scrollView)
        }

        private func snapTitleCollapse(in scrollView: UIScrollView) {
            guard collapseDistance > 0 else {
                return
            }
            let bounded = min(max(collapseOffset, 0), collapseDistance)
            guard bounded > 0, bounded < collapseDistance else {
                return
            }

            let target: CGFloat
            if !isListAtTop(scrollView) {
                target = collapseDistance
            } else if releaseVelocityY < -1 {
                target = collapseDistance
            } else if releaseVelocityY > 1 {
                target = 0
            } else {
                target = (bounded / collapseDistance) >= 0.5 ? collapseDistance : 0
            }

            if isListAtTop(scrollView) {
                stopScrollMotion(scrollView)
                holdListAtTop(scrollView)
            }
            setCollapseOffset(target, animated: true)
        }

        private func snapSliderIfPartiallyVisible(in scrollView: UIScrollView) {
            guard sliderPartialSnapDistance > 0 else {
                return
            }
            guard collapseOffset >= collapseDistance - 0.5 else {
                return
            }

            let offset = normalizedOffset(for: scrollView)
            guard offset > 0.5, offset < sliderPartialSnapDistance else {
                return
            }

            animateListToTop(scrollView)
        }

        private func clampListScrollIfTitleIsConsuming(_ scrollView: UIScrollView) {
            guard !isAdjustingScrollOffset else {
                return
            }
            guard collapseDistance > 0 else {
                return
            }
            guard collapseOffset < collapseDistance - 0.5 else {
                return
            }
            guard normalizedOffset(for: scrollView) > 0.5 else {
                return
            }
            holdListAtTop(scrollView)
        }

        private func setCollapseOffset(_ offset: CGFloat, animated: Bool) {
            let bounded = min(max(offset, 0), collapseDistance)
            guard abs(collapseOffset - bounded) > 0.1 else {
                return
            }
            collapseOffset = bounded
            onChange?(bounded, animated)
        }

        private func isListAtTop(_ scrollView: UIScrollView) -> Bool {
            normalizedOffset(for: scrollView) <= 0.5
        }

        private func normalizedOffset(for scrollView: UIScrollView) -> CGFloat {
            max(scrollView.contentOffset.y + scrollView.adjustedContentInset.top, 0)
        }

        private func holdListAtTop(_ scrollView: UIScrollView) {
            let topY = -scrollView.adjustedContentInset.top
            guard abs(scrollView.contentOffset.y - topY) > 0.1 else {
                return
            }
            isAdjustingScrollOffset = true
            scrollView.setContentOffset(CGPoint(x: scrollView.contentOffset.x, y: topY), animated: false)
            isAdjustingScrollOffset = false
        }

        private func animateListToTop(_ scrollView: UIScrollView) {
            let topY = -scrollView.adjustedContentInset.top
            guard abs(scrollView.contentOffset.y - topY) > 0.5 else {
                return
            }

            isAdjustingScrollOffset = true
            UIView.animate(
                withDuration: 0.24,
                delay: 0,
                options: [.allowUserInteraction, .beginFromCurrentState, .curveEaseInOut]
            ) {
                scrollView.setContentOffset(CGPoint(x: scrollView.contentOffset.x, y: topY), animated: false)
            } completion: { [weak self] _ in
                self?.isAdjustingScrollOffset = false
            }
        }

        private func stopScrollMotion(_ scrollView: UIScrollView) {
            scrollView.setContentOffset(scrollView.contentOffset, animated: false)
            let wasScrollEnabled = scrollView.isScrollEnabled
            scrollView.isScrollEnabled = false
            scrollView.isScrollEnabled = wasScrollEnabled
        }
    }
}

private extension UIView {
    func calendarEnclosingScrollView() -> UIScrollView? {
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

private struct CalendarInAppDragModifier: ViewModifier {
    let enabled: Bool
    let todo: TodoItem
    let onStart: (TodoItem, CGPoint) -> Void
    let onMove: (TodoItem, CGPoint) -> Void
    let onEnd: (TodoItem, CGPoint?) -> Void
    let onCancel: () -> Void

    func body(content: Content) -> some View {
        if enabled {
            content.background {
                GeometryReader { _ in
                    CalendarInAppLongPressBridge(
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

private struct CalendarInAppLongPressBridge: UIViewRepresentable {
    let todo: TodoItem
    let onStart: (TodoItem, CGPoint) -> Void
    let onMove: (TodoItem, CGPoint) -> Void
    let onEnd: (TodoItem, CGPoint?) -> Void
    let onCancel: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(
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
        context.coordinator.todo = todo
        context.coordinator.onStart = onStart
        context.coordinator.onMove = onMove
        context.coordinator.onEnd = onEnd
        context.coordinator.onCancel = onCancel
        DispatchQueue.main.async {
            context.coordinator.attach(to: uiView.calendarEnclosingScrollView() ?? uiView.superview, markerView: uiView)
        }
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        coordinator.detach()
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
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
            todo: TodoItem,
            onStart: @escaping (TodoItem, CGPoint) -> Void,
            onMove: @escaping (TodoItem, CGPoint) -> Void,
            onEnd: @escaping (TodoItem, CGPoint?) -> Void,
            onCancel: @escaping () -> Void
        ) {
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
            guard let view else {
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
            guard let markerView else {
                return false
            }

            let localPoint = gestureRecognizer.location(in: markerView)
            return markerView.bounds.insetBy(dx: -6, dy: -6).contains(localPoint)
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
            guard let markerView else {
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

private struct CalendarTaskDragPreview: View {
    let todo: TodoItem

    @Environment(\.tdayColors) private var colors

    var body: some View {
        let previewShape = RoundedRectangle(cornerRadius: 18, style: .continuous)

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

            if let priorityIcon = priorityIndicatorSymbolName(todo.priority) {
                Image(systemName: priorityIcon)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(priorityColor(todo.priority))
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .frame(width: 260, alignment: .leading)
        .background(colors.surface)
        .clipShape(previewShape)
        .overlay(
            previewShape.stroke(colors.onSurfaceVariant.opacity(0.14), lineWidth: 1)
        )
        .contentShape(previewShape)
        .compositingGroup()
        .shadow(color: Color.black.opacity(0.18), radius: 16, x: 0, y: 8)
        .opacity(0.96)
    }
}

private struct CalendarPendingTaskRow: View {
    let todo: TodoItem
    let list: ListSummary?
    let onComplete: () -> Void

    @Environment(\.tdayColors) private var colors
    @State private var completionPhase = CalendarTaskCompletionPhase.active

    private var showCheckmark: Bool {
        completionPhase != .active || todo.completed
    }

    private var showStrikethrough: Bool {
        completionPhase == .struck || completionPhase == .fading || todo.completed
    }

    private var isCompleting: Bool {
        completionPhase != .active
    }

    private var isFading: Bool {
        completionPhase == .fading
    }

    var body: some View {
        let priorityIcon = priorityIndicatorSymbolName(todo.priority)

        VStack(spacing: 0) {
            HStack(alignment: .center, spacing: 12) {
                Button(action: startCompletion) {
                    Image(systemName: showCheckmark ? "checkmark.circle.fill" : "circle")
                        .font(.system(size: TodoTimelineMetrics.minimalRowToggleSize, weight: .regular))
                        .foregroundStyle(showCheckmark ? Color.green : colors.onSurfaceVariant.opacity(0.78))
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
                    TodoTimelineTaskTitle(
                        text: todo.title,
                        isCompleted: showStrikethrough,
                        titleColor: showStrikethrough ? colors.onSurface.opacity(0.78) : colors.onSurface,
                        strikeColor: colors.onSurface.opacity(0.65)
                    )

                    Text(todo.due.formatted(date: .omitted, time: .shortened))
                        .font(.tdayRounded(size: TodoTimelineMetrics.minimalRowSubtitleSize, weight: .semibold))
                        .foregroundStyle(colors.onSurfaceVariant.opacity(0.8))
                }

                Spacer(minLength: 0)

                if list != nil || priorityIcon != nil {
                    HStack(spacing: 8) {
                        if let list {
                            Image(systemName: calendarListSymbolName(for: list.iconKey))
                                .font(.system(size: TodoTimelineMetrics.minimalRowIndicatorSize, weight: .semibold))
                                .foregroundStyle(calendarListAccentColor(for: list.color))
                        }
                        if let priorityIcon {
                            Image(systemName: priorityIcon)
                                .font(.system(size: TodoTimelineMetrics.minimalRowIndicatorSize, weight: .semibold))
                                .foregroundStyle(priorityColor(todo.priority))
                        }
                    }
                    .padding(.trailing, TodoTimelineMetrics.minimalRowTrailingIndicatorPadding)
                }
            }
            .padding(.vertical, CalendarTaskListMetrics.rowVerticalPadding)
            .contentShape(Rectangle())
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colors.background)
        .opacity(isFading ? 0 : 1)
        .scaleEffect(isFading ? 0.985 : 1, anchor: .center)
        .offset(y: isFading ? -10 : 0)
        .animation(.easeInOut(duration: 0.26), value: isFading)
        .allowsHitTesting(!isCompleting)
    }

    private func startCompletion() {
        guard completionPhase == .active else {
            return
        }

        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        Task { @MainActor in
            withAnimation(.easeInOut(duration: 0.18)) {
                completionPhase = .checked
            }
            try? await Task.sleep(nanoseconds: 160_000_000)
            withAnimation(.easeInOut(duration: 0.22)) {
                completionPhase = .struck
            }
            try? await Task.sleep(nanoseconds: 360_000_000)
            withAnimation(.easeInOut(duration: 0.26)) {
                completionPhase = .fading
            }
            try? await Task.sleep(nanoseconds: 260_000_000)
            onComplete()
            if completionPhase == .fading {
                completionPhase = .active
            }
        }
    }
}

private func calendarListAccentColor(for key: String?) -> Color {
    switch key {
    case "PINK":
        return calendarHexColor(0xC987A5)
    case "GOLD":
        return calendarHexColor(0xC7AA63)
    case "DEEP_BLUE":
        return calendarHexColor(0x6F86C6)
    case "CORAL":
        return calendarHexColor(0xD39A82)
    case "TEAL":
        return calendarHexColor(0x67AAA7)
    case "SLATE", "GRAY":
        return calendarHexColor(0x7F8996)
    case "BLUE":
        return calendarHexColor(0x6F9FCE)
    case "PURPLE":
        return calendarHexColor(0x9A86CF)
    case "ROSE":
        return calendarHexColor(0xC98299)
    case "LIGHT_RED":
        return calendarHexColor(0xD58D8D)
    case "BRICK":
        return calendarHexColor(0xAD786E)
    case "YELLOW":
        return calendarHexColor(0xCFB866)
    case "LIME", "GREEN":
        return calendarHexColor(0x8DBB73)
    case "ORANGE":
        return calendarHexColor(0xD69B63)
    case "RED":
        return calendarHexColor(0xD97873)
    default:
        return calendarHexColor(0xC987A5)
    }
}

private func calendarListSymbolName(for key: String?) -> String {
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

private func calendarHexColor(_ hex: UInt) -> Color {
    Color(
        .sRGB,
        red: Double((hex >> 16) & 0xFF) / 255,
        green: Double((hex >> 8) & 0xFF) / 255,
        blue: Double(hex & 0xFF) / 255,
        opacity: 1
    )
}
