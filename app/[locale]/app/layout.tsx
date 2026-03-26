import { auth } from "@/app/auth";
import { redirect } from "next/navigation";
import Provider from "./provider";
import SidebarContainer from "@/components/Sidebar/SidebarContainer";

export default async function Layout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const session = await auth();

  if (!session?.user) {
    redirect("/login");
  }

  if (session.user.approvalStatus !== "APPROVED") {
    redirect("/login?pending=1");
  }

  return (
    <Provider>
      {/* Timezone bootstrap */}
      <script
        async
        dangerouslySetInnerHTML={{
          __html: `
                (function () {
                  try {
                    const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;

                    fetch("/api/timezone", {
                      method: "GET",
                      headers: {
                        "x-timezone": tz,
                      },
                      credentials: "same-origin",
                    });
                  } catch (_) {}
                })();
              `,
        }}
      />
      <div className="flex min-h-screen h-screen w-full bg-background text-xs sm:text-sm md:text-base">
        <SidebarContainer />
        <div className="relative z-0 flex min-w-0 flex-1 flex-col overflow-hidden bg-background">
          <div className="pointer-events-none absolute inset-x-0 top-0 h-16 bg-gradient-to-b from-cream/70 to-transparent" />
          {children}
        </div>
      </div>
    </Provider>
  );
}
