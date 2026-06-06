import {
  AgendaDateProps,
  AgendaTimeProps,
  EventProps,
} from "react-big-calendar";
import { format } from "date-fns";
import { TodoItemType } from "@/types";
import i18n from "@/i18n";
import { getDateFnsLocale } from "@/lib/date/dateFnsLocale";

// --- Custom agenda date row ---
const CustomAgendaDate = ({ day }: AgendaDateProps) => {
  return <div>{format(day, "EEEE, MMM d", { locale: getDateFnsLocale(i18n.language) })}</div>;
};

// --- Custom agenda time row ---
const CustomAgendaTime = ({ day }: AgendaTimeProps) => {
  return <span>{format(day, "HH:mm")}</span>;
};

// --- Custom agenda event row ---
const CustomAgendaEvent = ({ event }: EventProps<TodoItemType>) => {
  return (
    <div>
      <span>{event.title}</span>
      <span>
        ({format(event.due, "HH:mm")})
      </span>
    </div>
  );
};

// --- Export only the agenda object ---
export const agendaComponents = {
  date: CustomAgendaDate,
  time: CustomAgendaTime,
  event: CustomAgendaEvent,
};
