function createMemoryStorage(): Storage {
  const values = new Map<string, string>();
  const storage: Partial<Storage> = {};

  function syncKeyProperty(key: string, value: string | null) {
    if (value == null) {
      delete (storage as Record<string, unknown>)[key];
      return;
    }
    Object.defineProperty(storage, key, {
      configurable: true,
      enumerable: true,
      value,
      writable: true,
    });
  }

  Object.defineProperties(storage, {
    length: {
      configurable: true,
      enumerable: false,
      get() {
        return values.size;
      },
    },
    clear: {
      configurable: true,
      enumerable: false,
      value() {
        for (const key of values.keys()) {
          syncKeyProperty(key, null);
        }
        values.clear();
      },
    },
    getItem: {
      configurable: true,
      enumerable: false,
      value(key: string) {
        return values.get(key) ?? null;
      },
    },
    key: {
      configurable: true,
      enumerable: false,
      value(index: number) {
        return Array.from(values.keys())[index] ?? null;
      },
    },
    removeItem: {
      configurable: true,
      enumerable: false,
      value(key: string) {
        values.delete(key);
        syncKeyProperty(key, null);
      },
    },
    setItem: {
      configurable: true,
      enumerable: false,
      value(key: string, value: string) {
        values.set(key, value);
        syncKeyProperty(key, value);
      },
    },
  });

  return storage as Storage;
}

function installStorage(name: "localStorage" | "sessionStorage") {
  if (typeof window === "undefined") {
    return;
  }

  const candidate = window[name];
  if (
    typeof candidate?.getItem === "function" &&
    typeof candidate?.setItem === "function" &&
    typeof candidate?.removeItem === "function" &&
    typeof candidate?.clear === "function"
  ) {
    return;
  }

  Object.defineProperty(window, name, {
    configurable: true,
    value: createMemoryStorage(),
  });
}

installStorage("localStorage");
installStorage("sessionStorage");
