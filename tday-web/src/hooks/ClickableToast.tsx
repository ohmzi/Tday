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
        // Icon-free, translucent blurred card. The destructive ("issue") variant
        // gets a red translucent shade; default stays the neutral popover surface.
        "flex w-full items-center gap-3 rounded-[24px] border px-4 py-3.5 text-left text-popover-foreground backdrop-blur-xl shadow-[0_10px_30px_-12px_hsl(var(--shadow)/0.45)] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-foreground/20",
        isDestructive
          ? "border-destructive/30 bg-destructive/15 hover:bg-destructive/20"
          : "border-border bg-popover/92 hover:bg-popover",
      )}
    >
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
    </button>
  );
}
