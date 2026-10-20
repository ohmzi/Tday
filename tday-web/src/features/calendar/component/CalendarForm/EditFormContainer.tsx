import React from "react";
import { TodoItemType } from "@/types";
import EditDrawer from "./Form/DrawerForm/EditDrawer";
import EditModal from "./Form/ModalForm/EditModal";
import useWindowSize from "@/hooks/useWindowSize";
import { useCalendarTaskFormState } from "@/features/calendar/hooks/useCalendarTaskFormState";

type EditCalendarFormContainerProps = {
  todo: TodoItemType;
  displayForm: boolean;
  setDisplayForm: React.Dispatch<React.SetStateAction<boolean>>;
};

// Both shells are imported outright. They used to be `lazy()` behind a Suspense
// boundary here, which put the code split around the surface and left the
// fallback with an impossible job: be a drawer and a modal at once, and then
// get out of the way of a sheet that animates itself in. It could do neither.
// The split now sits inside both shells, around the body that actually carries
// the weight — see `Form/LazyCalendarTaskFormBody`.
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

  return isDesktop ? (
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
  );
};

export default EditCalendarFormContainer;
