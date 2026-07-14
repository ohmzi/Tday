import XCTest
@testable import Tday

/// Covers the app side of the share-extension handoff: FIFO drain, cleanup of
/// the app-group key, and decoding of the exact payload shape the extension's
/// ShareViewController writes.
final class PendingShareStoreTests: XCTestCase {
    private var store: UserDefaults!

    override func setUp() {
        super.setUp()
        store = UserDefaults(suiteName: PendingShareStore.appGroupSuiteName) ?? .standard
        store.removeObject(forKey: PendingShareStore.queueKey)
    }

    override func tearDown() {
        store.removeObject(forKey: PendingShareStore.queueKey)
        store = nil
        super.tearDown()
    }

    func testDrainNextPopsOldestThenClearsTheKey() throws {
        let queue = [
            PendingShare(title: "First", notes: "https://example.com"),
            PendingShare(title: "Second", notes: nil)
        ]
        store.set(try JSONEncoder().encode(queue), forKey: PendingShareStore.queueKey)

        XCTAssertEqual(PendingShareStore.drainNext(), queue[0])
        XCTAssertEqual(PendingShareStore.drainNext(), queue[1])
        XCTAssertNil(PendingShareStore.drainNext())
        XCTAssertNil(store.data(forKey: PendingShareStore.queueKey))
    }

    func testDrainNextIgnoresGarbageData() {
        store.set(Data("not json".utf8), forKey: PendingShareStore.queueKey)
        XCTAssertNil(PendingShareStore.drainNext())
    }

    func testExtensionShapedPayloadDecodes() {
        // Byte-for-byte what ShareViewController enqueues.
        let json = #"[{"title":"Read the guide","notes":"https://example.com/guide"}]"#
        store.set(Data(json.utf8), forKey: PendingShareStore.queueKey)
        XCTAssertEqual(
            PendingShareStore.drainNext(),
            PendingShare(title: "Read the guide", notes: "https://example.com/guide")
        )
    }
}
