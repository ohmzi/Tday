import SwiftUI

private enum CalendarTitleHandoff {
    static let pinnedRevealStart: CGFloat = 0.18
    static let pinnedRevealEnd: CGFloat = 0.62
}

private enum CalendarPageDirection {
    case previous
    case next
}

private struct CalendarPagerActions {
    let previous: () -> Void
    let next: () -> Void
}

private let calendarPageSettleDuration: TimeInterval = 0.24
private let calendarPageSettleAnimation = Animation.interactiveSpring(response: 0.28, dampingFraction: 0.86, blendDuration: 0.05)
private let calendarSelectionAnimation = Animation.easeInOut(duration: 0.16)

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
    @State private var calendarScrollOffset: CGFloat = 0

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
        let distance = TodoTimelineMetrics.titleCollapseDistance
        guard distance > 0 else { return 0 }
        return min(max(calendarScrollOffset / distance, 0), 1)
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
            calendarHeroTitleRow

            Section {
                CalendarViewModeTabs(
                    selectedMode: displayMode,
                    onSelect: { mode in
                        withAnimation(.easeInOut(duration: 0.2)) {
                            displayMode = mode
                            if mode != .month {
                                visibleMonth = calendarMonthStart(for: selectedDate)
                            }
                        }
                    }
                )
                .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 14, trailing: TodoTimelineMetrics.horizontalPadding))
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
                if pendingItems.isEmpty {
                    Text("No pending task due for this day")
                        .font(.tdayRounded(size: 13, weight: .bold))
                        .foregroundStyle(colors.onSurfaceVariant)
                        .listRowInsets(EdgeInsets(top: 4, leading: TodoTimelineMetrics.horizontalPadding, bottom: 12, trailing: TodoTimelineMetrics.horizontalPadding))
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                } else {
                    ForEach(pendingItems) { todo in
                        CalendarPendingTaskRow(
                            todo: todo,
                            onComplete: { Task { await viewModel.complete(todo) } }
                        )
                        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                        .swipeRevealHintOnTap()
                        .swipeActions(edge: .leading, allowsFullSwipe: true) {
                            Button {
                                Task { await viewModel.complete(todo) }
                            } label: {
                                Label("Complete", systemImage: "checkmark")
                            }
                            .tint(.green)
                        }
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
                        TimelineRowDivider()
                    }
                }
            } header: {
                Text("Tasks due \(selectedDateHeaderText)")
                    .font(.tdayRounded(size: 22, weight: .heavy))
                    .foregroundStyle(colors.onSurface)
                    .textCase(nil)
                    .listRowInsets(EdgeInsets(top: 8, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
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
                titleText: "Create Task",
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
    }

    private var calendarTopInset: some View {
        TimelineTopBar(
            title: "Calendar",
            accentColor: calendarAccentColor,
            collapseProgress: titleCollapseProgress,
            onBack: { dismiss() },
            action: TimelineTopBarAction(
                systemName: "calendar",
                action: {
                    let today = Date()
                    selectDate(today)
                }
            ),
            titleRevealStart: CalendarTitleHandoff.pinnedRevealStart,
            titleRevealEnd: CalendarTitleHandoff.pinnedRevealEnd
        )
    }

    private var calendarHeroTitleRow: some View {
        CalendarExpandedTitleRow(
            title: "Calendar",
            accentColor: calendarAccentColor,
            collapseProgress: titleCollapseProgress
        )
        .background {
            TimelineScrollOffsetObserver { calendarScrollOffset = $0 }
                .frame(width: 0, height: 0)
        }
        .onVerticalScrollSnap(collapseDistance: TodoTimelineMetrics.titleCollapseDistance)
        .listRowInsets(EdgeInsets(top: 0, leading: TodoTimelineMetrics.horizontalPadding, bottom: 0, trailing: TodoTimelineMetrics.horizontalPadding))
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
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
                canGoPreviousMonth: canGoPreviousMonth,
                minimumNavigableMonth: minimumNavigableMonth,
                onPreviousMonth: { navigateMonth(by: -1) },
                onNextMonth: { navigateMonth(by: 1) },
                onSelectDate: { selectDate($0) }
            )
        case .week:
            CalendarWeekCard(
                selectedDate: selectedDate,
                today: Date(),
                tasksByDay: pendingItemsByDay,
                accentColor: calendarAccentColor,
                canGoPreviousWeek: canGoPreviousWeek,
                canSelectDate: { canNavigate(to: $0) },
                onPreviousWeek: { navigateDay(by: -7) },
                onNextWeek: { navigateDay(by: 7) },
                onSelectDate: { selectDate($0) }
            )
        case .day:
            CalendarDayCard(
                selectedDate: selectedDate,
                today: Date(),
                tasksByDay: pendingItemsByDay,
                accentColor: calendarAccentColor,
                canGoPreviousDay: canGoPreviousDay,
                canSelectDate: { canNavigate(to: $0) },
                onPreviousDay: { navigateDay(by: -1) },
                onNextDay: { navigateDay(by: 1) }
            )
        }
    }

    private func canNavigate(to date: Date) -> Bool {
        calendarMonthStart(for: date) >= minimumNavigableMonth
    }

    private func selectDate(_ date: Date) {
        guard canNavigate(to: date) else { return }
        withAnimation(calendarSelectionAnimation) {
            selectedDate = date
            visibleMonth = calendarMonthStart(for: date)
        }
    }

    private func navigateMonth(by value: Int) {
        guard value >= 0 || canGoPreviousMonth else { return }
        guard let targetMonth = Calendar.current.date(byAdding: .month, value: value, to: visibleMonth) else {
            return
        }
        withAnimation(calendarSelectionAnimation) {
            visibleMonth = calendarMonthStart(for: targetMonth)
        }
    }

    private func navigateDay(by value: Int) {
        guard let targetDate = Calendar.current.date(byAdding: .day, value: value, to: selectedDate) else {
            return
        }
        selectDate(targetDate)
    }
}

