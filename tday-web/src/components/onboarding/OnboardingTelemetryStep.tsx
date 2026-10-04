import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Loader2, ShieldCheck } from "lucide-react";
import { useTranslation } from "react-i18next";
import {
  HeroTile,
  TINT,
  WizardPrimaryButton,
  WizardTextButton,
} from "@/components/onboarding/OnboardingPrimitives";
import {
  SERVER_TELEMETRY_QUERY_KEY,
  type ServerTelemetryResponse,
} from "@/features/serverTelemetry/query/get-server-telemetry";
import { useUpdateServerTelemetry } from "@/features/serverTelemetry/query/update-server-telemetry";
import { useAuth } from "@/providers/AuthProvider";
import { api } from "@/lib/api-client";

/** Where the step sends the admin once it is done, or once there is nothing to ask. */
const APP_HOME = "/app";
const CRASH_REPORTS_GUIDE = "/app/guide/crash-reports";

/**
 * The last onboarding step, for an admin whose server has never answered the error-report question.
 *
 * It is the instance-wide answer itself: the same setting Settings → Privacy shows and the same one
 * the browser's SDK gate reads, which is why it can only be asked after the admin's account exists.
 * "Send reports" and "Don't send" are the two ways off this step; both write through
 * `PATCH /api/admin/telemetry`.
 *
 * It never blocks anyone else. A viewer who is not an admin gets a 403 from the admin endpoint, a
 * server with no DSN of its own has nothing to report, a server that already has an answer says so
 * in `updatedAt`, and a request that fails leaves the answer at its off default — all of them leave
 * the wizard for the app without a question. `onFinish` is the wizard's own way out: it lowers the
 * hold and navigates, whether the person answered or the step decided there was nothing to ask.
 */

export default function OnboardingTelemetryStep({
  onFinish,
}: {
  onFinish: (destination: string) => void;
}) {
  const { t } = useTranslation("crashReports");
  const { authState } = useAuth();
  const [errorMessage, setErrorMessage] = useState("");
  const update = useUpdateServerTelemetry();
  // Asked once, on mount, and never retried: a 403 here is the ordinary answer for everyone who is
  // not an admin, and making them wait for a second round trip to learn that is pointless.
  const { data, isError } = useQuery<ServerTelemetryResponse>({
    queryKey: SERVER_TELEMETRY_QUERY_KEY,
    enabled: authState === "authenticated",
    retry: false,
    staleTime: 0,
    queryFn: () => api.GET({ url: "/api/admin/telemetry" }),
  });

  // There is a question only for an admin whose server has a reporting endpoint and has never
  // answered for it. Everything else — a viewer the endpoint refuses, a server with no DSN, an
  // already-answered instance — is "nothing to ask", and the wizard opens the app.
  const needsAnswer = data !== undefined && data.dsnConfigured && data.updatedAt === null;
  const nothingToAsk = authState === "authenticated" && (isError || (data !== undefined && !needsAnswer));

  useEffect(() => {
    if (nothingToAsk) onFinish(APP_HOME);
  }, [nothingToAsk, onFinish]);

  const answer = (enabled: boolean) => {
    setErrorMessage("");
    update.mutate(enabled, {
      onSuccess: () => onFinish(APP_HOME),
      onError: () => setErrorMessage(t("card.saveFailed")),
    });
  };

  const showQuestion = authState === "authenticated" && needsAnswer;

  if (!showQuestion) {
    return (
      <div className="flex flex-col gap-[11px]">
        <HeroTile
          title={t("card.title")}
          Icon={ShieldCheck}
          tint={TINT.consentBlue}
          wrapTitle
        />
        <div className="flex justify-center py-6">
          <Loader2 className="h-5 w-5 animate-spin text-primary" />
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-[11px]">
      <HeroTile title={t("card.title")} Icon={ShieldCheck} tint={TINT.consentBlue} wrapTitle />
      <p className="px-1 text-[14px] font-bold leading-snug text-foreground/60">
        {t("card.intro")}
      </p>
      <p className="rounded-[18px] border border-border bg-muted/50 px-3.5 py-2.5 text-[13px] font-bold leading-snug text-foreground/70">
        {t("card.neverSent")}
      </p>
      {errorMessage && <p className="text-[14px] font-bold text-destructive">{errorMessage}</p>}
      <div className="flex flex-col gap-2.5">
        <WizardPrimaryButton
          type="button"
          label={t("card.share")}
          enabled={!update.isPending}
          onClick={() => answer(true)}
        />
        <WizardPrimaryButton
          type="button"
          label={t("card.notNow")}
          enabled={!update.isPending}
          onClick={() => answer(false)}
        />
      </div>
      <div className="flex justify-center">
        <WizardTextButton onClick={() => onFinish(CRASH_REPORTS_GUIDE)}>
          {t("card.readFaq")}
        </WizardTextButton>
      </div>
    </div>
  );
}
