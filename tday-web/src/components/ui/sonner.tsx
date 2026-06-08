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
          // Floating, on-brand card: rounded-24, translucent blurred surface,
          // subtle border, generous padding. Icons are removed app-wide; the
          // variant cue now lives in the card surface itself (issue/error/warning
          // get a red translucent shade — see the per-type --*-bg vars below).
          toast:
            "group toast group-[.toaster]:flex group-[.toaster]:items-center group-[.toaster]:gap-3 group-[.toaster]:rounded-[24px] group-[.toaster]:border group-[.toaster]:px-4 group-[.toaster]:py-3.5 group-[.toaster]:backdrop-blur-xl group-[.toaster]:shadow-[0_10px_30px_-12px_hsl(var(--shadow)/0.45)]",
          // No icon — hide Sonner's default per-variant icon slot entirely.
          icon: "group-[.toast]:hidden",
          content: "group-[.toast]:min-w-0",
          title: "group-[.toast]:font-extrabold group-[.toast]:leading-tight",
          description:
            "group-[.toast]:mt-0.5 group-[.toast]:text-current/75 group-[.toast]:font-medium group-[.toast]:leading-snug",
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
