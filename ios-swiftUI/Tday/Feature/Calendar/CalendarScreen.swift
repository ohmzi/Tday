import SwiftUI
import UIKit
import UniformTypeIdentifiers

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
    static let dayCellHeight: CGFloat = 42
    static let dayHighlightWidth: CGFloat = 42
    static let dayHighlightHeight: CGFloat = 40
    static let dayNumberWidth: CGFloat = 34
    static let dayNumberHeight: CGFloat = 24
    static let taskCountHeight: CGFloat = 13
    static let taskDotSize: CGFloat = 4.6
    static let cellCornerRadius: CGFloat = 16
}

private let calendarTodayTintColor = Color(red: 80.0 / 255.0, green: 154.0 / 255.0, blue: 230.0 / 255.0)

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
    @State private var activeDropDate: Date?
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

    private var selectedDateHeaderText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "EEE, MMM d"
        return formatter.string(from: selectedDate)
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
                        withAnimation(.easeInOut(duration: 0.2)) {
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

                calendarModeCard
                .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
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

            Section {
                if !pendingItems.isEmpty {
                    ForEach(Array(pendingItems.enumerated()), id: \.element.id) { index, todo in
                        CalendarPendingTaskRow(
                            todo: todo,
                            onComplete: { Task { await viewModel.complete(todo) } }
                        )
                        .opacity(draggedTodo?.id == todo.id && activeDropDate != nil ? 0.55 : 1)
                        .onDrag {
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            draggedTodo = todo
                            return NSItemProvider(object: todo.id as NSString)
                        }
                        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                        .todoTrailingSwipeActions(
                            onEdit: {
                                editingTodo = todo
                            },
                            onDelete: {
                                Task { await viewModel.delete(todo) }
                            }
                        )
                        .swipeActions(edge: .leading, allowsFullSwipe: true) {
                            Button {
                                Task { await viewModel.complete(todo) }
                            } label: {
                                Label("Complete", systemImage: "checkmark")
                            }
                            .tint(.green)
                        }
                        if shouldShowDateDivider(after: index, in: pendingItems) {
                            TimelineRowDivider()
                        }
                    }
                }
            } header: {
                Text("Tasks due \(selectedDateHeaderText)")
                    .font(.tdayRounded(size: 22, weight: .heavy))
                    .foregroundStyle(colors.onSurface)
                    .textCase(nil)
                    .listRowInsets(EdgeInsets(top: 8, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
                    .timelinePinnedSectionHeaderBackground()
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .contentMargins(.top, 0, for: .scrollContent)
        .listSectionSpacing(0)
        .environment(\.defaultMinListRowHeight, 1)
        .disableVerticalScrollBounce()
        .background(colors.background)
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
            TaskFloatingActionButtonDock(fillColor: calendarAccentColor) {
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

    private func isSelectedDay(_ date: Date) -> Bool {
        Calendar.current.isDate(date, inSameDayAs: selectedDate)
    }

    private func shouldShowDateDivider(after index: Int, in items: [TodoItem]) -> Bool {
        guard items.indices.contains(index),
              items.indices.contains(index + 1) else {
            return false
        }
        return !Calendar.current.isDate(items[index].due, inSameDayAs: items[index + 1].due)
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
                onMoveTaskToDate: { todo, date in requestReschedule(todo, to: date) }
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
                onMoveTaskToDate: { todo, date in requestReschedule(todo, to: date) }
            )
        case .day:
            CalendarDayCard(
                selectedDate: selectedDate,
                today: Date(),
                tasksByDay: pendingItemsByDay,
                accentColor: calendarAccentColor,
                draggedTodo: draggedTodo,
                activeDropDate: activeDropDate,
                canGoPreviousDay: canGoPreviousDay,
                canSelectDate: { canNavigate(to: $0) },
                todayJumpRequest: todayJumpRequest,
                onPreviousDay: { navigateDay(by: -1) },
                onNextDay: { navigateDay(by: 1) },
                onSelectDate: { selectDate($0) },
                onDropDateChange: { activeDropDate = $0 },
                onMoveTaskToDate: { todo, date in requestReschedule(todo, to: date) }
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
        activeDropDate = nil
        let targetDay = Calendar.current.startOfDay(for: targetDate)
        guard !Calendar.current.isDate(todo.due, inSameDayAs: targetDay) else {
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
            .background(colors.surface, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .shadow(color: Color.black.opacity(0.08), radius: 10, x: 0, y: 5)
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
                            .frame(height: 18)
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
        .padding(.bottom, 20)
        .frame(maxWidth: .infinity)
    }

    private func monthGrid(for displayMonth: Date) -> some View {
        LazyVGrid(columns: columns, spacing: CalendarMonthGridMetrics.spacing) {
            ForEach(Self.makeDays(for: displayMonth)) { day in
                let dayTasks = tasksByDay[Calendar.current.startOfDay(for: day.date)].orEmpty
                CalendarMonthDayCell(
                    day: day,
                    isSelected: Calendar.current.isDate(day.date, inSameDayAs: selectedDate),
                    isToday: Calendar.current.isDateInToday(day.date),
                    isEnabled: canSelectDate(day.date),
                    isDropTarget: activeDropDate.map { Calendar.current.isDate($0, inSameDayAs: day.date) } ?? false,
                    taskCount: dayTasks.count,
                    accentColor: accentColor,
                    draggedTodo: draggedTodo,
                    onSelectDate: onSelectDate,
                    onDropDateChange: onDropDateChange,
                    onMoveTaskToDate: onMoveTaskToDate
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

    @Environment(\.tdayColors) private var colors
    @State private var pageSelection = calendarNativePagerCenterIndex
    @State private var pendingTodayJump: CalendarTodayJumpRequest?

    var body: some View {
        let displaySelectedDate = Calendar.current.startOfDay(for: selectedDate)
        weekContent(for: displaySelectedDate)
            .onChange(of: todayJumpRequest) { _, request in handleTodayJump(request, from: displaySelectedDate) }
            .onChange(of: displaySelectedDate) { _, _ in resetPageSelection() }
            .background(colors.surface, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .shadow(color: Color.black.opacity(0.08), radius: 10, x: 0, y: 5)
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
                CalendarWeekDayCell(
                    date: date,
                    taskCount: taskCount,
                    isSelected: Calendar.current.isDate(date, inSameDayAs: displaySelectedDate),
                    isToday: Calendar.current.isDate(date, inSameDayAs: today),
                    isEnabled: isEnabled,
                    accentColor: accentColor,
                    isDropTarget: activeDropDate.map { Calendar.current.isDate($0, inSameDayAs: date) } ?? false,
                    draggedTodo: draggedTodo,
                    onSelect: { onSelectDate(date) },
                    onDropDateChange: onDropDateChange,
                    onMoveTaskToDate: onMoveTaskToDate
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
        .onDrop(
            of: [UTType.plainText.identifier],
            delegate: CalendarDateDropDelegate(
                date: date,
                draggedTodo: isEnabled ? draggedTodo : nil,
                onMove: onMoveTaskToDate,
                onDateChange: onDropDateChange
            )
        )
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
            return accentColor.opacity(0.34)
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
            return accentColor
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
        if isSelected {
            return accentColor
        }
        if isToday {
            return calendarTodayTintColor
        }
        return colors.onSurface
    }

    private var stateTint: Color {
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
    let draggedTodo: TodoItem?
    let onMove: (TodoItem, Date) -> Void
    let onDateChange: (Date?) -> Void

    func validateDrop(info: DropInfo) -> Bool {
        draggedTodo != nil
    }

    func dropEntered(info: DropInfo) {
        onDateChange(Calendar.current.startOfDay(for: date))
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
        guard let draggedTodo else {
            return false
        }
        onMove(draggedTodo, Calendar.current.startOfDay(for: date))
        return true
    }
}

private struct CalendarDayCard: View {
    let selectedDate: Date
    let today: Date
    let tasksByDay: [Date: [TodoItem]]
    let accentColor: Color
    let draggedTodo: TodoItem?
    let activeDropDate: Date?
    let canGoPreviousDay: Bool
    let canSelectDate: (Date) -> Bool
    let todayJumpRequest: CalendarTodayJumpRequest?
    let onPreviousDay: () -> Void
    let onNextDay: () -> Void
    let onSelectDate: (Date) -> Void
    let onDropDateChange: (Date?) -> Void
    let onMoveTaskToDate: (TodoItem, Date) -> Void

    @Environment(\.tdayColors) private var colors
    @State private var pageSelection = calendarNativePagerCenterIndex
    @State private var pendingTodayJump: CalendarTodayJumpRequest?

    var body: some View {
        let displayDate = Calendar.current.startOfDay(for: selectedDate)
        dayContent(for: displayDate)
            .onChange(of: todayJumpRequest) { _, request in handleTodayJump(request, from: displayDate) }
            .onChange(of: displayDate) { _, _ in resetPageSelection() }
            .background(colors.surface, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .shadow(color: Color.black.opacity(0.08), radius: 10, x: 0, y: 5)
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
        let isEnabled = canSelectDate(date)
        let isDropTarget = activeDropDate.map { Calendar.current.isDate($0, inSameDayAs: date) } ?? false
        return VStack(alignment: .leading, spacing: 14) {
            Text(dateTitle(for: date))
                .font(.tdayRounded(size: 25, weight: .heavy))
                .foregroundStyle(Calendar.current.isDate(date, inSameDayAs: today) ? accentColor : colors.onSurface)

            Text(taskCountText(for: date))
                .font(.tdayRounded(size: 18, weight: .heavy))
                .foregroundStyle(colors.onSurfaceVariant)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 4)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            isDropTarget ? accentColor.opacity(0.12) : .clear,
            in: RoundedRectangle(cornerRadius: 16, style: .continuous)
        )
        .onDrop(
            of: [UTType.plainText.identifier],
            delegate: CalendarDateDropDelegate(
                date: date,
                draggedTodo: isEnabled ? draggedTodo : nil,
                onMove: onMoveTaskToDate,
                onDateChange: onDropDateChange
            )
        )
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
        .onDrop(
            of: [UTType.plainText.identifier],
            delegate: CalendarDateDropDelegate(
                date: day.date,
                draggedTodo: isEnabled ? draggedTodo : nil,
                onMove: onMoveTaskToDate,
                onDateChange: onDropDateChange
            )
        )
        .opacity(day.isCurrentMonth ? 1 : 0.45)
    }

    private var dayNumberText: String {
        String(Calendar.current.component(.day, from: day.date))
    }

    private var dayTextColor: Color {
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
            return accentColor.opacity(0.34)
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
            return accentColor
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
                pressedShadowOpacity: chrome == .filled ? 0.14 : 0,
                normalShadowOpacity: chrome == .filled ? 0.24 : 0
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

private struct CalendarPendingTaskRow: View {
    let todo: TodoItem
    let onComplete: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        VStack(spacing: 0) {
            HStack(alignment: .center, spacing: 12) {
                Button(action: onComplete) {
                    Image(systemName: "circle")
                        .font(.system(size: TodoTimelineMetrics.minimalRowToggleSize, weight: .regular))
                        .foregroundStyle(colors.onSurfaceVariant.opacity(0.78))
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

                    Text(todo.due.formatted(date: .omitted, time: .shortened))
                        .font(.tdayRounded(size: TodoTimelineMetrics.minimalRowSubtitleSize, weight: .semibold))
                        .foregroundStyle(colors.onSurfaceVariant.opacity(0.8))
                }

                Spacer(minLength: 0)
            }
            .padding(.vertical, TodoTimelineMetrics.minimalRowVerticalPadding)
            .contentShape(Rectangle())
        }
    }
}
