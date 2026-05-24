import { Loader2 } from "lucide-react";

export default function AuthBootstrapScreen() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background" role="status">
      <Loader2 className="h-8 w-8 animate-spin text-accent" />
    </div>
  );
}
