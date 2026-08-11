import React from "react";
import {
  ChevronLeft,
  KeyRound,
  Loader2,
  Lock,
  ShieldAlert,
  ShieldOff,
  Trash2,
  TriangleAlert,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useIsLocalMode } from "@/hooks/useAppMode";
import { setAppMode } from "@/lib/local/appMode";
import { LocalVaultError } from "@/lib/local/localCrypto";
import {
  clearWorkspace,
  createLocalVault,
  createOpenLocalWorkspace,
  getLocalVaultState,
  keepLegacyWorkspaceOpen,
  openLocalWorkspace,
  protectPlaintextWorkspace,
  unlockLocalVault,
  type LocalVaultState,
} from "@/lib/local/localDb";
import { MIN_PASSPHRASE_LENGTH, validatePassphrase } from "@/lib/local/passphrasePolicy";

/**
 * Stands in front of the app while Local Mode is active and the browser
 * workspace is still sealed.
 *
 * Local Mode keeps every task in this browser, so the passphrase asked for here
 * is the only thing between that data and anyone who gets the profile. The key
 * is derived on submit and held in memory for the session only — which is why a
 * lost passphrase is unrecoverable, and why this screen says so before the user
 * commits to one.
 *
 * The passphrase is the default, not a toll gate: a user who would rather not
 * carry one can decline it here and store the workspace in the clear. That is a
 * real trade, so it takes two deliberate taps and the warning says exactly what
 * is given up.
 *
 * Server mode never sees this gate: that workspace lives behind the account.
 */

/**
 * An open workspace needs no key, so it is adopted here rather than shown a
 * panel — `"open"` never reaches the dispatch below. Done in the state resolver
 * so `getLocalVaultState` stays free of side effects, and so the first paint is
 * already the app rather than a flash of gate.
 *
 * Only called while Local Mode is actually selected: "Leave local workspace"
 * preserves the stored document across a switch to Server Mode, and this must
 * not reach into storage and decrypt it into memory for a session that isn't
 * using it.
 */
function resolveVaultState(): LocalVaultState {
  const state = getLocalVaultState();
  if (state !== "open") return state;
  try {
    openLocalWorkspace();
    return "unlocked";
  } catch {
    // A document that claims to be open but won't read. Fall back to setup
    // rather than rendering the app over nothing.
    return "empty";
  }
}

export default function LocalWorkspaceGate({
  children,
}: {
  children: React.ReactNode;
}) {
  const isLocal = useIsLocalMode();
  const [vaultState, setVaultState] = React.useState<LocalVaultState>(() =>
    isLocal ? resolveVaultState() : "unlocked",
  );

  // Picking "This device" in the wizard flips the mode under us — re-inspect so
  // the setup step appears without a reload.
  React.useEffect(() => {
    if (!isLocal) return;
    setVaultState(resolveVaultState());
  }, [isLocal]);

  if (!isLocal || vaultState === "unlocked") return <>{children}</>;

  return (
    <GateShell>
      {vaultState === "unsupported" ? (
        <UnsupportedPanel onReady={() => setVaultState("unlocked")} />
      ) : vaultState === "locked" ? (
        <UnlockPanel
          onUnlocked={() => setVaultState("unlocked")}
          onForget={() => setVaultState(resolveVaultState())}
        />
      ) : (
        <SetupPanel
          migrating={vaultState === "legacy"}
          onReady={() => setVaultState("unlocked")}
        />
      )}
    </GateShell>
  );
}

/** The wizard's card chrome, so the gate reads as part of the same flow. */
function GateShell({ children }: { children: React.ReactNode }) {
  return (
    <main className="relative min-h-screen overflow-hidden bg-background text-foreground">
      <div className="absolute inset-0 bg-gradient-to-br from-background via-background to-muted/35" />
      <div className="relative z-10 flex min-h-screen items-center justify-center px-4 py-10">
        <div className="w-full max-w-[440px] overflow-hidden rounded-[34px] border border-border bg-background/95 p-[18px] shadow-2xl">
          {children}
        </div>
      </div>
    </main>
  );
}

/**
 * `onBack` is a real navigation exit — it leaves Local Mode entirely, back to
 * the wizard's mode picker — so it sits where a modal's back arrow normally
 * does, above the icon, not folded into the page's other choices below.
 */
