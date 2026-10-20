import Foundation

@MainActor
final class SettingsRepository {
    private let api: TdayAPIService
    private let cacheManager: OfflineCacheManager
    private let secureStore: SecureStore

    init(api: TdayAPIService, cacheManager: OfflineCacheManager, secureStore: SecureStore) {
        self.api = api
        self.cacheManager = cacheManager
        self.secureStore = secureStore
    }

    func isAiSummaryEnabledSnapshot() -> Bool {
        if secureStore.isLocalMode() {
            return false
        }
        return cacheManager.loadOfflineState().aiSummaryEnabled
    }

    /// Refreshes the user's AI-summary preference from the server (per-user, default ON).
    func refreshAiSummaryEnabled() async -> Bool {
        if secureStore.isLocalMode() {
            return false
        }

        do {
            let enabled = try await api.getPreferences().aiSummaryEnabled ?? true
            _ = try await cacheManager.updateOfflineState { state in
                var nextState = state
                nextState.aiSummaryEnabled = enabled
                return nextState
            }
            return enabled
        } catch {
            return (try? await cacheManager.loadOfflineState().aiSummaryEnabled) ?? true
        }
    }

    /// Persists the user's AI-summary on/off preference and mirrors it into the offline
    /// cache so the dashboard gate reflects it immediately.
    @discardableResult
    func setAiSummaryEnabled(_ enabled: Bool) async throws -> Bool {
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.aiSummaryEnabled = enabled
            return nextState
        }
        if secureStore.isLocalMode() {
            return enabled
        }
        let response = try await api.patchPreferences(payload: PreferencesDTO(
            direction: nil,
            sortBy: nil,
            groupBy: nil,
            rrule: nil,
            aiSummaryEnabled: enabled
        ))
        return response.aiSummaryEnabled ?? enabled
    }

    /// "scheduled" or "floater" — the root feed a fresh cold launch should open on. Unlike
    /// `isAiSummaryEnabledSnapshot`, this preference IS user-configurable in Local Mode too —
    /// there is no server fallback to hardcode, so the cache (which Local Mode also writes
    /// through) is read directly in both modes. Uses the cache's in-memory
    /// `defaultHomeScreenSnapshot` mirror rather than `loadOfflineState()`: this runs
    /// synchronously from `AppRootView.init` on cold launch, before the splash screen's own
    /// body ever evaluates, so it must not repeat a full fetch across every cached SwiftData
    /// entity type on the main actor.
    func defaultHomeScreenSnapshot() -> String {
        cacheManager.defaultHomeScreenSnapshot
    }

    /// Refreshes the default-home-screen preference from the server. In Local Mode there is
    /// nothing to fetch, so the cached value is returned untouched.
    func refreshDefaultHomeScreen() async -> String {
        if secureStore.isLocalMode() {
            return (try? await cacheManager.loadOfflineState().defaultHomeScreen) ?? "scheduled"
        }

        do {
            let value = try await api.getPreferences().defaultHomeScreen ?? "scheduled"
            _ = try await cacheManager.updateOfflineState { state in
                var nextState = state
                nextState.defaultHomeScreen = value
                return nextState
            }
            return value
        } catch {
            return (try? await cacheManager.loadOfflineState().defaultHomeScreen) ?? "scheduled"
        }
    }

    /// Persists the default-home-screen preference via `/api/preferences` (server mode) or
    /// the offline cache directly (Local Mode), mirroring `setAiSummaryEnabled`'s split.
    ///
    /// Server Mode writes the cache SECOND, from what the server accepted. It used to write it
    /// first, before the PATCH, and never roll it back on a throw — which left a device-local
    /// value the account never got whenever the write failed (offline, a validation error, a
    /// 5xx). That value is what `AppRootView.init` reads on the next cold launch, so the app
    /// opened on a screen the account had not agreed to, and the first successful sync then
    /// silently reverted it: the choice appeared to take and then un-take itself. Android
    /// orders it this way too (`SettingsRepository.setDefaultHomeScreen`). Local Mode keeps the
    /// cache-first write, because there it IS the store — no network to contradict it.
    @discardableResult
    func setDefaultHomeScreen(_ value: String) async throws -> String {
        if secureStore.isLocalMode() {
            _ = try await cacheManager.updateOfflineState { state in
                var nextState = state
                nextState.defaultHomeScreen = value
                return nextState
            }
            return value
        }

        let response = try await api.patchPreferences(payload: PreferencesDTO(
            direction: nil,
            sortBy: nil,
            groupBy: nil,
            rrule: nil,
            defaultHomeScreen: value
        ))
        // The request is the authority on what was written (a 200 means the value passed
        // validation and landed); the response is the stored record and normally agrees. A
        // response carrying no preferences at all still contributes the value we sent rather
        // than a default, which is what `?? value` is for.
        let accepted = response.defaultHomeScreen ?? value
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.defaultHomeScreen = accepted
            return nextState
        }
        return accepted
    }
}
