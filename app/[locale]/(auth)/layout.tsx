import { SonnerToaster } from "@/components/ui/sonner";

export default function layout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <div className="relative flex min-h-screen items-center justify-center bg-background">
      <div className="absolute inset-0 bg-gradient-to-br from-background via-background to-muted/30" />
      <div className="relative z-10 mx-4 w-full max-w-md">{children}</div>
      <SonnerToaster />
    </div>
  );
}
