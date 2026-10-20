import AllTasksTimelineContainer from "@/features/todayTodos/component/AllTasksTimelineContainer";

export default function TodoPage() {
  return (
    <div className="select-none bg-inherit">
      <AllTasksTimelineContainer scope="all" />
    </div>
  );
}
