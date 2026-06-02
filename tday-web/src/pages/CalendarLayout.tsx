import { Outlet } from "react-router-dom";

export default function CalendarLayout() {
  return (
    <div className="h-full w-full overflow-hidden px-4 pb-[calc(6rem+env(safe-area-inset-bottom))] pt-4 sm:px-6 sm:pt-6 lg:px-8 xl:px-10">
      <Outlet />
    </div>
  );
}
