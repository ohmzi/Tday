import type { ElementType, ReactNode } from "react";
import { ArrowUpRight } from "lucide-react";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Link } from "@/lib/navigation";
import { cn } from "@/lib/utils";

type WebViewPageTemplateProps = {
  title: string;
  description?: string;
  icon?: ElementType;
  backHref?: string;
  backLabel?: string;
  children: ReactNode;
  className?: string;
};

type WebViewSectionCardProps = {
  title: string;
  description?: string;
  children: ReactNode;
  className?: string;
  contentClassName?: string;
};

export const WEB_VIEW_PAGE_CLASS = "w-full min-w-0 overflow-x-hidden space-y-5 pb-10";
export const WEB_VIEW_CARD_CLASS =
  "w-full min-w-0 overflow-hidden rounded-2xl border-border/70 bg-card/95";

export function WebViewPageTemplate({
  title,
  description,
  icon: Icon,
  backHref,
  backLabel = "Back",
  children,
  className,
}: WebViewPageTemplateProps) {
  return (
    <div className={cn(WEB_VIEW_PAGE_CLASS, className)}>
      <div className="lg:hidden">
        <MobileSearchHeader />
      </div>

      <header className="mt-8 space-y-1 sm:mt-10 lg:mt-0">
        <h1 className="flex items-center gap-2 text-2xl font-semibold tracking-tight text-foreground">
          {Icon ? <Icon className="h-5 w-5 text-accent" /> : null}
          {title}
        </h1>

        {description ? (
          <p className="max-w-2xl text-sm text-muted-foreground">{description}</p>
        ) : null}

        {backHref ? (
          <Link
            href={backHref}
            className="inline-flex items-center gap-2 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
          >
            {backLabel}
            <ArrowUpRight className="h-4 w-4" />
          </Link>
        ) : null}
      </header>

      {children}
    </div>
  );
}

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
