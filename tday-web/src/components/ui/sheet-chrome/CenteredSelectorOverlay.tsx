import type { ReactNode } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { Check } from "lucide-react";
import { cn } from "@/lib/utils";

// Centered overlay selector — mirrors iOS TdayCenteredSelectorCard. Built on
// Radix Dialog so it portals to <body> and stacks (z-[60]) above the vaul drawer
// and the calendar Modal (both z-50). Nested Radix dismissable layers mean
// interacting with this overlay does not dismiss the sheet beneath it.

export function CenteredSelectorOverlay({
  open,
  onOpenChange,
  title,
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  children: ReactNode;
}) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-[60] bg-black/45 data-[state=open]:animate-in data-[state=open]:fade-in-0" />
        <Dialog.Content
          aria-describedby={undefined}
          onOpenAutoFocus={(e) => e.preventDefault()}
          className="fixed left-1/2 top-1/2 z-[60] w-[min(330px,calc(100vw-3rem))] max-h-[80dvh] -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-[32px] border border-white/70 bg-card shadow-[0_30px_80px_-30px_hsl(var(--shadow)/0.7)] dark:border-white/10 data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95"
        >
          <Dialog.Title className="px-5 pb-3 pt-5 text-lg font-black text-muted-foreground">
            {title}
          </Dialog.Title>
          <div className="pb-3">{children}</div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

export function SelectorRow({
  label,
  swatchClass,
  swatchNode,
  selected,
  onClick,
}: {
  label: ReactNode;
  swatchClass?: string;
  swatchNode?: ReactNode;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={selected}
      className="flex w-full items-center gap-3.5 px-5 py-3 text-left transition-colors hover:bg-muted-foreground/5"
    >
      {swatchNode ? (
        <span className="flex h-2.5 w-2.5 shrink-0 items-center justify-center">
          {swatchNode}
        </span>
      ) : (
        <span
          className={cn(
            "h-2.5 w-2.5 shrink-0 rounded-full",
            swatchClass ?? "bg-transparent",
          )}
        />
      )}
      <span className="min-w-0 flex-1 truncate text-lg font-black text-foreground">
        {label}
      </span>
      {selected ? (
        <Check className="h-[18px] w-[18px] shrink-0 text-accent" />
      ) : (
        <span className="h-[18px] w-[18px] shrink-0" aria-hidden />
      )}
    </button>
  );
}

export function SelectorDivider() {
  return <div className="mx-5 h-px bg-muted-foreground/15" />;
}

export function SelectorDoneButton({
  onClick,
  label = "Done",
}: {
  onClick: () => void;
  label?: string;
}) {
  return (
    <div className="px-4 pb-1 pt-2">
      <button
        type="button"
        onClick={onClick}
        className="w-full rounded-2xl bg-muted/70 py-3 text-base font-black text-accent transition-colors hover:bg-muted"
      >
        {label}
      </button>
    </div>
  );
}
