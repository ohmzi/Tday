import { ChevronRight } from "lucide-react";
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
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex w-[min(calc(100vw-2rem),24rem)] items-center gap-3 rounded-2xl border p-4 text-left shadow-[0_20px_45px_-24px_hsl(var(--shadow)/0.45)] backdrop-blur-2xl transition",
        variant === "destructive"
          ? "border-red/30 bg-red/95 text-white hover:border-red/45"
          : "border-border/80 bg-card/95 text-foreground hover:border-accent/35",
      )}
    >
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-semibold">{title}</span>
        {description && (
          <span
            className={cn(
              "mt-1 block text-sm leading-5",
              variant === "destructive"
                ? "text-white/80"
                : "text-muted-foreground",
            )}
          >
            {description}
          </span>
        )}
      </span>
      <ChevronRight
        className={cn(
          "h-4 w-4 shrink-0",
          variant === "destructive" ? "text-white/80" : "text-muted-foreground",
        )}
      />
    </button>
  );
}
