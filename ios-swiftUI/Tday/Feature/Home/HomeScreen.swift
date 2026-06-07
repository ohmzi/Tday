import SwiftUI

private enum HomeMetrics {
    static let screenPadding: CGFloat = 18
    static let sectionSpacing: CGFloat = 14
    static let tileGap: CGFloat = 10
    static let topBarButtonSize: CGFloat = 56
    static let compactButtonSize: CGFloat = 30
    static let titleAnchorDistance: CGFloat = screenPadding + topBarButtonSize
    static let tileCornerRadius: CGFloat = 26
    static let tileHeight: CGFloat = 94
    static let tileInnerPadding: CGFloat = 12
    static let todayCardHeight: CGFloat = 70
    static let listRowHeight: CGFloat = 70
    static let rootDockCollapseThreshold: CGFloat = 44
    static let listContainerColorWeight: CGFloat = 0.66
    static let tileWatermarkSize: CGFloat = 116
    static let tileWatermarkTrailingInset: CGFloat = 22
}

private func isHomeDaytime(_ date: Date) -> Bool {
    let hour = Calendar.current.component(.hour, from: date)
    return (6..<18).contains(hour)
}

private func normalizedHomeSearchQuery(_ value: String) -> String {
    value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(with: .current)
}

private func homeSearchText(_ value: String) -> String {
    value.lowercased(with: .current)
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

private enum CreateListSheetMetrics {
    static let sheetHeight: CGFloat = 620
    static let maximumHeightFraction: CGFloat = TdaySheetMetrics.maximumScreenHeightFraction
    static let bottomContentPadding: CGFloat = 8
}

private let homeScrollTopID = "home-scroll-top"

struct HomeScreen: View {
    let onRootFeedTabSelected: (RootFeedTab) -> Void
    let showsRootControls: Bool
    let createTaskRequestID: Int
    let scrollToTopRequestID: Int
    let onRootDockCollapsedChange: (Bool) -> Void
    let onRootControlsVisibleChange: (Bool) -> Void
    let pullRefreshEnabled: Bool
    let summaryAvailable: Bool
    let onNavigate: (AppRoute) -> Void

    @State private var viewModel: HomeViewModel
    @Environment(\.tdayColors) private var colors
    @FocusState private var searchFieldFocused: Bool

    @State private var searchExpanded = false
    @State private var searchQuery = ""
    @State private var searchBarFrame: CGRect = .zero
    @State private var searchResultsFrame: CGRect = .zero
    @State private var openingSearchResultID: String?
    @State private var showingCreateTask = false
    @State private var lastHandledCreateTaskRequestID = 0
    @State private var showingCreateList = false
    @State private var showingSummary = false
    @State private var editingTodo: TodoItem?
    @State private var homeScrollOffset: CGFloat = 0
    @State private var openSwipeTaskID: String?

    init(
        container: AppContainer,
        onRootFeedTabSelected: @escaping (RootFeedTab) -> Void = { _ in },
        showsRootControls: Bool = true,
        createTaskRequestID: Int = 0,
        scrollToTopRequestID: Int = 0,
        onRootDockCollapsedChange: @escaping (Bool) -> Void = { _ in },
        onRootControlsVisibleChange: @escaping (Bool) -> Void = { _ in },
        pullRefreshEnabled: Bool = false,
        summaryAvailable: Bool = true,
        onNavigate: @escaping (AppRoute) -> Void
    ) {
        self.onRootFeedTabSelected = onRootFeedTabSelected
        self.showsRootControls = showsRootControls
        self.createTaskRequestID = createTaskRequestID
        self.scrollToTopRequestID = scrollToTopRequestID
        self.onRootDockCollapsedChange = onRootDockCollapsedChange
        self.onRootControlsVisibleChange = onRootControlsVisibleChange
        self.pullRefreshEnabled = pullRefreshEnabled
        self.summaryAvailable = summaryAvailable
        self.onNavigate = onNavigate
        _viewModel = State(initialValue: HomeViewModel(container: container))
    }

    private var normalizedSearchQuery: String {
        normalizedHomeSearchQuery(searchQuery)
    }

    private var listByID: [String: ListSummary] {
        Dictionary(viewModel.summary.lists.map { ($0.id, $0) }, uniquingKeysWith: { _, latest in latest })
    }

    private var filteredTodos: [TodoItem] {
        guard !normalizedSearchQuery.isEmpty else {
            return []
        }

        return viewModel.searchableTodos.filter { todo in
            homeSearchText(todo.title).contains(normalizedSearchQuery) ||
                (todo.description.map { homeSearchText($0).contains(normalizedSearchQuery) } ?? false) ||
                (todo.listId.flatMap { listByID[$0]?.name }.map { homeSearchText($0).contains(normalizedSearchQuery) } ?? false)
        }
        .sorted {
            ($0.due ?? .distantFuture) < ($1.due ?? .distantFuture)
        }
        .prefix(20)
        .map { $0 }
    }

    private var overdueCount: Int {
        let now = Date()
        return viewModel.searchableTodos.count { ($0.due ?? .distantFuture) < now }
    }

    private var showSearchResultsOverlay: Bool {
        searchExpanded && !normalizedSearchQuery.isEmpty
    }

    private var shouldCollapseRootDock: Bool {
        max(homeScrollOffset, 0) > HomeMetrics.rootDockCollapseThreshold
    }

    var body: some View {
        GeometryReader { proxy in
            let fallbackSearchBarFrame = CGRect(
                x: HomeMetrics.screenPadding,
                y: HomeMetrics.screenPadding,
                width: max(HomeMetrics.topBarButtonSize, proxy.size.width - (HomeMetrics.screenPadding * 2)),
                height: HomeMetrics.topBarButtonSize
            )
            let activeSearchBarFrame = searchBarFrame.width > 0 && searchBarFrame.height > 0
                ? searchBarFrame
                : fallbackSearchBarFrame

            ZStack(alignment: .topLeading) {
                TimelineView(.periodic(from: .now, by: 60)) { context in
                    let daytime = isHomeDaytime(context.date)
                    EmptyTaskWatermark(
                        systemName: daytime ? "sun.max.fill" : "moon.stars.fill",
                        accentColor: Color.tdayTodayBlue
                    )
                }

                PullToRefreshContainer(
                    isRefreshing: viewModel.isLoading,
                    isEnabled: pullRefreshEnabled,
                    action: {
                        await viewModel.refresh()
                    }
                ) {
                    ScrollViewReader { scrollProxy in
                        ScrollView(showsIndicators: false) {
                            LazyVStack(alignment: .leading, spacing: HomeMetrics.sectionSpacing) {
                                TimelineScrollOffsetObserver { homeScrollOffset = $0 }
                                    .frame(height: 0)
                                    .allowsHitTesting(false)
                                    .id(homeScrollTopID)

                                HomeTopBar(
                                    totalWidth: proxy.size.width - (HomeMetrics.screenPadding * 2),
                                    searchExpanded: $searchExpanded,
                                    searchQuery: $searchQuery,
                                    searchFieldFocused: $searchFieldFocused,
                                    onSearchClose: {
                                        closeSearch()
                                    },
                                    onCreateList: {
                                        closeSearch()
                                        showingCreateList = true
                                    },
                                    onOpenSettings: {
                                        closeSearch()
                                        onNavigate(.settings)
                                    }
                                )
                                .onTopPartialScrollSnap(
                                    anchorDistance: HomeMetrics.titleAnchorDistance,
                                    isDisabled: searchExpanded
                                )

                                HomeTodayCard(
                                    count: viewModel.summary.todayCount,
                                    action: {
                                        closeSearch()
                                        onNavigate(.todayTodos)
                                    }
                                )

                                if !viewModel.todayTodos.isEmpty {
                                    VStack(spacing: 0) {
                                        ForEach(viewModel.todayTodos) { todo in
                                            homeTodayTaskRow(todo)
                                                .transition(.opacity.combined(with: .move(edge: .top)))
                                        }
                                    }
                                    .animation(
                                        .spring(response: 0.34, dampingFraction: 0.9),
                                        value: viewModel.todayTodos.map(\.id)
                                    )
                                }

                                HomeCategoryBoard(
                                    overdueCount: overdueCount,
                                    scheduledCount: viewModel.summary.scheduledCount,
                                    allCount: viewModel.summary.allCount,
                                    priorityCount: viewModel.summary.priorityCount,
                                    completedCount: viewModel.summary.completedCount,
                                    calendarCount: viewModel.summary.scheduledCount,
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
                                    HomeListsSection(
                                        lists: viewModel.summary.lists,
                                        displayName: displayName(for:)
                                    ) { list, name in
                                        closeSearch()
                                        onNavigate(.listTodos(listId: list.id, listName: name))
                                    }
                                }

                                if let errorMessage = viewModel.errorMessage {
                                    ErrorRetryView(message: errorMessage) {
                                        Task { await viewModel.refresh() }
                                    }
                                }

                            }
                            .padding(.horizontal, HomeMetrics.screenPadding)
                            .padding(.top, HomeMetrics.screenPadding)

                        }
                        .scrollBounceBehavior(.always, axes: .vertical)
                        .safeAreaInset(edge: .bottom) {
                            if showsRootControls {
                                HStack(alignment: .bottom) {
                                    RootFeedDock(
                                        activeTab: .home,
                                        collapsed: shouldCollapseRootDock,
                                        accentColor: .tdayTodayBlue,
                                        onSelect: onRootFeedTabSelected
                                    )
                                    .padding(.leading, 18)
                                    .padding(.vertical, 8)

                                    Spacer(minLength: 12)

                                    TaskFloatingActionButton {
                                        HapticManager.buttonTap()
                                        closeSearch()
                                        showingCreateTask = true
                                    }
                                    .padding(.trailing, 18)
                                    .padding(.vertical, 8)
                                }
                            } else {
                                Color.clear.frame(height: 80)
                            }
                        }
                        .onChange(of: scrollToTopRequestID) { _, requestID in
                            guard requestID > 0 else { return }
                            closeSearch()
                            withAnimation(.easeInOut(duration: 0.34)) {
                                scrollProxy.scrollTo(homeScrollTopID, anchor: .top)
                            }
                        }
                    }
                }

                if showSearchResultsOverlay {
                    HomeSearchResultsOverlay(
                        todos: filteredTodos,
                        listsByID: listByID,
                        onOpenTodo: { todo in
                            openSearchResult(todo)
                        }
                    )
                    .frame(width: activeSearchBarFrame.width)
                    .offset(x: activeSearchBarFrame.minX, y: activeSearchBarFrame.maxY + 8)
                    .background(
                        GeometryReader { resultsProxy in
                            Color.clear
                                .preference(
                                    key: HomeSearchResultsFrameKey.self,
                                    value: resultsProxy.frame(in: .named("home-root"))
                            )
                        }
                    )
                    .zIndex(100)
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
            onRootControlsVisibleChange(!expanded)
            if expanded {
                searchFieldFocused = false
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.30) {
                    if searchExpanded {
                        searchFieldFocused = true
                    }
                }
            } else {
                searchFieldFocused = false
                searchResultsFrame = .zero
            }
        }
        .onChange(of: homeScrollOffset, initial: true) { _, offset in
            onRootDockCollapsedChange(max(offset, 0) > HomeMetrics.rootDockCollapseThreshold)
        }
        .onChange(of: createTaskRequestID) { _, requestID in
            handleCreateTaskRequest(requestID)
        }
        .onChange(of: viewModel.todayTodos.map(\.id)) { _, ids in
            guard let openSwipeTaskID, !ids.contains(openSwipeTaskID) else { return }
            self.openSwipeTaskID = nil
        }
        .onAppear {
            onRootControlsVisibleChange(!searchExpanded)
            onRootDockCollapsedChange(shouldCollapseRootDock)
        }
        .onDisappear {
            onRootControlsVisibleChange(true)
        }
        .createTaskSheet(isPresented: $showingCreateTask) {
            CreateTaskSheet(
                lists: viewModel.summary.lists,
                titleText: L("New task"),
                submitText: L("Create"),
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
        .tdayBottomSheetPresentation(isPresented: $showingCreateList) {
            CreateListSheet { name, color, iconKey in
                Task {
                    await viewModel.createList(name: name, color: color, iconKey: iconKey)
                }
            }
        }
        .sheet(isPresented: $showingSummary) {
            summarySheetContent
                .presentationDetents([.medium])
                .presentationDragIndicator(.hidden)
        }
        .createTaskSheet(item: $editingTodo) { todo in
            CreateTaskSheet(
                lists: viewModel.lists,
                titleText: L("Edit task"),
                submitText: L("Save"),
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
        .navigationBackButtonBehavior()
    }

    private func closeSearch() {
        searchFieldFocused = false
        withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
            searchExpanded = false
        }
        searchQuery = ""
        searchResultsFrame = .zero
    }

    private func handleCreateTaskRequest(_ requestID: Int) {
        guard requestID > 0, requestID > lastHandledCreateTaskRequestID else {
            return
        }
        lastHandledCreateTaskRequestID = requestID
        closeSearch()
        showingCreateTask = true
    }

    @ViewBuilder
    private func homeTodayTaskRow(_ todo: TodoItem) -> some View {
        HomeTodayTaskRow(
            todo: todo,
            lists: viewModel.lists,
            onComplete: { await viewModel.complete(todo) },
            onDelete: { Task { await viewModel.delete(todo) } },
            onEdit: { editingTodo = todo },
            openSwipeTaskID: $openSwipeTaskID
        )
    }

    private func openSearchResult(_ todo: TodoItem) {
        guard openingSearchResultID == nil else {
            return
        }
        openingSearchResultID = todo.id
        closeSearch()
        onNavigate(.allTodos(highlightTodoId: todo.id))
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            openingSearchResultID = nil
        }
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

    private var summarySheetContent: some View {
        VStack(spacing: 0) {
            TdaySheetHeader(
                title: L("Summary"),
                closeAccessibilityLabel: L("Close"),
                confirmSystemName: nil,
                onClose: { showingSummary = false }
            )

            ScrollView {
                TdaySheetCard {
                    VStack(alignment: .leading, spacing: 12) {
                        if !summaryAvailable {
                            Text("No summary available while offline.")
                                .foregroundStyle(colors.error)
                        } else if viewModel.isSummarizing {
                            ProgressView()
                        } else if let summaryText = viewModel.summaryText {
                            Text(summaryText)
                                .font(.tdayRounded(.body, weight: .bold))
                                .frame(maxWidth: .infinity, alignment: .leading)

                            if let source = viewModel.summarySource {
                                Text(source == "ai" ? L("Server model") : L("Local model"))
                                    .font(.tdayRounded(size: 11, weight: .heavy))
                                    .foregroundStyle(colors.onSurface.opacity(0.5))
                                    .textCase(.uppercase)
                                    .frame(maxWidth: .infinity, alignment: .center)
                                    .padding(.top, 4)
                            }
                        } else if let summaryError = viewModel.summaryError {
                            Text(summaryError)
                                .foregroundStyle(colors.error)
                        } else {
                            Text("No summary available.")
                        }
                    }
                    .padding(18)
                }
                .padding(.horizontal, 18)
                .padding(.top, 14)
                .padding(.bottom, 24)
            }
        }
    }

    private func presentSummary() {
        showingSummary = true
        Task {
            await viewModel.summarizeToday()
        }
    }
}

private struct HomeTopBar: View {
    let totalWidth: CGFloat
    @Binding var searchExpanded: Bool
    @Binding var searchQuery: String
    var searchFieldFocused: FocusState<Bool>.Binding
    let onSearchClose: () -> Void
    let onCreateList: () -> Void
    let onOpenSettings: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        let buttonSize = HomeMetrics.topBarButtonSize
        let buttonGap: CGFloat = 8
        let actionCount: CGFloat = 2
        let expandedSearchWidth = max(buttonSize, totalWidth)
        let searchWidth = searchExpanded ? expandedSearchWidth : buttonSize
        let collapsedSearchOffset = -((buttonSize * actionCount) + (buttonGap * actionCount))
        let searchOffsetX = searchExpanded ? 0 : collapsedSearchOffset

        ZStack(alignment: .trailing) {
            TimelineView(.periodic(from: .now, by: 60)) { context in
                let daytime = isHomeDaytime(context.date)

                HStack(spacing: 8) {
                    Image(systemName: daytime ? "sun.max.fill" : "moon.stars.fill")
                        .font(.system(size: 26, weight: .regular))
                        .foregroundStyle(Color(hex: daytime ? 0xF4C542 : 0xA8B8E8))

                    Text("T'Day")
                        .font(.tdayRounded(size: 32, weight: .heavy))
                        .foregroundStyle(colors.onSurface)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.leading, 2)
                .opacity(searchExpanded ? 0 : 1)
                .allowsHitTesting(false)
            }

            HStack(spacing: buttonGap) {
                HomeIconCircleButton(icon: "NavListPlus") {
                    HapticManager.buttonTap()
                    onCreateList()
                }

                HomeIconCircleButton(icon: "NavEllipsis") {
                    HapticManager.gentleTap()
                    onOpenSettings()
                }
            }
            .opacity(searchExpanded ? 0 : 1)
            .allowsHitTesting(!searchExpanded)

            ZStack {
                Button {
                    HapticManager.buttonTap()
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
                        searchExpanded = true
                    }
                } label: {
                    Image("NavSearch")
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 22, height: 22)
                        .foregroundStyle(colors.onSurface)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                .buttonStyle(HomeIconButtonStyle(compact: false))
                .opacity(searchExpanded ? 0 : 1)
                .allowsHitTesting(!searchExpanded)
                .accessibilityLabel("Search")

                HStack(spacing: 10) {
                    Image("NavSearch")
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 20, height: 20)
                        .foregroundStyle(colors.onSurface)
                        .frame(width: HomeMetrics.compactButtonSize, height: HomeMetrics.compactButtonSize)

                    TextField("", text: $searchQuery, prompt: Text("Search").foregroundStyle(colors.onSurfaceVariant))
                        .focused(searchFieldFocused)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .font(.tdayRounded(size: 18, weight: .bold))
                        .foregroundStyle(colors.onSurface)
                        .tint(colors.primary)
                        .disabled(!searchExpanded)

                    Button {
                        HapticManager.sheetDismiss()
                        onSearchClose()
                    } label: {
                        Image("NavClose")
                            .renderingMode(.template)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 18, height: 18)
                            .foregroundStyle(colors.onSurfaceVariant.opacity(0.78))
                    }
                    .buttonStyle(
                        TdayPressButtonStyle(
                            shadowColor: Color.black,
                            pressedShadowOpacity: 0,
                            normalShadowOpacity: 0
                        )
                    )
                    .accessibilityLabel("Cancel search")
                }
                .padding(.horizontal, 14)
                .opacity(searchExpanded ? 1 : 0)
                .allowsHitTesting(searchExpanded)
            }
            .frame(width: searchWidth, height: buttonSize)
            .background(colors.surface, in: Capsule())
            .overlay(
                Capsule()
                    .stroke(colors.onSurface.opacity(0.26), lineWidth: 1)
            )
            .offset(x: searchOffsetX)
            .background(
                GeometryReader { proxy in
                    Color.clear
                        .preference(key: HomeSearchBarFrameKey.self, value: proxy.frame(in: .named("home-root")))
                }
            )
            .zIndex(2)
            .animation(.spring(response: 0.28, dampingFraction: 0.86), value: searchExpanded)
        }
        .frame(maxWidth: .infinity, minHeight: HomeMetrics.topBarButtonSize)
    }
}

private struct HomeIconCircleButton: View {
    /// Asset-catalog name of the lucide template glyph (shared with web/Android).
    let icon: String
    var compact = false
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: action) {
            Image(icon)
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(width: compact ? 20 : 22, height: compact ? 20 : 22)
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

private enum HomeTodayTaskCompletionPhase {
    case active
    case checked
    case struck
    case fading
}

private struct HomeTodayTaskRow: View {
    let todo: TodoItem
    let lists: [ListSummary]
    let onComplete: () async -> Void
    let onDelete: () -> Void
    let onEdit: () -> Void
    @Binding var openSwipeTaskID: String?

    @Environment(\.tdayColors) private var colors

    @State private var completionPhase = HomeTodayTaskCompletionPhase.active

    private var listMeta: ListSummary? {
        todo.listId.flatMap { id in lists.first { $0.id == id } }
    }

    private var priorityIcon: String? { priorityIndicatorSymbolName(todo.priority) }
    private var isOverdue: Bool { !todo.completed && (todo.due ?? .distantFuture) < Date() }
    private var dueText: String? { todo.due?.formatted(.dateTime.hour().minute().locale(AppLocale.current)) }
    private var subtitleText: String? {
        guard let dueText else { return nil }
        return isOverdue ? "Overdue, \(dueText)" : "Due \(dueText)"
    }
    private var subtitleColor: Color { isOverdue ? colors.error : colors.onSurfaceVariant.opacity(0.8) }
    private var isCompleting: Bool { completionPhase != .active }
    private var isFading: Bool { completionPhase == .fading }
    private var showCheckmark: Bool { completionPhase != .active || todo.completed }
    private var showStrikethrough: Bool { completionPhase == .struck || completionPhase == .fading || todo.completed }
    private var titleColor: Color {
        showStrikethrough ? colors.onSurface.opacity(0.78) : colors.onSurface
    }

    var body: some View {
        rowContent
            .todoTrailingSwipeActions(
                rowID: todo.id,
                openRowID: $openSwipeTaskID,
                enabled: !isCompleting,
                onEdit: onEdit,
                onDelete: onDelete
            )
        .opacity(isFading ? 0 : 1)
        .scaleEffect(isFading ? 0.985 : 1, anchor: .center)
        .offset(y: isFading ? -10 : 0)
        .animation(.easeInOut(duration: 0.26), value: isFading)
        .allowsHitTesting(!isCompleting)
    }

    private var rowContent: some View {
        HStack(alignment: .center, spacing: 12) {
            Button(action: startCompletion) {
                Image(systemName: showCheckmark ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 24, weight: .regular))
                    .foregroundStyle(showCheckmark ? Color.green : colors.onSurfaceVariant.opacity(0.78))
                    .frame(width: 38, height: 38)
            }
            .buttonStyle(TdayPressButtonStyle(shadowColor: .black, pressedShadowOpacity: 0, normalShadowOpacity: 0))
            .disabled(isCompleting)

            VStack(alignment: .leading, spacing: 3) {
                HomeTodayTaskTitle(
                    text: todo.title,
                    isCompleted: showStrikethrough,
                    titleColor: titleColor,
                    strikeColor: colors.onSurface.opacity(0.65)
                )

                if let subtitleText {
                    Text(subtitleText)
                        .font(.tdayRounded(size: 13, weight: .semibold))
                        .foregroundStyle(subtitleColor)
                }
            }

            Spacer(minLength: 0)

            if listMeta != nil || priorityIcon != nil {
                HStack(spacing: 8) {
                    if let listMeta {
                        TdayListIcon(iconKey: listMeta.iconKey, size: 14)
                            .foregroundStyle(homeListAccentColor(for: listMeta.color))
                    }
                    if let priorityIcon {
                        Image(systemName: priorityIcon)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(priorityColor(todo.priority))
                    }
                }
                .padding(.trailing, 8)
            }
        }
        .padding(.vertical, 10)
        .padding(.horizontal, 4)
        .contentShape(Rectangle())
    }

    private func startCompletion() {
        guard completionPhase == .active else { return }
        if openSwipeTaskID == todo.id {
            openSwipeTaskID = nil
        }

        HapticManager.taskCompleted()
        withAnimation(.easeInOut(duration: 0.18)) {
            completionPhase = .checked
        }

        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 160_000_000)
            withAnimation(.easeInOut(duration: 0.22)) {
                completionPhase = .struck
            }
            try? await Task.sleep(nanoseconds: 360_000_000)
            withAnimation(.easeInOut(duration: 0.26)) {
                completionPhase = .fading
            }
            try? await Task.sleep(nanoseconds: 260_000_000)
            await onComplete()
            if completionPhase == .fading {
                withAnimation(.easeInOut(duration: 0.16)) {
                    completionPhase = .active
                }
            }
        }
    }
}

