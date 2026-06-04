import { useEditFloater } from "./update-floater";

export const useEditFloaterInstance = () => {
  const { editTodoMutateFn, editTodoStatus } = useEditFloater();
  return {
    editTodoInstanceMutateFn: editTodoMutateFn,
    editTodoInstanceStatus: editTodoStatus,
  };
};
