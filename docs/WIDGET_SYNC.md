# Widget Synchronization

How the **Today** (Scheduled) and **Floater** (Anytime) home-screen widgets stay in sync with the
app on Android (Glance + WorkManager) and iOS (WidgetKit + App Groups).

The guiding principle is **app-driven, immediate refresh**: the widget repaints synchronously the
moment the app's offline cache changes, from the single write chokepoint every mutation already
goes through — rather than relying on the platform's slow, system-scheduled update interval.
Background workers (WorkManager on Android, `BGAppRefreshTask` on iOS) exist only as a freshness
fallback for when the app process isn't running to make that write.

## What this fixes

| Symptom                                            | Root cause                                                                                     | Fix                                                                                         |
|----------------------------------------------------|------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| Widget stays stale for 30+ min after adding a task | Relying on `updatePeriodMillis` (Android minimum 30 min) or WidgetKit's passive refresh        | The offline-cache write path itself repaints the widget, synchronously, on every mutation   |
| Widget doesn't update when app is closed           | No lifecycle hook on app background                                                            | Not needed: the repaint already happened at write time, not on close. Android's `MainActivity.onStart()` still re-requests a refresh on the *next* foreground return as belt-and-braces; iOS re-arms its background fallback task |
| Pressing + and adding a task doesn't update widget | No call to update the widget from the save path                                                | `OfflineCacheManager` is the single chokepoint every write goes through, on both platforms, and it writes the widget snapshot + requests a repaint itself |
| Widget updates are irregular / unreliable          | No background worker as fallback                                                               | `WidgetSyncWorker` (Android, WorkManager `PeriodicWorkRequest`) / `BGAppRefreshTask` (iOS) both run on a **30-minute** earliest-begin fallback |

## Files — where they go

### Android (`android-compose/`)

```
app/src/main/java/com/ohmz/tday/compose/feature/widget/
├── TodayTasksWidget.kt                 ← Glance widget (Today): renders WidgetSnapshotStore, no Hilt on the render path
├── TodayTasksWidgetReceiver.kt         ← Small/default/Large AppWidgetReceivers + WidgetFastPaint cold-boot hook
├── WidgetRefresher.kt                  ← the ONE repaint trigger for all three widgets (Hilt Singleton: one mutex + one conflated channel)
├── WidgetInstanceKind.kt               ← the ONE per-instance kind/feed resolution (provider binding -> TODAY/FLOATER/LIST) + the render plan
├── TodayTasksWidgetPreviewPublisher.kt ← Android 15+ widget-picker preview (setWidgetPreview)
├── FloaterTasksWidget.kt               ← mirror of TodayTasksWidget for floaters
├── FloaterTasksWidgetReceiver.kt       ← mirror receivers
├── CompleteTaskAction.kt               ← inline-completion ActionCallbacks (widgets v2)
├── WidgetCompleteTaskSubmitter.kt      ← resolves the tapped row, completes it, pushes an expedited sync
├── WidgetCreateTaskActivity.kt         ← translucent Activity behind the widget's + button (a bottom sheet, not MainActivity)
├── WidgetCreateRoute.kt                ← the tday://todos/create deep link (carries the tapped appWidgetId) + WidgetCreateTarget
├── WidgetCreateTaskSubmitter.kt        ← creates the task, then repaints every instance with the tapped one first
├── ListTasksWidget.kt                  ← per-list widget (widgets v3): one class, per-instance selection
├── ListTasksWidgetReceiver.kt          ← its Small/default/Large receivers
├── WidgetListConfigurationActivity.kt  ← the ACTION_APPWIDGET_CONFIGURE list picker
├── WidgetListSelectionStore.kt         ← per-appWidgetId list selection (plain SharedPreferences)
├── WidgetEntryPoint.kt                 ← Hilt @EntryPoint exposing only the completion/refresh singletons to the render path
├── WidgetFastPaint.kt                  ← paints from the cold-boot broadcast before Glance's managed session starts (~2.4-3.0s saved)
├── WidgetHydrateWorker.kt              ← the only widget-flow class allowed to open the encrypted cache; seeds a missing snapshot
├── WidgetSyncWorker.kt                 ← WorkManager periodic (30 min, network sync) + expedited one-shot
├── WidgetLog.kt                        ← shared "TdayWidget" Logcat tag
├── TaskWidgetDesign.kt                 ← shared Glance UI (rows, states) both widgets render through
└── snapshot/
    ├── WidgetSnapshot.kt               ← the render-payload DTOs
    ├── WidgetSnapshotStore.kt          ← AES/GCM (AndroidKeyStore) encrypted read/write of the two snapshot files
    ├── WidgetSnapshotIo.kt             ← the store's process-wide lock + encrypt-then-rename write (JVM-testable)
    ├── WidgetSnapshotWriter.kt         ← builds + writes both snapshots from OfflineSyncState, bumps the repaint signal
    └── WidgetSnapshotBuilders.kt       ← buildTodayWidgetSnapshot / buildFloaterWidgetSnapshot (selection, ordering, capping)
```