function GateHeader({
  Icon,
  title,
  subtitle,
  onBack,
}: {
  Icon: React.ComponentType<{ className?: string; strokeWidth?: number }>;
  title: string;
  subtitle: string;
  onBack?: () => void;
}) {
  return (
    <div className="flex flex-col gap-2 pb-1">
      {onBack && (
        <button
          type="button"
          onClick={onBack}
          className="-ml-1.5 -mt-1 flex w-fit items-center gap-0.5 rounded-full py-1 pl-1.5 pr-2.5 text-[13px] font-bold text-foreground/45 transition hover:text-primary active:opacity-60"
        >
          <ChevronLeft className="h-4 w-4" strokeWidth={2.75} />
          Change setup
        </button>
      )}
      <div className="flex h-[42px] w-[42px] items-center justify-center rounded-2xl bg-primary/12">
        <Icon className="h-[22px] w-[22px] text-primary" strokeWidth={2.25} />
      </div>
      <p className="text-[21px] font-bold leading-tight text-foreground">{title}</p>
      <p className="text-[13.5px] font-bold leading-snug text-foreground/60">
        {subtitle}
      </p>
    </div>
  );
}

function GateInput({
  placeholder,
  value,
  onChange,
  autoFocus = false,
}: {
  placeholder: string;
  value: string;
  onChange: (value: string) => void;
  autoFocus?: boolean;
}) {
  return (
    <input
      type="password"
      value={value}
      onChange={(event) => onChange(event.target.value)}
      placeholder={placeholder}
      aria-label={placeholder}
      autoComplete="off"
      // Never offered to a password manager: the passphrase is the key, and the
      // whole point is that it lives nowhere but the user's head.
      autoFocus={autoFocus}
      className="h-[54px] w-full rounded-[22px] border border-border bg-muted/50 px-4 text-[15px] font-bold text-foreground shadow-sm outline-none transition placeholder:font-bold placeholder:text-foreground/40 focus:border-primary/80 focus:ring-1 focus:ring-primary/40"
    />
  );
}

type GateButtonTone = "primary" | "warning" | "destructive";

const GATE_BUTTON_TONE: Record<GateButtonTone, string> = {
  primary: "bg-primary text-primary-foreground shadow-lg shadow-primary/20",
  warning: "bg-amber-500 text-white shadow-lg shadow-amber-500/25 hover:bg-amber-500/90",
  destructive:
    "bg-destructive text-destructive-foreground shadow-lg shadow-destructive/20 hover:bg-destructive/90",
};

/**
 * The screen's one filled, unmissable action. `onClick` makes it a plain
 * button rather than a form submit — every non-form use (the skip and delete
 * confirm screens) is a single deliberate press, not something Enter should
 * trigger from an unrelated field.
 */
function GateButton({
  label,
  enabled,
  busy = false,
  tone = "primary",
  onClick,
}: {
  label: string;
  enabled: boolean;
  busy?: boolean;
  tone?: GateButtonTone;
  onClick?: () => void;
}) {
  return (
    <button
      type={onClick ? "button" : "submit"}
      onClick={onClick}
      disabled={!enabled}
      className={cn(
        "flex h-12 w-full items-center justify-center gap-2 rounded-full text-[15px] font-bold transition active:scale-[0.985]",
        enabled
          ? GATE_BUTTON_TONE[tone]
          : "cursor-not-allowed bg-muted text-muted-foreground/60 opacity-70",
      )}
    >
      {busy && <Loader2 className="h-4 w-4 animate-spin" />}
      {label}
    </button>
  );
}

/**
 * An outlined counterpart to [GateButton] — same size and shape, so it pairs
 * visually with the primary action, but unfilled: this is "consider this
 * option," not yet the commitment. That happens a step later, once the
 * consequence has been spelled out and the option becomes a filled [GateButton].
 */
function GateSecondaryButton({
  label,
  onClick,
  tone = "neutral",
}: {
  label: string;
  onClick: () => void;
  tone?: "neutral" | "warning";
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex h-12 w-full items-center justify-center rounded-full border text-[15px] font-bold transition active:scale-[0.985]",
        tone === "warning"
          ? "border-amber-500/40 text-amber-700 hover:bg-amber-500/10 dark:border-amber-400/35 dark:text-amber-400"
          : "border-border text-foreground/70 hover:bg-muted/60",
      )}
    >
      {label}
    </button>
  );
}

