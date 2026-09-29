# Widget Synchronization

How the **Today** (Scheduled) and **Floater** (Anytime) home-screen widgets stay in sync with the
app on Android (RemoteViews + WorkManager) and iOS (WidgetKit + App Groups).

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
| (iOS) Today widget shows yesterday's tasks, or "No tasks due today", after midnight when nothing syncs (offline) | The snapshot baked one local day at write time and only a cache write retook it; the extension cannot open the cache to rebuild | The snapshot carries the next six days and the widget renders the day containing its entry date, with a timeline entry at local midnight; launch, foreground and background refresh retake it — see "Day rollover (iOS)" |
| (Android) Today widget says "No tasks due today", shows yesterday's tasks, or sits on "Loading tasks…" after midnight | The Today snapshot bakes its day at write time, and only a cache write with UI changes rewrote it — so on any day nothing changed (offline, or just a quiet day) it described an earlier day. v0.7.41 rendered that as LOADING until `WidgetHydrateWorker` rebuilt it, and WorkManager in the background can be deferred for hours, so the widget stuck on "Loading tasks…" | Like iOS, the snapshot carries the next six days (`upcomingDays`) and the widget renders the one containing now (`WidgetSnapshot.todayAt`) — no rebuild, no network, no WorkManager. Every cache save also rewrites a snapshot from an earlier day even when nothing changed (`WidgetSnapshotWriter.ensureCurrent`), so opening the app or any sync tops the days up. The receivers' 30-min `onUpdate` re-renders, and every render re-reads the clock, so the day turns over offline; `WidgetHydrateWorker` is left as the fallback for a snapshot that has run out of days |
| (Android, HyperOS 4) Both widgets stuck on "Loading tasks…" after sign-in, even with Autostart on and no battery restrictions | Not an app-side render failure: the app composed with a valid snapshot and `AppWidgetService` stored the RemoteViews, but the launcher's widget host had stopped listening (`dumpsys appwidget` → that host's `callbacks=null`), so nothing was delivered until the launcher restarted | Nothing an app can force; the renderer below at least never depends on a WorkManager job to publish. Diagnose with the `dumpsys appwidget` host block before touching app code |
| (Android, HyperOS 4) Widget shows its header but the task list is blank, and no tap works — not the body, not "+", not the check ring | HyperOS 4's launcher re-implements RemoteViews in Flutter and ignores the `RemoteViews(packageName, layoutId, viewId)` constructor. Glance 1.1.1 builds every element that way on API 33+ and targets that generated id for text, clicks and the list adapter, so on that host every one of those actions missed | Rendering no longer uses Glance: `TaskWidgetRemoteViews` builds plain RemoteViews from `layout/widget_task.xml` and `widget_task_list_row.xml`, where every targeted view has a real XML id. Verified on the device with hand-built probes (XML ids, `addView`, `RemoteCollectionItems` and size maps all work there; the generated-id child does not) |
| (Android) Pressing a widget shows a sharp square highlight around the rounded widget | The root view was square with a rounded *drawable*, so the launcher's press/drag treatment followed the square bounds | The root is `@android:id/background` with `android:clipToOutline="true"`, which is what launchers use to find the widget's rounded silhouette |
| (Android) Today widget still says "Set up your workspace" after setting up a workspace with no tasks | SETUP is baked into the snapshot from `AppDataMode`, and `WidgetSnapshotWriter.ensureCurrent` only rewrote a missing or earlier-day snapshot, so a mode change that changed no task data left SETUP on disk | `ensureCurrent` also rewrites when the workspace flag differs from the one the last write recorded (a boolean in plain prefs) |
| (Android) Widget text unreadable after toggling system dark/light mode | Every widget color resolves correctly on a repaint, but nothing ever asked for one when the system theme itself changed — `ACTION_CONFIGURATION_CHANGED` is undeliverable to a manifest receiver, and no other trigger covered "app not foregrounded, theme flipped" | `TdayApplication.onConfigurationChanged` compares the system `uiMode`'s night bits against the last-seen value and calls `WidgetRefresher.requestRefresh()` on an actual flip — `Application` is notified in any live process, including a widget-only one that never opened `MainActivity` |

