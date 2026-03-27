import AllTasksTimelineContainer from "@/features/todayTodos/component/AllTasksTimelineContainer";

export default function PriorityPage() {
  return (
    <div className="select-none bg-inherit">
      <AllTasksTimelineContainer scope="priority" />
    </div>
  );
}
