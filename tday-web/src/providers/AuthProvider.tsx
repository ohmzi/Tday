import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api-client";
import { clearClientUserData } from "@/lib/security/clearClientUserData";
import {
  markReturningBrowser,
  RETURNING_BROWSER_STORAGE_KEY,
} from "@/lib/security/returningBrowser";

const AUTH_SESSION_RETRY_DELAY_MS = 15_000;

export type AuthSessionState =
  | "loading"
  | "authenticated"
  | "unauthenticated"
  | "unavailable";

export type AuthUser = {
  id: string;
  name: string | null;
  username: string | null;
  role: string | null;
  approvalStatus: string | null;
  timeZone: string | null;
  requirePasswordChange?: boolean;
};

type AuthContextValue = {
  user: AuthUser | null;
  authState: AuthSessionState;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (username: string, credentialPayload: Record<string, string>) => Promise<{ ok: boolean; code?: string; message?: string }>;
  logout: () => Promise<void>;
  refreshSession: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [authState, setAuthState] = useState<AuthSessionState>("loading");
  const queryClient = useQueryClient();
  const authStateRef = useRef<AuthSessionState>("loading");
  const userRef = useRef<AuthUser | null>(null);

  const applySessionState = useCallback((nextAuthState: AuthSessionState, nextUser: AuthUser | null) => {
    authStateRef.current = nextAuthState;
    userRef.current = nextUser;
    setAuthState(nextAuthState);
    setUser(nextUser);
  }, []);

  const setSessionAvailability = useCallback((nextAuthState: AuthSessionState) => {
    authStateRef.current = nextAuthState;
    setAuthState(nextAuthState);
  }, []);

  const fetchSession = useCallback(async () => {
    try {
      const data = await api.GET({ url: "/api/auth/session" });
      const nextUser = data?.user ?? null;

      if (nextUser) {
        markReturningBrowser();
        applySessionState("authenticated", nextUser);
        return;
      }

      applySessionState("unauthenticated", null);
    } catch (error) {
      const apiError = error instanceof ApiError ? error : null;

      if (apiError?.status === 401) {
        const hadAuthenticatedSession =
          authStateRef.current === "authenticated" || userRef.current !== null;

        if (hadAuthenticatedSession) {
          queryClient.clear();
          await clearClientUserData({
            preserveLocalStorageKeys: [RETURNING_BROWSER_STORAGE_KEY],
          });
        }

        applySessionState("unauthenticated", null);
        return;
      }

      setSessionAvailability("unavailable");
    }
  }, [applySessionState, queryClient, setSessionAvailability]);

  useEffect(() => {
    void fetchSession();
  }, [fetchSession]);

  useEffect(() => {
    if (authState !== "unavailable") return;

    const retryTimer = window.setTimeout(() => {
      void fetchSession();
    }, AUTH_SESSION_RETRY_DELAY_MS);

    return () => {
      window.clearTimeout(retryTimer);
    };
  }, [authState, fetchSession]);

  const login = useCallback(
    async (username: string, credentialPayload: Record<string, string>) => {
      try {
        await api.POST({
          url: "/api/auth/callback/credentials",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ username, ...credentialPayload }),
        });
        markReturningBrowser();
        await fetchSession();

        if (authStateRef.current === "unauthenticated") {
          return {
            ok: false,
            message: "Unable to establish a session. Please try again.",
          };
        }

        return { ok: true };
      } catch (e) {
        const err = e instanceof ApiError ? e : null;
        return {
          ok: false,
          code: err?.code,
          message: err?.message ?? "Invalid credentials",
        };
      }
    },
    [fetchSession],
  );

  const logout = useCallback(async () => {
    markReturningBrowser();
    try {
      await api.POST({ url: "/api/auth/logout", body: "{}" });
    } catch (error) {
      const message = error instanceof ApiError
        ? error.message
        : "Unable to log out. Please try again.";
      throw new Error(message);
    }
    queryClient.clear();
    await clearClientUserData({
      preserveLocalStorageKeys: [RETURNING_BROWSER_STORAGE_KEY],
    });
    applySessionState("unauthenticated", null);
    window.location.replace(window.location.origin);
  }, [applySessionState, queryClient]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      authState,
      isLoading: authState === "loading",
      isAuthenticated: authState === "authenticated",
      login,
      logout,
      refreshSession: fetchSession,
    }),
    [user, authState, login, logout, fetchSession],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
