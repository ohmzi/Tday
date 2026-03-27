# ADR 006: RFC 5545 Recurrence Rules for Repeating Tasks

**Status:** Accepted
**Date:** 2024

## Context

Users need repeating tasks (daily, weekly, monthly, custom patterns). Options:

1. **Simple enum** (`daily`, `weekly`, `monthly`) — easy but inflexible.
2. **RFC 5545 / iCal RRULE** — standard recurrence rule format used by calendar applications worldwide.
3. **Cron expressions** — powerful but designed for job scheduling, not human-readable calendar events.

## Decision

- Store recurrence rules as **RFC 5545 RRULE strings** on the `Todo` model.
- Support `dtstart`, `due`, `exdates` (exclusion dates), and `durationMinutes` alongside the RRULE.
- Expand recurrence instances into `TodoInstance` records for per-occurrence overrides and completion tracking.
- Use **lib-recur** (Kotlin/JVM) on the backend for rule expansion, and **rrule** (JavaScript) on the web frontend.

## Rationale

- RFC 5545 is the industry standard — compatible with Google Calendar, Apple Calendar, Outlook, and iCal.
- RRULE strings encode complex patterns (e.g., "every 2nd Tuesday of the month") in a compact, parseable format.
- Materializing instances in `TodoInstance` allows per-occurrence edits (e.g., reschedule just one occurrence) without modifying the series.
- Exclusion dates (`exdates`) handle "skip this occurrence" cleanly.

## Consequences

- **Positive**: Flexible recurrence, future calendar export/import compatibility, per-instance overrides.
- **Negative**: RRULE expansion logic is complex — requires thorough testing (covered in `tday-web/tests/recurrence/` and backend tests). Instance materialization adds database writes.
- **Trade-off**: The legacy `RepeatInterval` enum still exists in the database schema for backward compatibility but new features use RRULE.
