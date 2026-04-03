import SwiftUI
import UniformTypeIdentifiers

struct TodoListScreen: View {
    let highlightedTodoId: String?
    let onBack: () -> Void
    @State private var viewModel: TodoListViewModel
    @Environment(\.tdayColors) private var colors

    @State private var showingCreateTask = false
    @State private var editingTodo: TodoItem?
    @State private var showingSummary = false
    @State private var showingListSettings = false
    @State private var draggedTodo: TodoItem?
    @State private var activeDropSectionId: String?

    init(container: AppContainer, mode: TodoListMode, listId: String?, listName: String?, highlightedTodoId: String?, onBack: @escaping () -> Void) {
        self.highlightedTodoId = highlightedTodoId
        self.onBack = onBack
        _viewModel = State(initialValue: TodoListViewModel(container: container, mode: mode, listId: listId, listName: listName))
    }

    private var groupedSections: [TodoTimelineSection] {
        buildSections(items: viewModel.items, mode: viewModel.mode)
    }

    var body: some View {
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
        .background(colors.background)
        .navigationTitle(viewModel.title)
        .navigationBarTitleDisplayMode(viewModel.mode == .today ? .large : .inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Back", action: onBack)
            }
            if viewModel.mode != .list && viewModel.mode != .overdue && viewModel.aiSummaryEnabled {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        Task {
                            await viewModel.summarizeCurrentMode()
                            showingSummary = true
                        }
                    } label: {
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
            ToolbarItem(placement: .primaryAction) {
                Button {
                    showingCreateTask = true
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .refreshable {
            await viewModel.refresh()
        }
        .onChange(of: viewModel.items) { _ in
            activeDropSectionId = nil
            draggedTodo = nil
        }
        .sheet(isPresented: $showingCreateTask) {
            CreateTaskSheet(
                lists: viewModel.lists,
                titleText: "Create Task",
                submitText: "Create",
                initialPayload: CreateTaskPayload(title: "", description: nil, priority: viewModel.mode == .priority ? "High" : "Low", dtstart: Date(), due: Date().addingTimeInterval(60 * 60), rrule: nil, listId: viewModel.listId),
                onParseTaskTitleNlp: { title, start, due in
                    await viewModel.parseTaskTitleNlp(text: title, referenceStartEpochMs: start, referenceDueEpochMs: due)
                },
                onDismiss: { showingCreateTask = false },
                onSubmit: { payload in
                    await viewModel.addTask(payload)
                }
            )
        }
        .sheet(item: $editingTodo) { todo in
            CreateTaskSheet(
                lists: viewModel.lists,
                titleText: "Edit Task",
                submitText: "Save",
                initialPayload: CreateTaskPayload(title: todo.title, description: todo.description, priority: todo.priority, dtstart: todo.dtstart, due: todo.due, rrule: todo.rrule, listId: todo.listId),
                onParseTaskTitleNlp: { title, start, due in
                    await viewModel.parseTaskTitleNlp(text: title, referenceStartEpochMs: start, referenceDueEpochMs: due)
                },
                onDismiss: { editingTodo = nil },
                onSubmit: { payload in
                    await viewModel.updateTask(todo, payload: payload)
                }
            )
        }
        .sheet(isPresented: $showingSummary) {
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
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Close") { showingSummary = false }
                    }
                }
            }
            .presentationDetents([.medium, .large])
        }
        .sheet(isPresented: $showingListSettings) {
            ListSettingsSheet(list: viewModel.lists.first { $0.id == viewModel.listId }) { name, color, iconKey in
                Task { await viewModel.updateListSettings(name: name, color: color, iconKey: iconKey) }
            }
        }
    }

    private func todoRow(_ todo: TodoItem, in section: TodoTimelineSection) -> some View {
        let rowContent = VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 10) {
                Circle()
                    .fill(priorityColor(todo.priority))
                    .frame(width: 10, height: 10)
                Text(todo.title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(colors.onSurface)
                Spacer()
                if todo.pinned {
                    Image(systemName: "pin.fill")
                        .foregroundStyle(colors.tertiary)
                }
            }
            Text(todo.due.formatted(date: .abbreviated, time: .shortened))
                .font(.caption)
                .foregroundStyle(colors.onSurfaceVariant)
            if let description = todo.description, !description.isEmpty {
                Text(description)
                    .font(.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
                    .lineLimit(2)
            }
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
        return ["Morning", "Afternoon", "Tonight"].compactMap { key in
            guard let values = grouped[key], !values.isEmpty else { return nil }
            return TodoTimelineSection(
                id: key,
                title: key,
                items: values.sorted(by: { $0.due < $1.due }),
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
    case .all, .priority, .list:
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
    return date.formatted(.dateTime.weekday(.wide).month(.abbreviated).day())
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
