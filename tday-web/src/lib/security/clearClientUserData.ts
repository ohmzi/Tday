type ClearClientUserDataOptions = {
  preserveLocalStorageKeys?: string[];
};

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

export async function clearClientUserData(
  options: ClearClientUserDataOptions = {},
): Promise<void> {
  if (typeof window === "undefined") return;

  // Unsubscribe from push notifications before clearing storage.
  try {
    if ("serviceWorker" in navigator) {
      const reg = await navigator.serviceWorker.getRegistration("/");
      const sub = await reg?.pushManager.getSubscription();
      if (sub) await sub.unsubscribe();
    }
  } catch {
    // Ignore push unsubscribe failures.
  }

  try {
    window.localStorage.removeItem("tday.push-enabled");
  } catch {
    // Ignore storage failures.
  }

  try {
    window.sessionStorage.clear();
  } catch {
    // Ignore storage clear failures in restricted browser contexts.
  }

  try {
    const preserveKeys = new Set(options.preserveLocalStorageKeys ?? []);
    const keys = Object.keys(window.localStorage);
    for (const key of keys) {
      if (preserveKeys.has(key)) continue;
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
