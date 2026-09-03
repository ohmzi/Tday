import { isToday, isYesterday, isTomorrow, isThisWeek } from "date-fns";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useLocale } from "@/lib/navigation";

/**
 * Buckets any completed-item shape (todo or floater — anything with a
 * `completedAt`) into "Today" / "Yesterday" / weekday / date sections, newest
 * first within the source order. Generic over the item type so both
 * completion histories share one grouping implementation.
 */
export const useGroupedHistory = <T extends { completedAt: Date }>(
  completedItems: T[],
) => {
  const locale = useLocale();
  const { t: appDict } = useTranslation("app");

  const humanizeDate = useMemo(() => {
    // Cache formatters for performance
    const weekdayFormatter = new Intl.DateTimeFormat(locale, {
      weekday: "long",
    });
    const dateFormatter = new Intl.DateTimeFormat(locale, {
      month: "short",
      day: "2-digit",
      year: "numeric",
    });

    return (date: Date) => {
      if (isToday(date)) return appDict("today");
      if (isYesterday(date)) return appDict("yesterday");
      if (isTomorrow(date)) return appDict("tomorrow");
      if (isThisWeek(date)) return weekdayFormatter.format(date);
      return dateFormatter.format(date);
    };
  }, [locale, appDict]);

  return useMemo(
    () =>
      completedItems.reduce<Map<string, T[]>>((acc, curr) => {
        const label = humanizeDate(new Date(curr.completedAt));
        const relatedGroupArray = acc.get(label);
        if (relatedGroupArray) {
          acc.set(label, [...relatedGroupArray, curr]);
        } else {
          acc.set(label, [curr]);
        }
        return acc;
      }, new Map()),
    [completedItems, humanizeDate],
  );
};
