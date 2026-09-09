import React from "react";

import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Flag } from "lucide-react";
import { useTodoForm } from "@/providers/TodoFormProvider";
import { priorityFlagClasses } from "@/lib/priority";
import clsx from "clsx";
import { Button } from "@/components/ui/button";
import { useTranslation } from "react-i18next";

const PRIORITIES = ["Lowest", "Low", "Medium", "High"] as const;

const PriorityDropdownMenu = ({ }) => {
  const { t: appDict } = useTranslation("app");
  const { priority, setPriority } = useTodoForm();
  const itemClass =
    "flex justify-start items-center p-1.5 px-2 gap-2 rounded w-full hover:bg-popover-accent cursor-pointer m-auto text-sm";

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          variant={"outline"}
          className="w-fit h-fit p-2! cursor-pointer text-muted-foreground bg-inherit"
        >
          <Flag
            className={clsx(
              "w-4 h-4 transition-text duration-200 ease-out",
              priorityFlagClasses(priority).text,
            )}
          />
          <p>{appDict("priority")}</p>
        </Button>
      </PopoverTrigger>
      <PopoverContent className="min-w-38 text-foreground flex flex-col p-1 items-start justify-center">
        {PRIORITIES.map((value) => {
          const { text, fill } = priorityFlagClasses(value);
          return (
            <button
              key={value}
              className={itemClass}
              onClick={() => setPriority(value)}
            >
              <Flag className={clsx("w-4 h-4", text, priority === value && fill)} />
            </button>
          );
        })}
      </PopoverContent>
    </Popover>
  );
};

export default PriorityDropdownMenu;
