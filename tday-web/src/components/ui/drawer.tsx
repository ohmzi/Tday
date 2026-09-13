import * as React from "react"
import { Drawer as DrawerPrimitive } from "vaul"

import { useFadeUnmount } from "@/hooks/useFadeUnmount"
import { DURATION_MS } from "@/lib/motion"
import { cn } from "@/lib/utils"

/**
 * How long a drawer's exit is given to play.
 *
 * vaul animates the sheet and its scrim from a stylesheet it injects at import —
 * half a second each way. That half-second is a library default rather than a
 * decision made here, the same standing `docs/motion.md` gives Compose's
 * `StiffnessMediumLow`, and the enter keeps it: the identical 0.5s is written
 * inline onto the sheet as the transition a released drag settles on, so
 * retiming the way in without the way a drag lands splits one gesture in two.
 *
 * The exit is a decision, and it is on a rung. `globals.css` plays it there and
 * this is the same rung in milliseconds, read by `useDrawerPresence` — the
 * `MODAL_EXIT_MS` arrangement exactly: one number read twice cannot drift, and
 * two numbers tuned to look alike is how an exit ends up half-played.
 */
export const DRAWER_EXIT_MS = DURATION_MS.emphasis;

/**
 * Whether a caller's drawer subtree should still be rendered right now — true
 * while it is open, and for `DRAWER_EXIT_MS` after it closes.
 *
 * The drawer's answer to `useModalPresence`, and it exists for that hook's
 * reason: the guard belongs to whoever decides to render the drawer at all, and
 * nothing `DrawerContent` does inside itself survives a parent that stops
 * rendering it. It is a separate hook rather than a duration argument because
 * the two surfaces leave on different clocks and the caller should not have to
 * remember which — the calendar's form sheet spent a release waiting out the
 * modal's 200 ms, which took it away mid-slide and took the confirm sheet
 * stacked on top of it in the same frame.
 */
export function useDrawerPresence(open: boolean): boolean {
  return useFadeUnmount(open, DRAWER_EXIT_MS);
}

/**
 * Which drawers are open right now, by identity.
 *
 * Module scope rather than a context because the drawers that stack are not
 * nested in the tree: the calendar's confirm sheet is a SIBLING of the form
 * sheet it covers (`EditDrawer`, `CreateDrawer`), so there is no provider either
 * of them is inside of. What they do share is the screen, and that is what this
 * tracks.
 *
 * Identities rather than a count because a scrim has to be able to leave itself
 * out of the answer, and it cannot do that by subtracting one: whether its own
 * drawer is in the set by the time the scrim asks depends on which commit vaul
 * mounts the portal in, which is vaul's business rather than a fact to build on.
 */
const openDrawerIds = new Set<string>();

/** This scrim's own drawer, so it can tell itself apart from the stack. */
const DrawerIdContext = React.createContext<string | null>(null);

/**
 * Holds this drawer in `openDrawerIds` for as long as it is open.
 *
 * Deliberately keyed on the caller's flag rather than on the scrim being in the
 * document, which is what an earlier version registered on and is a different,
 * longer span: Radix's `Presence` keeps a closed overlay mounted until its exit
 * animation reports `animationend`, so a drawer registered by mount stays
 * registered for the whole `DRAWER_EXIT_MS`. A second sheet opened inside that
 * window — two calendar rows tapped in the same third of a second, or a sheet
 * reopened off the one just dismissed — found a scrim "already up" that was on
 * its way out, declined to dim, and then stayed undimmed for its whole life,
 * because the answer below is taken once. Registering on the flag closes the
 * window: the entry is dropped on the frame the drawer is told to close, while
 * the scrim it belongs to is still fading.
 */
function useDrawerIsOpenRegistration(id: string, open: boolean): void {
  React.useLayoutEffect(() => {
    if (!open) return;
    openDrawerIds.add(id);
    return () => {
      openDrawerIds.delete(id);
    };
  }, [id, open]);
}

