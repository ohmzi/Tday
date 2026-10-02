import { Activity, Server } from "lucide-react";
import { useTranslation } from "react-i18next";
import { GuideHelpLink } from "@/features/guide/GuideHelpLink";
import type { ServerTelemetryResponse } from "@/features/serverTelemetry/query/get-server-telemetry";
import { useUpdateServerTelemetry } from "@/features/serverTelemetry/query/update-server-telemetry";
import { useToast } from "@/hooks/use-toast";
import { useTelemetryConsent } from "@/hooks/useTelemetryConsent";
import { setTelemetryConsent } from "@/lib/privacy/telemetryConsent";
import { RowIcon, SettingsSwitch } from "./SettingsControls";

/**
 * This browser's crash and problem reports. Flipping it is the same act as answering the consent
 * card, so answering here first means the card never asks. Turning it off stops the SDK on the
 * spot, with no reload (see `sentryInit.ts`).
 *
 * The "?" sits on the row, not in a card heading as the other help links do: this card holds two
 * unrelated switches, and only this one has a guide topic.
 */
export function CrashReportsRow() {
  const { t } = useTranslation("settings");
  const consent = useTelemetryConsent();
  const granted = consent === "granted";

  return (
    <div className="flex items-center justify-between gap-4">
      <div className="flex min-w-0 items-center gap-3.5">
        <RowIcon icon={Activity} />
        <p className="min-w-0 text-[1.05rem] font-black text-foreground">{t("crashReports.title")}</p>
        <GuideHelpLink topic="crash-reports" label={t("crashReports.helpLabel")} />
      </div>
      <SettingsSwitch
        checked={granted}
        ariaLabel={t("crashReports.toggle")}
        onClick={() => setTelemetryConsent(!granted)}
      />
    </div>
  );
}

/**
 * The server's own error reports, for the admin of an instance that has a `SENTRY_DSN`. Web-only on
 * purpose: it configures the backend, which is not something a phone does. It is a different
 * switch from the one above — that one is about what this browser sends; this one is about what
 * the server sends — and its copy says so.
 */
export function ServerTelemetryRow({ telemetry }: { telemetry: ServerTelemetryResponse }) {
  const { t } = useTranslation("settings");
  const { toast } = useToast();
  const update = useUpdateServerTelemetry();
  // While a change is on its way the switch shows where it is going, so it answers the tap at
  // once; if the server refuses, the query's own value is what it falls back to.
  const enabled = update.isPending ? update.variables : telemetry.enabled;

  return (
    <div className="flex items-center justify-between gap-4">
      <div className="flex min-w-0 items-center gap-3.5">
        <RowIcon icon={Server} />
        <div className="min-w-0">
          <p className="text-[1.05rem] font-black text-foreground">{t("serverTelemetry.title")}</p>
          <p className="mt-0.5 text-sm font-extrabold text-muted-foreground">
            {t("serverTelemetry.description")}
          </p>
        </div>
      </div>
      <SettingsSwitch
        checked={enabled}
        disabled={update.isPending}
        ariaLabel={t("serverTelemetry.toggle")}
        onClick={() =>
          update.mutate(!telemetry.enabled, {
            onError: () =>
              toast({ description: t("serverTelemetry.updateFailed"), variant: "destructive" }),
          })
        }
      />
    </div>
  );
}
