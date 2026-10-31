import { Link, useParams } from "react-router-dom";
import { Home } from "lucide-react";
import Illustration from "@/components/illustrations/Illustration";
import { Button } from "@/components/ui/button";
import { DEFAULT_LOCALE } from "@/i18n";

export default function NotFoundPage() {
  const { locale } = useParams();
  const loc = locale || DEFAULT_LOCALE;

  return (
    <div className="relative flex min-h-screen w-full items-center justify-center overflow-hidden bg-background px-6">
      <span
        aria-hidden
        className="pointer-events-none absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 select-none text-[min(28vw,14rem)] font-extrabold leading-none tracking-tighter text-muted-foreground/[0.06]"
      >
        404
      </span>

      <div className="relative z-10 mx-auto flex max-w-md flex-col items-center text-center">
        <Illustration id="lost" className="mb-6 max-w-[320px]" />

        <h1 className="text-2xl font-semibold tracking-tight text-foreground">
          Page not found
        </h1>
        <p className="mt-2 text-[0.938rem] leading-relaxed text-muted-foreground">
          The page you&apos;re looking for doesn&apos;t exist or has been moved.
        </p>
        <p className="mt-1 text-sm text-muted-foreground/70">
          Check the URL or head back home.
        </p>

        <div className="mt-8">
          <Button asChild>
            <Link to={`/${loc}/app/tday`}>
              <Home className="h-4 w-4" />
              Go home
            </Link>
          </Button>
        </div>
      </div>
    </div>
  );
}
