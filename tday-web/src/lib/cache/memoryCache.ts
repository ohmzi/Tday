interface CacheEntry<T> {
  data: T;
  expiresAt: number;
}

const DEFAULT_TTL_MS = 60_000;
const CLEANUP_INTERVAL_MS = 5 * 60_000;

class MemoryCache {
  private store = new Map<string, CacheEntry<unknown>>();
  private lastCleanup = Date.now();

  get<T>(key: string): T | undefined {
    this.lazyCleanup();
    const entry = this.store.get(key);
    if (!entry) return undefined;
    if (Date.now() > entry.expiresAt) {
      this.store.delete(key);
      return undefined;
    }
    return entry.data as T;
  }

  set<T>(key: string, data: T, ttlMs = DEFAULT_TTL_MS): void {
    this.store.set(key, {
      data,
      expiresAt: Date.now() + ttlMs,
    });
  }

  invalidateForUser(userId: string): void {
    const prefix = `${userId}:`;
    for (const key of this.store.keys()) {
      if (key.startsWith(prefix)) {
        this.store.delete(key);
      }
    }
  }

  invalidateUserEndpoint(userId: string, endpoint: string): void {
    const prefix = `${userId}:${endpoint}`;
    for (const key of this.store.keys()) {
      if (key.startsWith(prefix)) {
        this.store.delete(key);
      }
    }
  }

  clear(): void {
    this.store.clear();
  }

  get size(): number {
    return this.store.size;
  }

  private lazyCleanup() {
    const now = Date.now();
    if (now - this.lastCleanup < CLEANUP_INTERVAL_MS) return;
    this.lastCleanup = now;
    for (const [key, entry] of this.store) {
      if (now > entry.expiresAt) {
        this.store.delete(key);
      }
    }
  }
}

const globalCache = globalThis as unknown as { __apiCache?: MemoryCache };
export const apiCache: MemoryCache =
  globalCache.__apiCache ?? (globalCache.__apiCache = new MemoryCache());

export function cacheKey(
  userId: string,
  endpoint: string,
  params?: Record<string, string>,
): string {
  if (!params || Object.keys(params).length === 0) {
    return `${userId}:${endpoint}`;
  }
  const sorted = Object.entries(params)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([k, v]) => `${k}=${v}`)
    .join("&");
  return `${userId}:${endpoint}:${sorted}`;
}

export function invalidateTodoCaches(userId: string): void {
  apiCache.invalidateUserEndpoint(userId, "todo");
  apiCache.invalidateUserEndpoint(userId, "completedTodo");
}

export function invalidateListCaches(userId: string): void {
  apiCache.invalidateUserEndpoint(userId, "list");
  apiCache.invalidateUserEndpoint(userId, "todo");
}

export function invalidateCompletedCaches(userId: string): void {
  apiCache.invalidateUserEndpoint(userId, "completedTodo");
}

export function invalidateSettingsCaches(userId: string): void {
  apiCache.invalidateUserEndpoint(userId, "app-settings");
}
