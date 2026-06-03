import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import TodoMutationProvider from "@/providers/TodoMutationProvider";
import TaskFormSheet from "@/components/todo/component/TodoForm/TaskFormSheet";
import { useCompleteTodo } from "@/features/todayTodos/query/complete-todo";
import { useDeleteTodo } from "@/features/todayTodos/query/delete-todo";
import { usePrioritizeTodo } from "@/features/todayTodos/query/prioritize-todo";
import { useReorderTodo } from "@/features/todayTodos/query/reorder-todo";
import { useEditTodo } from "@/features/todayTodos/query/update-todo";
import { useEditTodoInstance } from "@/features/todayTodos/query/update-todo-instance";

type CreateTaskOverrides = { listID?: string };

type CreateTaskContextValue = {
  openCreateTask: (overrideFields?: CreateTaskOverrides) => void;
};

const CreateTaskContext = createContext<CreateTaskContextValue | null>(null);

/**
 * Hosts a single "new task" bottom sheet for the whole app so the FAB (and any
 * other entry point) can pop it open in place instead of routing to a dedicated
 * add-task screen. The sheet's drawer unmounts on close, so each open remounts
 * the form with fresh overrideFields.
 */
export default function CreateTaskProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const [overrideFields, setOverrideFields] = useState<
    CreateTaskOverrides | undefined
  >(undefined);

  const openCreateTask = useCallback((fields?: CreateTaskOverrides) => {
    setOverrideFields(fields);
    setOpen(true);
  }, []);

  const value = useMemo(() => ({ openCreateTask }), [openCreateTask]);

  return (
    <CreateTaskContext.Provider value={value}>
      {children}
      <TodoMutationProvider
        useCompleteTodo={useCompleteTodo}
        useDeleteTodo={useDeleteTodo}
        useEditTodo={useEditTodo}
        useEditTodoInstance={useEditTodoInstance}
        usePrioritizeTodo={usePrioritizeTodo}
        useReorderTodo={useReorderTodo}
      >
        <TaskFormSheet
          open={open}
          onOpenChange={setOpen}
          overrideFields={overrideFields}
        />
      </TodoMutationProvider>
    </CreateTaskContext.Provider>
  );
}

export function useCreateTask() {
  const context = useContext(CreateTaskContext);
  if (!context) {
    throw new Error("useCreateTask must be used within CreateTaskProvider");
  }
  return context;
}
