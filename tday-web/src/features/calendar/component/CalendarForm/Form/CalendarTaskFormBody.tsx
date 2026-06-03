import { useState, type Dispatch, type RefObject, type SetStateAction } from "react";
import { Calendar as CalendarIcon, Flag, List as ListIcon, Repeat } from "lucide-react";
import type { Options } from "rrule";
import { useTranslation } from "react-i18next";
import NLPTitleInput from "@/components/todo/component/TodoForm/NLPTitleInput";
import TaskSelectorOverlays, {
  type TaskSelector,
} from "@/components/todo/component/TodoForm/TodoFormSelectors";
import {
  priorityLabelKey,
  repeatLabelKey,
  type DerivedRepeatType,
  type Priority,
} from "@/components/todo/component/TodoForm/labels";
import {
  DueDateTimeControl,
  SheetCard,
  SheetDivider,
  SheetRow,
  SheetSectionTitle,
  SheetSelectorRow,
} from "@/components/ui/sheet-chrome";
import ListDot from "@/components/ListDot";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";

type DateRange = { from: Date; to: Date };

type CalendarTaskFormBodyProps = {
  titleRef: RefObject<HTMLDivElement | null>;
  title: string;
  setTitle: Dispatch<SetStateAction<string>>;
  description: string;
  setDescription: Dispatch<SetStateAction<string>>;
  priority: Priority;
  setPriority: Dispatch<SetStateAction<Priority>>;
  dateRange: DateRange;
  setDateRange: Dispatch<SetStateAction<DateRange>>;
  listID: string | null;
  setListID: Dispatch<SetStateAction<string | null>>;
  setRruleOptions: Dispatch<SetStateAction<Partial<Options> | null>>;
  derivedRepeatType: DerivedRepeatType;
  onSubmit: () => void;
};

export default function CalendarTaskFormBody({
  titleRef,
  title,
  setTitle,
  description,
  setDescription,
  priority,
  setPriority,
  dateRange,
  setDateRange,
  listID,
  setListID,
  setRruleOptions,
  derivedRepeatType,
  onSubmit,
}: CalendarTaskFormBodyProps) {
  const { t: appDict } = useTranslation("app");
  const { listMetaData } = useListMetaData();
  const [active, setActive] = useState<TaskSelector>(null);

  const repeatValueLabel = derivedRepeatType
    ? appDict(repeatLabelKey[derivedRepeatType])
    : appDict("noRepeat");
  const selectedListName = listID ? listMetaData[listID]?.name?.trim() : null;

  return (
    <div className="flex flex-col gap-3">
      {/* Title + Notes */}
      <SheetCard>
        <div className="px-[18px] pb-2 pt-3">
          <NLPTitleInput
            className="text-lg font-black"
            title={title}
            setTitle={setTitle}
            titleRef={titleRef}
            setDateRange={setDateRange}
            onSubmit={onSubmit}
          />
        </div>
        <SheetDivider />
        <input
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          name="description"
          placeholder={appDict("notes")}
          className="w-full bg-transparent px-[18px] py-3 text-base font-bold text-foreground placeholder:font-bold placeholder:text-muted-foreground/60 focus:outline-hidden"
        />
      </SheetCard>

      {/* Schedule */}
      <SheetSectionTitle>{appDict("schedule")}</SheetSectionTitle>
      <SheetCard>
        <SheetRow icon={<CalendarIcon className="h-5 w-5" />} label={appDict("due")}>
          <DueDateTimeControl
            due={dateRange.to}
            onDateClick={() => setActive("date")}
            onTimeClick={() => setActive("time")}
          />
        </SheetRow>
      </SheetCard>

      {/* Details */}
      <SheetSectionTitle>{appDict("details")}</SheetSectionTitle>
      <SheetCard>
        <SheetSelectorRow
          icon={<ListIcon className="h-5 w-5" />}
          label={appDict("list")}
          ariaLabel={`${appDict("list")}, ${selectedListName ?? appDict("noList")}`}
          value={
            listID && selectedListName ? (
              <>
                <ListDot id={listID} className="h-3.5 w-3.5" />
                <span className="truncate">{selectedListName}</span>
              </>
            ) : (
              appDict("noList")
            )
          }
          onClick={() => setActive("list")}
        />
        <SheetDivider />
        <SheetSelectorRow
          icon={<Flag className="h-5 w-5" />}
          label={appDict("priority")}
          ariaLabel={`${appDict("priority")}, ${appDict(priorityLabelKey[priority])}`}
          value={appDict(priorityLabelKey[priority])}
          onClick={() => setActive("priority")}
        />
        <SheetDivider />
        <SheetSelectorRow
          icon={<Repeat className="h-5 w-5" />}
          label={appDict("repeat")}
          ariaLabel={`${appDict("repeat")}, ${repeatValueLabel}`}
          value={repeatValueLabel}
          onClick={() => setActive("repeat")}
        />
      </SheetCard>

      <TaskSelectorOverlays
        active={active}
        setActive={setActive}
        dateRange={dateRange}
        setDateRange={setDateRange}
        priority={priority}
        setPriority={setPriority}
        listID={listID}
        setListID={setListID}
        setRruleOptions={setRruleOptions}
        derivedRepeatType={derivedRepeatType}
      />
    </div>
  );
}