## Files — where they go

### Android (`android-compose/`)

```
app/src/main/java/com/ohmz/tday/compose/feature/widget/
├── TaskWidgetDesign.kt                 ← TaskWidgetRemoteViews: builds the RemoteViews every widget renders through (states, header, list, sizes)
├── WidgetRenderer.kt                   ← renders + publishes via AppWidgetManager.updateAppWidget, one render at a time process-wide
├── TaskWidgetReceiver.kt               ← AppWidgetProvider base: onUpdate / resize render that receiver's own ids straight away
├── TodayTasksWidget.kt                 ← Today model: reads WidgetSnapshotStore + app lock, no Hilt on the render path
├── TodayTasksWidgetReceiver.kt         ← Small/default/Large receivers
├── FloaterTasksWidget.kt               ← mirror of TodayTasksWidget for floaters
├── FloaterTasksWidgetReceiver.kt       ← mirror receivers
├── ListTasksWidget.kt                  ← List widget model: unconfigured setup state, then one picked list per instance
├── ListWidgetReceiver.kt               ← its Small/default/Large receivers (+ onDeleted cleanup)
├── WidgetTaskActions.kt                ← list-row taps: the row template, the invisible trampoline activity, the complete receiver
├── WidgetRefresher.kt                  ← the ONE repaint trigger for all three widgets (Hilt Singleton, one conflated channel)
├── WidgetInstanceKind.kt               ← the ONE per-instance kind/feed resolution (provider binding -> TODAY/FLOATER/LIST) + the render plan
├── TodayTasksWidgetPreviewPublisher.kt ← Android 15+ widget-picker preview (setWidgetPreview)
├── WidgetCompleteTaskSubmitter.kt      ← resolves the tapped row, completes it, pushes an expedited sync
├── WidgetCreateTaskActivity.kt         ← translucent Activity behind the widget's + button (a bottom sheet, not MainActivity)
├── WidgetCreateRoute.kt                ← the tday://todos/create deep link (carries the tapped appWidgetId) + WidgetCreateTarget
├── WidgetCreateTaskSubmitter.kt        ← creates the task, then repaints every instance with the tapped one first
├── WidgetListPickerActivity.kt         ← the List widget's list picker (tap on an unconfigured widget, reconfigure, APPWIDGET_CONFIGURE)
├── WidgetListSelectionStore.kt         ← per-appWidgetId list selection (plain SharedPreferences)
├── WidgetEntryPoint.kt                 ← Hilt @EntryPoint exposing only the completion/refresh singletons to widget actions
├── WidgetHydrateWorker.kt              ← the only widget-flow class allowed to open the encrypted cache; seeds a missing snapshot, rebuilds a Today snapshot that has run out of days
├── WidgetSyncWorker.kt                 ← WorkManager periodic (30 min, network sync) + expedited one-shot
├── WidgetLog.kt                        ← shared "TdayWidget" Logcat tag + the per-render identity line
└── snapshot/
    ├── WidgetSnapshot.kt               ← the render-payload DTOs
    ├── WidgetSnapshotStore.kt          ← AES/GCM (AndroidKeyStore) encrypted read/write of the snapshot files
    ├── WidgetSnapshotIo.kt             ← the store's process-wide lock + encrypt-then-rename write (JVM-testable)
    ├── WidgetSnapshotWriter.kt         ← builds + writes every snapshot from OfflineSyncState; ensureCurrent tops them up
    └── WidgetSnapshotBuilders.kt       ← buildTodayWidgetSnapshot / buildFloaterWidgetSnapshot (selection, ordering, capping)

app/src/main/res/layout/
├── widget_task.xml                     ← the placed widget: root @android:id/background, clipToOutline, real ids only
└── widget_task_list_row.xml            ← one row of the scrolling task list (RemoteCollectionItems item)
```

