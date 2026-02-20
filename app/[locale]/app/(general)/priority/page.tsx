import React from "react";
import AllTasksTimelineContainer from "@/features/todayTodos/component/AllTasksTimelineContainer";

const Page = () => {
  return (
    <div className="select-none bg-inherit">
      <AllTasksTimelineContainer scope="priority" />
    </div>
  );
};

export default Page;