/**
 * What kind of return path a footer link represents, so hovering hints at the
 * consequence before the tap: a plain reconsideration (neutral) or a
 * destructive recovery action (red).
 */
type GateFooterLinkTone = "neutral" | "destructive";

const GATE_FOOTER_LINK_TONE: Record<GateFooterLinkTone, string> = {
  neutral: "text-foreground/50 hover:text-foreground/80",
  destructive: "text-destructive/75 hover:text-destructive",
};

/**
 * A quiet, minor return path — canceling a confirm screen back to the one
 * before it, or a rare recovery action like "I forgot my passphrase." Idle,
 * it reads as a footnote, not a call to action: the screen's real choices are
 * the filled [GateButton] and the outlined [GateSecondaryButton] above it.
 */
function GateFooterLink({
  children,
  onClick,
  tone = "neutral",
}: {
  children: React.ReactNode;
  onClick: () => void;
  tone?: GateFooterLinkTone;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "text-[12.5px] font-bold underline decoration-dotted decoration-1 underline-offset-[3px] transition active:opacity-60",
        GATE_FOOTER_LINK_TONE[tone],
      )}
    >
      {children}
    </button>
  );
}

function GateError({ message }: { message: string }) {
  if (!message) return null;
  return <p className="text-[14px] font-bold text-destructive">{message}</p>;
}

/** The red block the setup screens use to say what is at stake. */
function GateWarning({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex gap-2.5 rounded-[22px] border border-destructive/35 bg-destructive/10 p-3">
      <TriangleAlert
        className="mt-0.5 h-[18px] w-[18px] shrink-0 text-destructive"
        strokeWidth={2.5}
      />
      <p className="text-[13px] font-bold leading-snug text-foreground/80">
        {children}
      </p>
    </div>
  );
}

/**
 * The second step of declining the passphrase — the first is a
 * [GateSecondaryButton] on the previous screen that just brings this into
 * view. Its own screen, not appended below the form that led here, so the
 * choice is read on its own rather than tacked under fields that no longer
 * apply. Storing a workspace in the clear is not undone by a reload, so
 * accepting it is deliberately a separate, later press than the one that
 * opened this.
 */
function SkipEncryptionConfirm({
  reason,
  onSkip,
  onCancel,
  cancelLabel = "Choose a passphrase instead",
}: {
  /** Why this workspace would be unencrypted, in the user's terms. */
  reason: React.ReactNode;
  onSkip: () => void;
  onCancel: () => void;
  cancelLabel?: string;
}) {
  const [error, setError] = React.useState("");

  return (
    <div className="flex flex-col gap-[11px]">
      <GateHeader
        Icon={ShieldOff}
        title="Store these tasks unencrypted?"
        subtitle="You can turn encryption on later from Settings — there's no route back the other way."
        onBack={changeSetup}
      />
      <GateWarning>{reason}</GateWarning>
      <GateError message={error} />
      <GateButton
        label="Store unencrypted"
        enabled
        tone="warning"
        onClick={() => {
          setError("");
          try {
            onSkip();
          } catch (caught) {
            console.error(caught);
            setError(messageFor(caught, "Could not open a workspace in this browser."));
          }
        }}
      />
      <div className="flex justify-center pt-1">
        <GateFooterLink onClick={onCancel}>{cancelLabel}</GateFooterLink>
      </div>
    </div>
  );
}

/** What an unencrypted workspace costs, said plainly. */
const PLAINTEXT_RISK =
  "Anyone who can use this browser profile — or read this computer's disk, or restore a backup of it — will be able to read every task, list and note you keep here. Nothing is sent to a server either way; encryption is what stops someone with the device from reading it.";

