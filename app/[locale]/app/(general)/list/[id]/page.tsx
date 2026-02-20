import ListContainer from "@/features/list/component/ListContainer";

const Page = async ({ params }: { params: Promise<{ id: string }> }) => {
  const { id } = await params;
  return <ListContainer id={id} />;
};

export default Page;
