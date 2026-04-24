import SwiftUI

private enum HomeMetrics {
    static let screenPadding: CGFloat = 18
    static let sectionSpacing: CGFloat = 14
    static let tileGap: CGFloat = 10
    static let topBarButtonSize: CGFloat = 54
    static let compactButtonSize: CGFloat = 30
    static let tileCornerRadius: CGFloat = 26
    static let tileHeight: CGFloat = 102
    static let listRowHeight: CGFloat = 70
    static let tileWatermarkSize: CGFloat = 124
    static let tileWatermarkTrailingInset: CGFloat = 22
}

private func isHomeDaytime(_ date: Date) -> Bool {
    let hour = Calendar.current.component(.hour, from: date)
    return (6..<18).contains(hour)
}

private struct HomeSearchBarFrameKey: PreferenceKey {
    static var defaultValue: CGRect = .zero

    static func reduce(value: inout CGRect, nextValue: () -> CGRect) {
        value = nextValue()
    }
}

private struct HomeSearchResultsFrameKey: PreferenceKey {
    static var defaultValue: CGRect = .zero

    static func reduce(value: inout CGRect, nextValue: () -> CGRect) {
        value = nextValue()
    }
}

private struct HomeListColorOption {
    let key: String
    let color: Color
}

private struct HomeListIconOption {
    let key: String
    let symbolName: String
}

struct HomeScreen: View {
    let onNavigate: (AppRoute) -> Void

    @State private var viewModel: HomeViewModel
    @Environment(\.tdayColors) private var colors
    @FocusState private var searchFieldFocused: Bool

    @State private var searchExpanded = false
    @State private var searchQuery = ""
    @State private var searchBarFrame: CGRect = .zero
    @State private var searchResultsFrame: CGRect = .zero
    @State private var showingCreateTask = false
    @State private var showingCreateList = false

    init(container: AppContainer, onNavigate: @escaping (AppRoute) -> Void) {
        self.onNavigate = onNavigate
        _viewModel = State(initialValue: HomeViewModel(container: container))
    }

    private var normalizedSearchQuery: String {
        searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    private var listByID: [String: ListSummary] {
        Dictionary(uniqueKeysWithValues: viewModel.summary.lists.map { ($0.id, $0) })
    }

    private var filteredTodos: [TodoItem] {
        guard !normalizedSearchQuery.isEmpty else {
            return []
        }

        return viewModel.searchableTodos.filter { todo in
            todo.title.lowercased().contains(normalizedSearchQuery) ||
                (todo.description?.lowercased().contains(normalizedSearchQuery) ?? false) ||
                (todo.listId.flatMap { listByID[$0]?.name.lowercased() }?.contains(normalizedSearchQuery) ?? false)
        }
        .sorted { $0.due < $1.due }
        .prefix(20)
        .map { $0 }
    }

    private var overdueCount: Int {
        let now = Date()
        return viewModel.searchableTodos.count { $0.due < now }
    }

    private var showSearchResultsOverlay: Bool {
        searchExpanded && !normalizedSearchQuery.isEmpty
    }

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .topLeading) {
                ScrollView(showsIndicators: false) {
                    LazyVStack(alignment: .leading, spacing: HomeMetrics.sectionSpacing) {
                        HomeTopBar(
                            totalWidth: proxy.size.width - (HomeMetrics.screenPadding * 2),
                            searchExpanded: $searchExpanded,
                            searchQuery: $searchQuery,
                            searchFieldFocused: $searchFieldFocused,
                            onCreateList: {
                                closeSearch()
                                showingCreateList = true
                            },
                            onOpenSettings: {
                                closeSearch()
                                onNavigate(.settings)
                            }
                        )

                        HomeCategoryBoard(
                            todayCount: viewModel.summary.todayCount,
                            overdueCount: overdueCount,
                            scheduledCount: viewModel.summary.scheduledCount,
                            allCount: viewModel.summary.allCount,
                            priorityCount: viewModel.summary.priorityCount,
                            completedCount: viewModel.summary.completedCount,
                            onOpenToday: {
                                closeSearch()
                                onNavigate(.todayTodos)
                            },
                            onOpenOverdue: {
                                closeSearch()
                                onNavigate(.overdueTodos)
                            },
                            onOpenScheduled: {
                                closeSearch()
                                onNavigate(.scheduledTodos)
                            },
                            onOpenAll: {
                                closeSearch()
                                onNavigate(.allTodos(highlightTodoId: nil))
                            },
                            onOpenPriority: {
                                closeSearch()
                                onNavigate(.priorityTodos)
                            },
                            onOpenCompleted: {
                                closeSearch()
                                onNavigate(.completed)
                            },
                            onOpenCalendar: {
                                closeSearch()
                                onNavigate(.calendar)
                            }
                        )

                        if !viewModel.summary.lists.isEmpty {
                            HomeListsHeader()

                            ForEach(viewModel.summary.lists) { list in
                                HomeListRow(
                                    name: displayName(for: list.name),
                                    colorKey: list.color,
                                    iconKey: list.iconKey,
                                    count: list.todoCount
                                ) {
                                    closeSearch()
                                    onNavigate(.listTodos(listId: list.id, listName: displayName(for: list.name)))
                                }
                            }
                        }

                        if let errorMessage = viewModel.errorMessage {
                            ErrorRetryView(message: errorMessage) {
                                Task { await viewModel.refresh() }
                            }
                        }

                        if viewModel.isLoading && viewModel.summary.allCount == 0 {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                                .padding(.top, 10)
                        }

                    }
                    .padding(.horizontal, HomeMetrics.screenPadding)
                    .padding(.top, HomeMetrics.screenPadding)
                }
                .refreshable {
                    await viewModel.refresh()
                }
                .scrollBounceBehavior(.basedOnSize, axes: .vertical)
                .safeAreaInset(edge: .bottom) {
                    TaskFloatingActionButtonDock {
                        closeSearch()
                        showingCreateTask = true
                    }
                }

                if showSearchResultsOverlay, searchBarFrame != .zero {
                    HomeSearchResultsOverlay(
                        todos: filteredTodos,
                        listsByID: listByID,
                        onOpenTodo: { todo in
                            closeSearch()
                            onNavigate(.allTodos(highlightTodoId: todo.id))
                        }
                    )
                    .frame(width: searchBarFrame.width)
                    .offset(x: searchBarFrame.minX, y: searchBarFrame.maxY + 8)
                    .background(
                        GeometryReader { resultsProxy in
                            Color.clear
                                .preference(
                                    key: HomeSearchResultsFrameKey.self,
                                    value: resultsProxy.frame(in: .named("home-root"))
                                )
                        }
                    )
                    .zIndex(5)
                }
            }
            .coordinateSpace(name: "home-root")
            .background(colors.background)
            .simultaneousGesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .named("home-root"))
                    .onEnded { value in
                        let isTap = abs(value.translation.width) < 8 && abs(value.translation.height) < 8
                        guard isTap else { return }
                        handleSearchTap(at: value.startLocation)
                    }
            )
        }
        .onPreferenceChange(HomeSearchBarFrameKey.self) { frame in
            searchBarFrame = frame
        }
        .onPreferenceChange(HomeSearchResultsFrameKey.self) { frame in
            searchResultsFrame = frame
        }
        .onChange(of: searchExpanded) { _, expanded in
            if expanded {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                    searchFieldFocused = true
                }
            } else {
                searchFieldFocused = false
                searchResultsFrame = .zero
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
        .navigationBackButtonBehavior()
    }

    private func closeSearch() {
        withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
            searchExpanded = false
        }
        searchQuery = ""
    }

    private func handleSearchTap(at location: CGPoint) {
        guard searchExpanded else { return }
        guard !searchBarFrame.contains(location) else { return }
        guard !showSearchResultsOverlay || !searchResultsFrame.contains(location) else { return }
        closeSearch()
    }

    private func displayName(for value: String) -> String {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let first = trimmed.first else {
            return value
        }
        return first.uppercased() + String(trimmed.dropFirst())
    }
}

