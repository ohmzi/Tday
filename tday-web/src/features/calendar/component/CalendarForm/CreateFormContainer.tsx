import React, { lazy, Suspense } from 'react'
import useWindowSize from '@/hooks/useWindowSize';
import ModalPlaceholder from '../LoadingPlaceholders/ModalPlaceholder';
import DrawerPlaceholder from '../LoadingPlaceholders/DrawerPlaceholder';
const MobileDrawer = lazy(() => import("./Form/DrawerForm/CreateDrawer"));
const DesktopModal = lazy(() => import("./Form/ModalForm/CreateModal"));

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
    if (width > 1300) return (
        <Suspense fallback={<ModalPlaceholder />}>
            <DesktopModal displayForm={displayForm} setDisplayForm={setDisplayForm} start={start} end={end} />
        </Suspense>
    );
    return (
        <Suspense fallback={<DrawerPlaceholder />}>
            <MobileDrawer displayForm={displayForm} setDisplayForm={setDisplayForm} start={start} end={end} />
        </Suspense>
    );
};

export default CreateCalendarFormContainer;







