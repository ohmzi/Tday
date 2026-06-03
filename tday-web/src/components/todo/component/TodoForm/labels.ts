// Central label-key maps shared by the native task selectors.
// Values resolve against the "app" i18n namespace (e.g. app.normal = "Normal").

export type Priority = "Low" | "Medium" | "High";

export const priorityLabelKey: Record<Priority, string> = {
  Low: "normal",
  Medium: "important",
  High: "urgent",
};

export type DerivedRepeatType =
  | "Daily"
  | "Weekly"
  | "Monthly"
  | "Yearly"
  | "Weekday"
  | "Custom"
  | null;

export const repeatLabelKey: Record<
  Exclude<DerivedRepeatType, null>,
  string
> = {
  Daily: "everyDay",
  Weekly: "everyWeek",
  Monthly: "everyMonth",
  Yearly: "everyYear",
  Weekday: "weekdaysOnly",
  Custom: "custom",
};
