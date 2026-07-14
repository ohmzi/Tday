import SwiftUI
import UIKit

struct AppRootView: View {
    private let container: AppContainer

    @State private var appViewModel: AppViewModel
    @State private var authViewModel: AuthViewModel
    @State private var notificationDeepLinkRouter = NotificationDeepLinkRouter.shared
    @State private var hasLeftActiveScene = false
    @State private var isLaunchSplashHeld = false
    @State private var rootFeedTab: RootFeedTab = .home
    @State private var rootCreateTaskRequestID = 0
    @State private var pendingRootCreateTask: PendingRootCreateTask?
    // Prefill from a share-extension capture, applied to the next create sheet.
    @State private var rootCreateTaskPrefill: CreateTaskPayload?
    @State private var rootHomeScrollToTopRequestID = 0
    @State private var rootFloaterScrollToTopRequestID = 0
    @State private var rootDockCollapsed = false
    @State private var rootControlsVisible = true
    @Environment(\.scenePhase) private var scenePhase

    init(container: AppContainer) {
        self.container = container
        _appViewModel = State(initialValue: AppViewModel(container: container))
        _authViewModel = State(initialValue: AuthViewModel(
            authRepository: container.authRepository,
            systemCredentialService: container.systemCredentialService
        ))
    }

    var body: some View {
        Group {
            if !appViewModel.hasCompletedInitialBootstrap || isLaunchSplashHeld {
                AppLaunchSplashView(isHeld: $isLaunchSplashHeld)
            } else {
                let showOnboardingOverlay = !appViewModel.isWorkspaceAvailable && appViewModel.versionCheckResult == .compatible

                NavigationStack(
                    path: rootNavigationPath
                ) {
                    TdayBackground {
                        ZStack(alignment: .bottom) {
                            switch rootFeedTab {
                            case .home:
                                HomeScreen(
                                    container: container,
                                    onRootFeedTabSelected: handleRootFeedTabSelection,
                                    showsRootControls: false,
                                    createTaskRequestID: rootCreateTaskRequestID,
                                    createTaskPrefill: rootCreateTaskPrefill,
                                    onCreateTaskSheetClosed: { rootCreateTaskPrefill = nil },
                                    scrollToTopRequestID: rootHomeScrollToTopRequestID,
                                    onRootDockCollapsedChange: { rootDockCollapsed = $0 },
                                    onRootControlsVisibleChange: { rootControlsVisible = $0 },
                                    pullRefreshEnabled: !appViewModel.isLocalMode,
                                    summaryAvailable: !appViewModel.isLocalMode && !appViewModel.isOffline
                                ) { route in
                                    handleRoute(route)
                                }
                            case .floater:
                                TodoListScreen(
                                    container: container,
                                    mode: .floater,
                                    listId: nil,
                                    listName: nil,
                                    highlightedTodoId: nil,
                                    rootFeedTab: .floater,
                                    onRootFeedTabSelected: handleRootFeedTabSelection,
                                    showsRootControls: false,
                                    pullRefreshEnabled: !appViewModel.isLocalMode,
                                    usesRootFeedHeader: true,
                                    createTaskRequestID: rootCreateTaskRequestID,
                                    scrollToTopRequestID: rootFloaterScrollToTopRequestID,
                                    onRootDockCollapsedChange: { rootDockCollapsed = $0 },
                                    onRootControlsVisibleChange: { rootControlsVisible = $0 },
                                    onOpenFloaterList: { listId, listName in
                                        handleRoute(.floaterListTodos(listId: listId, listName: listName))
                                    },
                                    onOpenSettings: {
                                        handleRoute(.settings)
                                    },
                                    summaryAvailable: !appViewModel.isLocalMode && !appViewModel.isOffline
                                )
                            }

                            if appViewModel.isWorkspaceAvailable, rootControlsVisible {
                                rootFloatingControls
                            }
                        }
                    }
                    .blur(radius: showOnboardingOverlay ? 6 : 0)
                    .scaleEffect(showOnboardingOverlay ? 0.992 : 1)
                    .animation(.easeInOut(duration: 0.22), value: showOnboardingOverlay)
                    .navigationBarBackButtonHidden(true)
                    .toolbar(.hidden, for: .navigationBar)
                    .navigationDestination(for: AppRoute.self) { route in
                        destinationView(for: route)
                    }
                    .onChange(of: appViewModel.navigationPath) { _, path in
                        normalizeRootNavigationPath(path)
                    }
                    .onChange(of: appViewModel.offlineNoticeID) { _, _ in
                        showOfflineToast()
                    }
                    .onChange(of: appViewModel.isOffline) { wasOffline, isOffline in
                        if wasOffline && !isOffline {
                            showBackOnlineToast()
                        }
                    }
                    .overlay {
                        if !appViewModel.isWorkspaceAvailable {
                            let isVersionBlocking = appViewModel.versionCheckResult != .compatible

                            if appViewModel.pendingApproval {
                                PendingApprovalView(
                                    username: appViewModel.pendingApprovalUsername,
                                    isChecking: appViewModel.isCheckingApproval,
                                    onCheckStatus: {
                                        await appViewModel.checkPendingApproval()
                                    },
                                    onUseDifferentAccount: {
                                        authViewModel.clearStatus()
                                        appViewModel.cancelPendingApproval()
                                    }
                                )
                            } else if isVersionBlocking {
                                UpdateRequiredView(
                                    versionCheckResult: appViewModel.versionCheckResult,
                                    onRetry: {
                                        Task { await appViewModel.recheckVersion() }
                                    }
                                )
                            } else {
                                OnboardingWizardOverlay(
                                    initialServerURL: appViewModel.serverURL,
                                    serverErrorMessage: appViewModel.error,
                                    serverCanResetTrust: appViewModel.canResetServerTrust,
                                    pendingApprovalMessage: appViewModel.pendingApprovalMessage,
                                    authViewModel: authViewModel,
                                    systemCredentialService: container.systemCredentialService,
                                    onConnectServer: { rawURL in
                                        await appViewModel.connectServer(rawURL: rawURL)
                                    },
                                    onResetServerTrust: { rawURL in
                                        await appViewModel.resetTrustedServer(rawURL: rawURL)
                                    },
                                    onLogin: { username, password, source in
                                        let success = await authViewModel.login(username: username, password: password, source: source)
                                        if success {
                                            await appViewModel.refreshSession()
                                        } else if authViewModel.pendingApproval {
                                            appViewModel.enterPendingApproval(username: username, password: password)
                                        }
                                        return success
                                    },
                                    onRegister: { firstName, username, password, securityAnswers in
                                        let success = await authViewModel.register(firstName: firstName, lastName: "", username: username, password: password, securityAnswers: securityAnswers)
                                        if success {
                                            if authViewModel.pendingApproval {
                                                appViewModel.enterPendingApproval(username: username, password: password)
                                            } else {
                                                await appViewModel.refreshSession()
                                            }
                                        }
                                        return success
                                    },
                                    onLoadSecurityQuestions: {
                                        await authViewModel.loadAllSecurityQuestions()
                                    },
                                    onUseLocalMode: {
                                        authViewModel.clearStatus()
                                        appViewModel.clearPendingApprovalNotice()
                                        await appViewModel.useLocalMode()
                                    },
                                    onClearAuthStatus: {
                                        authViewModel.clearStatus()
                                        appViewModel.clearPendingApprovalNotice()
                                    }
                                )
                            }
                        }

                        if appViewModel.authenticated && !appViewModel.isLocalMode && appViewModel.versionCheckResult != .compatible {
                            UpdateRequiredView(
                                versionCheckResult: appViewModel.versionCheckResult,
                                onRetry: {
                                    Task { await appViewModel.recheckVersion() }
                                }
                            )
                        }

                        if appViewModel.authenticated,
                           !appViewModel.isLocalMode,
                           appViewModel.versionCheckResult == .compatible,
                           appViewModel.user?.requireSecurityQuestions == true {
                            SecurityQuestionsGateView(
                                authViewModel: authViewModel,
                                onSaved: {
                                    await appViewModel.refreshSession()
                                }
                            )
                        }
                    }
                }
                .navigationInteractivePopGesture()
                // The snackbar overlays the NavigationStack itself, not the
                // stack's root content: toasts scheduled while a destination
                // is pushed (deleting a list or task from a pushed screen)
                // must stay visible across pushes and pops. Attached to the
                // root content they render into a covered view and never
                // appear after navigating back.
                .overlay(alignment: .bottom) {
                    if let content = container.snackbarManager.content {
                        AppSnackbar(content: content) {
                            container.snackbarManager.dismiss()
                        }
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                    }
                }
                .animation(.snappy(duration: 0.3), value: container.snackbarManager.content?.id)
            }
        }
        .tdayAppTheme(themeMode: appViewModel.themeMode)
        // One provider for every contextual "?" help link (GuideHelpLink):
        // pushes the guide onto the main navigation stack, pre-scrolled.
        .environment(\.openGuideTopic, { topicId in
            appViewModel.navigationPath.append(.helpGuide(topic: topicId))
        })
        // In-app language override: changing the locale (and reading the
        // generation token) re-resolves every Text against the selected
        // language bundle instantly, no restart.
        .environment(\.locale, Locale(identifier: appViewModel.resolvedLocaleIdentifier))
        .id(appViewModel.localizationGeneration)
        .background(
            TdayKeyboardPrewarmView(
                isEnabled: scenePhase == .active
            )
        )
        .task {
            if !appViewModel.hasCompletedInitialBootstrap {
                await appViewModel.bootstrap()
            }
            routePendingNotificationDeepLink()
            drainPendingShareIfReady()
            presentPendingRootCreateTaskIfReady()
        }
        .onOpenURL { url in
            handleDeepLink(url)
        }
        .onChange(of: notificationDeepLinkRouter.pendingURL) { _, _ in
            routePendingNotificationDeepLink()
        }
        .onChange(of: notificationDeepLinkRouter.pendingReminderAction) { _, _ in
            handlePendingReminderAction()
        }
        .onChange(of: appViewModel.hasCompletedInitialBootstrap) { _, _ in
            drainPendingShareIfReady()
            presentPendingRootCreateTaskIfReady()
            // Cold launch: apply completions tapped on widgets while the app
            // was dead (scenePhase is already .active here, so the .onChange
            // drain below never fires for this activation).
            Task {
                await container.todoRepository.drainWidgetCompletions()
            }
        }
        .onChange(of: appViewModel.isWorkspaceAvailable) { _, _ in
            drainPendingShareIfReady()
            presentPendingRootCreateTaskIfReady()
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active:
                Task {
                    await container.todoRepository.drainWidgetCompletions()
                }
                drainPendingShareIfReady()
                guard hasLeftActiveScene else {
                    presentPendingRootCreateTaskIfReady()
                    return
                }
                hasLeftActiveScene = false
                presentPendingRootCreateTaskIfReady()
                Task {
                    await appViewModel.reconnectAfterForeground()
                }
            case .inactive, .background:
                hasLeftActiveScene = true
            @unknown default:
                break
            }
        }
    }

    // Kept out of `body`: as part of the ~300-line body expression this switch
    // pushes the type-checker over its time limit ("unable to type-check this
    // expression in reasonable time"); as a standalone function each case is
    // checked independently.
    @ViewBuilder
    private func destinationView(for route: AppRoute) -> some View {
        switch route {
        case .home:
            HomeScreen(
                container: container,
                onRootFeedTabSelected: handleRootFeedTabSelection,
                summaryAvailable: !appViewModel.isLocalMode && !appViewModel.isOffline
            ) { nextRoute in
                handleRoute(nextRoute)
            }
        case .todayTodos:
            TodoListScreen(container: container, mode: .today, listId: nil, listName: nil, highlightedTodoId: nil, summaryAvailable: !appViewModel.isLocalMode && !appViewModel.isOffline)
        case .createTodayTodo:
            EmptyView()
        case .createFloaterTodo:
            EmptyView()
        case .overdueTodos:
            TodoListScreen(container: container, mode: .overdue, listId: nil, listName: nil, highlightedTodoId: nil, summaryAvailable: !appViewModel.isLocalMode && !appViewModel.isOffline)
        case .scheduledTodos:
            TodoListScreen(container: container, mode: .scheduled, listId: nil, listName: nil, highlightedTodoId: nil, summaryAvailable: !appViewModel.isLocalMode && !appViewModel.isOffline)
        case let .allTodos(highlightTodoId):
            TodoListScreen(container: container, mode: .all, listId: nil, listName: nil, highlightedTodoId: highlightTodoId, summaryAvailable: !appViewModel.isLocalMode && !appViewModel.isOffline)
        case .priorityTodos:
            TodoListScreen(container: container, mode: .priority, listId: nil, listName: nil, highlightedTodoId: nil, summaryAvailable: !appViewModel.isLocalMode && !appViewModel.isOffline)
        case .floaterTodos:
            Color.clear
                .navigationBarBackButtonHidden(true)
                .toolbar(.hidden, for: .navigationBar)
                .onAppear {
                    selectRootFeedTab(.floater)
                }
        case let .floaterListTodos(listId, listName):
            TodoListScreen(
                container: container,
                mode: .floater,
                listId: listId,
                listName: listName,
                highlightedTodoId: nil,
                summaryAvailable: !appViewModel.isLocalMode && !appViewModel.isOffline,
                onListDeleted: {
                    handleRoute(.floaterTodos)
                }
            )
        case let .listTodos(listId, listName):
            TodoListScreen(
                container: container,
                mode: .list,
                listId: listId,
                listName: listName,
                highlightedTodoId: nil,
                summaryAvailable: !appViewModel.isLocalMode && !appViewModel.isOffline,
                onListDeleted: {
                    appViewModel.navigate(to: .home)
                }
            )
        case .completed:
            CompletedScreen(container: container)
        case .calendar:
            CalendarScreen(container: container)
        case .settings:
            SettingsScreen(viewModel: appViewModel)
        case .latestRelease:
            LatestReleaseScreen(viewModel: appViewModel)
        case let .helpGuide(topic):
            HelpGuideScreen(viewModel: appViewModel, initialTopic: topic)
        case .morningSweep:
            MorningSweepScreen(viewModel: appViewModel)
        case .forgotPassword:
            ForgotPasswordView(
                authViewModel: authViewModel,
                initialUsername: authViewModel.savedUsername,
                onDismiss: {
                    appViewModel.goBack()
                },
                onResetComplete: { _ in
                    appViewModel.goBack()
                    container.snackbarManager.show(
                        L("Password reset. Sign in with your new password."),
                        kind: .success
                    )
                }
            )
        }
    }

    private func handleRoute(_ route: AppRoute) {
        switch route {
        case .home:
            selectRootFeedTab(.home)
        case .createTodayTodo:
            requestRootCreateTask(on: .home)
        case .createFloaterTodo:
            requestRootCreateTask(on: .floater)
        case .floaterTodos:
            selectRootFeedTab(.floater)
        default:
            appViewModel.navigate(to: route)
        }
    }

    private func handleRootFeedTabSelection(_ tab: RootFeedTab) {
        if tab == rootFeedTab {
            requestRootFeedScrollToTop(for: tab)
            return
        }
        HapticManager.tabSwitch()
        selectRootFeedTab(tab)
    }

    private func selectRootFeedTab(_ tab: RootFeedTab) {
        rootFeedTab = tab
        appViewModel.navigationPath = []
    }

    private func requestRootFeedScrollToTop(for tab: RootFeedTab) {
        switch tab {
        case .home:
            rootHomeScrollToTopRequestID += 1
        case .floater:
            rootFloaterScrollToTopRequestID += 1
        }
    }

    private func requestRootCreateTask(on tab: RootFeedTab) {
        selectRootFeedTab(tab)
        pendingRootCreateTask = PendingRootCreateTask(tab: tab)
        presentPendingRootCreateTaskIfReady()
    }

    /// Turns the oldest share-extension capture into a prefilled create sheet
    /// on the Home tab. One per activation; the queue holds the rest. Skips
    /// while another create request is mid-flight so the prefill can't attach
    /// to a sheet the user asked for manually.
    private func drainPendingShareIfReady() {
        guard
            scenePhase == .active,
            appViewModel.hasCompletedInitialBootstrap,
            appViewModel.isWorkspaceAvailable,
            pendingRootCreateTask == nil,
            rootCreateTaskPrefill == nil,
            let share = PendingShareStore.drainNext()
        else {
            return
        }
        rootCreateTaskPrefill = CreateTaskPayload(
            title: share.title,
            description: share.notes,
            priority: TaskPriorityDisplay.normalValue,
            // Same default the blank sheet uses; the NLP parse can move it if
            // the shared text carries a date phrase.
            due: Date().addingTimeInterval(60 * 60),
            rrule: nil,
            listId: nil
        )
        requestRootCreateTask(on: .home)
    }

    private func presentPendingRootCreateTaskIfReady() {
        guard
            let request = pendingRootCreateTask,
            scenePhase == .active,
            appViewModel.hasCompletedInitialBootstrap,
            appViewModel.isWorkspaceAvailable
        else {
            return
        }

        pendingRootCreateTask = nil
        Task { @MainActor in
            selectRootFeedTab(request.tab)
            await Task.yield()
            try? await Task.sleep(for: .milliseconds(180))
            guard appViewModel.hasCompletedInitialBootstrap, appViewModel.isWorkspaceAvailable else {
                pendingRootCreateTask = request
                return
            }
            let nextRequestID = rootCreateTaskRequestID + 1
            rootCreateTaskRequestID = nextRequestID
        }
    }

    private var rootNavigationPath: Binding<[AppRoute]> {
        Binding(
            get: { sanitizedNavigationPath(appViewModel.navigationPath) },
            set: { newPath in
                setNavigationPath(newPath)
            }
        )
    }

    private func setNavigationPath(_ newPath: [AppRoute]) {
        if newPath.contains(.createTodayTodo) {
            requestRootCreateTask(on: .home)
            return
        }

        if newPath.contains(.createFloaterTodo) {
            requestRootCreateTask(on: .floater)
            return
        }

        if let rootTab = rootFeedTabRoute(in: newPath) {
            selectRootFeedTab(rootTab)
            return
        }

        appViewModel.navigationPath = newPath
    }

    private func sanitizedNavigationPath(_ path: [AppRoute]) -> [AppRoute] {
        path.filter { route in
            !route.isRootFeedRoute && !route.isCommandRoute
        }
    }

    private func rootFeedTabRoute(in path: [AppRoute]) -> RootFeedTab? {
        for route in path.reversed() {
            if let tab = route.rootFeedTab {
                return tab
            }
        }

        return nil
    }

    private func normalizeRootNavigationPath(_ path: [AppRoute]) {
        if path.contains(.createTodayTodo) {
            DispatchQueue.main.async {
                requestRootCreateTask(on: .home)
            }
            return
        }

        if path.contains(.createFloaterTodo) {
            DispatchQueue.main.async {
                requestRootCreateTask(on: .floater)
            }
            return
        }

        guard let rootTab = rootFeedTabRoute(in: path) else {
            return
        }

        DispatchQueue.main.async {
            selectRootFeedTab(rootTab)
        }
    }

    private var rootFloatingControls: some View {
        HStack(alignment: .bottom) {
            RootFeedDock(
                activeTab: rootFeedTab,
                collapsed: rootDockCollapsed,
                accentColor: rootCreateTaskFillColor,
                onSelect: handleRootFeedTabSelection
            )
            .padding(.leading, 18)
            .padding(.vertical, 8)

            Spacer(minLength: 12)

            TaskFloatingActionButton(fillColor: rootCreateTaskFillColor) {
                rootCreateTaskRequestID += 1
            }
            .padding(.trailing, 18)
            .padding(.vertical, 8)
        }
    }

    private var rootCreateTaskFillColor: Color {
        rootFeedTab == .floater ? .tdayFloaterGreen : .tdayTodayBlue
    }

    /// Transient "you're offline" toast, gated the same way the old OfflineBanner was
    /// (signed in, not local-mode). Driven by `offlineNoticeID`, which the AppViewModel
    /// already bumps with a cooldown, so this won't spam on every failed sync.
    private func showOfflineToast() {
        guard appViewModel.authenticated, !appViewModel.isLocalMode, appViewModel.isOffline else {
            return
        }
        container.snackbarManager.show(offlineToastMessage, kind: .error)
    }

    /// Transient "back online" toast, fired when connectivity is restored.
    private func showBackOnlineToast() {
        guard appViewModel.authenticated, !appViewModel.isLocalMode else {
            return
        }
        container.snackbarManager.show(
            L("Back online — syncing your latest changes…"),
            kind: .info
        )
    }

    private var offlineToastMessage: String {
        let count = appViewModel.pendingMutationCount
        if count == 1 {
            return L("You're offline — 1 change waiting to sync.")
        }
        if count > 1 {
            return L("You're offline — %lld changes waiting to sync.", Int64(count))
        }
        return L("You're offline — changes will sync when your connection returns.")
    }

    private func handleDeepLink(_ url: URL) {
        guard let route = AppRoute.from(url: url) else {
            return
        }
        handleRoute(route)
    }

    private func routePendingNotificationDeepLink() {
        guard let url = notificationDeepLinkRouter.pendingURL else {
            return
        }
        handleDeepLink(url)
        notificationDeepLinkRouter.clearPendingURL()
    }

    /// Reminder-notification actions that need the data layer ("Tonight").
    private func handlePendingReminderAction() {
        guard let action = notificationDeepLinkRouter.pendingReminderAction else {
            return
        }
        notificationDeepLinkRouter.clearPendingReminderAction()
        switch action {
        case let .moveTonight(taskID):
            Task {
                try? await container.todoRepository.moveTodoTonight(taskID: taskID)
            }
        }
    }
}

