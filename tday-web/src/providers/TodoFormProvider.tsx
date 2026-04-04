import { TodoItemType } from "@/types";
import React, {
  createContext,
  SetStateAction,
  useContext,
  useState,
} from "react";
import { Options, RRule } from "rrule";
import deriveRepeatType from "@/lib/deriveRepeatType";

type FormDateRange = { from: Date; to: Date };

// Types for context values
interface TodoFormContextType {
  todoItem?: TodoItemType;
  title: string;
  setTitle: React.Dispatch<SetStateAction<string>>;
  desc: string;
  setDesc: React.Dispatch<SetStateAction<string>>;
  priority: "Low" | "Medium" | "High";
  listID: string | null;
  setListID: React.Dispatch<SetStateAction<string | null>>;
  setPriority: React.Dispatch<SetStateAction<"Low" | "Medium" | "High">>;
  dateRange: FormDateRange;
  setDateRange: React.Dispatch<SetStateAction<FormDateRange>>;
  rruleOptions: Partial<Options> | null;
  setRruleOptions: React.Dispatch<SetStateAction<Partial<Options> | null>>;
  timeZone: string;
  derivedRepeatType:
    | "Daily"
    | "Weekly"
    | "Monthly"
    | "Yearly"
    | "Weekday"
    | "Custom"
    | null;
  instanceDate?: Date;
  dateRangeChecksum: string;
  rruleChecksum: string | null; //send what was changed to the backend, to either delete override instances or overwrite them
}

// Props for the provider
interface TodoFormProviderProps {
  todoItem?: TodoItemType;
  overrideFields?: { listID?: string };
  children: React.ReactNode;
}

const TodoFormContext = createContext<TodoFormContextType | undefined>(
  undefined,
);

const TodoFormProvider = ({ children, todoItem, overrideFields }: TodoFormProviderProps) => {
  const [title, setTitle] = useState<string>(todoItem?.title || "");
  const [desc, setDesc] = useState<string>(todoItem?.description || "");
  const [listID, setListID] = useState<string | null>(
    overrideFields?.listID ??
      todoItem?.listID ??
      null,
  );
  const [priority, setPriority] = useState<"Low" | "Medium" | "High">(
    todoItem?.priority || "Low",
  );
  const now = new Date();
  now.setHours(now.getHours() + 3);

  const due = todoItem?.due ?? now;
  const [dateRange, setDateRange] = useState<FormDateRange>({
    from: due,
    to: due,
  });
  const [rruleOptions, setRruleOptions] = useState(
    todoItem?.rrule ? RRule.parseString(todoItem.rrule) : null,
  );
  const dateRangeChecksum = todoItem
    ? todoItem.due.toISOString()
    : dateRange.to.toISOString();

  const rruleChecksum = todoItem?.rrule || null;

  const timeZone =
    Intl.DateTimeFormat().resolvedOptions().timeZone ||
    todoItem?.timeZone ||
    "UTC";

  const derivedRepeatType = deriveRepeatType({ rruleOptions });

  const contextValue: TodoFormContextType = {
    todoItem,
    title,
    setTitle,
    desc,
    setDesc,
    priority,
    setPriority,
    listID,
    setListID,
    dateRange,
    setDateRange,
    rruleOptions,
    setRruleOptions,
    timeZone,
    derivedRepeatType,
    dateRangeChecksum,
    rruleChecksum,
  };

  return (
    <TodoFormContext.Provider value={contextValue}>
      {children}
    </TodoFormContext.Provider>
  );
};

export default TodoFormProvider;

// Custom hook to use the TodoForm context
export function useTodoForm() {
  const context = useContext(TodoFormContext);
  if (!context) {
    throw new Error("useTodoForm must be used within TodoFormProvider");
  }
  return context;
}
