import Foundation

final class CookieStore {
    private let storage: HTTPCookieStorage

    init(storage: HTTPCookieStorage = .shared) {
        self.storage = storage
        storage.cookieAcceptPolicy = .always
    }

    func allCookies() -> [HTTPCookie] {
        storage.cookies ?? []
    }

    func clearAll() {
        for cookie in storage.cookies ?? [] {
            storage.deleteCookie(cookie)
        }
    }
}
