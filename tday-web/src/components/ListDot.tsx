import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import clsx from "clsx";
import { getListIconForList } from "@/lib/listIcons";
import { resolveRowList } from "@/lib/listMark";

// Map every list color to its accent token. Normalized to uppercase so the icon
// is tinted regardless of how the API casts the color value.
const LIST_COLOR_CLASS: Record<string, string> = {
  RED: "text-accent-red",
  ORANGE: "text-accent-orange",
  YELLOW: "text-accent-yellow",
  LIME: "text-accent-lime",
  BLUE: "text-accent-blue",
  PURPLE: "text-accent-purple",
  PINK: "text-accent-pink",
  TEAL: "text-accent-teal",
  CORAL: "text-accent-coral",
  GOLD: "text-accent-gold",
  DEEP_BLUE: "text-accent-deep-blue",
  ROSE: "text-accent-rose",
  LIGHT_RED: "text-accent-light-red",
  BRICK: "text-accent-brick",
  SLATE: "text-accent-slate",
};

/**
 * The list's own glyph, tinted by the list's colour.
 *
 * `id` is enough for every row whose list is still there to be looked up. `name` and `color`
 * are the fallbacks a COMPLETED row needs, and they are not decoration: that row is a
 * denormalised snapshot, the backend nulls its `listID` when its list is deleted, and the
 * snapshot still holds the name and the colour. Passing the name as well is what lets a
 * deleted-list row draw the list it was in — resolved again by name if Undo recreated it —
 * instead of falling through to the default glyph.
 */
export default function ListDot({
  id,
  name,
  color,
  className,
}: {
  id?: string | null;
  name?: string | null;
  color?: string | null;
  className?: string;
}) {
  const { listMetaData } = useListMetaData();
  // The id survives a rename and wins; the name is what is left once the id is gone.
  const meta = resolveRowList(listMetaData, id, name);
  // The whole meta, not just its key: a list whose owner never picked an icon
  // takes one from its name, and the name is half of that question. With no live
  // list at all, the row's own snapshot name is what answers it.
  const Icon = getListIconForList(meta ?? { name });

  const colorKey = String(meta?.color ?? color ?? "")
    .trim()
    .toUpperCase();
  const colorClass = LIST_COLOR_CLASS[colorKey] ?? "text-muted-foreground";

  return (
    <Icon
      className={clsx(
        "inline-flex h-3.5 w-3.5 shrink-0 stroke-[2.4]",
        colorClass,
        className,
      )}
    />
  );
}
