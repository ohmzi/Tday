import { Outlet } from "react-router-dom";
import { MenuProvider } from "@/providers/MenuProvider";
import { UserPreferencesProvider } from "@/providers/UserPreferencesProvider";
import CreateTaskProvider from "@/providers/CreateTaskProvider";
import ReleaseUpdateAnnouncer from "@/components/release/ReleaseUpdateAnnouncer";
import NativeAppShell from "@/components/app/NativeAppShell";

export default function AppLayout() {
  return (
    <MenuProvider>
      <UserPreferencesProvider>
        <CreateTaskProvider>
          <NativeAppShell>
            <div className="relative z-0 flex min-w-0 flex-1 flex-col overflow-hidden">
              <ReleaseUpdateAnnouncer />
              <Outlet />
            </div>
          </NativeAppShell>
        </CreateTaskProvider>
      </UserPreferencesProvider>
    </MenuProvider>
  );
}