private struct AppSnackbar: View {
    let content: SnackbarManager.Content
    let onDismiss: () -> Void

    @Environment(\.tdayColors) private var colors
    @State private var dragOffset: CGFloat = 0

    // Icons are removed app-wide; the variant cue now lives in the card surface
    // itself. Issue/error toasts get a red translucent shade layered over the
    // material; info/success keep the plain neutral material. The action label
    // still uses the accent colour.
    private var accent: Color {
        switch content.kind {
        case .error: return colors.error
        case .success: return Color(hex: 0xE06F66)
        case .info: return Color(hex: 0xE06F66)
        }
    }

    private var isError: Bool { content.kind == .error }

    var body: some View {
        HStack(spacing: 12) {
            Text(content.message)
                .font(.tdayRounded(.subheadline, weight: .bold))
                .foregroundStyle(colors.onSurface)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, alignment: .center)

            if let label = content.actionLabel, let action = content.action {
                Button {
                    action()
                    onDismiss()
                } label: {
                    Text(label)
                        .font(.tdayRounded(.subheadline, weight: .heavy))
                        .foregroundStyle(accent)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        // Red tint sits in front of the frosted material (between it and the text)
        // so the error shade reads clearly over the blurred backdrop.
        .background(
            colors.error.opacity(isError ? (colors.isDark ? 0.30 : 0.20) : 0),
            in: RoundedRectangle(cornerRadius: 24, style: .continuous)
        )
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(colors.cardStroke, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(colors.isDark ? 0.35 : 0.18), radius: 18, y: 10)
        // Hit area is the card itself — applied before the outer paddings so
        // the invisible margin over the RootFeedDock doesn't swallow taps
        // meant for the dock while a toast is showing.
        .contentShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .onTapGesture(perform: onDismiss)
        .gesture(
            // Global space keeps the translation stable while the card itself
            // moves with the finger — measured locally, the card's own offset
            // feeds back into the translation and the drag oscillates.
            DragGesture(minimumDistance: 10, coordinateSpace: .global)
                .onChanged { value in
                    dragOffset = max(0, value.translation.height)
                }
                .onEnded { value in
                    if value.translation.height > 30 || value.predictedEndTranslation.height > 90 {
                        onDismiss()
                    } else {
                        withAnimation(.snappy(duration: 0.25)) {
                            dragOffset = 0
                        }
                    }
                }
        )
        .offset(y: dragOffset)
        .padding(.horizontal, 20)
        // Sit above the bottom RootFeedDock (height 60 + 8/8 vertical padding ≈
        // 76pt above the safe area) with a ~12pt gap, instead of overlapping it.
        // Matches Android's 88dp bottom inset.
        .padding(.bottom, 88)
        .task(id: content.id) {
            dragOffset = 0
            let seconds: UInt64 = content.action == nil ? 4 : 8
            try? await Task.sleep(nanoseconds: seconds * 1_000_000_000)
            onDismiss()
        }
    }
}

private struct PendingRootCreateTask {
    let tab: RootFeedTab
}

private struct TdayKeyboardPrewarmView: UIViewRepresentable {
    let isEnabled: Bool

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.isUserInteractionEnabled = false
        view.alpha = 0.01
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.update(isEnabled: isEnabled, hostView: uiView)
    }

    final class Coordinator {
        private static var didPrewarm = false

        private var isScheduled = false
        private var attempts = 0

        func update(isEnabled: Bool, hostView: UIView) {
            guard isEnabled, !Self.didPrewarm, !isScheduled else {
                return
            }
            schedulePrewarm(from: hostView)
        }

        private func schedulePrewarm(from hostView: UIView) {
            isScheduled = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { [weak self, weak hostView] in
                guard let self else {
                    return
                }
                self.isScheduled = false

                guard let hostView else {
                    return
                }

                guard let window = hostView.window else {
                    self.attempts += 1
                    if self.attempts < 8 {
                        self.schedulePrewarm(from: hostView)
                    }
                    return
                }

                Self.didPrewarm = true
                self.attempts = 0
                self.prewarm(in: window)
            }
        }

        private func prewarm(in window: UIWindow) {
            let textField = UITextField(frame: CGRect(x: -100, y: -100, width: 1, height: 1))
            textField.alpha = 0.01
            textField.isUserInteractionEnabled = false
            textField.autocorrectionType = .no
            textField.spellCheckingType = .no
            textField.inputView = UIView(frame: CGRect(x: 0, y: 0, width: 1, height: 1))
            window.addSubview(textField)

            textField.becomeFirstResponder()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                textField.resignFirstResponder()
                textField.removeFromSuperview()
            }
        }
    }
}

