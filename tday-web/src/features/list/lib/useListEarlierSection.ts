import { useMemo } from "react";
import {
  buildTimelineSections,
  hasNonEarlierTimelineTodos,
} from "@/lib/timeline/buildTimelineSections";
import type { TodoItemType } from "@/types";

/**
 * `ListContainer`'s date-bucketed timeline sections (built from the
 * search-filtered todos), plus BOTH readings of the Earlier/current split the
 * screen needs downstream — deliberately kept as two distinct booleans, never
 * merged into one:
 *
 * - `hasNonEarlierListTodos`: search-REACTIVE, built from `filteredTodos` /
 *   `timelineSections` themselves. Feeds the visible illustration/timeline
 *   gating (`useListEmptyState`'s `showEmpty`), which legitimately narrows
 *   with the search box like every other derivation on this screen.
 * - `hasNonEarlierRawListTodos`: search-IMMUNE, built from the RAW
 *   `listTodos` via `hasNonEarlierTimelineTodos` (see that function's own doc
 *   comment). Feeds ONLY the remote-completion celebration signal in
 *   `useListEmptyState` — a plain previous-vs-current ref comparison with no
 *   notion of *why* its input changed, so it must never see a count a search
 *   query can move on its own.
 *
 * Collapsing these two back into a single signal reintroduces the exact
 * regression fixed in "fix(web): keep List remote-completion celebration
 * immune to search" (PR #160): a non-matching search could fake or swallow
 * the empty transition the celebration hook watches for.
 */
export function useListEarlierSection({
  listTodos,
  filteredTodos,
  locale,
  timeZone,
  todayLabel,
  tomorrowLabel,
  earlierLabel,
  dragActive,
}: {
  listTodos: TodoItemType[];
  filteredTodos: TodoItemType[];
  locale: string;
  timeZone?: string;
  todayLabel: string;
  tomorrowLabel: string;
  earlierLabel: string;
  dragActive: boolean;
}) {
  const timelineSections = useMemo(
    () =>
      buildTimelineSections({
        todos: filteredTodos,
        locale,
        timeZone,
        futureOnly: false,
        placesEarlierBeforeToday: true,
        includeEmptyDropTargets: dragActive,
        todayLabel,
        tomorrowLabel,
        earlierLabel,
      }),
    [dragActive, filteredTodos, locale, timeZone, todayLabel, tomorrowLabel, earlierLabel],
  );

  // The same "earlier" bucket `TimelineSections` renders below — reusing
  // `buildTimelineSections`'s own classification rather than a second dayKey
  // comparison. Every other dated todo `buildTimelineSections` places is
  // "current" by construction, whether or not this list opened with a due
  // date on every row.
  const earlierSection = useMemo(
    () => timelineSections.find((section) => section.kind === "earlier") ?? null,
    [timelineSections],
  );
  const hasEarlierItems = Boolean(earlierSection && earlierSection.todos.length > 0);
  const nonEarlierTodoCount = filteredTodos.length - (earlierSection?.todos.length ?? 0);
  const hasNonEarlierListTodos = nonEarlierTodoCount > 0;

  const hasNonEarlierRawListTodos = useMemo(
    () =>
      hasNonEarlierTimelineTodos({
        todos: listTodos,
        locale,
        timeZone,
        futureOnly: false,
        placesEarlierBeforeToday: true,
        includeEmptyDropTargets: false,
        todayLabel,
        tomorrowLabel,
        earlierLabel,
      }),
    [listTodos, locale, timeZone, todayLabel, tomorrowLabel, earlierLabel],
  );

  return {
    timelineSections,
    hasEarlierItems,
    hasNonEarlierListTodos,
    hasNonEarlierRawListTodos,
  };
}
