"use client";

import clsx from "clsx";
import React from "react";
import Image from "next/image";
import { Link } from "@/i18n/navigation";
import { usePathname } from "next/navigation";
import {
  Calendar1Icon,
  CheckCircleIcon,
  Plus,
  Sun,
} from "lucide-react";
import { useMenu } from "@/providers/MenuProvider";
import { useTodo } from "@/features/todayTodos/query/get-todo";
import { useCompletedTodo } from "@/features/completed/query/get-completedTodo";
import useWindowSize from "@/hooks/useWindowSize";
import UserCard from "./User/UserCard";
import LineSeparator from "../ui/lineSeparator";
import { cn } from "@/lib/utils";
import { Tooltip, TooltipContent, TooltipTrigger } from "../ui/tooltip";
import TagSidebarSection from "./Tag/TagSidebarSection";
import {
  Sheet,
  SheetContent,
  SheetTitle,
} from "@/components/ui/sheet";

const SIDEBAR_CONTENT_SWAP_MS = 500;

const expandedNavButtonBase =
  "group flex h-10 w-full min-w-0 items-center overflow-hidden rounded-xl pl-0 pr-3 text-sm font-medium transition-colors duration-200";
const expandedNavButtonIdle =
  "text-sidebar-foreground/70 hover:bg-sidebar-accent/70 hover:text-sidebar-foreground";
const expandedNavButtonActive = "bg-sidebar-accent text-sidebar-accent-foreground";

const collapsedRailButtonBase =
  "group flex h-10 min-h-10 w-10 shrink-0 items-center justify-center rounded-xl text-sidebar-foreground/70 transition-colors duration-200 hover:bg-sidebar-accent/70 hover:text-sidebar-foreground";

const countBadgeBase =
  "ml-auto text-xs font-medium";

const railIconSlot = "flex h-10 w-10 shrink-0 items-center justify-center";
const railIconClass =
  "h-4 w-4 transition-colors duration-200";

