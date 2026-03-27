import CryptoKit
import Foundation
import Security

final class NetworkConfiguration: NSObject, URLSessionDelegate {
    private let secureStore: SecureStore
    private let serverURLState: ServerURLState
    private let cookieStore: CookieStore

    lazy var session: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.httpCookieStorage = HTTPCookieStorage.shared
        configuration.httpShouldSetCookies = true
        configuration.waitsForConnectivity = true
        configuration.timeoutIntervalForRequest = 30
        configuration.timeoutIntervalForResource = 60
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
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.6.0"
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

        let fingerprint = fingerprintForTrust(trust)
        if let stored = secureStore.trustedFingerprint(for: host), let fingerprint, stored != fingerprint {
            completionHandler(.cancelAuthenticationChallenge, nil)
            return
        }
        if let fingerprint, secureStore.trustedFingerprint(for: host) == nil {
            secureStore.saveTrustedFingerprint(fingerprint, for: host)
        }

        completionHandler(.useCredential, URLCredential(trust: trust))
    }

    func clearCookies() {
        cookieStore.clearAll()
    }

    func isSecureTransportRequired(for url: URL) -> Bool {
        guard let host = url.host?.lowercased() else {
            return true
        }
        return !isLocalAddress(host: host)
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
        guard let certificate = SecTrustGetCertificateAtIndex(trust, 0) else {
            return nil
        }
        let data = SecCertificateCopyData(certificate) as Data
        let digest = SHA256.hash(data: data)
        return Data(digest).base64EncodedString()
    }
}