/** Leaves Local Mode entirely, dropping the visitor back on the wizard. */
function changeSetup() {
  // A hard reload, not a client-side state flip: the router is already
  // sitting on a protected app route by the time any panel here is visible —
  // `chooseLocalMode` in the wizard navigates to /app before this gate ever
  // shows a setup screen. Flipping appMode alone would leave every query
  // mounted under Local Mode (Sidebar meta, dashboard cards) to refetch
  // against the real backend, fail with 401s, and race AuthProvider's
  // session-expiry handler into firing "Your session expired" once per
  // failing query for a session that was never real. `AuthProvider.logout()`
  // already solves exactly this for "Leave local workspace" with the same
  // reload-to-origin; this is that same fix, reached one screen earlier.
  setAppMode(null);
  window.location.replace(window.location.origin);
}

function messageFor(error: unknown, fallback: string): string {
  if (error instanceof LocalVaultError) return error.message;
  return fallback;
}

/**
 * First run, and the migration of a workspace written before encryption existed.
 * Both end the same way — a passphrase is chosen and the document is sealed —
 * so they share a form and differ only in wording.
 */
function SetupPanel({
  migrating,
  onReady,
}: {
  migrating: boolean;
  onReady: () => void;
}) {
  const [passphrase, setPassphrase] = React.useState("");
  const [confirmation, setConfirmation] = React.useState("");
  const [acknowledged, setAcknowledged] = React.useState(false);
  const [busy, setBusy] = React.useState(false);
  const [error, setError] = React.useState("");
  const [skipping, setSkipping] = React.useState(false);

  const ready =
    passphrase.length >= MIN_PASSPHRASE_LENGTH &&
    confirmation.length > 0 &&
    acknowledged &&
    !busy;

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    const problem = validatePassphrase(passphrase, confirmation);
    if (problem === "too-short") {
      setError(`Use at least ${MIN_PASSPHRASE_LENGTH} characters.`);
      return;
    }
    if (problem === "mismatch") {
      setError("The two passphrases don't match.");
      return;
    }
    setBusy(true);
    try {
      if (migrating) {
        await protectPlaintextWorkspace(passphrase);
      } else {
        await createLocalVault(passphrase);
      }
      // Nothing keeps a copy: the fields go before the app opens.
      setPassphrase("");
      setConfirmation("");
      onReady();
    } catch (caught) {
      console.error(caught);
      setError(messageFor(caught, "Could not protect this workspace."));
      setBusy(false);
    }
  };

  if (skipping) {
    return (
      <SkipEncryptionConfirm
        reason={
          migrating ? (
            <>
              These tasks are already stored unencrypted in this browser.
              Skipping keeps them that way. {PLAINTEXT_RISK}
            </>
          ) : (
            PLAINTEXT_RISK
          )
        }
        onSkip={() => {
          // The legacy rows are wrapped rather than left bare, so declining is
          // recorded and the migration prompt stops asking on every load.
          if (migrating) keepLegacyWorkspaceOpen();
          else createOpenLocalWorkspace();
          onReady();
        }}
        onCancel={() => setSkipping(false)}
      />
    );
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-[11px]">
      <GateHeader
        Icon={KeyRound}
        title={migrating ? "Protect your tasks" : "Choose a passphrase"}
        subtitle={
          migrating
            ? "This browser already holds tasks in the clear. Pick a passphrase and they'll be encrypted in place — nothing is lost."
            : "Everything you write in this browser is encrypted with it. Nothing is sent to a server."
        }
        onBack={changeSetup}
      />

      <GateWarning>
        There is no recovery. No reset link, no backup key, no support account —
        if you forget this passphrase, these tasks are gone for good. Write it
        down somewhere safe.
      </GateWarning>

      <GateInput
        placeholder="Passphrase"
        value={passphrase}
        onChange={(value) => {
          setPassphrase(value);
          setError("");
        }}
        autoFocus
      />
      <GateInput
        placeholder="Repeat passphrase"
        value={confirmation}
        onChange={(value) => {
          setConfirmation(value);
          setError("");
        }}
      />

      <label className="flex cursor-pointer items-start gap-2.5 px-1 py-0.5">
        <input
          type="checkbox"
          checked={acknowledged}
          onChange={(event) => setAcknowledged(event.target.checked)}
          className="mt-[3px] h-4 w-4 shrink-0 accent-[hsl(var(--primary))]"
        />
        <span className="text-[13px] font-bold leading-snug text-foreground/70">
          I understand that losing this passphrase means losing this workspace.
        </span>
      </label>

      <GateError message={error} />
      <GateButton
        label={migrating ? "Encrypt my tasks" : "Create workspace"}
        enabled={ready}
        busy={busy}
      />
      <GateSecondaryButton
        label="Skip encryption on this device"
        tone="warning"
        onClick={() => setSkipping(true)}
      />
    </form>
  );
}

