import { Outlet } from "react-router-dom";

export default function GeneralLayout() {
  return (
    <div className="h-full w-full overflow-y-auto scrollbar-none px-4 pb-[calc(7rem+env(safe-area-inset-bottom))] pt-4 sm:px-6 sm:pt-6 lg:px-10 xl:px-14">
      <Outlet />
    </div>
  );
}
