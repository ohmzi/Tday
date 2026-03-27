import AllTasksTimelineContainer from "@/features/todayTodos/component/AllTasksTimelineContainer";

export default function ScheduledPage() {
  return (
    <div className="select-none bg-inherit">
      <AllTasksTimelineContainer scope="scheduled" />
    </div>
  );
}
