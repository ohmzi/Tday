import ProjectContainer from "@/features/project/component/ProjectContainer";

const Page = async ({ params }: { params: Promise<{ id: string }> }) => {
  const { id } = await params;
  return <ProjectContainer id={id} />;
};

export default Page;
