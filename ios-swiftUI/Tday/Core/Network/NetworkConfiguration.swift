import CryptoKit
import Foundation
import Security

final class NetworkConfiguration: NSObject, URLSessionDelegate {
    private let secureStore: SecureStore
    private let serverURLState: ServerURLState
    private let cookieStore: CookieStore

    // The TLS pinning challenge can only be cancelled, not failed with a typed
    // error — so we record the host whose pinned fingerprint mismatched here and
    // let the probe layer translate the resulting URLError.cancelled into a clear
    // "certificate changed" error (which surfaces the "Reset saved server trust"
    // affordance). Accessed from the URLSession delegate queue and async callers.
    private let trustFailureLock = NSLock()
    private var trustFailureHosts: Set<String> = []

    lazy var session: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.httpCookieStorage = HTTPCookieStorage.shared
        configuration.httpShouldSetCookies = true
        configuration.requestCachePolicy = .reloadIgnoringLocalAndRemoteCacheData
        configuration.urlCache = nil
        configuration.waitsForConnectivity = false
        configuration.timeoutIntervalForRequest = 30
        configuration.timeoutIntervalForResource = 60
        return URLSession(configuration: configuration, delegate: self, delegateQueue: nil)
    }()

    lazy var probeSession: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.httpCookieStorage = HTTPCookieStorage.shared
        configuration.httpShouldSetCookies = true
        configuration.requestCachePolicy = .reloadIgnoringLocalAndRemoteCacheData
        configuration.urlCache = nil
        configuration.waitsForConnectivity = false
        configuration.timeoutIntervalForRequest = 5
        configuration.timeoutIntervalForResource = 8
        return URLSession(configuration: configuration, delegate: self, delegateQueue: nil)
    }()

    init(secureStore: SecureStore, serverURLState: ServerURLState, cookieStore: CookieStore) {
        self.secureStore = secureStore
        self.serverURLState = serverURLState
        self.cookieStore = cookieStore
    }

    func currentBaseURL() throws -> URL {
        if let url = serverURLState.currentURL {
            return url
        }
        if let url = secureStore.loadPersistedServerURL() {
            return url
        }
        throw APIError(message: "Server URL is not configured", statusCode: nil)
    }

    func makeURL(path: String, allowRewrite: Bool = true) throws -> URL {
        if let absolute = URL(string: path), absolute.scheme != nil {
            return absolute
        }

        let baseURL = try currentBaseURL()
        if !allowRewrite {
            return baseURL.appending(path: path.trimmingCharacters(in: CharacterSet(charactersIn: "/")))
        }

        return baseURL.appending(path: path.trimmingCharacters(in: CharacterSet(charactersIn: "/")))
    }

    func defaultHeaders(extraHeaders: [String: String] = [:], allowRewrite: Bool = true) -> [String: String] {
        var headers: [String: String] = [
            "Accept": "application/json",
            "Cache-Control": "no-store",
            "Pragma": "no-cache",
            "X-User-Timezone": TimeZone.current.identifier,
            "X-Tday-Client": "ios",
            "X-Tday-App-Version": appVersion,
            "X-Tday-Device-Id": secureStore.loadOrCreateDeviceID(),
        ]
        if !allowRewrite {
            headers["X-Tday-No-Rewrite"] = "1"
        }
        extraHeaders.forEach { headers[$0.key] = $0.value }
        return headers
    }

    var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "unknown"
    }

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let trust = challenge.protectionSpace.serverTrust
        else {
            completionHandler(.performDefaultHandling, nil)
            return
        }

        let host = challenge.protectionSpace.host.lowercased()
        if isLocalAddress(host: host) {
            completionHandler(.performDefaultHandling, nil)
            return
        }

        // Certificates that already validate against the system (public CA) trust
        // store use standard CA validation — no pinning. This is the common case and
        // means routine renewals (e.g. Let's Encrypt rotating to a new key) never
        // false-trip. Any previously stored TOFU pin for this host is cleared so the
        // app doesn't keep enforcing a now-irrelevant fingerprint.
        if isSystemTrusted(trust, host: host) {
            if secureStore.trustedFingerprint(for: host) != nil {
                secureStore.clearTrustedFingerprint(for: host)
            }
            completionHandler(.performDefaultHandling, nil)
            return
        }

        // Otherwise the certificate is self-signed / privately issued: the system
        // can't vouch for it, so we trust it on first use, pin its key, and enforce
        // that pin thereafter to guard against MITM on self-hosted servers.
        let fingerprint = fingerprintForTrust(trust)
        if let stored = secureStore.trustedFingerprint(for: host), let fingerprint, stored != fingerprint {
            recordTrustFailure(host: host)
            completionHandler(.cancelAuthenticationChallenge, nil)
            return
        }
        if let fingerprint, secureStore.trustedFingerprint(for: host) == nil {
            secureStore.saveTrustedFingerprint(fingerprint, for: host)
        }

        completionHandler(.useCredential, URLCredential(trust: trust))
    }

    private func recordTrustFailure(host: String) {
        trustFailureLock.lock()
        defer { trustFailureLock.unlock() }
        trustFailureHosts.insert(host.lowercased())
    }

    /// Returns true (and clears the record) if the most recent connection to `host`
    /// was cancelled because its pinned certificate fingerprint no longer matches.
    func consumeTrustFailure(host: String) -> Bool {
        trustFailureLock.lock()
        defer { trustFailureLock.unlock() }
        return trustFailureHosts.remove(host.lowercased()) != nil
    }

    func clearCookies() {
        cookieStore.clearAll()
    }

    func syncPersistedAuthCookie() {
        cookieStore.syncPersistedAuthCookie()
    }

    func isSecureTransportRequired(for url: URL) -> Bool {
        guard let host = url.host?.lowercased() else {
            return true
        }
        return !isLocalAddress(host: host)
    }

    /// True if the server trust chains up to a system-trusted (public CA) anchor and
    /// passes hostname validation. Such certificates are left to standard CA
    /// validation rather than TOFU pinning.
    private func isSystemTrusted(_ trust: SecTrust, host: String) -> Bool {
        let policy = SecPolicyCreateSSL(true, host as CFString)
        SecTrustSetPolicies(trust, policy)
        return SecTrustEvaluateWithError(trust, nil)
    }

    private func isLocalAddress(host: String) -> Bool {
        host == "localhost" ||
        host == "127.0.0.1" ||
        host == "10.0.2.2" ||
        host.hasPrefix("192.168.") ||
        host.hasPrefix("10.") ||
        host.hasSuffix(".local")
    }

    private func fingerprintForTrust(_ trust: SecTrust) -> String? {
        guard let key = SecTrustCopyKey(trust) else {
            return leafCertificateHash(for: trust)
        }

        var error: Unmanaged<CFError>?
        guard let external = SecKeyCopyExternalRepresentation(key, &error) as Data? else {
            return leafCertificateHash(for: trust)
        }
        let digest = SHA256.hash(data: external)
        return Data(digest).base64EncodedString()
    }

    private func leafCertificateHash(for trust: SecTrust) -> String? {
        guard let certificates = SecTrustCopyCertificateChain(trust) as? [SecCertificate],
              let certificate = certificates.first
        else {
            return nil
        }
        let data = SecCertificateCopyData(certificate) as Data
        let digest = SHA256.hash(data: data)
        return Data(digest).base64EncodedString()
    }
}
