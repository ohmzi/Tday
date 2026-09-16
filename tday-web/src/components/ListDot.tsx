import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import clsx from "clsx";
import { getListIconForList } from "@/lib/listIcons";

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

export default function ListDot({
  id,
  className,
}: {
  id: string;
  className?: string;
}) {
  const { listMetaData } = useListMetaData();
  // The whole meta, not just its key: a list whose owner never picked an icon
  // takes one from its name, and the name is half of that question.
  const Icon = getListIconForList(listMetaData[id]);

  const colorKey = String(listMetaData[id]?.color ?? "")
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
