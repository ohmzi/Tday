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

    func refreshAiSummaryEnabled() async -> Bool {
        if secureStore.isLocalMode() {
            return false
        }

        do {
            let enabled = try await api.getAppSettings().aiSummaryEnabled
            _ = try await cacheManager.updateOfflineState { state in
                var nextState = state
                nextState.aiSummaryEnabled = enabled
                return nextState
            }
            return enabled
        } catch {
            return (try? await cacheManager.loadOfflineState().aiSummaryEnabled) ?? false
        }
    }

    func fetchAdminAiSummaryEnabled() async throws -> Bool {
        if secureStore.isLocalMode() {
            return false
        }

        let enabled = try await api.getAdminSettings().aiSummaryEnabled
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.aiSummaryEnabled = enabled
            return nextState
        }
        return enabled
    }

    func updateAdminAiSummaryEnabled(_ enabled: Bool) async throws -> AdminSettingsResponse {
        if secureStore.isLocalMode() {
            throw APIError(message: "Admin settings are unavailable in local mode", statusCode: nil)
        }

        let response = try await api.patchAdminSettings(payload: UpdateAdminSettingsRequest(aiSummaryEnabled: enabled))
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.aiSummaryEnabled = response.aiSummaryEnabled
            return nextState
        }
        return response
    }
}
