import React, { lazy, Suspense } from "react";
import DrawerPlaceholder from "../LoadingPlaceholders/DrawerPlaceholder";
import useWindowSize from "@/hooks/useWindowSize";
import { useCalendarTaskFormState } from "@/features/calendar/hooks/useCalendarTaskFormState";

const CreateDrawer = lazy(() => import("./Form/DrawerForm/CreateDrawer"));
const CreateModal = lazy(() => import("./Form/ModalForm/CreateModal"));

type CreateCalendarFormContainerProps = {
  start: Date;
  end: Date;
  displayForm: boolean;
  setDisplayForm: React.Dispatch<React.SetStateAction<boolean>>;
};

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

  return (
    <Suspense fallback={<DrawerPlaceholder />}>
      {isDesktop ? (
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
      )}
    </Suspense>
  );
};

export default CreateCalendarFormContainer;
