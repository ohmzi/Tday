import { useParams } from "react-router-dom";
import FloaterListContainer from "@/features/floaterList/component/FloaterListContainer";

export default function FloaterListPage() {
  const { id } = useParams();
  if (!id) return null;
  return <FloaterListContainer id={id} />;
}
