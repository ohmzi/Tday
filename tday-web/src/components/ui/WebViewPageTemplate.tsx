import type { ElementType, ReactNode } from "react";
import { ArrowUpRight } from "lucide-react";
import NativeAppBrandButton from "@/components/app/NativeAppBrandButton";
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

/** Renders a shared desktop/mobile page shell that matches the web app utility pages. */
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
        <header className="sticky top-0 z-40 flex w-full items-center justify-between gap-2.5 bg-background pt-[calc(0.5rem+env(safe-area-inset-top))] pb-1.5">
          <div aria-hidden className="pointer-events-none absolute inset-x-0 bottom-full h-screen bg-background" />
          <NativeAppBrandButton className="min-w-0 max-w-[58%] sm:max-w-none" />
        </header>
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
