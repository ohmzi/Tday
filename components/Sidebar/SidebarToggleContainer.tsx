"use client"
import React, { useEffect, useRef, useState } from 'react'
import { useMenu } from "@/providers/MenuProvider";
import SidebarToggle from "@/components/ui/SidebarToggle";
import { SonnerToaster } from "@/components/ui/sonner";
import { usePathname, useRouter } from 'next/navigation';
import useWindowSize from "@/hooks/useWindowSize";

export default function SidebarToggleContainer() {
  const { showMenu, sidebarReady } = useMenu();
  const { width } = useWindowSize();
  const [mounted, setMounted] = useState(false);
  const seqRef = useRef<string[]>([]);
  const router = useRouter();
  const pathname = usePathname();
  const timerRef = useRef<number | null>(null);

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement;

      if (
        target?.isContentEditable ||
        ["INPUT", "TEXTAREA"].includes(target.tagName)
      )
        return;

      const key = e.key.toLowerCase();

      if (e.ctrlKey || e.metaKey || e.altKey) return;
      if (key.length !== 1) return;

      seqRef.current.push(key);

      if (timerRef.current) window.clearTimeout(timerRef.current);

      timerRef.current = window.setTimeout(() => {
        seqRef.current = [];
      }, 600);

      const seq = seqRef.current.join("");

      const routes = {
        gt: { path: "/app/tday", name: "Todo" },
        gc: { path: "/app/calendar", name: "Calendar" },
        gd: { path: "/app/completed", name: "Completed" },
      };

      const route = routes[seq as keyof typeof routes];

      if (route) {
        e.preventDefault();
        e.stopPropagation();

        const { path, name } = route;

        localStorage.setItem("tab", JSON.stringify({ name }));

        router.push(path);

        seqRef.current = [];
      }

      if (seqRef.current.length === 1 && seqRef.current[0] !== "g") {
        seqRef.current = [];
      }
    };

    document.addEventListener("keydown", handler, true);
    return () => document.removeEventListener("keydown", handler, true);
  }, [router]);

  const isDesktop = width === 0 ? true : width >= 1024;
  const hasInlineMobileToggle =
    pathname?.includes("/app/tday") ||
    pathname?.includes("/app/completed") ||
    pathname?.includes("/app/calendar") ||
    pathname?.includes("/app/add-task");
  const showMobileToggle =
    mounted && sidebarReady && !isDesktop && !showMenu && !hasInlineMobileToggle;

  return (<>
    {mounted && sidebarReady && <SonnerToaster />}

    {showMobileToggle && (
      <SidebarToggle className="fixed left-3 top-3 z-40 p-0 text-muted-foreground hover:text-foreground lg:hidden" />
    )}

  </>
  )
}
