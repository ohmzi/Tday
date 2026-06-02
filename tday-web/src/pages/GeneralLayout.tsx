import { NativeAppPageLayout } from "@/components/app/nativeAppLayout";
import { Outlet } from "react-router-dom";

export default function GeneralLayout() {
  return (
    <NativeAppPageLayout>
      <Outlet />
    </NativeAppPageLayout>
  );
}
