"use client"

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
        success: <CircleCheckIcon className="size-4" />,
        info: <InfoIcon className="size-4" />,
        warning: <TriangleAlertIcon className="size-4" />,
        error: <OctagonXIcon className="size-4" />,
        loading: <Loader2Icon className="size-4 animate-spin" />,
      }}
      toastOptions={{
        classNames: {
          toast:
            "group toast group-[.toaster]:backdrop-blur-xl group-[.toaster]:shadow-lg group-[.toaster]:rounded-xl",
          description: "group-[.toast]:text-current/90",
          actionButton:
            "group-[.toast]:bg-primary group-[.toast]:text-primary-foreground",
          cancelButton:
            "group-[.toast]:bg-muted group-[.toast]:text-muted-foreground",
        },
      }}
      style={
        {
          "--normal-bg": "hsl(var(--accent))",
          "--normal-text": "hsl(var(--accent-foreground))",
          "--normal-border": "hsl(var(--form-button-accent))",
          "--success-bg": "hsl(var(--accent))",
          "--success-text": "hsl(var(--accent-foreground))",
          "--success-border": "hsl(var(--form-button-accent))",
          "--info-bg": "hsl(var(--accent))",
          "--info-text": "hsl(var(--accent-foreground))",
          "--info-border": "hsl(var(--form-button-accent))",
          "--warning-bg": "hsl(var(--accent))",
          "--warning-text": "hsl(var(--accent-foreground))",
          "--warning-border": "hsl(var(--form-button-accent))",
        } as React.CSSProperties
      }
      {...props}
    />
  )
}

export { SonnerToaster }
