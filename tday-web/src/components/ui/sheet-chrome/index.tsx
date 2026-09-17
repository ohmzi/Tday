import type { ReactNode } from "react";
import { Check, ChevronDown, X } from "lucide-react";
import { format } from "date-fns";
import { cn } from "@/lib/utils";
import { hapticConfirm, hapticDismiss } from "@/lib/haptics";

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
        onClick={() => { hapticDismiss(); onClose(); }}
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
          onClick={() => { hapticConfirm(); onConfirm?.(); }}
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

export function SheetDivider({ className }: { className?: string }) {
  return <div className={cn("mx-[18px] h-px bg-muted-foreground/15", className)} />;
}

/**
 * How much of the screen the title-and-notes block may claim before it stops
 * growing.
 *
 * Before this, a long note simply grew the card, and the card grew the sheet,
 * until the sheet hit its own ceiling — and then everything past the fold,
 * which by that point was most of the form *and* the rest of the note the user
 * was still typing, went below it. Reading your own note meant scrolling the
 * whole sheet, with the schedule and the list sliding up out of the way as you
 * went. So the pair gets a budget instead: the card stops here and the two
 * fields scroll inside it.
 *
 * Half the screen, because that is the point where the note is worth reading
 * where it sits — and the other half is what the rest of the form needs to stay
 * reachable. `dvh` rather than `vh` for the reason AppBottomSheet caps itself in
 * `dvh`: it is the same "screen", so it has to move with the browser chrome the
 * same way the sheet above it does.
 */
const TITLE_NOTES_MAX = "max-h-[50dvh]";

/**
 * The title's share of that budget.
 *
 * Half of the card's, which is what keeps a long title from starving the note
 * beside it: neither field can claim more than half the block, so both always have
 * a window. It is deliberately not a `flex-basis` that reserves this much — a share
 * would open every task sheet with a quarter-screen of empty title. As a cap, a
 * one-line title takes the line it needs and leaves the rest of the card to the note,
 * which is the shape of nearly every real task.
 *
 * The two windows do not then measure out pixel-equal, and should not be read as
 * claiming to: the title's slot is its text and nothing else, while the notes carry
 * their own padding and, while focused, the format bar below them — roughly 60 px of
 * chrome the title does not have. Measured at a 390×800 viewport with both fields at
 * their caps, that lands at 200 px of title against 136 px of notes. Both are several
 * lines of readable text, which is the point; the alternative is a `calc` subtracting
 * the format bar's height, which would go stale the day the bar's buttons change size.
 */
const TITLE_FIELD_MAX = "max-h-[25dvh]";

/**
 * The card every task sheet opens with: the title, a divider, and the notes.
 *
 * Three surfaces render this pair — the task sheet, the calendar form and the
 * floater sheet — and they render it identically. The caps above are therefore
 * spelled once, here, rather than three times at the call sites, where the one
 * that got missed would be the one nobody looked at. A notes field rendered
 * outside this card is an uncapped one; `tests/guardrails/notes-growth-cap`
 * holds the three to it.
 *
 * The two fields are `flex` siblings under the card's cap, which is what divides
 * the budget without either of them knowing the other's height. The title row is
 * `shrink-0`, so it never gives up the height its own cap allowed it; the notes
 * take the rest and shrink to it, which is where the scrolling comes from.
 * `SheetDivider` is pinned for the duller version of the same reason — as a
 * shrinkable one-pixel item it would be squeezed by the overflow rather than
 * sitting still between the two fields.
 */
export function SheetTitleNotesCard({
  title,
  titleAccessory,
  children,
}: {
  title: ReactNode;
  /** Rendered beside the title, outside its scroller, e.g. the guide help link. */
  titleAccessory?: ReactNode;
  children: ReactNode;
}) {
  return (
    <SheetCard className={cn("flex flex-col", TITLE_NOTES_MAX)}>
      <div className="flex shrink-0 items-start gap-2 px-[18px] pb-2 pt-3">
        <div className={cn("min-w-0 flex-1 overflow-y-auto", TITLE_FIELD_MAX)}>
          {title}
        </div>
        {titleAccessory}
      </div>
      <SheetDivider className="shrink-0" />
      {children}
    </SheetCard>
  );
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
    <div className="flex items-stretch overflow-hidden rounded-lg border border-muted-foreground/25 bg-card/40">
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
