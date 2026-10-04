# ADR 010: Web crash reports are governed by the instance, not the browser

Status: accepted, 2026-10-04. Amends [ADR 009](009-opt-in-failure-only-crash-reporting.md) for the
web client and the backend. The mobile decision in 009 stands, with one change to *when* the
question is asked.

## Context

ADR 009 made crash reporting opt-in, per device and per browser, with the answer held locally. That
was right for the mobile apps, where the data belongs to the person, the choice has to be made
before auth, and there may be no server at all. On the web it produced a question per browser and
per user, and it left the operator — who already owns the backend's own error reports through a
server-wide switch — unable to answer once for the thing they run.

The web is not a device. It is the operator's deployment, visited by the operator's users, and the
SDK runs on a page the operator serves.

## Decision

- The web client's consent is the **instance-wide** answer an admin gives, stored on the server and
  shared with the backend's own reports. It covers every user, every browser and every session. No
  individual web user is asked, and there is no per-user control.
- An admin is asked while setting the server up, as a step in the existing onboarding wizard, and
  can change the answer afterwards in Settings → Privacy. `PATCH /api/admin/telemetry` is the write;
  `GET /api/admin/telemetry` stays the admin's view.
- The browser reads the answer from a public `GET /api/instance/telemetry`
  (`{ enabled, updatedAt }`) **before** it starts the SDK, and drops any event older than
  `updatedAt`. The endpoint carries no DSN state and no personal data, and fails closed.
- Mobile keeps ADR 009's per-device answer, but the wizard asks **on every sign-in** instead of once
  per install. An unanswered sign-in behaves like a denial, so nothing is sent until the person
  answers again.

## Rationale

- The person whose data it is still decides on their own device; the operator decides for the
  deployment they run. Both are opt-in, and neither is default-on.
- One answer for a browser fleet matches the backend, so web and server reports turn on and off
  together, which is how an operator thinks about their own instance.
- Reading the answer from the server keeps ADR 009's "no SDK before consent" property: the browser
  waits for the answer before initialising and sends nothing before it.
- Asking mobile again on each sign-in keeps the choice visible, which a set-once-per-install
  question quietly stops doing.

## Consequences

- The per-user web card, its `localStorage` keys and its hook are gone; a non-admin sees the state,
  not a control, and stale values from older builds are ignored.
- `InstanceTelemetryRoutes` is public by design and allow-listed in
  `tday-web/tests/guardrails/api-guidelines.test.ts`.
- A web user cannot opt out of their deployment's decision. The trade is stated in the
  `crash-reports` guide topic and in `docs/TELEMETRY.md`: the admin's answer is the instance's
  answer.
- ADR 009's "the consent card appears once, after the wizard" and "none of the three wizards has a
  final step" now describe mobile only; the web wizard has an admin-only step and no card.
