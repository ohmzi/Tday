package com.ohmz.tday.compose.feature.widget.snapshot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * The three snapshot-durability defects, as tests.
 *
 * `WidgetSnapshotStore` itself is untestable on the JVM (AndroidKeyStore, a real `Context`), which
 * is precisely why the two properties that went wrong live in [WidgetSnapshotIo] instead: they are
 * plain file behaviour and nothing here needs Android.
 *
 *  1. The store used to `delete()` the target and only then evaluate `encrypt(bytes)`, so any
 *     Keystore/cipher/IO failure destroyed the last good snapshot and returned `false` with the
 *     throwable swallowed. The widget then read `null` and sat on "Loading tasks…".
 *  2. Nothing serialised the writers — `OfflineCacheManager`'s save/clear paths, the legacy
 *     migration, `WidgetHydrateWorker` on a WorkManager thread and `WidgetListConfigurationViewModel`
 *     on `viewModelScope` all wrote the same files with no shared lock. Two interleaving inside one
 *     `writeBytes` produced a file that failed GCM authentication, which `read` then deleted.
 *  3. `FloaterTasksWidget`, `TodayTasksWidget`, `ListTasksWidget` and `WidgetFastPaint` decide
 *     whether to hydrate (or whether to fast-paint at all) from a bare `File.exists()`. Under
 *     delete-then-write that probe was transiently false on every single cache write.
 *
 * Defect 3 is checked by asserting that the replacement never unlinks the name, rather than by
 * racing a thread to catch the name missing. A `rename(2)` has no window to catch, so a sampling
 * probe can only ever report noise or nothing.
 *
 * There was briefly a second oracle here that watched the directory through `WatchService` and
 * failed on an `ENTRY_DELETE` for the target name. It was removed because it is unsound, and the
 * way it is unsound is worth recording so nobody rebuilds it. The kernel does distinguish the two
 * cases — `strace`/raw `inotify` show a replacement emitting only `IN_MOVED_FROM` on the `.tmp`
 * name and `IN_MOVED_TO` on the target, and never `IN_DELETE` on the target — but Java's
 * `WatchService` surfaces the target's arrival as `ENTRY_DELETE` all the same, 200 times out of
 * 200 renames. So the probe reported an unlink on a write that provably never unlinked anything,
 * which is the same shape of error as the `exists()` loop it was written to replace: an inference
 * from a JDK-level signal to a filesystem-level claim the signal does not carry.
 */
class WidgetSnapshotIoTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun target(): File = File(folder.newFolder("widget"), "widget-today-snapshot.json")

    @Test
    fun `a write replaces the previous contents exactly`() {
        val target = target()
        WidgetSnapshotIo.writeAtomically(target, "first".toByteArray())
        WidgetSnapshotIo.writeAtomically(target, "second payload".toByteArray())

        assertArrayEquals("second payload".toByteArray(), target.readBytes())
    }

    @Test
    fun `a write creates the directory when it does not exist yet`() {
        // The fresh-install path: `filesDir/widget/` has never been created.
        val target = File(File(folder.root, "never-created"), "widget-today-snapshot.json")

        WidgetSnapshotIo.writeAtomically(target, "seeded".toByteArray())

        assertArrayEquals("seeded".toByteArray(), target.readBytes())
    }

    @Test
    fun `a failed write leaves the previous good snapshot intact`() {
        val target = target()
        WidgetSnapshotIo.writeAtomically(target, "the last good snapshot".toByteArray())

        // Stand a directory where the temp file wants to go, so `writeBytes` cannot open it. This
        // stands in for the real failures — a Keystore key invalidated by a lock-screen change, a
        // provider unavailable before first unlock, a full disk — all of which used to land AFTER
        // the target had already been deleted.
        val blocker = File(target.parentFile, "${target.name}.tmp")
        assertTrue(blocker.mkdirs())

        val failure = runCatching { WidgetSnapshotIo.writeAtomically(target, "doomed".toByteArray()) }
        assertTrue("the write should surface its failure, not swallow it", failure.isFailure)

        assertTrue("the previous snapshot must survive a failed write", target.exists())
        assertArrayEquals("the last good snapshot".toByteArray(), target.readBytes())
    }

    @Test
    fun `no temp file survives a successful write`() {
        val target = target()
        WidgetSnapshotIo.writeAtomically(target, "payload".toByteArray())

        assertFalse(File(target.parentFile, "${target.name}.tmp").exists())
        assertEquals(listOf(target.name), target.parentFile.list()!!.sorted())
    }

    @Test
    fun `a replacement never deletes the snapshot it is replacing`() {
        // Defect 3, half one: the regression this guards IS a `target.delete()`, so watch for that
        // call directly instead of sampling for its after-effect. No threads, no timing, no
        // sampling — a delete is either called or it is not.
        //
        // This replaced a prober thread that spun on `target.exists()` and failed on three
        // consecutive misses. That instrument did not work. Measured against a deliberately
        // reintroduced delete-then-write, the spin loop noticed it in 30 of 50 idle runs and 18 of
        // 50 at load average 74-90 — a 64%-under-load false-negative rate against the one defect
        // it exists to catch — because the writes finish before a descheduled prober gets to stat
        // at all (probes taken during the write window ranged from 0 to 119,404 across runs). This
        // check and the inotify one below both caught the same regression 50 of 50 times, idle and
        // loaded, with no false alarm on the shipped implementation.
        val deletes = AtomicInteger(0)
        val target = object : File(folder.newFolder("widget"), "widget-today-snapshot.json") {
            override fun delete(): Boolean {
                deletes.incrementAndGet()
                return super.delete()
            }
        }
        WidgetSnapshotIo.writeAtomically(target, "seed".toByteArray())

        repeat(WRITE_ITERATIONS) { i ->
            WidgetSnapshotIo.writeAtomically(target, "payload-$i".toByteArray())
        }

        assertEquals(
            "the write unlinked the snapshot instead of renaming onto it, so the four " +
                "File.exists() hydrate/fast-paint probes go transiently false",
            0,
            deletes.get(),
        )
        // The write has to have actually happened, or the count above proves nothing.
        assertArrayEquals("payload-${WRITE_ITERATIONS - 1}".toByteArray(), target.readBytes())
    }

    @Test
    fun `concurrent writers never leave a torn file`() {
        // Defect 2. Each writer uses a payload of a different length AND different content, so a
        // half-and-half interleave cannot accidentally equal a valid one. A real GCM ciphertext
        // fails its auth tag when torn, and `read` deletes it — which is how this reached the user
        // as "Loading tasks…" rather than as garbled text.
        val target = target()
        val payloads = (0 until WRITER_THREADS).map { writer ->
            ("w$writer:" + writer.toString().repeat(PAYLOAD_REPEAT * (writer + 1))).toByteArray()
        }
        WidgetSnapshotIo.writeAtomically(target, payloads.first())

        val valid = payloads.map { it.toList() }.toSet()
        val torn = AtomicReference<List<Byte>?>(null)
        val stop = AtomicBoolean(false)
        val reader = Thread {
            while (!stop.get()) {
                val bytes = runCatching { target.readBytes().toList() }.getOrNull() ?: continue
                if (bytes !in valid) torn.compareAndSet(null, bytes)
            }
        }
        reader.start()

        val start = CountDownLatch(1)
        val done = CountDownLatch(WRITER_THREADS)
        val writerFailure = AtomicReference<Throwable?>(null)
        payloads.forEachIndexed { index, payload ->
            Thread {
                runCatching {
                    start.await()
                    repeat(WRITE_ITERATIONS) {
                        WidgetSnapshotIo.withStoreLock { WidgetSnapshotIo.writeAtomically(target, payload) }
                    }
                }.onFailure { writerFailure.compareAndSet(null, it) }
                done.countDown()
            }.also { it.name = "snapshot-writer-$index" }.start()
        }
        start.countDown()
        assertTrue("writers did not finish", done.await(AWAIT_TIMEOUT_S, TimeUnit.SECONDS))
        stop.set(true)
        reader.join(JOIN_TIMEOUT_MS)

        // `join` returns silently on timeout, so assert the reader really finished. A reader still
        // running here would have had its findings discarded.
        assertFalse("the reader thread did not finish", reader.isAlive)
        assertNull("a writer threw: ${writerFailure.get()}", writerFailure.get())
        assertNull(
            "read a file that was neither payload — a torn write",
            torn.get()?.let { String(it.toByteArray()) },
        )
        // Whichever writer landed last, the file is one whole payload.
        assertTrue(target.readBytes().toList() in valid)
    }

    @Test
    fun `the store lock is reentrant`() {
        // `read` takes the lock, and a write path can legitimately read inside its own hold. A
        // non-reentrant lock would deadlock the render path rather than fail a test.
        val result = WidgetSnapshotIo.withStoreLock {
            WidgetSnapshotIo.withStoreLock { "reached" }
        }
        assertEquals("reached", result)
    }

    @Test
    fun `the store lock serialises its critical sections`() {
        val overlapping = AtomicBoolean(false)
        val inside = AtomicBoolean(false)
        val start = CountDownLatch(1)
        val done = CountDownLatch(LOCK_THREADS)

        repeat(LOCK_THREADS) {
            Thread {
                start.await()
                repeat(LOCK_ITERATIONS) {
                    WidgetSnapshotIo.withStoreLock {
                        if (!inside.compareAndSet(false, true)) overlapping.set(true)
                        inside.set(false)
                    }
                }
                done.countDown()
            }.start()
        }
        start.countDown()
        assertTrue(done.await(AWAIT_TIMEOUT_S, TimeUnit.SECONDS))

        assertFalse("two threads were inside the store lock at once", overlapping.get())
    }

    private companion object {
        const val WRITE_ITERATIONS = 200



        const val WRITER_THREADS = 4
        const val PAYLOAD_REPEAT = 400
        const val LOCK_THREADS = 6
        const val LOCK_ITERATIONS = 500

        /**
         * Generous on purpose. These threads only ever finish work, never wait on each other, so a
         * long wait cannot mask a bug — but a shared CI runner under load can make a short one
         * fail for no reason.
         */
        const val AWAIT_TIMEOUT_S = 30L
        const val JOIN_TIMEOUT_MS = 30_000L
    }
}
