import { Plus } from "lucide-react";
import { Link } from "@/lib/navigation";
import { cn } from "@/lib/utils";

export default function TaskFloatingActionButton({
  className,
}: {
  className?: string;
}) {
  return (
    <Link
      href="/app/add-task"
      aria-label="Create task"
      className={cn(
        "fixed bottom-[calc(18px+env(safe-area-inset-bottom))] right-5 z-40",
        "flex h-14 w-14 items-center justify-center rounded-full",
        "border border-accent/70 bg-accent text-accent-foreground",
        "shadow-[0_18px_34px_-18px_hsl(var(--shadow)/0.65)]",
        "transition-transform duration-200 hover:-translate-y-0.5 active:translate-y-0.5 active:scale-95",
        "md:right-8",
        className,
      )}
    >
      <Plus className="h-8 w-8 stroke-[2.35]" />
    </Link>
  );
}