private struct HomeTopBar: View {
    let totalWidth: CGFloat
    @Binding var searchExpanded: Bool
    @Binding var searchQuery: String
    var searchFieldFocused: FocusState<Bool>.Binding
    let onCreateList: () -> Void
    let onOpenSettings: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        let buttonSize = HomeMetrics.topBarButtonSize
        let buttonGap: CGFloat = 8
        let expandedSearchWidth = max(buttonSize, totalWidth - (buttonSize * 2) - (buttonGap * 2))
        let searchWidth = searchExpanded ? expandedSearchWidth : buttonSize

        ZStack(alignment: .trailing) {
            if !searchExpanded {
                TimelineView(.periodic(from: .now, by: 60)) { context in
                    let daytime = isHomeDaytime(context.date)

                    HStack(spacing: 8) {
                        Image(systemName: daytime ? "sun.max.fill" : "moon.stars.fill")
                            .font(.system(size: 26, weight: .regular))
                            .foregroundStyle(Color(hex: daytime ? 0xF4C542 : 0xA8B8E8))

                        Text("T'Day")
                            .font(.system(size: 32, weight: .bold))
                            .foregroundStyle(colors.onSurface)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.leading, 2)
                    .transition(.opacity)
                }
            }

            HStack(spacing: buttonGap) {
                Group {
                    if searchExpanded {
                        HStack(spacing: 10) {
                            HomeIconCircleButton(icon: "magnifyingglass", compact: true) {
                                withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
                                    searchExpanded = false
                                }
                                searchQuery = ""
                            }

                            TextField("", text: $searchQuery, prompt: Text("Search").foregroundStyle(colors.onSurfaceVariant))
                                .focused(searchFieldFocused)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .font(.system(size: 18, weight: .medium))
                                .foregroundStyle(colors.onSurface)
                                .tint(colors.primary)

                            Button {
                                withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
                                    searchExpanded = false
                                }
                                searchQuery = ""
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.system(size: 18, weight: .semibold))
                                    .foregroundStyle(colors.onSurfaceVariant.opacity(0.78))
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Close search")
                        }
                        .padding(.horizontal, 14)
                        .frame(width: searchWidth, height: buttonSize)
                        .background(colors.surface, in: Capsule())
                        .overlay(
                            Capsule()
                                .stroke(colors.onSurface.opacity(0.26), lineWidth: 1)
                        )
                    } else {
                        HomeIconCircleButton(icon: "magnifyingglass") {
                            withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
                                searchExpanded = true
                            }
                        }
                        .frame(width: searchWidth, height: buttonSize)
                    }
                }
                .background(
                    GeometryReader { proxy in
                        Color.clear
                            .preference(key: HomeSearchBarFrameKey.self, value: proxy.frame(in: .named("home-root")))
                    }
                )

                HomeIconCircleButton(icon: "text.badge.plus") {
                    onCreateList()
                }

                HomeIconCircleButton(icon: "ellipsis") {
                    onOpenSettings()
                }
            }
            .animation(.spring(response: 0.28, dampingFraction: 0.86), value: searchExpanded)
        }
        .frame(maxWidth: .infinity, minHeight: HomeMetrics.topBarButtonSize)
    }
}

