import { useEffect, useState } from "react";
import { Check, Loader2, ShieldQuestion } from "lucide-react";
import { api, ApiError } from "@/lib/api-client";
import { getErrorMessage } from "@/lib/error-message";
import { cn } from "@/lib/utils";
import {
  fetchQuestionsForUsername,
  verifySecurityAnswers,
  type SecurityAnswerInput,
  type SecurityQuestion,
} from "@/lib/securityQuestions";

// Staged wizard — the bare content, designed to live inside the login dialog's card
// (reusing the same dialog, like the create-account panel does) and the standalone
// /forgot-password page.
//   username  → validate against the backend; block + warn if no such account
//   challenge → answer 2 of the stored questions; verified before the password step.
//               A wrong answer swaps that question for an unshown one and keeps cycling.
//   password  → only reachable after answers verified
//   success   → "Password changed" + OK; auto-returns to sign in after 2s
//   locked / requested → too many misses → ask an admin
type Step = "username" | "challenge" | "password" | "success" | "locked" | "requested";

const RETURN_DELAY_MS = 2000;

// Same rose tint the login wizard's Sign in hero tile uses (TINT.heroRose).
const HERO_TINT = "rgb(201, 120, 128)";

const FIELD_CLASS =
  "h-[54px] w-full rounded-[22px] border border-border bg-muted/50 px-4 text-[15px] font-bold text-foreground shadow-sm outline-none transition placeholder:font-bold placeholder:text-foreground/40 focus:border-primary/80 focus:ring-1 focus:ring-primary/40";

export default function ForgotPasswordPanel({
  initialUsername = "",
  onBackToLogin,
}: {
  initialUsername?: string;
  /** Called to leave the flow. `resetUsername` is set only after a successful reset
   * so the caller can prefill the sign-in field. */
  onBackToLogin: (resetUsername?: string) => void;
}) {
  const [step, setStep] = useState<Step>("username");
  const [username, setUsername] = useState(initialUsername);
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

  // On the success step, return to sign in after a short pause; the OK button skips it.
  useEffect(() => {
    if (step !== "success") return;
    const timer = setTimeout(() => onBackToLogin(username), RETURN_DELAY_MS);
    return () => clearTimeout(timer);
  }, [step, username, onBackToLogin]);

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
      setStep("success");
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
      setErrorMessage(getErrorMessage(error, "Unable to send request. Please try again."));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex flex-col gap-4">
      {/* Same red hero tile the Sign in panel uses, so the reset flow reads as the same dialog. */}
      <div
        className="relative flex h-[78px] items-center overflow-hidden rounded-[26px] px-3.5"
        style={{ backgroundColor: HERO_TINT }}
      >
        <div
          className="pointer-events-none absolute inset-0"
          style={{
            background:
              "radial-gradient(210px at 18% 18%, rgba(255,255,255,0.24), rgba(255,255,255,0.08) 38%, transparent 70%)",
          }}
        />
        <ShieldQuestion
          className="pointer-events-none absolute right-2 top-2.5 h-[82px] w-[82px] text-white/20"
          strokeWidth={1.5}
        />
        <div className="relative flex min-w-0 items-center gap-3">
          <div className="flex h-[42px] w-[42px] shrink-0 items-center justify-center rounded-2xl bg-white/[0.18]">
            <ShieldQuestion className="h-[23px] w-[23px] text-white" strokeWidth={2.25} />
          </div>
          <p className="truncate text-[21px] font-bold leading-tight text-white">Reset password</p>
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

      {step === "success" && (
        <div className="flex flex-col gap-3">
          <div className="flex items-center gap-2.5">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-primary/15 text-primary">
              <Check className="h-5 w-5" strokeWidth={3} />
            </span>
            <p className="text-[16px] font-extrabold text-foreground">Password changed</p>
          </div>
          <PrimaryButton busy={false} label="OK" onClick={() => onBackToLogin(username)} />
        </div>
      )}

      {step === "locked" && (
        <div className="flex flex-col gap-3">
          <p className="text-[14px] font-bold text-foreground/70">
            Too many incorrect attempts. You can request a password reset from an
            administrator.
          </p>
          {errorMessage && <ErrorText>{errorMessage}</ErrorText>}
          <PrimaryButton
            busy={busy}
            label="Request reset from an administrator"
            onClick={requestAdminReset}
          />
        </div>
      )}

      {step === "requested" && (
        <p className="text-[14px] font-bold text-foreground/70">
          Your request has been sent. An administrator will reset your password and share a
          temporary one with you.
        </p>
      )}

      {step !== "success" && step !== "requested" && (
        <div className="pt-1 text-center">
          <button
            type="button"
            onClick={() => onBackToLogin()}
            className="text-[14px] font-bold text-primary transition active:opacity-60"
          >
            Back to sign in
          </button>
        </div>
      )}
    </div>
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
