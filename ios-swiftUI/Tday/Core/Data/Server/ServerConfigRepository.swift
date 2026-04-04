import Foundation

enum ServerProbeError: Error, Equatable, LocalizedError {
    case invalidURL
    case insecureTransport
    case notTdayServer
    case certificateChanged

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Enter a valid server URL"
        case .insecureTransport:
            return "HTTPS is required for remote servers"
        case .notTdayServer:
            return "This server does not look like a T'Day instance"
        case .certificateChanged:
            return "The trusted certificate changed for this server"
        }
    }
}

final class ServerConfigRepository {
    private let secureStore: SecureStore
    private let serverURLState: ServerURLState
    private let api: TdayAPIService

    init(secureStore: SecureStore, serverURLState: ServerURLState, api: TdayAPIService) {
        self.secureStore = secureStore
        self.serverURLState = serverURLState
        self.api = api
    }

    func hasServerConfigured() -> Bool {
        serverURLState.currentURL != nil || secureStore.loadPersistedServerURL() != nil
    }

    func getServerURL() -> URL? {
        serverURLState.currentURL ?? secureStore.loadPersistedServerURL()
    }

    func serverURL() -> String? {
        getServerURL()?.absoluteString
    }

    struct ProbeResult {
        let serverURL: String
        let versionCheck: VersionCheckResult
        let backendVersion: String?
    }

    func saveServerURL(rawURL: String) async throws -> MobileProbeResponse {
        let normalizedURL = try normalize(rawURL: rawURL)
        serverURLState.currentURL = normalizedURL
        let probeURL = normalizedURL.appending(path: "api/mobile/probe")
        return try await api.probeServer(url: probeURL)
    }

    func saveServerURL(_ rawURL: String) async throws -> String {
        let response = try await saveServerURL(rawURL: rawURL)
        guard response.service.compare("tday", options: .caseInsensitive) == .orderedSame,
              response.version == "1" else {
            throw ServerProbeError.notTdayServer
        }
        return getServerURL()?.absoluteString ?? rawURL
    }

    func probeAndSave(_ rawURL: String) async throws -> ProbeResult {
        let response = try await saveServerURL(rawURL: rawURL)
        guard response.service.compare("tday", options: .caseInsensitive) == .orderedSame,
              response.version == "1" else {
            throw ServerProbeError.notTdayServer
        }
        let compatibility = response.encryptedCompatibility.flatMap { ProbeDecryptor.decrypt($0) }
        let versionCheck = checkVersionCompatibility(payload: compatibility)
        return ProbeResult(
            serverURL: getServerURL()?.absoluteString ?? rawURL,
            versionCheck: versionCheck,
            backendVersion: compatibility?.appVersion
        )
    }

    func recheckVersion() async -> VersionCheckResult {
        guard let url = getServerURL() else { return .compatible }
        let probeURL = url.appending(path: "api/mobile/probe")
        guard let response = try? await api.probeServer(url: probeURL) else { return .compatible }
        let compatibility = response.encryptedCompatibility.flatMap { ProbeDecryptor.decrypt($0) }
        return checkVersionCompatibility(payload: compatibility)
    }

    func resetTrustedServer(rawURL: String) async throws -> MobileProbeResponse {
        let normalizedURL = try normalize(rawURL: rawURL)
        if let host = normalizedURL.host {
            secureStore.clearTrustedFingerprint(for: host)
        }
        serverURLState.currentURL = normalizedURL
        return try await api.probeServer(url: normalizedURL.appending(path: "api/mobile/probe"))
    }

    func resetTrustedServer(_ rawURL: String) {
        Task {
            _ = try? await resetTrustedServer(rawURL: rawURL)
        }
    }

    func persistRuntimeServerURL() {
        guard let url = serverURLState.currentURL else {
            return
        }
        secureStore.savePersistedServerURL(url)
    }

    func clearServerConfiguration() {
        serverURLState.currentURL = nil
        secureStore.clearPersistedServerURL()
        secureStore.clearAllTrustedFingerprints()
    }

    func buildAbsoluteAppURL(_ path: String) -> URL? {
        getServerURL()?.appending(path: path.trimmingCharacters(in: CharacterSet(charactersIn: "/")))
    }

    private func normalize(rawURL: String) throws -> URL {
        let trimmed = rawURL.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            throw APIError(message: "Server URL is required", statusCode: nil)
        }

        let candidate = trimmed.contains("://") ? trimmed : "https://\(trimmed)"
        guard var components = URLComponents(string: candidate), let host = components.host?.lowercased() else {
            throw ServerProbeError.invalidURL
        }

        let scheme = components.scheme?.lowercased() ?? "https"
        guard scheme == "https" || scheme == "http" else {
            throw ServerProbeError.invalidURL
        }
        if scheme == "http" && !isLocalAddress(host) {
            throw ServerProbeError.insecureTransport
        }

        components.scheme = scheme
        components.path = components.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        components.query = nil
        components.fragment = nil

        guard let resolvedURL = components.url else {
            throw ServerProbeError.invalidURL
        }
        return resolvedURL
    }

    private func isLocalAddress(_ host: String) -> Bool {
        host == "localhost" ||
        host == "127.0.0.1" ||
        host == "10.0.2.2" ||
        host.hasPrefix("192.168.") ||
        host.hasPrefix("10.") ||
        host.hasSuffix(".local")
    }
}
