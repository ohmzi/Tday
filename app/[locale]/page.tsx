import OnboardingLanding from "@/components/landing/OnboardingLanding";
import { auth } from "@/app/auth";
import { redirect } from "next/navigation";

const Page = async () => {
  const session = await auth();
  if (!session?.user) return <OnboardingLanding />;
  redirect("/app/tday");
};

export default Page;