private extension AppRoute {
    var rootFeedTab: RootFeedTab? {
        switch self {
        case .home:
            return .home
        case .floaterTodos:
            return .floater
        default:
            return nil
        }
    }

    var isRootFeedRoute: Bool {
        rootFeedTab != nil
    }

    var isCommandRoute: Bool {
        switch self {
        case .createTodayTodo, .createFloaterTodo:
            return true
        default:
            return false
        }
    }
}

struct AppLaunchSplashView: View {
    @Binding var isHeld: Bool
    @Environment(\.colorScheme) private var colorScheme
    @State private var tagline = splashTaglines.randomElement() ?? "Running on your server, running your life"

    var body: some View {
        ZStack {
            splashBackground

            VStack(spacing: 0) {
                SplashTdayLogoMark()
                    .frame(width: 160, height: 160)

                Spacer()
                    .frame(height: 24)

                Text("T\u{2019}Day")
                    .font(.tdayRounded(size: 32, weight: .heavy))
                    .foregroundStyle(titleColor)

                Text(tagline)
                    .font(.tdayRounded(size: 14, weight: .bold))
                    .foregroundStyle(taglineColor)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, 32)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    isHeld = true
                }
                .onEnded { _ in
                    isHeld = false
                }
        )
        .onDisappear {
            isHeld = false
        }
        .background(splashBackground)
        .ignoresSafeArea()
    }

    private var splashBackground: Color {
        colorScheme == .dark ? .tdayDarkBackground : .tdayLightBackground
    }

    private var titleColor: Color {
        colorScheme == .dark ? .tdayDarkForeground : .tdayLightForeground
    }

    private var taglineColor: Color {
        colorScheme == .dark ? .tdayDarkMuted : .tdayLightMuted
    }
}

