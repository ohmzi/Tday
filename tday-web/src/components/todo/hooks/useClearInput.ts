import { useCallback } from "react";
import { useTodoForm } from "@/providers/TodoFormProvider";
import type { Priority } from "@/components/todo/component/TodoForm/labels";

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
    setPriority,
    setPriorityTouchedByUser,
    setRruleOptions,
  } = useTodoForm();
  const clearInput = useCallback(
    function clearInput(activeListDefaultPriority?: Priority | null) {
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
      // Set the active list's default explicitly, and re-arm the touched flag so the form's
      // list-default effect keeps following that list. Setting it here (not only re-arming
      // the flag) means a list-less or unchanged-deps form still starts on the right value.
      setPriority(activeListDefaultPriority ?? "Low");
      setPriorityTouchedByUser(false);
      setRruleOptions(null);
      if (titleRef.current) {
        titleRef.current.textContent = "";
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [todoItem?.due, setPriority, setPriorityTouchedByUser],
  );
  return clearInput;
}
