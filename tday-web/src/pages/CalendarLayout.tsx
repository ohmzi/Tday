import { NativeAppPageLayout } from "@/components/app/nativeAppLayout";
import { CalendarCreateActionProvider } from "@/features/calendar/context/CalendarCreateActionContext";
import { Outlet } from "react-router-dom";

export default function CalendarLayout() {
  return (
    <NativeAppPageLayout>
      <CalendarCreateActionProvider>
        <Outlet />
      </CalendarCreateActionProvider>
    </NativeAppPageLayout>
  );
}
