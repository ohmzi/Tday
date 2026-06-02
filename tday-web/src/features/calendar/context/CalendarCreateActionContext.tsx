import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

type CalendarCreateHandler = (() => void) | null;

const CalendarCreateActionContext = createContext<{
  handler: CalendarCreateHandler;
  setHandler: (handler: CalendarCreateHandler) => void;
} | null>(null);

export function CalendarCreateActionProvider({ children }: { children: ReactNode }) {
  const [handler, setHandler] = useState<CalendarCreateHandler>(null);
  const value = useMemo(() => ({ handler, setHandler }), [handler]);

  return (
    <CalendarCreateActionContext.Provider value={value}>
      {children}
    </CalendarCreateActionContext.Provider>
  );
}

export function useCalendarCreateAction() {
  return useContext(CalendarCreateActionContext)?.handler ?? null;
}

export function useRegisterCalendarCreateAction(open: () => void) {
  const context = useContext(CalendarCreateActionContext);

  useEffect(() => {
    if (!context) return;
    context.setHandler(() => open);
    return () => context.setHandler(null);
  }, [context, open]);
}
