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
  surface?: "card" | "sheet";
}
const TodoFormContainer = ({
  editInstanceOnly,
  setEditInstanceOnly,
  displayForm,
  setDisplayForm,
  todo,
  overrideFields,
  persistent,
  surface,
}: TodoFormContainerProps) => {
  return (
    <TodoFormProvider todoItem={todo} overrideFields={overrideFields}>
      <TodoForm
        displayForm={displayForm}
        setDisplayForm={setDisplayForm}
        editInstanceOnly={editInstanceOnly}
        setEditInstanceOnly={setEditInstanceOnly}
        persistent={persistent}
        surface={surface}
      />
    </TodoFormProvider>
  );
};

export default TodoFormContainer;
