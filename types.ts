import { Prisma } from "@prisma/client";
import type { ListColorType } from "@/schema";

export type ListColor = ListColorType;

export interface RegisterFormProp {
  fname: string;
  lname?: string;
  email: string;
  password: string;
}

export interface LoginFormProp {
  email: string;
  password: string;
}

export interface NoteItemType {
  id: string;
  name: string;
  content?: string;
  createdAt: Date;
}

export interface ListItemType {
  id: string;
  name: string;
  color?: ListColor;
  todos: TodoItemType[];
  createdAt: Date;
  updatedAt: Date;
}

export type ListItemMetaType = Pick<ListItemType, "id" | "color" | "name"> & {
  todoCount?: number;
};

export type ListItemMetaMapType = {
  [id: string]: Omit<ListItemMetaType, "id">;
};

export type NonNullableDateRange = {
  from: Date;
  to: Date;
};

export type User = Prisma.UserGetPayload<{
  include: {
    accounts: true;
    Todos: true;
    CompletedTodo: true;
    Note: true;
    File: true;
  };
}>;

export interface TodoItemType {
  id: string;
  title: string;
  description: string | null;
  pinned: boolean;
  createdAt: Date;
  order: number;
  priority: "Low" | "Medium" | "High";
  dtstart: Date;
  durationMinutes: number;
  due: Date;
  rrule: string | null;
  timeZone: string;
  userID: string;
  completed: boolean;
  exdates: Date[];
  instances?: overridingInstance[] | null;
  instanceDate?: Date | null;
  listID?: string | null;
}

export interface overridingInstance {
  id: string;
  completedAt: Date | null;
  todoId: string;
  recurId: string;
  instanceDate: Date;
  overriddenTitle: string | null;
  overriddenDescription: string | null;
  overriddenDtstart: Date | null;
  overriddenDue: Date | null;
  overriddenDurationMinutes: number | null;
  overriddenPriority: "Low" | "Medium" | "High" | null;
}

export interface recurringTodoItemType extends TodoItemType {
  rrule: string;
  instances: overridingInstance[];
}

export interface CompletedTodoItemType {
  id: string;
  originalTodoID: string | null;
  title: string;
  description?: string;
  createdAt: Date;
  completedAt: Date;
  priority: "Low" | "Medium" | "High";
  dtstart: Date;
  due: Date;
  userID: string;
  rrule: string | null;
  instanceDate: Date | null;
  listName?: string;
  listColor?: string;
}
