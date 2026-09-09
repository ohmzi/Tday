import React from "react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Flag } from "lucide-react";
import { TodoItemType } from "@/types";
import { ChevronDown } from "lucide-react";
import { useTranslation } from "react-i18next";
import { priorityFlagClasses } from "@/lib/priority";
import clsx from "clsx";

type PriorityDropdownMenuProps = {
  priority: TodoItemType["priority"];
  setPriority: React.Dispatch<
    React.SetStateAction<TodoItemType["priority"]>
  >;
};

const PRIORITIES = ["Lowest", "Low", "Medium", "High"] as const;

const PriorityDropdownMenu = ({
  priority,
  setPriority,
}: PriorityDropdownMenuProps) => {
  const { t: appDict } = useTranslation("app");
  const triggerColor = priorityFlagClasses(priority);

  return (
    <DropdownMenu modal={true}>
      <DropdownMenuTrigger className="cursor-pointer bg-popover border p-2 text-sm flex justify-center items-center gap-2 hover:bg-popover-border rounded-md hover:text-foreground">
        <Flag className={clsx("w-4 h-4", triggerColor.text, triggerColor.fill)} />
        <p className="hidden sm:block">{appDict("priority")}</p>
        <ChevronDown className="w-4 h-4 text-muted-foreground" />
      </DropdownMenuTrigger>
      <DropdownMenuContent className="min-w-[150px] text-foreground space-y-1">
        {PRIORITIES.map((value) => {
          const { text, fill } = priorityFlagClasses(value);
          return (
            <DropdownMenuItem
              key={value}
              className="hover:text-foreground hover:bg-popover-accent"
              onClick={() => setPriority(value)}
            >
              <Flag className={clsx("w-4 h-4", text, priority === value && fill)} />
            </DropdownMenuItem>
          );
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  );
};

export default PriorityDropdownMenu;
