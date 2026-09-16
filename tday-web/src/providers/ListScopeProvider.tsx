"use client";

import { createContext, useContext } from "react";

/**
 * The list a screen is already showing, for the rows inside it.
 *
 * A context rather than a prop, because the thing being carried is a property of the
 * SCREEN and the rows that need it are four levels down a tree shared with screens
 * that have no answer: `TimelineSections` → `TimelineSectionDroppable` →
 * `DraggableTodoItem` → `TodoItemCard` is the same path on All, Priority, Scheduled
 * and one list's detail, and only the last of those is scoped. Threading a prop
 * through it would add a parameter to three shared components so that three of the
 * four callers could pass null, and every future row surface would inherit the same
 * obligation — with a default of "not scoped" that is silently right for the mixed
 * feeds and silently wrong for any new detail screen that forgot it.
 *
 * Defaults to null, so a row rendered outside a provider behaves exactly as it always
 * has. That is the same bargain `TaskSelectionProvider` and `TodoMutationProvider`
 * already make with these rows, and it is the safe direction to fail in: without a
 * provider the mark is DRAWN, which is redundant at worst, where the other default
 * would hide a row's only statement of where it lives.
 *
 * Deliberately NOT folded into `TaskSelectionProvider`, which already accepts a
 * `scopeListId`. That one exists because a bulk MOVE needs to know where rows came
 * from, it is mounted on exactly one of the two detail screens, and it is absent from
 * every Anytime screen — so reusing it would make the mark depend on whether the
 * screen happens to offer multi-select, which is not a relationship anyone would want
 * to maintain.
 */
const ListScopeContext = createContext<string | null>(null);

/** The scoped list id, or null on a mixed feed. */
export function useScopedListId(): string | null {
  return useContext(ListScopeContext);
}

export default function ListScopeProvider({
  listId,
  children,
}: {
  listId: string | null;
  children: React.ReactNode;
}) {
  // No memo: the value is a primitive, so context compares it by value and an
  // unchanged id cannot re-render the rows no matter how often this re-renders.
  const value = listId?.trim() || null;
  return (
    <ListScopeContext.Provider value={value}>{children}</ListScopeContext.Provider>
  );
}
