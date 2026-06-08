import { useRouter } from "@/lib/navigation";
import AuthScreenShell from "@/components/auth/AuthScreenShell";
import ForgotPasswordPanel from "@/components/auth/ForgotPasswordPanel";

// Standalone /forgot-password page. Reuses the same staged reset flow the login dialog
// embeds (ForgotPasswordPanel); on completion or "Back to sign in" it returns to /login.
export default function ForgotPasswordFlow() {
  const router = useRouter();

  return (
    <AuthScreenShell>
      <div className="relative w-full max-w-[440px] overflow-hidden rounded-[34px] border border-border bg-background/95 p-[18px] shadow-2xl backdrop-blur-md">
        <div className="pointer-events-none absolute inset-0 rounded-[34px] bg-gradient-to-br from-white/10 to-transparent dark:from-white/[0.04]" />
        <div className="relative">
          <ForgotPasswordPanel onBackToLogin={() => router.replace("/login")} />
        </div>
      </div>
    </AuthScreenShell>
  );
}
