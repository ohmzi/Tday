# ADR 005: Offline-First Android Client with Background Sync

**Status:** Accepted  
**Date:** 2025

## Context

The Android client needs to work reliably on unstable networks and provide instant UI feedback. Options:

1. **Online-only**: All data from network requests. Simple but fragile.
2. **Cache-then-network**: Show cached data, refresh from network. Good UX but no offline writes.
3. **Offline-first with sync queue**: Full local cache + pending mutation queue. Best UX but most complex.

## Decision

- Implement **offline-first** architecture with a local JSON cache and a pending mutation queue.
- Cache state is serialized to `EncryptedSharedPreferences` as `OfflineSyncState`.
- Mutations made offline are queued as `PendingMutationRecord` entries and synced when connectivity returns.
- `TdayRepository` exposes a `cacheDataVersion` `StateFlow` that increments on cache changes — ViewModels observe it to reload.
- `AppViewModel` runs a periodic background sync loop.

## Rationale

- Mobile networks are unreliable. Users expect their task app to work everywhere.
- Optimistic UI updates (write locally, sync later) provide instant feedback.
- The mutation queue preserves user intent even through app restarts.
- Encrypted storage protects task data at rest on the device.

## Consequences

- **Positive**: Instant UI, works offline, data survives app restarts and network outages.
- **Negative**: Conflict resolution is limited — last-write-wins for most fields. Complex sync logic lives in `TdayRepository`, making it a large class.
- **Future**: Consider Room database if cache complexity grows beyond JSON serialization capabilities. Consider extracting domain-specific repositories from the single `TdayRepository`.
