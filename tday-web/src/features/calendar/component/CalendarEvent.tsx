import { TodoItemType } from "@/types";
import React, { useState, lazy, Suspense } from "react";
import { EventProps } from "react-big-calendar";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { format } from "date-fns";
import { AlignCenterIcon } from "lucide-react";
import clsx from "clsx";
import { Pen, Trash, X } from "lucide-react";
import LineSeparator from "@/components/ui/lineSeparator";
import CompleteButton from "./CompleteButton";
import { Button } from "@/components/ui/button";
import EditCalendarFormContainer from "./CalendarForm/EditFormContainer";

const ConfirmDelete = lazy(() => import("./ConfirmationModals/ConfirmDelete"));
const ConfirmDeleteAll = lazy(() => import("./ConfirmationModals/ConfirmDeleteAll"));

const formatDueTime = (due: Date) => format(due, "MMM dd hh:mm");

const CalendarEvent = ({ event: todo }: EventProps<TodoItemType>) => {
  const [open, setOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deleteAllDialogOpen, setDeleteAllDialogOpen] = useState(false);
  const [displayForm, setDisplayForm] = useState(false);

  return (
    <>
      {/* ----------------- Event Form popover ----------- */}
      {displayForm && (
        <EditCalendarFormContainer
          todo={todo}
          displayForm={displayForm}
          setDisplayForm={setDisplayForm}
        />
      )}
      {/* ---------------- Event info popover ------------- */}
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <div
            className="z-50! h-full w-full cursor-pointer text-white"
            title={todo.title}
            onContextMenu={(e) => {
              e.preventDefault();
              setOpen(true);
            }}

          >
            <p className="max-w-full truncate text-xs font-black sm:max-w-xs md:max-w-sm md:text-sm lg:max-w-md xl:max-w-lg 2xl:max-w-xl">
              {todo.title}
            </p>
          </div>
        </PopoverTrigger>

        <PopoverContent className="w-screen rounded-[24px] border border-white/70 bg-popover p-0 shadow-[0_24px_60px_-30px_hsl(var(--shadow)/0.72)] dark:border-white/10 sm:w-120 md:w-100 lg:w-120" onMouseDown={(e) => e.stopPropagation()}>
          {/* Header */}
          <div className="flex gap-0 md:gap-2 p-2 justify-end ">
            {/* EDIT */}
            <Button
              variant={"ghost"}
              size={"icon"}
              className="rounded-full p-2 text-muted-foreground hover:bg-popover-accent hover:text-foreground"
              onClick={() => {
                setOpen(false);
                setDisplayForm(true);
              }}
            >
              <Pen className="w-3 h-3 sm:h-4 sm:w-4" />
            </Button>

            {/* DELETE  */}
            <Button
              variant={"destructive"}
              size={"icon"}
              className="rounded-full bg-popover p-2 text-muted-foreground hover:bg-popover-accent hover:text-foreground"
              onClick={() => {
                setOpen(false);
                if (todo.rrule) {
                  setDeleteAllDialogOpen(true);
                } else {
                  setDeleteDialogOpen(true);
                }
              }}
            >
              <Trash className="w-3 h-3 sm:h-4 sm:w-4" />
            </Button>

            {/* Close */}
            <Button
              variant={"destructive"}
              size={"icon"}
              className="rounded-full bg-popover p-2 text-muted-foreground hover:bg-popover-accent hover:text-foreground"
              onClick={() => setOpen(false)}
            >
              <X className="w-4 h-4 sm:h-5 sm:w-5" />
            </Button>
          </div>

          {/* TODO information */}
          <div className="flex flex-col gap-4 text-sm px-3 pt-1 pb-6 sm:px-6 md:px-8">
            <div className="flex items-start gap-4">
              <div
                className={clsx(
                  "min-w-4 min-h-4 sm:min-w-5 sm:min-h-5 md:min-w-6 md:min-h-6 rounded-sm mt-1",
                  todo.priority === "Low"
                    ? "bg-lime"
                    : todo.priority === "Medium"
                      ? "bg-orange"
                      : "bg-red",
                )}
              />
              <div className="min-w-0">
                <p className="text-md w-full truncate font-black leading-none md:text-lg">
                  {todo.title}
                </p>
                <p className="text-[0.6rem] font-extrabold text-muted-foreground sm:text-xs md:text-sm">
                  {formatDueTime(todo.due)}
                </p>
              </div>
            </div>

            {todo.description && (
              <div className="flex gap-2 sm:gap-3 md:gap-4 items-start min-w-0">
                <AlignCenterIcon className="w-4 h-4 text-muted-foreground shrink-0 flex-0" />
                <p className="line-clamp-3 text-[0.7rem] font-extrabold text-muted-foreground md:text-sm">
                  {todo.description}
                </p>
              </div>
            )}
          </div>
          <LineSeparator />
          <CompleteButton todoItem={todo} />
        </PopoverContent>
      </Popover>

      {/* ---------------- CONFIRM DELETE DIALOG ---------------- */}
      <Suspense fallback={null}>
        <ConfirmDelete
          todo={todo}
          deleteDialogOpen={deleteDialogOpen}
          setDeleteDialogOpen={setDeleteDialogOpen}
        />
      </Suspense>
      <Suspense fallback={null}>
        <ConfirmDeleteAll
          todo={todo}
          deleteAllDialogOpen={deleteAllDialogOpen}
          setDeleteAllDialogOpen={setDeleteAllDialogOpen}
        />
      </Suspense>
    </>
  );
};

export default CalendarEvent;
