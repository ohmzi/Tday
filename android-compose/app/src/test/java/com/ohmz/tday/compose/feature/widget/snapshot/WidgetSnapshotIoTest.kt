package com.ohmz.tday.compose.feature.widget.snapshot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
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
 * Defect 3 is checked by asserting that the replacement never unlinks the name — once against the
 * `File` the caller passes in, once against the kernel's own event stream — rather than by racing
 * a thread to catch the name missing. A `rename(2)` has no window to catch, so a sampling probe
 * can only ever report noise or nothing; see the comments on those two tests for what it measured.
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
    fun `the kernel never reports the snapshot name being unlinked mid-replacement`() {
        // Defect 3, half two. The check above only sees a delete routed through the `File` handed
        // in; this one sees any unlink of the name whatever API performs it, because it reads the
        // kernel's own event stream. inotify queues events, so unlike a stat loop this cannot miss
        // the window by being descheduled — which is why it held at 50/50 under load where the
        // spin loop collapsed to 18/50.
        //
        // `rename(2)` reports the temp NAME moving away and the target name being created; it
        // never reports the target being deleted. Delete-then-write reports exactly that.
        val target = target()
        WidgetSnapshotIo.writeAtomically(target, "seed".toByteArray())

        FileSystems.getDefault().newWatchService().use { watcher ->
            target.parentFile.toPath()
                .register(watcher, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.OVERFLOW)

            // Positive control, before trusting this oracle to report an absence of events: prove
            // it can see an unlink at all. A JDK whose WatchService polls directory listings rather
            // than using inotify (the BSD/macOS fallback) would deliver nothing and turn every
            // assertion below into a silent pass, which is worse than no test. Skip there instead;
            // `a replacement never deletes the snapshot it is replacing` still runs everywhere.
            val control = File(target.parentFile, "watcher-control-probe")
            control.writeBytes("x".toByteArray())
            assertTrue(control.delete())
            assumeTrue(
                "this JDK's WatchService is not event-driven, so it cannot witness an unlink",
                drain(watcher, CONTROL_TIMEOUT_MS).contains(control.name),
            )

            repeat(WRITE_ITERATIONS) { i ->
                WidgetSnapshotIo.writeAtomically(target, "payload-$i".toByteArray())
            }
            val unlinked = drain(watcher, DRAIN_TIMEOUT_MS)

            assertFalse("inotify overflowed, so this run proved nothing — rerun it", unlinked.contains(OVERFLOWED))
            // Liveness. Each rename moves `<name>.tmp` away, which inotify reports against that
            // name. Seeing none of those would mean the watcher was dead and the assertion below
            // vacuously true — the failure mode the old prober had, which nothing there asserted
            // against: some of its runs got through the whole write window taking zero probes.
            assertTrue("the watcher observed nothing at all", unlinked.contains("${target.name}.tmp"))
            assertFalse(
                "the replacement unlinked ${target.name}, so it is transiently absent to the " +
                    "hydrate and fast-paint probes",
                unlinked.contains(target.name),
            )
        }
    }

    /**
     * Every entry name the watcher reported, plus [OVERFLOWED] if the kernel queue overran.
     *
     * Waits [firstTimeoutMs] for anything at all to arrive, then [DRAIN_TIMEOUT_MS] between
     * batches. Two timeouts rather than one because the first wait has to be long enough to
     * conclude "this watcher never delivers", while the later ones only bridge the gap between
     * events the kernel has already queued — paying the long wait on every batch would add ten
     * seconds to a test that is otherwise instant.
     */
    private fun drain(watcher: WatchService, firstTimeoutMs: Long): List<String> {
        val names = mutableListOf<String>()
        var timeoutMs = firstTimeoutMs
        while (true) {
            val key = watcher.poll(timeoutMs, TimeUnit.MILLISECONDS) ?: return names
            key.pollEvents().forEach { event ->
                names += if (event.kind() === StandardWatchEventKinds.OVERFLOW) {
                    OVERFLOWED
                } else {
                    event.context().toString()
                }
            }
            key.reset()
            timeoutMs = DRAIN_TIMEOUT_MS
        }
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

        /** Marks a kernel event-queue overrun in [drain]; no file can be named this. */
        const val OVERFLOWED = "<overflow>"

        /** Long enough that a loaded box still delivers the control unlink before we give up. */
        const val CONTROL_TIMEOUT_MS = 10_000L

        /**
         * Gap between batches of events the kernel has already queued, so this is a scheduling
         * delay and not an IO wait. Generous anyway: ending the drain early would mean missing a
         * delete that did happen, which is the one way this test could wrongly pass.
         */
        const val DRAIN_TIMEOUT_MS = 1_000L
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
