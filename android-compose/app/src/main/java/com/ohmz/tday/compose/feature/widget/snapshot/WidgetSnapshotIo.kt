package com.ohmz.tday.compose.feature.widget.snapshot

import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * The file half of [WidgetSnapshotStore]: one process-wide lock, and a write that either replaces
 * a snapshot completely or leaves the previous one exactly as it was.
 *
 * Split out of the store because the store cannot be unit-tested — it needs `AndroidKeyStore` and a
 * real `Context` — while the two properties that actually went wrong (atomicity and mutual
 * exclusion) are plain file behaviour. `WidgetSnapshotIoTest` covers them on the JVM.
 *
 * ## Why the lock is here and not on the store
 *
 * Every writer constructs its own `WidgetSnapshotStore`, so an instance lock would guard nothing.
 * The writers are genuinely concurrent and none of them coordinate: `OfflineCacheManager`'s
 * `saveOfflineStateBlocking` (`@Synchronized`, but on the cache manager, which nothing else takes),
 * its migration path, `clearAllLocalData` and `clearSessionOnly`, `WidgetHydrateWorker.doWork` on a
 * WorkManager thread, and `WidgetListConfigurationViewModel.selectList` on `viewModelScope`. Two of
 * those interleaving inside one `writeBytes` produced a file that failed GCM authentication, which
 * [WidgetSnapshotStore.read] then deleted — dropping the widget to "Loading tasks…".
 *
 * ## Why the write is a rename and not a delete
 *
 * The store used to `delete()` the target and only then evaluate `encrypt(bytes)` as the argument
 * to `writeBytes`, with the whole thing inside `runCatching {}.getOrElse { false }`. Any Keystore,
 * cipher or IO failure therefore destroyed the last good snapshot, reported `false`, and swallowed
 * the throwable. That is the worst possible ordering: the failure modes that make encryption throw
 * (a key invalidated by a lock-screen change, a provider unavailable before first unlock) are
 * exactly the ones where the stale snapshot was still the best thing to show.
 *
 * The original argument for delete-then-write was that GCM's auth tag makes a truncated file fail
 * to decrypt rather than decrypt wrong, so a half-written file is already safe. True, but it argues
 * FOR this: `rename(2)` is atomic within a directory, so a reader in another process sees either
 * the old file or the new one, never a partial one — and a failure leaves the old file readable
 * instead of leaving nothing.
 *
 * It also closes a window the callers depend on. `FloaterTasksWidget`, `TodayTasksWidget`,
 * `ListTasksWidget` and `WidgetFastPaint` probe `File.exists()` to decide whether to enqueue
 * `WidgetHydrateWorker` or whether to fast-paint at all. Under delete-then-write that probe was
 * transiently false on EVERY cache write, so a `provideGlance` landing in that window enqueued the
 * hydrate worker — adding one more unsynchronised writer — and a fast paint landing in it silently
 * skipped, reintroducing the ~2.4-3.0s post-reboot delay that class exists to remove. Here the
 * target is only ever replaced, never absent, so all four probes are truthful without changing a
 * single call site.
 */
internal object WidgetSnapshotIo {

    /**
     * Guards every snapshot read and write in this process. Reentrant so a caller already holding
     * it (a read taken inside a write path) does not deadlock.
     *
     * Coarse on purpose: these are sub-millisecond operations on a handful of small files, and a
     * per-path lock would not help the case that matters — two writers racing on the SAME path.
     */
    private val lock = ReentrantLock()

    fun <T> withStoreLock(block: () -> T): T = lock.withLock(block)

    /**
     * Replaces [target]'s contents with [payload], atomically.
     *
     * Throws rather than returning a boolean so the caller can log what actually failed; on any
     * failure [target] is left holding whatever it held before, and no temp file survives.
     */
    fun writeAtomically(target: File, payload: ByteArray) {
        target.parentFile?.mkdirs()
        // Same directory as the target: `rename(2)` is only atomic within a filesystem, and a
        // sibling is the one place guaranteed to be on the same one.
        val temp = File(target.parentFile, "${target.name}.tmp")
        try {
            temp.writeBytes(payload)
            if (!temp.renameTo(target)) {
                throw IOException("could not rename ${temp.name} onto ${target.name}")
            }
        } finally {
            // No-op on the success path (the rename consumed it); on any failure this is what
            // stops a stale half-written temp file accumulating.
            temp.delete()
        }
    }
}
