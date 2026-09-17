/**
 * Whether a task row should draw its list's glyph (and, on desktop, its name pill) as
 * a trailing mark.
 *
 * The mark answers one question — "which list is this task in?" — and it is worth
 * drawing exactly where the screen has not already answered it. On Today, Overdue,
 * All, Priority, the calendar, Completed and the Anytime HOME feed a row could have
 * come from any list, so the mark is the only thing that says. On a list DETAIL screen
 * every row was filtered to one list before it was rendered, so the mark repeats the
 * screen's own title once per row, forever, and says nothing either time — the desktop
 * pill literally prints the heading again beside each task.
 *
 * Expressed as SCOPE rather than as a screen or mode test, so the answer comes from
 * the one fact that actually decides it. The native clients had the same rule written
 * as a test on their view-model's mode, and it could not tell the Anytime home feed
 * from one Anytime list's detail because those two screens share a mode; comparing the
 * row's list against the screen's covers both detail screens with one line instead of
 * one case each. This client reaches the same rule from a different direction — no
 * modes, one component per screen — but it lands on the same three inputs, which is
 * what makes the three tests transcriptions of each other rather than three opinions.
 *
 * It also keeps the one case that must survive: a row whose list is NOT the scoped one
 * still draws its mark. That is what a feed looks like for the frame between a task
 * being moved to another list and the query catching up, and on that frame the mark is
 * the most informative thing on the row.
 *
 * @param rowListId the list this task belongs to, or null for an unfiled task.
 * @param scopedListId the list the SCREEN is already showing, or null on a mixed feed.
 */
export function shouldShowListMark(
  rowListId: string | null | undefined,
  scopedListId: string | null | undefined,
): boolean {
  if (!rowListId?.trim()) return false;
  if (!scopedListId?.trim()) return true;
  return rowListId !== scopedListId;
}

/** The two fields a lookup needs of any shape of list meta. */
type RowListSource = { name?: string | null };

/**
 * WHICH list a row's mark belongs to, given what the row knows and what the store holds.
 *
 * The other half of the question above: that one decides whether a row draws its list's glyph,
 * this one decides which list's glyph that is. They live together because they are asked by the
 * same row at the same moment — the same pairing the iOS twin keeps in `TaskRowListMark.swift`,
 * where `shouldShowListMark` and `tdayResolvedRowList` are neighbours.
 *
 * The id is tried first, because an id survives a rename. It is not sufficient on its own: a
 * COMPLETED row is a denormalised snapshot, and the backend nulls a completed Floater's `listID`
 * the moment its list is deleted (`ON DELETE SET NULL`, keeping `originalListID`) — so a
 * deleted-list row falls through to the name, which is what its snapshot still holds, and finds
 * the list again if Undo recreated it. Handing such a row to a lookup that only takes an id is
 * what would make it draw the app's `inbox` glyph: a mark that says "Inbox" about a task in any
 * list at all.
 *
 * The name match is case- and whitespace-insensitive and takes the first match, matching
 * `tdayResolvedRowList` on iOS and `CompletedItem.resolveListSummary` on Android. The namespace
 * is the caller's: scheduled lists and Floater lists are two disjoint stores, and a row resolved
 * against the wrong one finds nothing and silently loses its mark.
 */
export function resolveRowList<T extends RowListSource>(
  lists: Record<string, T> | undefined,
  listID: string | null | undefined,
  listName: string | null | undefined,
): T | undefined {
  const namespace = lists ?? {};

  const byId = listID?.trim();
  if (byId && namespace[byId]) return namespace[byId];

  const wanted = listName?.trim().toLowerCase();
  if (!wanted) return undefined;

  return Object.values(namespace).find(
    (list) => (list.name ?? "").trim().toLowerCase() === wanted,
  );
}
