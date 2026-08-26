import type { ReactNode } from "react";
import { ChevronRight, type LucideIcon } from "lucide-react";
import { Link } from "@/lib/navigation";
import { cn } from "@/lib/utils";

/** Leading row glyph — a single Lucide icon in a fixed 22px slot so every label
 * in a card starts on the same line. Decorative: the row label carries the
 * meaning, so it stays out of the accessibility tree. */
export function RowIcon({
  icon: Icon,
  destructive,
}: {
  icon: LucideIcon;
  destructive?: boolean;
}) {
  return (
    <span
      aria-hidden
      className={cn(
        "flex h-[22px] w-[22px] shrink-0 items-center justify-center",
        destructive ? "text-destructive" : "text-accent",
      )}
    >
      <Icon className="h-5 w-5" />
    </span>
  );
}

/** The same slot left empty — reserves the icon column for a glyph-less fact
 * row so its label still lines up with the rows around it. */
export function RowIconSlot() {
  return <span aria-hidden className="h-[22px] w-[22px] shrink-0" />;
}

/** Thin divider between sub-sections within a card. */
export function CardDivider() {
  return <div className="h-px bg-border/60" />;
}

/** Tappable row — icon, label and an optional chevron. Renders a Link when
 * `href` is given, a button otherwise. The chevron means "this opens another
 * screen": a row that acts in place (download, encrypt, reset) leaves it off.
 * `destructive` colours the whole row; `destructiveIcon` colours the glyph
 * alone, for a row whose label has always been the neutral foreground. */
export function SettingsOptionRow({
  icon,
  label,
  href,
  onClick,
  disabled,
  destructive,
  destructiveIcon,
  showChevron,
  trailing,
}: {
  icon: LucideIcon;
  label: ReactNode;
  href?: string;
  onClick?: () => void;
  disabled?: boolean;
  destructive?: boolean;
  destructiveIcon?: boolean;
  showChevron?: boolean;
  /** Right-hand slot for a row that reports progress (a spinner) rather than
   * a destination. */
  trailing?: ReactNode;
}) {
  const content = (
    <>
      <span className="flex min-w-0 flex-1 items-center gap-3.5">
        <RowIcon icon={icon} destructive={destructive || destructiveIcon} />
        <span
          className={cn(
            "min-w-0 flex-1 text-[1.05rem] font-black",
            // Only the chevron rows clipped their label before the icons
            // landed; the others wrap, which the longer translations rely on.
            showChevron && "truncate",
            destructive ? "text-destructive" : "text-foreground",
          )}
        >
          {label}
        </span>
      </span>
      {trailing}
      {showChevron ? (
        <ChevronRight className="h-5 w-5 shrink-0 text-muted-foreground" />
      ) : null}
    </>
  );
  const rowClass = "flex w-full items-center gap-3 py-1.5 text-left transition active:opacity-60";

  if (href) {
    return (
      <Link href={href} className={rowClass}>
        {content}
      </Link>
    );
  }
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={cn(rowClass, "disabled:opacity-50")}
    >
      {content}
    </button>
  );
}

/**
 * The one settings pill, shared by every platform: a continuous capsule filled
 * with the accent at 12%, accent-coloured content, a heavy 12px glyph and a
 * heavy 14px label 5px apart, 34 tall with 14 of horizontal padding — the spec
 * iOS's `SettingsInlineEditButton` sets.
 *
 * With `onClick` it is the tap target (Edit, Change). Without one it renders a
 * span, so a row that is already a button can show its current value in a pill
 * without nesting a button inside a button.
 */
export function SettingsPill({
  icon: Icon,
  label,
  onClick,
  className,
}: {
  icon: LucideIcon;
  label: string;
  onClick?: () => void;
  className?: string;
}) {
  const content = (
    <>
      <Icon className="h-3 w-3 shrink-0" strokeWidth={2.75} aria-hidden />
      <span className="min-w-0 truncate">{label}</span>
    </>
  );
  const pillClass = cn(
    "inline-flex h-[34px] min-w-0 shrink-0 items-center gap-[5px] rounded-full bg-accent/[0.12] px-3.5 text-sm font-black text-accent",
    className,
  );

  if (!onClick) {
    return <span className={pillClass}>{content}</span>;
  }
  return (
    <button type="button" onClick={onClick} className={cn(pillClass, "transition active:opacity-70")}>
      {content}
    </button>
  );
}
