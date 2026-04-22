import Foundation

@MainActor
final class SettingsRepository {
    private let api: TdayAPIService
    private let cacheManager: OfflineCacheManager

    init(api: TdayAPIService, cacheManager: OfflineCacheManager) {
        self.api = api
        self.cacheManager = cacheManager
    }

    func isAiSummaryEnabledSnapshot() -> Bool {
        cacheManager.loadOfflineState().aiSummaryEnabled
    }

    func refreshAiSummaryEnabled() async -> Bool {
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
        let enabled = try await api.getAdminSettings().aiSummaryEnabled
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.aiSummaryEnabled = enabled
            return nextState
        }
        return enabled
    }

    func updateAdminAiSummaryEnabled(_ enabled: Bool) async throws -> AdminSettingsResponse {
        let response = try await api.patchAdminSettings(payload: UpdateAdminSettingsRequest(aiSummaryEnabled: enabled))
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.aiSummaryEnabled = response.aiSummaryEnabled
            return nextState
        }
        return response
    }
}
