import clsx from "clsx";
import { getListIcon } from "@/lib/listIcons";
import { useFloaterListMetaData } from "@/features/floaterList/query/get-floater-list-meta";

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

export default function FloaterListDot({
  id,
  className,
}: {
  id: string;
  className?: string;
}) {
  const { floaterListMetaData } = useFloaterListMetaData();
  const Icon = getListIcon(floaterListMetaData[id]?.iconKey);

  const colorKey = String(floaterListMetaData[id]?.color ?? "")
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
