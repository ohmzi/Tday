import SwiftUI

struct AppRootView: View {
    private let container: AppContainer

    @State private var appViewModel: AppViewModel
    @State private var authViewModel: AuthViewModel

    init(container: AppContainer) {
        self.container = container
        _appViewModel = State(initialValue: AppViewModel(container: container))
        _authViewModel = State(initialValue: AuthViewModel(authRepository: container.authRepository))
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
            }
        }
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
    var body: some View {
        Image("LaunchSplashBundle")
            .resizable()
            .aspectRatio(contentMode: .fill)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipped()
            .ignoresSafeArea()
    }
}

private struct SplashBackdrop: View {
    let palette: SplashPalette
    let animate: Bool

    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            let height = proxy.size.height

            ZStack {
                Circle()
                    .fill(palette.topGlow)
                    .frame(width: width * 0.72, height: width * 0.72)
                    .blur(radius: 74)
                    .offset(x: animate ? width * 0.26 : width * 0.16, y: animate ? -height * 0.19 : -height * 0.24)
                    .animation(.easeInOut(duration: 7).repeatForever(autoreverses: true), value: animate)

                Circle()
                    .fill(palette.bottomGlow)
                    .frame(width: width * 0.82, height: width * 0.82)
                    .blur(radius: 96)
                    .offset(x: animate ? -width * 0.24 : -width * 0.14, y: animate ? height * 0.31 : height * 0.24)
                    .animation(.easeInOut(duration: 8.5).repeatForever(autoreverses: true), value: animate)

                RoundedRectangle(cornerRadius: 54, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: palette.ribbonGradient,
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: width * 0.62, height: height * 0.24)
                    .rotationEffect(.degrees(animate ? 24 : 12))
                    .blur(radius: 44)
                    .offset(x: -width * 0.22, y: -height * 0.08)
                    .animation(.easeInOut(duration: 6.8).repeatForever(autoreverses: true), value: animate)

                RoundedRectangle(cornerRadius: 48, style: .continuous)
                    .stroke(palette.frameStroke, lineWidth: 1)
                    .frame(width: width * 0.92, height: height * 0.6)
                    .rotationEffect(.degrees(animate ? -6 : -2))
                    .offset(y: height * 0.02)
                    .opacity(0.35)
                    .animation(.easeInOut(duration: 9).repeatForever(autoreverses: true), value: animate)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .allowsHitTesting(false)
    }
}

private struct SplashLoadingBadge: View {
    let palette: SplashPalette
    let animate: Bool

    var body: some View {
        HStack(spacing: 10) {
            HStack(spacing: 6) {
                ForEach(0..<3, id: \.self) { index in
                    Capsule()
                        .fill(index == 1 ? palette.accent : palette.loadingDot)
                        .frame(width: 10, height: 10)
                        .scaleEffect(animate ? 1 : 0.55)
                        .opacity(animate ? 1 : 0.45)
                        .animation(
                            .easeInOut(duration: 0.95)
                                .repeatForever(autoreverses: true)
                                .delay(Double(index) * 0.12),
                            value: animate
                        )
                }
            }

            Text("Restoring your workspace")
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundStyle(palette.loadingText)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(palette.loadingFill, in: Capsule())
        .overlay(
            Capsule()
                .stroke(palette.loadingBorder, lineWidth: 1)
        )
    }
}

private struct SplashPalette {
    let backgroundGradient: [Color]
    let topGlow: Color
    let bottomGlow: Color
    let ribbonGradient: [Color]
    let frameStroke: Color
    let cardFill: Color
    let cardBorder: Color
    let cardShadow: Color
    let badgeFill: Color
    let badgeBorder: Color
    let badgeText: Color
    let logoPlateGradient: [Color]
    let logoPlateBorder: Color
    let logoShadow: Color
    let orbitColor: Color
    let accentRing: Color
    let title: Color
    let subtitle: Color
    let accent: Color
    let loadingDot: Color
    let loadingFill: Color
    let loadingBorder: Color
    let loadingText: Color

    static let light = SplashPalette(
        backgroundGradient: [
            Color(hex: 0xF7FBFF),
            Color(hex: 0xEEF3FF),
            Color(hex: 0xFFF5EF)
        ],
        topGlow: Color(hex: 0x78D4D0, alpha: 0.34),
        bottomGlow: Color(hex: 0xF6B2BC, alpha: 0.3),
        ribbonGradient: [
            Color(hex: 0x8ED7D3, alpha: 0.24),
            Color(hex: 0x6EA8E1, alpha: 0.18)
        ],
        frameStroke: Color.white.opacity(0.42),
        cardFill: Color.white.opacity(0.72),
        cardBorder: Color.white.opacity(0.82),
        cardShadow: Color(hex: 0x5B7C9B, alpha: 0.16),
        badgeFill: Color.white.opacity(0.74),
        badgeBorder: Color.white.opacity(0.88),
        badgeText: Color(hex: 0x2A6177),
        logoPlateGradient: [
            Color.white.opacity(0.98),
            Color(hex: 0xF2F7FF)
        ],
        logoPlateBorder: Color.white.opacity(0.94),
        logoShadow: Color(hex: 0x335B73, alpha: 0.17),
        orbitColor: Color(hex: 0x2D6B6B, alpha: 0.2),
        accentRing: Color(hex: 0x6EA8E1, alpha: 0.26),
        title: Color(hex: 0x1A2233),
        subtitle: Color(hex: 0x57637B),
        accent: Color(hex: 0xE85B6F),
        loadingDot: Color(hex: 0x7DA6D5, alpha: 0.55),
        loadingFill: Color.white.opacity(0.68),
        loadingBorder: Color.white.opacity(0.84),
        loadingText: Color(hex: 0x38536D)
    )

    static let dark = SplashPalette(
        backgroundGradient: [
            Color(hex: 0x08111A),
            Color(hex: 0x101A28),
            Color(hex: 0x161528)
        ],
        topGlow: Color(hex: 0x2F8E91, alpha: 0.34),
        bottomGlow: Color(hex: 0xB64A68, alpha: 0.28),
        ribbonGradient: [
            Color(hex: 0x225E61, alpha: 0.26),
            Color(hex: 0x244C74, alpha: 0.22)
        ],
        frameStroke: Color.white.opacity(0.08),
        cardFill: Color.white.opacity(0.07),
        cardBorder: Color.white.opacity(0.12),
        cardShadow: Color.black.opacity(0.32),
        badgeFill: Color.white.opacity(0.08),
        badgeBorder: Color.white.opacity(0.14),
        badgeText: Color(hex: 0xA7D8D6),
        logoPlateGradient: [
            Color(hex: 0x102433),
            Color(hex: 0x0C1A27)
        ],
        logoPlateBorder: Color.white.opacity(0.08),
        logoShadow: Color.black.opacity(0.34),
        orbitColor: Color.white.opacity(0.14),
        accentRing: Color(hex: 0x73C9CE, alpha: 0.22),
        title: Color(hex: 0xF4F6FC),
        subtitle: Color(hex: 0xA6AEC1),
        accent: Color(hex: 0xF06B80),
        loadingDot: Color(hex: 0x6FAEF8, alpha: 0.65),
        loadingFill: Color.white.opacity(0.06),
        loadingBorder: Color.white.opacity(0.12),
        loadingText: Color(hex: 0xD5DCEC)
    )
}

private struct SplashTdayLogoMark: View {
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
                    ForEach(0..<4, id: \.self) { _ in
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

private let splashLaunchTagline = "Running on your server, running your life"

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
