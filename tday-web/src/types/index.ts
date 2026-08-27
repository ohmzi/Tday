export type ListColor =
  | "RED" | "ORANGE" | "YELLOW" | "LIME" | "BLUE" | "PURPLE"
  | "PINK" | "TEAL" | "CORAL" | "GOLD" | "DEEP_BLUE" | "ROSE"
  | "LIGHT_RED" | "BRICK" | "SLATE";

export type ShareRoleType = "OWNER" | "EDITOR" | "VIEWER";

export interface ListMemberType {
  userId: string;
  username: string;
  name?: string | null;
  role: ShareRoleType;
  addedAt?: string | null;
}

export interface ListItemMetaType {
  id: string;
  name: string;
  color?: ListColor;
  iconKey?: string | null;
  todoCount?: number;
  myRole?: ShareRoleType | null;
  isShared?: boolean;
  memberCount?: number;
  ownerUsername?: string | null;
}

export type ListItemMetaMapType = {
  [id: string]: Omit<ListItemMetaType, "id">;
};

export interface FloaterListItemMetaType {
  id: string;
  name: string;
  color?: ListColor;
  iconKey?: string | null;
  reusable?: boolean;
  todoCount?: number;
  userID?: string | null;
  createdAt?: Date | null;
  updatedAt?: Date | null;
  myRole?: ShareRoleType | null;
  isShared?: boolean;
  memberCount?: number;
  ownerUsername?: string | null;
}

export type FloaterListItemMetaMapType = {
  [id: string]: Omit<FloaterListItemMetaType, "id">;
};

export interface FloaterItemType {
  id: string;
  title: string;
  description: string | null;
  pinned: boolean;
  createdAt: Date | null;
  updatedAt?: Date | null;
  order: number;
  priority: "Low" | "Medium" | "High";
  userID?: string | null;
  completed: boolean;
  listID?: string | null;
}

export interface TodoItemType {
  id: string;
  title: string;
  description: string | null;
  pinned: boolean;
  createdAt: Date;
  updatedAt?: Date | null;
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
  /**
   * True when the endpoint this row came from does not send `rrule` at all, so
   * `rrule: null` here means "not stated", not "does not repeat".
   *
   * Only a backend older than the release that added `ListTodoDto.rrule` can
   * produce it. Bulk selection treats such a row as if it repeats, which keeps
   * it out of delete / priority / move — the three actions that would hit a
   * whole series. Failing closed costs a skipped row; failing open costs the
   * series.
   */
  recurrenceUnknown?: boolean;
}

export interface TaskStepType {
  id: string;
  todoID: string;
  title: string;
  completed: boolean;
  position: number;
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

export interface TodoApiItemType extends Omit<TodoItemType, "due"> {
  due: Date | null;
}

export interface CompletedTodoItemType {
  id: string;
  originalTodoID: string | null;
  title: string;
  description?: string;
  createdAt: Date;
  completedAt: Date;
  priority: "Low" | "Medium" | "High";
  due: Date | null;
  userID: string;
  rrule: string | null;
  instanceDate: Date | null;
  listName?: string;
  listColor?: string;
}