private let splashTaglines = [
    "Your server remembers, so you don\u{2019}t have to",
    "Hosted by you, haunted by deadlines",
    "Because \u{2018}I\u{2019}ll remember later\u{2019} is always a lie",
    "Self-hosted sanity, one task at a time",
    "Nagging you from your own hardware",
    "Your data, your server, your no-excuse zone",
    "Making procrastination slightly harder since v0.1",
    "Running on your server, running your life",
    "Because sticky notes don\u{2019}t have push notifications",
    "Turning \u{2018}I forgot\u{2019} into \u{2018}I got this\u{2019}",
    "Your personal nudge machine",
    "Self-hosted, self-organized\u{2026} well, getting there",
    "Where forgotten tasks go to get found",
    "Adulting, but make it self-hosted",
    "Taming chaos from a server near you",
    "Future you says thanks in advance",
    "The cloud is just someone else\u{2019}s server. This one\u{2019}s yours.",
    "Organizing your life, no landlord required",
    "Zero trust\u{2026} except your own server",
    "Syncing your tasks, judging your priorities",
    "Today called. It wants a plan.",
    "Making later file a formal request",
    "Turning chaos into checkboxes",
    "Your tasks are lining up nicely",
    "A private server with opinions about your priorities",
    "For when your brain opens too many tabs",
    "Scheduling the chaos before it schedules you",
    "Your lists have entered their productive era",
    "A tiny operations desk for future you",
    "Because vibes are not a task strategy",
    "Private tasks. Better mornings.",
    "Making your backlog feel seen, then sorted",
    "Where scattered thoughts get assigned seating",
    "Your priorities just got a home address",
    "Sync first, panic later",
    "Calendar drama, now with containment",
    "Deadlines hate this one self-hosted trick",
    "Helping your day stop freelancing",
    "Your reminders came prepared",
    "Turning I should into scheduled",
    "Your TODO list, but it shows up",
    "Outsmarting 'I'll do it tomorrow'",
    "A to-do list with a memory",
    "Your executor, self-hosted",
    "Procrastination's worst nightmare",
    "Where 'someday' gets a date",
    "Productivity, hosted on your terms",
    "One server. Zero excuses.",
    "Keeping your promises for you",
    "The 'later' column, tamed",
    "Your second brain, but reliable",
    "Escalating 'maybe' to 'scheduled'",
    "Chaos management, private edition",
    "Your day's project manager",
    "Because 'in my head' is not a system",
    "Task herding made official",
    "Turning mental load into checkmarks",
    "Serving tasks like a good server should",
    "Your personal accountability server",
    "Prioritizing so you don't have to"
]

