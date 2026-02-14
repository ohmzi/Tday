import ProjectContainer from "@/features/project/component/ProjectContainer";

const Page = async ({ params }: { params: Promise<{ id: string }> }) => {
  const { id } = await params;
  return <div className="pt-6 sm:pt-0"><ProjectContainer id={id} /></div>;
};

export default Page;