private struct HomeTodayTaskTitle: View {
    let text: String
    let isCompleted: Bool
    let titleColor: Color
    let strikeColor: Color

    private var strikeProgress: CGFloat {
        isCompleted ? 1 : 0
    }

    var body: some View {
        Text(text)
            .font(.tdayRounded(size: 18, weight: .bold))
            .foregroundStyle(titleColor)
            .lineLimit(1)
            .overlay {
                GeometryReader { proxy in
                    Rectangle()
                        .fill(strikeColor)
                        .frame(width: proxy.size.width * strikeProgress, height: 1.4)
                        .position(
                            x: (proxy.size.width * strikeProgress) / 2,
                            y: proxy.size.height * 0.55
                        )
                }
                .allowsHitTesting(false)
            }
            .animation(.easeInOut(duration: 0.32), value: isCompleted)
    }
}

private struct HomeTodayCard: View {
    let count: Int
    let action: () -> Void

    private var dateLabel: String {
        Date.now.formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day().locale(AppLocale.current))
    }

    var body: some View {
        let color = Color(hex: 0x6EA8E1)
        let shape = RoundedRectangle(cornerRadius: HomeMetrics.tileCornerRadius, style: .continuous)

        Button(action: action) {
            ZStack {
                shape.fill(color)
                shape.fill(
                    RadialGradient(
                        colors: [Color.white.opacity(0.1), Color.white.opacity(0.03), .clear],
                        center: UnitPoint(x: 0.22, y: 0.2),
                        startRadius: 0,
                        endRadius: 200
                    )
                )

                HStack {
                    Text(dateLabel)
                        .font(.tdayRounded(size: 22, weight: .bold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)

                    Spacer()

                    Text("\(count)")
                        .font(.tdayRounded(size: 34, weight: .black))
                        .foregroundStyle(.white)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
            }
            .frame(maxWidth: .infinity)
            .frame(height: HomeMetrics.todayCardHeight)
            .clipShape(shape)
            .contentShape(shape)
        }
        .buttonStyle(HomeTileButtonStyle())
    }
}

private struct HomeCategoryBoard: View {
    let overdueCount: Int
    let scheduledCount: Int
    let allCount: Int
    let priorityCount: Int
    let completedCount: Int
    let calendarCount: Int
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
                    color: Color(hex: 0xD98F4B),
                    icon: "TileScheduled",
                    watermark: "TileScheduled",
                    title: L("Scheduled"),
                    count: scheduledCount,
                    action: onOpenScheduled
                )

                HomeCategoryTile(
                    color: Color(hex: 0xC97880),
                    icon: "TilePriority",
                    watermark: "TilePriority",
                    title: L("Priority"),
                    count: priorityCount,
                    action: onOpenPriority
                )
            }

            HStack(spacing: HomeMetrics.tileGap) {
                HomeCategoryTile(
                    color: Color(hex: 0xE06F66),
                    icon: "TileOverdue",
                    watermark: "TileOverdue",
                    title: L("Overdue"),
                    count: overdueCount,
                    action: onOpenOverdue
                )

                HomeCategoryTile(
                    color: Color(hex: 0x68717A),
                    icon: "TileAll",
                    watermark: "TileAll",
                    title: L("All"),
                    count: allCount,
                    action: onOpenAll
                )
            }

            HStack(spacing: HomeMetrics.tileGap) {
                HomeCategoryTile(
                    color: Color(hex: 0x719F84),
                    icon: "TileComplete",
                    watermark: "TileComplete",
                    title: L("Completed"),
                    count: completedCount,
                    action: onOpenCompleted
                )

                HomeCategoryTile(
                    color: Color(hex: 0x9A89D2),
                    icon: "TileCalendar",
                    watermark: "TileCalendar",
                    title: L("Calendar"),
                    count: calendarCount,
                    action: onOpenCalendar
                )
            }
        }
    }
}

