import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";

export type AuthUser = {
  id: string;
  name: string | null;
  email: string | null;
  role: string | null;
  approvalStatus: string | null;
  timeZone: string | null;
};

type AuthContextValue = {
  user: AuthUser | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (email: string, credentialPayload: Record<string, string>) => Promise<{ ok: boolean; code?: string; message?: string }>;
  logout: () => Promise<void>;
  refreshSession: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const queryClient = useQueryClient();

  const fetchSession = useCallback(async () => {
    try {
      const res = await fetch("/api/auth/session", {
        credentials: "same-origin",
        cache: "no-store",
      });
      if (res.ok) {
        const data = await res.json();
        setUser(data.user ?? null);
      } else {
        setUser(null);
      }
    } catch {
      setUser(null);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchSession();
  }, [fetchSession]);

  const login = useCallback(
    async (email: string, credentialPayload: Record<string, string>) => {
      const res = await fetch("/api/auth/callback/credentials", {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, ...credentialPayload }),
      });

      const data = await res.json().catch(() => ({}));

      if (!res.ok) {
        return {
          ok: false,
          code: data.code ?? data.reason,
          message: data.message ?? "Invalid credentials",
        };
      }

      await fetchSession();
      return { ok: true };
    },
    [fetchSession],
  );

  const logout = useCallback(async () => {
    queryClient.clear();
    await clearClientUserData();
    await fetch("/api/auth/logout", {
      method: "POST",
      credentials: "same-origin",
    }).catch(() => {});
    setUser(null);
    window.location.replace(window.location.origin);
  }, [queryClient]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isLoading,
      isAuthenticated: user !== null,
      login,
      logout,
      refreshSession: fetchSession,
    }),
    [user, isLoading, login, logout, fetchSession],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}

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

async function clearClientUserData(): Promise<void> {
  if (typeof window === "undefined") return;

  try {
    window.sessionStorage.clear();
  } catch {}

  try {
    const keys = Object.keys(window.localStorage);
    for (const key of keys) {
      window.localStorage.removeItem(key);
    }
  } catch {}

  try {
    if ("caches" in window) {
      const cacheKeys = await window.caches.keys();
      await Promise.all(cacheKeys.map((cacheKey) => window.caches.delete(cacheKey)));
    }
  } catch {}

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
  } catch {}
}
