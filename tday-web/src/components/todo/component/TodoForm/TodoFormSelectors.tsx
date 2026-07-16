import type { Dispatch, SetStateAction } from "react";
import { set as setDateParts, parse, isValid, format } from "date-fns";
import { Options, RRule } from "rrule";
import { useTranslation } from "react-i18next";
import { Calendar } from "@/components/ui/calendar";
import ListDot from "@/components/ListDot";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import {
  CenteredSelectorOverlay,
  SelectorDivider,
  SelectorDoneButton,
  SelectorRow,
} from "@/components/ui/sheet-chrome/CenteredSelectorOverlay";
import {
  prioritySwatchClass,
  repeatSwatchClass,
} from "@/components/ui/sheet-chrome/swatches";
import { priorityLabelKey, type DerivedRepeatType, type Priority } from "./labels";

export type TaskSelector =
  | "date"
  | "time"
  | "list"
  | "priority"
  | "repeat"
  | null;

type DateRange = { from: Date; to: Date };

type TaskSelectorOverlaysProps = {
  active: TaskSelector;
  setActive: (value: TaskSelector) => void;
  dateRange: DateRange;
  setDateRange: Dispatch<SetStateAction<DateRange>>;
  priority: Priority;
  setPriority: Dispatch<SetStateAction<Priority>>;
  listID: string | null;
  setListID: Dispatch<SetStateAction<string | null>>;
  setRruleOptions: Dispatch<SetStateAction<Partial<Options> | null>>;
  derivedRepeatType: DerivedRepeatType;
};

const PRIORITIES: Priority[] = ["Low", "Medium", "High"];

type RepeatOption = {
  key: string;
  match: DerivedRepeatType;
  labelKey: string | null; // null → "No repeat"
  build: () => Partial<Options> | null;
};

const REPEAT_OPTIONS: RepeatOption[] = [
  { key: "none", match: null, labelKey: null, build: () => null },
  { key: "daily", match: "Daily", labelKey: "everyDay", build: () => ({ freq: RRule.DAILY }) },
  { key: "weekly", match: "Weekly", labelKey: "everyWeek", build: () => ({ freq: RRule.WEEKLY }) },
  {
    key: "weekday",
    match: "Weekday",
    labelKey: "weekdaysOnly",
    build: () => ({
      freq: RRule.WEEKLY,
      byweekday: [RRule.MO, RRule.TU, RRule.WE, RRule.TH, RRule.FR],
    }),
  },
  { key: "monthly", match: "Monthly", labelKey: "everyMonth", build: () => ({ freq: RRule.MONTHLY }) },
  { key: "yearly", match: "Yearly", labelKey: "everyYear", build: () => ({ freq: RRule.YEARLY }) },
];

export default function TaskSelectorOverlays({
  active,
  setActive,
  dateRange,
  setDateRange,
  priority,
  setPriority,
  listID,
  setListID,
  setRruleOptions,
  derivedRepeatType,
}: TaskSelectorOverlaysProps) {
  const { t: appDict } = useTranslation("app");
  const { listMetaData } = useListMetaData();

  const close = () => setActive(null);

  const onPickDay = (day: Date | undefined) => {
    if (!day) return;
    const combined = setDateParts(day, {
      hours: dateRange.to.getHours(),
      minutes: dateRange.to.getMinutes(),
      seconds: 0,
      milliseconds: 0,
    });
    setDateRange({ from: combined, to: combined });
  };

  const onPickTime = (value: string) => {
    const parsed = parse(value, "HH:mm", dateRange.to);
    if (!isValid(parsed)) return;
    const combined = setDateParts(parsed, { seconds: 0, milliseconds: 0 });
    setDateRange({ from: combined, to: combined });
  };

  return (
    <>
      <CenteredSelectorOverlay
        open={active === "date"}
        onOpenChange={(open) => !open && close()}
        title={appDict("date")}
      >
        <div className="px-2">
          <Calendar
            mode="single"
            defaultMonth={dateRange.to}
            selected={dateRange.to}
            onSelect={onPickDay}
          />
        </div>
        <SelectorDoneButton onClick={close} label={appDict("save")} />
      </CenteredSelectorOverlay>

      <CenteredSelectorOverlay
        open={active === "time"}
        onOpenChange={(open) => !open && close()}
        title={appDict("due")}
      >
        <div className="px-5 pb-2 pt-1">
          <input
            type="time"
            value={format(dateRange.to, "HH:mm")}
            onChange={(e) => onPickTime(e.target.value)}
            className="w-full rounded-2xl border border-muted-foreground/25 bg-card/40 px-4 py-3 text-2xl font-black tracking-tight text-foreground focus:outline-hidden"
          />
        </div>
        <SelectorDoneButton onClick={close} label={appDict("save")} />
      </CenteredSelectorOverlay>

      <CenteredSelectorOverlay
        open={active === "list"}
        onOpenChange={(open) => !open && close()}
        title={appDict("list")}
      >
        <SelectorRow
          label={appDict("noList")}
          selected={!listID}
          onClick={() => {
            setListID(null);
            close();
          }}
        />
        {Object.entries(listMetaData).map(([id, value]) => (
          <div key={id}>
            <SelectorDivider />
            <SelectorRow
              label={value.name.trim()}
              swatchNode={<ListDot id={id} className="h-2.5 w-2.5" />}
              selected={listID === id}
              onClick={() => {
                setListID(id);
                close();
              }}
            />
          </div>
        ))}
      </CenteredSelectorOverlay>

      <CenteredSelectorOverlay
        open={active === "priority"}
        onOpenChange={(open) => !open && close()}
        title={appDict("priority")}
      >
        {PRIORITIES.map((value, index) => (
          <div key={value}>
            {index > 0 ? <SelectorDivider /> : null}
            <SelectorRow
              label={appDict(priorityLabelKey[value])}
              swatchClass={prioritySwatchClass(value)}
              selected={priority === value}
              onClick={() => {
                setPriority(value);
                close();
              }}
            />
          </div>
        ))}
      </CenteredSelectorOverlay>

      <CenteredSelectorOverlay
        open={active === "repeat"}
        onOpenChange={(open) => !open && close()}
        title={appDict("repeat")}
      >
        {REPEAT_OPTIONS.map((option, index) => (
          <div key={option.key}>
            {index > 0 ? <SelectorDivider /> : null}
            <SelectorRow
              label={option.labelKey ? appDict(option.labelKey) : appDict("noRepeat")}
              swatchClass={repeatSwatchClass(option.match)}
              selected={derivedRepeatType === option.match}
              onClick={() => {
                setRruleOptions(option.build());
                close();
              }}
            />
          </div>
        ))}
        {derivedRepeatType === "Custom" ? (
          <div>
            <SelectorDivider />
            <SelectorRow
              label={appDict("custom")}
              swatchClass={repeatSwatchClass("Custom")}
              selected
              onClick={close}
            />
          </div>
        ) : null}
      </CenteredSelectorOverlay>
    </>
  );
}
