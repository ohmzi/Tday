import SwiftUI
import UIKit

struct AppRootView: View {
    private let container: AppContainer

    @State private var appViewModel: AppViewModel
    @State private var authViewModel: AuthViewModel
    @State private var notificationDeepLinkRouter = NotificationDeepLinkRouter.shared
    @State private var hasLeftActiveScene = false
    @State private var isLaunchSplashHeld = false
    // Seeded once, from the user's "Default home screen" setting, at this view's own `init` —
    // a config change or SwiftUI re-rendering the same identity keeps whatever `rootFeedTab`
    // already holds instead of re-applying the default underneath an in-session dock tap.
    @State private var rootFeedTab: RootFeedTab
    @State private var rootCreateTaskRequestID = 0
    @State private var pendingRootCreateTask: PendingRootCreateTask?
    // Prefill from a share-extension capture, applied to the next create sheet.
    @State private var rootCreateTaskPrefill: CreateTaskPayload?
    @State private var scheduledTaskHomeScrollToTopRequestID = 0
    @State private var floaterTaskHomeScrollToTopRequestID = 0
    @State private var rootDockCollapsed = false
    @State private var rootControlsVisible = true
    // Optional biometric gate, default OFF. When disabled every member below is inert.
    @State private var appLock = AppLockController()
    @Environment(\.scenePhase) private var scenePhase
    /// The app's one motion gate — see `TdayMotionEnvironment.swift`. Every
    /// `.animation` in this view's body passes its spec through it, so Reduce Motion
    /// refuses the trip in one place rather than at each of them. It resolves against
    /// the provider `TdayApp` installs above this view, not the one `tdayAppTheme`
    /// applies to this body: a property wrapper reads the environment the view was
    /// placed in, so a gate this view installs would reach its children and miss it.
    /// `AppSnackbar` below is a separate view with an environment of its own and is
    /// not covered — its drag snap-back still animates, and is owed to the open
    /// `reduced-motion-coverage` box.
    @Environment(\.tdayAnimation) private var tdayAnimation

