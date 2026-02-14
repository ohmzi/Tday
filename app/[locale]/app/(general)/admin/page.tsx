import { auth } from "@/app/auth";
import { redirect } from "next/navigation";
import AdminUserControl from "@/components/admin/AdminUserControl";

export default async function Page() {
  const session = await auth();

  if (!session?.user) {
    redirect("/login");
  }

  if (
    session.user.role !== "ADMIN" ||
    session.user.approvalStatus !== "APPROVED"
  ) {
    redirect("/app/tday");
  }

  return <AdminUserControl />;
}
