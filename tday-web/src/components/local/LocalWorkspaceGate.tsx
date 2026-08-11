import React from "react";
import { KeyRound, Loader2, Lock, ShieldAlert, TriangleAlert } from "lucide-react";
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

function GateHeader({
  Icon,
  title,
  subtitle,
}: {
  Icon: React.ComponentType<{ className?: string; strokeWidth?: number }>;
  title: string;
  subtitle: string;
}) {
  return (
    <div className="flex flex-col gap-2 pb-1">
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

function GateButton({
  label,
  enabled,
  busy = false,
}: {
  label: string;
  enabled: boolean;
  busy?: boolean;
}) {
  return (
    <button
      type="submit"
      disabled={!enabled}
      className={cn(
        "flex h-12 w-full items-center justify-center gap-2 rounded-full text-[15px] font-bold transition active:scale-[0.985]",
        enabled
          ? "bg-primary text-primary-foreground shadow-lg shadow-primary/20"
          : "cursor-not-allowed bg-muted text-muted-foreground/60 opacity-70",
      )}
    >
      {busy && <Loader2 className="h-4 w-4 animate-spin" />}
      {label}
    </button>
  );
}

function GateTextButton({
  children,
  onClick,
  destructive = false,
}: {
  children: React.ReactNode;
  onClick: () => void;
  destructive?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "text-[14px] font-bold transition active:opacity-60",
        destructive ? "text-destructive" : "text-primary",
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
 * Declining the passphrase, in two deliberate taps.
 *
 * The same two-step shape `UnlockPanel` uses for "I forgot my passphrase": the
 * trigger only reveals the consequence, and a second, separate press accepts it.
 * Storing a workspace in the clear is not undone by a reload, so it should not
 * be reachable by one stray tap.
 */
function SkipEncryptionBlock({
  reason,
  onSkip,
}: {
  /** Why this workspace would be unencrypted, in the user's terms. */
  reason: React.ReactNode;
  onSkip: () => void;
}) {
  const [confirming, setConfirming] = React.useState(false);
  const [error, setError] = React.useState("");

  if (!confirming) {
    return (
      <GateTextButton onClick={() => setConfirming(true)}>
        Skip encryption on this device
      </GateTextButton>
    );
  }

  return (
    <div className="flex w-full flex-col items-center gap-2">
      <p className="px-1 text-center text-[14px] font-bold text-foreground">
        Store these tasks unencrypted?
      </p>
      <GateWarning>{reason}</GateWarning>
      <p className="px-2 text-center text-[13px] font-bold leading-snug text-foreground/60">
        In exchange there is no passphrase to remember and nothing to lose. You
        can turn encryption on later from Settings, and your tasks will be
        encrypted in place.
      </p>
      <GateError message={error} />
      <GateTextButton
        destructive
        onClick={() => {
          setError("");
          try {
            onSkip();
          } catch (caught) {
            console.error(caught);
            setError(messageFor(caught, "Could not open a workspace in this browser."));
          }
        }}
      >
        Store unencrypted
      </GateTextButton>
      <GateTextButton onClick={() => setConfirming(false)}>
        Choose a passphrase instead
      </GateTextButton>
    </div>
  );
}

/** What an unencrypted workspace costs, said plainly. */
const PLAINTEXT_RISK =
  "Anyone who can use this browser profile — or read this computer's disk, or restore a backup of it — will be able to read every task, list and note you keep here. Nothing is sent to a server either way; encryption is what stops someone with the device from reading it.";

/** Leaves Local Mode entirely, dropping the visitor back on the wizard. */
function changeSetup() {
  setAppMode(null);
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
      <div className="flex flex-col items-center gap-2 pt-1">
        <GateTextButton onClick={changeSetup}>
          Use a self-hosted account instead
        </GateTextButton>
        <SkipEncryptionBlock
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
        />
      </div>
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

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-[11px]">
      <GateHeader
        Icon={Lock}
        title="Unlock this device"
        subtitle="Your tasks are encrypted in this browser. Enter the passphrase you chose to open them."
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

      <div className="flex flex-col items-center gap-2 pt-1">
        <GateTextButton onClick={changeSetup}>
          Use a self-hosted account instead
        </GateTextButton>
        {confirmingWipe ? (
          <>
            <p className="px-2 text-center text-[13px] font-bold leading-snug text-foreground/70">
              Deleting is the only way past a forgotten passphrase, and it erases
              every task in this browser permanently.
            </p>
            <GateTextButton
              destructive
              onClick={() => {
                clearWorkspace();
                setConfirmingWipe(false);
                setPassphrase("");
                setError("");
                onForget();
              }}
            >
              Yes, delete this workspace
            </GateTextButton>
            <GateTextButton onClick={() => setConfirmingWipe(false)}>
              Keep it
            </GateTextButton>
          </>
        ) : (
          <GateTextButton destructive onClick={() => setConfirmingWipe(true)}>
            I forgot my passphrase
          </GateTextButton>
        )}
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
  return (
    <div className="flex flex-col gap-[11px]">
      <GateHeader
        Icon={ShieldAlert}
        title="Encryption unavailable here"
        subtitle="This page is served over an insecure origin, so the browser withholds the encryption API T'Day needs to protect a local workspace."
      />
      <p className="px-1 text-[13px] font-bold leading-snug text-foreground/70">
        Open T'Day over https://, or on http://localhost, and this device
        workspace can be encrypted as normal. Any tasks already stored in this
        browser are untouched.
      </p>
      <p className="px-1 text-[13px] font-bold leading-snug text-foreground/70">
        You can still use this device workspace without encryption.
      </p>
      <div className="flex flex-col items-center gap-2 pt-1">
        <GateTextButton onClick={changeSetup}>
          Use a self-hosted account instead
        </GateTextButton>
        <SkipEncryptionBlock
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
        />
      </div>
    </div>
  );
}
