import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

final class ServerURLPersistenceTests: XCTestCase {
    private var secureStore: SecureStore!
    private var defaults: UserDefaults!
    private var defaultsSuiteName: String!

    override func setUp() {
        super.setUp()
        defaultsSuiteName = "com.ohmz.tday.tests.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: defaultsSuiteName)!
        defaults.removePersistentDomain(forName: defaultsSuiteName)
        secureStore = SecureStore(
            service: "com.ohmz.tday.tests.secure-store.\(UUID().uuidString)",
            defaults: defaults
        )
    }

    override func tearDown() {
        secureStore.clearAllUserValues()
        defaults.removePersistentDomain(forName: defaultsSuiteName)
        secureStore = nil
        defaults = nil
        defaultsSuiteName = nil
        super.tearDown()
    }

    func testPersistedServerURLUsesKeychainInsteadOfDefaults() {
        let url = URL(string: "https://tday.ohmz.cloud")!

        secureStore.savePersistedServerURL(url)

        XCTAssertEqual(secureStore.loadPersistedServerURL(), url)
        XCTAssertNil(defaults.string(forKey: SecureStore.Key.persistedServerURL.rawValue))
    }

    func testUserCleanupCanPreservePersistedServerURL() {
        let url = URL(string: "https://tday.ohmz.cloud")!
        secureStore.savePersistedServerURL(url)
        secureStore.saveLastEmail("user@example.com")

        secureStore.clearAllUserValues(preservingServerURL: true)

        XCTAssertEqual(secureStore.loadPersistedServerURL(), url)
        XCTAssertNil(secureStore.loadLastEmail())
    }

    func testReinstallCookieCleanupPreservesServerURLAndLastEmail() {
        let url = URL(string: "https://tday.ohmz.cloud")!
        secureStore.savePersistedServerURL(url)
        secureStore.saveLastEmail("user@example.com")
        secureStore.savePersistedAuthSessionCookieData(Data("cookie".utf8))

        secureStore.clearPersistedAuthSessionCookieIfAppReinstalled()

        XCTAssertEqual(secureStore.loadPersistedServerURL(), url)
        XCTAssertEqual(secureStore.loadLastEmail(), "user@example.com")
        XCTAssertNil(secureStore.loadPersistedAuthSessionCookieData())
    }

    func testReinstallCookieCleanupRunsOnlyOncePerInstall() {
        let url = URL(string: "https://tday.ohmz.cloud")!

        secureStore.clearPersistedAuthSessionCookieIfAppReinstalled()
        secureStore.savePersistedServerURL(url)
        secureStore.saveLastEmail("user@example.com")
        secureStore.savePersistedAuthSessionCookieData(Data("cookie".utf8))

        secureStore.clearPersistedAuthSessionCookieIfAppReinstalled()

        XCTAssertEqual(secureStore.loadPersistedServerURL(), url)
        XCTAssertEqual(secureStore.loadLastEmail(), "user@example.com")
        XCTAssertEqual(secureStore.loadPersistedAuthSessionCookieData(), Data("cookie".utf8))
    }
}
