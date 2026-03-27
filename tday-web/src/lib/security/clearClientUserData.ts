async function deleteIndexedDbDatabase(name: string): Promise<void> {
  await new Promise<void>((resolve) => {
    try {
      const request = indexedDB.deleteDatabase(name);
      request.onsuccess = () => resolve();
      request.onerror = () => resolve();
      request.onblocked = () => resolve();
    } catch {
      resolve();
    }
  });
}

export async function clearClientUserData(): Promise<void> {
  if (typeof window === "undefined") return;

  try {
    window.sessionStorage.clear();
  } catch {
    // Ignore storage clear failures in restricted browser contexts.
  }

  try {
    const keys = Object.keys(window.localStorage);
    for (const key of keys) {
      window.localStorage.removeItem(key);
    }
  } catch {
    // Ignore storage clear failures in restricted browser contexts.
  }

  try {
    if ("caches" in window) {
      const cacheKeys = await window.caches.keys();
      await Promise.all(cacheKeys.map((cacheKey) => window.caches.delete(cacheKey)));
    }
  } catch {
    // Ignore CacheStorage clear failures.
  }

  try {
    if (typeof indexedDB === "undefined") return;
    if (typeof indexedDB.databases !== "function") return;

    const databases = await indexedDB.databases();
    await Promise.all(
      databases
        .map((database) => database.name?.trim())
        .filter((name): name is string => Boolean(name))
        .map((name) => deleteIndexedDbDatabase(name)),
    );
  } catch {
    // Ignore IndexedDB clear failures.
  }
}