private struct SplashTdayLogoMark: View {
    var body: some View {
        Canvas { context, size in
            let iconSize = min(size.width, size.height)
            let scale = iconSize / 108
            let origin = CGPoint(
                x: (size.width - iconSize) / 2,
                y: (size.height - iconSize) / 2
            )

            func scaled(_ value: CGFloat) -> CGFloat {
                value * scale
            }

            func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
                CGPoint(x: origin.x + scaled(x), y: origin.y + scaled(y))
            }

            func rect(_ x: CGFloat, _ y: CGFloat, _ width: CGFloat, _ height: CGFloat) -> CGRect {
                CGRect(x: origin.x + scaled(x), y: origin.y + scaled(y), width: scaled(width), height: scaled(height))
            }

            func roundedRect(_ x: CGFloat, _ y: CGFloat, _ width: CGFloat, _ height: CGFloat, _ radius: CGFloat) -> Path {
                Path(roundedRect: rect(x, y, width, height), cornerRadius: scaled(radius))
            }

            func strokeLine(_ start: CGPoint, _ end: CGPoint, color: Color, width: CGFloat) {
                var path = Path()
                path.move(to: start)
                path.addLine(to: end)
                context.stroke(
                    path,
                    with: .color(color),
                    style: StrokeStyle(lineWidth: scaled(width), lineCap: .round)
                )
            }

            context.fill(roundedRect(36, 37, 36, 41, 5), with: .color(Color(hex: 0x90D5D2)))

            var contentPath = Path()
            contentPath.move(to: point(36, 48))
            contentPath.addLine(to: point(72, 48))
            contentPath.addLine(to: point(72, 73))
            contentPath.addQuadCurve(to: point(67, 78), control: point(72, 78))
            contentPath.addLine(to: point(41, 78))
            contentPath.addQuadCurve(to: point(36, 73), control: point(36, 78))
            contentPath.closeSubpath()
            context.fill(contentPath, with: .color(.white))

            context.stroke(
                roundedRect(36, 37, 36, 41, 5),
                with: .color(Color(hex: 0x2D6B6B)),
                style: StrokeStyle(lineWidth: scaled(3))
            )
            strokeLine(point(36, 48), point(72, 48), color: Color(hex: 0x2D6B6B), width: 2)

            for x in [44.0, 52.0, 60.0] {
                var ringPath = Path()
                ringPath.move(to: point(x, 40))
                ringPath.addLine(to: point(x, 33))
                ringPath.addCurve(
                    to: point(x + 8, 33),
                    control1: point(x, 27.5),
                    control2: point(x + 8, 27.5)
                )
                ringPath.addLine(to: point(x + 8, 40))
                context.stroke(
                    ringPath,
                    with: .color(Color(hex: 0x2D6B6B)),
                    style: StrokeStyle(lineWidth: scaled(3.2), lineCap: .round)
                )
            }

            for y in [52.0, 59.0, 66.0, 73.0] {
                strokeLine(point(41, y), point(50, y), color: Color(hex: 0xC4C4C4), width: 1.8)
                strokeLine(point(41, y + 3), point(47, y + 3), color: Color(hex: 0xC4C4C4), width: 1.8)
            }

            for y in [50.0, 57.0, 64.0, 71.0] {
                for x in [53.0, 59.0, 65.0] {
                    context.fill(Path(rect(x, y, 4.5, 5)), with: .color(Color(hex: 0xE85B6F)))
                }
            }
        }
        .aspectRatio(1, contentMode: .fit)
    }
}

