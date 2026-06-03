import type { ReactNode } from "react";
import { Check, ChevronDown, X } from "lucide-react";
import { format } from "date-fns";
import { cn } from "@/lib/utils";

// Native sheet chrome — mirrors ios-swiftUI/Tday/UI/Component/TdaySheetChrome.swift.
// Shared by the task sheet, list sheet, and calendar create/edit forms so every
// surface reads from one visual language: a circular X / title / ✓ header,
// rounded section cards, and icon+label+value selector rows.

const CLOSE_ACCENT = "227, 90, 90"; // #E35A5A
const CONFIRM_ACCENT = "47, 163, 91"; // #2FA35B

export function SheetActionButton({
  icon,
  accentRgb,
  disabled,
  onClick,
  ariaLabel,
}: {
  icon: ReactNode;
  accentRgb: string;
  disabled?: boolean;
  onClick?: () => void;
  ariaLabel: string;
}) {
  return (
    <button
      type="button"
      aria-label={ariaLabel}
      aria-disabled={disabled}
      disabled={disabled}
      onClick={onClick}
      style={{ borderColor: `rgba(${accentRgb}, ${disabled ? 0.3 : 0.6})` }}
      className={cn(
        "flex h-12 w-12 shrink-0 items-center justify-center rounded-full border-[1.5px] bg-card text-foreground shadow-sm transition-all active:scale-95",
        disabled ? "opacity-55" : "hover:bg-card/80",
      )}
    >
      {icon}
    </button>
  );
}

export function SheetHeader({
  title,
  onClose,
  onConfirm,
  confirmDisabled,
  confirmLabel = "Done",
  closeLabel = "Cancel",
}: {
  title: ReactNode;
  onClose: () => void;
  onConfirm?: () => void;
  confirmDisabled?: boolean;
  confirmLabel?: string;
  closeLabel?: string;
}) {
  return (
    <div className="flex items-center gap-3 px-4 pb-2 pt-3 sm:px-5">
      <SheetActionButton
        icon={<X className="h-5 w-5 stroke-[2.6]" />}
        accentRgb={CLOSE_ACCENT}
        onClick={onClose}
        ariaLabel={closeLabel}
      />
      <h2 className="min-w-0 flex-1 truncate text-center text-2xl font-black tracking-tight text-foreground">
        {title}
      </h2>
      {onConfirm ? (
        <SheetActionButton
          icon={<Check className="h-5 w-5 stroke-[2.6]" />}
          accentRgb={CONFIRM_ACCENT}
          disabled={confirmDisabled}
          onClick={onConfirm}
          ariaLabel={confirmLabel}
        />
      ) : (
        <span className="h-12 w-12 shrink-0" aria-hidden />
      )}
    </div>
  );
}

export function SheetSectionTitle({ children }: { children: ReactNode }) {
  return (
    <p className="px-1 text-sm font-black text-muted-foreground">{children}</p>
  );
}

export function SheetCard({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "overflow-hidden rounded-[28px] border border-white/70 bg-card/95 dark:border-white/10",
        className,
      )}
    >
      {children}
    </div>
  );
}

export function SheetDivider() {
  return <div className="mx-[18px] h-px bg-muted-foreground/15" />;
}

export function SheetRow({
  icon,
  label,
  children,
  className,
}: {
  icon: ReactNode;
  label: ReactNode;
  children?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex min-h-[60px] items-center gap-3.5 px-4 py-3", className)}>
      <span className="flex h-[22px] w-[22px] shrink-0 items-center justify-center text-muted-foreground">
        {icon}
      </span>
      <span className="text-lg font-black text-foreground">{label}</span>
      {children ? (
        <span className="ml-auto flex min-w-0 items-center">{children}</span>
      ) : null}
    </div>
  );
}

export function SheetSelectorRow({
  icon,
  label,
  value,
  onClick,
  disabled,
  ariaLabel,
}: {
  icon: ReactNode;
  label: string;
  value: ReactNode;
  onClick: () => void;
  disabled?: boolean;
  ariaLabel?: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-haspopup="dialog"
      aria-label={ariaLabel}
      className={cn(
        "flex min-h-[56px] w-full items-center gap-3.5 px-4 py-3 text-left transition-colors active:bg-muted-foreground/5",
        disabled ? "opacity-45" : "hover:bg-muted-foreground/5",
      )}
    >
      <span className="flex h-[22px] w-[22px] shrink-0 items-center justify-center text-muted-foreground">
        {icon}
      </span>
      <span className="text-lg font-black text-foreground">{label}</span>
      <span className="ml-auto flex min-w-0 items-center gap-1.5">
        <span className="flex min-w-0 items-center gap-1.5 truncate text-sm font-black text-muted-foreground">
          {value}
        </span>
        <ChevronDown className="h-3.5 w-3.5 shrink-0 text-muted-foreground/70" />
      </span>
    </button>
  );
}

export function DueDateTimeControl({
  due,
  onDateClick,
  onTimeClick,
}: {
  due: Date;
  onDateClick: () => void;
  onTimeClick: () => void;
}) {
  return (
    <div className="flex items-stretch overflow-hidden rounded-2xl border border-muted-foreground/25 bg-card/40">
      <button
        type="button"
        aria-label="Due date"
        onClick={onDateClick}
        className="px-3 py-2 text-sm font-black text-muted-foreground transition-colors hover:text-foreground"
      >
        {format(due, "EEE, MMM d")}
      </button>
      <span className="my-1.5 w-px bg-muted-foreground/25" />
      <button
        type="button"
        aria-label="Due time"
        onClick={onTimeClick}
        className="px-3 py-2 text-sm font-black text-muted-foreground transition-colors hover:text-foreground"
      >
        {format(due, "h:mm a")}
      </button>
    </div>
  );
}
