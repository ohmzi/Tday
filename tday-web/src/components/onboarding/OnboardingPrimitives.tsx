import React from "react";
import { cn } from "@/lib/utils";

/**
 * The wizard's own building blocks, shared with the crash-report consent card so that card reads
 * as one more step of the same flow rather than a differently-dressed dialog.
 */

// Fixed tints lifted 1:1 from the native wizard (iOS/Android). These are
// intentionally theme-independent so the card reads identically across light
// and dark mode, matching the apps.
export const TINT = {
  modeGreen: "rgb(128, 184, 138)", // step chip · "Mode" · "This device" tile
  serverBlue: "rgb(110, 168, 224)", // step chip · "Server" · setup + self-hosted tiles
  loginRose: "rgb(212, 138, 140)", // step chip · "Login"
  heroRose: "rgb(201, 120, 128)", // hero tile · sign in / create
  sun: "rgb(245, 196, 66)",
} as const;

export function HeroTile({
  title,
  subtitle,
  Icon,
  tint,
  wrapTitle = false,
}: {
  title: string;
  subtitle?: string;
  Icon: React.ComponentType<{ className?: string; strokeWidth?: number }>;
  tint: string;
  /**
   * Lets a long title wrap onto a second line and the tile grow to hold it, instead of being cut
   * with an ellipsis. For copy that is translated: a title that fits in English does not in French.
   */
  wrapTitle?: boolean;
}) {
  return (
    <div
      className={cn(
        "relative flex items-center overflow-hidden rounded-[26px] px-3.5",
        wrapTitle ? "min-h-[78px] py-3" : "h-[78px]",
      )}
      style={{ backgroundColor: tint, boxShadow: `0 7px 9px ${tint}29` }}
    >
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(210px at 18% 18%, rgba(255,255,255,0.24), rgba(255,255,255,0.08) 38%, transparent 70%)",
        }}
      />
      <Icon
        className="pointer-events-none absolute right-2 top-2.5 h-[82px] w-[82px] text-white/20"
        strokeWidth={1.5}
      />
      <div className="relative flex min-w-0 items-center gap-3">
        <div className="flex h-[42px] w-[42px] shrink-0 items-center justify-center rounded-lg bg-white/[0.18]">
          <Icon className="h-[23px] w-[23px] text-white" strokeWidth={2.25} />
        </div>
        <div className="min-w-0">
          <p
            className={cn(
              "text-[21px] font-bold leading-tight text-white",
              !wrapTitle && "truncate",
            )}
          >
            {title}
          </p>
          {subtitle ? (
            <p className="mt-0.5 truncate text-[13px] font-bold text-white/85">
              {subtitle}
            </p>
          ) : null}
        </div>
      </div>
    </div>
  );
}

export function WizardPrimaryButton({
  label,
  enabled = true,
  type = "submit",
  onClick,
}: {
  label: string;
  enabled?: boolean;
  /** `submit` for the wizard's forms; `button` for an action that is not one. */
  type?: "submit" | "button";
  onClick?: () => void;
}) {
  return (
    <button
      type={type}
      disabled={!enabled}
      onClick={onClick}
      className={cn(
        "relative h-12 w-full overflow-hidden rounded-full text-[15px] font-bold transition active:scale-[0.985]",
        enabled
          ? "bg-primary text-primary-foreground shadow-lg shadow-primary/20"
          : "cursor-not-allowed bg-muted text-muted-foreground/60 opacity-70",
      )}
    >
      <span className="pointer-events-none absolute inset-0 bg-gradient-to-br from-white/15 to-transparent" />
      <span className="relative">{label}</span>
    </button>
  );
}

export function WizardTextButton({
  children,
  onClick,
}: {
  children: React.ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="text-[15px] font-bold text-primary transition active:scale-[0.985] active:opacity-60"
    >
      {children}
    </button>
  );
}
