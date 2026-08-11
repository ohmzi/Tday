# ADR 008: Local Mode encryption becomes optional, not mandatory

**Status:** Accepted
**Date:** 2026-08-11

## Context

Commit `59556b3d` ("Web: encrypt Local Mode, stop a plaintext title leak, add the admin security panel") made passphrase encryption compulsory for the web Local Mode workspace: choosing "This device" in the onboarding wizard leads to a gate (`LocalWorkspaceGate.tsx`) that will not open the app until a 10-character passphrase is set and confirmed. `docs/security/SECURITY_CONTROLS.md` documented this outright as "mandatory once chosen: there is no plaintext Local Mode any more."

That is a stronger default than the rest of the product commits to. Local Mode is explicitly a no-login, no-account, scratch workspace — the wizard's own copy says "no account, no sync." A passphrase with no recovery path (by design: nothing derived from it is ever stored) is a real cost for a workspace some users want casually, on a personal device they already trust. It also had an unintended second effect: `crypto.subtle` is only available in secure contexts, so a self-hosted deployment served over plain `http://` on a LAN address could not use Local Mode at all — the gate's `unsupported` state refused to open rather than degrade to plaintext, which was the right call when the only two options were "plaintext silently" or "nothing," but stopped being the only two options once an explicit unencrypted mode existed elsewhere.

The goal: let a user decline the passphrase and use an unencrypted local workspace, with the risk stated plainly before they can take it, while keeping the passphrase as the default and not weakening anything about the existing encrypted path.

## Decision

- Add a third on-disk shape for the Local Mode workspace document at `tday.local.workspace.v1`: `{protection: "none", version, workspace}` — alongside the existing encrypted envelope `{version, salt, iv, ciphertext}` and the pre-encryption legacy document (a bare workspace object, from a build before `59556b3d`).
- The choice lives in the document itself, not in a side flag. A second `localStorage` key or a flag next to `tday.appMode` was rejected: two sources of truth for the same fact can desync, which is exactly the class of failure the migration write was already hardened against (`openWith` writes loudly and rethrows on failure for this reason).
- `LocalVaultState` gains an `"open"` member — storage holds a readable-with-no-key document, not yet adopted into this session. It is not collapsed into `"unlocked"` at the probe: `inspectStorage()` has no side effects, and returning `"unlocked"` before anything is cached would be a lie the next `loadWorkspace()` call would contradict. Adoption happens one layer up, in the gate, which is why an unencrypted workspace opens on first paint with no visible transition.
- `crypto.subtle` availability is checked *after* probing for an open document, not before. An origin without secure-context crypto can still read a workspace that needs no key.
- The passphrase screen — first setup, and the pre-encryption migration prompt — gains a two-step "Skip encryption on this device" → "Store unencrypted" confirmation, naming the risk before it can be taken. The same confirmation is offered on the insecure-origin screen, which now opens an unencrypted workspace instead of refusing outright.
- Settings gains a one-way "Encrypt this workspace" upgrade for a workspace currently stored unencrypted, reusing the existing seal-in-place machinery (`openWith`) that already migrates a legacy plaintext workspace.
- No downgrade path. There is no button that takes an encrypted workspace back to unencrypted; that would turn "the vault protects the disk" into "the vault protects the disk until someone at an unlocked session clicks a button." The existing export → delete local data → set up again → import cycle is the honest route, same as changing a passphrase.
- Gate copy for the new confirmation stays hardcoded English, matching the rest of `LocalWorkspaceGate.tsx`, which has no i18n today. Settings copy is fully translated across all ten locales, since that screen is already localized and the repo's i18n-parity guardrail enforces identical key sets.

## Rationale

- Local Mode's own pitch is casualness — no account, no server, nothing to configure. A mandatory, unrecoverable passphrase cuts against that for users who just want a scratch workspace on a device they trust.
- The pre-encryption legacy document and a workspace stored unencrypted by choice are byte-for-byte the same shape. Without an explicit wrapper, every load of an intentionally unencrypted workspace would misclassify as "legacy" and re-show the migration prompt forever — this was the one real technical hazard the feature had to solve, and the wrapper is the whole fix.
- Refusing Local Mode entirely on an insecure origin was correct while the alternative was "silently store in the clear." Once storing in the clear became an explicit, warned, two-tap choice everywhere else in Local Mode, refusing it here only meant an `https` user could have a device workspace while a LAN user serving the same app over plain `http` could not — a distinction with no relationship to what either user is actually choosing to store.
- Keeping the choice in the stored document rather than a side flag means there is exactly one thing to read to know how a workspace is protected, and no way for that answer to disagree with what is actually on disk.
- No downgrade path keeps the encrypted state meaningful: an encrypted workspace is encrypted until the user deliberately re-does setup, not until anyone with an open session decides otherwise.

## Consequences

- `localDb.ts`'s module state and public API grow to track *how* a workspace is protected (`LocalProtection = "passphrase" | "none"`), not just whether it is unlocked. `saveWorkspace` keeps one write queue and one `writeGeneration` cancellation guard for both forms — only the branch inside the queued write differs (seal vs. wrap).
- `docs/security/SECURITY_CONTROLS.md`'s claim that "there is no plaintext Local Mode any more" is no longer true and has been corrected, along with the insecure-origin section, which now describes offering the unencrypted workspace instead of refusing.
- Every future change to the passphrase-encrypted path must also reason about the unencrypted path sharing the same write queue and generation guard — they are not two parallel implementations, they are one write path with a branch.
- A user who skips encryption is trusting the device and the browser profile entirely; nothing in this feature reduces that risk, it only makes the trade explicit and reversible in one direction (to encrypted, never back).
