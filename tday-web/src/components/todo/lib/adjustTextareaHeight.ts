import React from "react";

/**
 * Grows a textarea to fit its content.
 *
 * The collapse to `1rem` first is load-bearing, not a reset for tidiness: `scrollHeight`
 * never reports less than the element's current height, so measuring without shrinking
 * first makes the box a one-way ratchet that can grow but never shrink again as the user
 * deletes lines.
 */
const adjustTextareaHeight = (
  textareaRef: React.RefObject<null | HTMLTextAreaElement>,
) => {
  const textarea = textareaRef.current;
  if (!textarea) return;

  textarea.style.height = "1rem";
  // A detached or display:none textarea measures 0; leave it at the floor rather than
  // committing a 0px height it would then be stuck with once it becomes visible.
  if (textarea.scrollHeight !== 0) {
    textarea.style.height = `${textarea.scrollHeight}px`;
  }
};

export default adjustTextareaHeight;
