import React, { lazy, Suspense } from "react";
import { TodoItemType } from "@/types";
import DrawerPlaceholder from "../LoadingPlaceholders/DrawerPlaceholder";
import useWindowSize from "@/hooks/useWindowSize";

const EditDrawer = lazy(() => import("./Form/DrawerForm/EditDrawer"));
const EditModal = lazy(() => import("./Form/ModalForm/EditModal"));

type EditCalendarFormContainerProps = {
  todo: TodoItemType;
  displayForm: boolean;
  setDisplayForm: React.Dispatch<React.SetStateAction<boolean>>;
};

const EditCalendarFormContainer = ({
  todo,
  displayForm,
  setDisplayForm,
}: EditCalendarFormContainerProps) => {
  const { width } = useWindowSize();
  const isDesktop = width >= 640;

  return (
    <Suspense fallback={<DrawerPlaceholder />}>
      {isDesktop ? (
        <EditModal
          todo={todo}
          displayForm={displayForm}
          setDisplayForm={setDisplayForm}
        />
      ) : (
        <EditDrawer
          todo={todo}
          displayForm={displayForm}
          setDisplayForm={setDisplayForm}
        />
      )}
    </Suspense>
  );
};

export default EditCalendarFormContainer;
