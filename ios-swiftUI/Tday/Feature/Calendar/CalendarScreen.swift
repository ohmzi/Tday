import SwiftUI

private enum CalendarScope: String, CaseIterable {
    case month
    case week
    case day
}

struct CalendarScreen: View {
    @State private var viewModel: CalendarViewModel
    @Environment(\.tdayColors) private var colors

    @State private var selectedDate = Date()
    @State private var scope: CalendarScope = .month
    @State private var showingCreateTask = false
    @State private var editingTodo: TodoItem?

    init(container: AppContainer) {
        _viewModel = State(initialValue: CalendarViewModel(container: container))
    }

    private var pendingItems: [TodoItem] {
        viewModel.items.filter { matches(date: $0.due) }.sorted(by: { $0.due < $1.due })
    }

    private var completedItems: [CompletedItem] {
        viewModel.completedItems.filter { matches(date: $0.completedAt ?? $0.due) }.sorted(by: { ($0.completedAt ?? $0.due) < ($1.completedAt ?? $1.due) })
    }

    var body: some View {
        List {
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

            Section("Pending") {
                if pendingItems.isEmpty {
                    Text("No scheduled tasks for this selection.")
                        .foregroundStyle(colors.onSurfaceVariant)
                } else {
                    ForEach(pendingItems) { todo in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(todo.title)
                                .font(.subheadline.weight(.semibold))
                            Text(todo.due.formatted(date: .abbreviated, time: .shortened))
                                .font(.caption)
                                .foregroundStyle(colors.onSurfaceVariant)
                        }
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
            }

            Section("Completed") {
                if completedItems.isEmpty {
                    Text("No completed tasks for this selection.")
                        .foregroundStyle(colors.onSurfaceVariant)
                } else {
                    ForEach(completedItems) { item in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.title)
                                .font(.subheadline.weight(.semibold))
                            Text((item.completedAt ?? item.due).formatted(date: .abbreviated, time: .shortened))
                                .font(.caption)
                                .foregroundStyle(colors.onSurfaceVariant)
                        }
                        .swipeActions(edge: .leading, allowsFullSwipe: true) {
                            Button {
                                Task { await viewModel.uncomplete(item) }
                            } label: {
                                Label("Restore", systemImage: "arrow.uturn.backward")
                            }
                            .tint(.blue)
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(colors.background)
        .navigationBackHistoryTitle("Calendar")
        .navigationTitle("Calendar")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    selectedDate = Date()
                } label: {
                    Text("Today")
                }
            }
        }
        .refreshable {
            await viewModel.refresh()
        }
        .safeAreaInset(edge: .bottom) {
            TaskFloatingActionButtonDock {
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

    private func matches(date: Date) -> Bool {
        let calendar = Calendar.current
        switch scope {
        case .month:
            return calendar.isDate(date, equalTo: selectedDate, toGranularity: .month)
        case .week:
            return calendar.isDate(date, equalTo: selectedDate, toGranularity: .weekOfYear)
        case .day:
            return calendar.isDate(date, inSameDayAs: selectedDate)
        }
    }
}
