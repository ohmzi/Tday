import SwiftUI

private enum CalendarScope: String, CaseIterable {
    case month
    case week
    case day
}

private enum CalendarTitleHandoff {
    static let pinnedRevealStart: CGFloat = 0.18
    static let pinnedRevealEnd: CGFloat = 0.62
}

struct CalendarScreen: View {
    @State private var viewModel: CalendarViewModel
    @Environment(\.tdayColors) private var colors
    @Environment(\.dismiss) private var dismiss
    private let calendarAccentColor = Color(red: 125.0 / 255.0, green: 103.0 / 255.0, blue: 182.0 / 255.0)

    @State private var selectedDate = Date()
    @State private var scope: CalendarScope = .month
    @State private var showingCreateTask = false
    @State private var editingTodo: TodoItem?
    @State private var calendarScrollOffset: CGFloat = 0

    init(container: AppContainer) {
        _viewModel = State(initialValue: CalendarViewModel(container: container))
    }

    private var pendingItems: [TodoItem] {
        viewModel.items.filter { isSelectedDay($0.due) }.sorted(by: { $0.due < $1.due })
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

    var body: some View {
        List {
            calendarHeroTitleRow

            Section {
                Picker("Scope", selection: $scope) {
                    ForEach(CalendarScope.allCases, id: \.self) { value in
                        Text(value.rawValue.capitalized).tag(value)
                    }
                }
                .pickerStyle(.segmented)

                DatePicker("Selected date", selection: $selectedDate, displayedComponents: [.date])
                    .datePickerStyle(.graphical)
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
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .contentMargins(.top, 0, for: .scrollContent)
        .listSectionSpacing(8)
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
                action: { selectedDate = Date() }
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
            .frame(minHeight: 58)

            Rectangle()
                .fill(colors.onSurfaceVariant.opacity(0.18))
                .frame(height: 1)
                .padding(.trailing, -TodoTimelineMetrics.swipeDividerExtension)
        }
    }
}
