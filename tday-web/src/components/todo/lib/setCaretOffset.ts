/**
 * Puts the caret `offset` characters into `el`, undoing `getCaretOffset`.
 *
 * The offset counts characters across the element, but a Range has to be anchored to one
 * text node, so walk the text nodes accumulating lengths until the offset lands inside one.
 * Running past the end is a no-op — the caller's offset can outlive a shortened value.
 */
export function setCaretOffset(el: HTMLElement, offset: number) {
  const selection = window.getSelection();
  if (!selection) return;

  const textNodes = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, null);
  let consumed = 0;

  let node: Node | null;
  while ((node = textNodes.nextNode())) {
    const length = node.textContent?.length ?? 0;

    if (consumed + length >= offset) {
      const caret = document.createRange();
      caret.setStart(node, offset - consumed);
      caret.collapse(true);

      selection.removeAllRanges();
      selection.addRange(caret);
      return;
    }

    consumed += length;
  }
}
