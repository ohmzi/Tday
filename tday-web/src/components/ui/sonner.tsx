import { useTheme } from "next-themes"
import { Toaster as Sonner, type ToasterProps } from "sonner"

const SonnerToaster = ({ ...props }: ToasterProps) => {
  const { theme = "system" } = useTheme()

  return (
    <Sonner
      theme={theme as ToasterProps["theme"]}
      className="toaster group"
      position="bottom-center"
      // Lift the toast above the bottom RootDock (fixed at 18px + safe-area,
      // h-16 = 64px tall, so its top is ~82px up) so it never overlaps the
      // Scheduled/Floater dock + FAB. ~12px gap above the dock.
      offset={{ bottom: "calc(env(safe-area-inset-bottom) + 94px)" }}
      mobileOffset={{ bottom: "calc(env(safe-area-inset-bottom) + 94px)" }}
      toastOptions={{
        classNames: {
          // Floating, on-brand pill (matches iOS): fully-rounded, translucent
          // blurred surface, subtle border, generous padding. Each variant's own
          // status glyph is removed (see `icon` below) so success/error/info/
          // normal all share one neutral surface — the one deliberate exception
          // is the Undo action itself, which is icon-based (see `actionButton`
          // below and use-undoable-delete.tsx).
          //
          // The background is set here as a Tailwind utility (not only via the
          // sonner --*-bg vars) so it also applies to custom/clickable toasts,
          // which render with data-styled="false" and therefore skip sonner's
          // own background rule. Without this the outer <li> would be a
          // transparent bordered box wrapping the inner card → a double box.
          //
          // EVERY surface/typography property here carries `!` — and it has to.
          // Sonner injects its sheet UNLAYERED at runtime
          // (`__insertCSS` → `head.appendChild(<style>)`,
          // node_modules/sonner/dist/index.js), while Tailwind v4 emits every
          // utility inside `@layer utilities`. Unlayered normal declarations
          // beat layered normal declarations regardless of specificity, so
          // sonner's stock
          // `[data-sonner-toast][data-styled=true]{border-radius:var(--border-radius);
          // padding:16px;font-size:13px;gap:6px;box-shadow:0 4px 12px rgba(0,0,0,.1)}`
          // silently won every one of these for PLAIN toasts: `rounded-full` was
          // in the class list, compiled, and did nothing, so every plain toast
          // rendered as sonner's stock 8px/13px box while only the custom ones
          // (which skip that rule, `data-styled="false"`) wore the pill this
          // config describes — two families, not one. `!` moves each declaration
          // into the important tier, which outranks unlayered normal no matter
          // what. (The `actionButton`/`cancelButton` lists below always did this;
          // it is this same mechanism, not a source-order tie.) Do NOT drop a
          // `!`: the class stays in the list and silently stops applying.
          //
          // The explicit `text-[15px]` base is what makes a custom/clickable
          // toast (which falls back to the document size otherwise — sonner's
          // own font-size rule is gated on [data-styled=true]) the same size as
          // a plain one. 15px + py-3.5 matches iOS's toast height (subheadline
          // ~15pt + 14pt vertical padding) for cross-platform parity.
          //
          // `min-[601px]:!w-full` is the width half of the same job: sonner only
          // sets a toast's width under `[data-styled=true]{width:var(--width)}`,
          // so a custom toast shrink-to-fits (~160px against every plain toast's
          // 356px). Phones are excluded on purpose — sonner's
          // `@media (max-width:600px) [data-sonner-toaster] [data-sonner-toast]`
          // width rule is NOT gated on data-styled, so it already gives custom
          // and plain toasts one width there (601px mirrors the end of that
          // query), and forcing 100% inside it would overflow the inset toaster.
          toast:
            "group toast group-[.toaster]:!flex group-[.toaster]:!items-center group-[.toaster]:!gap-3 group-[.toaster]:!rounded-full group-[.toaster]:!border group-[.toaster]:!border-border/60 group-[.toaster]:!bg-popover/55 group-[.toaster]:!px-4 group-[.toaster]:!py-3.5 group-[.toaster]:!text-[15px] group-[.toaster]:!backdrop-blur-xl group-[.toaster]:!shadow-[0_10px_30px_-12px_hsl(var(--shadow)/0.45)] group-[.toaster]:min-[601px]:!w-full",
          // No status glyph — hide Sonner's default per-variant icon slot
          // entirely (unrelated to the Undo action's own icon, below). Forced
          // for the same reason as the surface above: sonner's injected
          // `[data-styled=true] [data-icon]{display:flex}` is unlayered and
          // would otherwise still show the icon.
          icon: "group-[.toast]:!hidden",
          // Content fills the pill and centers its text horizontally. `!gap-0`
          // cancels sonner's unlayered `[data-styled=true] [data-content]{gap:2px}`
          // so a plain two-line toast spaces its description the way a custom
          // one does (the description's own mt-0.5), not by an extra 2px.
          content:
            "group-[.toast]:min-w-0 group-[.toast]:flex-1 group-[.toast]:!gap-0 group-[.toast]:text-center",
          title:
            "group-[.toast]:!font-extrabold group-[.toast]:!leading-tight group-[.toast]:text-center",
          // `text-[13px]` is forced, not inherited: the pill's 15px base would
          // otherwise carry into the description, while a custom toast's
          // description (ClickableToast) is 13px — this keeps the two families
          // identical. Weight/colour/leading are forced for the same reason as
          // the surface above (sonner's unlayered `[data-styled=true]
          // [data-description]{font-weight:400;line-height:1.4;color:#3f3f3f}`).
          description:
            "group-[.toast]:mt-0.5 group-[.toast]:!text-[13px] group-[.toast]:!text-current/75 group-[.toast]:!font-medium group-[.toast]:!leading-snug group-[.toast]:text-center",
          // Icon-based, not a filled pill — the Undo action is the one
          // deliberate reversal of "icons removed app-wide" (see the toast
          // comment above and use-undoable-delete.tsx), matching iOS's
          // AppSnackbar carrying an icon for the same action, with sign-off.
          // Colour still matters even though the label is no longer text:
          // the icon paints with `currentColor`, so the forced colour below
          // is what tints it.
          //
          // EVERY property here needs `!`, for the unlayered-sheet reason spelled
          // out on `toast` above. Sonner injects
          // `[data-sonner-toast][data-styled=true] [data-button]` at runtime and a
          // plain utility silently loses to it.
          // Colour especially: sonner paints the action `color: var(--normal-bg)`, i.e. the
          // toast's OWN surface colour, because it normally sits on a filled chip. Drop the
          // chip without forcing the colour and the icon is painted in the surface it sits
          // on — invisible. It also forces 12px/500 text sizing, so size and weight stay
          // pinned even though there is no text to size — dropping them is untested territory
          // this class list has no reason to wander into.
          actionButton:
            "group-[.toast]:!bg-transparent group-[.toast]:!px-0 group-[.toast]:!h-auto group-[.toast]:shrink-0 group-[.toast]:!text-[15px] group-[.toast]:!font-extrabold group-[.toast]:!text-[hsl(var(--toast-action))]",
          cancelButton:
            "group-[.toast]:!rounded-full group-[.toast]:!bg-muted group-[.toast]:!px-3 group-[.toast]:!font-bold group-[.toast]:!text-muted-foreground",
        },
      }}
      style={
        {
          // Every toast is the same frosted-glass pill (matches iOS): a light,
          // translucent surface over a backdrop blur — soft, not the old opaque
          // white pill. All variants share one neutral surface (no per-type red
          // tint, no icon) so offline/error/success/normal all look identical.
          "--normal-bg": "hsl(var(--popover) / 0.55)",
          "--normal-text": "hsl(var(--popover-foreground))",
          "--normal-border": "hsl(var(--border) / 0.6)",
          "--success-bg": "hsl(var(--popover) / 0.55)",
          "--success-text": "hsl(var(--popover-foreground))",
          "--success-border": "hsl(var(--border) / 0.6)",
          "--info-bg": "hsl(var(--popover) / 0.55)",
          "--info-text": "hsl(var(--popover-foreground))",
          "--info-border": "hsl(var(--border) / 0.6)",
          "--warning-bg": "hsl(var(--popover) / 0.55)",
          "--warning-text": "hsl(var(--popover-foreground))",
          "--warning-border": "hsl(var(--border) / 0.6)",
          "--error-bg": "hsl(var(--popover) / 0.55)",
          "--error-text": "hsl(var(--popover-foreground))",
          "--error-border": "hsl(var(--border) / 0.6)",
        } as React.CSSProperties
      }
      {...props}
    />
  )
}

export { SonnerToaster }
