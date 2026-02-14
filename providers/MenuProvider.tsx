"use client";
import {
  useCallback,
  createContext,
  useRef,
  SetStateAction,
  useContext,
  useEffect,
  useState,
} from "react";
import { usePathname } from "next/navigation";
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
const DESKTOP_BREAKPOINT = 1024;

export const MenuProvider = ({ children }: { children: React.ReactNode }) => {
  const { width } = useWindowSize();
  const pathName = usePathname();
  const [activeMenu, setActiveMenu] = useState<MenuState>({ name: "Todo" });
  const [showMenu, setShowMenuState] = useState(true);
  const [sidebarReady, setSidebarReady] = useState(false);
  const [isResizing, setIsResizing] = useState(false);
  const [mounted, setMounted] = useState(false);
  const previousIsDesktop = useRef<boolean | null>(null);

  const setShowMenu = useCallback<React.Dispatch<SetStateAction<boolean>>>(
    (value) => {
      setShowMenuState((previous) => {
        const next =
          typeof value === "function"
            ? (value as (prevState: boolean) => boolean)(previous)
            : value;

        if (typeof window !== "undefined" && window.innerWidth >= DESKTOP_BREAKPOINT) {
          localStorage.setItem(SIDEBAR_STORAGE_KEY, String(next));
        }

        return next;
      });
    },
    [],
  );

  // Mount + initialize sidebar state from persisted desktop preference
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

  // Keep sidebar behavior stable when crossing desktop/mobile breakpoint
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

  // Infer last visited tab from pathname or retrieve from local storage
  useEffect(() => {
    if (!mounted) return;

    if (pathName.includes("add-task")) {
      setActiveMenu({ name: "AddTask" });
      return;
    }

    if (pathName.includes("tag")) {
      const path = pathName.split("/");
      const tagID = path[path.length - 1];
      setActiveMenu({ name: "Tag", open: true, children: { name: tagID } });
      return;
    }

    if (pathName.includes("note")) {
      if (pathName.endsWith("note")) {
        setActiveMenu({ name: "Note", open: true });
        return;
      } else {
        const path = pathName.split("/");
        const noteID = path[path.length - 1];
        setActiveMenu({ name: "Note", open: true, children: { name: noteID } });
        return;
      }
    }

    if (pathName.includes("tday") || pathName.includes("todo")) {
      setActiveMenu({ name: "Todo" });
      return;
    }
    if (pathName.includes("calendar")) {
      setActiveMenu({ name: "Calendar" });
      return;
    }
    if (pathName.includes("completed")) {
      setActiveMenu({ name: "Completed" });
      return;
    }
    const tab = localStorage.getItem("tab");
    if (tab) {
      const tabObj = JSON.parse(tab);
      setActiveMenu(tabObj);
    }
  }, [mounted, pathName]);

  // Sync local menu state with local storage when menu state changes
  useEffect(() => {
    if (mounted) {
      localStorage.setItem("tab", JSON.stringify(activeMenu));
    }
  }, [activeMenu, mounted]);

  // Toggle menu on ctrl+`
  useEffect(() => {
    function closeOnKey(e: KeyboardEvent) {
      if (e.ctrlKey && e.key.toLowerCase() === "`") {
        setShowMenu((prev) => !prev);
      }
    }
    document.addEventListener("keydown", closeOnKey);
    return () => {
      document.removeEventListener("keydown", closeOnKey);
    };
  }, []);

  return (
    <MenuContext.Provider
      value={{
        activeMenu,
        setActiveMenu,
        showMenu,
        setShowMenu,
        sidebarReady,
        isResizing,
        setIsResizing,
      }}
    >
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
  // Return safe defaults when rendered outside MenuProvider (e.g. auth pages)
  return context ?? fallbackContext;
};
