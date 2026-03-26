import { Link } from "@/i18n/navigation";
import { Home } from "lucide-react";

export default function RootLayout({ children }) {
  return (
    <>
      <div className="mb-4 h-16 w-full border p-4">
        <Link href="/app/tday">
          <Home className="h-8 w-8 text-foreground/50 hover:text-foreground" />
        </Link>
      </div>
      <div className="flex flex-col justify-between gap-6 px-4 sm:px-8 md:px-16 lg:flex-row lg:gap-10 lg:px-32 2xl:px-80">
        {children}
      </div>
    </>
  );
}
