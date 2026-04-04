import SwiftUI

struct HomeScreen: View {
    let onNavigate: (AppRoute) -> Void
    @State private var viewModel: HomeViewModel
    @Environment(\.tdayColors) private var colors

    @State private var searchQuery = ""
    @State private var showingCreateTask = false
    @State private var showingCreateList = false

    init(container: AppContainer, onNavigate: @escaping (AppRoute) -> Void) {
        self.onNavigate = onNavigate
        _viewModel = State(initialValue: HomeViewModel(container: container))
    }

    private var filteredTodos: [TodoItem] {
        let normalizedQuery = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !normalizedQuery.isEmpty else {
            return []
        }
        let listNames = Dictionary(uniqueKeysWithValues: viewModel.summary.lists.map { ($0.id, $0.name.lowercased()) })
        return viewModel.searchableTodos.filter { todo in
            todo.title.lowercased().contains(normalizedQuery) ||
            (todo.description?.lowercased().contains(normalizedQuery) ?? false) ||
            (todo.listId.flatMap { listNames[$0] }?.contains(normalizedQuery) ?? false)
        }
        .prefix(20)
        .map { $0 }
    }

    private var overdueCount: Int {
        let now = Date()
        return viewModel.searchableTodos.filter { todo in
            todo.due < now
        }.count
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                header
                searchBar
                if let errorMessage = viewModel.errorMessage {
                    ErrorRetryView(message: errorMessage) {
                        Task { await viewModel.refresh() }
                    }
                }
                dashboardGrid
                if !filteredTodos.isEmpty {
                    searchResults
                }
                listsSection
                if viewModel.isLoading && viewModel.summary.allCount == 0 {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                        .padding(.top, 20)
                }
            }
            .padding(20)
        }
        .background(colors.background)
        .refreshable {
            await viewModel.refresh()
        }
        .safeAreaInset(edge: .bottom) {
            HStack {
                Spacer()
                Button {
                    showingCreateTask = true
                } label: {
                    Image(systemName: "plus")
                        .font(.title3.bold())
                        .foregroundStyle(colors.onPrimary)
                        .frame(width: 58, height: 58)
                        .background(colors.primary)
                        .clipShape(Circle())
                        .shadow(color: .black.opacity(0.2), radius: 18, x: 0, y: 10)
                }
                .padding(.trailing, 20)
                .padding(.vertical, 8)
            }
        }
        .sheet(isPresented: $showingCreateTask) {
            CreateTaskSheet(
                lists: viewModel.summary.lists,
                titleText: "Create Task",
                submitText: "Create",
                initialPayload: nil,
                onParseTaskTitleNlp: { title, dueRef in
                    await viewModel.parseTaskTitleNlp(text: title, referenceDueEpochMs: dueRef)
                },
                onDismiss: { showingCreateTask = false },
                onSubmit: { payload in
                    await viewModel.createTask(payload)
                }
            )
        }
        .sheet(isPresented: $showingCreateList) {
            CreateListSheet { name, color, iconKey in
                Task {
                    await viewModel.createList(name: name, color: color, iconKey: iconKey)
                }
            }
        }
    }

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 6) {
                Text("Tday")
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .foregroundStyle(colors.onSurface)
                Text("Everything due, scheduled, and ready to move.")
                    .font(.subheadline)
                    .foregroundStyle(colors.onSurfaceVariant)
            }
            Spacer()
            Button {
                onNavigate(.settings)
            } label: {
                Image(systemName: "gearshape.fill")
                    .font(.title3)
                    .foregroundStyle(colors.onSurface)
                    .padding(12)
                    .background(colors.surface)
                    .clipShape(Circle())
            }
        }
    }

    private var searchBar: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(colors.onSurfaceVariant)
                TextField("Search tasks", text: $searchQuery)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                if !searchQuery.isEmpty {
                    Button {
                        searchQuery = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                }
            }
            .padding(14)
            .background(colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))

            HStack(spacing: 12) {
                Button("Create list") {
                    showingCreateList = true
                }
                .buttonStyle(.bordered)

                Button("Calendar") {
                    onNavigate(.calendar)
                }
                .buttonStyle(.bordered)

                Button("Completed") {
                    onNavigate(.completed)
                }
                .buttonStyle(.bordered)
            }
            .font(.subheadline.weight(.semibold))
        }
    }

    private var dashboardGrid: some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 14) {
            DashboardCard(title: "Today", count: viewModel.summary.todayCount, icon: "sun.max.fill", tint: colors.primary) {
                onNavigate(.todayTodos)
            }
            DashboardCard(title: "Overdue", count: overdueCount, icon: "exclamationmark.circle.fill", tint: colors.error) {
                onNavigate(.overdueTodos)
            }
            DashboardCard(title: "Scheduled", count: viewModel.summary.scheduledCount, icon: "calendar.badge.clock", tint: colors.tertiary) {
                onNavigate(.scheduledTodos)
            }
            DashboardCard(title: "All", count: viewModel.summary.allCount, icon: "tray.full.fill", tint: colors.secondary) {
                onNavigate(.allTodos(highlightTodoId: nil))
            }
            DashboardCard(title: "Priority", count: viewModel.summary.priorityCount, icon: "flag.fill", tint: .red) {
                onNavigate(.priorityTodos)
            }
        }
    }

    private var searchResults: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Search Results")
                .font(.headline)
                .foregroundStyle(colors.onSurface)
            ForEach(filteredTodos) { todo in
                Button {
                    onNavigate(.allTodos(highlightTodoId: todo.id))
                } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(todo.title)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(colors.onSurface)
                        Text(todo.due.formatted(date: .abbreviated, time: .shortened))
                            .font(.caption)
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(14)
                    .background(colors.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
            }
        }
    }

    private var listsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("My Lists")
                .font(.headline)
                .foregroundStyle(colors.onSurface)
            if viewModel.summary.lists.isEmpty {
                Text("Create your first list to organize recurring work, projects, or routines.")
                    .font(.subheadline)
                    .foregroundStyle(colors.onSurfaceVariant)
                    .padding(18)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(colors.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            } else {
                ForEach(viewModel.summary.lists) { list in
                    Button {
                        onNavigate(.listTodos(listId: list.id, listName: list.name))
                    } label: {
                        HStack(spacing: 14) {
                            Circle()
                                .fill(listAccentColor(list.color))
                                .frame(width: 12, height: 12)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(list.name)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(colors.onSurface)
                                Text("\(list.todoCount) open tasks")
                                    .font(.caption)
                                    .foregroundStyle(colors.onSurfaceVariant)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(colors.onSurfaceVariant)
                        }
                        .padding(16)
                        .background(colors.surface)
                        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                    }
                }
            }
        }
    }
}

private struct DashboardCard: View {
    let title: String
    let count: Int
    let icon: String
    let tint: Color
    let action: () -> Void
    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 12) {
                Image(systemName: icon)
                    .font(.title3)
                    .foregroundStyle(tint)
                Text(title)
                    .font(.headline)
                    .foregroundStyle(colors.onSurface)
                Text(count.formatted())
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundStyle(colors.onSurface)
            }
            .frame(maxWidth: .infinity, minHeight: 120, alignment: .leading)
            .padding(18)
            .background(colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        }
    }
}

private struct CreateListSheet: View {
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
            .navigationTitle("Create List")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Create") {
                        onSubmit(name.trimmingCharacters(in: .whitespacesAndNewlines), color, iconKey)
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}

private func listAccentColor(_ key: String?) -> Color {
    switch key?.uppercased() {
    case "GREEN":
        return .green
    case "ORANGE":
        return .orange
    case "PINK":
        return .pink
    case "PURPLE":
        return .purple
    case "GRAY":
        return .gray
    default:
        return .blue
    }
}
