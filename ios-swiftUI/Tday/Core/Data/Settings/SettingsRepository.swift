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
    /// through) is read directly in both modes.
    func defaultHomeScreenSnapshot() -> String {
        cacheManager.loadOfflineState().defaultHomeScreen
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
    @discardableResult
    func setDefaultHomeScreen(_ value: String) async throws -> String {
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.defaultHomeScreen = value
            return nextState
        }
        if secureStore.isLocalMode() {
            return value
        }
        let response = try await api.patchPreferences(payload: PreferencesDTO(
            direction: nil,
            sortBy: nil,
            groupBy: nil,
            rrule: nil,
            defaultHomeScreen: value
        ))
        return response.defaultHomeScreen ?? value
    }
}
