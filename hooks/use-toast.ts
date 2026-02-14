"use client"

import { toast as sonnerToast } from "sonner"

type ToastVariant = "default" | "destructive";

interface ToastOptions {
  title?: string;
  description?: string;
  variant?: ToastVariant;
  duration?: number;
}

function toast(options: ToastOptions) {
  const { title, description, variant, duration } = options;
  const message = title || description || "";
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

export { useToast, toast }