const Drawer = ({
  shouldScaleBackground = true,
  // We own keyboard handling via the visual viewport (see useViewportSheetMetrics
  // + DrawerContent). vaul's built-in input repositioning fights that math and
  // leaves the sheet shifted after the keyboard dismisses, so disable it.
  repositionInputs = false,
  open,
  defaultOpen,
  onOpenChange,
  ...props
}: React.ComponentProps<typeof DrawerPrimitive.Root>) => {
  // vaul's root can be driven or left to itself, and the registry has to be
  // right either way — `CustomRepeatDrawer` opens from a `DrawerTrigger` and
  // passes no flag at all. Mirror every change vaul reports and defer to the
  // caller's flag whenever there is one; `open` still goes to vaul untouched, so
  // which of the two is in charge is vaul's decision, not a second one made here.
  const [uncontrolledOpen, setUncontrolledOpen] = React.useState(defaultOpen ?? false);
  const handleOpenChange = React.useCallback(
    (next: boolean) => {
      setUncontrolledOpen(next);
      onOpenChange?.(next);
    },
    [onOpenChange],
  );

  const id = React.useId();
  useDrawerIsOpenRegistration(id, open ?? uncontrolledOpen);

  return (
    <DrawerIdContext.Provider value={id}>
      <DrawerPrimitive.Root
        shouldScaleBackground={shouldScaleBackground}
        repositionInputs={repositionInputs}
        open={open}
        defaultOpen={defaultOpen}
        onOpenChange={handleOpenChange}
        {...props}
      />
    </DrawerIdContext.Provider>
  );
}
Drawer.displayName = "Drawer"

const DrawerTrigger = DrawerPrimitive.Trigger

const DrawerPortal = DrawerPrimitive.Portal

const DrawerClose = DrawerPrimitive.Close

/**
 * Whether this scrim is the one dimming the page, or a second one over a page
 * that is already dim.
 *
 * Opacity composes. Two `black/80` sheets over the same pixels resolve to 96%
 * black — darker than either was drawn to be, and darker than any surface in the
 * app — so a confirm sheet opened over a drawer arrived on what read as a
 * different colour scheme. A scrim that finds one already up therefore draws no
 * dim of its own: the dim it wants is the one that is already there.
 *
 * Decided when the scrim arrives and never revised afterwards. Recomputing when
 * the sheet underneath leaves would be the more principled rule and would look
 * worse — two stacked sheets are usually dismissed together, and the nested
 * scrim would turn from transparent to 80% black for the last frames of its own
 * exit. A flash on the way out is most of what this is here to remove. What
 * makes that safe is that what it reads is the set of drawers that are OPEN,
 * not of scrims still in the document (`useDrawerIsOpenRegistration`): a "yes"
 * taken here is about a sheet that is staying, not one already leaving.
 *
 * Read in a layout effect rather than during render because a double-invoked
 * render would ask twice and, more to the point, would ask before the commit the
 * answer belongs to. The effect and the state it sets both land before the
 * browser paints, so the first frame is already the right one.
 *
 * "Any drawer but mine" rather than "more than one drawer", because whether this
 * scrim's own root has registered by the time this runs depends on which commit
 * vaul mounts the portal in. Asked this way the question has the same answer
 * either way.
 */
function useScrimIsNested(): boolean {
  const ownId = React.useContext(DrawerIdContext);
  const [nested, setNested] = React.useState(false);

  React.useLayoutEffect(() => {
    for (const id of openDrawerIds) {
      if (id !== ownId) {
        setNested(true);
        return;
      }
    }
  }, [ownId]);

  return nested;
}

const DrawerOverlay = React.forwardRef<
  React.ElementRef<typeof DrawerPrimitive.Overlay>,
  React.ComponentPropsWithoutRef<typeof DrawerPrimitive.Overlay>
>(({ className, ...props }, ref) => {
  const nested = useScrimIsNested();

  return (
    <DrawerPrimitive.Overlay
      ref={ref}
      // The node stays, and stays catching pointers: the scrim is what a tap
      // outside the sheet lands on, and what vaul releases a drag against. Only
      // the dim is dropped.
      data-nested-scrim={nested ? "true" : undefined}
      className={cn(
        "fixed inset-0 z-50",
        nested ? "bg-transparent" : "bg-black/80",
        className,
      )}
      {...props}
    />
  );
})
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