/// Persistent "waiting for admin approval" holding screen. Shown on every launch while a
/// registered account is still PENDING; a silent re-login (on launch and via "Check
/// status") advances to Home the moment approval lands.
private struct PendingApprovalView: View {
    let username: String?
    let isChecking: Bool
    let onCheckStatus: () async -> Void
    let onUseDifferentAccount: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        ZStack {
            Color.black.opacity(0.45)
                .ignoresSafeArea()

            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 8) {
                        Image(systemName: "hourglass")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundStyle(colors.primary)
                        Text(L("Waiting for approval"))
                            .font(.tdayRounded(size: 20, weight: .heavy))
                            .foregroundStyle(colors.onSurface)
                    }

                    Text(pendingMessage)
                        .font(.tdayRounded(size: 14, weight: .bold))
                        .foregroundStyle(colors.onSurface.opacity(0.62))
                        .fixedSize(horizontal: false, vertical: true)
                }

                Button {
                    Task { await onCheckStatus() }
                } label: {
                    HStack(spacing: 8) {
                        if isChecking {
                            ProgressView().tint(colors.onPrimary)
                        }
                        Text(L(isChecking ? "Checking..." : "Check approval status"))
                            .font(.tdayRounded(size: 15, weight: .bold))
                            .foregroundStyle(colors.onPrimary)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background {
                        Capsule(style: .continuous).fill(colors.primary)
                    }
                }
                .buttonStyle(.plain)
                .opacity(isChecking ? 0.72 : 1)
                .disabled(isChecking)

                Button(action: onUseDifferentAccount) {
                    Text(L("Use a different account"))
                        .font(.tdayRounded(size: 15, weight: .bold))
                        .foregroundStyle(colors.primary)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.plain)
                .disabled(isChecking)
            }
            .padding(20)
            .frame(maxWidth: 430, alignment: .leading)
            .background {
                RoundedRectangle(cornerRadius: 30, style: .continuous)
                    .fill(colors.background)
                    .overlay(
                        RoundedRectangle(cornerRadius: 30, style: .continuous)
                            .stroke(colors.onSurface.opacity(colors.isDark ? 0.12 : 0.08), lineWidth: 1)
                    )
            }
            .shadow(color: Color.black.opacity(colors.isDark ? 0.34 : 0.14), radius: 18, x: 0, y: 10)
            .padding(18)
        }
    }

    private var pendingMessage: String {
        if let username, !username.isEmpty {
            return L("Your account (%@) is waiting for an administrator to approve it. We'll let you in as soon as it's approved.", username)
        }
        return L("Your account is waiting for an administrator to approve it. We'll let you in as soon as it's approved.")
    }
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
}
