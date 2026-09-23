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
  SheetTitleNotesCard,
} from "@/components/ui/sheet-chrome";
import ListDot from "@/components/ListDot";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import NotesField from "@/components/todo/component/NotesField/NotesField";

type DateRange = { from: Date; to: Date };

export type CalendarTaskFormBodyProps = {
  titleRef: RefObject<HTMLDivElement | null>;
  title: string;
  setTitle: Dispatch<SetStateAction<string>>;
  description: string;
  setDescription: Dispatch<SetStateAction<string>>;
  priority: Priority;
  setPriority: Dispatch<SetStateAction<Priority>>;
  priorityTouchedByUser: boolean;
  setPriorityTouchedByUser: Dispatch<SetStateAction<boolean>>;
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
  // Only the setter is called here; the hook itself reads the flag to decide whether
  // to keep re-deriving priority from the list's default.
  setPriorityTouchedByUser,
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

  // Wraps the raw setter so the hook's touch-tracking sees a user's own pick without
  // TodoFormSelectors needing to know anything about it — see TodoForm.tsx.
  const handlePrioritySelect: Dispatch<SetStateAction<Priority>> = (value) => {
    setPriority(value);
    setPriorityTouchedByUser(true);
  };

  const repeatValueLabel = derivedRepeatType
    ? appDict(repeatLabelKey[derivedRepeatType])
    : appDict("noRepeat");
  const selectedListName = listID ? listMetaData[listID]?.name?.trim() : null;

  return (
    <div className="flex flex-col gap-3">
      {/* Title + Notes */}
      <SheetTitleNotesCard
        title={
          <NLPTitleInput
            className="text-lg font-black"
            title={title}
            setTitle={setTitle}
            titleRef={titleRef}
            setDateRange={setDateRange}
            setPriority={setPriority}
            setRruleOptions={setRruleOptions}
            onSubmit={onSubmit}
          />
        }
      >
        <NotesField
          value={description}
          onChange={setDescription}
          placeholder={appDict("notes")}
        />
      </SheetTitleNotesCard>

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
          value={null}
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
        setPriority={handlePrioritySelect}
        listID={listID}
        setListID={setListID}
        setRruleOptions={setRruleOptions}
        derivedRepeatType={derivedRepeatType}
      />
    </div>
  );
}
