import type { ReactNode } from "react";
import { SheetCard } from "@/components/ui/sheet-chrome";

// Cohesive action-button styling shared by every row button (approve/deny/reset/
// delete/clear) so they read as one family: same height, radius, weight, and icon
// gap — full-width on mobile, auto-width from sm up. Colour conveys intent.
export const ACTION_BUTTON_BASE =
  "h-10 flex-1 gap-2 rounded-xl text-sm font-bold transition-colors sm:flex-none sm:px-4";
export const ACTION_PRIMARY = "bg-primary text-primary-foreground hover:bg-primary/90";
export const ACTION_NEUTRAL =
  "border border-border/60 bg-muted/50 text-foreground hover:bg-muted";
export const ACTION_DESTRUCTIVE =
  "border border-destructive/30 bg-destructive/10 text-destructive hover:bg-destructive/20";

/** Rounded grouped section card with a big ExtraBold title — mirrors the
 * native settings cards so the admin page feels at home on mobile. */
export const SectionCard = ({ title, children }: { title: string; children: ReactNode }) => (
  <SheetCard className="space-y-4 p-[18px]">
    <h2 className="text-[1.4rem] font-black leading-tight text-foreground">{title}</h2>
    {children}
  </SheetCard>
);
