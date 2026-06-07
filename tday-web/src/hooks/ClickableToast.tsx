import { ChevronRightIcon, InfoIcon, OctagonXIcon } from "lucide-react";
import { cn } from "@/lib/utils";

type ClickableToastProps = {
  title: string;
  description?: string;
  onClick: () => void;
  variant?: "default" | "destructive";
};

export default function ClickableToast({
  title,
  description,
  onClick,
  variant = "default",
}: ClickableToastProps) {
  const hasDescription = Boolean(description);
  const isDestructive = variant === "destructive";

  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex w-full items-center gap-3 rounded-[24px] border bg-popover/92 px-4 py-3.5 text-left text-popover-foreground backdrop-blur-xl shadow-[0_10px_30px_-12px_hsl(var(--shadow)/0.45)] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-foreground/20",
        isDestructive
          ? "border-destructive/30 hover:bg-popover"
          : "border-border hover:bg-popover",
      )}
    >
      <span
        className={cn(
          "flex size-9 shrink-0 items-center justify-center rounded-full",
          isDestructive
            ? "bg-destructive/15 text-destructive"
            : "bg-[#E06F66]/15 text-[#E06F66]",
        )}
      >
        {isDestructive ? (
          <OctagonXIcon className="size-[18px]" />
        ) : (
          <InfoIcon className="size-[18px]" />
        )}
      </span>
      <span className="block min-w-0 flex-1">
        <span
          className={cn(
            "block leading-tight",
            hasDescription ? "font-extrabold" : "font-bold",
          )}
        >
          {title}
        </span>
        {description && (
          <span className="mt-0.5 block text-[13px] font-medium leading-snug text-current/75">
            {description}
          </span>
        )}
      </span>
      <ChevronRightIcon className="size-4 shrink-0 text-current/40" />
    </button>
  );
}
