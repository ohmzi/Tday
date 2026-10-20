import React from "react";
import CreateDrawer from "./Form/DrawerForm/CreateDrawer";
import CreateModal from "./Form/ModalForm/CreateModal";
import useWindowSize from "@/hooks/useWindowSize";
import { useCalendarTaskFormState } from "@/features/calendar/hooks/useCalendarTaskFormState";

type CreateCalendarFormContainerProps = {
  start: Date;
  end: Date;
  displayForm: boolean;
  setDisplayForm: React.Dispatch<React.SetStateAction<boolean>>;
};

// Imported outright, for the reason `EditFormContainer` sets out: the code
// split belongs inside the sheet, not around it.
const CreateCalendarFormContainer = ({
  start,
  end,
  displayForm,
  setDisplayForm,
}: CreateCalendarFormContainerProps) => {
  const { width } = useWindowSize();
  const isDesktop = width >= 640;

  // The draft lives here, above the breakpoint switch. Modal and drawer are different
  // component types, so React unmounts one and mounts the other when `isDesktop` flips —
  // anything they owned would be destroyed with them. Held here, only the shell changes.
  const form = useCalendarTaskFormState({ due: end });

  return isDesktop ? (
    <CreateModal
      start={start}
      end={end}
      displayForm={displayForm}
      setDisplayForm={setDisplayForm}
      form={form}
    />
  ) : (
    <CreateDrawer
      start={start}
      end={end}
      displayForm={displayForm}
      setDisplayForm={setDisplayForm}
      form={form}
    />
  );
};

export default CreateCalendarFormContainer;
