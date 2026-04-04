import { useTodoForm } from "@/providers/TodoFormProvider";
import { RRule } from "rrule";
import { masqueradeAsUTC } from "../lib/masqueradeAsUTC";
import { useMemo } from "react";
/*
 * Masquerade local time as UTC for RRule, then interpret results in UTC.
 */
export const useNextCalculatedRepeatDate = function () {
  const { rruleOptions, dateRange } = useTodoForm();
  const locallyInferredRruleObject = useMemo(() => {
    if (!rruleOptions) return null;
    return new RRule({
      ...rruleOptions,
      dtstart: masqueradeAsUTC(dateRange.to),
    });
  }, [rruleOptions, dateRange]);
  const nextCalculatedRepeatDate = useMemo(
    () => locallyInferredRruleObject?.after(masqueradeAsUTC(dateRange.to)),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [rruleOptions, dateRange],
  );
  return { nextCalculatedRepeatDate, locallyInferredRruleObject };
};
