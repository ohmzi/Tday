import { useState } from "react";
import { Activity } from "lucide-react";
import { useTranslation } from "react-i18next";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  HeroTile,
  TINT,
  WizardPrimaryButton,
  WizardTextButton,
} from "@/components/onboarding/OnboardingPrimitives";
import { useTelemetryConsent } from "@/hooks/useTelemetryConsent";
import { useRouter } from "@/lib/navigation";
import {
  isCrashReportingConfigured,
  setTelemetryConsent,
} from "@/lib/privacy/telemetryConsent";
import { useAuth } from "@/providers/AuthProvider";

// Set when the card is dismissed without an answer. In sessionStorage on purpose: "not now" and
// Escape are not a no, so the question comes back, but not on every route change of this visit.
const DEFERRED_STORAGE_KEY = "tday.telemetry.consentDeferred";

function readDeferred(): boolean {
  try {
    return window.sessionStorage.getItem(DEFERRED_STORAGE_KEY) === "1";
  } catch {
    return false;
  }
}

function writeDeferred(): void {
  try {
    window.sessionStorage.setItem(DEFERRED_STORAGE_KEY, "1");
  } catch {
    // Ignore storage write failures; the card is still dismissed for this mount.
  }
}

/**
 * The one-time question that decides whether this browser may send crash and problem reports.
 *
 * Mounted inside the signed-in shell, so it covers both workspaces — Server Mode after login and
 * Local Mode after the passphrase — and the guide it links to is reachable. It dresses like the
 * onboarding wizard because it is the last step of the same first run, for people arriving now and
 * for installs that predate it.
 *
 * It shows only while every one of these holds: the build carries a DSN (otherwise there is
 * nowhere to send and nothing to ask), the question is unanswered, it was not set aside earlier in
 * this session, and no blocking prompt (a forced password change, security questions) is up.
 *
 * Share and Not now are the same button, deliberately: a consent control that nudges is not one.
 * Escape, the backdrop and "Read the full FAQ" set the question aside without answering it.
 */
export default function CrashReportsConsentGate() {
  const { t } = useTranslation("crashReports");
  const router = useRouter();
  const { user } = useAuth();
  const consent = useTelemetryConsent();
  const [deferred, setDeferred] = useState(readDeferred);

  if (!isCrashReportingConfigured()) return null;

  const blockedByAnotherPrompt = Boolean(
    user?.requirePasswordChange || user?.requireSecurityQuestions,
  );
  const open = consent === "unanswered" && !deferred && !blockedByAnotherPrompt;

  const defer = () => {
    writeDeferred();
    setDeferred(true);
  };

  const readFaq = () => {
    defer();
    router.push("/app/guide/crash-reports");
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) defer();
      }}
    >
      <DialogContent
        className="max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-[440px] gap-3.5 overflow-y-auto rounded-[34px] p-[18px] shadow-2xl sm:rounded-[34px]"
        // Focus the card rather than its first button: this appears on its own, and Enter or a
        // space bar pressed at that moment must not be able to answer for the person.
        onOpenAutoFocus={(event) => {
          event.preventDefault();
          event.currentTarget instanceof HTMLElement && event.currentTarget.focus();
        }}
      >
        <DialogTitle className="sr-only">{t("card.title")}</DialogTitle>
        <div aria-hidden>
          <HeroTile title={t("card.title")} Icon={Activity} tint={TINT.serverBlue} wrapTitle />
        </div>

        <DialogDescription className="text-[14px] font-bold leading-snug text-foreground/60">
          {t("card.intro")}
        </DialogDescription>

        <dl className="space-y-2.5 rounded-[22px] border border-border bg-muted/50 px-4 py-3">
          <div>
            <dt className="text-[13px] font-extrabold text-foreground">{t("card.sentLabel")}</dt>
            <dd className="text-[14px] font-bold leading-snug text-foreground/60">{t("card.sent")}</dd>
          </div>
          <div>
            <dt className="text-[13px] font-extrabold text-foreground">{t("card.neverSentLabel")}</dt>
            <dd className="text-[14px] font-bold leading-snug text-foreground/60">
              {t("card.neverSent")}
            </dd>
          </div>
        </dl>

        <p className="text-center text-[13px] font-bold leading-snug text-foreground/50">
          {t("card.footnote")}
        </p>

        <div className="flex flex-col gap-2.5">
          <WizardPrimaryButton
            type="button"
            label={t("card.share")}
            onClick={() => setTelemetryConsent(true)}
          />
          <WizardPrimaryButton
            type="button"
            label={t("card.notNow")}
            onClick={() => setTelemetryConsent(false)}
          />
        </div>

        <div className="flex justify-center">
          <WizardTextButton onClick={readFaq}>{t("card.readFaq")}</WizardTextButton>
        </div>
      </DialogContent>
    </Dialog>
  );
}