/** An encrypted workspace is already here; only the passphrase opens it. */
function UnlockPanel({
  onUnlocked,
  onForget,
}: {
  onUnlocked: () => void;
  onForget: () => void;
}) {
  const [passphrase, setPassphrase] = React.useState("");
  const [busy, setBusy] = React.useState(false);
  const [error, setError] = React.useState("");
  const [confirmingWipe, setConfirmingWipe] = React.useState(false);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!passphrase || busy) return;
    setError("");
    setBusy(true);
    try {
      await unlockLocalVault(passphrase);
      setPassphrase("");
      onUnlocked();
    } catch (caught) {
      setError(messageFor(caught, "Could not open this workspace."));
      setBusy(false);
    }
  };

  if (confirmingWipe) {
    return (
      <div className="flex flex-col gap-[11px]">
        <GateHeader
          Icon={Trash2}
          title="Delete this workspace?"
          subtitle="Deleting is the only way past a forgotten passphrase, and it erases every task in this browser permanently."
          onBack={changeSetup}
        />
        <GateButton
          label="Yes, delete this workspace"
          enabled
          tone="destructive"
          onClick={() => {
            clearWorkspace();
            setConfirmingWipe(false);
            setPassphrase("");
            setError("");
            onForget();
          }}
        />
        <div className="flex justify-center pt-1">
          <GateFooterLink onClick={() => setConfirmingWipe(false)}>Keep it</GateFooterLink>
        </div>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-[11px]">
      <GateHeader
        Icon={Lock}
        title="Unlock this device"
        subtitle="Your tasks are encrypted in this browser. Enter the passphrase you chose to open them."
        onBack={changeSetup}
      />

      <GateInput
        placeholder="Passphrase"
        value={passphrase}
        onChange={(value) => {
          setPassphrase(value);
          setError("");
        }}
        autoFocus
      />

      <GateError message={error} />
      <GateButton label="Unlock" enabled={passphrase.length > 0 && !busy} busy={busy} />

      <div className="flex justify-center pt-1">
        <GateFooterLink tone="destructive" onClick={() => setConfirmingWipe(true)}>
          I forgot my passphrase
        </GateFooterLink>
      </div>
    </form>
  );
}

/**
 * `crypto.subtle` is only handed to secure contexts, so a T'Day served over
 * plain http on a LAN address can't encrypt anything.
 *
 * Encryption is the one thing this origin cannot offer, so the honest answer is
 * to say so and let the user decide — the same unencrypted workspace they could
 * choose on https is still available here, and refusing it would only mean an
 * https user can have a device workspace while a LAN user can't.
 */
function UnsupportedPanel({ onReady }: { onReady: () => void }) {
  const [skipping, setSkipping] = React.useState(false);

  if (skipping) {
    return (
      <SkipEncryptionConfirm
        reason={
          <>
            This page is served over plain http, so the browser will not give
            T&apos;Day the encryption API. Your tasks would be stored in this
            browser in the clear. {PLAINTEXT_RISK}
          </>
        }
        onSkip={() => {
          createOpenLocalWorkspace();
          onReady();
        }}
        onCancel={() => setSkipping(false)}
        cancelLabel="Go back"
      />
    );
  }

  return (
    <div className="flex flex-col gap-[11px]">
      <GateHeader
        Icon={ShieldAlert}
        title="Encryption unavailable here"
        subtitle="This page is served over an insecure origin, so the browser withholds the encryption API T'Day needs to protect a local workspace."
        onBack={changeSetup}
      />
      <p className="px-1 text-[13px] font-bold leading-snug text-foreground/70">
        Open T'Day over https://, or on http://localhost, and this device
        workspace can be encrypted as normal. Any tasks already stored in this
        browser are untouched.
      </p>
      <GateSecondaryButton
        label="Continue without encryption"
        tone="warning"
        onClick={() => setSkipping(true)}
      />
    </div>
  );
}
