import React from "react";
import { toast as sonnerToast } from "sonner";
import ClickableToast from "@/hooks/ClickableToast";

type ToastVariant = "default" | "destructive";

interface ToastOptions {
  title?: string;
  description?: string;
  variant?: ToastVariant;
  duration?: number;
  onClick?: () => void;
}

function toast(options: ToastOptions) {
  const { title, description, variant, duration, onClick } = options;
  const message = title || description || "";

  if (onClick) {
    return sonnerToast.custom(
      (id) =>
        React.createElement(ClickableToast, {
          title: message,
          description: title ? description : undefined,
          variant,
          onClick: () => {
            sonnerToast.dismiss(id);
            onClick();
          },
        }),
      {
        duration: duration ?? 5000,
      },
    );
  }

  const opts: Parameters<typeof sonnerToast>[1] = {
    description: title ? description : undefined,
    duration: duration ?? 3000,
  };

  if (variant === "destructive") {
    return sonnerToast.error(message, opts);
  }

  return sonnerToast(message, opts);
}

/**
 * Compatibility hook — keeps the same API so existing callers don't break.
 * Sonner doesn't need a hook (it's imperative), but we wrap it so the
 * pattern `const { toast } = useToast()` still works everywhere.
 */
function useToast() {
  return {
    toast,
    dismiss: (id?: string | number) => {
      if (id !== undefined) {
        sonnerToast.dismiss(id);
      } else {
        sonnerToast.dismiss();
      }
    },
    toasts: [] as never[], // Sonner manages its own state
  };
}

export { useToast }
