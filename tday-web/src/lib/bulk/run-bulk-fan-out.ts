import { BULK_MAX_CONCURRENCY } from "./bulk-selection-policy";

export type BulkFanOutResult = {
  total: number;
  /** How many items threw. Never reconstructed from response bodies — see below. */
  failed: number;
};

/**
 * Runs one request per selected row, a few at a time, and never gives up part
 * way through.
 *
 * Two rules from `docs/design/bulk-selection.md` §5–§6 are load-bearing here:
 *
 * - **Never abort on the first failure.** A bulk action that stops halfway is a
 *   partially applied action the user was told nothing about, which is worse
 *   than one that finishes and reports what it could not do. Each lane swallows
 *   its own rejection and keeps pulling work, so this settles every item — the
 *   `Promise.allSettled` guarantee, with a concurrency limit bolted on.
 * - **Bounded concurrency.** `api_global` rate-limits to 180 requests/60s per
 *   user and every mutation fans out realtime + webhook + push work server-side,
 *   so a hundred simultaneous requests would take 429s mid-batch.
 *
 * The count returned is a count of *thrown* requests only. `update`,
 * `prioritize` and `completeTodo` all return success even when the tenant filter
 * matched zero rows, so a success count reconstructed from responses would be a
 * lie; callers must invalidate and let the server re-state the truth instead.
 *
 * Deliberately plain promises: this runs on iOS Safari inside the installed PWA,
 * where the scheduling APIs it would be tempting to reach for do not exist.
 */
export async function runBulkFanOut<T>(
  items: readonly T[],
  run: (item: T) => Promise<unknown>,
  concurrency: number = BULK_MAX_CONCURRENCY,
): Promise<BulkFanOutResult> {
  const total = items.length;
  if (total === 0) {
    return { total: 0, failed: 0 };
  }

  const lanes = Math.max(1, Math.min(Math.floor(concurrency) || 1, total));
  let cursor = 0;
  let failed = 0;

  const worker = async () => {
    for (;;) {
      const index = cursor;
      cursor += 1;
      if (index >= total) return;
      const item = items[index];
      try {
        await run(item);
      } catch {
        // Swallowed on purpose: the batch reports one aggregate failure at the
        // end (§6), never one toast per item.
        failed += 1;
      }
    }
  };

  await Promise.all(Array.from({ length: lanes }, () => worker()));

  return { total, failed };
}