The write chokepoint lives outside this package, in `core/data/cache/OfflineCacheManager.kt`:
every place that persists a cache change (`saveOfflineStateBlocking`, `clearAllLocalData`,
`clearSessionOnly`, the legacy-SharedPreferences migration) calls `WidgetSnapshotWriter.write(...)`
and then `WidgetRefresher.requestRefresh()`.

**res/xml/{today,floater}_tasks_widget_{,small_,large}_info.xml** — `android:updatePeriodMillis="1800000"`
(30 min). This is only the OS-level fallback; the app still refreshes explicitly on every cache write.

### iOS (`ios-swiftUI/`)

```
Tday/Core/Widget/
├── TodayTasksWidgetSnapshotStore.swift ← both snapshots' DTOs/stores (Today + Floater), WidgetSnapshotFileStore
│                                          (App Group file, .completeUntilFirstUserAuthentication protection),
│                                          WidgetPendingCompletionQueue, WidgetBackendSession
├── WidgetBackgroundRefresh.swift       ← BGAppRefreshTask registration + ~30-min earliest-begin scheduling
└── WatchSessionManager.swift           ← mirrors the Today snapshot to a paired Apple Watch

TdayWidget/
└── TodayTasksWidget.swift              ← the only file the widget extension target compiles: both
                                            TimelineProviders, both Widget structs, CompleteWidgetTaskIntent
                                            (instant-sync completion), duplicated snapshot/session readers

TdayWatchWidget/
└── TdayWatchComplication.swift         ← the watch complication (out of scope for this doc)
```

The write chokepoint is `Tday/Core/Data/Cache/OfflineCacheManager.swift`: `saveOfflineState` calls
`TodayTasksWidgetSnapshotStore.saveTodayTasks(from:)` / `FloaterTasksWidgetSnapshotStore.saveFloaterTasks(from:)`
directly — there is no separate "reload helper" class. Both `save*Tasks` calls are **conditional**:
they skip the file write and the `WidgetCenter.reloadTimelines` call when the snapshot's *displayed*
content (everything but `generatedAtEpochMs`) hasn't changed. That's the opposite of the Android
refresher's deliberately-unconditional stance (see its KDoc) — the two platforms made different
reliability/efficiency trade-offs at this same point in the pipeline.

## Where it's wired in

### Android

- `OfflineCacheManager` is the single chokepoint — no ViewModel injects a widget refresher and calls
  it directly; every code path that persists a cache change already does.
- `TdayApplication.runDeferredStartup()` calls `WidgetSyncWorker.schedule(this)` once at process
  start (`ExistingPeriodicWorkPolicy.UPDATE`); `BootRescheduleReceiver` re-schedules with `.KEEP`
  after a reboot and also fires a `WidgetSyncWorker.runOnce()`.
- `MainActivity.onStart()` calls `WidgetRefresher.requestRefresh()` as belt-and-braces (covers
  drift such as an app-lock toggle that predates a render) — `onStop()` touches nothing widget-related.
- A widget row's render payload comes from `WidgetSnapshotBuilders.buildTodayWidgetSnapshot` /
  `buildFloaterWidgetSnapshot`, which read `OfflineSyncState` directly — there is no separate
  repository method to implement.
- `@HiltWorker` + WorkManager Hilt integration is already wired: `TdayApplication` implements
  `Configuration.Provider`.

### iOS

- App Groups are already enabled on the main app target and the `TdayWidget` / `TdayWatchWidget`
  extensions, group id `group.com.ohmz.tday`.
- `OfflineCacheManager.saveOfflineState` is the single chokepoint: it calls both snapshot stores'
  `save*Tasks(from:)` directly — no call from `ScheduledTaskHomeViewModel` / `TodoListViewModel` needed.
