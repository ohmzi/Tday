import { useTheme } from "next-themes"
import { Toaster as Sonner, type ToasterProps } from "sonner"

const SonnerToaster = ({ ...props }: ToasterProps) => {
  const { theme = "system" } = useTheme()

  return (
    <Sonner
      theme={theme as ToasterProps["theme"]}
      className="toaster group"
      position="bottom-center"
      toastOptions={{
        classNames: {
          // Floating, on-brand pill (matches iOS): fully-rounded, translucent
          // blurred surface, subtle border, generous padding. Icons are removed
          // app-wide; all variants share one neutral surface.
          //
          // The background is set here as a Tailwind utility (not only via the
          // sonner --*-bg vars) so it also applies to custom/clickable toasts,
          // which render with data-styled="false" and therefore skip sonner's
          // own background rule. Without this the outer <li> would be a
          // transparent bordered box wrapping the inner card → a double box.
          //
          // The explicit `text-[13px]` base is required so custom/clickable toasts
          // match plain ones: custom toasts render data-styled="false", so sonner's
          // own `font-size:13px` rule (gated on [data-styled=true]) never applies and
          // they'd otherwise fall back to the document's larger size.
          toast:
            "group toast group-[.toaster]:flex group-[.toaster]:items-center group-[.toaster]:gap-3 group-[.toaster]:rounded-full group-[.toaster]:border group-[.toaster]:border-border/60 group-[.toaster]:bg-popover/55 group-[.toaster]:px-4 group-[.toaster]:py-3.5 group-[.toaster]:text-[13px] group-[.toaster]:backdrop-blur-xl group-[.toaster]:shadow-[0_10px_30px_-12px_hsl(var(--shadow)/0.45)]",
          // No icon — hide Sonner's default per-variant icon slot entirely.
          // !important is required: the plain `hidden` ties specificity with
          // sonner's runtime-injected `[data-styled=true] [data-icon]{display:flex}`
          // and loses on source order, so the icon would otherwise still show.
          icon: "group-[.toast]:!hidden",
          // Content fills the pill and centers its text horizontally.
          content: "group-[.toast]:min-w-0 group-[.toast]:flex-1 group-[.toast]:text-center",
          title: "group-[.toast]:font-extrabold group-[.toast]:leading-tight group-[.toast]:text-center",
          description:
            "group-[.toast]:mt-0.5 group-[.toast]:text-current/75 group-[.toast]:font-medium group-[.toast]:leading-snug group-[.toast]:text-center",
          actionButton:
            "group-[.toast]:rounded-full group-[.toast]:bg-primary group-[.toast]:px-3 group-[.toast]:font-bold group-[.toast]:text-primary-foreground",
          cancelButton:
            "group-[.toast]:rounded-full group-[.toast]:bg-muted group-[.toast]:px-3 group-[.toast]:font-bold group-[.toast]:text-muted-foreground",
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
