// Tailwind class strings for the timeline drag-and-drop visuals, kept in one
// place so the section droppable, placeholder and drag overlay stay in sync.
// Mirrors the native lift / drop-placeholder / header-highlight treatment using
// CSS transitions + the app's `--destructive` token (no animation dependency).

// Vertical rhythm between timeline date sections. Driven by top margins only so the
// gap above each date is owned by that date — empty dates pull up tight, dates with
// tasks keep breathing room. Shared by the section droppable and the Overdue branch.
export const sectionTopGapFirst = "mt-2"; // first section, below the page title
export const sectionTopGapFilled = "mt-3"; // a date that has tasks
export const sectionTopGapEmpty = "mt-1"; // a date with no tasks — tight, native-style
export const headerToBodyGap = "mb-1.5"; // header → tasks/placeholder

// A section that is the active, valid drop target (whole-bucket highlight).
export const sectionActiveClass =
  "bg-destructive/[0.04] ring-1 ring-destructive/25";

// Section header text while a valid drop hovers the bucket.
export const headerActiveClass = "text-destructive";

// Drop placeholder: a faint dashed slot at rest, growing into a solid,
// destructive-tinted target while active. Honors reduced motion via Tailwind's
// motion-reduce variants.
export const placeholderBaseClass =
  "rounded-[18px] border border-dashed border-border/60 transition-all duration-200 ease-out motion-reduce:transition-none";
export const placeholderRestClass = "h-6 opacity-60";
export const placeholderActiveClass =
  "h-14 border-solid border-destructive/60 bg-destructive/[0.06] opacity-100 animate-in fade-in";

// The lifted card rendered inside the DragOverlay.
export const overlayCardClass =
  "pointer-events-none w-[min(20rem,80vw)] rounded-[20px] border border-white/70 bg-card px-4 py-3 shadow-[0_24px_48px_-20px_hsl(var(--shadow)/0.6)] ring-1 ring-destructive/25 dark:border-white/10";