const SidebarContainer = () => {
  const { showMenu, sidebarReady, setShowMenu } = useMenu();
  const { width } = useWindowSize();
  const { todos } = useTodo();
  const { completedTodos } = useCompletedTodo();
  const pathname = usePathname();

  const isDesktop = width === 0 ? true : width >= 1024;
  const shouldCollapse = isDesktop && !showMenu;
  const [renderCollapsed, setRenderCollapsed] = React.useState(shouldCollapse);
  const [animationsEnabled, setAnimationsEnabled] = React.useState(false);
  const initialSidebarSyncDone = React.useRef(false);

  React.useEffect(() => {
    if (!sidebarReady) {
      setAnimationsEnabled(false);
      initialSidebarSyncDone.current = false;
      return;
    }

    const frame = window.requestAnimationFrame(() => setAnimationsEnabled(true));
    return () => window.cancelAnimationFrame(frame);
  }, [sidebarReady]);

  React.useEffect(() => {
    if (!sidebarReady) {
      setRenderCollapsed(shouldCollapse);
      return;
    }

    if (!initialSidebarSyncDone.current) {
      setRenderCollapsed(shouldCollapse);
      initialSidebarSyncDone.current = true;
      return;
    }

    let timer: ReturnType<typeof setTimeout> | undefined;

    if (shouldCollapse) {
      // Switch slightly before width animation ends to avoid a visible post-collapse jump.
      timer = setTimeout(() => setRenderCollapsed(true), SIDEBAR_CONTENT_SWAP_MS);
    } else {
      setRenderCollapsed(false);
    }

    return () => {
      if (timer) {
        clearTimeout(timer);
      }
    };
  }, [shouldCollapse, sidebarReady]);

  const handleSidebarNavigate = React.useCallback(() => {
    if (!isDesktop) {
      setShowMenu(false);
    }
  }, [isDesktop, setShowMenu]);

  const isTodoActive = pathname?.includes("/app/tday") || pathname?.includes("/app/todo");
  const isCompletedActive = pathname?.includes("/app/completed");
  const isCalendarActive = pathname?.includes("/app/calendar");

  // Desktop: standard sidebar with collapse/expand
  if (isDesktop) {
    return (
      <nav
        id="sidebar_container"
        className={clsx(
          "relative z-30 flex h-full shrink-0 overflow-hidden border-r border-sidebar-border/90 bg-sidebar/95 backdrop-blur-sm",
          animationsEnabled
            ? "transition-[width] duration-500 ease-in-out"
            : "transition-none",
          shouldCollapse ? "w-[72px]" : "w-64",
        )}
      >
        {renderCollapsed ? (
          <CollapsedSidebarContent onNavigate={handleSidebarNavigate} />
        ) : (
          <ExpandedSidebarContent
            isDesktop={isDesktop}
            todosCount={todos.length}
            completedCount={completedTodos.length}
            isTodoActive={Boolean(isTodoActive)}
            isCompletedActive={Boolean(isCompletedActive)}
            isCalendarActive={Boolean(isCalendarActive)}
            onNavigate={handleSidebarNavigate}
          />
        )}
      </nav>
    );
  }

  // Mobile: Sheet-based sidebar with smooth slide animation
  return (
    <Sheet open={sidebarReady && showMenu} onOpenChange={setShowMenu}>
      <SheetContent
        side="left"
        className="w-[288px] max-w-[84vw] border-r border-sidebar-border/90 bg-sidebar/95 p-0"
        hideClose
      >
        <SheetTitle className="sr-only">Navigation</SheetTitle>
        <ExpandedSidebarContent
          isDesktop={isDesktop}
          todosCount={todos.length}
          completedCount={completedTodos.length}
          isTodoActive={Boolean(isTodoActive)}
          isCompletedActive={Boolean(isCompletedActive)}
          isCalendarActive={Boolean(isCalendarActive)}
          onNavigate={handleSidebarNavigate}
        />
      </SheetContent>
    </Sheet>
  );
};

export default SidebarContainer;

