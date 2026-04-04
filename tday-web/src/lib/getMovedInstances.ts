import { TodoItemType } from "@/types";
import { mergeInstanceAndTodo } from "./mergeInstanceAndTodo";

// /**
//  * @description generate "orphaned todos" by finding instances that had their due overriden to another time
//  * @param mergedTodos a list of todos that are used to check for duplicates
//  * @param recurringParents a list of todos that has all the instances
//  * @param bounds a { dateRangeStart: Date; dateRangeEnd: Date } object
//  * @returns a list of orphaned todos

export function getMovedInstances(
  mergedTodos: TodoItemType[],
  recurringParents: TodoItemType[],
  bounds: { dateRangeStart: Date; dateRangeEnd: Date },
): TodoItemType[] {
  const mergedKeys = mergedTodos.map(
    (merged) => merged.due.getTime() + " " + merged.instanceDate?.getTime(),
  );
  const orphanedInstances = recurringParents.flatMap((todo: TodoItemType) => {
    if (!todo.instances) return [];

    return todo.instances.filter(
      ({ overriddenDue, instanceDate }) => {
        const exDateList = todo.exdates.map((exdate) => {
          return exdate.getTime();
        });
        return (
          overriddenDue &&
          !exDateList.includes(instanceDate.getTime()) &&
          overriddenDue <= bounds.dateRangeEnd &&
          overriddenDue >= bounds.dateRangeStart &&
          !mergedKeys.includes(
            overriddenDue.getTime() + " " + instanceDate.getTime(),
          )
        );
      },
    );
  });

  const orphanedTodos = orphanedInstances.flatMap((instance) => {
    const parentTodo = recurringParents.find(
      (parent) => parent.id === instance.todoId,
    );
    if (parentTodo) return mergeInstanceAndTodo(instance, parentTodo);
    return [];
  });
  return orphanedTodos;
}
