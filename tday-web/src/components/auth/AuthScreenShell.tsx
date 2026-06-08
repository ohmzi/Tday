import type { ReactNode } from "react";
import MockHomeBackdrop from "@/components/auth/MockHomeBackdrop";

/**
 * Full-screen auth shell that mirrors the login screen: a blurred, dimmed mock home
 * dashboard behind a centered card. Used for the reset-password wizard so it matches
 * the rest of the app instead of sitting on a flat black page.
 */
export default function AuthScreenShell({ children }: { children: ReactNode }) {
  return (
    <main className="relative min-h-screen overflow-hidden bg-background text-foreground">
      <div className="absolute inset-0 overflow-hidden" aria-hidden>
        <div className="h-full w-full scale-[1.04] blur-xl">
          <MockHomeBackdrop />
        </div>
        <div className="absolute inset-0 bg-background/60" />
      </div>
      <div className="relative z-10 flex min-h-screen items-center justify-center px-4 py-10">
        {children}
      </div>
    </main>
  );
}