- `AppRootView`'s `.onChange(of: scenePhase)` calls `WidgetBackgroundRefresh.scheduleNext()` on
  `.background` / `.inactive` (arms the ~30-min fallback task) and drains any queued widget
  completions (`todoRepository.drainWidgetCompletions()`) on `.active` and on cold launch.
- The widget kind strings are `"TodayTasksWidget"` and `"FloaterTasksWidget"` — they must match
  `TodayTasksWidgetSnapshotStore.widgetKind` / `FloaterTasksWidgetSnapshotStore.widgetKind` and the
  `kind` on each `Widget` struct declared in `TdayWidget/TodayTasksWidget.swift`.
- For the check-ring App Intent, `CompleteWidgetTaskIntent.perform()` already calls
  `WidgetCenter.shared.reloadTimelines(ofKind:)` and drives the instant-sync completion described below.

## Inline completion (widgets v2)

### Android

Tapping a task's leading dot in the Today/Floater widgets completes it inline:

1. The row's dot carries `actionRunCallback<CompleteTodayTaskAction|CompleteFloaterTaskAction>`
   with the cached record id (`feature/widget/CompleteTaskAction.kt`).
2. The callback resolves `WidgetCompleteTaskSubmitter` via `WidgetEntryPoint`,
   looks the record up in the offline cache, and calls the same
   `TodoRepository.completeTodo/completeFloater` the in-app checkbox uses —
   optimistic cache write (`eagerSync = false`, so the tap isn't held hostage by the
   network) and a queued `COMPLETE_*` mutation.
3. `OfflineCacheManager`'s own write-chokepoint refresh repaints the widget the moment
   the optimistic write lands, so the row disappears immediately. The submitter then
   pushes an **expedited** `WidgetSyncWorker.runOnce()` so the completion reaches the
   backend right away in Server Mode, instead of waiting for the periodic sync or the
   next app launch. Mis-taps are reversed from the app's Completed screen (no transient
   in-widget undo).

### iOS

The widget extension runs in its own process with no cache or SwiftData access, so a
tap is durable-first (a queued fallback) with a best-effort instant path on top:

1. Each row's leading ring is a `Button(intent: CompleteWidgetTaskIntent(...))`
   (`TdayWidget/TodayTasksWidget.swift`, the only file the widget target compiles).
   `perform()` first queues a `{kind, id}` descriptor under the
   `tday.widget.pendingCompletions` app-group key (`WidgetPendingCompletionStore`),
   then marks it "checking" so the ring shows filled + a checkmark for one ~900ms
   beat, and reloads that widget's timeline.
2. Both timeline providers filter out snapshot rows whose id is queued (but keep a row
   mid-"checking" so the animation plays), so the row disappears right after that beat
   even though nothing has necessarily completed server-side yet.
3. Still inside `perform()`, the intent makes a best-effort authenticated
   `PATCH /api/todo/complete` (or `/api/floater/complete`) straight from the widget
   process — the "instant sync" path — using a session cookie and TOFU pin the app
   hands it through the app group (`WidgetBackendSession`, `WidgetPinnedTrustDelegate`).
   Any failure (offline, timeout) is swallowed silently; the queue is the fallback.
4. Regardless of whether that direct call succeeded, the completion also gets applied
   through the normal app path the next time the app activates: `AppRootView`'s
   cold-launch bootstrap and its `scenePhase == .active` handler both call
   `TodoRepository.drainWidgetCompletions()`, which empties the queue
   (`WidgetPendingCompletionQueue` — key and entry shape must stay in lockstep with the
   widget-side store), resolves each id against the offline cache, and rides the normal
   `completeTodo/completeFloater` path — optimistic cache write, queued `COMPLETE_*`
   mutation, sync in Server Mode. The backend endpoints are idempotent, so replaying a
   completion the widget already pushed directly is safe. Mis-taps are reversed from
   the app's Completed screen, same as Android.

## How the refresh cascade works (after pressing +)

For the in-app create flow (the same repository call on both platforms — the widget's own
+ button is a separate, faster path on Android, covered after):