The write chokepoint lives outside this package, in `core/data/cache/OfflineCacheManager.kt`:
every place that persists a cache change (`saveOfflineStateBlocking`, `clearAllLocalData`,
`clearSessionOnly`, the legacy-SharedPreferences migration) calls `WidgetSnapshotWriter.write(...)`
and then `WidgetRefresher.requestRefresh()`.

**res/xml/{today,floater}_tasks_widget_{,small_,large}_info.xml** — `android:updatePeriodMillis="1800000"`
(30 min). This is only the OS-level fallback; the app still refreshes explicitly on every cache write.
Each `onUpdate` re-renders from the snapshot and the current clock, which is what turns the Today
widget over after midnight (see the table above) — it fires offline, unlike the network-constrained
`WidgetSyncWorker`.

### iOS (`ios-swiftUI/`)

```
Tday/Core/Widget/
├── TodayTasksWidgetSnapshotStore.swift ← both snapshots' DTOs/stores (Today + Floater), WidgetSnapshotFileStore
│                                          (App Group file, .completeUntilFirstUserAuthentication protection),
│                                          WidgetPendingCompletionQueue, WidgetBackendSession
├── TodayWidgetDayWindow.swift          ← local-day windows for the Today snapshot; the ONE source file the
│                                          app and the TdayWidget extension both compile
├── WidgetBackgroundRefresh.swift       ← BGAppRefreshTask registration + ~30-min earliest-begin scheduling
└── WatchSessionManager.swift           ← mirrors the Today snapshot to a paired Apple Watch

TdayWidget/
└── TodayTasksWidget.swift              ← the extension's own source (it also compiles TodayWidgetDayWindow.swift):
                                            both TimelineProviders, both Widget structs, CompleteWidgetTaskIntent
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
- `TdayApplication.onConfigurationChanged` calls `WidgetRefresher.requestRefresh()` whenever the
  system day/night setting flips, so a placed widget picks up the new color scheme without the
  app ever being opened. This only fires while the app's process is alive; a process the system
  has already killed in the background is covered by the existing `WidgetSyncWorker` fallback and
  the next app open instead, same as any other drift this app tolerates.
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
- The same `.active` handler, the cold-launch `.task`, and every `BGAppRefreshTask` run (whatever
  its sync did) call `OfflineCacheManager.refreshTodayWidgetSnapshot()`, which retakes the Today
  snapshot from the in-memory `lastState` mirror. It rewrites the snapshot and reloads WidgetKit only
  when the content differs — normally once per new local day — see "Day rollover (iOS)".
- The widget kind strings are `"TodayTasksWidget"` and `"FloaterTasksWidget"` — they must match
  `TodayTasksWidgetSnapshotStore.widgetKind` / `FloaterTasksWidgetSnapshotStore.widgetKind` and the
  `kind` on each `Widget` struct declared in `TdayWidget/TodayTasksWidget.swift`.
- For the check-ring App Intent, `CompleteWidgetTaskIntent.perform()` already calls
  `WidgetCenter.shared.reloadTimelines(ofKind:)` and drives the instant-sync completion described below.

## Rendering (Android)

Widgets are plain RemoteViews built by hand; there is no Glance, no composition and no render
session.

- **One renderer.** `WidgetRenderer.publish` turns each `(appWidgetId, kind)` step into a model
  (`TodayTasksWidget.model` / `FloaterTasksWidget.model` / `ListTasksWidget.model` — app lock first,
  then the snapshot, then the clock) and `TaskWidgetRemoteViews.build` into RemoteViews, then calls
  `AppWidgetManager.updateAppWidget` directly. Renders are serialised process-wide by one mutex, so
  the refresher, an `onUpdate` and a resize can never publish out of order.
- **Straight from the broadcast.** `TaskWidgetReceiver.onUpdate` and `onAppWidgetOptionsChanged`
  render that receiver's own ids inside a `goAsync()` window. Under Glance the only publish point
  was a WorkManager `SessionWorker` (~2.4-3.0s after a reboot, and nothing at all if the job never
  ran), which `WidgetFastPaint` existed to shortcut; both are gone.
- **Real ids only.** Every view an action targets is declared, with its id, in
  `layout/widget_task.xml` or `layout/widget_task_list_row.xml`. Hosts that re-implement RemoteViews
  (HyperOS 4's Flutter launcher) ignore ids stamped on at runtime — see the table at the top. Only
  RemoteViews-allowed classes appear in those layouts (empty `TextView`s stand in for spacers).
- **Sizes.** `taskWidgetSizes` reads the host's reported sizes (`OPTION_APPWIDGET_SIZES` on API 31+,
  else the min/max landscape/portrait pair, else the provider's minimum). Each distinct shape
  (`taskWidgetShapeFor`: the layout bucket from `taskWidgetLayoutFor` — COMPACT, WIDE, MEDIUM, TALL —
  plus whether it is narrower than 220dp) is built once; one shape is a plain RemoteViews, several
  become a size map (API 31+) or the orientation pair below it. A narrow MEDIUM/TALL (a two-column
  widget on a tall-celled launcher) drops the due pills and Today's date and ring, the way iOS's
  small family does.
- **Every render sets every optional view both ways.** A host that already shows `widget_task`
  re-applies a new render onto the live views instead of inflating afresh (on every update and
  resize, and on every recycled list row), so a view only ever switched on stays on. All visibility
  goes through `setVisible(id, Boolean)`; `WidgetReapplyVisibilityTest` and the on-device
  `TaskWidgetReapplyTest` pin it.
- **The task list** is a `ListView` fed by `RemoteViewsCompat.setRemoteAdapter` with
  `RemoteCollectionItems` (core-remoteviews backports it below API 31 through its own service), so it
  still scrolls through the full snapshot. It has two view types: task rows
  (`layout/widget_task_list_row.xml`) and labels (`layout/widget_task_list_label.xml` — the Overdue
  section name, and the message and day name heading an empty Today's preview).
- **Today's header** (both platforms): a date block ("Mon" over "28") for the day being shown, "Today"
  over "2 of 5 done" (or "3 due" before anything is done), and a progress ring — tasks due that day
  that are done, out of those plus the ones still due. Overdue rows sit in their own section below
  the day's tasks, under "Overdue · N", with the day each was due in a red pill. An empty day with
  room previews the next day with tasks ("Tomorrow · 3 due") instead of centring one line.
- **Rounded press.** The root is `@android:id/background` with `clipToOutline`, so launchers follow
  the rounded silhouette for press, drag and launch animations.
- **Colors** are resource references in the XML (and `setColorStateList` on API 31+ for the overdue
  tint), so the host resolves day/night itself.

## Inline completion (widgets v2)

### Android

Tapping a task's leading check ring in any widget completes it inline:

1. A list item cannot own a `PendingIntent`, so the list carries one mutable template
   (`WidgetTaskActions.rowTemplate`, an activity) and each row fills in its action: the ring fills
   in `complete` + the cached record id, the rest of the row fills in `open`. The template targets
   `WidgetTaskActionActivity`, an invisible trampoline (translucent, no animation, no history, its
   own task affinity) — the same shape Glance used for lazy lists. Where to open is re-derived from
   the instance, never taken from the fill-in, because a mutable template lets the host set extras.
2. For `complete`, the trampoline hands off to `WidgetTaskActionReceiver`, which keeps the process
   alive with `goAsync()`, resolves the instance's feed (`WidgetInstanceResolver.feedOf`) and calls
   `WidgetCompleteTaskSubmitter`, which looks the record up in the offline cache and calls the same
   `TodoRepository.completeTodo/completeFloater` the in-app checkbox uses — optimistic cache write
   (`eagerSync = false`, so the tap isn't held hostage by the network) and a queued `COMPLETE_*`
   mutation.
3. Before any of that, the receiver marks the id checked off (`WidgetCheckOff`, in memory) and
   repaints: the ring fills and ticks, the text is struck through and dimmed, and Today's ring counts
   it as done. It holds that frame for the shared `WidgetCheckHold` token (900ms, the same beat iOS
   plays), then writes the completion, then calls `refreshNow(firstAppWidgetId = …)` so the row
   leaves before the broadcast window closes. The write waits for the beat because the row is only
   kept on screen by still being in the snapshot; a widget cannot animate its removal, so the check
   is the whole of the feedback. The submitter also pushes an
   **expedited** `WidgetSyncWorker.runOnce()` so the completion reaches the backend right away in
   Server Mode. Mis-taps are reversed from the app's Completed screen (no transient in-widget undo).

### iOS

The widget extension runs in its own process with no cache or SwiftData access, so a
tap is durable-first (a queued fallback) with a best-effort instant path on top:

1. Each row's leading ring is a `Button(intent: CompleteWidgetTaskIntent(...))`
   (`TdayWidget/TodayTasksWidget.swift`; the widget target also compiles the app's
   `TodayWidgetDayWindow.swift` and `TdayMotionGenerated.swift`).
   `perform()` first queues a `{kind, id}` descriptor under the
   `tday.widget.pendingCompletions` app-group key (`WidgetPendingCompletionStore`),
   then marks it "checking" so the ring shows filled + a checkmark for one beat — the
   shared `WidgetCheckHold` token (900ms), which the extension compiles in from
   `TdayMotionGenerated.swift` — and reloads that widget's timeline.
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
        │                 └─ widgetRefresher.requestRefresh()
        │                      └─ WidgetRenderer.publish(plan): per real appWidgetId, rendered as THAT
        │                         id's own kind ──▶ updateAppWidget ──▶ widget repaints ~instantly
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

There are three widget kinds — `TodayTasksWidget`, `FloaterTasksWidget` and `ListTasksWidget`, each in
Small, Medium and Large — and every question of the form "which widget is this instance?" is answered in exactly one place:
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
  accent until the selection re-read (the next repaint, e.g. opening the app, is what made it flip
  back).
- Rendering an unresolved instance is now explicitly kind-neutral:
  `ListTasksWidget.UnconfiguredListWidgetVisuals` draws no watermark at all and a neutral "+",
  because every watermark this app ships is a kind-specific glyph in that kind's accent, so
  choosing one asserts an identity the instance may not have. `TaskWidgetVisuals`' watermarks are
  nullable for exactly this.
- The widget's "+" carries its own `appWidgetId` on the deep link
  (`tday://todos/create?target=…&appWidgetId=…`, built by `WidgetCreateRoute`). It rides in the
  **data URI**, not an intent extra: `Intent.filterEquals` ignores extras, so an extra would not
  distinguish two instances' `PendingIntent`s, while a query parameter does. (The renderer also uses
  the `appWidgetId` as each `PendingIntent`'s request code, which covers intents that differ only by
  an extra, such as the per-list reconfigure intent.) `WidgetCreateTaskActivity` is `singleTop`, so it also
  re-resolves in `onNewIntent`.
