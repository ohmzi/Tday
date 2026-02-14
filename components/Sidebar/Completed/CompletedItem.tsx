import clsx from "clsx";
import React from "react";
import { useMenu } from "@/providers/MenuProvider";
import { Link } from "@/i18n/navigation";

import { useCompletedTodo } from "@/features/completed/query/get-completedTodo";
import useWindowSize from "@/hooks/useWindowSize";
import { CheckCircleIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useTranslations } from "next-intl";
const CompletedItem = () => {
  const sidebarDict = useTranslations("sidebar")

  const { width } = useWindowSize();
  const { activeMenu, setActiveMenu, setShowMenu } = useMenu();
  const { completedTodos } = useCompletedTodo();

  // Count only todos created today
  const completedTodoCount = completedTodos.length;

  return (
    <Button
      asChild
      variant={"ghost"}
      className={clsx(
        "h-10 w-full justify-start rounded-xl border border-transparent px-3 text-sm font-medium text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent/50 hover:text-sidebar-foreground",
        activeMenu.name === "Completed" &&
        "bg-sidebar-accent text-sidebar-accent-foreground",
      )}
    >
      <Link
        prefetch={true}
        href="/app/completed"
        className="flex h-full w-full items-center gap-3"
        onClick={() => {
          setActiveMenu({ name: "Completed" });
          if (width <= 1266) setShowMenu(false);
        }}
      >
        <CheckCircleIcon className="h-4 w-4 shrink-0" />
        <p className="truncate whitespace-nowrap text-foreground">{sidebarDict("completed")}</p>

        <span
          className={clsx(
            "ml-auto min-w-[24px] truncate rounded-full border border-border/60 bg-background/60 px-2 py-0.5 text-center text-xs font-medium",
            activeMenu.name === "Completed"
              ? "bg-background/80 text-foreground/75"
              : "text-muted-foreground",
          )}
        >
          {completedTodoCount}
        </span>
      </Link>
    </Button>
  );
};

export default CompletedItem;