```
User taps + → fills form → taps Save
        │
        ▼
repository.createTodo(payload)  /  createFloater(payload)
        │
        ├─[Android]──▶  OfflineCacheManager.saveOfflineStateBlocking(state)
        │                 ├─ WidgetSnapshotWriter.write(state)      ← unconditional: always re-encrypts + rewrites
        │                 │    └─ WidgetSnapshotSignal.bump()
        │                 └─ widgetRefresher.requestRefresh()
        │                      └─ widget.update(id) per real appWidgetId, paired with THAT id's own
        │                         widget class ──▶ widget recomposes ~instantly
        │
        └─[iOS]──────▶  OfflineCacheManager.saveOfflineState(state)
                          ├─ TodayTasksWidgetSnapshotStore.saveTodayTasks(from: state)     ← conditional: skipped if content unchanged
                          │    ├─ WidgetSnapshotFileStore.write(...)                        (App Group, protected-until-first-unlock)
                          │    ├─ WidgetCenter.reloadTimelines(ofKind: "TodayTasksWidget")
                          │    └─ WatchSessionManager.shared.syncTodaySnapshot()
                          └─ FloaterTasksWidgetSnapshotStore.saveFloaterTasks(from: state)  ← same, kind "FloaterTasksWidget"
```

Android's widget-native + button skips the in-app form entirely: `WidgetCreateTaskActivity`
(a translucent overlay, not `MainActivity`) collects the task, and
`WidgetCreateTaskSubmitter.submitTodayTask/submitFloaterTask` calls the repository and — on
top of the automatic chokepoint above — calls `refreshNow()` directly, so the widget repaints
before the short-lived activity's process window closes. iOS's widget + button
(`Link(destination: mode.createURL)`) instead opens the app itself via the
`tday://todos/create` deep link; there is no widget-process create path on iOS.

```
App backgrounds / user leaves it
        │
        ├─[Android]──▶  MainActivity.onStop() only records a timestamp — nothing widget-related.
        │               The widget was already repainted at the moment of the write above, so
        │               there is nothing left to trigger here. onStart(), on the *next* foreground
        │               return, re-requests both refreshes as belt-and-braces against drift.
        │
        └─[iOS]──────▶  scenePhase → .background / .inactive
                        WidgetBackgroundRefresh.scheduleNext()   ← re-arms the ~30-min BGAppRefreshTask;
                        the widget itself was already reloaded at the moment of the write above
```

## Which widget is which (Android)

There are three Glance classes — `TodayTasksWidget`, `FloaterTasksWidget` and `ListTasksWidget` —
and every question of the form "which widget is this instance?" is answered in exactly one place:
`WidgetInstanceKind.kt`.

- `WidgetInstanceResolver.kindOf(appWidgetId)` reads
  `AppWidgetManager.getAppWidgetInfo(id).provider` and maps that receiver to `TODAY` / `FLOATER` /
  `LIST` through `WidgetInstanceCatalog.bindings` (the nine receivers in the manifest). This is the
  platform's own record of what was placed, so it cannot disagree with the home screen.
- `feedOf(appWidgetId)` turns that into a `WidgetFeed` (`SCHEDULED` or `FLOATER`), consulting
  `WidgetListSelectionStore` for a `LIST` instance. An id that cannot be resolved returns **null**,
  never a default. That is deliberate: an unknown instance used to become the scheduled one on two
  separate paths — the create sheet's `target=` parameter defaulted to `today`, and
  `ListTasksWidget` picked its visuals on a `when` whose `null` branch shared the todo-list arm, so
  a per-list instance whose selection would not read painted the Today sun watermark and the Today
  accent until the selection re-read (opening the app bumps `WidgetSnapshotSignal`, which is what
  made it flip back).
- Rendering an unresolved instance is now explicitly kind-neutral:
  `ListTasksWidget.UnconfiguredListWidgetVisuals` draws no watermark at all and a neutral "+",
  because every watermark this app ships is a kind-specific glyph in that kind's accent, so
  choosing one asserts an identity the instance may not have. `TaskWidgetVisuals`' watermarks are
  nullable for exactly this.
- The widget's "+" carries its own `appWidgetId` on the deep link
  (`tday://todos/create?target=…&appWidgetId=…`, built by `WidgetCreateRoute`). It rides in the
  **data URI**, not an intent extra: Glance builds these `PendingIntent`s with request code 0 and
  `FLAG_UPDATE_CURRENT`, and `Intent.filterEquals` ignores extras — an extra would be shared across
  instances, a query parameter is not. `WidgetCreateTaskActivity` is `singleTop`, so it also
  re-resolves in `onNewIntent`.