- `WidgetCreateTarget.resolve` still ends in `else -> TODAY`, and that is intended: it is the
  no-widget default for the Quick Settings tile, the launcher shortcut and the share sheet. A
  widget-originated tap never reaches it, because every widget stamps
  `WidgetCreateRoute.targetFor(feed)` on its own link, so the parameter still answers even when the
  placement cannot be resolved at sheet time. That mapping and `resolve`'s parameter branch must
  stay exact inverses; `WidgetInstanceKindTest` pins the round trip.
- `WidgetRefresher` is the single repaint trigger. `WidgetInstanceCatalog.renderPlan` pairs every
  live `appWidgetId` with the kind of the receiver it was enumerated from, so no id can ever be
  handed to a foreign widget kind, and one call repaints all three kinds — there is no per-kind
  refresher left for a call site to forget. `refreshNow(firstAppWidgetId = …)` paints the instance
  the user just interacted with first. The plan is the **only** thing it renders through: the
  Glance-era `updateAll` sweep that used to follow it could only reach ids the plan already had and
  routed by class, which is not an instance identity in a release build (see below). An enumeration
  failure is logged at ERROR instead of silently compensated for.

- Every render logs the instance's identity, so a report of the form "my Floater widget
  rendered as the Today widget" is answerable from one `adb logcat -s TdayWidget` capture instead
  of from code review:

  ```
  floater[42]: composing, provider=FLOATER, locked=false snapshotNull=false
  list[43]:    composing, provider=LIST, locked=false listType=none snapshotNull=false
  ```

  The prefix is the kind being rendered; `provider=` is the receiver
  `AppWidgetManager.getAppWidgetInfo` says owns that id. Under the Glance renderer these could
  disagree — Glance kept whichever widget class started an id's render session, and R8 once merged
  the three classes (see below). Rendering now routes by kind with no session, but if the two ever
  disagree again the line reads `today[42]: composing, provider=FLOATER KIND-MISMATCH …`, at ERROR.

