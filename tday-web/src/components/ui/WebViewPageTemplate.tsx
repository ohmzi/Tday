import type { ReactNode } from "react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { cn } from "@/lib/utils";

type WebViewSectionCardProps = {
  title: string;
  description?: string;
  children: ReactNode;
  className?: string;
  contentClassName?: string;
};

export const WEB_VIEW_CARD_CLASS =
  "w-full min-w-0 overflow-hidden rounded-lg border-border/70 bg-card/95";

/** Wraps section content in the shared card pattern used by web utility screens. */
export function WebViewSectionCard({
  title,
  description,
  children,
  className,
  contentClassName,
}: WebViewSectionCardProps) {
  return (
    <Card className={cn(WEB_VIEW_CARD_CLASS, className)}>
      <CardHeader className="space-y-1">
        <CardTitle className="text-base">{title}</CardTitle>
        {description ? <CardDescription>{description}</CardDescription> : null}
      </CardHeader>
      <CardContent className={contentClassName}>{children}</CardContent>
    </Card>
  );
}