private struct HomeCategoryTile: View {
    let color: Color
    /// Asset-catalog name of the lucide glyph (template-rendered), shared with web.
    let icon: String
    let watermark: String?
    let title: String
    let count: Int
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
                                Color.white.opacity(0.1),
                                Color.white.opacity(0.03),
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

                if let watermark {
                    Image(watermark)
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .frame(width: HomeMetrics.tileWatermarkSize, height: HomeMetrics.tileWatermarkSize)
                        .foregroundStyle(color.blended(with: .white, amount: 0.28).opacity(0.4))
                        .offset(x: HomeMetrics.tileWatermarkTrailingInset, y: 12)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
                        .allowsHitTesting(false)
                }

                VStack(alignment: .leading, spacing: 6) {
                    HStack(alignment: .center) {
                        Image(icon)
                            .renderingMode(.template)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 24, height: 24)
                            .foregroundStyle(.white)
                        Spacer()
                        Text("\(count)")
                            .font(.tdayRounded(size: 26, weight: .black))
                            .foregroundStyle(.white)
                    }

                    Text(title)
                        .font(.tdayRounded(size: 20, weight: .bold))
                        .foregroundStyle(.white)
                }
                .padding(HomeMetrics.tileInnerPadding)
            }
            .frame(maxWidth: .infinity, alignment: .topLeading)
            .frame(height: HomeMetrics.tileHeight)
            .clipShape(shape)
            .contentShape(shape)
        }
        .buttonStyle(HomeTileButtonStyle())
    }
}

