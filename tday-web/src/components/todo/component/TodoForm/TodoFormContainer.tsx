import TodoFormProvider from "@/providers/TodoFormProvider";
import TodoForm from "./TodoForm";
import { TodoItemType } from "@/types";

interface TodoFormContainerProps {
  editInstanceOnly?: boolean;
  setEditInstanceOnly?: React.Dispatch<React.SetStateAction<boolean>>;
  displayForm: boolean;
  setDisplayForm: React.Dispatch<React.SetStateAction<boolean>>;
  todo?: TodoItemType;
  overrideFields?: { listID?: string };
  persistent?: boolean;
  registerSubmit?: (submit: () => void) => void;
  onCanSubmitChange?: (canSubmit: boolean) => void;
}
const TodoFormContainer = ({
  editInstanceOnly,
  setEditInstanceOnly,
  displayForm,
  setDisplayForm,
  todo,
  overrideFields,
  persistent,
  registerSubmit,
  onCanSubmitChange,
}: TodoFormContainerProps) => {
  return (
    <TodoFormProvider todoItem={todo} overrideFields={overrideFields}>
      <TodoForm
        displayForm={displayForm}
        setDisplayForm={setDisplayForm}
        editInstanceOnly={editInstanceOnly}
        setEditInstanceOnly={setEditInstanceOnly}
        persistent={persistent}
        registerSubmit={registerSubmit}
        onCanSubmitChange={onCanSubmitChange}
      />
    </TodoFormProvider>
  );
};

export default TodoFormContainer;
