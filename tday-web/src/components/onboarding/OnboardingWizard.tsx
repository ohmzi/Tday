import React from "react";
import {
  Check,
  Globe,
  Loader2,
  Lock,
  MoonStar,
  Smartphone,
  Sun,
  User,
  UserPlus,
} from "lucide-react";
import { useSearchParams } from "react-router-dom";
import { useRouter } from "@/lib/navigation";
import { cn } from "@/lib/utils";
import { useAuth } from "@/providers/AuthProvider";
import { api } from "@/lib/api-client";
import { getErrorMessage } from "@/lib/error-message";
import { createClientCredentialEnvelope } from "@/lib/security/clientCredentialEnvelope";
import PendingApprovalScreen from "@/components/auth/PendingApprovalScreen";
import {
  clearPendingApproval,
  getPendingApproval,
  setPendingApproval,
} from "@/lib/pendingApproval";
import { Link } from "@/lib/navigation";
import {
  fetchAllSecurityQuestions,
  type SecurityQuestion,
} from "@/lib/securityQuestions";

// Fixed tints lifted 1:1 from the native wizard (iOS/Android). These are
// intentionally theme-independent so the card reads identically across light
// and dark mode, matching the apps.
const TINT = {
  modeGreen: "rgb(128, 184, 138)", // step chip · "Mode"
  serverBlue: "rgb(110, 168, 224)", // step chip · "Server"
  loginRose: "rgb(212, 138, 140)", // step chip · "Login"
  heroRose: "rgb(201, 120, 128)", // hero tile · sign in / create
  sun: "rgb(245, 196, 66)",
} as const;

type AuthMode = "signin" | "create";

const USERNAME_REGEX = /^[a-z0-9](?:[a-z0-9._-]{1,28}[a-z0-9])$/;

type RegisterResponse = {
  requiresApproval?: boolean;
};

function isDaytime(): boolean {
  const hour = new Date().getHours();
  return hour >= 6 && hour < 18;
}

