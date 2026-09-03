import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSearchParams } from "react-router-dom";
import { cn } from "@/lib/utils";
import { nativeAppScrollAttribute } from "@/components/app/nativeAppLayout";
import { hapticTick } from "@/lib/haptics";
import { useCompletedTodo } from "../query/get-completedTodo";
import { useCompletedFloater } from "../query/get-completedFloater";
import CompletedTodoContainer from "./CompletedTodoContainer";
import CompletedFloaterContainer from "./CompletedFloaterContainer";

type CompletedScope = "tasks" | "floater";

function scrollCompletedToTop() {
  document
    .querySelector<HTMLElement>(`[${nativeAppScrollAttribute}]`)
    ?.scrollTo({ top: 0, behavior: "smooth" });
}

/**
 * The Completed screen's outer shell: switches between the durable Todo and
 * Floater completion histories, which stay two fully independent screens
 * (their own header/search/empty-states) rather than one component branching
 * internally — see CompletedTodoContainer/CompletedFloaterContainer.
 *
 * `?scope=floater` opens straight into the Floater tab — the deep link the
 * Floater dashboard's "Completed" entry point uses, since Floater has no
 * sidebar/nav path to this screen otherwise.
 */
export default function CompletedContainer() {
  const [searchParams] = useSearchParams();
  const [scope, setScope] = useState<CompletedScope>(
    searchParams.get("scope") === "floater" ? "floater" : "tasks",
  );
  const { t: completedDict } = useTranslation("completed");
  const { t: appDict } = useTranslation("app");
  const { completedTodos } = useCompletedTodo();
  const { completedFloaters } = useCompletedFloater();

  const tabs = useMemo(
    () =>
      [
        { id: "tasks" as const, label: completedDict("tabTasks"), count: completedTodos.length },
        { id: "floater" as const, label: appDict("floater"), count: completedFloaters.length },
      ],
    [appDict, completedDict, completedFloaters.length, completedTodos.length],
  );
  const activeIndex = Math.max(0, tabs.findIndex((tab) => tab.id === scope));

  const tabSwitcher = (
    <div
      role="tablist"
      aria-label={completedDict("title")}
      className="relative mx-auto mt-4 flex h-11 w-full max-w-xs rounded-2xl bg-muted/60 p-1"
    >
      <span
        aria-hidden
        className="absolute bottom-1 top-1 w-[calc(50%-0.25rem)] rounded-xl bg-card shadow-sm transition-transform duration-200 ease-out"
        style={{ transform: `translateX(${activeIndex * 100}%)` }}
      />
      {tabs.map((tab, index) => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          aria-selected={index === activeIndex}
          onClick={() => {
            if (tab.id === scope) return;
            hapticTick();
            setScope(tab.id);
            scrollCompletedToTop();
          }}
          className={cn(
            "relative z-10 flex flex-1 items-center justify-center gap-1.5 rounded-xl text-sm font-black transition-colors duration-200",
            index === activeIndex ? "text-foreground" : "text-muted-foreground",
          )}
        >
          <span>{tab.label}</span>
          <span className="text-xs font-black opacity-60">{tab.count}</span>
        </button>
      ))}
    </div>
  );

  return scope === "tasks" ? (
    <CompletedTodoContainer tabSwitcher={tabSwitcher} />
  ) : (
    <CompletedFloaterContainer tabSwitcher={tabSwitcher} />
  );
}
