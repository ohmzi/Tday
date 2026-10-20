import NativeScheduledTaskHomeDashboard from "@/features/scheduledTaskHome/component/NativeScheduledTaskHomeDashboard";
import { ShareQuickAddBridge } from "@/features/share/ShareQuickAddBridge";

export default function ScheduledTaskHomePage() {
  return (
    <div className="select-none bg-inherit">
      <ShareQuickAddBridge />
      <NativeScheduledTaskHomeDashboard />
    </div>
  );
}