private struct HomeListsHeader: View {
    @Environment(\.tdayColors) private var colors

    var body: some View {
        Text("My Lists")
            .font(.tdayRounded(size: 28, weight: .bold))
            .foregroundStyle(colors.onSurface)
            .padding(.top, 2)
    }
}

private struct HomeListsSection: View {
    let lists: [ListSummary]
    let displayName: (String) -> String
    let onOpenList: (ListSummary, String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: HomeMetrics.sectionSpacing) {
            HomeListsHeader()

            ForEach(lists) { list in
                let name = displayName(list.name)
                HomeListRow(
                    name: name,
                    colorKey: list.color,
                    iconKey: list.iconKey,
                    count: list.todoCount
                ) {
                    onOpenList(list, name)
                }
            }
        }
    }
}

private struct HomeListRow: View {
    let name: String
    let colorKey: String?
    let iconKey: String?
    let count: Int
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    private var accent: Color {
        homeListAccentColor(for: colorKey)
    }

    private var symbolName: String {
        homeListSymbolName(for: iconKey)
    }

    private var containerColor: Color {
        colors.surfaceVariant.blended(with: accent, amount: HomeMetrics.listContainerColorWeight)
    }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: HomeMetrics.tileCornerRadius, style: .continuous)

        Button(action: action) {
            ZStack {
                shape
                    .fill(containerColor)

                shape
                    .fill(
                        RadialGradient(
                            colors: [
                                Color.white.opacity(0.1),
                                Color.white.opacity(0.03),
                                .clear,
                            ],
                            center: .topLeading,
                            startRadius: 8,
                            endRadius: 120
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

                TdayListIcon(iconKey: iconKey, size: 60)
                    .foregroundStyle(containerColor.blended(with: .white, amount: 0.34).opacity(0.42))
                    .offset(x: 18, y: 8)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
                    .allowsHitTesting(false)

                HStack {
                    HStack(spacing: 10) {
                        TdayListIcon(iconKey: iconKey, size: 22)
                            .foregroundStyle(.white)
                            .frame(width: 32, height: 32)

                        Text(name)
                            .font(.tdayRounded(size: 22, weight: .bold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                    }

                    Spacer()

                    Text("\(count)")
                        .font(.tdayRounded(size: 22, weight: .bold))
                        .foregroundStyle(.white)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .frame(maxWidth: .infinity, minHeight: HomeMetrics.listRowHeight, maxHeight: HomeMetrics.listRowHeight)
            .clipShape(shape)
            .contentShape(shape)
        }
        .buttonStyle(HomeListButtonStyle())
    }
}

private struct HomeSearchResultsOverlay: View {
    let todos: [TodoItem]
    let listsByID: [String: ListSummary]
    let onOpenTodo: (TodoItem) -> Void

    @Environment(\.tdayColors) private var colors
    private let maxResultsHeight: CGFloat = 320
    private let resultRowHeight: CGFloat = 66
    private let resultVerticalPadding: CGFloat = 8
    private let resultSeparatorHeight: CGFloat = 1

    private var resultSeparatorCount: Int {
        todos.indices.filter { shouldShowDateDivider(after: $0) }.count
    }

    private var resultsHeight: CGFloat {
        let contentHeight = (CGFloat(todos.count) * resultRowHeight) +
            (CGFloat(resultSeparatorCount) * resultSeparatorHeight) +
            resultVerticalPadding
        return min(contentHeight, maxResultsHeight)
    }

    private var dueFormatter: DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = AppLocale.current
        formatter.setLocalizedDateFormatFromTemplate("EEE jmm")
        return formatter
    }

    var body: some View {
        VStack(spacing: 0) {
            if todos.isEmpty {
                Text("No matching tasks")
                    .font(.tdayRounded(size: 14, weight: .bold))
                    .foregroundStyle(colors.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
            } else {
                ScrollView(showsIndicators: true) {
                    VStack(spacing: 0) {
                        ForEach(Array(todos.enumerated()), id: \.element.id) { index, todo in
                            let list = todo.listId.flatMap { listsByID[$0] }
                            let tint = homeListAccentColor(for: list?.color)

                            HStack(spacing: 10) {
                                TdayListIcon(iconKey: list?.iconKey, size: 17)
                                    .foregroundStyle(tint.opacity(0.92))
                                    .frame(width: 18)

                                VStack(alignment: .leading, spacing: 3) {
                                    Text(todo.title)
                                        .font(.tdayRounded(size: 15, weight: .bold))
                                        .foregroundStyle(colors.onSurface)
                                        .lineLimit(1)

                                    Text(todo.due.map(dueFormatter.string(from:)) ?? "")
                                        .font(.tdayRounded(size: 12, weight: .bold))
                                        .foregroundStyle(colors.onSurfaceVariant)
                                        .lineLimit(1)
                                }

                                Spacer(minLength: 0)
                            }
                            .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 9)
                            .contentShape(Rectangle())
                            .onTapGesture {
                                onOpenTodo(todo)
                            }
                            .accessibilityElement(children: .combine)
                            .accessibilityAddTraits(.isButton)

                            if shouldShowDateDivider(after: index) {
                                Rectangle()
                                    .fill(colors.onSurface.opacity(0.08))
                                    .frame(height: 1)
                                    .padding(.horizontal, 12)
                            }
                        }
                    }
                    .padding(.vertical, 4)
                }
                .frame(height: resultsHeight)
                .scrollBounceBehavior(.basedOnSize, axes: .vertical)
            }
        }
        .background(colors.surface, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(colors.onSurface.opacity(0.2), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.14), radius: 10, x: 0, y: 8)
    }

    private func shouldShowDateDivider(after index: Int) -> Bool {
        guard todos.indices.contains(index),
              todos.indices.contains(index + 1) else {
            return false
        }
        guard let currentDue = todos[index].due,
              let nextDue = todos[index + 1].due else {
            return false
        }
        return !Calendar.current.isDate(currentDue, inSameDayAs: nextDue)
    }
}

private struct HomeTileButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .tdayPressEffect(
                isPressed: configuration.isPressed,
                shadowColor: Color.black,
                pressedShadowOpacity: 0.08,
                normalShadowOpacity: 0.14
            )
    }
}

private struct HomeListButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .tdayPressEffect(
                isPressed: configuration.isPressed,
                shadowColor: Color.black,
                pressedShadowOpacity: 0.08,
                normalShadowOpacity: 0.13
            )
    }
}

