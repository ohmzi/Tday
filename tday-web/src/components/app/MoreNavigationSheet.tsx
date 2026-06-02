import { X } from "lucide-react";
import ListSidebarSection from "@/components/Sidebar/List/ListSidebarSection";
import UserCard from "@/components/Sidebar/User/UserCard";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetTitle,
} from "@/components/ui/sheet";
import { Link, usePathname } from "@/lib/navigation";
import { cn } from "@/lib/utils";
import {
  isNativeRouteActive,
  moreNavigationRoutes,
  type NativeRouteCounts,
} from "./nativeRouteConfig";

export default function MoreNavigationSheet({
  open,
  onOpenChange,
  counts,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  counts: NativeRouteCounts;
}) {
  const pathname = usePathname();

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        hideClose
        className={cn(
          "w-[360px] max-w-[92vw] border-l border-border/70 bg-background/96 p-0",
          "shadow-[0_24px_60px_-28px_hsl(var(--shadow)/0.7)] backdrop-blur-2xl",
        )}
      >
        <div className="flex min-h-0 flex-1 flex-col">
          <header className="flex h-20 items-center justify-between px-5">
            <div>
              <SheetTitle className="text-2xl font-black tracking-normal">
                More
              </SheetTitle>
              <SheetDescription className="text-sm font-extrabold">
                Navigation and lists
              </SheetDescription>
            </div>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="h-11 w-11 rounded-full bg-card/80 text-foreground shadow-sm hover:bg-card"
              onClick={() => onOpenChange(false)}
              aria-label="Close navigation"
            >
              <X className="h-5 w-5" />
            </Button>
          </header>

          <div className="min-h-0 flex-1 overflow-y-auto px-3 pb-4">
            <div className="space-y-1.5 px-2">
              {moreNavigationRoutes.map((route) => {
                const Icon = route.icon;
                const active = isNativeRouteActive(pathname, route);
                const count = counts[route.id];

                return (
                  <Link
                    key={route.id}
                    href={route.path}
                    onClick={() => onOpenChange(false)}
                    aria-current={active ? "page" : undefined}
                    className={cn(
                      "flex h-12 items-center gap-3 rounded-2xl px-3 text-sm font-black transition-colors",
                      active
                        ? "bg-card text-foreground shadow-sm"
                        : "text-muted-foreground hover:bg-card/70 hover:text-foreground",
                    )}
                  >
                    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-muted/70">
                      <Icon className={cn("h-4.5 w-4.5 stroke-[2.4]", route.accentClass)} />
                    </span>
                    <span className="min-w-0 flex-1 truncate">{route.label}</span>
                    {count != null && (
                      <span
                        className={cn(
                          "rounded-full px-2 py-0.5 text-xs font-black",
                          active
                            ? "bg-muted text-foreground"
                            : "bg-muted/70 text-muted-foreground",
                        )}
                      >
                        {count}
                      </span>
                    )}
                  </Link>
                );
              })}
            </div>

            <div className="my-4 h-px bg-border/70" />

            <ListSidebarSection mode="expanded" onNavigate={() => onOpenChange(false)} />
          </div>

          <footer className="border-t border-border/70 p-3">
            <UserCard onNavigate={() => onOpenChange(false)} />
          </footer>
        </div>
      </SheetContent>
    </Sheet>
  );
}
