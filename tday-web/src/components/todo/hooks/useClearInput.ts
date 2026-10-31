import { useCallback } from "react";
import { useTodoForm } from "@/providers/TodoFormProvider";

export function useClearInput(
  setEditInstanceOnly:
    | React.Dispatch<React.SetStateAction<boolean>>
    | undefined,
  titleRef: React.RefObject<HTMLDivElement | null>,
) {
  const {
    todoItem,
    setDesc,
    setTitle,
    setDateRange,
    setPriorityTouchedByUser,
    setRruleOptions,
  } = useTodoForm();
  const clearInput = useCallback(
    function clearInput() {
      const now = new Date();
      now.setHours(now.getHours() + 3);
      now.setSeconds(0, 0);
      if (setEditInstanceOnly) setEditInstanceOnly(false);
      setDesc("");
      setTitle("");
      setDateRange({
        from: todoItem?.due ? todoItem.due : now,
        to: todoItem?.due ? todoItem.due : now,
      });
      // Re-arm the list default rather than hard-coding "Low": the form's list-default
      // effect re-seeds the priority from the active list on the next render.
      setPriorityTouchedByUser(false);
      setRruleOptions(null);
      if (titleRef.current) {
        titleRef.current.textContent = "";
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [todoItem?.due],
  );
  return clearInput;
}