function ExpandedSidebarContent({
  isDesktop,
  todosCount,
  completedCount,
  isTodoActive,
  isCompletedActive,
  isCalendarActive,
  onNavigate,
}: {
  isDesktop: boolean;
  todosCount: number;
  completedCount: number;
  isTodoActive: boolean;
  isCompletedActive: boolean;
  isCalendarActive: boolean;
  onNavigate?: () => void;
}) {
  const { setShowMenu, setActiveMenu } = useMenu();

  return (
    <div className="flex min-w-0 flex-1 flex-col overflow-x-hidden">
      <div className="flex h-16 items-center border-b border-sidebar-border/80 px-3">
        {isDesktop ? (
          <button
            type="button"
            onClick={() => setShowMenu(false)}
            className="flex min-w-0 w-full items-center gap-3 transition-opacity hover:opacity-90"
            aria-label="Collapse sidebar"
          >
            <span className={railIconSlot}>
              <Image
                src="/tday-icon.svg"
                alt="Tday"
                width={36}
                height={36}
              />
            </span>
            <span className="min-w-0 truncate text-xl font-bold text-sidebar-foreground">
              Tday
            </span>
          </button>
        ) : (
          <Link
            href="/app/tday"
            onClick={() => {
              setActiveMenu({ name: "Todo" });
              onNavigate?.();
            }}
            className="flex min-w-0 w-full items-center gap-3 transition-opacity hover:opacity-90"
          >
            <span className={railIconSlot}>
              <Image
                src="/tday-icon.svg"
                alt="Tday"
                width={36}
                height={36}
              />
            </span>
            <span className="min-w-0 truncate text-xl font-bold text-sidebar-foreground">
              Tday
            </span>
          </Link>
        )}
      </div>

      <div className="px-3 pb-2 pt-4">
        <Link
          href="/app/add-task"
          onClick={() => {
            setActiveMenu({ name: "AddTask" });
            onNavigate?.();
          }}
          className={cn(
            "group relative flex h-12 w-full items-center gap-0 overflow-hidden rounded-2xl border-2 border-dashed border-accent/40 bg-accent/5 pl-0 pr-4 text-base font-medium text-accent transition-colors duration-500 hover:border-solid hover:border-accent hover:bg-accent hover:text-accent-foreground hover:shadow-lg hover:shadow-accent/20 active:scale-[0.98]",
          )}
          aria-label="Add a task"
        >
          <span className="flex h-12 w-12 shrink-0 items-center justify-center">
            <Plus className="h-5 w-5 shrink-0" />
          </span>
          <span className="truncate whitespace-nowrap">Add a task</span>
        </Link>
      </div>

      <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
        <div className="space-y-1 px-3 py-2">
          <Link
            href="/app/tday"
            prefetch
            onClick={() => {
              setActiveMenu({ name: "Todo" });
              onNavigate?.();
            }}
            className={cn(
              expandedNavButtonBase,
              isTodoActive
                ? expandedNavButtonActive
                : expandedNavButtonIdle,
            )}
            aria-current={isTodoActive ? "page" : undefined}
          >
            <span className={railIconSlot}>
              <Sun className={railIconClass} />
            </span>
            <span className="truncate whitespace-nowrap">Today</span>
            <span
              className={cn(
                countBadgeBase,
                isTodoActive
                  ? "text-sidebar-accent-foreground/80"
                  : "text-sidebar-foreground/40",
              )}
            >
              {todosCount}
            </span>
          </Link>

          <Link
            href="/app/completed"
            prefetch
            onClick={() => {
              setActiveMenu({ name: "Completed" });
              onNavigate?.();
            }}
            className={cn(
              expandedNavButtonBase,
              isCompletedActive
                ? expandedNavButtonActive
                : expandedNavButtonIdle,
            )}
            aria-current={isCompletedActive ? "page" : undefined}
          >
            <span className={railIconSlot}>
              <CheckCircleIcon className={railIconClass} />
            </span>
            <span className="truncate whitespace-nowrap">Completed</span>
            <span
              className={cn(
                countBadgeBase,
                isCompletedActive
                  ? "text-sidebar-accent-foreground/80"
                  : "text-sidebar-foreground/40",
              )}
            >
              {completedCount}
            </span>
          </Link>

          <Link
            href="/app/calendar"
            onClick={() => {
              setActiveMenu({ name: "Calendar" });
              onNavigate?.();
            }}
            className={cn(
              expandedNavButtonBase,
              isCalendarActive
                ? expandedNavButtonActive
                : expandedNavButtonIdle,
            )}
            aria-current={isCalendarActive ? "page" : undefined}
          >
            <span className={railIconSlot}>
              <Calendar1Icon className={railIconClass} />
            </span>
            <span className="truncate whitespace-nowrap">Calendar</span>
          </Link>
        </div>

        <LineSeparator className="mx-3 my-0 border-sidebar-border/80" />
        <div className="scrollbar-none min-h-0 flex-1 overflow-y-auto overflow-x-hidden text-muted-foreground">
          <TagSidebarSection mode="expanded" onNavigate={onNavigate} />
        </div>

        <div className="border-t border-sidebar-border/80 p-3">
          <UserCard onNavigate={onNavigate} />
        </div>
      </div>
    </div>
  );
}

