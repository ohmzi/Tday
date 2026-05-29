# ADR 005: Offline-First Mobile Clients with Background Sync

**Status:** Accepted  
**Date:** 2025, updated 2026-05-29

## Context

The mobile clients need to work reliably on unstable networks and provide instant UI feedback. Android shipped first, then iOS adopted the same product behavior with platform-native storage. Options:

1. **Online-only**: All data from network requests. Simple but fragile.
2. **Cache-then-network**: Show cached data, refresh from network. Good UX but no offline writes.
3. **Offline-first with sync queue**: Full local cache + pending mutation queue. Best UX but most complex.

## Decision

- Implement **offline-first** architecture with local platform databases and a pending mutation queue.
- Android stores cache state in Room. A one-time migration reads the legacy encrypted JSON cache and moves it into Room.
- iOS stores cache state in SwiftData.
- Both platforms mirror the same logical `OfflineSyncState`: todos, floaters, lists, floater lists, completed records, pending mutations, sync timestamps, and app settings metadata.
- Mutations made offline are queued as `PendingMutationRecord` entries and synced when connectivity returns.
- `OfflineCacheManager` exposes a `cacheDataVersion` `StateFlow` that increments on cache changes — ViewModels observe it to reload.
- iOS posts `.offlineCacheDidChange` for the same purpose.
- `AppViewModel` runs periodic/foreground sync in Server Mode.
- Local Mode is a first-class mobile workspace and clears/ignores pending mutations because there is no remote target.

## Rationale

- Mobile networks are unreliable. Users expect their task app to work everywhere.
- Optimistic UI updates (write locally, sync later) provide instant feedback.
- The mutation queue preserves user intent even through app restarts.
- Platform storage keeps task data local on the device and avoids rewriting entire JSON blobs as the dataset grows.

## Consequences

- **Positive**: Instant UI, works offline, data survives app restarts and network outages.
- **Negative**: Conflict resolution is limited — last-write-wins for most fields. Sync and mutation replay add coordination complexity across repositories and `SyncManager`.
- **Future**: Define explicit import/export or migration behavior before moving Local Mode data into a server workspace. Keep mobile ViewModels focused on presentation and prefer repositories/services or focused use cases over broad app-layer abstractions.
