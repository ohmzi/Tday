import SwiftUI

struct AppRootView: View {
    private let container: AppContainer

    @State private var appViewModel: AppViewModel
    @State private var authViewModel: AuthViewModel

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
            if !appViewModel.hasCompletedInitialBootstrap {
                AppLaunchSplashView()
            } else {
                let showOnboardingOverlay = !appViewModel.authenticated && appViewModel.versionCheckResult == .compatible

                NavigationStack(
                    path: Binding(
                        get: { appViewModel.navigationPath },
                        set: { appViewModel.navigationPath = $0 }
                    )
                ) {
                    TdayBackground {
                        HomeScreen(container: container) { route in
                            handleRoute(route)
                        }
                    }
                    .blur(radius: showOnboardingOverlay ? 6 : 0)
                    .scaleEffect(showOnboardingOverlay ? 0.992 : 1)
                    .animation(.easeInOut(duration: 0.22), value: showOnboardingOverlay)
                    .navigationDestination(for: AppRoute.self) { route in
                        switch route {
                        case .home:
                            HomeScreen(container: container) { nextRoute in
                                handleRoute(nextRoute)
                            }
                        case .todayTodos:
                            TodoListScreen(container: container, mode: .today, listId: nil, listName: nil, highlightedTodoId: nil)
                        case .overdueTodos:
                            TodoListScreen(container: container, mode: .overdue, listId: nil, listName: nil, highlightedTodoId: nil)
                        case .scheduledTodos:
                            TodoListScreen(container: container, mode: .scheduled, listId: nil, listName: nil, highlightedTodoId: nil)
                        case let .allTodos(highlightTodoId):
                            TodoListScreen(container: container, mode: .all, listId: nil, listName: nil, highlightedTodoId: highlightTodoId)
                        case .priorityTodos:
                            TodoListScreen(container: container, mode: .priority, listId: nil, listName: nil, highlightedTodoId: nil)
                        case let .listTodos(listId, listName):
                            TodoListScreen(container: container, mode: .list, listId: listId, listName: listName, highlightedTodoId: nil)
                        case .completed:
                            CompletedScreen(container: container)
                        case .calendar:
                            CalendarScreen(container: container)
                        case .settings:
                            SettingsScreen(viewModel: appViewModel)
                        case .latestRelease:
                            LatestReleaseScreen(viewModel: appViewModel)
                        }
                    }
                    .overlay(alignment: .top) {
                        OfflineBanner(
                            visible: appViewModel.authenticated && appViewModel.isOffline,
                            pendingMutationCount: appViewModel.pendingMutationCount,
                            noticeID: appViewModel.offlineNoticeID
                        )
                        .padding(.top, 8)
                    }
                    .overlay(alignment: .bottom) {
                        if let message = container.snackbarManager.message {
                            Text(message)
                                .font(.tdayRounded(.footnote, weight: .bold))
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
                            let isVersionBlocking = appViewModel.versionCheckResult != .compatible

                            if isVersionBlocking {
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
                                    onLogin: { email, password, source in
                                        let success = await authViewModel.login(email: email, password: password, source: source)
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

                        if appViewModel.authenticated && appViewModel.versionCheckResult != .compatible {
                            UpdateRequiredView(
                                versionCheckResult: appViewModel.versionCheckResult,
                                onRetry: {
                                    Task { await appViewModel.recheckVersion() }
                                }
                            )
                        }
                    }
                }
                .navigationInteractivePopGesture()
                .background(TdayTheme.backgroundGradient.ignoresSafeArea())
            }
        }
        .background(TdayTheme.backgroundGradient.ignoresSafeArea())
        .preferredColorScheme(appViewModel.themeMode.colorScheme)
        .task {
            if !appViewModel.hasCompletedInitialBootstrap {
                await appViewModel.bootstrap()
            }
        }
        .onOpenURL { url in
            handleDeepLink(url)
        }
    }

    private func handleRoute(_ route: AppRoute) {
        appViewModel.navigate(to: route)
    }

    private func handleDeepLink(_ url: URL) {
        guard let route = AppRoute.from(url: url) else {
            return
        }
        handleRoute(route)
    }
}

private struct AppLaunchSplashView: View {
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
    "Turning I should into scheduled"
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
