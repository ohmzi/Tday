import * as React from "react"
import { Drawer as DrawerPrimitive } from "vaul"

import { cn } from "@/lib/utils"

const Drawer = ({
  shouldScaleBackground = true,
  // We own keyboard handling via the visual viewport (see useViewportSheetMetrics
  // + DrawerContent). vaul's built-in input repositioning fights that math and
  // leaves the sheet shifted after the keyboard dismisses, so disable it.
  repositionInputs = false,
  ...props
}: React.ComponentProps<typeof DrawerPrimitive.Root>) => (
  <DrawerPrimitive.Root
    shouldScaleBackground={shouldScaleBackground}
    repositionInputs={repositionInputs}
    {...props}
  />
)
Drawer.displayName = "Drawer"

const DrawerTrigger = DrawerPrimitive.Trigger

const DrawerPortal = DrawerPrimitive.Portal

const DrawerClose = DrawerPrimitive.Close

const DrawerOverlay = React.forwardRef<
  React.ElementRef<typeof DrawerPrimitive.Overlay>,
  React.ComponentPropsWithoutRef<typeof DrawerPrimitive.Overlay>
>(({ className, ...props }, ref) => (
  <DrawerPrimitive.Overlay
    ref={ref}
    className={cn("fixed inset-0 z-50 bg-black/80", className)}
    {...props}
  />
))
DrawerOverlay.displayName = DrawerPrimitive.Overlay.displayName

/**
 * Tracks the visual viewport so bottom sheets behave consistently with the
 * on-screen keyboard: the sheet is tall enough for its content when the keyboard
 * is closed, and is capped + lifted above the keyboard when it opens.
 */
function useViewportSheetMetrics() {
  const [metrics, setMetrics] = React.useState(() => ({
    height: typeof window !== "undefined" ? window.innerHeight : 0,
    keyboard: 0,
  }));

  React.useEffect(() => {
    const vv = window.visualViewport;
    const update = () => {
      if (vv) {
        const rawKeyboard = window.innerHeight - vv.height - vv.offsetTop;
        // Treat anything under the threshold as "no keyboard" so the sheet
        // settles back to a clean, full-height state once it dismisses (the
        // residual offset is what used to leave it half-hidden).
        const keyboard = rawKeyboard > 80 ? rawKeyboard : 0;
        setMetrics({ height: vv.height, keyboard });
      } else {
        setMetrics({ height: window.innerHeight, keyboard: 0 });
      }
    };
    update();
    vv?.addEventListener("resize", update);
    vv?.addEventListener("scroll", update);
    window.addEventListener("resize", update);
    window.addEventListener("orientationchange", update);
    return () => {
      vv?.removeEventListener("resize", update);
      vv?.removeEventListener("scroll", update);
      window.removeEventListener("resize", update);
      window.removeEventListener("orientationchange", update);
    };
  }, []);

  return metrics;
}

const DrawerContent = React.forwardRef<
  React.ElementRef<typeof DrawerPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof DrawerPrimitive.Content>
>(({ className, children, style, ...props }, ref) => {
  const { height, keyboard } = useViewportSheetMetrics();
  const keyboardOpen = keyboard > 0;
  // The sheet is anchored to the bottom of the *layout* viewport. When the
  // keyboard opens the visual viewport shrinks but the layout viewport doesn't,
  // so lift the sheet above the keyboard (`bottom`) and cap its height to the
  // visible area. `bottom` is 0 when the keyboard is closed, so dismissing it
  // resets the sheet cleanly. The body scrolls (see AppBottomSheet), so a
  // shortened sheet never clips its contents.
  const sheetStyle: React.CSSProperties = height
    ? {
        maxHeight: Math.round((keyboardOpen ? 0.9 : 0.94) * height),
        bottom: keyboard,
        ...style,
      }
    : style ?? {};

  // With vaul's own input repositioning disabled, make sure the focused field
  // is scrolled into the visible part of the sheet's scroll area. Scroll ONLY
  // the sheet's scrollable body: scrollIntoView (and the browser's native
  // focus scrolling) also scrolls overflow-hidden ancestors — including the
  // sheet container itself — which clips the drag handle and header off the
  // top with no way to drag them back (first seen with the members sheet,
  // whose search field sits low in the sheet).
  const handleFocus = React.useCallback(
    (event: React.FocusEvent<HTMLDivElement>) => {
      const container = event.currentTarget;
      const target = event.target as HTMLElement | null;
      if (!target?.matches("input, textarea, select, [contenteditable='true']")) {
        return;
      }
      window.requestAnimationFrame(() => {
        // Undo any container scroll the browser applied on focus.
        container.scrollTop = 0;

        let body: HTMLElement | null = target.parentElement;
        while (body && body !== container) {
          const overflowY = window.getComputedStyle(body).overflowY;
          if (overflowY === "auto" || overflowY === "scroll") break;
          body = body.parentElement;
        }
        if (!body || body === container) return;

        const bodyRect = body.getBoundingClientRect();
        const targetRect = target.getBoundingClientRect();
        const delta =
          targetRect.top - bodyRect.top - (body.clientHeight - targetRect.height) / 2;
        body.scrollBy({ top: delta, behavior: "smooth" });
      });
    },
    [],
  );

  return (
    <DrawerPortal>
      <DrawerOverlay />
      <DrawerPrimitive.Content
        ref={ref}
        style={sheetStyle}
        onFocus={handleFocus}
        className={cn(
          "fixed inset-x-0 bottom-0 z-50 mt-24 flex h-auto flex-col rounded-t-[10px] border bg-background",
          className
        )}
        {...props}
      >
        <div className="mx-auto mt-4 h-2 w-[100px] shrink-0 rounded-full bg-muted" />
        {children}
      </DrawerPrimitive.Content>
    </DrawerPortal>
  );
})
DrawerContent.displayName = "DrawerContent"

const DrawerHeader = ({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) => (
  <div
    className={cn("grid gap-1.5 p-4 text-center sm:text-left", className)}
    {...props}
  />
)
DrawerHeader.displayName = "DrawerHeader"

const DrawerFooter = ({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) => (
  <div
    className={cn("mt-auto flex flex-col gap-2 p-4", className)}
    {...props}
  />
)
DrawerFooter.displayName = "DrawerFooter"

const DrawerTitle = React.forwardRef<
  React.ElementRef<typeof DrawerPrimitive.Title>,
  React.ComponentPropsWithoutRef<typeof DrawerPrimitive.Title>
>(({ className, ...props }, ref) => (
  <DrawerPrimitive.Title
    ref={ref}
    className={cn(
      "text-lg font-semibold leading-none tracking-tight",
      className
    )}
    {...props}
  />
))
DrawerTitle.displayName = DrawerPrimitive.Title.displayName

const DrawerDescription = React.forwardRef<
  React.ElementRef<typeof DrawerPrimitive.Description>,
  React.ComponentPropsWithoutRef<typeof DrawerPrimitive.Description>
>(({ className, ...props }, ref) => (
  <DrawerPrimitive.Description
    ref={ref}
    className={cn("text-sm text-muted-foreground", className)}
    {...props}
  />
))
DrawerDescription.displayName = DrawerPrimitive.Description.displayName

export {
  Drawer,
  DrawerTrigger,
  DrawerClose,
  DrawerContent,
  DrawerHeader,
  DrawerFooter,
  DrawerTitle,
  DrawerDescription,
}