`WidgetInstanceKindTest` and `WidgetRefreshRoutingTest` cover these rules as plain JVM tests.

### Receiver class identity survives R8 only because of a keep rule

`WidgetInstanceResolver.kindOf` maps the provider class NAME the platform reports for an id back to
a kind through each receiver's runtime `Class.name`, so the nine size receivers must reach the
release APK under their own names. The manifest pins them, and so does an explicit
`-keep class * extends com.ohmz.tday.compose.feature.widget.TaskWidgetReceiver` in
`android-compose/app/proguard-rules.pro`. `:app:verifyReleaseWidgetClassIdentity`, wired into
`assembleRelease`/`bundleRelease`, reads the R8 mapping back and fails the build unless each receiver
appears under its **own** name — which also catches a class R8 *removed* (it still gets a mapping
line, as `<original> -> R8$$REMOVED$$CLASS$$<N>:`) and one *renamed* because a keep rule stopped
matching. There is no unit-test equivalent; the defect exists only in the minified artifact.

History worth keeping: the Glance renderer identified widgets by class name too, keyed its render
sessions on the `appWidgetId` alone, and R8's horizontal class merger collapsed the three
structurally identical Glance widget classes into one. All nine receivers then registered under one
provider name and a Today session could own a Floater instance until the process died — "my Floater
widget turned into the Today widget until I reopened the app". Debug builds and unit tests are
unminified, which is why source review and JVM tests all said it was impossible. The renderer no
longer has widget classes or sessions to confuse; the receiver names are what remain load-bearing.

