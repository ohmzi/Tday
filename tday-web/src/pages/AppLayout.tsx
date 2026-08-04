import { Outlet } from "react-router-dom";
import { useIsLocalMode } from "@/hooks/useAppMode";
import { MenuProvider } from "@/providers/MenuProvider";
import { UserPreferencesProvider } from "@/providers/UserPreferencesProvider";
import CreateTaskProvider from "@/providers/CreateTaskProvider";
import CreateFloaterProvider from "@/providers/CreateFloaterProvider";
import ReleaseUpdateAnnouncer from "@/components/release/ReleaseUpdateAnnouncer";
import NativeAppShell from "@/components/app/NativeAppShell";
import KeyboardLayer from "@/features/palette/KeyboardLayer";
import RealtimeInvalidator from "@/lib/realtime";

export default function AppLayout() {
  // Local Mode has no server to push change events from — nothing to connect to.
  const isLocalMode = useIsLocalMode();

  return (
    <MenuProvider>
      <UserPreferencesProvider>
        <CreateTaskProvider>
          <CreateFloaterProvider>
            {isLocalMode ? null : <RealtimeInvalidator />}
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
