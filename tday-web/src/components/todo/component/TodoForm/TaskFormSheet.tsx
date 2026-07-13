import {
  lazy,
  Suspense,
  useCallback,
  useRef,
  useState,
  type Dispatch,
  type SetStateAction,
} from "react";
import { useTranslation } from "react-i18next";
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
  overrideFields?: { listID?: string; title?: string };
  editInstanceOnly?: boolean;
  setEditInstanceOnly?: Dispatch<SetStateAction<boolean>>;
  persistent?: boolean;
  title?: string;
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
}: TaskFormSheetProps) {
  const { t: appDict } = useTranslation("app");
  const { t: todayDict } = useTranslation("today");
  const submitRef = useRef<(() => void) | null>(null);
  const [canSubmit, setCanSubmit] = useState(false);

  const registerSubmit = useCallback((submit: () => void) => {
    submitRef.current = submit;
  }, []);

  return (
    <AppBottomSheet
      variant="native"
      open={open}
      onOpenChange={onOpenChange}
      title={title ?? (todo ? appDict("editTask") : appDict("newTask"))}
      onClose={() => onOpenChange(false)}
      onConfirm={() => submitRef.current?.()}
      confirmDisabled={!canSubmit}
      confirmLabel={editInstanceOnly ? todayDict("saveInstance") : appDict("save")}
      closeLabel={appDict("cancel")}
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
          registerSubmit={registerSubmit}
          onCanSubmitChange={setCanSubmit}
        />
      </Suspense>
    </AppBottomSheet>
  );
}
