/**
 * Mirror of the shared Kotlin `FloaterMainScreen`, which web cannot link. Keep the rule identical:
 * a task with no list, or whose list has not loaded, reads as shown. A task must never vanish
 * because metadata is missing. `tests/guardrails/floater-main-screen-parity.test.ts` checks it.
 */
export function isFloaterShownOnMainScreen(
  listId: string | null | undefined,
  hiddenListIds: ReadonlySet<string>,
): boolean {
  return listId == null || !hiddenListIds.has(listId);
}

/** Only an explicit `false` hides a list: a row written before the field existed stays shown. */
export function hiddenFloaterListIds(
  lists: Iterable<{ id: string; showOnMainScreen?: boolean }>,
): Set<string> {
  const hidden = new Set<string>();
  for (const list of lists) {
    if (list.showOnMainScreen === false) hidden.add(list.id);
  }
  return hidden;
}
