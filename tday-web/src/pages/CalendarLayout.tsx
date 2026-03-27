import { Outlet } from "react-router-dom";
import SidebarToggleContainer from "@/components/Sidebar/SidebarToggleContainer";

export default function CalendarLayout() {
  return (
    <div className="h-full w-full overflow-hidden px-4 sm:px-6 sm:pt-4 lg:px-8 xl:px-10">
      <SidebarToggleContainer />
      <Outlet />
    </div>
  );
}