export default function OnboardingWizard({
  initialMode = "signin",
}: {
  initialMode?: AuthMode;
}) {
  const router = useRouter();
  const { login } = useAuth();
  const [searchParams] = useSearchParams();

  const [mode, setMode] = React.useState<AuthMode>(initialMode);
  const [username, setUsername] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [firstName, setFirstName] = React.useState("");
  const [registerPassword, setRegisterPassword] = React.useState("");
  const [confirmPassword, setConfirmPassword] = React.useState("");
  const [securityQuestions, setSecurityQuestions] = React.useState<SecurityQuestion[]>([]);
  const [questionId1, setQuestionId1] = React.useState<number | null>(null);
  const [answer1, setAnswer1] = React.useState("");
  const [questionId2, setQuestionId2] = React.useState<number | null>(null);
  const [answer2, setAnswer2] = React.useState("");
  const [questionId3, setQuestionId3] = React.useState<number | null>(null);
  const [answer3, setAnswer3] = React.useState("");
  const [errorMessage, setErrorMessage] = React.useState("");
  const [infoMessage, setInfoMessage] = React.useState("");
  const [isSubmitting, setIsSubmitting] = React.useState(false);
  // Initialise from the persisted marker synchronously so the holding screen is up on
  // the first paint — the bare login form never flashes behind it on a pending reload.
  const [pendingApprovalOpen, setPendingApprovalOpen] = React.useState(
    () => getPendingApproval() != null,
  );
  // Held in memory only (never persisted) so "Check approval status" can silently
  // re-attempt the login while the user stays on the holding screen this session.
  const [pendingPassword, setPendingPassword] = React.useState("");
  const [checkingApproval, setCheckingApproval] = React.useState(false);

  const isCreating = mode === "create";
  const daytime = isDaytime();

  // Reopen the holding screen on load whenever a pending marker is present (survives
  // reload) or the post-redirect ?pending=1 hint is set.
  React.useEffect(() => {
    if (searchParams.get("pending") === "1" || getPendingApproval()) {
      setPendingApprovalOpen(true);
    }
  }, [searchParams]);

  // Load the question catalogue when the user switches to account creation, then
  // default to the first two distinct questions.
  React.useEffect(() => {
    if (!isCreating || securityQuestions.length > 0) return;
    let cancelled = false;
    void fetchAllSecurityQuestions().then((questions) => {
      if (cancelled) return;
      setSecurityQuestions(questions);
      if (questions[0]) setQuestionId1((prev) => prev ?? questions[0].id);
      if (questions[1]) setQuestionId2((prev) => prev ?? questions[1].id);
      if (questions[2]) setQuestionId3((prev) => prev ?? questions[2].id);
    });
    return () => {
      cancelled = true;
    };
  }, [isCreating, securityQuestions.length]);

  const clearMessages = () => {
    if (errorMessage) setErrorMessage("");
    if (infoMessage) setInfoMessage("");
  };

  const switchMode = (next: AuthMode) => {
    setMode(next);
    setErrorMessage("");
    setPassword("");
    setRegisterPassword("");
    setConfirmPassword("");
  };

  const validateRegistration = (): boolean => {
    const normalizedFirstName = firstName.trim();
    const normalizedUsername = username.trim().toLowerCase();
    if (normalizedFirstName.length < 2) {
      setErrorMessage("First name must be at least 2 characters");
      return false;
    }
    if (!USERNAME_REGEX.test(normalizedUsername)) {
      setErrorMessage("Please enter a valid username");
      return false;
    }
    if (registerPassword.length < 8) {
      setErrorMessage("Password must be at least 8 characters");
      return false;
    }
    if (!/[A-Z]/.test(registerPassword)) {
      setErrorMessage("Password must include at least one uppercase letter");
      return false;
    }
    if (!/[^A-Za-z0-9]/.test(registerPassword)) {
      setErrorMessage("Password must include at least one special character");
      return false;
    }
    if (registerPassword !== confirmPassword) {
      setErrorMessage("Passwords do not match");
      return false;
    }
    if (questionId1 == null || questionId2 == null || questionId3 == null) {
      setErrorMessage("Please choose three security questions");
      return false;
    }
    if (new Set([questionId1, questionId2, questionId3]).size !== 3) {
      setErrorMessage("Security questions must be different");
      return false;
    }
    if (
      answer1.trim().length === 0 ||
      answer2.trim().length === 0 ||
      answer3.trim().length === 0
    ) {
      setErrorMessage("Please answer all three security questions");
      return false;
    }
    return true;
  };

  const handleSignIn = async () => {
    setErrorMessage("");
    setInfoMessage("");
    const normalizedUsername = username.trim().toLowerCase();
    if (!normalizedUsername || !password) {
      setErrorMessage("Username and password are required");
      return;
    }
    setIsSubmitting(true);
    try {
      const credentialPayload = await createClientCredentialEnvelope(username, password);
      const result = await login(
        normalizedUsername,
        credentialPayload as unknown as Record<string, string>,
      );

      if (!result.ok) {
        if (result.code === "pending_approval") {
          setPendingApproval(normalizedUsername);
          setPendingPassword(password);
          setPendingApprovalOpen(true);
          return;
        }
        setErrorMessage(result.message || "Unable to sign in. Please try again.");
        return;
      }

      // Approved sign-in: drop any lingering holding-screen marker.
      clearPendingApproval();
      router.replace("/app/tday");
    } catch (error) {
      console.error(error);
      setErrorMessage(
        error instanceof Error && error.message
          ? error.message
          : "Unable to sign in. Please try again.",
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCreate = async () => {
    setErrorMessage("");
    setInfoMessage("");
    if (!validateRegistration()) return;

    setIsSubmitting(true);
    try {
      const body = (await api.POST({
        url: "/api/auth/register",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          fname: firstName.trim(),
          lname: "",
          username: username.trim().toLowerCase(),
          password: registerPassword,
          securityAnswers: [
            { questionId: questionId1, answer: answer1.trim() },
            { questionId: questionId2, answer: answer2.trim() },
            { questionId: questionId3, answer: answer3.trim() },
          ],
        }),
      })) as RegisterResponse | null;

      if (body?.requiresApproval) {
        setPendingApproval(username.trim().toLowerCase());
        setPendingPassword(registerPassword);
        setPendingApprovalOpen(true);
        return;
      }

      // Account created (no approval needed) — drop back to sign in with the
      // username prefilled, mirroring the native flow's "open your workspace" step.
      setMode("signin");
      setPassword("");
      setRegisterPassword("");
      setConfirmPassword("");
      setInfoMessage("Account created. Sign in to open your workspace.");
    } catch (error) {
      console.error(error);
      setErrorMessage(
        getErrorMessage(error, "Unable to create account. Please try again."),
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  // Re-attempt the credentials login (the only way to detect approval — pending
  // accounts get no session). On success the user is approved → go to the app.
  const handleCheckApprovalStatus = async () => {
    const pendingUsername = getPendingApproval();
    // After a reload the in-memory password is gone; fall back to the sign-in form
    // (prefilled) so the user can re-enter it — we never persist the password.
    if (!pendingUsername || !pendingPassword) {
      setPendingApprovalOpen(false);
      setMode("signin");
      if (pendingUsername) setUsername(pendingUsername);
      setInfoMessage("Sign in to check your approval status.");
      return;
    }
    setCheckingApproval(true);
    try {
      const credentialPayload = await createClientCredentialEnvelope(
        pendingUsername,
        pendingPassword,
      );
      const result = await login(
        pendingUsername,
        credentialPayload as unknown as Record<string, string>,
      );
      if (result.ok) {
        clearPendingApproval();
        router.replace("/app/tday");
        return;
      }
      // Still pending (or a transient failure) — keep the holding screen up.
    } catch {
      // Swallow and stay on the holding screen; the user can retry.
    } finally {
      setCheckingApproval(false);
    }
  };

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (isCreating) {
      void handleCreate();
    } else {
      void handleSignIn();
    }
  };

  const signInEnabled = username.trim().length > 0 && password.length > 0;
  const createEnabled =
    firstName.trim().length > 0 &&
    username.trim().length > 0 &&
    registerPassword.length > 0 &&
    confirmPassword.length > 0 &&
    questionId1 != null &&
    questionId2 != null &&
    questionId3 != null &&
    new Set([questionId1, questionId2, questionId3]).size === 3 &&
    answer1.trim().length > 0 &&
    answer2.trim().length > 0 &&
    answer3.trim().length > 0;
  const primaryEnabled = (isCreating ? createEnabled : signInEnabled) && !isSubmitting;

  return (
    <main className="relative min-h-screen overflow-hidden bg-background text-foreground">
      <div className="absolute inset-0 bg-gradient-to-br from-background via-background to-muted/35" />
      <div className="absolute inset-0 bg-[radial-gradient(1200px_700px_at_12%_12%,hsla(var(--accent),0.11),transparent),radial-gradient(900px_500px_at_90%_18%,hsla(var(--primary),0.1),transparent)]" />

      <div className="absolute inset-0 p-3 sm:p-5 lg:p-8">
        <div className="h-full w-full rounded-3xl border border-border/70 bg-card/55 p-3 blur-xl sm:p-4">
          <MockTodayWorkspace />
        </div>
      </div>

      <div className="absolute inset-0 bg-background/60" />

      <div className="relative z-10 flex min-h-screen items-center justify-center px-4 py-10">
        <div className="relative w-full max-w-[440px] overflow-hidden rounded-[34px] border border-border bg-background/95 p-[18px] shadow-2xl backdrop-blur-md">
          <div className="pointer-events-none absolute inset-0 rounded-[34px] bg-gradient-to-br from-white/10 to-transparent dark:from-white/[0.04]" />

          <div className="relative flex flex-col gap-3.5">
            {/* Header */}
            <div className="flex items-center gap-2">
              {daytime ? (
                <Sun className="h-6 w-6" style={{ color: TINT.sun }} strokeWidth={2} />
              ) : (
                <MoonStar className="h-6 w-6" style={{ color: TINT.sun }} strokeWidth={2} />
              )}
              <span className="text-[31px] font-extrabold leading-none tracking-tight text-foreground">
                {"T'Day"}
              </span>
            </div>

            {/* Step chips */}
            <div className="flex items-center gap-2">
              <StepChip title="Mode" Icon={Smartphone} tint={TINT.modeGreen} completed />
              <StepChip title="Server" Icon={Globe} tint={TINT.serverBlue} completed />
              <StepChip title="Login" Icon={User} tint={TINT.loginRose} active />
            </div>

            {isSubmitting ? (
              <WizardLoadingPanel
                Icon={isCreating ? UserPlus : Lock}
                title={isCreating ? "Creating account" : "Authenticating"}
                subtitle={
                  isCreating
                    ? "Preparing your new T'Day account."
                    : "Completing encrypted sign in."
                }
              />
            ) : (
              <form onSubmit={handleSubmit} className="flex flex-col gap-[11px]">
                <HeroTile
                  title={isCreating ? "Create account" : "Sign in"}
                  Icon={isCreating ? UserPlus : User}
                  tint={TINT.heroRose}
                />

                {isCreating && (
                  <WizardInput
                    placeholder="First name"
                    autoComplete="given-name"
                    autoCapitalize="words"
                    value={firstName}
                    onChange={(value) => {
                      setFirstName(value);
                      clearMessages();
                    }}
                  />
                )}

                <WizardInput
                  placeholder="Username"
                  type="text"
                  autoComplete="username"
                  autoCapitalize="off"
                  value={username}
                  onChange={(value) => {
                    setUsername(value);
                    clearMessages();
                  }}
                />

                {isCreating ? (
                  <>
                    <WizardInput
                      placeholder="Password"
                      type="password"
                      autoComplete="new-password"
                      value={registerPassword}
                      onChange={(value) => {
                        setRegisterPassword(value);
                        clearMessages();
                      }}
                    />
                    <WizardInput
                      placeholder="Confirm password"
                      type="password"
                      autoComplete="new-password"
                      value={confirmPassword}
                      onChange={(value) => {
                        setConfirmPassword(value);
                        clearMessages();
                      }}
                    />
                    <p className="-mb-0.5 mt-0.5 text-[12px] font-bold text-foreground/55">
                      Security questions
                    </p>
                    <WizardQuestionSelect
                      value={questionId1}
                      questions={securityQuestions.filter(
                        (q) => q.id !== questionId2,
                      )}
                      onChange={(id) => {
                        setQuestionId1(id);
                        clearMessages();
                      }}
                    />
                    <WizardInput
                      placeholder="Answer"
                      autoComplete="off"
                      value={answer1}
                      onChange={(value) => {
                        setAnswer1(value);
                        clearMessages();
                      }}
                    />
                    <WizardQuestionSelect
                      value={questionId2}
                      questions={securityQuestions.filter(
                        (q) => q.id !== questionId1 && q.id !== questionId3,
                      )}
                      onChange={(id) => {
                        setQuestionId2(id);
                        clearMessages();
                      }}
                    />
                    <WizardInput
                      placeholder="Answer"
                      autoComplete="off"
                      value={answer2}
                      onChange={(value) => {
                        setAnswer2(value);
                        clearMessages();
                      }}
                    />
                    <WizardQuestionSelect
                      value={questionId3}
                      questions={securityQuestions.filter(
                        (q) => q.id !== questionId1 && q.id !== questionId2,
                      )}
                      onChange={(id) => {
                        setQuestionId3(id);
                        clearMessages();
                      }}
                    />
                    <WizardInput
                      placeholder="Answer"
                      autoComplete="off"
                      value={answer3}
                      onChange={(value) => {
                        setAnswer3(value);
                        clearMessages();
                      }}
                    />
                  </>
                ) : (
                  <WizardInput
                    placeholder="Password"
                    type="password"
                    autoComplete="current-password"
                    value={password}
                    onChange={(value) => {
                      setPassword(value);
                      clearMessages();
                    }}
                  />
                )}

                {!isCreating && (
                  <Link
                    href="/forgot-password"
                    className="-mt-0.5 self-start text-[13px] font-bold text-primary transition active:opacity-60"
                  >
                    Forgot password?
                  </Link>
                )}

                {(errorMessage || infoMessage) && (
                  <p
                    className={cn(
                      "text-[14px] font-bold",
                      errorMessage ? "text-destructive" : "text-muted-foreground",
                    )}
                  >
                    {errorMessage || infoMessage}
                  </p>
                )}

                <WizardPrimaryButton
                  label={isCreating ? "Create account" : "Sign in"}
                  enabled={primaryEnabled}
                />

                <div className="pt-1.5">
                  <WizardTextButton
                    onClick={() =>
                      switchMode(isCreating ? "signin" : "create")
                    }
                  >
                    {isCreating ? "I already have an account" : "Create account"}
                  </WizardTextButton>
                </div>
              </form>
            )}
          </div>
        </div>
      </div>

      <PendingApprovalScreen
        open={pendingApprovalOpen}
        username={getPendingApproval()}
        isChecking={checkingApproval}
        onCheckStatus={handleCheckApprovalStatus}
        onUseDifferentAccount={() => {
          clearPendingApproval();
          setPendingPassword("");
          setPendingApprovalOpen(false);
          setMode("signin");
          setPassword("");
        }}
      />
    </main>
  );
}

function StepChip({
  title,
  Icon,
  tint,
  active = false,
  completed = false,
}: {
  title: string;
  Icon: React.ComponentType<{ className?: string; strokeWidth?: number }>;
  tint: string;
  active?: boolean;
  completed?: boolean;
}) {
  // On web, Mode and Server are always satisfied (the app is served from, and
  // talks to, its own backend), so completed chips are filled with their native
  // tint just like the active Login chip — matching the iOS/Android wizard.
  const filled = active || completed;
  return (
    <div
      className={cn(
        "flex flex-1 items-center justify-center gap-1.5 rounded-[18px] border px-2.5 py-2 text-[13px] font-bold transition",
        filled ? "border-transparent text-white" : "border-border bg-muted/50 text-foreground/65",
      )}
      style={
        filled
          ? { backgroundColor: tint, boxShadow: `0 5px 8px ${tint}2e` }
          : undefined
      }
    >
      {completed ? (
        <Check className="h-3.5 w-3.5" strokeWidth={3} />
      ) : (
        <Icon className="h-3.5 w-3.5" strokeWidth={2.5} />
      )}
      <span className="truncate">{title}</span>
    </div>
  );
}

function HeroTile({
  title,
  Icon,
  tint,
}: {
  title: string;
  Icon: React.ComponentType<{ className?: string; strokeWidth?: number }>;
  tint: string;
}) {
  return (
    <div
      className="relative flex h-[78px] items-center overflow-hidden rounded-[26px] px-3.5"
      style={{ backgroundColor: tint, boxShadow: `0 7px 9px ${tint}29` }}
    >
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(210px at 18% 18%, rgba(255,255,255,0.24), rgba(255,255,255,0.08) 38%, transparent 70%)",
        }}
      />
      <Icon
        className="pointer-events-none absolute right-2 top-2.5 h-[82px] w-[82px] text-white/20"
        strokeWidth={1.5}
      />
      <div className="relative flex min-w-0 items-center gap-3">
        <div className="flex h-[42px] w-[42px] shrink-0 items-center justify-center rounded-2xl bg-white/[0.18]">
          <Icon className="h-[23px] w-[23px] text-white" strokeWidth={2.25} />
        </div>
        <div className="min-w-0">
          <p className="truncate text-[21px] font-bold leading-tight text-white">
            {title}
          </p>
        </div>
      </div>
    </div>
  );
}

function WizardInput({
  placeholder,
  value,
  onChange,
  type = "text",
  autoComplete,
  autoCapitalize,
}: {
  placeholder: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  autoComplete?: string;
  autoCapitalize?: string;
}) {
  return (
    <input
      type={type}
      value={value}
      onChange={(event) => onChange(event.target.value)}
      placeholder={placeholder}
      autoComplete={autoComplete}
      autoCapitalize={autoCapitalize}
      aria-label={placeholder}
      className="h-[54px] w-full rounded-[22px] border border-border bg-muted/50 px-4 text-[15px] font-bold text-foreground shadow-sm outline-none transition placeholder:font-bold placeholder:text-foreground/40 focus:border-primary/80 focus:ring-1 focus:ring-primary/40"
    />
  );
}

function WizardQuestionSelect({
  value,
  questions,
  onChange,
}: {
  value: number | null;
  questions: SecurityQuestion[];
  onChange: (id: number) => void;
}) {
  return (
    <select
      value={value ?? ""}
      onChange={(event) => onChange(Number(event.target.value))}
      aria-label="Security question"
      className="h-[54px] w-full rounded-[22px] border border-border bg-muted/50 px-4 text-[15px] font-bold text-foreground shadow-sm outline-none transition focus:border-primary/80 focus:ring-1 focus:ring-primary/40"
    >
      {questions.map((question) => (
        <option key={question.id} value={question.id}>
          {question.text}
        </option>
      ))}
    </select>
  );
}

function WizardPrimaryButton({
  label,
  enabled,
}: {
  label: string;
  enabled: boolean;
}) {
  return (
    <button
      type="submit"
      disabled={!enabled}
      className={cn(
        "relative h-12 w-full overflow-hidden rounded-full text-[15px] font-bold transition active:scale-[0.985]",
        enabled
          ? "bg-primary text-primary-foreground shadow-lg shadow-primary/20"
          : "cursor-not-allowed bg-muted text-muted-foreground/60 opacity-70",
      )}
    >
      <span className="pointer-events-none absolute inset-0 bg-gradient-to-br from-white/15 to-transparent" />
      <span className="relative">{label}</span>
    </button>
  );
}

function WizardTextButton({
  children,
  onClick,
}: {
  children: React.ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="text-[15px] font-bold text-primary transition active:scale-[0.985] active:opacity-60"
    >
      {children}
    </button>
  );
}

function WizardLoadingPanel({
  Icon,
  title,
  subtitle,
}: {
  Icon: React.ComponentType<{ className?: string; strokeWidth?: number }>;
  title: string;
  subtitle: string;
}) {
  return (
    <div className="flex flex-col items-center gap-3 py-6 text-center">
      <Icon className="h-8 w-8 text-primary" strokeWidth={2} />
      <Loader2 className="h-5 w-5 animate-spin text-primary" />
      <p className="text-[22px] font-bold text-foreground">{title}</p>
      <p className="text-[14px] font-bold text-foreground/60">{subtitle}</p>
    </div>
  );
}

function MockTodayWorkspace() {
  return (
    <div className="flex h-full gap-3 rounded-2xl border border-border/70 bg-card/75 p-3 sm:gap-4 sm:p-4">
      <aside className="hidden w-64 shrink-0 flex-col rounded-2xl border border-border/70 bg-background/75 p-4 text-foreground lg:flex">
        <div className="mb-5 flex items-center gap-3">
          <div className="h-9 w-9 rounded-xl bg-accent/20" />
          <div>
            <p className="text-sm font-semibold">{"T'Day"}</p>
            <p className="text-xs text-muted-foreground">Planner Workspace</p>
          </div>
        </div>

        <nav className="space-y-2 text-sm">
          <div className="rounded-xl bg-accent/18 px-3 py-2 text-foreground">Today</div>
          <div className="rounded-xl px-3 py-2 text-muted-foreground">Completed</div>
          <div className="rounded-xl px-3 py-2 text-muted-foreground">Calendar</div>
          <div className="rounded-xl px-3 py-2 text-muted-foreground">Settings</div>
        </nav>

        <div className="mt-6 border-t border-border/70 pt-4">
          <p className="mb-2 text-xs uppercase tracking-wide text-muted-foreground">
            Lists
          </p>
          <div className="space-y-2 text-sm">
            <div className="flex items-center gap-2">
              <span className="h-2 w-2 rounded-full bg-emerald-300" />
              Product
            </div>
            <div className="flex items-center gap-2">
              <span className="h-2 w-2 rounded-full bg-violet-300" />
              Personal
            </div>
            <div className="flex items-center gap-2">
              <span className="h-2 w-2 rounded-full bg-amber-300" />
              Errands
            </div>
          </div>
        </div>
      </aside>

      <section className="flex min-w-0 flex-1 flex-col rounded-2xl border border-border/70 bg-background/75 p-4 sm:p-5">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border/70 pb-4">
          <div>
            <p className="text-xs uppercase tracking-wide text-muted-foreground">
              Today View
            </p>
            <h2 className="text-xl font-semibold text-foreground sm:text-2xl">
              Tuesday, Focus Session
            </h2>
          </div>
          <div className="rounded-full border border-border/70 bg-card/70 px-3 py-1 text-xs text-muted-foreground">
            4 Tasks Scheduled
          </div>
        </div>

        <div className="mt-4 grid gap-3">
          {mockTasks.map((task) => (
            <article
              key={task.title}
              className="rounded-xl border border-border/70 bg-card/75 p-3 sm:p-4"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h3 className="text-sm font-medium text-foreground sm:text-base">
                    {task.title}
                  </h3>
                  <p className="mt-1 text-xs text-muted-foreground sm:text-sm">
                    {task.meta}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-xs font-medium text-accent sm:text-sm">{task.time}</p>
                  <p className="mt-1 text-[11px] text-muted-foreground sm:text-xs">
                    {task.status}
                  </p>
                </div>
              </div>
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}

const mockTasks = [
  {
    title: "Prepare sprint review deck",
    time: "9:30 AM",
    meta: "Work · High",
    status: "Due today",
  },
  {
    title: "Gym and mobility session",
    time: "12:15 PM",
    meta: "Health · Medium",
    status: "1h duration",
  },
  {
    title: "Pick up groceries",
    time: "5:40 PM",
    meta: "Home · Low",
    status: "Reminder set",
  },
  {
    title: "Review project architecture",
    time: "8:00 PM",
    meta: "Deep Work · Medium",
    status: "Due tonight",
  },
];
