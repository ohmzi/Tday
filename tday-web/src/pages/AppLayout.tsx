import { Outlet } from "react-router-dom";
import { MenuProvider } from "@/providers/MenuProvider";
import { UserPreferencesProvider } from "@/providers/UserPreferencesProvider";
import SidebarContainer from "@/components/Sidebar/SidebarContainer";
import ReleaseUpdateAnnouncer from "@/components/release/ReleaseUpdateAnnouncer";

export default function AppLayout() {
  return (
    <MenuProvider>
      <UserPreferencesProvider>
        <div className="flex min-h-screen h-screen">
          <SidebarContainer />
          <div className="relative z-0 flex min-w-0 flex-1 flex-col overflow-hidden">
            <ReleaseUpdateAnnouncer />
            <Outlet />
          </div>
        </div>
      </UserPreferencesProvider>
    </MenuProvider>
  );
}
