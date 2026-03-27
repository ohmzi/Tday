import {
  useCallback,
  createContext,
  useRef,
  type SetStateAction,
  useContext,
  useEffect,
  useState,
} from "react";
import { useLocation } from "react-router-dom";
import useWindowSize from "@/hooks/useWindowSize";

type MenuState = {
  name: string;
  open?: boolean;
  children?: MenuState;
};

type MenuContextType = {
  activeMenu: MenuState;
  setActiveMenu: React.Dispatch<SetStateAction<MenuState>>;
  showMenu: boolean;
  setShowMenu: React.Dispatch<SetStateAction<boolean>>;
  sidebarReady: boolean;
  isResizing: boolean;
  setIsResizing: React.Dispatch<SetStateAction<boolean>>;
};

const MenuContext = createContext<MenuContextType | undefined>(undefined);
const SIDEBAR_STORAGE_KEY = "tday.sidebar.desktop.open";
const TAB_STORAGE_KEY = "tab";
const DESKTOP_BREAKPOINT = 1024;

function isValidMenuState(value: unknown): value is MenuState {
  if (!value || typeof value !== "object") return false;
  const candidate = value as { name?: unknown; open?: unknown; children?: unknown };
  if (typeof candidate.name !== "string" || candidate.name.trim().length === 0) return false;
  if (candidate.open !== undefined && typeof candidate.open !== "boolean") return false;
  if (candidate.children !== undefined && !isValidMenuState(candidate.children)) return false;
  return true;
}

function parseStoredMenuState(raw: string | null): MenuState | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as unknown;
    return isValidMenuState(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

export const MenuProvider = ({ children }: { children: React.ReactNode }) => {
  const { width } = useWindowSize();
  const { pathname } = useLocation();
  const [activeMenu, setActiveMenu] = useState<MenuState>({ name: "Todo" });
  const [showMenu, setShowMenuState] = useState(true);
  const [sidebarReady, setSidebarReady] = useState(false);
  const [isResizing, setIsResizing] = useState(false);
  const [mounted, setMounted] = useState(false);
  const previousIsDesktop = useRef<boolean | null>(null);

  const setShowMenu = useCallback<React.Dispatch<SetStateAction<boolean>>>(
    (value) => {
      setShowMenuState((previous) => {
        const next = typeof value === "function" ? (value as (prev: boolean) => boolean)(previous) : value;
        if (window.innerWidth >= DESKTOP_BREAKPOINT) {
          localStorage.setItem(SIDEBAR_STORAGE_KEY, String(next));
        }
        return next;
      });
    },
    [],
  );

  useEffect(() => {
    setMounted(true);
    const desktop = window.innerWidth >= DESKTOP_BREAKPOINT;
    const storedPreference = localStorage.getItem(SIDEBAR_STORAGE_KEY);

    if (!desktop) {
      setShowMenuState(false);
    } else if (storedPreference === "true" || storedPreference === "false") {
      setShowMenuState(storedPreference === "true");
    } else {
      setShowMenuState(true);
      localStorage.setItem(SIDEBAR_STORAGE_KEY, "true");
    }

    previousIsDesktop.current = desktop;
    setSidebarReady(true);
  }, []);

  useEffect(() => {
    if (!mounted || !sidebarReady) return;
    const isDesktop = width >= DESKTOP_BREAKPOINT;
    const wasDesktop = previousIsDesktop.current;
    if (wasDesktop === null) {
      previousIsDesktop.current = isDesktop;
      return;
    }
    if (wasDesktop !== isDesktop) {
      if (!isDesktop) {
        setShowMenuState(false);
      } else {
        const storedPreference = localStorage.getItem(SIDEBAR_STORAGE_KEY);
        setShowMenuState(storedPreference === "false" ? false : true);
      }
      previousIsDesktop.current = isDesktop;
    }
  }, [mounted, sidebarReady, width]);

  useEffect(() => {
    if (!mounted || !sidebarReady) return;
    const isDesktop = width >= DESKTOP_BREAKPOINT;
    if (!isDesktop) setShowMenuState(false);
  }, [mounted, pathname, sidebarReady, width]);

  useEffect(() => {
    if (!mounted) return;
    if (pathname.includes("add-task")) { setActiveMenu({ name: "AddTask" }); return; }
    if (pathname.includes("/list/")) {
      const parts = pathname.split("/");
      const listID = parts[parts.length - 1];
      setActiveMenu({ name: "List", open: true, children: { name: listID } });
      return;
    }
    if (pathname.includes("note")) {
      if (pathname.endsWith("note")) { setActiveMenu({ name: "Note", open: true }); return; }
      const parts = pathname.split("/");
      const noteID = parts[parts.length - 1];
      setActiveMenu({ name: "Note", open: true, children: { name: noteID } });
      return;
    }
    if (pathname.includes("tday") || pathname.includes("todo")) { setActiveMenu({ name: "Todo" }); return; }
    if (pathname.includes("calendar")) { setActiveMenu({ name: "Calendar" }); return; }
    if (pathname.includes("completed")) { setActiveMenu({ name: "Completed" }); return; }
    const parsedTab = parseStoredMenuState(localStorage.getItem(TAB_STORAGE_KEY));
    if (parsedTab) { setActiveMenu(parsedTab); return; }
    localStorage.removeItem(TAB_STORAGE_KEY);
    setActiveMenu({ name: "Todo" });
  }, [mounted, pathname]);

  useEffect(() => {
    if (mounted) localStorage.setItem(TAB_STORAGE_KEY, JSON.stringify(activeMenu));
  }, [activeMenu, mounted]);

  useEffect(() => {
    function closeOnKey(e: KeyboardEvent) {
      if (e.ctrlKey && e.key.toLowerCase() === "`") setShowMenu((prev) => !prev);
    }
    document.addEventListener("keydown", closeOnKey);
    return () => document.removeEventListener("keydown", closeOnKey);
  }, [setShowMenu]);

  return (
    <MenuContext.Provider value={{ activeMenu, setActiveMenu, showMenu, setShowMenu, sidebarReady, isResizing, setIsResizing }}>
      {children}
    </MenuContext.Provider>
  );
};

const noopSetState = () => {};
const fallbackContext: MenuContextType = {
  activeMenu: { name: "" },
  setActiveMenu: noopSetState,
  showMenu: false,
  setShowMenu: noopSetState,
  sidebarReady: false,
  isResizing: false,
  setIsResizing: noopSetState,
};

export const useMenu = () => {
  const context = useContext(MenuContext);
  return context ?? fallbackContext;
};
