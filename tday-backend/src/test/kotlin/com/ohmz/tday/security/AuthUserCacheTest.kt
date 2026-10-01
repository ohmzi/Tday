package com.ohmz.tday.security

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

private const val USER_ID = "u1"
private const val GHOST_USER_ID = "ghost"

class AuthUserCacheTest {
    private fun user(tokenVersion: Int = 1) = AuthCachedUser(
        role = "USER",
        approvalStatus = "APPROVED",
        tokenVersion = tokenVersion,
        timeZone = "UTC",
    )

    @Test
    fun `concurrent misses for one user share a single load`() = runBlocking {
        val cache = AuthUserCache()
        val loads = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val loaded = user()

        val callers = (1..8).map {
            async {
                cache.getOrLoad(USER_ID) {
                    loads.incrementAndGet()
                    gate.await()
                    loaded
                }
            }
        }
        repeat(20) { yield() }
        gate.complete(Unit)

        val results = callers.awaitAll()
        assertEquals(1, loads.get())
        results.forEach { assertSame(loaded, it) }
        // The shared load populated the cache, so the next caller never loads.
        assertSame(loaded, cache.getOrLoad(USER_ID) { error("must be served from the cache") })
    }

    @Test
    fun `different users load independently`() = runBlocking {
        val cache = AuthUserCache()
        val loads = AtomicInteger()
        val a = cache.getOrLoad("a") { loads.incrementAndGet(); user(1) }
        val b = cache.getOrLoad("b") { loads.incrementAndGet(); user(2) }
        assertEquals(2, loads.get())
        assertEquals(1, a?.tokenVersion)
        assertEquals(2, b?.tokenVersion)
    }

    @Test
    fun `a missing user is not cached`() = runBlocking {
        val cache = AuthUserCache()
        val loads = AtomicInteger()
        assertNull(cache.getOrLoad(GHOST_USER_ID) { loads.incrementAndGet(); null })
        assertNull(cache.getOrLoad(GHOST_USER_ID) { loads.incrementAndGet(); null })
        assertEquals(2, loads.get())
        assertNull(cache.get(GHOST_USER_ID))
    }

    @Test
    fun `a load invalidated while running answers its waiters but does not repopulate the cache`() = runBlocking {
        val cache = AuthUserCache()
        val gate = CompletableDeferred<Unit>()
        val stale = user(tokenVersion = 1)

        val waiting = async { cache.getOrLoad(USER_ID) { gate.await(); stale } }
        repeat(20) { yield() }

        cache.invalidate(USER_ID)
        gate.complete(Unit)

        assertSame(stale, waiting.await())
        assertNull(cache.get(USER_ID), "a row read before the invalidation must not be cached")

        // And a caller arriving after the invalidation reads fresh instead of joining the old load.
        val fresh = user(tokenVersion = 2)
        assertSame(fresh, cache.getOrLoad(USER_ID) { fresh })
    }

    @Test
    fun `a failed load reaches every waiter and the next call retries`() = runBlocking {
        val cache = AuthUserCache()
        val loads = AtomicInteger()
        val gate = CompletableDeferred<Unit>()

        val callers = (1..3).map {
            async {
                runCatching {
                    cache.getOrLoad(USER_ID) {
                        loads.incrementAndGet()
                        gate.await()
                        error("database unavailable")
                    }
                }
            }
        }
        repeat(20) { yield() }
        gate.complete(Unit)

        callers.awaitAll().forEach {
            assertFailsWith<IllegalStateException> { it.getOrThrow() }
        }
        assertEquals(1, loads.get())

        val retryLoads = AtomicInteger()
        val recovered = cache.getOrLoad(USER_ID) { retryLoads.incrementAndGet(); user() }
        assertNotNull(recovered)
        assertEquals(1, retryLoads.get(), "the failed load must not be reused")
    }
}
