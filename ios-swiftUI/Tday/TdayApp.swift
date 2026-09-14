import Sentry
import SwiftData
import SwiftUI

@main
struct TdayApp: App {
    @State private var appContainer: AppContainer?
    @State private var isLaunchSplashHeld = false

    init() {
        // Apply the persisted in-app language before any view renders so even
        // the launch splash is localized. nil = follow the system language.
        LanguageBundle.activate()
        let storedLanguage = LanguageStore().load()
        LanguageBundle.setLanguage(
            storedLanguage == LanguageStore.systemValue ? nil : storedLanguage,
        )
        TdayFont.applyGlobalAppearances()
        SentryConfiguration.start()
        NotificationDeepLinkDelegate.shared.install()
        WatchSessionManager.shared.activate()
        // Register the ~30-min background widget-refresh handler before launch completes.
        WidgetBackgroundRefresh.register()
    }

    var body: some Scene {
        WindowGroup {
            // Bare, and deliberately: this boundary is splash → splash. `appContainer`
            // arrives long before the bootstrap does, so the arm it switches to is
            // `AppRootView` still drawing `AppLaunchSplashView`, and the hand-over a user
            // can actually see is one level in, animated there on `showsLaunchSplash`.
            //
            // What makes "no cut here" true rather than merely intended is that the two
            // splashes draw the same pixels. They are two structural positions, so two
            // identities, so anything either of them seeds itself is seeded twice — which
            // is why the tagline is the process-wide `launchTagline` and not `@State`.
            // Adding anything else to that screen that varies per instance puts a cut
            // back here, and there is nothing on this `Group` to fade it with.
            Group {
                if let appContainer, !isLaunchSplashHeld {
                    AppRootView(container: appContainer)
                        .modelContainer(appContainer.modelContainer)
                } else {
                    AppLaunchSplashView(isHeld: $isLaunchSplashHeld)
                }
            }
            .tdayAppTypography()
            // The motion gate is installed HERE and not only inside `tdayAppTheme`.
            // `AppRootView` applies the theme to its own body, and a view's
            // `@Environment` resolves against the environment it was *placed* in — so a
            // provider the root view installs reaches every descendant and never the
            // root view's own property. Its three animations (the tab hand-over, the
            // dock and create button, the onboarding blur) would fall back to reading
            // `accessibilityReduceMotion` through the accessor, which is correct at
            // first draw and says nothing about the flip. The copy inside the theme
            // stays for `AppLockWindowHost`, whose separate window inherits none of this.
            .tdayResolvedMotion()
            .task {
                guard appContainer == nil else {
                    return
                }
                await MainActor.run {
                    appContainer = AppContainer.shared
                }
            }
        }
    }
}
