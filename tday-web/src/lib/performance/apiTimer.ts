const SLOW_THRESHOLD_MS = 200;

export function apiTimer(label: string): () => void {
  const start = performance.now();
  return () => {
    const elapsed = performance.now() - start;
    if (elapsed > SLOW_THRESHOLD_MS) {
      console.warn(`[api-perf] ${label}: ${elapsed.toFixed(1)}ms (slow)`);
    } else if (process.env.NODE_ENV !== "production") {
      console.log(`[api-perf] ${label}: ${elapsed.toFixed(1)}ms`);
    }
  };
}
