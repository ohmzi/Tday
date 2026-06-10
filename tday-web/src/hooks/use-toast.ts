import React from "react";
import { toast as sonnerToast } from "sonner";
import ClickableToast from "@/hooks/ClickableToast";

type ToastVariant = "default" | "destructive";

interface ToastAction {
  label: string;
  onClick: () => void;
}

interface ToastOptions {
  title?: string;
  description?: string;
  variant?: ToastVariant;
  duration?: number;
  onClick?: () => void;
  /** Optional action button (e.g. Undo) rendered inside the toast. */
  action?: ToastAction;
  /** Sonner pass-through: fires when the toast's timer elapses. */
  onAutoClose?: () => void;
  /** Sonner pass-through: fires when the toast is dismissed (swipe, close button, or programmatically). */
  onDismiss?: () => void;
}

function toast(options: ToastOptions) {
  const {
    title,
    description,
    variant,
    duration,
    onClick,
    action,
    onAutoClose,
    onDismiss,
  } = options;
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
          action: action
            ? {
                label: action.label,
                onClick: () => {
                  // Run the handler before dismissing so callers can flip
                  // state (e.g. an undo flag) ahead of the onDismiss callback.
                  action.onClick();
                  sonnerToast.dismiss(id);
                },
              }
            : undefined,
        }),
      {
        duration: duration ?? 5000,
        onAutoClose,
        onDismiss,
      },
    );
  }

  const opts: Parameters<typeof sonnerToast>[1] = {
    description: title ? description : undefined,
    duration: duration ?? 3000,
    action,
    onAutoClose,
    onDismiss,
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
