import { RRule, RRuleSet } from "rrule";
import { TodoItemType, recurringTodoItemType } from "@/types";
import { toZonedTime } from "date-fns-tz";
type bounds = {
  dateRangeStart: Date;
  dateRangeEnd: Date;
};

/**
 * generates in-memory instances of todo based on the RRule field in todo.
 *
 * @param recurringParents array of todos with the rrule and optional instances field
 * @param timeZone user timeZone in standard IANA format
 * @param bounds time in UTC of user's start and end of day
 * @returns an array of "ghost" todos
 */

export default function generateTodosFromRRule(
  recurringParents: recurringTodoItemType[],
  timeZone: string,
  bounds: bounds,
): TodoItemType[] {
  return recurringParents.flatMap((parent) => {
    try {
      const ruleSet = genRule(
        parent.rrule,
        parent.due,
        timeZone,
        parent.exdates,
      );
      const occurrences = ruleSet.between(
        bounds.dateRangeStart,
        bounds.dateRangeEnd,
        true,
      );

      return occurrences
        .map((occ) => {
          return {
            ...parent,
            due: occ,
            instanceDate: occ,
          };
        })
        .filter((instance) => {
          return (
            instance.due.getTime() > bounds.dateRangeStart.getTime() &&
            instance.due.getTime() <= bounds.dateRangeEnd.getTime()
          );
        });
    } catch (e) {
      console.error(`Error parsing RRULE for Todo ${parent.id}:`, e);
      return [];
    }
  });
}

/**
 * generates a rule object that is timeZone aware
 * @param rrule string with the rrule
 * @param dtStart anchor instant for the recurrence (UTC)
 * @param timeZone user's time zone
 * @returns RRule object
 */

export function genRule(
  rrule: string,
  dtStart: Date,
  timeZone: string,
  exdates?: Date[],
) {
  const options = RRule.parseString(rrule);
  options.dtstart = toZonedTime(dtStart, timeZone);
  options.tzid = timeZone;

  const rule = new RRule(options);
  const set = new RRuleSet();

  set.rrule(rule);

  for (const ex of exdates ?? []) {
    set.exdate(toZonedTime(ex, timeZone));
  }

  return set;
}