What the single refresher fixed, precisely: the per-kind refreshers it replaced got coverage wrong.
The add path aimed its one *synchronous* repaint by a guessed create target, leaving the widget
actually tapped to the fire-and-forget request from the cache write — which a short-lived widget
process can be torn down before it paints — and `MainActivity`, `TodoRepository`, `SyncManager`,
`BulkTaskRepository` and `BootRescheduleReceiver` never called the per-list refresher at all, so a
per-list instance sat on its static `android:initialLayout` after a reboot until some unrelated
cache write repainted it.

## Widget corner radius (Android)

A widget has **two** outer surfaces here, and they are deliberately separate drawables with
separate radius tokens (`app/src/main/res/values/dimens.xml`):

| Surface | Drawable | Token | Where it renders |
| --- | --- | --- | --- |
| Placed widget | `widget_preview_background` | `tday_widget_surface_corner_radius` (24dp) | The rendered widget's root (`layout/widget_task.xml`) and the three `android:initialLayout`s — i.e. the widget on a home screen |
| Picker preview | `widget_preview_bg_today`, `widget_preview_bg_floater` | `tday_widget_picker_preview_corner_radius` (16dp) | The six `android:previewLayout`s and `TodayTasksWidgetPreviewPublisher`'s `setWidgetPreview` — i.e. the card in the launcher's widget picker, API 31+ only |

