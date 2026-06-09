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
}: ClickableToastProps) {
  const hasDescription = Boolean(description);

  // Content only — no box of its own. The surrounding sonner <li> supplies the
  // frosted, fully-rounded pill (surface, border, padding, blur) so clickable
  // toasts look identical to plain ones. Carrying its own card here would nest
  // a second box inside the sonner pill (the old "Task Deleted" double-box).
  return (
    <button
      type="button"
      onClick={onClick}
      className="block w-full min-w-0 text-left text-popover-foreground focus-visible:outline-none"
    >
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
    </button>
  );
}
