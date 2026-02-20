import { endOfDay, startOfDay } from "date-fns";
import { fromZonedTime, toZonedTime } from "date-fns-tz";

type TodayBoundaries = {
  dateRangeStart: Date;
  dateRangeEnd: Date;
  todayStartUTC: Date;
  todayEndUTC: Date;
};

export default function getTodayBoundaries(
  timeZone: string,
  now: Date = new Date(),
): TodayBoundaries {
  const zonedNow = toZonedTime(now, timeZone);
  let dateRangeStart = fromZonedTime(startOfDay(zonedNow), timeZone);
  let dateRangeEnd = fromZonedTime(endOfDay(zonedNow), timeZone);

  return {
    get dateRangeStart() {
      return dateRangeStart;
    },
    set dateRangeStart(value: Date) {
      dateRangeStart = value;
    },
    get dateRangeEnd() {
      return dateRangeEnd;
    },
    set dateRangeEnd(value: Date) {
      dateRangeEnd = value;
    },
    get todayStartUTC() {
      return dateRangeStart;
    },
    set todayStartUTC(value: Date) {
      dateRangeStart = value;
    },
    get todayEndUTC() {
      return dateRangeEnd;
    },
    set todayEndUTC(value: Date) {
      dateRangeEnd = value;
    },
  };
}
