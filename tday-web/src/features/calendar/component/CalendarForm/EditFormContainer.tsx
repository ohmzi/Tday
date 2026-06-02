import React, { lazy, Suspense } from 'react'
import { TodoItemType } from '@/types';
import DrawerPlaceholder from '../LoadingPlaceholders/DrawerPlaceholder';
const EditDrawer = lazy(() => import("./Form/DrawerForm/EditDrawer"));

type EditCalendarFormContainerProps = {
    todo: TodoItemType
    displayForm: boolean;
    setDisplayForm: React.Dispatch<React.SetStateAction<boolean>>;
};

const EditCalendarFormContainer = ({
    todo,
    displayForm,
    setDisplayForm,
}: EditCalendarFormContainerProps) => {
    return (
        <Suspense fallback={<DrawerPlaceholder />}>
            <EditDrawer displayForm={displayForm} setDisplayForm={setDisplayForm} todo={todo} />
        </Suspense>
    );
};

export default EditCalendarFormContainer;