private struct HomeIconCircleButton: View {
    let icon: String
    var compact = false
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: compact ? 20 : 22, weight: .semibold))
                .foregroundStyle(colors.onSurface)
                .frame(
                    width: compact ? HomeMetrics.compactButtonSize : HomeMetrics.topBarButtonSize,
                    height: compact ? HomeMetrics.compactButtonSize : HomeMetrics.topBarButtonSize
                )
                .background(compact ? Color.clear : colors.surface)
                .clipShape(Circle())
                .overlay {
                    if !compact {
                        Circle()
                            .stroke(colors.onSurface.opacity(0.34), lineWidth: 1)
                    }
                }
        }
        .buttonStyle(HomeIconButtonStyle(compact: compact))
    }
}

private struct HomeCategoryBoard: View {
    let todayCount: Int
    let overdueCount: Int
    let scheduledCount: Int
    let allCount: Int
    let priorityCount: Int
    let completedCount: Int
    let onOpenToday: () -> Void
    let onOpenOverdue: () -> Void
    let onOpenScheduled: () -> Void
    let onOpenAll: () -> Void
    let onOpenPriority: () -> Void
    let onOpenCompleted: () -> Void
    let onOpenCalendar: () -> Void

    var body: some View {
        VStack(spacing: HomeMetrics.tileGap) {
            HStack(spacing: HomeMetrics.tileGap) {
                HomeCategoryTile(
                    color: Color(hex: 0x6EA8E1),
                    icon: "sun.max.fill",
                    watermark: "sun.max.fill",
                    title: "Today",
                    count: todayCount,
                    action: onOpenToday
                )

                HomeCategoryTile(
                    color: Color(hex: 0xDA7661),
                    icon: "exclamationmark.circle",
                    watermark: "exclamationmark.circle",
                    title: "Overdue",
                    count: overdueCount,
                    action: onOpenOverdue
                )
            }

            HStack(spacing: HomeMetrics.tileGap) {
                HomeCategoryTile(
                    color: Color(hex: 0xDDB37D),
                    icon: "clock",
                    watermark: "clock",
                    title: "Scheduled",
                    count: scheduledCount,
                    action: onOpenScheduled
                )

                HomeCategoryTile(
                    color: Color(hex: 0xD48A8C),
                    icon: "flag.fill",
                    watermark: "flag.fill",
                    title: "Priority",
                    count: priorityCount,
                    action: onOpenPriority
                )
            }

            HStack(spacing: HomeMetrics.tileGap) {
                HomeCategoryTile(
                    color: Color(hex: 0x4E4E50),
                    icon: "tray.fill",
                    watermark: "tray.fill",
                    title: "All",
                    count: allCount,
                    action: onOpenAll
                )

                HomeCategoryTile(
                    color: Color(hex: 0xA8C8B2),
                    icon: "checkmark",
                    watermark: "checkmark",
                    title: "Completed",
                    count: completedCount,
                    action: onOpenCompleted
                )
            }

            HomeCategoryTile(
                color: Color(hex: 0xC3B4DF),
                icon: "calendar",
                watermark: nil,
                title: "Calendar",
                count: scheduledCount,
                backgroundGrid: true,
                action: onOpenCalendar
            )
            .frame(maxWidth: .infinity)
        }
    }
}

