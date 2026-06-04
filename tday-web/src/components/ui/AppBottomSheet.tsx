import type { ReactNode } from "react";
import {
  Drawer,
  DrawerContent,
  DrawerDescription,
  DrawerHeader,
  DrawerTitle,
} from "@/components/ui/drawer";
import { SheetHeader } from "@/components/ui/sheet-chrome";
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
  /**
   * "native" renders the iOS-style circular X / title / ✓ header (submit lives in
   * the header, so the footer region is omitted). "default" keeps the original
   * title + description header. Responsive chrome (mobile bottom sheet, desktop
   * centered modal) is identical for both.
   */
  variant?: "default" | "native";
  onClose?: () => void;
  onConfirm?: () => void;
  confirmDisabled?: boolean;
  confirmLabel?: string;
  closeLabel?: string;
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
  variant = "default",
  onClose,
  onConfirm,
  confirmDisabled,
  confirmLabel,
  closeLabel,
}: AppBottomSheetProps) {
  const isNative = variant === "native";

  return (
    <Drawer open={open} onOpenChange={onOpenChange} shouldScaleBackground={false}>
      <DrawerContent
        className={cn(
          "max-h-[92dvh] overflow-hidden rounded-t-[28px] border-white/70 bg-background shadow-[0_24px_70px_-34px_hsl(var(--shadow)/0.82)] dark:border-white/10",
          "sm:left-1/2 sm:right-auto sm:w-[min(720px,calc(100vw-2rem))] sm:-translate-x-1/2",
          className,
        )}
      >
        {isNative ? (
          <div className="shrink-0">
            {/* Visually hidden title keeps vaul/Radix happy; the visible header
                is rendered by SheetHeader below. */}
            <DrawerTitle className="sr-only">{title}</DrawerTitle>
            <SheetHeader
              title={title}
              onClose={onClose ?? (() => onOpenChange(false))}
              onConfirm={onConfirm}
              confirmDisabled={confirmDisabled}
              confirmLabel={confirmLabel}
              closeLabel={closeLabel}
            />
          </div>
        ) : (
          <DrawerHeader className="shrink-0 px-5 pb-3 pt-4 text-left sm:px-6">
            <DrawerTitle className="text-2xl font-black tracking-tight text-foreground">
              {title}
            </DrawerTitle>
            {description ? (
              <DrawerDescription className="font-extrabold text-muted-foreground">
                {description}
              </DrawerDescription>
            ) : null}
          </DrawerHeader>
        )}
        <div
          className={cn(
            // Scrollable body so a keyboard-shortened sheet never clips its
            // contents. When the sheet is tall enough for everything, there's
            // no overflow and nothing scrolls.
            "min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 pb-5 sm:px-6",
            bodyClassName,
          )}
        >
          {children}
        </div>
        {!isNative && footer ? (
          <div className="shrink-0 border-t border-border/70 bg-background/95 px-5 py-4 sm:px-6">
            {footer}
          </div>
        ) : null}
      </DrawerContent>
    </Drawer>
  );
}
