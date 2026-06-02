import React, { lazy, Suspense } from 'react'
import DrawerPlaceholder from '../LoadingPlaceholders/DrawerPlaceholder';
const CreateDrawer = lazy(() => import("./Form/DrawerForm/CreateDrawer"));

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
    return (
        <Suspense fallback={<DrawerPlaceholder />}>
            <CreateDrawer displayForm={displayForm} setDisplayForm={setDisplayForm} start={start} end={end} />
        </Suspense>
    );
};

export default CreateCalendarFormContainer;