private struct HomeCategoryTile: View {
    let color: Color
    let icon: String
    let watermark: String?
    let title: String
    let count: Int
    var backgroundGrid = false
    let action: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: HomeMetrics.tileCornerRadius, style: .continuous)

        Button(action: action) {
            ZStack {
                shape
                    .fill(color)

                shape
                    .fill(
                        RadialGradient(
                            colors: [
                                Color.white.opacity(0.22),
                                Color.white.opacity(0.08),
                                .clear,
                            ],
                            center: .topLeading,
                            startRadius: 8,
                            endRadius: 140
                        )
                    )

                shape
                    .fill(
                        LinearGradient(
                            colors: [
                                Color.white.opacity(0.12),
                                Color(hex: 0xE7F3FF).opacity(0.1),
                                Color(hex: 0xFFF2FA).opacity(0.08),
                                .clear,
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )

                if backgroundGrid {
                    HomeCalendarGridWatermark(baseColor: color)
                        .allowsHitTesting(false)
                }

                if let watermark {
                    Image(systemName: watermark)
                        .font(.system(size: HomeMetrics.tileWatermarkSize, weight: .regular))
                        .foregroundStyle(color.blended(with: .white, amount: 0.28).opacity(0.4))
                        .offset(x: HomeMetrics.tileWatermarkTrailingInset, y: 12)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
                        .allowsHitTesting(false)
                }

                VStack(alignment: .leading, spacing: 8) {
                    HStack(alignment: .center) {
                        Image(systemName: icon)
                            .font(.system(size: 24, weight: .bold))
                            .foregroundStyle(.white)
                        Spacer()
                        Text("\(count)")
                            .font(.system(size: 28, weight: .black))
                            .foregroundStyle(.white)
                    }

                    Text(title)
                        .font(.system(size: 22, weight: .bold))
                        .foregroundStyle(.white)
                }
                .padding(16)
            }
            .frame(maxWidth: .infinity, alignment: .topLeading)
            .frame(height: HomeMetrics.tileHeight)
            .clipShape(shape)
            .contentShape(shape)
        }
        .buttonStyle(HomeTileButtonStyle())
    }
}

private struct HomeCalendarGridWatermark: View {
    let baseColor: Color

    var body: some View {
        Canvas { context, size in
            let rect = CGRect(origin: .zero, size: size)
            let strokeColor = baseColor.blended(with: .white, amount: 0.32).opacity(0.9)
            let lineColor = baseColor.blended(with: .white, amount: 0.32).opacity(0.82)
            let lineWidth: CGFloat = 1.2
            let cornerRadius: CGFloat = 8

            let border = Path(roundedRect: rect, cornerRadius: cornerRadius)
            context.stroke(border, with: .color(strokeColor), lineWidth: lineWidth)

            for column in 1..<6 {
                let x = rect.minX + (rect.width * CGFloat(column) / 6)
                var path = Path()
                path.move(to: CGPoint(x: x, y: rect.minY))
                path.addLine(to: CGPoint(x: x, y: rect.maxY))
                context.stroke(path, with: .color(lineColor), lineWidth: lineWidth)
            }

            for row in 1..<4 {
                let y = rect.minY + (rect.height * CGFloat(row) / 4)
                var path = Path()
                path.move(to: CGPoint(x: rect.minX, y: y))
                path.addLine(to: CGPoint(x: rect.maxX, y: y))
                context.stroke(path, with: .color(lineColor), lineWidth: lineWidth)
            }
        }
        .frame(width: 172, height: 116)
        .offset(x: 28, y: 14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
        .opacity(0.42)
    }
}

private struct HomeListsHeader: View {
    @Environment(\.tdayColors) private var colors

    var body: some View {
        Text("My Lists")
            .font(.system(size: 28, weight: .bold))
            .foregroundStyle(colors.onSurface)
            .padding(.top, 2)
    }
}

private struct HomeListRow: View {
    let name: String
    let colorKey: String?
    let iconKey: String?
    let count: Int
    let action: () -> Void

    private var accent: Color {
        homeListAccentColor(for: colorKey)
    }

    private var symbolName: String {
        homeListSymbolName(for: iconKey)
    }

    private var containerColor: Color {
        Color.tdayLightSurfaceVariant.blended(with: accent, amount: 0.38)
    }

