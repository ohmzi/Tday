export type ListColor =
  | "RED" | "ORANGE" | "YELLOW" | "LIME" | "BLUE" | "PURPLE"
  | "PINK" | "TEAL" | "CORAL" | "GOLD" | "DEEP_BLUE" | "ROSE"
  | "LIGHT_RED" | "BRICK" | "SLATE";

export interface ListItemMetaType {
  id: string;
  name: string;
  color?: ListColor;
  iconKey?: string | null;
  todoCount?: number;
}

export type ListItemMetaMapType = {
  [id: string]: Omit<ListItemMetaType, "id">;
};

export interface TodoItemType {
  id: string;
  title: string;
  description: string | null;
  pinned: boolean;
  createdAt: Date;
  order: number;
  priority: "Low" | "Medium" | "High";
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
  overriddenDue: Date | null;
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
  due: Date;
  userID: string;
  rrule: string | null;
  instanceDate: Date | null;
  listName?: string;
  listColor?: string;
}
