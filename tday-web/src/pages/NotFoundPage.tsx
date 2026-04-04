import { Link, useParams } from "react-router-dom";
import { Home, FileQuestion } from "lucide-react";
import { Button } from "@/components/ui/button";
import { DEFAULT_LOCALE } from "@/i18n";

export default function NotFoundPage() {
  const { locale } = useParams();
  const loc = locale || DEFAULT_LOCALE;

  const goHomeAction = (
    <Button asChild>
      <Link to={`/${loc}/app/tday`}>
        <Home className="h-4 w-4" />
        Go home
      </Link>
    </Button>
  );

  return (
    <div className="relative flex min-h-screen w-full items-center justify-center overflow-hidden bg-background px-6">
      <span
        aria-hidden
        className="pointer-events-none absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 select-none text-[min(28vw,14rem)] font-extrabold leading-none tracking-tighter text-muted-foreground/[0.06]"
      >
        404
      </span>

      <div className="relative z-10 mx-auto flex max-w-md flex-col items-center text-center">
        <div className="mb-6 flex h-16 w-16 items-center justify-center rounded-2xl bg-muted/60 text-muted-foreground ring-1 ring-border/60">
          <FileQuestion className="h-7 w-7" />
        </div>

        <h1 className="text-2xl font-semibold tracking-tight text-foreground">
          Page not found
        </h1>
        <p className="mt-2 text-[0.938rem] leading-relaxed text-muted-foreground">
          The page you&apos;re looking for doesn&apos;t exist or has been moved.
        </p>
        <p className="mt-1 text-sm text-muted-foreground/70">
          Check the URL or head back home.
        </p>

        <div className="mt-8">{goHomeAction}</div>
      </div>
    </div>
  );
}