    var body: some View {
        Button(action: action) {
            ZStack {
                RoundedRectangle(cornerRadius: HomeMetrics.tileCornerRadius, style: .continuous)
                    .fill(containerColor)

                RoundedRectangle(cornerRadius: HomeMetrics.tileCornerRadius, style: .continuous)
                    .fill(
                        RadialGradient(
                            colors: [
                                Color.white.opacity(0.22),
                                Color.white.opacity(0.08),
                                .clear,
                            ],
                            center: .topLeading,
                            startRadius: 8,
                            endRadius: 120
                        )
                    )

                RoundedRectangle(cornerRadius: HomeMetrics.tileCornerRadius, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [
                                Color.white.opacity(0.12),
                                Color(hex: 0xE7F3FF).opacity(0.1),
                                Color(hex: 0xFFF2FA).opacity(0.08),
                                .clear,
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )

                Image(systemName: symbolName)
                    .font(.system(size: 60, weight: .regular))
                    .foregroundStyle(containerColor.blended(with: .white, amount: 0.34).opacity(0.42))
                    .offset(x: 18, y: 8)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
                    .allowsHitTesting(false)

                HStack {
                    HStack(spacing: 10) {
                        Image(systemName: symbolName)
                            .font(.system(size: 22, weight: .semibold))
                            .foregroundStyle(.white)
                            .frame(width: 32, height: 32)

                        Text(name)
                            .font(.system(size: 22, weight: .semibold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                    }

                    Spacer()

                    Text("\(count)")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundStyle(.white)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .frame(maxWidth: .infinity, minHeight: HomeMetrics.listRowHeight, maxHeight: HomeMetrics.listRowHeight)
        }
        .buttonStyle(HomeListButtonStyle())
    }
}

private struct HomeSearchResultsOverlay: View {
    let todos: [TodoItem]
    let listsByID: [String: ListSummary]
    let onOpenTodo: (TodoItem) -> Void

    @Environment(\.tdayColors) private var colors

    private let dueFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "EEE h:mm a"
        return formatter
    }()

    var body: some View {
        VStack(spacing: 0) {
            if todos.isEmpty {
                Text("No matching tasks")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(colors.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
            } else {
                ForEach(Array(todos.enumerated()), id: \.element.id) { index, todo in
                    let list = todo.listId.flatMap { listsByID[$0] }
                    let tint = homeListAccentColor(for: list?.color)
                    let symbolName = homeListSymbolName(for: list?.iconKey)

                    Button {
                        onOpenTodo(todo)
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: symbolName)
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundStyle(tint.opacity(0.92))
                                .frame(width: 18)

                            VStack(alignment: .leading, spacing: 3) {
                                Text(todo.title)
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundStyle(colors.onSurface)
                                    .lineLimit(1)

                                Text(dueFormatter.string(from: todo.due))
                                    .font(.system(size: 12, weight: .regular))
                                    .foregroundStyle(colors.onSurfaceVariant)
                                    .lineLimit(1)
                            }

                            Spacer(minLength: 0)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 9)
                    }
                    .buttonStyle(.plain)

                    if index < todos.count - 1 {
                        Rectangle()
                            .fill(colors.onSurface.opacity(0.08))
                            .frame(height: 1)
                            .padding(.horizontal, 12)
                    }
                }
            }
        }
        .background(colors.surface, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(colors.onSurface.opacity(0.2), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.14), radius: 10, x: 0, y: 8)
    }
}

private struct HomeTileButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .offset(y: configuration.isPressed ? 2 : 0)
            .shadow(
                color: Color.black.opacity(configuration.isPressed ? 0.08 : 0.14),
                radius: configuration.isPressed ? 4 : 12,
                x: 0,
                y: configuration.isPressed ? 2 : 8
            )
            .animation(.easeOut(duration: 0.15), value: configuration.isPressed)
    }
}

private struct HomeListButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .offset(y: configuration.isPressed ? 2 : 0)
            .shadow(
                color: Color.black.opacity(configuration.isPressed ? 0.08 : 0.13),
                radius: configuration.isPressed ? 4 : 10,
                x: 0,
                y: configuration.isPressed ? 2 : 8
            )
            .animation(.easeOut(duration: 0.15), value: configuration.isPressed)
    }
}

private struct HomeIconButtonStyle: ButtonStyle {
    let compact: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.93 : 1)
            .opacity(configuration.isPressed ? 0.92 : 1)
            .animation(.easeOut(duration: 0.14), value: configuration.isPressed)
    }
}