function CollapsedSidebarContent({ onNavigate }: { onNavigate?: () => void }) {
  const { setActiveMenu, setShowMenu } = useMenu();
  const pathname = usePathname();
  const isTodoActive = pathname?.includes("/app/tday") || pathname?.includes("/app/todo");
  const isCompletedActive = pathname?.includes("/app/completed");
  const isCalendarActive = pathname?.includes("/app/calendar");

  return (
    <div className="flex min-w-0 flex-1 flex-col overflow-x-hidden">
      <div className="flex h-16 items-center border-b border-sidebar-border/80 px-3">
        <Tooltip>
          <TooltipTrigger asChild>
            <button
              type="button"
              onClick={() => setShowMenu(true)}
              className="flex h-10 w-10 items-center justify-center rounded-xl transition-opacity hover:opacity-90"
              aria-label="Expand sidebar"
            >
              <span
                className={cn(
                  railIconSlot,
                  "rounded-xl border border-border/60 bg-card/90 shadow-sm",
                )}
              >
              <Image
                src="/tday-icon.svg"
                alt="Tday"
                width={36}
                height={36}
                className="h-9 w-9 shrink-0 rounded-md object-cover"
              />
              </span>
            </button>
          </TooltipTrigger>
          <TooltipContent side="right" sideOffset={10}>
            Expand sidebar
          </TooltipContent>
        </Tooltip>
      </div>

      <div className="px-3 pb-2 pt-4">
        <Tooltip>
          <TooltipTrigger asChild>
            <Link
              href="/app/add-task"
              onClick={() => {
                setActiveMenu({ name: "AddTask" });
                onNavigate?.();
              }}
              className={cn(
                "group flex h-12 w-12 items-center justify-center rounded-2xl border-2 border-dashed border-accent/40 bg-accent/5 text-accent transition-colors duration-500 hover:border-solid hover:border-accent hover:bg-accent hover:text-accent-foreground hover:shadow-lg hover:shadow-accent/20 active:scale-95",
              )}
              aria-label="Add a task"
            >
              <Plus className="h-5 w-5" />
            </Link>
          </TooltipTrigger>
          <TooltipContent side="right" sideOffset={10}>
            Add a task
          </TooltipContent>
        </Tooltip>
      </div>

      <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
        <div className="space-y-1 px-3 py-2">
          <Tooltip>
            <TooltipTrigger asChild>
              <Link
                href="/app/tday"
                onClick={() => {
                  setActiveMenu({ name: "Todo" });
                  onNavigate?.();
                }}
                className={cn(
                  collapsedRailButtonBase,
                  isTodoActive && expandedNavButtonActive,
                )}
                aria-label="Today"
              >
                <Sun className={railIconClass} />
              </Link>
            </TooltipTrigger>
            <TooltipContent side="right" sideOffset={10}>
              Today
            </TooltipContent>
          </Tooltip>

          <Tooltip>
            <TooltipTrigger asChild>
              <Link
                href="/app/completed"
                onClick={() => {
                  setActiveMenu({ name: "Completed" });
                  onNavigate?.();
                }}
                className={cn(
                  collapsedRailButtonBase,
                  isCompletedActive && expandedNavButtonActive,
                )}
                aria-label="Completed"
              >
                <CheckCircleIcon className={railIconClass} />
              </Link>
            </TooltipTrigger>
            <TooltipContent side="right" sideOffset={10}>
              Completed
            </TooltipContent>
          </Tooltip>

          <Tooltip>
            <TooltipTrigger asChild>
              <Link
                href="/app/calendar"
                onClick={() => {
                  setActiveMenu({ name: "Calendar" });
                  onNavigate?.();
                }}
                className={cn(
                  collapsedRailButtonBase,
                  isCalendarActive && expandedNavButtonActive,
                )}
                aria-label="Calendar"
              >
                <Calendar1Icon className={railIconClass} />
              </Link>
            </TooltipTrigger>
            <TooltipContent side="right" sideOffset={10}>
              Calendar
            </TooltipContent>
          </Tooltip>
        </div>

        <LineSeparator className="mx-3 my-0 border-sidebar-border/80" />
        <div className="scrollbar-none min-h-0 flex-1 overflow-y-auto">
          <TagSidebarSection mode="collapsed" onNavigate={onNavigate} />
        </div>
      </div>

      <div className="border-t border-sidebar-border/80 p-3">
        <UserCard collapsed onNavigate={onNavigate} />
      </div>
    </div>
  );
}
