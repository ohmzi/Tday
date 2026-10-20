import clsx from "clsx";
import { getListIconForList } from "@/lib/listIcons";
import { useFloaterListMetaData } from "@/features/floaterList/query/get-floater-list-meta";
import { resolveRowList } from "@/lib/listMark";

// Map every list color to its accent token. Normalized to uppercase so the icon
// is tinted regardless of how the API casts the color value. Mirrors ListDot so
// floater list icons match the main screen.
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

// `name`/`color` are the completed row's fallbacks — see `ListDot`, which argues them. The
// Floater twin needs them for the same reason and one of its own: a completed Floater is the
// row whose `listID` the backend actually nulls, so the id lookup is expected to miss and the
// name is the only thing that can find the list again.
export default function FloaterListDot({
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
  const { floaterListMetaData } = useFloaterListMetaData();
  // The whole meta, not just its key — see `ListDot`.
  const meta = resolveRowList(floaterListMetaData, id, name);
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
