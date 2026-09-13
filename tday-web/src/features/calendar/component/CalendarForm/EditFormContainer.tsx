import React, { lazy, Suspense } from "react";
import { TodoItemType } from "@/types";
import DrawerPlaceholder from "../LoadingPlaceholders/DrawerPlaceholder";
import useWindowSize from "@/hooks/useWindowSize";
import { useCalendarTaskFormState } from "@/features/calendar/hooks/useCalendarTaskFormState";

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

  // The edits live here, above the breakpoint switch. Modal and drawer are different
  // component types, so React unmounts one and mounts the other when `isDesktop` flips —
  // anything they owned would be destroyed with them. Held here, only the shell changes.
  const form = useCalendarTaskFormState({
    title: todo.title,
    description: todo.description,
    priority: todo.priority,
    due: todo.due,
    rrule: todo.rrule,
    listID: todo.listID,
  });

  return (
    <Suspense fallback={<DrawerPlaceholder />}>
      {isDesktop ? (
        <EditModal
          todo={todo}
          displayForm={displayForm}
          setDisplayForm={setDisplayForm}
          form={form}
        />
      ) : (
        <EditDrawer
          todo={todo}
          displayForm={displayForm}
          setDisplayForm={setDisplayForm}
          form={form}
        />
      )}
    </Suspense>
  );
};

export default EditCalendarFormContainer;
