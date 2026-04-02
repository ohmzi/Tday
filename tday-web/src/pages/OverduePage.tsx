import AllTasksTimelineContainer from "@/features/todayTodos/component/AllTasksTimelineContainer";

export default function OverduePage() {
  return (
    <div className="select-none bg-inherit">
      <AllTasksTimelineContainer scope="overdue" />
    </div>
  );
}
