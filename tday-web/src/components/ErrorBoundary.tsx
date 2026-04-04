import React, { Component, ReactNode } from "react";
import * as Sentry from "@sentry/react";
import { RefreshCw, ShieldAlert, Home } from "lucide-react";

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  isChunkError: boolean;
}

function isChunkLoadError(error: unknown): boolean {
  if (error instanceof Error) {
    return /dynamically imported module|loading chunk|loading css chunk/i.test(
      error.message,
    );
  }
  return false;
}

class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, isChunkError: false };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, isChunkError: isChunkLoadError(error) };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    Sentry.captureException(error, {
      contexts: { react: { componentStack: errorInfo.componentStack } },
    });
    console.error("Error caught by ErrorBoundary:", error, errorInfo);
  }

  handleReload = () => {
    window.location.reload();
  };

  handleReset = () => {
    this.setState({ hasError: false, isChunkError: false });
  };

  handleGoHome = () => {
    window.location.href = "/";
  };

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) return this.props.fallback;

      const { isChunkError } = this.state;

      return (
        <div className="relative flex min-h-screen w-full items-center justify-center overflow-hidden bg-background px-6">
          <span
            aria-hidden
            className="pointer-events-none absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 select-none text-[min(28vw,14rem)] font-extrabold leading-none tracking-tighter text-muted-foreground/[0.06]"
          >
            {isChunkError ? "Update" : "Error"}
          </span>

          <div className="relative z-10 mx-auto flex max-w-md flex-col items-center text-center">
            <div className="mb-6 flex h-16 w-16 items-center justify-center rounded-2xl bg-muted/60 text-muted-foreground ring-1 ring-border/60">
              {isChunkError ? (
                <RefreshCw className="h-7 w-7" />
              ) : (
                <ShieldAlert className="h-7 w-7" />
              )}
            </div>

            <h1 className="text-2xl font-semibold tracking-tight text-foreground">
              {isChunkError ? "New version available" : "Something went wrong"}
            </h1>
            <p className="mt-2 text-[0.938rem] leading-relaxed text-muted-foreground">
              {isChunkError
                ? "The app has been updated since you last loaded it. A quick reload will get you back on track."
                : "An unexpected error occurred. We've logged it so we can investigate."}
            </p>
            <p className="mt-1 text-sm text-muted-foreground/70">
              {isChunkError
                ? "This usually happens after we ship improvements."
                : "Try refreshing the page."}
            </p>

            <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
              <button
                type="button"
                onClick={this.handleReload}
                className="inline-flex cursor-pointer items-center gap-2 rounded-md bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/80"
              >
                <RefreshCw className="h-4 w-4" />
                Reload page
              </button>

              {!isChunkError && (
                <button
                  type="button"
                  onClick={this.handleGoHome}
                  className="inline-flex cursor-pointer items-center gap-2 rounded-md border border-input bg-background px-4 py-2.5 text-sm font-medium transition-colors hover:bg-accent hover:text-accent-foreground"
                >
                  <Home className="h-4 w-4" />
                  Go home
                </button>
              )}
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