    init(container: AppContainer) {
        self.container = container
        _appViewModel = State(initialValue: AppViewModel(container: container))
        _authViewModel = State(initialValue: AuthViewModel(
            authRepository: container.authRepository,
            systemCredentialService: container.systemCredentialService
        ))
        _rootFeedTab = State(initialValue: rootFeedTabFromDefaultHomeScreenApiValue(
            container.settingsRepository.defaultHomeScreenSnapshot()
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
                            case .scheduledTaskHome:
                                ScheduledTaskHomeScreen(
                                    container: container,
                                    onRootFeedTabSelected: handleRootFeedTabSelection,
                                    showsRootControls: false,
                                    createTaskRequestID: rootCreateTaskRequestID,
                                    createTaskPrefill: rootCreateTaskPrefill,
                                    onCreateTaskSheetClosed: { rootCreateTaskPrefill = nil },
                                    scrollToTopRequestID: scheduledTaskHomeScrollToTopRequestID,
                                    onRootDockCollapsedChange: { rootDockCollapsed = $0 },
                                    onRootControlsVisibleChange: { rootControlsVisible = $0 },
                                    pullRefreshEnabled: !appViewModel.isLocalMode,
                                    summaryAvailable: !appViewModel.isLocalMode && !appViewModel.isOffline
                                ) { route in
                                    handleRoute(route)
                                }
                                .transition(.opacity)
                            case .floaterTaskHome:
                                TodoListScreen(
                                    container: container,
                                    mode: .floater,
                                    listId: nil,
                                    listName: nil,
                                    highlightedTodoId: nil,
                                    rootFeedTab: .floaterTaskHome,
                                    onRootFeedTabSelected: handleRootFeedTabSelection,
                                    showsRootControls: false,
                                    pullRefreshEnabled: !appViewModel.isLocalMode,
                                    usesRootFeedHeader: true,
                                    createTaskRequestID: rootCreateTaskRequestID,
                                    scrollToTopRequestID: floaterTaskHomeScrollToTopRequestID,
                                    onRootDockCollapsedChange: { rootDockCollapsed = $0 },
                                    onRootControlsVisibleChange: { rootControlsVisible = $0 },
                                    onOpenFloaterList: { listId, listName in
                                        handleRoute(.floaterListTodos(listId: listId, listName: listName))
                                    },
                                    onOpenSettings: {
                                        handleRoute(.settings)
                                    },
                                    onOpenCompleted: {
                                        handleRoute(.completed)
                                    },
                                    summaryAvailable: !appViewModel.isLocalMode && !appViewModel.isOffline
                                )
                                .transition(.opacity)
                            }

                            if appViewModel.isWorkspaceAvailable, rootControlsVisible {
                                rootFloatingControls
                                    // Down and out through the bottom edge, and back up the
                                    // same way. The opacity half is load-bearing rather than
                                    // decorative: `.move(edge:)` offsets by the view's own
                                    // height, which clears the dock's box but not the home
                                    // indicator strip it sits above, so the fade is what
                                    // guarantees the control is gone rather than parked in
                                    // it. Android pairs `slideOutVertically { it }` with a
                                    // fade for the same reason. The travel answers to
                                    // `rootControlsVisible` and to nothing else — the unlock
                                    // that also inserts these controls is handed no animation
                                    // below, for the reason written there.
                                    .transition(.move(edge: .bottom).combined(with: .opacity))
                            }
                        }
                        // The dock's pill slides to the tab that was tapped and the feed under
                        // it changed on the next frame: one gesture running at two speeds. The
                        // two transitions above are inert without a transaction, and this is
                        // it. Nothing in the body travels — the arriving feed is drawn in the
                        // slot the leaving one had — so by the geometry rule this is not
                        // Emphasis, and a tab handover is the Quick rung the vocabulary
                        // already names for it. Quick also keeps the fade inside the 180 ms
                        // `presentPendingRootCreateTaskIfReady` waits out, so a deep link that
                        // switches tab and then asks for a create sheet still finds one feed
                        // on screen. The floating controls are in the transaction too, which
                        // crosses their accent over with the body instead of snapping it; the
                        // pill is a `UISegmentedControl`, so its own indicator stays on
                        // UIKit's timing rather than this one. Android crossfades the same
                        // swap on the same rung and curve, and drives its own create button's
                        // accent across on that rung too so the corner doesn't cut while the
                        // body fades. Reduce Motion passes no animation at all: the swap
                        // cuts to the arriving feed finished rather than holding it
                        // half-faded (`docs/motion.md`'s fifth idiom rule).
                        .animation(
                            tdayAnimation(TdayMotion.standard(duration: TdayMotion.Durations.quick)),
                            value: rootFeedTab
                        )
                        // The dock and the create button used to be nothing but the `if`
                        // above: expanding the search field took them off the screen in the
                        // frame the field grew into, leaving a hole where the chrome had
                        // been and then putting the chrome back in it. The transition on the
                        // controls is inert without a transaction, and this is that one.
                        //
                        // Travel, so Emphasis by the geometry rule — and Settle is the rung
                        // spelled as a spring, the token whose own doc string names a dock
                        // and a bar. One spec for both directions: the exit is the enter
                        // played backwards, and a length of its own would read as two
                        // gestures rather than one control getting out of the way. The first
                        // idiom rule only forbids an exit that OUTLASTS its arrival, and
                        // these cannot. Android drives the same two controls off the same
                        // spring; web, having no spring runtime, spells it as the Gesture
                        // easing on the Emphasis rung.
                        //
                        // Its own `.animation` rather than a second value on the one above,
                        // because a tab swap and the chrome standing down are different
                        // events that happen to share a container — folding them together
                        // would put the dock's departure on the tab handover's clock.
                        // Reduce Motion passes nil, so the controls are taken away and put
                        // back finished (`docs/motion.md`'s fifth idiom rule).
                        .animation(
                            tdayAnimation(TdayMotion.settle),
                            value: rootControlsVisible
                        )
                        // The other way these controls come and go: `isWorkspaceAvailable` in
                        // the `if` above, which the unlock flips at the same moment as the
                        // `showOnboardingOverlay` further down. That one opens a transaction
                        // over this whole subtree, and a transaction is all a `.transition`
                        // needs — so without this line the move above would ALSO play the
                        // lock and unlock, travelling the dock and the button up from under
                        // the bottom edge on the Quick rung, in the one handover where
                        // nothing else on the screen moves at all.
                        //
                        // Handing that value no animation is how an insertion says it has no
                        // before. Android says it in its own dialect: `RootFeedContent` is
                        // composed fresh inside the arriving half of the lock Crossfade, and
                        // `AnimatedVisibility` plays no enter for a `visible` that was
                        // already true on its first composition, so the dock is simply drawn
                        // in its slot while the wizard hands over above it. What fades on
                        // either client is the wizard, not the chrome underneath it.
                        //
                        // It also settles a split this file already had: an unlock whose
                        // version check is blocking leaves `showOnboardingOverlay` false on
                        // both sides, so that unlock opened no transaction and the chrome
                        // appeared finished, while an ordinary one animated it. The same
                        // event cannot mean two things depending on a version number.
                        .animation(nil, value: showOnboardingOverlay)
                    }
                    .blur(radius: showOnboardingOverlay ? 6 : 0)
                    .scaleEffect(showOnboardingOverlay ? 0.992 : 1)
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
                                    pendingCertificateApproval: appViewModel.pendingCertificateApproval,
                                    pendingApprovalMessage: appViewModel.pendingApprovalMessage,
                                    authViewModel: authViewModel,
                                    systemCredentialService: container.systemCredentialService,
                                    onConnectServer: { rawURL in
                                        await appViewModel.connectServer(rawURL: rawURL)
                                    },
                                    onResetServerTrust: { rawURL in
                                        await appViewModel.resetTrustedServer(rawURL: rawURL)
                                    },
                                    onApproveCertificate: {
                                        await appViewModel.approveServerCertificate()
                                    },
                                    onDismissCertificate: {
                                        appViewModel.dismissCertificateApproval()
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
                                .transition(.opacity)
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
                    // Locking and unlocking the app is one event with several surfaces in
                    // it: the app behind goes out of focus and shrinks a thousandth, the
                    // wizard covers it, and the floating controls that only exist for a
                    // real workspace come and go underneath. The blur and the scale were
                    // already animated, on a 220 ms of their own; the wizard carried no
                    // `.transition` at all, so it cut in over a backdrop that was still
                    // resolving. They need one transaction between them, and a transaction
                    // reaches a `.transition` only from a modifier applied OUTSIDE the
                    // `.overlay` that inserts it — which is why this sits below the overlay
                    // rather than beside the blur it also drives. Absorbing that 220 is the
                    // point: three surfaces of one event cannot keep separate clocks, and
                    // the odd duration was never in the vocabulary to be kept. The wizard
                    // is drawn where it will stay and the controls are put back in their
                    // slot finished, so nothing in the handover travels; the 0.992 is the
                    // blur's other half and not a geometry change — eight thousandths is a
                    // focus cue, too small to read as a move, and it answers to the rung
                    // the blur it accompanies is on. Those controls carry a move of their
                    // own for when the search field takes their row, and they are held out
                    // of this transaction (`.animation(nil, value:)` above) so it cannot
                    // drive that move through an event nothing else moves in. So by the
                    // geometry rule this is not Emphasis, and a whole-screen handover is
                    // the Quick rung the vocabulary names for it — the rung the tab swap
                    // above already runs on. Standard is the curve because a crossfade runs
                    // both halves off one clock and neither Enter nor Exit describes that,
                    // and one animation covers both directions because the way in and the
                    // way out are the same handover reversed. Android times the same moment
                    // on this rung and curve; its third surface is a crossfade rather than
                    // a fade-in, because it draws an inert placeholder feed under the
                    // wizard where this one draws the real screens, and it has no scale
                    // because its backdrop cue is a 14 dp blur that carries the focus
                    // change on its own. Reduce Motion passes no animation: the app is
                    // drawn unlocked and in focus, finished, rather than held mid-blur
                    // (`docs/motion.md`'s fifth idiom rule).
                    .animation(
                        tdayAnimation(TdayMotion.standard(duration: TdayMotion.Durations.quick)),
                        value: showOnboardingOverlay
                    )
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
                // `.snappy(duration: 0.3)` was SwiftUI's own preset — `spring(duration:
                // 0.3, bounce: 0.15)` — and the Snappy token is `response: 0.28,
                // dampingFraction: 0.86`, the same bounce and the same perceptual length
                // to within a frame. The literal was approximating this token, so naming
                // it is not a retiming. What the site gains is the gate: under Reduce
                // Motion the toast is simply there and simply gone, the slide and the
                // fade refused rather than shortened (`docs/motion.md`'s fifth idiom
                // rule), which is what the rest of this body has done since 35a.
                .animation(
                    tdayAnimation(TdayMotion.snappy),
                    value: container.snackbarManager.content?.id
                )
            }
        }
        // FALLBACK layer only. Applied INSIDE the theme/locale modifiers below so it is themed
        // and localized like the rest of the app, and it covers every state above it — splash,
        // onboarding, all pushed destinations. What it CANNOT cover is a `.sheet` or
        // `.fullScreenCover`: those are presented in their own layer on top of this whole view,
        // so a create-task sheet left open at backgrounding used to stay fully readable over the
        // "locked" app. `AppLockWindowHost` below is the real gate; this stays for the case where
        // no window scene is available yet. Renders nothing while the setting is off.
        .overlay {
            switch appLockCoverMode {
            case .gate:
                AppLockGateView(
                    isAuthenticating: appLock.isAuthenticating,
                    failureMessage: appLock.failureMessage,
                    onUnlock: {
                        await appLock.authenticate()
                    }
                )
            case .privacyCover:
                AppLockPrivacyCover()
            case .hidden:
                EmptyView()
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
        // The gate that actually holds: a separate UIWindow above every presented surface.
        // Values are read here, in `body`, so Observation re-runs this view (and therefore the
        // representable's update) whenever the lock state changes.
        .background(
            AppLockWindowHost(
                mode: appLockCoverMode,
                isAuthenticating: appLock.isAuthenticating,
                failureMessage: appLock.failureMessage,
                themeMode: appViewModel.themeMode,
                onUnlock: {
                    await appLock.authenticate()
                }
            )
        )
        // Cold start: `.onChange(of: scenePhase)` never fires for the launch value, so the
        // first prompt has to come from here. No-op while the setting is off.
        .task {
            await appLock.authenticateIfNeeded()
        }
        .task {
            if !appViewModel.hasCompletedInitialBootstrap {
                await appViewModel.bootstrap()
            }
            // Seed the launch value. `.onChange(of: scenePhase)` never fires for it, and with
            // nothing recorded the first foreground return has no "before" to compare against
            // — which is precisely the trip out to iOS Settings we are here to catch.
            await appViewModel.refreshNotificationAuthorization()
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
                // Foreground return: re-prompt while the gate is armed.
                Task {
                    await appLock.authenticateIfNeeded()
                }
                container.reapplyDatabaseProtection()
                Task {
                    await container.todoRepository.drainWidgetCompletions()
                }
                // The notification permission is granted in the *system* Settings app, so the
                // only moment T'Day can notice is the return from it — and the screen the user
                // lands back on is whichever one they left, not ours. Owned here rather than in
                // SettingsScreen so "OS on + switch on" starts delivering from anywhere.
                Task {
                    await appViewModel.refreshNotificationAuthorization()
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
            case .background:
                // Re-arm the gate only on a real backgrounding. Doing it on .inactive would
                // fire while the Face ID sheet itself is up (and on every notification-centre
                // pull), which would relock mid-prompt and loop.
                appLock.lockIfEnabled()
                // Sidecars SQLite recreated during this session are born with the container
                // default; re-stamp before the app is suspended and the files sit at rest.
                container.reapplyDatabaseProtection()
                hasLeftActiveScene = true
                // Arm the ~30-min background widget refresh each time we leave the foreground.
                WidgetBackgroundRefresh.scheduleNext()
            case .inactive:
                hasLeftActiveScene = true
                WidgetBackgroundRefresh.scheduleNext()
            @unknown default:
                break
            }
        }
    }

    private var appLockCoverMode: AppLockCoverMode {
        appLock.coverMode(isSceneActive: scenePhase == .active)
    }

    // Kept out of `body`: as part of the ~300-line body expression this switch
    // pushes the type-checker over its time limit ("unable to type-check this
    // expression in reasonable time"); as a standalone function each case is
    // checked independently.
    @ViewBuilder
    private func destinationView(for route: AppRoute) -> some View {
        switch route {
        case .scheduledTaskHome:
            ScheduledTaskHomeScreen(
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
        case .floaterTaskHome:
            Color.clear
                .navigationBarBackButtonHidden(true)
                .toolbar(.hidden, for: .navigationBar)
                .onAppear {
                    selectRootFeedTab(.floaterTaskHome)
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
                    handleRoute(.floaterTaskHome)
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
                    appViewModel.navigate(to: .scheduledTaskHome)
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
        case .scheduledTaskHome:
            selectRootFeedTab(.scheduledTaskHome)
        case .createTodayTodo:
            requestRootCreateTask(on: .scheduledTaskHome)
        case .createFloaterTodo:
            requestRootCreateTask(on: .floaterTaskHome)
        case .floaterTaskHome:
            selectRootFeedTab(.floaterTaskHome)
        default:
            appViewModel.navigate(to: route)
        }
    }

    private func handleRootFeedTabSelection(_ tab: RootFeedTab) {
        if tab == rootFeedTab {
            requestRootFeedScrollToTop(for: tab)
            return
        }
        HapticManager.selection()
        selectRootFeedTab(tab)
    }

    private func selectRootFeedTab(_ tab: RootFeedTab) {
        rootFeedTab = tab
        appViewModel.navigationPath = []
    }

    private func requestRootFeedScrollToTop(for tab: RootFeedTab) {
        switch tab {
        case .scheduledTaskHome:
            scheduledTaskHomeScrollToTopRequestID += 1
        case .floaterTaskHome:
            floaterTaskHomeScrollToTopRequestID += 1
        }
    }

    private func requestRootCreateTask(on tab: RootFeedTab) {
        selectRootFeedTab(tab)
        pendingRootCreateTask = PendingRootCreateTask(tab: tab)
        presentPendingRootCreateTaskIfReady()
    }

    /// Turns the oldest share-extension capture into a prefilled create sheet
    /// on the scheduled task home tab. One per activation; the queue holds the rest. Skips
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
        requestRootCreateTask(on: .scheduledTaskHome)
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
            requestRootCreateTask(on: .scheduledTaskHome)
            return
        }

        if newPath.contains(.createFloaterTodo) {
            requestRootCreateTask(on: .floaterTaskHome)
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
                requestRootCreateTask(on: .scheduledTaskHome)
            }
            return
        }

        if path.contains(.createFloaterTodo) {
            DispatchQueue.main.async {
                requestRootCreateTask(on: .floaterTaskHome)
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
        rootFeedTab == .floaterTaskHome ? .tdayFloaterGreen : .tdayTodayBlue
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
        // A backend 5xx (server/database down) reads as a server error, not "you're offline".
        if appViewModel.offlineNoticeKind == .serverDown {
            return L("Server error — the backend or database may be down. Try again shortly.")
        }
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

    // Icons are removed app-wide, with one deliberate exception: the action
    // slot below. It is used only for Undo (see `UndoableDeleteScheduler` —
    // the sole call site that ever sets `actionLabel`/`action`), and that one
    // word started following complete/delete toasts for both todos and
    // floaters, on every screen; a rotate-back glyph reads faster there than
    // re-parsing "Undo" each time, and needs no localized width to make room
    // for. The variant cue for error/success/info still lives in the card
    // surface itself, unchanged. The action label still uses the accent
    // colour, now via `.accessibilityLabel` rather than visible text.
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
                    Image("ActionUndo")
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 20, height: 20)
                        .foregroundStyle(accent)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(label)
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

/// Full-screen cover shown while the optional app lock is armed. Opaque on purpose: it has to
/// hide the content underneath, including in the app-switcher snapshot.
private struct AppLockGateView: View {
    let isAuthenticating: Bool
    let failureMessage: String?
    let onUnlock: () async -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        ZStack {
            colors.background
                .ignoresSafeArea()

            VStack(spacing: 18) {
                Image(systemName: "lock.fill")
                    .font(.system(size: 34, weight: .semibold))
                    .foregroundStyle(colors.primary)

                Text(L("T'Day is locked"))
                    .font(.tdayRounded(size: 20, weight: .heavy))
                    .foregroundStyle(colors.onSurface)

                if let failureMessage {
                    Text(failureMessage)
                        .font(.tdayRounded(size: 14, weight: .bold))
                        .foregroundStyle(colors.error)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Button {
                    Task { await onUnlock() }
                } label: {
                    HStack(spacing: 8) {
                        if isAuthenticating {
                            ProgressView().tint(colors.onPrimary)
                        }
                        Text(L("Unlock"))
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
                .opacity(isAuthenticating ? 0.72 : 1)
                .disabled(isAuthenticating)
            }
            .padding(.horizontal, 40)
            .frame(maxWidth: 430)
        }
        // Swallow taps so nothing underneath can be driven blind through the cover.
        .contentShape(Rectangle())
        .onTapGesture {}
    }
}

/// Hosts the app-lock UI in its OWN `UIWindow`, above every presented surface.
///
/// A root-level `.overlay` renders inside the app's view hierarchy, and a `.sheet` /
/// `.fullScreenCover` is presented on top of that hierarchy — so a create-task sheet that was
/// open when the app was backgrounded stayed fully readable over the "locked" app, and showed up
/// in the app-switcher snapshot. A window at `.alert + 1` sits above those presentation layers,
/// so modals get covered too. The biometric prompt is drawn by the system out of process and
/// still appears above this window.
///
/// Itself invisible: it is only here to give the coordinator a view (and therefore a window
/// scene) to hang the lock window off, and to be re-run whenever `mode` changes.
private struct AppLockWindowHost: UIViewRepresentable {
    let mode: AppLockCoverMode
    let isAuthenticating: Bool
    let failureMessage: String?
    let themeMode: AppThemeMode
    let onUnlock: () async -> Void

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
        context.coordinator.update(
            isPresenting: mode != .hidden,
            content: AnyView(lockContent),
            hostView: uiView
        )
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        // SwiftUI tears representables down on the main thread; the window must go with the
        // view or it would stay on screen with nothing left to drive it.
        MainActor.assumeIsolated {
            coordinator.teardown()
        }
    }

    // The theme has to be re-applied here: this content is rendered into a different window, so
    // it inherits nothing from the modifiers wrapping the app's own root view. `L()` resolves
    // through the swizzled main bundle, so localization needs no environment.
    @ViewBuilder
    private var lockContent: some View {
        Group {
            switch mode {
            case .gate:
                AppLockGateView(
                    isAuthenticating: isAuthenticating,
                    failureMessage: failureMessage,
                    onUnlock: onUnlock
                )
            case .privacyCover:
                AppLockPrivacyCover()
            case .hidden:
                EmptyView()
            }
        }
        .tdayAppTheme(themeMode: themeMode)
        .tdayAppTypography()
    }

    @MainActor
    final class Coordinator {
        private var window: UIWindow?
        private var host: UIHostingController<AnyView>?

        func update(isPresenting: Bool, content: AnyView, hostView: UIView) {
            guard isPresenting else {
                teardown()
                return
            }

            guard let scene = hostView.window?.windowScene ?? Self.foregroundWindowScene() else {
                // No scene yet (cold start, before the view is in a window). The in-hierarchy
                // fallback overlay is already covering the content in this window.
                return
            }

            if let host, let window, window.windowScene === scene {
                host.rootView = content
                window.isHidden = false
                return
            }

            teardown()
            let controller = UIHostingController(rootView: content)
            let lockWindow = UIWindow(windowScene: scene)
            lockWindow.rootViewController = controller
            // Above sheets and fullScreenCovers (which live at `.normal`) and above UIKit alerts.
            lockWindow.windowLevel = .alert + 1
            // Visible but never made key: the app's own window keeps first-responder duties,
            // while hit-testing still reaches this one first because it sits higher.
            lockWindow.isHidden = false
            window = lockWindow
            host = controller
        }

        func teardown() {
            window?.isHidden = true
            window?.rootViewController = nil
            window = nil
            host = nil
        }

        private static func foregroundWindowScene() -> UIWindowScene? {
            UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .first { $0.activationState != .unattached }
        }
    }
}

/// Plain opaque cover for the app-switcher snapshot while the lock is enabled but not yet
/// re-armed. Carries no task content by design.
private struct AppLockPrivacyCover: View {
    @Environment(\.tdayColors) private var colors

    var body: some View {
        ZStack {
            colors.background
                .ignoresSafeArea()

            Image(systemName: "lock.fill")
                .font(.system(size: 34, weight: .semibold))
                .foregroundStyle(colors.primary)
        }
    }
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
        case .scheduledTaskHome:
            return .scheduledTaskHome
        case .floaterTaskHome:
            return .floaterTaskHome
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
    /// The English line is the catalog key; `L` resolves it against the in-app
    /// language so the splash speaks the same language as the rest of the app.
    @State private var taglineKey = splashTaglines.randomElement() ?? "Running on your server, running your life"

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

                Text(L(taglineKey))
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
    "Prioritizing so you don't have to",
    "Your excuses are not on the schedule",
    "Built for the version of you that shows up",
    "Tomorrow you is watching",
    "Where good intentions get timestamps",
    "No cloud, no clutter, no forgetting",
    "Your day, minus the guesswork",
    "Quietly running the boring part of your life",
    "Your brain gets a break, your server gets a job",
    "Ambition, now with due dates",
    "Small tasks, big server energy",
    "Every list deserves a home you own",
    "Uptime for your intentions",
    "The nag you actually asked for",
    "Because \u{2018}sometime this week\u{2019} is not a time",
    "Your plans, backed up and back on track",
    "Reminding you from a box you can unplug",
    "Fewer tabs, more done",
    "Turning the mental pile into a plan",
    "Localhost, but for your life",
    "Your inbox of intentions, sorted",
    "Deadlines respected, privacy included",
    "Root access to your own routine",
    "Making \u{2018}busy\u{2019} mean something again",
    "Your future self filed a request",
    "The quiet part of getting things done",
    "No subscription, no surprises, no forgetting",
    "Order, served fresh from your own rack",
    "Where \u{2018}urgent\u{2019} finally meets \u{2018}planned\u{2019}",
    "Your day compiles cleanly now",
    "Off the cloud, on the ball"
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
/// status") advances to the scheduled task home screen the moment approval lands.
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