private struct HomeTdayLogoMark: View {
    var body: some View {
        GeometryReader { proxy in
            let size = min(proxy.size.width, proxy.size.height)
            let stroke = size * 0.085
            let ringWidth = size * 0.16
            let paperWidth = size * 0.64
            let paperHeight = size * 0.72
            let paperX = size * 0.18
            let paperY = size * 0.18
            let headerHeight = size * 0.2

            ZStack {
                RoundedRectangle(cornerRadius: size * 0.14, style: .continuous)
                    .fill(Color(hex: 0x90D5D2))
                    .frame(width: paperWidth, height: paperHeight)
                    .offset(x: paperX - (size / 2) + (paperWidth / 2), y: paperY - (size / 2) + (paperHeight / 2))

                RoundedRectangle(cornerRadius: size * 0.14, style: .continuous)
                    .fill(.white)
                    .frame(width: paperWidth, height: paperHeight - headerHeight)
                    .offset(x: paperX - (size / 2) + (paperWidth / 2), y: (paperY + headerHeight) - (size / 2) + ((paperHeight - headerHeight) / 2))

                RoundedRectangle(cornerRadius: size * 0.14, style: .continuous)
                    .stroke(Color(hex: 0x2D6B6B), lineWidth: stroke)
                    .frame(width: paperWidth, height: paperHeight)
                    .offset(x: paperX - (size / 2) + (paperWidth / 2), y: paperY - (size / 2) + (paperHeight / 2))

                Rectangle()
                    .fill(Color(hex: 0x2D6B6B))
                    .frame(width: paperWidth, height: stroke * 0.66)
                    .offset(x: paperX - (size / 2) + (paperWidth / 2), y: (paperY + headerHeight) - (size / 2))

                ForEach([0.28, 0.5, 0.72], id: \.self) { fraction in
                    Path { path in
                        let x = size * CGFloat(fraction)
                        path.move(to: CGPoint(x: x, y: size * 0.16))
                        path.addLine(to: CGPoint(x: x, y: size * 0.03))
                        path.addArc(
                            center: CGPoint(x: x + ringWidth * 0.35, y: size * 0.16),
                            radius: ringWidth * 0.5,
                            startAngle: .degrees(180),
                            endAngle: .degrees(0),
                            clockwise: false
                        )
                        path.addLine(to: CGPoint(x: x + ringWidth * 0.7, y: size * 0.16))
                    }
                    .stroke(Color(hex: 0x2D6B6B), style: StrokeStyle(lineWidth: stroke, lineCap: .round))
                }

                VStack(spacing: size * 0.06) {
                    ForEach(0..<4, id: \.self) { row in
                        HStack(spacing: size * 0.06) {
                            VStack(alignment: .leading, spacing: size * 0.03) {
                                Capsule()
                                    .fill(Color(hex: 0xC4C4C4))
                                    .frame(width: size * 0.16, height: stroke * 0.55)
                                Capsule()
                                    .fill(Color(hex: 0xC4C4C4))
                                    .frame(width: size * 0.11, height: stroke * 0.55)
                            }

                            HStack(spacing: size * 0.025) {
                                ForEach(0..<3, id: \.self) { _ in
                                    RoundedRectangle(cornerRadius: size * 0.018, style: .continuous)
                                        .fill(Color(hex: 0xE85B6F))
                                        .frame(width: size * 0.08, height: size * 0.09)
                                }
                            }
                        }
                    }
                }
                .offset(x: size * 0.08, y: size * 0.18)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .aspectRatio(1, contentMode: .fit)
    }
}

private struct CreateListSheet: View {
    let onSubmit: (String, String?, String?) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.tdayColors) private var colors
    @FocusState private var nameFieldFocused: Bool

    @State private var name = ""
    @State private var color = "BLUE"
    @State private var iconKey = "inbox"

    private var trimmedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var canCreate: Bool {
        !trimmedName.isEmpty
    }

    private var accentColor: Color {
        homeListAccentColor(for: color)
    }

    private var selectedSymbolName: String {
        homeListSymbolName(for: iconKey)
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 14) {
                CreateListSheetHeader(
                    canCreate: canCreate,
                    onClose: { dismiss() },
                    onConfirm: {
                        onSubmit(trimmedName, color, iconKey)
                        dismiss()
                    }
                )

                CreateListSheetSectionTitle(text: "List")
                CreateListSheetCard {
                    VStack(spacing: 18) {
                        ZStack {
                            Circle()
                                .fill(accentColor)
                                .frame(width: 86, height: 86)

                            Image(systemName: selectedSymbolName)
                                .font(.system(size: 38, weight: .semibold))
                                .foregroundStyle(.white)
                        }

                        TextField(
                            "",
                            text: $name,
                            prompt: Text("List name")
                                .foregroundStyle(colors.onSurfaceVariant.opacity(0.78))
                        )
                        .focused($nameFieldFocused)
                        .textInputAutocapitalization(.words)
                        .autocorrectionDisabled()
                        .multilineTextAlignment(.center)
                        .font(.system(size: 28, weight: .bold))
                        .foregroundStyle(accentColor)
                        .padding(.horizontal, 14)
                        .frame(maxWidth: .infinity)
                        .frame(height: 74)
                        .background(
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .fill(colors.surfaceVariant)
                        )
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 18)
                }

                CreateListSheetSectionTitle(text: "Color")
                CreateListSheetCard {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            ForEach(homeListColorOptions, id: \.key) { option in
                                let isSelected = option.key == color
                                Button {
                                    color = option.key
                                } label: {
                                    Circle()
                                        .fill(option.color)
                                        .frame(width: 48, height: 48)
                                        .overlay(
                                            Circle()
                                                .stroke(
                                                    isSelected ? colors.onSurface.opacity(0.3) : .clear,
                                                    lineWidth: 3
                                                )
                                        )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 14)
                    }
                }

                CreateListSheetSectionTitle(text: "Icon")
                CreateListSheetCard {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(homeListIconOptions, id: \.key) { option in
                                let isSelected = option.key == iconKey
                                Button {
                                    iconKey = option.key
                                } label: {
                                    Circle()
                                        .fill(isSelected ? accentColor.opacity(0.2) : colors.surfaceVariant)
                                        .frame(width: 48, height: 48)
                                        .overlay(
                                            Circle()
                                                .stroke(
                                                    isSelected ? accentColor.opacity(0.55) : .clear,
                                                    lineWidth: 2
                                                )
                                        )
                                        .overlay {
                                            Image(systemName: option.symbolName)
                                                .font(.system(size: 22, weight: .semibold))
                                                .foregroundStyle(isSelected ? accentColor : colors.onSurfaceVariant)
                                        }
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(formattedOptionName(option.key))
                            }
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 14)
                    }
                }

                Spacer(minLength: 8)
            }
            .padding(.horizontal, 18)
            .padding(.top, 14)
            .padding(.bottom, 20)
        }
        .background(colors.background.ignoresSafeArea())
        .presentationDetents([.fraction(0.78)])
        .presentationDragIndicator(.hidden)
        .presentationCornerRadius(34)
        .presentationBackground(colors.background)
        .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                nameFieldFocused = true
            }
        }
    }

    private func formattedOptionName(_ value: String) -> String {
        value
            .replacingOccurrences(of: "_", with: " ")
            .replacingOccurrences(of: ".", with: " ")
            .split(separator: " ")
            .map { $0.capitalized }
            .joined(separator: " ")
    }
}

private struct CreateListSheetHeader: View {
    let canCreate: Bool
    let onClose: () -> Void
    let onConfirm: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        HStack {
            CreateListSheetActionButton(
                icon: "xmark",
                accentColor: Color(hex: 0xE79A9A),
                enabled: true,
                action: onClose
            )

            Spacer()

            Text("New list")
                .font(.system(size: 34, weight: .bold))
                .foregroundStyle(colors.onSurface)

            Spacer()

            CreateListSheetActionButton(
                icon: "checkmark",
                accentColor: Color(hex: 0xA6D4B3),
                enabled: canCreate,
                action: onConfirm
            )
        }
    }
}

