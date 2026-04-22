import Foundation

final class CookieStore {
    private struct PersistedAuthCookie: Codable {
        let name: String
        let value: String
        let domain: String
        let path: String
        let expiresDate: Date?
        let isSecure: Bool
        let isHTTPOnly: Bool
        let originURL: String?
    }

    private let secureStore: SecureStore
    private let storage: HTTPCookieStorage
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private let authCookieNames: Set<String> = [
        "authjs.session-token",
        "__Secure-authjs.session-token",
    ]

    init(secureStore: SecureStore, storage: HTTPCookieStorage = .shared) {
        self.secureStore = secureStore
        self.storage = storage
        storage.cookieAcceptPolicy = .always
        secureStore.clearPersistedAuthSessionCookieIfAppReinstalled()
        if currentAuthCookie() == nil {
            restorePersistedAuthCookie()
        }
        syncPersistedAuthCookie()
    }

    func allCookies() -> [HTTPCookie] {
        storage.cookies ?? []
    }

    func syncPersistedAuthCookie() {
        removeExpiredAuthCookies()
        guard let cookie = currentAuthCookie() else {
            clearPersistedAuthCookie()
            return
        }
        persist(cookie)
    }

    func clearAll() {
        for cookie in storage.cookies ?? [] {
            storage.deleteCookie(cookie)
        }
        clearPersistedAuthCookie()
    }

    func clearAuthCookies() {
        for cookie in authCookies() {
            storage.deleteCookie(cookie)
        }
        clearPersistedAuthCookie()
    }

    private func restorePersistedAuthCookie() {
        guard let data = secureStore.loadPersistedAuthSessionCookieData() else {
            return
        }

        guard let persisted = try? decoder.decode(PersistedAuthCookie.self, from: data),
              let cookie = makeCookie(from: persisted)
        else {
            clearPersistedAuthCookie()
            return
        }

        guard !isExpired(cookie) else {
            clearPersistedAuthCookie()
            return
        }

        storage.setCookie(cookie)
    }

    private func persist(_ cookie: HTTPCookie) {
        let persisted = PersistedAuthCookie(
            name: cookie.name,
            value: cookie.value,
            domain: cookie.domain,
            path: cookie.path,
            expiresDate: cookie.expiresDate,
            isSecure: cookie.isSecure,
            isHTTPOnly: cookie.isHTTPOnly,
            originURL: (cookie.properties?[.originURL] as? URL)?.absoluteString ??
                (cookie.properties?[.originURL] as? String)
        )

        guard let data = try? encoder.encode(persisted) else {
            return
        }
        secureStore.savePersistedAuthSessionCookieData(data)
    }

    private func makeCookie(from persisted: PersistedAuthCookie) -> HTTPCookie? {
        var properties: [HTTPCookiePropertyKey: Any] = [
            .name: persisted.name,
            .value: persisted.value,
            .domain: persisted.domain,
            .path: persisted.path,
            .version: 0,
        ]

        if let expiresDate = persisted.expiresDate {
            properties[.expires] = expiresDate
        }
        if persisted.isSecure {
            properties[.secure] = "TRUE"
        }
        if persisted.isHTTPOnly {
            properties[HTTPCookiePropertyKey("HttpOnly")] = "TRUE"
        }
        if let originURL = persisted.originURL, let url = URL(string: originURL) {
            properties[.originURL] = url
        }

        return HTTPCookie(properties: properties)
    }

    private func removeExpiredAuthCookies() {
        for cookie in authCookies() where isExpired(cookie) {
            storage.deleteCookie(cookie)
        }
    }

    private func currentAuthCookie() -> HTTPCookie? {
        authCookies()
            .sorted { lhs, rhs in
                authCookiePriority(lhs) > authCookiePriority(rhs)
            }
            .first
    }

    private func authCookiePriority(_ cookie: HTTPCookie) -> Int {
        var priority = 0
        if !isExpired(cookie) {
            priority += 2
        }
        if cookie.name.hasPrefix("__Secure-") {
            priority += 1
        }
        return priority
    }

    private func authCookies() -> [HTTPCookie] {
        allCookies().filter { authCookieNames.contains($0.name) }
    }

    private func isExpired(_ cookie: HTTPCookie) -> Bool {
        guard let expiresDate = cookie.expiresDate else {
            return false
        }
        return expiresDate <= Date()
    }

    private func clearPersistedAuthCookie() {
        secureStore.clearPersistedAuthSessionCookie()
    }
}
