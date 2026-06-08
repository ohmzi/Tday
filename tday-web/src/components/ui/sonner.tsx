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
          // Normal / success / info share the neutral, slightly-translucent card
          // surface. Error + warning ("issue") toasts get a red translucent shade
          // (still backdrop-blurred); text stays on the popover foreground so it
          // remains legible over the tint.
          "--normal-bg": "hsl(var(--popover) / 0.92)",
          "--normal-text": "hsl(var(--popover-foreground))",
          "--normal-border": "hsl(var(--border))",
          "--success-bg": "hsl(var(--popover) / 0.92)",
          "--success-text": "hsl(var(--popover-foreground))",
          "--success-border": "hsl(var(--border))",
          "--info-bg": "hsl(var(--popover) / 0.92)",
          "--info-text": "hsl(var(--popover-foreground))",
          "--info-border": "hsl(var(--border))",
          "--warning-bg": "hsl(var(--destructive) / 0.18)",
          "--warning-text": "hsl(var(--popover-foreground))",
          "--warning-border": "hsl(var(--destructive) / 0.30)",
          "--error-bg": "hsl(var(--destructive) / 0.18)",
          "--error-text": "hsl(var(--popover-foreground))",
          "--error-border": "hsl(var(--destructive) / 0.30)",
        } as React.CSSProperties
      }
      {...props}
    />
  )
}

export { SonnerToaster }
