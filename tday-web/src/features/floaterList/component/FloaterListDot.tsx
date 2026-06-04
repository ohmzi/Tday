import { getListIcon } from "@/lib/listIcons";
import { listColorMap } from "@/lib/listColorMap";
import { cn } from "@/lib/utils";
import { useFloaterListMetaData } from "@/features/floaterList/query/get-floater-list-meta";

export default function FloaterListDot({
  id,
  className,
}: {
  id: string;
  className?: string;
}) {
  const { floaterListMetaData } = useFloaterListMetaData();
  const list = floaterListMetaData[id];
  const Icon = getListIcon(list?.iconKey);
  const color =
    listColorMap.find((option) => option.value === list?.color)?.tailwind ??
    "bg-accent-teal";

  return (
    <span
      className={cn(
        "inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-full text-white",
        color,
        className,
      )}
    >
      <Icon className="h-[0.62em] w-[0.62em] stroke-[2.5]" />
    </span>
  );
}
