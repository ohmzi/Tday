import { useState } from "react";
import { Loader2, ShieldQuestion } from "lucide-react";
import { useRouter, Link } from "@/lib/navigation";
import { api, ApiError } from "@/lib/api-client";
import { getErrorMessage } from "@/lib/error-message";
import { useToast } from "@/hooks/use-toast";
import { cn } from "@/lib/utils";
import AuthScreenShell from "@/components/auth/AuthScreenShell";
import {
  fetchQuestionsForUsername,
  verifySecurityAnswers,
  type SecurityAnswerInput,
  type SecurityQuestion,
} from "@/lib/securityQuestions";

// Staged wizard:
//   username  → validate against the backend; block + warn if no such account
//   challenge → answer 2 of the stored questions; verified before the password step.
//               A wrong answer swaps that question for an unshown one and keeps cycling.
//   password  → only reachable after answers verified
//   locked / requested → too many misses → ask an admin
type Step = "username" | "challenge" | "password" | "locked" | "requested";

const FIELD_CLASS =
  "h-[54px] w-full rounded-[22px] border border-border bg-muted/50 px-4 text-[15px] font-bold text-foreground shadow-sm outline-none transition placeholder:font-bold placeholder:text-foreground/40 focus:border-primary/80 focus:ring-1 focus:ring-primary/40";

