import React, { lazy, Suspense } from "react";
import DrawerPlaceholder from "../LoadingPlaceholders/DrawerPlaceholder";
import useWindowSize from "@/hooks/useWindowSize";

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

  return (
    <Suspense fallback={<DrawerPlaceholder />}>
      {isDesktop ? (
        <CreateModal
          start={start}
          end={end}
          displayForm={displayForm}
          setDisplayForm={setDisplayForm}
        />
      ) : (
        <CreateDrawer
          start={start}
          end={end}
          displayForm={displayForm}
          setDisplayForm={setDisplayForm}
        />
      )}
    </Suspense>
  );
};

export default CreateCalendarFormContainer;
