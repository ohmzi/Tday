import { cn } from "@/lib/utils";
import type { FloaterItemType } from "@/types";
import { useRowPlacement } from "@/hooks/useRowPlacement";
import FloaterItemContainer from "./FloaterItemContainer";

/**
 * Floater order is now FIXED (priority → most-recently-modified → id, shared with every
 * platform and the widgets), so drag-to-reorder is retired here — the displayed order always
 * mirrors the incoming sort.
 */
export default function FloaterGroup({
  floaters,
  className,
  highlightedFloaterId,
  readOnly = false,
}: {
  floaters: FloaterItemType[];
  className?: string;
  highlightedFloaterId?: string | null;
  readOnly?: boolean;
}) {
  // Same travel the scheduled rows get, for the same reason: priority is the sort's
  // first key here, so flagging an Anytime task moves it to the top of the list and
  // every task it passed takes a new slot.
  const placementRef = useRowPlacement<HTMLDivElement>();

  return (
    <div ref={placementRef} className={cn("space-y-0", className)}>
      {floaters.map((item) => (
        <FloaterItemContainer
          key={item.id}
          floater={item}
          highlighted={highlightedFloaterId === item.id}
          readOnly={readOnly}
        />
      ))}
    </div>
  );
}
