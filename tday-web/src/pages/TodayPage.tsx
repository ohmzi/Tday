import NativeHomeDashboard from "@/features/home/component/NativeHomeDashboard";
import { ShareQuickAddBridge } from "@/features/share/ShareQuickAddBridge";

export default function TodayPage() {
  return (
    <div className="select-none bg-inherit">
      <ShareQuickAddBridge />
      <NativeHomeDashboard />
    </div>
  );
}
