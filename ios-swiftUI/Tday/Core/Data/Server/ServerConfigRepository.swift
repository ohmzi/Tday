import Foundation

enum ServerProbeError: Error, Equatable, LocalizedError {
    case invalidURL
    case insecureTransport
    case notTdayServer
    case certificateChanged
    /// The server presented a certificate the system does not trust and that we have never pinned.
    /// Carries the fingerprint so the UI can show it and ask the user to confirm. Only ever raised
    /// for private/LAN hosts — see `untrustedPublicCertificate`.
    case untrustedCertificate(host: String, fingerprint: String?)
    /// A PUBLIC host presented a certificate the system cannot verify. Carries no fingerprint on
    /// purpose: there is nothing for the user to approve, because approving it is the attack.
    case untrustedPublicCertificate(host: String)

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
        case .untrustedCertificate:
            return "This server's certificate is not trusted"
        case .untrustedPublicCertificate:
            return "This server's certificate could not be verified"
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

    func appDataMode() -> AppDataMode {
        secureStore.appDataMode()
    }

    func isLocalMode() -> Bool {
        secureStore.isLocalMode()
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

    struct VersionRecheckResult {
        let versionCheck: VersionCheckResult
        let backendVersion: String?
        /// True when the probe was refused by the TLS trust check rather than merely failing.
        /// Without this the caller cannot tell "server unreachable" from "someone may be
        /// intercepting this connection", and both would report `.compatible`.
        var trustFailed: Bool = false
    }

    func saveServerURL(rawURL: String) async throws -> MobileProbeResponse {
        let result = try await probe(rawURL: rawURL)
        let response = result.response
        guard response.service.compare("tday", options: .caseInsensitive) == .orderedSame,
              response.version == "1" else {
            throw ServerProbeError.notTdayServer
        }
        serverURLState.currentURL = result.serverURL
        persistRuntimeServerURL()
        return response
    }

    func saveServerURL(_ rawURL: String) async throws -> String {
        _ = try await saveServerURL(rawURL: rawURL)
        return getServerURL()?.absoluteString ?? rawURL
    }

    func probeAndSave(_ rawURL: String) async throws -> ProbeResult {
        let result = try await probe(rawURL: rawURL)
        let response = result.response
        guard response.service.compare("tday", options: .caseInsensitive) == .orderedSame,
              response.version == "1" else {
            throw ServerProbeError.notTdayServer
        }
        serverURLState.currentURL = result.serverURL
        persistRuntimeServerURL()
        let compatibility = response.encryptedCompatibility.flatMap { ProbeDecryptor.decrypt($0) }
        let versionCheck = checkVersionCompatibility(payload: compatibility)
        return ProbeResult(
            serverURL: result.serverURL.absoluteString,
            versionCheck: versionCheck,
            backendVersion: compatibility?.appVersion ?? response.appVersion
        )
    }

    func recheckVersion() async -> VersionRecheckResult {
        guard let url = getServerURL() else {
            return VersionRecheckResult(versionCheck: .compatible, backendVersion: nil)
        }
        let probeURL = url.appending(path: "api/mobile/probe")
        guard let response = try? await api.probeServer(url: probeURL) else {
            // Now that the trust check fails closed, a refused certificate reaches this path as a
            // bare cancellation. Reporting ".compatible" here would silently mask exactly the
            // interception the fail-closed change exists to catch.
            let trustFailed = url.host.map { host in
                api.consumeTrustFailure(forHost: host) ||
                    api.consumePublicTrustRefusal(forHost: host) ||
                    api.consumeTrustUnknown(forHost: host) != nil
            } ?? false
            return VersionRecheckResult(versionCheck: .compatible, backendVersion: nil, trustFailed: trustFailed)
        }
        let compatibility = response.encryptedCompatibility.flatMap { ProbeDecryptor.decrypt($0) }
        return VersionRecheckResult(
            versionCheck: checkVersionCompatibility(payload: compatibility),
            backendVersion: compatibility?.appVersion ?? response.appVersion
        )
    }

    /// Forgets the stored pin and re-probes.
    ///
    /// Dropping the pin does not mean "trust whatever answers next": the probe goes through the
    /// same fail-closed path, so an unrecognised certificate comes back as
    /// [ServerProbeError.untrustedCertificate] and the user still has to confirm the fingerprint.
    func resetTrustedServer(rawURL: String) async throws -> MobileProbeResponse {
        let normalizedURL = try normalize(rawURL: rawURL)
        if let host = normalizedURL.host {
            secureStore.clearTrustedFingerprint(for: host)
        }
        let (serverURL, response) = try await probe(rawURL: rawURL)
        guard response.service.compare("tday", options: .caseInsensitive) == .orderedSame,
              response.version == "1" else {
            throw ServerProbeError.notTdayServer
        }
        serverURLState.currentURL = serverURL
        persistRuntimeServerURL()
        return response
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
        secureStore.clearAppDataMode()
        secureStore.clearAllTrustedFingerprints()
    }

    func enableLocalMode() {
        serverURLState.currentURL = nil
        secureStore.clearPersistedServerURL()
        secureStore.clearCachedSessionUser()
        secureStore.clearLastUsername()
        secureStore.clearPersistedAuthSessionCookie()
        secureStore.setAppDataMode(.local)
    }

    /// The exact inverse of [enableLocalMode], and deliberately nothing more.
    ///
    /// Leaving a local workspace is a MODE SWITCH, not a teardown — web says so in as many
    /// words (`AuthProvider.tsx`: "Leaving the local workspace is a mode switch, not a session
    /// teardown"). The rows stay on the device so choosing Local Mode again finds the
    /// workspace where it was left. Wiping is the separate, confirmed row next to it; the
    /// marker is what stops `bootstrap()` folding the two together on the next launch.
    func leaveLocalMode() {
        secureStore.clearCachedSessionUser()
        secureStore.setAppDataMode(.unset)
        secureStore.setRetainedLocalWorkspace(true)
    }

    /// A local workspace that was left rather than deleted is still on disk.
    var hasRetainedLocalWorkspace: Bool {
        secureStore.hasRetainedLocalWorkspace()
    }

    func clearRetainedLocalWorkspace() {
        secureStore.setRetainedLocalWorkspace(false)
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

    private func probe(rawURL: String) async throws -> (serverURL: URL, response: MobileProbeResponse) {
        let normalizedURL = try normalize(rawURL: rawURL)
        do {
            let response = try await api.probeServer(url: normalizedURL.appending(path: "api/mobile/probe"))
            return (normalizedURL, response)
        } catch {
            // The TLS pinning check cancels the request (URLError.cancelled) when the
            // server's certificate no longer matches the pinned fingerprint. Translate
            // that into a clear, actionable error instead of a bare "cancelled".
            if let host = normalizedURL.host, api.consumeTrustFailure(forHost: host) {
                throw ServerProbeError.certificateChanged
            }
            // Public host we could not verify: refused with no fingerprint to approve. Checked
            // before the enrollment path below so this can never fall through into a trust prompt.
            if let host = normalizedURL.host, api.consumePublicTrustRefusal(forHost: host) {
                throw ServerProbeError.untrustedPublicCertificate(host: host)
            }
            // Unrecognised certificate on a private/LAN host: refused rather than
            // trusted-on-first-use. Surface the fingerprint so the setup screen can ask the user to
            // confirm it explicitly. The outer `if let` only tests that a refusal was recorded —
            // the fingerprint itself may be nil.
            if let host = normalizedURL.host, let fingerprint = api.consumeTrustUnknown(forHost: host) {
                throw ServerProbeError.untrustedCertificate(host: host, fingerprint: fingerprint)
            }
            throw error
        }
    }

    /// Pins `fingerprint` for `host` and re-probes, after the user has confirmed it on screen.
    ///
    /// The approval names the exact fingerprint, so a certificate swapped between the prompt and
    /// this retry is still refused — this is a confirmation, not a blanket "trust anything next".
    func approveCertificate(rawURL: String, host: String, fingerprint: String) async throws -> MobileProbeResponse {
        api.allowTrustEnrollment(host: host, expecting: fingerprint)
        do {
            let (serverURL, response) = try await probe(rawURL: rawURL)
            guard response.service.compare("tday", options: .caseInsensitive) == .orderedSame,
                  response.version == "1" else {
                throw ServerProbeError.notTdayServer
            }
            serverURLState.currentURL = serverURL
            persistRuntimeServerURL()
            return response
        } catch {
            api.cancelTrustEnrollment(host: host)
            throw error
        }
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