- `WidgetCreateTarget.resolve` still ends in `else -> TODAY`, and that is intended: it is the
  no-widget default for the Quick Settings tile, the launcher shortcut and the share sheet. A
  widget-originated tap never reaches it, because every widget stamps
  `WidgetCreateRoute.targetFor(feed)` on its own link, so the parameter still answers even when the
  placement cannot be resolved at sheet time. That mapping and `resolve`'s parameter branch must
  stay exact inverses; `WidgetInstanceKindTest` pins the round trip.
- `WidgetRefresher` is the single repaint trigger. `WidgetInstanceCatalog.renderPlan` pairs every
  live `appWidgetId` with the kind of the receiver it was enumerated from, so no id can ever be
  handed to a foreign widget class, and one call repaints all three kinds — there is no per-kind
  refresher left for a call site to forget. `refreshNow(firstAppWidgetId = …)` paints the instance
  the user just interacted with first. The plan is the **only** thing it renders through: the
  `updateAll` belt-and-braces sweep that used to follow it was removed, because `updateAll` bottoms
  out in the same `AppWidgetManager.getAppWidgetIds` over a *subset* of the receivers (only those
  Glance has recorded this process), so it can never reach an id the plan missed — and it routes by
  class, which is not an instance identity in a release build (see below). An enumeration failure is
  now logged at ERROR instead of silently compensated for.

- Every widget's composition logs its own identity, so a report of the form "my Floater widget
  rendered as the Today widget" is answerable from one `adb logcat -s TdayWidget` capture instead
  of from code review:

  ```
  floater[42]: composing, provider=FLOATER, version=7 locked=false snapshotNull=false
  list[43]:    composing, provider=LIST, version=7 locked=false listType=none snapshotNull=false
  ```

  The prefix is the Glance class that is composing; `provider=` is the receiver
  `AppWidgetManager.getAppWidgetInfo` says owns that id. Glance keys its render session by
  `appWidgetId` **alone** (`AppWidgetSession` → `createUniqueRemoteUiName(appWidgetId)`) and reuses
  a running session with the `GlanceAppWidget` it was constructed with, so a session started with
  the wrong class renders the wrong kind for that instance until the process dies. That is not
  hypothetical — it is what the R8 section below describes — and if it happens again the line reads
  `today[42]: composing, provider=FLOATER KIND-MISMATCH …`, at ERROR level.

`WidgetInstanceKindTest` and `WidgetRefreshRoutingTest` cover these rules as plain JVM tests.

### Widget class identity survives R8 only because of a keep rule

**Everything above assumes `TodayTasksWidget`, `FloaterTasksWidget` and `ListTasksWidget` are three
different classes at runtime. In a release build that is true only because `proguard-rules.pro`
says so.**

Glance identifies a widget provider by class NAME, never by class identity:
`GlanceAppWidgetManager.updateReceiver` records `provider:<receiver>` as
`appWidget.javaClass.canonicalName`; `updateAll` reads it back through an unvalidated
`providerNameToReceivers[canonicalName]`; and `GlanceAppWidget.update` keys its render session on
`createUniqueRemoteUiName(appWidgetId)` — the id alone, with no class in the key.

The three widget classes are structurally identical, so R8's horizontal class merger collapsed all
three into one. In the mapping of a release build made without the keep rules, `FloaterTasksWidget`
mapped to `ki1` carrying a synthesized `$r8$classId` discriminator and a `provideGlance` inlined
from all three, and the other two classes had no mapping entry at all; `GlanceAppWidget` itself was
then *vertically* merged into it, which R8 only does once a class has one subclass left. All nine
receivers therefore registered under one provider name, every kind's `updateAll` enumerated every
other kind's ids, and a Today-flavoured instance could take ownership of a Floater instance's render
session until the process was replaced — "my Floater widget turned into the Today widget until I
reopened the app". Debug builds and unit tests are unminified, which is why source review and JVM
tests all said this was impossible.

Two things keep it fixed:

- `-keep class * extends androidx.glance.appwidget.GlanceAppWidget` (and the same for
  `GlanceAppWidgetReceiver`) in `android-compose/app/proguard-rules.pro`. Pinning the names is what
  excludes the classes from the merger. glance-appwidget ships no such rule of its own — its
  consumer rules cover only `ActionCallback` subclasses, which is why `CompleteTodayTaskAction` and
  `CompleteFloaterTaskAction`, identically-shaped siblings, were never merged.
