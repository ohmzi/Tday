import {
  TodoItemType,
  overridingInstance,
  recurringTodoItemType,
} from "@/types";

/**
 * given a single todo parent and a instance, merges them by overriding some parent information with the instance
 * @param instance the todo instance that has inforamtion which overrides parent
 * @param parent the todo to be overriden
 * @returns a todoItemType todo with the overriden information for that instance
 */
export function mergeInstanceAndTodo(
  instance: overridingInstance,
  parent: recurringTodoItemType | TodoItemType,
): recurringTodoItemType | TodoItemType {
  const merged = structuredClone(parent);

  merged.completed = instance.completedAt ? true : false;

  if (instance.overriddenTitle) merged.title = instance.overriddenTitle;
  if (instance.overriddenDescription)
    merged.description = instance.overriddenDescription;
  if (instance.overriddenDue) merged.due = instance.overriddenDue;
  if (instance.overriddenPriority)
    merged.priority = instance.overriddenPriority;
  merged.instanceDate = instance.instanceDate;
  return merged;
}
