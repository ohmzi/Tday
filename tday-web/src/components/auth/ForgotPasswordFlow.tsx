import { useState } from "react";
import { Loader2, ShieldQuestion } from "lucide-react";
import { useRouter, Link } from "@/lib/navigation";
import { api, ApiError } from "@/lib/api-client";
import { getErrorMessage } from "@/lib/error-message";
import { useToast } from "@/hooks/use-toast";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  fetchQuestionsForUsername,
  type SecurityQuestion,
} from "@/lib/securityQuestions";

type Step = "username" | "challenge" | "locked" | "requested";

export default function ForgotPasswordFlow() {
  const router = useRouter();
  const { toast } = useToast();

  const [step, setStep] = useState<Step>("username");
  const [username, setUsername] = useState("");
  const [questions, setQuestions] = useState<SecurityQuestion[]>([]);
  const [answer1, setAnswer1] = useState("");
  const [answer2, setAnswer2] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [errorMessage, setErrorMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [failedAttempts, setFailedAttempts] = useState(0);

  const lookupQuestions = async (event: React.FormEvent) => {
    event.preventDefault();
    setErrorMessage("");
    const normalized = username.trim().toLowerCase();
    if (!normalized) {
      setErrorMessage("Please enter your username");
      return;
    }
    setBusy(true);
    try {
      const fetched = await fetchQuestionsForUsername(normalized);
      if (fetched.length < 2) {
        // Should not happen — the server always returns two questions — but fail safe.
        setErrorMessage("Security questions are unavailable for this account.");
        return;
      }
      setQuestions(fetched);
      setStep("challenge");
    } catch (error) {
      setErrorMessage(getErrorMessage(error, "Unable to load security questions."));
    } finally {
      setBusy(false);
    }
  };

  const submitReset = async (event: React.FormEvent) => {
    event.preventDefault();
    setErrorMessage("");
    if (newPassword.length < 8) {
      setErrorMessage("Password must be at least 8 characters");
      return;
    }
    if (!/[A-Z]/.test(newPassword)) {
      setErrorMessage("Password must include at least one uppercase letter");
      return;
    }
    if (!/[^A-Za-z0-9]/.test(newPassword)) {
      setErrorMessage("Password must include at least one special character");
      return;
    }
    if (newPassword !== confirmPassword) {
      setErrorMessage("Passwords do not match");
      return;
    }
    setBusy(true);
    try {
      await api.POST({
        url: "/api/auth/reset-password",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          username: username.trim().toLowerCase(),
          answers: [
            { questionId: questions[0].id, answer: answer1.trim() },
            { questionId: questions[1].id, answer: answer2.trim() },
          ],
          newPassword,
        }),
      });
      toast({ description: "Password reset. Sign in with your new password." });
      router.replace("/login");
    } catch (error) {
      if (error instanceof ApiError && error.code === "reset_locked") {
        setStep("locked");
        return;
      }
      const nextFailedAttempts = failedAttempts + 1;
      setFailedAttempts(nextFailedAttempts);
      const baseMessage = getErrorMessage(
        error,
        "Those answers didn't match. Please try again.",
      );
      // After more than two failed attempts, point the user to an admin reset.
      setErrorMessage(
        nextFailedAttempts > 2
          ? `${baseMessage} Please contact an administrator to reset your password.`
          : baseMessage,
      );
    } finally {
      setBusy(false);
    }
  };

  const requestAdminReset = async () => {
    setBusy(true);
    try {
      await api.POST({
        url: "/api/auth/request-admin-reset",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: username.trim().toLowerCase() }),
      });
      setStep("requested");
    } catch (error) {
      toast({
        description: getErrorMessage(error, "Unable to send request. Please try again."),
        variant: "destructive",
      });
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="relative flex min-h-screen items-center justify-center bg-background px-4 py-10 text-foreground">
      <Card className="w-full max-w-[440px] rounded-3xl border-border/70 bg-card/95 shadow-2xl">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ShieldQuestion className="h-5 w-5 text-accent" />
            Reset your password
          </CardTitle>
          <CardDescription>
            {step === "username" &&
              "Enter your username to answer your security questions."}
            {step === "locked" &&
              "Too many incorrect attempts. You can request a password reset from an administrator."}
            {step === "requested" &&
              "Your request has been sent. An administrator will reset your password and share a temporary one with you."}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {step === "username" && (
            <form onSubmit={lookupQuestions} className="space-y-3">
              <div className="space-y-1.5">
                <Label htmlFor="fpUsername">Username</Label>
                <Input
                  id="fpUsername"
                  autoComplete="username"
                  autoCapitalize="off"
                  value={username}
                  onChange={(e) => {
                    setUsername(e.target.value);
                    if (errorMessage) setErrorMessage("");
                  }}
                  required
                />
              </div>
              {errorMessage && (
                <p className="text-sm font-medium text-destructive">{errorMessage}</p>
              )}
              <Button type="submit" disabled={busy} className="w-full">
                {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
                Continue
              </Button>
            </form>
          )}

          {step === "challenge" && (
            <form onSubmit={submitReset} className="space-y-3">
              <div className="space-y-1.5">
                <Label htmlFor="fpAnswer1">{questions[0]?.text}</Label>
                <Input
                  id="fpAnswer1"
                  autoComplete="off"
                  value={answer1}
                  onChange={(e) => setAnswer1(e.target.value)}
                  required
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="fpAnswer2">{questions[1]?.text}</Label>
                <Input
                  id="fpAnswer2"
                  autoComplete="off"
                  value={answer2}
                  onChange={(e) => setAnswer2(e.target.value)}
                  required
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="fpNewPassword">New password</Label>
                <Input
                  id="fpNewPassword"
                  type="password"
                  autoComplete="new-password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  minLength={8}
                  required
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="fpConfirmPassword">Confirm new password</Label>
                <Input
                  id="fpConfirmPassword"
                  type="password"
                  autoComplete="new-password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  minLength={8}
                  required
                />
              </div>
              {errorMessage && (
                <p className="text-sm font-medium text-destructive">{errorMessage}</p>
              )}
              <Button type="submit" disabled={busy} className="w-full">
                {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
                Reset password
              </Button>
            </form>
          )}

          {step === "locked" && (
            <Button
              onClick={requestAdminReset}
              disabled={busy}
              variant="destructive"
              className="w-full"
            >
              {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              Request reset from an administrator
            </Button>
          )}

          <div className="pt-1 text-center">
            <Link
              href="/login"
              className="text-sm font-medium text-primary hover:underline"
            >
              Back to sign in
            </Link>
          </div>
        </CardContent>
      </Card>
    </main>
  );
}
