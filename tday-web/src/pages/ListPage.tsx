import { useParams } from "react-router-dom";
import ListContainer from "@/features/list/component/ListContainer";

export default function ListPage() {
  const { id } = useParams();
  if (!id) return null;
  return <ListContainer id={id} />;
}
