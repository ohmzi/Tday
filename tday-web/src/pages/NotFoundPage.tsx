import { Link, useParams } from "react-router-dom";
import { DEFAULT_LOCALE } from "@/i18n";

export default function NotFoundPage() {
  const { locale } = useParams();
  const loc = locale || DEFAULT_LOCALE;

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-gradient-to-br from-background to-muted/30 px-4">
      <div className="text-center">
        <div className="mb-8">
          <h1 className="text-7xl font-bold">404</h1>
          <div className="mx-auto mt-2 h-1 w-24 rounded" />
        </div>

        <h2 className="mb-4 text-3xl font-semibold">Page Not Found</h2>
        <p className="mb-8">
          The page you&apos;re looking for doesn&apos;t exist or has been moved.
        </p>

        <div className="flex flex-col justify-center gap-4 sm:flex-row">
          <Link
            to={`/${loc}/app/tday`}
            className="rounded-lg border px-6 py-3 font-medium transition-colors"
          >
            Go Back
          </Link>
        </div>

        <div className="mt-12">
          <p className="text-8xl font-bold text-muted-foreground">
            &gt;&lt;
          </p>
        </div>
      </div>
    </div>
  );
}
