import { useEffect, useRef } from "react";
import adjustHeight from "@/components/todo/lib/adjustTextareaHeight";
import { moveCursorToEnd } from "@/components/todo/component/TodoForm/NLPTitleInput";
import { isDesktopPointer } from "@/lib/utils";

//adjust height of the todo description based on content size
export function useTodoFormFocusAndAutosize(displayForm: boolean) {
  const titleRef = useRef<HTMLDivElement | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);

  useEffect(() => {
    adjustHeight(textareaRef);
  }, [displayForm]);

  // On desktop pointers, focus the title once the sheet/drawer open animation
  // has settled and place the caret at the end of any existing text.
  useEffect(() => {
    if (!displayForm || !isDesktopPointer()) return;
    const timer = window.setTimeout(() => {
      const node = titleRef.current;
      if (!node) return;
      node.focus();
      moveCursorToEnd(node);
    }, 180);
    return () => window.clearTimeout(timer);
  }, [displayForm]);

  return { titleRef, textareaRef };
}
