import { Check, Server, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { GuideHelpLink } from "@/features/guide/GuideHelpLink";
import type { InstanceTelemetryResponse } from "@/features/serverTelemetry/query/get-instance-telemetry";
import type { ServerTelemetryResponse } from "@/features/serverTelemetry/query/get-server-telemetry";
import { useUpdateServerTelemetry } from "@/features/serverTelemetry/query/update-server-telemetry";
import { useToast } from "@/hooks/use-toast";
import { RowIcon, SettingsPill, SettingsSwitch } from "./SettingsControls";

/**
 * The instance-wide error-report answer, as a control for the one person who may change it.
 *
 * It covers the whole server: this website for everyone using it, and the server's own error
 * reports. Turning it off stops this browser's SDK on the spot, with no reload (see
 * `sentryInit.ts`), because the browser reads the same answer.
 *
 * The "?" sits on the row, not in a card heading as the other help links do: only this row has a
 * guide topic.
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
          <div className="flex min-w-0 items-center gap-1.5">
            <p className="text-[1.05rem] font-black text-foreground">
              {t("serverTelemetry.title")}
            </p>
            <GuideHelpLink topic="crash-reports" label={t("serverTelemetry.helpLabel")} />
          </div>
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

/**
 * The same answer for someone who may not change it: the current state as information, never a
 * switch. A control here would be a switch that does nothing — the server refuses anyone but an
 * admin — so the row reports and stops.
 */
export function ServerTelemetryStateRow({ telemetry }: { telemetry: InstanceTelemetryResponse }) {
  const { t } = useTranslation("settings");

  return (
    <div className="flex items-center justify-between gap-4">
      <div className="flex min-w-0 items-center gap-3.5">
        <RowIcon icon={Server} />
        <div className="min-w-0">
          <div className="flex min-w-0 items-center gap-1.5">
            <p className="text-[1.05rem] font-black text-foreground">
              {t("serverTelemetry.title")}
            </p>
            <GuideHelpLink topic="crash-reports" label={t("serverTelemetry.helpLabel")} />
          </div>
          <p className="mt-0.5 text-sm font-extrabold text-muted-foreground">
            {t("serverTelemetry.stateDetail")}
          </p>
        </div>
      </div>
      <SettingsPill
        icon={telemetry.enabled ? Check : X}
        label={t(telemetry.enabled ? "serverTelemetry.stateOn" : "serverTelemetry.stateOff")}
      />
    </div>
  );
}
