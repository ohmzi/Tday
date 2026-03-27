import { Outlet } from "react-router-dom";
import SidebarToggleContainer from "@/components/Sidebar/SidebarToggleContainer";

export default function GeneralLayout() {
  return (
    <div className="h-full w-full overflow-y-auto scrollbar-none px-4 pb-6 sm:px-6 sm:pt-4 sm:pb-8 lg:px-10 xl:px-14">
      <SidebarToggleContainer />
      <Outlet />
    </div>
  );
}
