
import useWindowSize from '@/hooks/useWindowSize';
import React, { lazy, Suspense } from 'react'
import { Skeleton } from '@/components/ui/skeleton';
import { useTodoForm } from '@/providers/TodoFormProvider';
const MobileDrawer = lazy(() => import("./repeatDrawerMenu/CustomRepeatDrawer"));
const DesktopModal = lazy(() => import("./repeatModalMenu/CutomRepeatModalMenu"));

export default function CustomRepeatMenuContainer() {
    const { rruleOptions, setRruleOptions, derivedRepeatType } = useTodoForm();
    const { width } = useWindowSize();
    if (width > 1300) return (
        <Suspense fallback={<Skeleton className='h-7 w-full bg-popover-accent rounded-md' />}>
            <DesktopModal className="flex w-full text-sm hover:bg-popover-accent cursor-pointer justify-between p-1.5 px-2" />
        </Suspense>
    );
    return (
        <Suspense fallback={null}>
            <MobileDrawer rruleOptions={rruleOptions} setRruleOptions={setRruleOptions} derivedRepeatType={derivedRepeatType} />
        </Suspense>
    );
}
