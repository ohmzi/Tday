import { redirect } from "next/navigation";
import { auth } from "@/app/auth";
import RegisterPage from "./Register";
const page = async () => {
  const session = await auth();
  if (!session?.user) return <RegisterPage />;
  redirect("/app/tday");
};
export default page;