- `:app:verifyReleaseWidgetClassIdentity`, wired into `assembleRelease`/`bundleRelease`. It reads
  the R8 mapping back and fails the build if any of the twelve classes lost its mapping entry or
  shares an output name with another. There is no unit-test equivalent — the defect exists only in
  the minified artifact.

What the single refresher fixed, precisely — the per-kind refreshers could not paint the wrong
content *in an unminified build* (each only ever enumerated its own receivers' ids, and Glance's
`updateAll` resolves a `GlanceAppWidget` class to that class's own receivers via
`GlanceAppWidgetManager.getGlanceIds`); under class merging that guarantee did not hold.
What they got wrong was coverage: the add path aimed its one *synchronous* repaint by a guessed
create target, leaving the widget actually tapped to the fire-and-forget request from the cache
write — which a short-lived widget process can be torn down before it paints — and `MainActivity`,
`TodoRepository`, `SyncManager`, `BulkTaskRepository` and `BootRescheduleReceiver` never called the
per-list refresher at all, so a per-list instance sat on its static `android:initialLayout` after a
reboot until some unrelated cache write repainted it.

## Snapshot durability (Android)

Every widget renders from `filesDir/widget/*.json`, so how those files are written decides whether a
widget shows content or "Loading tasks…". Three rules, all in `WidgetSnapshotIo`:

- **Encrypt first, then swap.** The write serialises and encrypts into memory, writes a sibling
  `.tmp`, and `rename(2)`s it onto the target. A Keystore failure (a key invalidated by a
  lock-screen change, a provider unavailable before first unlock), a cipher failure or a full disk
  therefore leaves the *previous* good snapshot readable. The store used to `delete()` the target
  and only then evaluate `encrypt(bytes)`, inside a `runCatching {}.getOrElse { false }` — so those
  failures destroyed the last good snapshot and left no log line explaining why.
- **One process-wide lock** around every read and write. The writers do not otherwise coordinate:
  `OfflineCacheManager`'s save, clear and legacy-migration paths, `WidgetHydrateWorker` on a
  WorkManager thread and `WidgetListConfigurationViewModel.selectList` on `viewModelScope` all write
  the same files. Two interleaving inside one `writeBytes` produced a file that failed GCM
  authentication, which `read` then deleted.
- **The file is never absent.** `FloaterTasksWidget`, `TodayTasksWidget`, `ListTasksWidget` and
  `WidgetFastPaint` all decide whether to hydrate (or whether to fast-paint at all) from a bare
  `File.exists()`. Under delete-then-write that probe was transiently false on *every* cache write,
  which spuriously enqueued `WidgetHydrateWorker` as one more unsynchronised writer and made fast
  paint silently skip. A rename-based write closes all four windows with no call-site change.

`WidgetSnapshotIoTest` covers all three as plain JVM tests — the store itself needs AndroidKeyStore
and a real `Context`, which is why the file behaviour lives in its own class.

## Background refresh cadence

- Android `WidgetSyncWorker` (WorkManager `PeriodicWorkRequest`, network-constrained) fires every
  **30 minutes** (5-min flex window) and runs a full `SyncManager.syncCachedData(force = true)` —
  a real network sync, not just a cache-only repaint. Retries (linear backoff, up to 3 attempts)
  only apply in Server Mode; Local Mode returns success immediately without touching the network.
- `today_tasks_widget_info.xml` / `floater_tasks_widget_info.xml` (and their `_small`/`_large`
  variants) also set `updatePeriodMillis="1800000"` (30 min) as an OS-level belt-and-braces on top
  of the WorkManager job.
- iOS `BGAppRefreshTask` (`com.ohmz.tday.ios.widgetRefresh`) has an earliest-begin hint of
  **30 minutes**, (re-)submitted on every background/inactive transition — iOS decides the actual
  cadence and may run it less often based on usage.
- The Today widget's own WidgetKit timeline additionally requests a fresh `getTimeline` at
  `min(now + 30 min, next 6am/6pm boundary)` (so the day/night watermark artwork follows the
  clock); the Floater widget's timeline refreshes every 30 min flat. Neither adds new task data
  on its own — that only ever changes via a snapshot write.
- On both platforms, the snapshot-write step is the fast path and fires on every offline-cache
  write, independent of any cadence above — this is what makes freshness feel immediate.
