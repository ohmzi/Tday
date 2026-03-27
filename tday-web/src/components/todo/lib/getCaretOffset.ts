/**
 * The caret's position in `el` as a plain character count.
 *
 * Contenteditable reports the caret as (node, offset), which is unusable across a re-render
 * because React hands back different text nodes. A single integer measured over the whole
 * element's text survives that, and pairs with `setCaretOffset` to restore it.
 */
export function getCaretOffset(el: HTMLElement) {
  const selection = window.getSelection();
  if (!selection || !selection.rangeCount) return 0;

  const caret = selection.getRangeAt(0);
  // Everything from the start of the element up to the caret; its length is the offset.
  const precedingText = caret.cloneRange();
  precedingText.selectNodeContents(el);
  precedingText.setEnd(caret.endContainer, caret.endOffset);

  return precedingText.toString().length;
}
