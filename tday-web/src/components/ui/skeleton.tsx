import { cn } from "@/lib/utils";

/**
 * A pulsing placeholder block for content that has not loaded yet.
 *
 * Sizing is the caller's job — pass width/height through `className` so each skeleton can
 * match the real element it stands in for, which is what keeps the layout from jumping when
 * the content arrives.
 */
function Skeleton({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn("animate-pulse rounded-md bg-border", className)}
      {...props}
    />
  );
}

export { Skeleton };