private struct CreateListSheetActionButton: View {
    let icon: String
    let accentColor: Color
    let enabled: Bool
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(colors.onSurface.opacity(enabled ? 1 : 0.4))
                .frame(width: 54, height: 54)
                .background(colors.surfaceVariant)
                .clipShape(Circle())
                .overlay(
                    Circle()
                        .stroke(accentColor.opacity(enabled ? 0.8 : 0.42), lineWidth: 1.5)
                )
        }
        .buttonStyle(CreateListSheetActionButtonStyle())
        .disabled(!enabled)
    }
}

private struct CreateListSheetActionButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.93 : 1)
            .offset(y: configuration.isPressed ? 1 : 0)
            .shadow(
                color: Color.black.opacity(configuration.isPressed ? 0.06 : 0.12),
                radius: configuration.isPressed ? 4 : 10,
                x: 0,
                y: configuration.isPressed ? 2 : 7
            )
            .animation(.easeOut(duration: 0.14), value: configuration.isPressed)
    }
}

private struct CreateListSheetSectionTitle: View {
    let text: String

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Text(text)
            .font(.system(size: 30, weight: .bold))
            .foregroundStyle(colors.onSurfaceVariant)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 4)
    }
}

private struct CreateListSheetCard<Content: View>: View {
    @Environment(\.tdayColors) private var colors

    @ViewBuilder let content: Content

    var body: some View {
        content
            .frame(maxWidth: .infinity)
            .background(
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(colors.surface)
            )
    }
}

private let homeListColorOptions: [HomeListColorOption] = [
    HomeListColorOption(key: "RED", color: Color(hex: 0xE65E52)),
    HomeListColorOption(key: "ORANGE", color: Color(hex: 0xF29F38)),
    HomeListColorOption(key: "YELLOW", color: Color(hex: 0xF3D04A)),
    HomeListColorOption(key: "LIME", color: Color(hex: 0x8ACF56)),
    HomeListColorOption(key: "BLUE", color: Color(hex: 0x5C9FE7)),
    HomeListColorOption(key: "PURPLE", color: Color(hex: 0x8D6CE2)),
    HomeListColorOption(key: "PINK", color: Color(hex: 0xDF6DAA)),
    HomeListColorOption(key: "TEAL", color: Color(hex: 0x4EB5B0)),
    HomeListColorOption(key: "CORAL", color: Color(hex: 0xE3876D)),
    HomeListColorOption(key: "GOLD", color: Color(hex: 0xCFAB57)),
    HomeListColorOption(key: "DEEP_BLUE", color: Color(hex: 0x4B73D6)),
    HomeListColorOption(key: "ROSE", color: Color(hex: 0xD9799A)),
    HomeListColorOption(key: "LIGHT_RED", color: Color(hex: 0xE48888)),
    HomeListColorOption(key: "BRICK", color: Color(hex: 0xB86A5C)),
    HomeListColorOption(key: "SLATE", color: Color(hex: 0x7B8593)),
]

