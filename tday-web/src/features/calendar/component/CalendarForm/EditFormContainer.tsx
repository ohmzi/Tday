import React, { lazy, Suspense } from 'react'
import useWindowSize from '@/hooks/useWindowSize';
import { TodoItemType } from '@/types';
import ModalPlaceholder from '../LoadingPlaceholders/ModalPlaceholder';
import DrawerPlaceholder from '../LoadingPlaceholders/DrawerPlaceholder';
const MobileDrawer = lazy(() => import("./Form/DrawerForm/EditDrawer"));
const DesktopModal = lazy(() => import("./Form/ModalForm/EditModal"));

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
    const { width } = useWindowSize();
    if (width > 1300) return (
        <Suspense fallback={<ModalPlaceholder />}>
            <DesktopModal displayForm={displayForm} setDisplayForm={setDisplayForm} todo={todo} />
        </Suspense>
    );
    return (
        <Suspense fallback={<DrawerPlaceholder />}>
            <MobileDrawer displayForm={displayForm} setDisplayForm={setDisplayForm} todo={todo} />
        </Suspense>
    );
};

export default EditCalendarFormContainer;







