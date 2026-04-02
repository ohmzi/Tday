import SwiftUI

private enum RootTab: Hashable {
    case home
    case calendar
    case completed
    case settings
}

struct AppRootView: View {
    private let container: AppContainer

    @State private var appViewModel: AppViewModel
    @State private var authViewModel: AuthViewModel
    @State private var selectedTab: RootTab = .home

    init(container: AppContainer) {
        self.container = container
        _appViewModel = State(initialValue: AppViewModel(container: container))
        _authViewModel = State(initialValue: AuthViewModel(authRepository: container.authRepository))
    }

    var body: some View {
        NavigationStack(
            path: Binding(
                get: { appViewModel.navigationPath },
                set: { appViewModel.navigationPath = $0 }
            )
        ) {
            TdayBackground {
                TabView(selection: $selectedTab) {
                    HomeScreen(container: container) { route in
                        handleRoute(route)
                    }
                        .tabItem {
                            Label("Home", systemImage: "house.fill")
                        }
                        .tag(RootTab.home)

                    CalendarScreen(container: container) {
                        selectedTab = .home
                    }
                    .tabItem {
                        Label("Calendar", systemImage: "calendar")
                    }
                    .tag(RootTab.calendar)

                    CompletedScreen(container: container) {
                        selectedTab = .home
                    }
                    .tabItem {
                        Label("Completed", systemImage: "checkmark.circle.fill")
                    }
                    .tag(RootTab.completed)

                    SettingsScreen(viewModel: appViewModel) {
                        selectedTab = .home
                    }
                    .tabItem {
                        Label("Settings", systemImage: "gearshape.fill")
                    }
                    .tag(RootTab.settings)
                }
            }
            .navigationDestination(for: AppRoute.self) { route in
                switch route {
                case .home:
                    HomeScreen(container: container) { nextRoute in
                        handleRoute(nextRoute)
                    }
                case .todayTodos:
                    TodoListScreen(container: container, mode: .today, listId: nil, listName: nil, highlightedTodoId: nil) {
                        appViewModel.goBack()
                    }
                case .overdueTodos:
                    TodoListScreen(container: container, mode: .overdue, listId: nil, listName: nil, highlightedTodoId: nil) {
                        appViewModel.goBack()
                    }
                case .scheduledTodos:
                    TodoListScreen(container: container, mode: .scheduled, listId: nil, listName: nil, highlightedTodoId: nil) {
                        appViewModel.goBack()
                    }
                case let .allTodos(highlightTodoId):
                    TodoListScreen(container: container, mode: .all, listId: nil, listName: nil, highlightedTodoId: highlightTodoId) {
                        appViewModel.goBack()
                    }
                case .priorityTodos:
                    TodoListScreen(container: container, mode: .priority, listId: nil, listName: nil, highlightedTodoId: nil) {
                        appViewModel.goBack()
                    }
                case let .listTodos(listId, listName):
                    TodoListScreen(container: container, mode: .list, listId: listId, listName: listName, highlightedTodoId: nil) {
                        appViewModel.goBack()
                    }
                case .completed:
                    CompletedScreen(container: container) {
                        appViewModel.goBack()
                    }
                case .calendar:
                    CalendarScreen(container: container) {
                        appViewModel.goBack()
                    }
                case .settings:
                    SettingsScreen(viewModel: appViewModel) {
                        appViewModel.goBack()
                    }
                }
            }
            .overlay(alignment: .top) {
                OfflineBanner(
                    visible: appViewModel.authenticated && appViewModel.isOffline,
                    pendingMutationCount: appViewModel.pendingMutationCount
                )
                .padding(.top, 8)
            }
            .overlay(alignment: .bottom) {
                if let message = container.snackbarManager.message {
                    Text(message)
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        .background(Color.black.opacity(0.78), in: Capsule())
                        .padding(.bottom, 18)
                        .onTapGesture {
                            container.snackbarManager.dismiss()
                        }
                }
            }
            .overlay {
                if !appViewModel.authenticated {
                    OnboardingWizardOverlay(
                        initialServerURL: appViewModel.serverURL,
                        serverErrorMessage: appViewModel.error,
                        serverCanResetTrust: appViewModel.canResetServerTrust,
                        pendingApprovalMessage: appViewModel.pendingApprovalMessage,
                        authViewModel: authViewModel,
                        onConnectServer: { rawURL in
                            await appViewModel.connectServer(rawURL: rawURL)
                        },
                        onResetServerTrust: { rawURL in
                            await appViewModel.resetTrustedServer(rawURL: rawURL)
                        },
                        onLogin: { email, password in
                            let success = await authViewModel.login(email: email, password: password)
                            if success {
                                await appViewModel.refreshSession()
                            }
                            return success
                        },
                        onRegister: { firstName, email, password in
                            let success = await authViewModel.register(firstName: firstName, lastName: "", email: email, password: password)
                            if success {
                                await appViewModel.refreshSession()
                            }
                            return success
                        },
                        onClearAuthStatus: {
                            authViewModel.clearStatus()
                            appViewModel.clearPendingApprovalNotice()
                        }
                    )
                }
            }
            .preferredColorScheme(appViewModel.themeMode.colorScheme)
            .task {
                await appViewModel.bootstrap()
            }
            .onOpenURL { url in
                handleDeepLink(url)
            }
        }
    }

    private func handleRoute(_ route: AppRoute) {
        switch route {
        case .calendar:
            selectedTab = .calendar
        case .completed:
            selectedTab = .completed
        case .settings:
            selectedTab = .settings
        default:
            selectedTab = .home
            appViewModel.navigate(to: route)
        }
    }

    private func handleDeepLink(_ url: URL) {
        guard let route = AppRoute.from(url: url) else {
            return
        }
        handleRoute(route)
    }
}
