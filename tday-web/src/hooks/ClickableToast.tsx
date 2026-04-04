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

  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "w-full rounded-xl border px-4 py-3 text-left text-[13px] shadow-[0_4px_12px_rgba(0,0,0,0.1)] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-foreground/20",
        variant === "destructive"
          ? "border-red/25 bg-red/95 text-white hover:bg-red/90"
          : "border-form-button-accent bg-accent text-accent-foreground hover:bg-accent/95",
      )}
    >
      <span className="block min-w-0">
        <span
          className={cn(
            "block leading-[1.5]",
            hasDescription ? "font-medium" : "font-normal",
          )}
        >
          {title}
        </span>
        {description && (
          <span
            className={cn(
              "mt-0.5 block leading-[1.4]",
              variant === "destructive"
                ? "text-white/85"
                : "text-accent-foreground/90",
            )}
          >
            {description}
          </span>
        )}
      </span>
    </button>
  );
}
