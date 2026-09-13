import { useState, type Dispatch, type SetStateAction } from "react";
import { RRule, type Options } from "rrule";
import type { TodoItemType } from "@/types";
import deriveRepeatType, { type RepeatType } from "@/lib/deriveRepeatType";

export type CalendarFormDateRange = { from: Date; to: Date };

/**
 * Everything the user has typed or picked into a calendar task form, plus the setters the
 * form body writes through. One object rather than twelve loose props, because this bundle
 * is threaded whole from the container down through whichever shell is currently mounted.
 */
export type CalendarTaskFormState = {
  title: string;
  setTitle: Dispatch<SetStateAction<string>>;
  description: string;
  setDescription: Dispatch<SetStateAction<string>>;
  priority: TodoItemType["priority"];
  setPriority: Dispatch<SetStateAction<TodoItemType["priority"]>>;
  dateRange: CalendarFormDateRange;
  setDateRange: Dispatch<SetStateAction<CalendarFormDateRange>>;
  rruleOptions: Partial<Options> | null;
  setRruleOptions: Dispatch<SetStateAction<Partial<Options> | null>>;
  listID: string | null;
  setListID: Dispatch<SetStateAction<string | null>>;
  derivedRepeatType: RepeatType;
};

/** The values the form opens with: an existing task's fields, or a slot's date for a new one. */
export type CalendarTaskFormSeed = {
  title?: string;
  description?: string | null;
  priority?: TodoItemType["priority"];
  due: Date;
  rrule?: string | null;
  listID?: string | null;
};

/**
 * Owns the draft of a calendar task form.
 *
 * This state used to live inside `CreateModal`/`CreateDrawer` and `EditModal`/`EditDrawer` —
 * four separate copies, one per shell. That put it *below* the 640 px `isDesktop` switch in
 * the form containers, and React treats a swap between two different component types as an
 * unmount plus a mount: crossing the breakpoint with the form open tore down the shell that
 * held the draft and built the other one from its initial values, so a half-written task
 * silently reset to blank. A phone rotating from portrait to landscape is enough to cross it.
 *
 * Calling this hook in the container instead puts the draft above the switch, where only the
 * presentation changes when the viewport crosses 640 px. The seed is read once, at mount, the
 * way a `useState` initial value always is — a later change to the underlying todo does not
 * overwrite what the user has typed, which is the same contract the four shells had.
 */
export function useCalendarTaskFormState(seed: CalendarTaskFormSeed): CalendarTaskFormState {
  const [title, setTitle] = useState(seed.title ?? "");
  const [description, setDescription] = useState(seed.description ?? "");
  const [priority, setPriority] = useState<TodoItemType["priority"]>(seed.priority ?? "Low");
  const [dateRange, setDateRange] = useState<CalendarFormDateRange>(() => ({
    from: seed.due,
    to: seed.due,
  }));
  const [rruleOptions, setRruleOptions] = useState<Partial<Options> | null>(() =>
    seed.rrule ? RRule.parseString(seed.rrule) : null,
  );
  const [listID, setListID] = useState<string | null>(seed.listID ?? null);

  return {
    title,
    setTitle,
    description,
    setDescription,
    priority,
    setPriority,
    dateRange,
    setDateRange,
    rruleOptions,
    setRruleOptions,
    listID,
    setListID,
    derivedRepeatType: deriveRepeatType({ rruleOptions }),
  };
}
