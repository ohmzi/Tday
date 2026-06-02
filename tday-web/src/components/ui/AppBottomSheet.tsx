import type { ReactNode } from "react";
import {
  Drawer,
  DrawerContent,
  DrawerDescription,
  DrawerHeader,
  DrawerTitle,
} from "@/components/ui/drawer";
import { cn } from "@/lib/utils";

type AppBottomSheetProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  description?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  className?: string;
  bodyClassName?: string;
};

export default function AppBottomSheet({
  open,
  onOpenChange,
  title,
  description,
  children,
  footer,
  className,
  bodyClassName,
}: AppBottomSheetProps) {
  return (
    <Drawer open={open} onOpenChange={onOpenChange} shouldScaleBackground={false}>
      <DrawerContent
        className={cn(
          "max-h-[92dvh] overflow-hidden rounded-t-[28px] border-white/70 bg-background shadow-[0_24px_70px_-34px_hsl(var(--shadow)/0.82)] dark:border-white/10",
          "sm:left-1/2 sm:right-auto sm:w-[min(720px,calc(100vw-2rem))] sm:-translate-x-1/2",
          className,
        )}
      >
        <DrawerHeader className="px-5 pb-3 pt-4 text-left sm:px-6">
          <DrawerTitle className="text-2xl font-black tracking-tight text-foreground">
            {title}
          </DrawerTitle>
          {description ? (
            <DrawerDescription className="font-extrabold text-muted-foreground">
              {description}
            </DrawerDescription>
          ) : null}
        </DrawerHeader>
        <div
          className={cn(
            "min-h-0 flex-1 overflow-y-auto px-5 pb-5 sm:px-6",
            bodyClassName,
          )}
        >
          {children}
        </div>
        {footer ? (
          <div className="border-t border-border/70 bg-background/95 px-5 py-4 sm:px-6">
            {footer}
          </div>
        ) : null}
      </DrawerContent>
    </Drawer>
  );
}