export default function ForgotPasswordFlow() {
  const router = useRouter();
  const { toast } = useToast();

  const [step, setStep] = useState<Step>("username");
  const [username, setUsername] = useState("");
  const [questions, setQuestions] = useState<SecurityQuestion[]>([]);
  const [shownIds, setShownIds] = useState<number[]>([]);
  const [answers, setAnswers] = useState<Record<number, string>>({});
  const [verifiedAnswers, setVerifiedAnswers] = useState<SecurityAnswerInput[]>([]);
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [errorMessage, setErrorMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [failedAttempts, setFailedAttempts] = useState(0);

  const questionText = (id: number) => questions.find((q) => q.id === id)?.text ?? "";

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
        setErrorMessage("Security questions are unavailable for this account.");
        return;
      }
      setUsername(normalized);
      setQuestions(fetched);
      setShownIds([fetched[0].id, fetched[1].id]);
      setAnswers({});
      setStep("challenge");
    } catch (error) {
      if (error instanceof ApiError && error.code === "user_not_found") {
        setErrorMessage("We couldn't find an account with that username.");
        return;
      }
      if (error instanceof ApiError && error.code === "reset_locked") {
        setStep("locked");
        return;
      }
      setErrorMessage(getErrorMessage(error, "Unable to load security questions."));
    } finally {
      setBusy(false);
    }
  };

  // Replace each wrongly-answered question with a random not-yet-shown one (cycling the
  // 3rd question in). With only two stored questions there's nothing to swap.
  const cycleFailedQuestions = (wrongIds: number[]) => {
    const pool = questions.map((q) => q.id).filter((id) => !shownIds.includes(id));
    if (pool.length === 0) return;
    const next = shownIds.map((id) => {
      if (wrongIds.includes(id) && pool.length > 0) {
        const idx = Math.floor(Math.random() * pool.length);
        return pool.splice(idx, 1)[0];
      }
      return id;
    });
    setShownIds(next);
    setAnswers({});
  };

  const submitAnswers = async (event: React.FormEvent) => {
    event.preventDefault();
    setErrorMessage("");
    const payload: SecurityAnswerInput[] = shownIds.map((id) => ({
      questionId: id,
      answer: (answers[id] ?? "").trim(),
    }));
    if (payload.some((a) => a.answer.length === 0)) {
      setErrorMessage("Please answer both questions.");
      return;
    }
    setBusy(true);
    try {
      const result = await verifySecurityAnswers(username, payload);
      if (result.valid) {
        setVerifiedAnswers(payload);
        setNewPassword("");
        setConfirmPassword("");
        setStep("password");
        return;
      }
      const wrongIds = result.results.filter((r) => !r.correct).map((r) => r.questionId);
      const nextFailed = failedAttempts + 1;
      setFailedAttempts(nextFailed);
      cycleFailedQuestions(wrongIds.length > 0 ? wrongIds : shownIds);
      setErrorMessage(
        nextFailed > 2
          ? "Those answers didn't match. Please contact an administrator to reset your password."
          : "Those answers didn't match. Please try again.",
      );
    } catch (error) {
      if (error instanceof ApiError && error.code === "reset_locked") {
        setStep("locked");
        return;
      }
      setErrorMessage(getErrorMessage(error, "Unable to verify your answers."));
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
        body: JSON.stringify({ username, answers: verifiedAnswers, newPassword }),
      });
      toast({ description: "Password reset. Sign in with your new password." });
      router.replace("/login");
    } catch (error) {
      if (error instanceof ApiError && error.code === "reset_locked") {
        setStep("locked");
        return;
      }
      if (error instanceof ApiError && error.code === "reset_failed") {
        // Answers no longer line up (e.g. cycled mid-flight) — send back to verify.
        setStep("challenge");
        setAnswers({});
        setErrorMessage("Please re-enter your security answers.");
        return;
      }
      setErrorMessage(getErrorMessage(error, "Unable to reset password. Please try again."));
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
    <AuthScreenShell>
      <div className="relative w-full max-w-[440px] overflow-hidden rounded-[34px] border border-border bg-background/95 p-[18px] shadow-2xl backdrop-blur-md">
        <div className="pointer-events-none absolute inset-0 rounded-[34px] bg-gradient-to-br from-white/10 to-transparent dark:from-white/[0.04]" />

        <div className="relative flex flex-col gap-4">
          <div className="flex items-center gap-2.5">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-accent/15 text-accent">
              <ShieldQuestion className="h-5 w-5" strokeWidth={2.4} />
            </span>
            <div className="min-w-0">
              <p className="text-[21px] font-extrabold leading-none tracking-tight text-foreground">
                Reset password
              </p>
              {step === "challenge" || step === "password" ? (
                <p className="mt-1 truncate text-[13px] font-bold text-foreground/55">
                  {username}
                </p>
              ) : null}
            </div>
          </div>

          {step === "username" && (
            <form onSubmit={lookupQuestions} className="flex flex-col gap-3">
              <input
                autoFocus
                aria-label="Username"
                autoComplete="username"
                autoCapitalize="off"
                placeholder="Username"
                value={username}
                onChange={(e) => {
                  setUsername(e.target.value);
                  if (errorMessage) setErrorMessage("");
                }}
                className={FIELD_CLASS}
              />
              {errorMessage && <ErrorText>{errorMessage}</ErrorText>}
              <PrimaryButton busy={busy} label="Continue" />
            </form>
          )}

          {step === "challenge" && (
            <form onSubmit={submitAnswers} className="flex flex-col gap-3">
              {shownIds.map((id) => (
                <div key={id} className="flex flex-col gap-1.5">
                  <label className="px-1 text-[13px] font-extrabold text-foreground/70">
                    {questionText(id)}
                  </label>
                  <input
                    aria-label={questionText(id)}
                    autoComplete="off"
                    placeholder="Answer"
                    value={answers[id] ?? ""}
                    onChange={(e) => {
                      setAnswers((prev) => ({ ...prev, [id]: e.target.value }));
                      if (errorMessage) setErrorMessage("");
                    }}
                    className={FIELD_CLASS}
                  />
                </div>
              ))}
              {errorMessage && <ErrorText>{errorMessage}</ErrorText>}
              <PrimaryButton busy={busy} label="Verify answers" />
            </form>
          )}

          {step === "password" && (
            <form onSubmit={submitReset} className="flex flex-col gap-3">
              <input
                type="password"
                aria-label="New password"
                autoComplete="new-password"
                placeholder="New password"
                value={newPassword}
                onChange={(e) => {
                  setNewPassword(e.target.value);
                  if (errorMessage) setErrorMessage("");
                }}
                className={FIELD_CLASS}
              />
              <input
                type="password"
                aria-label="Confirm new password"
                autoComplete="new-password"
                placeholder="Confirm new password"
                value={confirmPassword}
                onChange={(e) => {
                  setConfirmPassword(e.target.value);
                  if (errorMessage) setErrorMessage("");
                }}
                className={FIELD_CLASS}
              />
              {errorMessage && <ErrorText>{errorMessage}</ErrorText>}
              <PrimaryButton busy={busy} label="Reset password" />
            </form>
          )}

          {step === "locked" && (
            <div className="flex flex-col gap-3">
              <p className="text-[14px] font-bold text-foreground/70">
                Too many incorrect attempts. You can request a password reset from an
                administrator.
              </p>
              <PrimaryButton busy={busy} label="Request reset from an administrator" onClick={requestAdminReset} />
            </div>
          )}

          {step === "requested" && (
            <p className="text-[14px] font-bold text-foreground/70">
              Your request has been sent. An administrator will reset your password and
              share a temporary one with you.
            </p>
          )}

          <div className="pt-1 text-center">
            <Link
              href="/login"
              className="text-[14px] font-bold text-primary transition active:opacity-60"
            >
              Back to sign in
            </Link>
          </div>
        </div>
      </div>
    </AuthScreenShell>
  );
}

function ErrorText({ children }: { children: React.ReactNode }) {
  return <p className="px-1 text-[13px] font-bold text-destructive">{children}</p>;
}

function PrimaryButton({
  label,
  busy,
  onClick,
}: {
  label: string;
  busy: boolean;
  onClick?: () => void;
}) {
  return (
    <button
      type={onClick ? "button" : "submit"}
      onClick={onClick}
      disabled={busy}
      className={cn(
        "relative flex h-12 w-full items-center justify-center gap-2 overflow-hidden rounded-full text-[15px] font-bold transition active:scale-[0.985]",
        "bg-primary text-primary-foreground shadow-lg shadow-primary/20",
        "disabled:cursor-not-allowed disabled:opacity-70",
      )}
    >
      <span className="pointer-events-none absolute inset-0 bg-gradient-to-br from-white/15 to-transparent" />
      {busy && <Loader2 className="relative h-4 w-4 animate-spin" />}
      <span className="relative">{label}</span>
    </button>
  );
}
