import { lazy, Suspense, type Dispatch, type SetStateAction } from "react";
import AppBottomSheet from "@/components/ui/AppBottomSheet";
import TodoFormLoading from "@/components/todo/component/TodoForm/TodoFormLoading";
import type { TodoItemType } from "@/types";

const TodoFormContainer = lazy(
  () => import("@/components/todo/component/TodoForm/TodoFormContainer"),
);

type TaskFormSheetProps = {
  open: boolean;
  onOpenChange: Dispatch<SetStateAction<boolean>>;
  todo?: TodoItemType;
  overrideFields?: { listID?: string };
  editInstanceOnly?: boolean;
  setEditInstanceOnly?: Dispatch<SetStateAction<boolean>>;
  persistent?: boolean;
  title?: string;
  description?: string;
};

export default function TaskFormSheet({
  open,
  onOpenChange,
  todo,
  overrideFields,
  editInstanceOnly,
  setEditInstanceOnly,
  persistent,
  title,
  description,
}: TaskFormSheetProps) {
  return (
    <AppBottomSheet
      open={open}
      onOpenChange={onOpenChange}
      title={title ?? (todo ? "Edit task" : "New task")}
      description={
        description ??
        "Set the title, schedule, repeat, list, priority, and notes in one place."
      }
      bodyClassName="pb-6"
    >
      <Suspense fallback={<TodoFormLoading />}>
        <TodoFormContainer
          editInstanceOnly={editInstanceOnly}
          setEditInstanceOnly={setEditInstanceOnly}
          displayForm={open}
          setDisplayForm={onOpenChange}
          todo={todo}
          overrideFields={overrideFields}
          persistent={persistent}
          surface="sheet"
        />
      </Suspense>
    </AppBottomSheet>
  );
}
