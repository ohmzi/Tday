import { format } from "date-fns";
import type { TFunction } from "i18next";
import { getDateFnsLocale } from "@/lib/date/dateFnsLocale";
import { flattenNotesToPlainText } from "@/lib/richNotes";

// Minimal shape shared by TodoItemType and FloaterItemType so both scheduled
// lists and floater lists export through the same builder.
export interface ShareableTodo {
  title: string;
  description?: string | null;
  completed: boolean;
  due?: Date | null;
}

// Minimal shape for a single task's plain-text export (swipe-to-copy, and
// eventually a single-task share sheet). Title, notes and due — the task as it
// reads, without the priority flag: copying a task copies the task, and the
// urgency tier is a list-view marking rather than part of what the task says.
export interface ShareableSingleTodo {
  title: string;
  description?: string | null;
  due?: Date | null;
}

// Canonical plain-text export of a single task — title + flattened notes + due.
// Mirrors iOS's ShareSheet.taskShareText and Android's ShareUtils.taskCopyText
// so a task copied from any platform reads the same.
export function buildTaskShareText({
  todo,
  lang,
  t,
}: {
  todo: ShareableSingleTodo;
  lang: string;
  t: TFunction;
}): string {
  const locale = getDateFnsLocale(lang);
  const lines: string[] = [todo.title];
  const notes = flattenNotesToPlainText(todo.description).trim();
  if (notes) {
    lines.push(notes);
  }
  if (todo.due) {
    lines.push(t("shareDueLabel", { date: format(todo.due, "PPp", { locale }) }));
  }
  return lines.join("\n");
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
    const notes = flattenNotesToPlainText(todo.description).trim();
    if (notes) {
      notes.split("\n").forEach((line) => lines.push(`   ${line}`));
    }
  }
  lines.push("");
  lines.push(t("shareTaskCount", { count: todos.length }));
  return lines.join("\n");
}