private struct HomeIconButtonStyle: ButtonStyle {
    let compact: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .tdayToolbarButtonEffect(
                isPressed: configuration.isPressed,
                shadowsEnabled: !compact
            )
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

struct CreateListSheet: View {
    let onSubmit: (String, String?, String?) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.tdayColors) private var colors

    @State private var name = ""
    @State private var color = "PINK"
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

    private var maximumSheetHeight: CGFloat {
        max(1, UIScreen.main.bounds.height * CreateListSheetMetrics.maximumHeightFraction)
    }

    private var stableSheetHeight: CGFloat {
        min(max(CreateListSheetMetrics.sheetHeight, 1), maximumSheetHeight)
    }

    var body: some View {
        VStack(spacing: 0) {
            TdaySheetHeader(
                title: L("New list"),
                closeAccessibilityLabel: L("Close"),
                confirmAccessibilityLabel: L("Create list"),
                isConfirmEnabled: canCreate,
                onClose: { dismiss() },
                onConfirm: {
                    onSubmit(trimmedName, color, iconKey)
                    dismiss()
                }
            )

            ScrollView(showsIndicators: false) {
                VStack(spacing: 14) {
                    TdaySheetCard {
                        VStack(spacing: 18) {
                            ZStack {
                                Circle()
                                    .fill(accentColor)
                                    .frame(width: 86, height: 86)

                                TdayListIcon(iconKey: iconKey, size: 38)
                                    .foregroundStyle(.white)
                            }

                            TextField(
                                "",
                                text: $name,
                                prompt: Text("List name")
                                    .foregroundStyle(colors.onSurfaceVariant.opacity(0.78))
                            )
                            .textInputAutocapitalization(.words)
                            .autocorrectionDisabled()
                            .multilineTextAlignment(.center)
                            .font(.tdayRounded(size: 22, weight: .bold))
                            .foregroundStyle(accentColor)
                            .padding(.horizontal, 14)
                            .frame(maxWidth: .infinity)
                            .frame(height: 62)
                            .background(
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .fill(colors.bottomSheetControlSurface)
                            )
                        }
                        .padding(.horizontal, 18)
                        .padding(.vertical, 18)
                    }

                    TdaySheetSectionTitle(text: "Color")
                    TdaySheetCard {
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
                                    .buttonStyle(
                                        TdayPressButtonStyle(
                                            shadowColor: Color.black,
                                            pressedShadowOpacity: 0.04,
                                            normalShadowOpacity: 0.08
                                        )
                                    )
                                }
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 14)
                        }
                    }

                    TdaySheetSectionTitle(text: "Icon")
                    TdaySheetCard {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 10) {
                                ForEach(homeListIconOptions, id: \.key) { option in
                                    let isSelected = option.key == iconKey
                                    Button {
                                        iconKey = option.key
                                    } label: {
                                        Circle()
                                            .fill(isSelected ? accentColor.opacity(0.2) : colors.bottomSheetControlSurface)
                                            .frame(width: 48, height: 48)
                                            .overlay(
                                                Circle()
                                                    .stroke(
                                                        isSelected ? accentColor.opacity(0.55) : .clear,
                                                        lineWidth: 2
                                                    )
                                            )
                                            .overlay {
                                                TdayListIcon(iconKey: option.key, size: 22)
                                                    .foregroundStyle(isSelected ? accentColor : colors.onSurfaceVariant)
                                            }
                                    }
                                    .buttonStyle(
                                        TdayPressButtonStyle(
                                            shadowColor: Color.black,
                                            pressedShadowOpacity: 0.04,
                                            normalShadowOpacity: 0.08
                                        )
                                    )
                                    .accessibilityLabel(formattedOptionName(option.key))
                                }
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 14)
                        }
                    }
                }
                .padding(.horizontal, 18)
                .padding(.top, 14)
                .padding(.bottom, CreateListSheetMetrics.bottomContentPadding)
            }
            .scrollDismissesKeyboard(.interactively)
            .disableVerticalScrollBounce()
        }
        .frame(maxWidth: .infinity, minHeight: stableSheetHeight, maxHeight: stableSheetHeight, alignment: .top)
        .background(colors.bottomSheetBackground)
        .clipShape(
            UnevenRoundedRectangle(
                topLeadingRadius: TdaySheetMetrics.sheetCornerRadius,
                bottomLeadingRadius: 0,
                bottomTrailingRadius: 0,
                topTrailingRadius: TdaySheetMetrics.sheetCornerRadius,
                style: .continuous
            )
        )
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

