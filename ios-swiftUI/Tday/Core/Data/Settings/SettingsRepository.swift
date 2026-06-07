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
}
