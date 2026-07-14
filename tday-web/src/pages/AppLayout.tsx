import { Outlet } from "react-router-dom";
import { MenuProvider } from "@/providers/MenuProvider";
import { UserPreferencesProvider } from "@/providers/UserPreferencesProvider";
import CreateTaskProvider from "@/providers/CreateTaskProvider";
import CreateFloaterProvider from "@/providers/CreateFloaterProvider";
import ReleaseUpdateAnnouncer from "@/components/release/ReleaseUpdateAnnouncer";
import NativeAppShell from "@/components/app/NativeAppShell";
import KeyboardLayer from "@/features/palette/KeyboardLayer";
import RealtimeInvalidator from "@/lib/realtime";

export default function AppLayout() {
  return (
    <MenuProvider>
      <UserPreferencesProvider>
        <CreateTaskProvider>
          <CreateFloaterProvider>
            <RealtimeInvalidator />
            <KeyboardLayer />
            <NativeAppShell>
              <div className="relative z-0 flex min-w-0 flex-1 flex-col overflow-hidden">
                <ReleaseUpdateAnnouncer />
                <Outlet />
              </div>
            </NativeAppShell>
          </CreateFloaterProvider>
        </CreateTaskProvider>
      </UserPreferencesProvider>
    </MenuProvider>
  );
}