private let homeListColorOptions: [HomeListColorOption] = [
    HomeListColorOption(key: "PINK", color: Color(hex: 0xE05299)),
    HomeListColorOption(key: "GOLD", color: Color(hex: 0xE8A530)),
    HomeListColorOption(key: "DEEP_BLUE", color: Color(hex: 0x3C9ADD)),
    HomeListColorOption(key: "CORAL", color: Color(hex: 0xE6664C)),
    HomeListColorOption(key: "TEAL", color: Color(hex: 0x2EB8AC)),
    HomeListColorOption(key: "SLATE", color: Color(hex: 0x3E4774)),
    HomeListColorOption(key: "BLUE", color: Color(hex: 0x6EA8E1)),
    HomeListColorOption(key: "PURPLE", color: Color(hex: 0x7D67B6)),
    HomeListColorOption(key: "ROSE", color: Color(hex: 0xD1617D)),
    HomeListColorOption(key: "LIGHT_RED", color: Color(hex: 0xE06C6C)),
    HomeListColorOption(key: "BRICK", color: Color(hex: 0xC64C39)),
    HomeListColorOption(key: "YELLOW", color: Color(hex: 0xE8BA30)),
    HomeListColorOption(key: "LIME", color: Color(hex: 0x46B963)),
    HomeListColorOption(key: "ORANGE", color: Color(hex: 0xE28736)),
    HomeListColorOption(key: "RED", color: Color(hex: 0xDF3A3A)),
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
    let normalizedKey: String?
    switch key {
    case "GREEN":
        normalizedKey = "LIME"
    case "GRAY":
        normalizedKey = "SLATE"
    default:
        normalizedKey = key
    }
    return homeListColorOptions.first(where: { $0.key == normalizedKey })?.color ?? Color(hex: 0xE05299)
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