private let homeListIconOptions: [HomeListIconOption] = [
    HomeListIconOption(key: "inbox", symbolName: "tray.fill"),
    HomeListIconOption(key: "sun", symbolName: "sun.max.fill"),
    HomeListIconOption(key: "calendar", symbolName: "calendar"),
    HomeListIconOption(key: "schedule", symbolName: "clock"),
    HomeListIconOption(key: "flag", symbolName: "flag.fill"),
    HomeListIconOption(key: "check", symbolName: "checkmark"),
    HomeListIconOption(key: "smile", symbolName: "face.smiling"),
    HomeListIconOption(key: "list", symbolName: "list.bullet"),
    HomeListIconOption(key: "bookmark", symbolName: "bookmark.fill"),
    HomeListIconOption(key: "key", symbolName: "key.fill"),
    HomeListIconOption(key: "gift", symbolName: "gift.fill"),
    HomeListIconOption(key: "cake", symbolName: "birthday.cake.fill"),
    HomeListIconOption(key: "school", symbolName: "graduationcap.fill"),
    HomeListIconOption(key: "bag", symbolName: "backpack.fill"),
    HomeListIconOption(key: "edit", symbolName: "pencil"),
    HomeListIconOption(key: "document", symbolName: "doc.text.fill"),
    HomeListIconOption(key: "book", symbolName: "book.closed.fill"),
    HomeListIconOption(key: "work", symbolName: "briefcase.fill"),
    HomeListIconOption(key: "wallet", symbolName: "wallet.pass.fill"),
    HomeListIconOption(key: "money", symbolName: "dollarsign.circle.fill"),
    HomeListIconOption(key: "fitness", symbolName: "dumbbell.fill"),
    HomeListIconOption(key: "run", symbolName: "figure.run"),
    HomeListIconOption(key: "food", symbolName: "fork.knife"),
    HomeListIconOption(key: "drink", symbolName: "wineglass.fill"),
    HomeListIconOption(key: "health", symbolName: "cross.case.fill"),
    HomeListIconOption(key: "monitor", symbolName: "display"),
    HomeListIconOption(key: "music", symbolName: "music.note"),
    HomeListIconOption(key: "computer", symbolName: "desktopcomputer"),
    HomeListIconOption(key: "game", symbolName: "gamecontroller.fill"),
    HomeListIconOption(key: "headphones", symbolName: "headphones"),
    HomeListIconOption(key: "eco", symbolName: "leaf.fill"),
    HomeListIconOption(key: "pets", symbolName: "pawprint.fill"),
    HomeListIconOption(key: "child", symbolName: "figure.2.and.child.holdinghands"),
    HomeListIconOption(key: "family", symbolName: "person.3.fill"),
    HomeListIconOption(key: "basket", symbolName: "basket.fill"),
    HomeListIconOption(key: "cart", symbolName: "cart.fill"),
    HomeListIconOption(key: "mall", symbolName: "bag.fill"),
    HomeListIconOption(key: "inventory", symbolName: "archivebox.fill"),
    HomeListIconOption(key: "soccer", symbolName: "soccerball"),
    HomeListIconOption(key: "baseball", symbolName: "baseball.fill"),
    HomeListIconOption(key: "basketball", symbolName: "basketball.fill"),
    HomeListIconOption(key: "football", symbolName: "football.fill"),
    HomeListIconOption(key: "tennis", symbolName: "tennis.racket"),
    HomeListIconOption(key: "train", symbolName: "tram.fill"),
    HomeListIconOption(key: "flight", symbolName: "airplane"),
    HomeListIconOption(key: "boat", symbolName: "ferry.fill"),
    HomeListIconOption(key: "car", symbolName: "car.fill"),
    HomeListIconOption(key: "umbrella", symbolName: "umbrella.fill"),
    HomeListIconOption(key: "drop", symbolName: "drop.fill"),
    HomeListIconOption(key: "snow", symbolName: "snowflake"),
    HomeListIconOption(key: "fire", symbolName: "flame.fill"),
    HomeListIconOption(key: "tools", symbolName: "hammer.fill"),
    HomeListIconOption(key: "scissors", symbolName: "scissors"),
    HomeListIconOption(key: "architecture", symbolName: "building.columns.fill"),
    HomeListIconOption(key: "code", symbolName: "chevron.left.forwardslash.chevron.right"),
    HomeListIconOption(key: "idea", symbolName: "lightbulb.fill"),
    HomeListIconOption(key: "chat", symbolName: "bubble.left.fill"),
    HomeListIconOption(key: "alert", symbolName: "exclamationmark.triangle.fill"),
    HomeListIconOption(key: "star", symbolName: "star.fill"),
    HomeListIconOption(key: "heart", symbolName: "heart.fill"),
    HomeListIconOption(key: "circle", symbolName: "circle.fill"),
    HomeListIconOption(key: "square", symbolName: "square.fill"),
    HomeListIconOption(key: "triangle", symbolName: "triangle.fill"),
    HomeListIconOption(key: "home", symbolName: "house.fill"),
    HomeListIconOption(key: "city", symbolName: "building.2.fill"),
    HomeListIconOption(key: "bank", symbolName: "building.columns.fill"),
    HomeListIconOption(key: "camera", symbolName: "camera.fill"),
    HomeListIconOption(key: "palette", symbolName: "paintpalette.fill"),
]

private func homeListAccentColor(for key: String?) -> Color {
    homeListColorOptions.first(where: { $0.key == key })?.color ?? Color(hex: 0xE9A03B)
}

private func homeListSymbolName(for key: String?) -> String {
    homeListIconOptions.first(where: { $0.key == key })?.symbolName ?? "tray.fill"
}

private extension Color {
    init(hex: UInt, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }

    func blended(with other: Color, amount: CGFloat) -> Color {
        let lhs = UIColor(self)
        let rhs = UIColor(other)
        var lhsRed: CGFloat = 0
        var lhsGreen: CGFloat = 0
        var lhsBlue: CGFloat = 0
        var lhsAlpha: CGFloat = 0
        var rhsRed: CGFloat = 0
        var rhsGreen: CGFloat = 0
        var rhsBlue: CGFloat = 0
        var rhsAlpha: CGFloat = 0

        lhs.getRed(&lhsRed, green: &lhsGreen, blue: &lhsBlue, alpha: &lhsAlpha)
        rhs.getRed(&rhsRed, green: &rhsGreen, blue: &rhsBlue, alpha: &rhsAlpha)

        let mix = amount.clamped(to: 0...1)
        return Color(
            uiColor: UIColor(
                red: lhsRed + ((rhsRed - lhsRed) * mix),
                green: lhsGreen + ((rhsGreen - lhsGreen) * mix),
                blue: lhsBlue + ((rhsBlue - lhsBlue) * mix),
                alpha: lhsAlpha + ((rhsAlpha - lhsAlpha) * mix)
            )
        )
    }
}

private extension CGFloat {
    func clamped(to range: ClosedRange<CGFloat>) -> CGFloat {
        Swift.min(Swift.max(self, range.lowerBound), range.upperBound)
    }
}
