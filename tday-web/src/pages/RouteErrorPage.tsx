import {
  useRouteError,
  isRouteErrorResponse,
  Link,
  useParams,
} from "react-router-dom";
import {
  RefreshCw,
  Home,
  WifiOff,
  FileQuestion,
  ServerCrash,
  ShieldAlert,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { DEFAULT_LOCALE } from "@/i18n";

type ErrorVariant = "chunk" | "notFound" | "network" | "server" | "generic";

interface ErrorMeta {
  code: string;
  icon: React.ReactNode;
  title: string;
  description: string;
  suggestion: string;
}

const iconClass = "h-7 w-7";

/** Maps a route error to a UI variant based on status code or message pattern. */
function classify(error: unknown): ErrorVariant {
  if (isRouteErrorResponse(error)) {
    if (error.status === 404) return "notFound";
    if (error.status >= 500) return "server";
  }

  if (error instanceof Error) {
    const msg = error.message.toLowerCase();
    if (
      msg.includes("dynamically imported module") ||
      msg.includes("failed to fetch dynamically imported module") ||
      msg.includes("loading chunk") ||
      msg.includes("loading css chunk")
    )
      return "chunk";

    if (
      msg.includes("failed to fetch") ||
      msg.includes("networkerror") ||
      msg.includes("network request failed") ||
      msg.includes("err_internet_disconnected") ||
      msg.includes("err_name_not_resolved") ||
      msg.includes("load failed")
    )
      return "network";
  }

  return "generic";
}

/** Returns display copy and icon for a given error variant. */
function getMeta(variant: ErrorVariant): ErrorMeta {
  switch (variant) {
    case "chunk":
      return {
        code: "Update",
        icon: <RefreshCw className={iconClass} />,
        title: "New version available",
        description:
          "The app has been updated since you last loaded it. A quick reload will get you back on track.",
        suggestion: "This usually happens after we ship improvements.",
      };
    case "notFound":
      return {
        code: "404",
        icon: <FileQuestion className={iconClass} />,
        title: "Page not found",
        description:
          "The page you're looking for doesn't exist or has been moved.",
        suggestion: "Check the URL or head back home.",
      };
    case "network":
      return {
        code: "Offline",
        icon: <WifiOff className={iconClass} />,
        title: "Connection lost",
        description:
          "We couldn't reach the server. Check your internet connection and try again.",
        suggestion: "This is usually a temporary issue.",
      };
    case "server":
      return {
        code: "500",
        icon: <ServerCrash className={iconClass} />,
        title: "Server hiccup",
        description:
          "Something went wrong on our end. We've been notified and are looking into it.",
        suggestion: "Try again in a moment.",
      };
    case "generic":
    default:
      return {
        code: "Error",
        icon: <ShieldAlert className={iconClass} />,
        title: "Something went wrong",
        description:
          "An unexpected error occurred. We've logged it so we can investigate.",
        suggestion: "Try refreshing the page.",
      };
  }
}

/** Route-level error boundary page used as `errorElement` in the router. */
export default function RouteErrorPage() {
  const error = useRouteError();
  const params = useParams<{ locale?: string }>();

  const variant = classify(error);
  const meta = getMeta(variant);
  const locale = params.locale || DEFAULT_LOCALE;
  const homePath = `/${locale}/app/tday`;

  return (
    <div className="relative flex min-h-screen w-full items-center justify-center overflow-hidden bg-background px-6">
      {/* watermark */}
      <span
        aria-hidden
        className="pointer-events-none absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 select-none text-[min(28vw,14rem)] font-extrabold leading-none tracking-tighter text-muted-foreground/[0.06]"
      >
        {meta.code}
      </span>

      <div className="relative z-10 mx-auto flex max-w-md flex-col items-center text-center">
        {/* icon badge */}
        <div className="mb-6 flex h-16 w-16 items-center justify-center rounded-2xl bg-muted/60 text-muted-foreground ring-1 ring-border/60">
          {meta.icon}
        </div>

        {/* copy */}
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">
          {meta.title}
        </h1>
        <p className="mt-2 text-[0.938rem] leading-relaxed text-muted-foreground">
          {meta.description}
        </p>
        <p className="mt-1 text-sm text-muted-foreground/70">
          {meta.suggestion}
        </p>

        {/* actions */}
        <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
          {variant === "chunk" || variant === "network" ? (
            <Button onClick={() => window.location.reload()}>
              <RefreshCw className="h-4 w-4" />
              Reload page
            </Button>
          ) : (
            <Button asChild>
              <Link to={homePath}>
                <Home className="h-4 w-4" />
                Go home
              </Link>
            </Button>
          )}

          {variant !== "chunk" && variant !== "network" && (
            <Button variant="outline" onClick={() => window.location.reload()}>
              <RefreshCw className="h-4 w-4" />
              Reload
            </Button>
          )}

          {(variant === "chunk" || variant === "network") && (
            <Button variant="outline" asChild>
              <Link to={homePath}>
                <Home className="h-4 w-4" />
                Go home
              </Link>
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
