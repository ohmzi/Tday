import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// `AsyncLock` is the mutex that serialises every load / transform / save the sync
/// path performs (`OfflineCacheManager.withSyncLock`), so two callers can never
/// interleave and write a half-merged cache. It used to wait with
/// `while locked { await Task.yield() }`, which never really suspends; it now parks
/// each waiter on a continuation and hands ownership straight to the next one.
///
/// Two of the four tests below pin behaviour the continuation version introduces;
/// the other two characterise behaviour the spin loop already had, and are here so a
/// future rewrite cannot quietly lose it. Worth being precise about which is which:
///
/// NEW with this rewrite — a spin loop cannot satisfy these:
///  - waiters are served in FIFO order, so a queued sync cannot be starved.
///    `testWaitersAreHandedTheLockInFIFOOrder` also depends on `waiterCount`
///    reaching 1, 2, 3… as each caller parks, which a spin loop never does: it
///    enqueues nothing, so that count would stay 0 and the test would hang rather
///    than fail — and it does not compile against the spin version at all, because
///    `waiterCount` does not exist there;
///  - ownership passes straight from the releasing holder to the next waiter with
///    `locked` never cleared. Clearing the flag and *then* resuming would let a
///    brand-new caller slip in alongside the waiter that was just handed ownership,
///    which is what the 32-caller exclusion test would catch.
///
/// PRESERVED from the spin loop — these passed before and must keep passing:
///  - only one caller is ever inside the lock (the spin's `while locked` test and
///    its `locked = true` ran in one uninterrupted actor job, so it was mutually
///    exclusive too);
///  - a throwing operation still releases and still hands the lock on, because every
///    real call site is `try await` (the spin version released in the same `defer`).
final class AsyncLockTests: XCTestCase {
    func testWaitersAreHandedTheLockInFIFOOrder() async {
        let lock = AsyncLock()
        let log = OrderLog()
        let holderHasLock = Flag()
        let holderMayRelease = Flag()

        // One holder takes the uncontended lock and keeps it until every waiter
        // below has parked behind it.
        let holder = Task {
            await lock.withLock { () async -> Void in
                await holderHasLock.set()
                await waitUntil("the test to release the holder") {
                    await holderMayRelease.isSet
                }
            }
        }
        await waitUntil("the holder to take the lock") { await holderHasLock.isSet }

        // Enqueue the waiters one at a time, each only after the previous one is
        // confirmed parked. That is what makes the expected order a fact about the
        // lock rather than a guess about how the scheduler started five tasks.
        var waiters: [Task<Void, Never>] = []
        for id in 1 ... 5 {
            waiters.append(Task {
                await lock.withLock { () async -> Void in
                    await log.record(id)
                }
            })
            await waitUntil("waiter \(id) to park on the lock") {
                await lock.waiterCount == id
            }
        }

        await holderMayRelease.set()
        await holder.value
        for waiter in waiters {
            await waiter.value
        }

        let order = await log.entries
        XCTAssertEqual(order, [1, 2, 3, 4, 5])
        let leftParked = await lock.waiterCount
        XCTAssertEqual(leftParked, 0, "every waiter should have been resumed exactly once")
    }

    func testOnlyOneCallerIsInsideTheLockAtATime() async {
        let lock = AsyncLock()
        let counter = ExclusionCounter()
        let callerCount = 32

        await withTaskGroup(of: Void.self) { group in
            for _ in 0 ..< callerCount {
                group.addTask {
                    await lock.withLock { () async -> Void in
                        await counter.enter()
                        // Suspend INSIDE the critical section. This is the window a
                        // broken hand-off opens: a caller arriving here while the
                        // lock flag is momentarily clear would be counted as a
                        // second occupant.
                        await Task.yield()
                        await counter.leave()
                    }
                }
            }
        }

        let result = await counter.result
        XCTAssertEqual(result.maxConcurrent, 1, "two callers were inside the lock at once")
        XCTAssertEqual(result.completed, callerCount, "every caller should have been let through")
    }

    func testAThrowingOperationStillReleasesTheLock() async {
        let lock = AsyncLock()

        do {
            try await lock.withLock { () async throws -> Void in
                throw SampleFailure.boom
            }
            XCTFail("withLock should rethrow the operation's error")
        } catch SampleFailure.boom {
            // Expected: `withLock` is `rethrows` and every real call site is `try await`.
        } catch {
            XCTFail("unexpected error: \(error)")
        }

        // Had the throw leaked the lock, this second acquire would park forever.
        let parkedBefore = await lock.waiterCount
        XCTAssertEqual(parkedBefore, 0)
        let value = await lock.withLock { () async -> Int in 42 }
        XCTAssertEqual(value, 42)
    }

    func testAThrowingHolderHandsTheLockToItsWaiter() async {
        let lock = AsyncLock()
        let holderHasLock = Flag()
        let holderMayRelease = Flag()
        let waiterFinished = Flag()

        let holder = Task { () async -> Bool in
            do {
                try await lock.withLock { () async throws -> Void in
                    await holderHasLock.set()
                    await waitUntil("the test to release the throwing holder") {
                        await holderMayRelease.isSet
                    }
                    throw SampleFailure.boom
                }
                return false
            } catch {
                return true
            }
        }
        await waitUntil("the throwing holder to take the lock") { await holderHasLock.isSet }

        let waiter = Task {
            await lock.withLock { () async -> Void in
                await waiterFinished.set()
            }
        }
        await waitUntil("the waiter to park behind the throwing holder") {
            await lock.waiterCount == 1
        }

        await holderMayRelease.set()
        let didThrow = await holder.value
        await waiter.value

        XCTAssertTrue(didThrow, "the operation's error should have propagated out of withLock")
        let waiterRan = await waiterFinished.isSet
        XCTAssertTrue(waiterRan, "the waiter should have been handed the lock by the failing holder")
    }
}

private enum SampleFailure: Error {
    case boom
}

/// Records the order in which callers were let into the lock.
private actor OrderLog {
    private(set) var entries: [Int] = []

    func record(_ id: Int) {
        entries.append(id)
    }
}

/// Counts how many callers are inside the lock at the same moment. With mutual
/// exclusion intact `maxConcurrent` can never exceed 1.
private actor ExclusionCounter {
    private var inside = 0
    private var highWaterMark = 0
    private var finished = 0

    func enter() {
        inside += 1
        highWaterMark = max(highWaterMark, inside)
    }

    func leave() {
        inside -= 1
        finished += 1
    }

    var result: (maxConcurrent: Int, completed: Int) {
        (highWaterMark, finished)
    }
}

/// A one-way latch two tasks can hand a signal across without a continuation of
/// their own.
private actor Flag {
    private(set) var isSet = false

    func set() {
        isSet = true
    }
}

/// Polls `condition` until it holds, or fails the test at `timeout`. Polling with a
/// sleep (rather than a fixed wait) keeps these tests both deterministic and quick:
/// they wait exactly as long as the scheduler needs, and report a stuck lock as a
/// named failure instead of hanging until XCTest's own timeout.
private func waitUntil(
    _ description: String,
    timeout: TimeInterval = 5,
    file: StaticString = #filePath,
    line: UInt = #line,
    condition: () async -> Bool
) async {
    let deadline = Date().addingTimeInterval(timeout)
    while Date() < deadline {
        if await condition() {
            return
        }
        try? await Task.sleep(nanoseconds: 1_000_000)
    }
    XCTFail("Timed out after \(timeout)s waiting for \(description).", file: file, line: line)
}