**The picker preview's radius must never be LARGER than the host's clip.** Since Android 12 the
launcher clips the preview card to its own enforced radius
(`android:dimen/system_app_widget_background_radius`, capped by Launcher3's
`enforced_rounded_corner_max_radius`; both default to 16dp, and OEMs raise them). Drawing a
rounder corner than that does not render rounder: our fill lands wholly *inside* the clip, our
arc becomes the visible edge, and the crescent between the two arcs is a hole in our own artwork
through which whatever the host paints behind the card shows.

That is what shipped as "the medium widget preview has a thin light outline around it": the art
was hardcoded at 24dp against a host clipping at ~19.8dp, so each corner leaked a crescent of the
picker's own opaque light backing (~354px² per corner at density 3, closing into a ring with a
fainter sliver along the straight edges). It was never a `<stroke>` — there is none anywhere in
the widget drawables. 16dp is the AOSP default of both platform values, so the art now stays at
or inside the clip on any launcher.

**Do not point the placed widget at the picker token.** They were briefly one token, which is the
mistake this table exists to prevent. Lowering the placed radius is *not* a no-op that the host's
clip absorbs: at 24dp inside a 19.8dp clip our arc is the silhouette (that is exactly why the
crescent existed), so going to 16dp moves every placed widget's visible corner to 19.8dp on a
clipping host, and to 16dp outright on the API 26-30 hosts that clip nothing and on any API 31+
launcher that does not implement the enforcement. That is a visible change to every home screen,
so treat it as a design decision with a device in hand, not as a side effect of a picker fix.
Over wallpaper the same "hole" has nothing bright behind it, so there is no artifact to fix there.

The accepted cost of keeping them apart is that the picker card reads slightly *squarer* than the
widget it previews — the card shows the host's clip (~19.8dp on the reporting device, 16dp on a
launcher that does not clip previews) against the placed widget's 24dp. That is the cheaper of the
two mismatches: the alternative is either the light outline the picker leak produced, or re-rounding
every home screen to remove it. Revisit it only with a device.

`WidgetCornerRadiusTest` pins this by **discovery**, not by a list of filenames: it walks the
manifest's `android.appwidget.provider` meta-data to every descriptor, each descriptor's
`initialLayout`/`previewLayout` to a layout, and each layout's root `android:background` to a
drawable, then asserts each drawable uses the token for its side and that no drawable serves both
sides. A new widget, size, or preview layout is covered as soon as it is wired up. A drawable no
widget roots on is not a widget surface and is not checked — so `widget_add_button_background`
and friends keep their own literals, and so would an orphaned file.

### Open: why the report singled out the medium card

Established: the art is identical across all nine descriptors (the manifest → info-xml →
`previewLayout` mapping and `TodayTasksWidgetPreviewPublisher`'s nine entries agree layout for
layout, rooting on just those two drawables), and the corner hole is present wherever the host
paints something bright behind the card. **Unresolved:** the report describes the outline on the
medium preview specifically. In the report screenshot the neighbouring preview pages carry the
same 24dp art at the same scale and opacity yet have no pixel brighter than 41/255 on their
perimeter, which suggests the light backing is painted only behind the *focused* page — host
behaviour, unverified, no device on the build machine. If a size-to-size difference is still
visible after this ships, that difference is the thing to chase; it was never explained.

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
- **The file is never absent.** `FloaterTasksWidget`, `TodayTasksWidget` and `ListTasksWidget` read
  the snapshot on every render and enqueue `WidgetHydrateWorker` when there is none. Under
  delete-then-write the file was transiently absent on *every* cache write, which spuriously
  enqueued the worker as one more unsynchronised writer and painted "Loading tasks…" over real
  content. A rename-based write closes that window with no call-site change.

