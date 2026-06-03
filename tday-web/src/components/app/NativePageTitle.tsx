import type { ElementType, ReactNode } from "react";
import { cn } from "@/lib/utils";

export default function NativePageTitle({
  title,
  accentColor,
  icon: Icon,
  iconNode,
  subtitle,
  className,
}: {
  title: string;
  accentColor: string;
  icon?: ElementType;
  iconNode?: ReactNode;
  subtitle?: string;
  className?: string;
}) {
  return (
    <div className={cn("mt-4 sm:mt-5", className)}>
      <div className="flex min-w-0 items-center gap-2.5">
        {iconNode}
        {Icon ? (
          <Icon className="h-8 w-8 shrink-0" style={{ color: accentColor }} strokeWidth={2.4} />
        ) : null}
        <h1
          className="truncate text-[2.1rem] font-black leading-tight tracking-normal sm:text-[2.55rem]"
          style={{ color: accentColor }}
        >
          {title}
        </h1>
      </div>
      {subtitle ? (
        <p className="mt-1.5 text-sm font-extrabold text-muted-foreground">{subtitle}</p>
      ) : null}
    </div>
  );
}