private struct CalendarViewModeTabs: View {
    let selectedMode: CalendarDisplayMode
    let onSelect: (CalendarDisplayMode) -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        HStack(spacing: 5) {
            ForEach(CalendarDisplayMode.allCases, id: \.self) { mode in
                let isSelected = mode == selectedMode
                Button {
                    onSelect(mode)
                } label: {
                    Text(mode.rawValue.capitalized)
                        .font(.tdayRounded(size: 15, weight: .heavy))
                        .foregroundStyle(isSelected ? colors.onSurface : colors.onSurfaceVariant.opacity(0.86))
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background {
                            if isSelected {
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .fill(colors.background)
                                    .shadow(color: Color.black.opacity(0.04), radius: 8, x: 0, y: 3)
                            }
                        }
                        .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
        .padding(5)
        .background(colors.surfaceVariant.opacity(0.55), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

private struct CalendarMonthGrid: View {
    let visibleMonth: Date
    let selectedDate: Date
    let tasksByDay: [Date: [TodoItem]]
    let accentColor: Color
    let canGoPreviousMonth: Bool
    let minimumNavigableMonth: Date
    let onPreviousMonth: () -> Void
    let onNextMonth: () -> Void
    let onSelectDate: (Date) -> Void

    @Environment(\.tdayColors) private var colors

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
        CalendarPagingCard(
            center: displayMonth,
            previous: canGoPreviousMonth ? calendarMonth(byAdding: -1, to: displayMonth) : nil,
            next: calendarMonth(byAdding: 1, to: displayMonth),
            height: 402,
            canNavigatePrevious: canGoPreviousMonth,
            onPrevious: onPreviousMonth,
            onNext: onNextMonth
        ) { month, actions in
            monthContent(for: month, actions: actions)
        }
    }

    private func monthContent(for displayMonth: Date, actions: CalendarPagerActions) -> some View {
        let canGoPrevious = calendarMonthStart(for: displayMonth) > minimumNavigableMonth

        return VStack(spacing: 16) {
            HStack {
                Button(action: actions.previous) {
                    Image(systemName: "chevron.left")
                        .font(.tdayRounded(size: 20, weight: .heavy))
                        .foregroundStyle(canGoPrevious ? accentColor : colors.onSurfaceVariant.opacity(0.36))
                        .frame(width: 40, height: 36)
                }
                .buttonStyle(.plain)
                .disabled(!canGoPrevious)

                Spacer(minLength: 0)

                Text(monthTitle(for: displayMonth))
                    .font(.tdayRounded(size: 18, weight: .heavy))
                    .foregroundStyle(colors.onSurface)

                Spacer(minLength: 0)

                Button(action: actions.next) {
                    Image(systemName: "chevron.right")
                        .font(.tdayRounded(size: 20, weight: .heavy))
                        .foregroundStyle(accentColor)
                        .frame(width: 40, height: 36)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 6)

            LazyVGrid(columns: columns, spacing: 11) {
                ForEach(Array(weekdaySymbols.enumerated()), id: \.offset) { _, symbol in
                    Text(symbol)
                        .font(.tdayRounded(size: 12, weight: .heavy))
                        .foregroundStyle(colors.onSurfaceVariant.opacity(0.48))
                        .frame(height: 18)
                }

                ForEach(Self.makeDays(for: displayMonth)) { day in
                    let dayTasks = tasksByDay[Calendar.current.startOfDay(for: day.date)].orEmpty
                    CalendarMonthDayCell(
                        day: day,
                        isSelected: Calendar.current.isDate(day.date, inSameDayAs: selectedDate),
                        isToday: Calendar.current.isDateInToday(day.date),
                        taskCount: dayTasks.count,
                        accentColor: accentColor,
                        onSelectDate: onSelectDate
                    )
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 18)
        .padding(.bottom, 20)
        .frame(maxWidth: .infinity)
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
    let canGoPreviousWeek: Bool
    let canSelectDate: (Date) -> Bool
    let onPreviousWeek: () -> Void
    let onNextWeek: () -> Void
    let onSelectDate: (Date) -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        CalendarPagingCard(
            center: Calendar.current.startOfDay(for: selectedDate),
            previous: canGoPreviousWeek ? calendarDate(byAddingDays: -7, to: selectedDate) : nil,
            next: calendarDate(byAddingDays: 7, to: selectedDate),
            height: 162,
            canNavigatePrevious: canGoPreviousWeek,
            onPrevious: onPreviousWeek,
            onNext: onNextWeek
        ) { displaySelectedDate, actions in
            weekContent(for: displaySelectedDate, actions: actions)
        }
    }

    private func weekContent(for displaySelectedDate: Date, actions: CalendarPagerActions) -> some View {
        let weekStart = calendarStartOfWeek(for: displaySelectedDate)
        let weekDays = (0..<7).compactMap { Calendar.current.date(byAdding: .day, value: $0, to: weekStart) }
        let previousWeekDate = calendarDate(byAddingDays: -7, to: displaySelectedDate)
        let canGoPrevious = previousWeekDate.map(canSelectDate) ?? false

        return VStack(spacing: 14) {
            HStack {
                CalendarNavButton(
                    systemName: "chevron.left",
                    isEnabled: canGoPrevious,
                    color: colors.onSurfaceVariant,
                    action: actions.previous
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
                    isEnabled: true,
                    color: colors.onSurfaceVariant,
                    action: actions.next
                )
            }
            .padding(.horizontal, 6)

            HStack(spacing: 6) {
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
                        onSelect: { onSelectDate(date) }
                    )
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.top, 16)
        .padding(.bottom, 18)
        .frame(maxWidth: .infinity)
    }
}

private struct CalendarWeekDayCell: View {
    let date: Date
    let taskCount: Int
    let isSelected: Bool
    let isToday: Bool
    let isEnabled: Bool
    let accentColor: Color
    let onSelect: () -> Void

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
                    .foregroundStyle(taskCount > 0 ? accentColor : colors.onSurfaceVariant.opacity(0.42))
            }
            .frame(maxWidth: .infinity, minHeight: 78)
            .background(cellBackground, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(cellBorderColor, lineWidth: cellBorderWidth)
            }
            .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
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
        if isSelected {
            return accentColor.opacity(0.20)
        }
        if isToday {
            return accentColor.opacity(0.12)
        }
        return colors.background
    }

    private var cellBorderColor: Color {
        if isSelected {
            return accentColor.opacity(0.82)
        }
        if isToday {
            return accentColor.opacity(0.42)
        }
        return .clear
    }

    private var cellBorderWidth: CGFloat {
        (isSelected || isToday) ? 1.4 : 0
    }

    private var dayTextColor: Color {
        if isSelected || isToday {
            return accentColor
        }
        return colors.onSurface
    }
}

private struct CalendarDayCard: View {
    let selectedDate: Date
    let today: Date
    let tasksByDay: [Date: [TodoItem]]
    let accentColor: Color
    let canGoPreviousDay: Bool
    let canSelectDate: (Date) -> Bool
    let onPreviousDay: () -> Void
    let onNextDay: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        CalendarPagingCard(
            center: Calendar.current.startOfDay(for: selectedDate),
            previous: canGoPreviousDay ? calendarDate(byAddingDays: -1, to: selectedDate) : nil,
            next: calendarDate(byAddingDays: 1, to: selectedDate),
            height: 158,
            canNavigatePrevious: canGoPreviousDay,
            onPrevious: onPreviousDay,
            onNext: onNextDay
        ) { displayDate, actions in
            dayContent(for: displayDate, actions: actions)
        }
    }

    private func dayContent(for displayDate: Date, actions: CalendarPagerActions) -> some View {
        let previousDay = calendarDate(byAddingDays: -1, to: displayDate)
        let canGoPrevious = previousDay.map(canSelectDate) ?? false

        return VStack(alignment: .leading, spacing: 14) {
            HStack {
                CalendarNavButton(
                    systemName: "chevron.left",
                    isEnabled: canGoPrevious,
                    color: colors.onSurfaceVariant,
                    action: actions.previous
                )

                Spacer(minLength: 0)

                Text(weekdayTitle(for: displayDate))
                    .font(.tdayRounded(size: 21, weight: .heavy))
                    .foregroundStyle(colors.onSurface)

                Spacer(minLength: 0)

                CalendarNavButton(
                    systemName: "chevron.right",
                    isEnabled: true,
                    color: colors.onSurfaceVariant,
                    action: actions.next
                )
            }

            Text(dateTitle(for: displayDate))
                .font(.tdayRounded(size: 25, weight: .heavy))
                .foregroundStyle(Calendar.current.isDate(displayDate, inSameDayAs: today) ? accentColor : colors.onSurface)

            Text(taskCountText(for: displayDate))
                .font(.tdayRounded(size: 18, weight: .heavy))
                .foregroundStyle(colors.onSurfaceVariant)
        }
        .padding(.horizontal, 18)
        .padding(.top, 16)
        .padding(.bottom, 22)
        .frame(maxWidth: .infinity, alignment: .leading)
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

private struct CalendarPagingCard<Page: Hashable, PageContent: View>: View {
    let center: Page
    let previous: Page?
    let next: Page?
    let height: CGFloat
    let canNavigatePrevious: Bool
    let onPrevious: () -> Void
    let onNext: () -> Void
    let content: (Page, CalendarPagerActions) -> PageContent

    @Environment(\.tdayColors) private var colors
    @State private var dragOffset: CGFloat = 0
    @State private var animatedOffset: CGFloat = 0
    @State private var isCompletingPage = false

    private let swipeThreshold: CGFloat = 48

    var body: some View {
        GeometryReader { proxy in
            let width = max(proxy.size.width, 1)
            let currentDragOffset = isCompletingPage ? animatedOffset : boundedDragOffset(dragOffset, width: width)
            let actions = CalendarPagerActions(
                previous: { completePage(.previous, width: width) },
                next: { completePage(.next, width: width) }
            )

            HStack(spacing: 0) {
                pageView(previous ?? center, actions: actions, width: width)
                pageView(center, actions: actions, width: width)
                pageView(next ?? center, actions: actions, width: width)
            }
            .offset(x: -width + currentDragOffset)
            .contentShape(Rectangle())
            .simultaneousGesture(dragGesture(width: width))
        }
        .frame(height: height)
        .clipped()
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .background(colors.surface, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .shadow(color: Color.black.opacity(0.08), radius: 10, x: 0, y: 5)
    }

    private func pageView(_ page: Page, actions: CalendarPagerActions, width: CGFloat) -> some View {
        content(page, actions)
            .frame(width: width)
    }

    private func dragGesture(width: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 12, coordinateSpace: .local)
            .onChanged { value in
                guard !isCompletingPage else { return }
                guard isHorizontalSwipe(value) else { return }
                var transaction = Transaction()
                transaction.disablesAnimations = true
                withTransaction(transaction) {
                    dragOffset = boundedDragOffset(value.translation.width, width: width)
                }
            }
            .onEnded { value in
                guard !isCompletingPage else { return }
                guard isHorizontalSwipe(value) else {
                    settleBackToCenter()
                    return
                }

                let predictedOffset = boundedDragOffset(value.predictedEndTranslation.width, width: width)
                let actualOffset = boundedDragOffset(value.translation.width, width: width)
                let intendedOffset = abs(predictedOffset) > abs(actualOffset) ? predictedOffset : actualOffset

                if intendedOffset > swipeThreshold, canNavigatePrevious {
                    completePage(.previous, width: width)
                } else if intendedOffset < -swipeThreshold {
                    completePage(.next, width: width)
                } else {
                    settleBackToCenter()
                }
            }
    }

    private func completePage(_ direction: CalendarPageDirection, width: CGFloat) {
        guard !isCompletingPage else { return }
        guard direction != .previous || canNavigatePrevious else { return }
        guard direction != .next || next != nil else { return }

        animatedOffset = dragOffset
        isCompletingPage = true
        dragOffset = 0
        withAnimation(calendarPageSettleAnimation) {
            animatedOffset = direction == .previous ? width : -width
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + calendarPageSettleDuration) {
            switch direction {
            case .previous:
                onPrevious()
            case .next:
                onNext()
            }

            var transaction = Transaction()
            transaction.disablesAnimations = true
            withTransaction(transaction) {
                animatedOffset = 0
                isCompletingPage = false
            }
        }
    }

    private func boundedDragOffset(_ offset: CGFloat, width: CGFloat) -> CGFloat {
        let maxRight = canNavigatePrevious ? width : 0
        let maxLeft = next == nil ? 0 : -width
        return min(max(offset, maxLeft), maxRight)
    }

    private func settleBackToCenter() {
        withAnimation(calendarPageSettleAnimation) {
            dragOffset = 0
        }
    }

    private func isHorizontalSwipe(_ value: DragGesture.Value) -> Bool {
        abs(value.translation.width) > abs(value.translation.height) * 1.12
    }
}

private struct CalendarMonthDayCell: View {
    let day: CalendarMonthDay
    let isSelected: Bool
    let isToday: Bool
    let taskCount: Int
    let accentColor: Color
    let onSelectDate: (Date) -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button {
            onSelectDate(day.date)
        } label: {
            VStack(spacing: 3) {
                Text(dayNumberText)
                    .font(.tdayRounded(size: 18, weight: .bold))
                    .foregroundStyle(dayTextColor)
                    .frame(width: 32, height: 28)
                    .background {
                        if isSelected {
                            Circle()
                                .fill(accentColor)
                        } else if isToday {
                            Circle()
                                .fill(accentColor.opacity(0.16))
                        }
                    }

                HStack(spacing: 3) {
                    if taskCount > 0 {
                        Circle()
                            .fill(accentColor)
                            .frame(width: 4.2, height: 4.2)

                        Text(calendarTaskCountText(taskCount))
                            .font(.tdayRounded(size: 10, weight: .heavy))
                            .foregroundStyle(accentColor)
                    }
                }
                .frame(height: 6)
            }
            .frame(height: 38)
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!day.isCurrentMonth)
        .opacity(day.isCurrentMonth ? 1 : 0.45)
    }

    private var dayNumberText: String {
        String(Calendar.current.component(.day, from: day.date))
    }

    private var dayTextColor: Color {
        if isSelected {
            return .white
        }
        if !day.isCurrentMonth {
            return colors.onSurfaceVariant.opacity(0.48)
        }
        if isToday {
            return accentColor
        }
        return colors.onSurface
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

private struct CalendarExpandedTitleRow: View {
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

    var body: some View {
        Text(title)
            .font(.tdayRounded(size: TodoTimelineMetrics.heroTitleSize, weight: .heavy))
            .foregroundStyle(accentColor)
            .lineLimit(1)
            .frame(
                maxWidth: .infinity,
                minHeight: TodoTimelineMetrics.expandedTitleHeight,
                maxHeight: TodoTimelineMetrics.expandedTitleHeight,
                alignment: .topLeading
            )
            .opacity(Double(1 - fadeProgress))
            .offset(y: titleOffsetY)
            .clipped()
            .allowsHitTesting(false)
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