`WidgetSnapshotIoTest` covers all three as plain JVM tests — the store itself needs AndroidKeyStore
and a real `Context`, which is why the file behaviour lives in its own class.

## Day rollover (iOS)

The Today snapshot is a picture of "due today" taken when the app writes it. Only an offline-cache
write retook it, so after midnight with nothing syncing (offline, or simply no change) the widget
kept rendering yesterday's picture: yesterday's rows under today's clock, or "No tasks due today"
while the cache already held today's tasks. A per-list todo widget went stale the same way (today's
rows missing, although its overdue tint was computed live). The WidgetKit timeline reloads covered
below never helped: they re-read the same file.

Android fixes this by rebuilding from its cache (`WidgetHydrateWorker`). The iOS extension cannot
open the cache, so the snapshot carries the days ahead instead:

- **The snapshot records its day and the next six.** `makeSnapshot` writes `dayStartEpochMs` /
  `dayEndEpochMs` for the day `tasks` describe, plus `upcomingDays` — the same "due today" feed for
  each of the next six local days (true count, rows capped at 20). A todo list's `perList` entry adds
  `upcomingTotalCounts` and `upcomingTasks`: every row that makes the list's cap on any upcoming day,
  in sort order. A list's window is cumulative (overdue + due that day), so the upcoming rows due
  before a given day's end begin with exactly the rows a rebuild on that day would write, and the
  extension needs no sort engine. The windows come from `TodayWidgetDayWindow`, the one file both
  targets compile, so writer and reader agree on where a day starts (DST days keep their real
  length).
- **The widget renders the day containing its entry date.** `TodayTasksProvider` (and a per-list
  todo instance in either gallery slot) looks the entry date up in the covered days and renders that
  day's feed. Each timeline carries a second entry at the next local midnight, so the switch lands on
  time even if nothing reloads the timeline.
- **Past the last covered day the widget says so.** It renders "Open T'Day / to refresh today's
  tasks" rather than any day it cannot vouch for; tapping it opens the app, which retakes the
  snapshot. A schema-2 snapshot (no window) covers only the day it was generated, so an upgraded
  install goes to this state after midnight until the app next launches instead of trusting it
  forever.
- **The app retakes the snapshot whenever the day may have turned.** Launch, every foreground return
  and every `BGAppRefreshTask` run call `OfflineCacheManager.refreshTodayWidgetSnapshot()`. The day
  window counts as content in `hasSameContent`, so a new day always writes, even when both days hold
  the same tasks.
- The Apple Watch mirror sends `withoutUpcomingDays()`: the watch shows `tasks` alone, so the extra
  days would only grow its WatchConnectivity payload.

`TodayTasksWidgetSnapshotStoreTests` pins that each carried day, global and per-list, reads exactly
like a rebuild on that day, that a snapshot written late yesterday lands on today's rows just after
midnight, that a snapshot without a window stops at its own day, and the DST window lengths.

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
  on its own — that only ever changes via a snapshot write. What the Today widget does on its own
  is switch days: each timeline also has an entry at the next local midnight, rendered from the
  upcoming days the snapshot already carries (see "Day rollover (iOS)"). A todo list picked in the
  Floater slot gets the same midnight entry.
- `BGAppRefreshTask` runs also retake the Today snapshot from the cache after their sync, success
  or not, so a run that finds nothing new, or no network, still rolls the widget onto the new day.
- On both platforms, the snapshot-write step is the fast path and fires on every offline-cache
  write, independent of any cadence above — this is what makes freshness feel immediate.
