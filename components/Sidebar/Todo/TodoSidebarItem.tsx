import clsx from "clsx";
import React from "react";
import { useMenu } from "@/providers/MenuProvider";
import { Link } from "@/i18n/navigation";

import { useTodo } from "@/features/todayTodos/query/get-todo";
import useWindowSize from "@/hooks/useWindowSize";
import { Sun } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useTranslations } from "next-intl";
const TodoItem = () => {
  const appDict = useTranslations("app")

  const { width } = useWindowSize();
  const { activeMenu, setActiveMenu, setShowMenu } = useMenu();
  const { todos } = useTodo();
  const todayTodoCount = todos.length;

  return (
    <Button
      asChild
      variant={"ghost"}
      className={clsx(
        "h-10 w-full justify-start rounded-xl border border-transparent px-3 text-sm font-medium text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent/50 hover:text-sidebar-foreground",
        activeMenu.name === "Todo" &&
        "bg-sidebar-accent text-sidebar-accent-foreground",
      )}
    >
      <Link
        prefetch={true}
        href="/app/tday"
        className="flex h-full w-full items-center gap-3"
        onClick={() => {
          setActiveMenu({ name: "Todo" });
          if (width <= 1266) setShowMenu(false);
        }}
      >
        <Sun className="h-4 w-4 shrink-0" />
        <p className="truncate whitespace-nowrap text-foreground">{appDict("today")}</p>

        <span
          className={clsx(
            "ml-auto min-w-[24px] truncate rounded-full border border-border/60 bg-background/60 px-2 py-0.5 text-center text-xs font-medium",
            activeMenu.name === "Todo"
              ? "bg-background/80 text-foreground/75"
              : "text-muted-foreground",
          )}
        >
          {todayTodoCount}
        </span>
      </Link>
    </Button>
  );
};

export default TodoItem;
