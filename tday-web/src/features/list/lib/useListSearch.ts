import { useMemo } from "react";
import { flattenNotesToPlainText } from "@/lib/richNotes";
import type { TodoItemType } from "@/types";

/**
 * `ListContainer`'s search box: filters this list's raw todos down to the
 * ones whose title or (plain-text-flattened) description contains the query,
 * plus the derived `isSearching` flag every other derivation on the screen
 * gates on. An empty/blank query is a no-op — the identity `listTodos` array
 * comes back so referential equality still holds for callers that memoize on
 * it.
 */
export function useListSearch({
  listTodos,
  searchQuery,
}: {
  listTodos: TodoItemType[];
  searchQuery: string;
}) {
  const filteredTodos = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return listTodos;
    return listTodos.filter((todo) => {
      const title = todo.title.toLowerCase();
      const description = flattenNotesToPlainText(todo.description).toLowerCase();
      return title.includes(query) || description.includes(query);
    });
  }, [listTodos, searchQuery]);

  const isSearching = Boolean(searchQuery.trim());

  return { filteredTodos, isSearching };
}
