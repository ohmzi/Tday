import AllTasksTimelineContainer from "@/features/todayTodos/component/AllTasksTimelineContainer";

export default function TodayTasksPage() {
  return (
    <div className="select-none bg-inherit">
      <AllTasksTimelineContainer scope="today" />
    </div>
  );
}
