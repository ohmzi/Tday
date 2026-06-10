import { format } from "date-fns";
import type { TFunction } from "i18next";
import { getDateFnsLocale } from "@/lib/date/dateFnsLocale";

// Minimal shape shared by TodoItemType and FloaterItemType so both scheduled
// lists and floater lists export through the same builder.
export interface ShareableTodo {
  title: string;
  description?: string | null;
  completed: boolean;
  due?: Date | null;
}

// Canonical plain-text export of a list. Mirrors the Android
// ShareUtils.buildListShareText output so a shared list reads the same from
// every platform.
export function buildListShareText({
  listName,
  todos,
  lang,
  t,
}: {
  listName: string;
  todos: ShareableTodo[];
  lang: string;
  t: TFunction;
}): string {
  const locale = getDateFnsLocale(lang);
  const lines: string[] = [listName, "—".repeat(Math.min(listName.length, 20))];
  for (const todo of todos) {
    lines.push(`${todo.completed ? "✓" : "○"} ${todo.title}`);
    if (todo.due) {
      lines.push(`   ${t("shareDueLabel", { date: format(todo.due, "PPp", { locale }) })}`);
    }
    const notes = todo.description?.trim();
    if (notes) {
      lines.push(`   ${notes}`);
    }
  }
  lines.push("");
  lines.push(t("shareTaskCount", { count: todos.length }));
  return lines.join("\n");
}
