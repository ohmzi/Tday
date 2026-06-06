import {
  CircleCheckIcon,
  InfoIcon,
  Loader2Icon,
  OctagonXIcon,
  TriangleAlertIcon,
} from "lucide-react"
import { useTheme } from "next-themes"
import { Toaster as Sonner, type ToasterProps } from "sonner"

const SonnerToaster = ({ ...props }: ToasterProps) => {
  const { theme = "system" } = useTheme()

  return (
    <Sonner
      theme={theme as ToasterProps["theme"]}
      className="toaster group"
      position="top-center"
      icons={{
        success: <CircleCheckIcon className="size-[18px]" />,
        info: <InfoIcon className="size-[18px]" />,
        warning: <TriangleAlertIcon className="size-[18px]" />,
        error: <OctagonXIcon className="size-[18px]" />,
        loading: <Loader2Icon className="size-[18px] animate-spin" />,
      }}
      toastOptions={{
        classNames: {
          // Floating, on-brand card: rounded-24, translucent blurred surface,
          // subtle border, generous padding. Text stays on-surface; the variant
          // colour lives in the leading icon chip (see per-type classes below).
          toast:
            "group toast group-[.toaster]:flex group-[.toaster]:items-center group-[.toaster]:gap-3 group-[.toaster]:rounded-[24px] group-[.toaster]:border group-[.toaster]:px-4 group-[.toaster]:py-3.5 group-[.toaster]:backdrop-blur-xl group-[.toaster]:shadow-[0_10px_30px_-12px_hsl(var(--shadow)/0.45)]",
          // The icon is rendered inside a tinted circular chip.
          icon:
            "group-[.toast]:m-0 group-[.toast]:flex group-[.toast]:size-9 group-[.toast]:shrink-0 group-[.toast]:items-center group-[.toast]:justify-center group-[.toast]:rounded-full",
          content: "group-[.toast]:min-w-0",
          title: "group-[.toast]:font-extrabold group-[.toast]:leading-tight",
          description:
            "group-[.toast]:mt-0.5 group-[.toast]:text-current/75 group-[.toast]:font-medium group-[.toast]:leading-snug",
          actionButton:
            "group-[.toast]:rounded-full group-[.toast]:bg-primary group-[.toast]:px-3 group-[.toast]:font-bold group-[.toast]:text-primary-foreground",
          cancelButton:
            "group-[.toast]:rounded-full group-[.toast]:bg-muted group-[.toast]:px-3 group-[.toast]:font-bold group-[.toast]:text-muted-foreground",
          // Per-variant icon-chip tint (the toast surface itself stays neutral).
          error:
            "[&_[data-icon]]:bg-destructive/15 [&_[data-icon]]:text-destructive",
          success:
            "[&_[data-icon]]:bg-accent-lime/15 [&_[data-icon]]:text-accent-lime",
          info: "[&_[data-icon]]:bg-accent/15 [&_[data-icon]]:text-accent",
          warning:
            "[&_[data-icon]]:bg-accent-orange/15 [&_[data-icon]]:text-accent-orange",
          loading: "[&_[data-icon]]:bg-muted [&_[data-icon]]:text-muted-foreground",
        },
      }}
      style={
        {
          // All variants share the same neutral, slightly-translucent card
          // surface; the colour cue comes from the icon chip, matching the
          // app's card design language.
          "--normal-bg": "hsl(var(--popover) / 0.92)",
          "--normal-text": "hsl(var(--popover-foreground))",
          "--normal-border": "hsl(var(--border))",
          "--success-bg": "hsl(var(--popover) / 0.92)",
          "--success-text": "hsl(var(--popover-foreground))",
          "--success-border": "hsl(var(--border))",
          "--info-bg": "hsl(var(--popover) / 0.92)",
          "--info-text": "hsl(var(--popover-foreground))",
          "--info-border": "hsl(var(--border))",
          "--warning-bg": "hsl(var(--popover) / 0.92)",
          "--warning-text": "hsl(var(--popover-foreground))",
          "--warning-border": "hsl(var(--border))",
          "--error-bg": "hsl(var(--popover) / 0.92)",
          "--error-text": "hsl(var(--popover-foreground))",
          "--error-border": "hsl(var(--border))",
        } as React.CSSProperties
      }
      {...props}
    />
  )
}

export { SonnerToaster }
