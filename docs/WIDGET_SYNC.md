# Widget Synchronization

How the **Today** (Scheduled) and **Floater** (Anytime) home-screen widgets stay in sync with the
app on Android (Glance + WorkManager) and iOS (WidgetKit + App Groups).

The guiding principle is **app-driven, immediate refresh**: rather than relying on the platform's
slow, system-scheduled update intervals, the app pushes an explicit widget refresh after every task
mutation and on app background. Background workers exist only as a freshness fallback.

## What this fixes

| Symptom                                            | Root cause                                                                                     | Fix                                                                                         |
|----------------------------------------------------|------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| Widget stays stale for 30+ min after adding a task | Relying on `updatePeriodMillis` (Android min 30 min) or WidgetKit's passive refresh            | Trigger explicit refresh immediately after every task mutation                              |
| Widget doesn't update when app is closed           | No lifecycle hook on app background                                                            | `onStop` (Android) / `.scenePhase == .background` (iOS) now push a reload                   |
| Pressing + and adding a task doesn't update widget | No call to `GlanceAppWidgetManager.update` / `WidgetCenter.reloadTimelines` from the save path | Added to ViewModels and sheet `.onDisappear`                                                |
| Widget updates are irregular / unreliable          | No background worker as fallback                                                               | WorkManager `PeriodicWorkRequest` (Android) / WidgetKit timeline entries every 15 min (iOS) |

## Files — where they go

### Android (`android-compose/`)

```
app/src/main/java/com/ohmz/tday/
├── widget/
│   ├── WidgetUpdateManager.kt        ← central refresh trigger (Hilt Singleton)
│   ├── WidgetStateKeys.kt            ← shared DataStore Preferences keys
│   ├── TodayGlanceWidget.kt          ← Glance widget + receiver
│   ├── FloaterGlanceWidget.kt        ← mirror of TodayGlanceWidget for floaters
│   └── WidgetIntegrationGuide.kt     ← integration comments + manifest XML reference
└── sync/
    └── WidgetSyncWorker.kt           ← WorkManager periodic + on-demand worker
```

**res/xml/today_widget_info.xml** — set `updatePeriodMillis="0"` (the app owns all updates).

### iOS (`ios-swiftUI/`)

```
Tday/Widget/
├── WidgetReloadHelper.swift          ← main-app side: writes App Group + calls reloadAllTimelines
└── WidgetLifecycleIntegration.swift  ← reference: where to add calls in App/ViewModels

TdayWidget/
└── TdayWidgetProvider.swift          ← smart timeline with 15-min entries + .atEnd policy
```

## Integration checklist

### Android

- [ ] **Inject `WidgetUpdateManager`** into every ViewModel that mutates todos or floaters
- [ ] After every `repo.insert/update/delete`, call:
  ```kotlin
  widgetUpdateManager.scheduleImmediateUpdate()
  WidgetSyncWorker.runOnce(context)
  ```
- [ ] In `Application.onCreate()`, call `WidgetSyncWorker.schedule(context)` once
- [ ] In `MainActivity.onCreate()`, add a `ProcessLifecycleOwner` observer that calls
  `widgetUpdateManager.scheduleImmediateUpdate()` in `onStop()`
- [ ] Set `android:updatePeriodMillis="0"` in both `today_widget_info.xml` and
  `floater_widget_info.xml`
- [ ] Implement `getTodayTasksForWidget()` in your `TodoRepository` — return a
  `List<WidgetTaskItem>` (lightweight, no joins or heavy fields needed)
- [ ] Add `@HiltWorker` + WorkManager Hilt integration if not already present:
  ```kotlin
  // In your Application class
  @HiltAndroidApp
  class TdayApplication : Application(), Configuration.Provider {
      @Inject lateinit var workerFactory: HiltWorkerFactory
      override val workManagerConfiguration
          get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
  }
  ```

### iOS

- [ ] **Enable App Groups** on both the main app target and the TdayWidget extension
  (same identifier, e.g. `group.com.ohmz.tday`)
- [ ] Set `kTdayAppGroupID` in `WidgetReloadHelper.swift` to your actual group ID
- [ ] Call `WidgetReloadHelper.shared.reloadTodayWidget()` from `ScheduledTaskViewModel`
  after add / edit / complete / delete
- [ ] Call `WidgetReloadHelper.shared.reloadFloaterWidget()` from `FloaterViewModel`
  after add / edit / complete / delete
- [ ] Add `.onChange(of: scenePhase)` in your `@main` App struct (see
  `WidgetLifecycleIntegration.swift`)
- [ ] Implement `TaskSnapshotLoader.loadTodayTasks()` and `loadFloaterTasks()` to
  actually query SwiftData
- [ ] Verify `WidgetKind.today` / `WidgetKind.floater` strings match the `kind:` parameter
  in your `Widget` struct declarations in the TdayWidget extension
- [ ] For App Intents (interactive widget buttons), add
  `WidgetCenter.shared.reloadAllTimelines()` at the end of `perform()`

## Inline completion (widgets v2)

### Android

Tapping a task's leading dot in the Today/Floater widgets completes it inline:

1. The row's dot carries `actionRunCallback<CompleteTodayTaskAction|CompleteFloaterTaskAction>`
   with the cached record id (`feature/widget/CompleteTaskAction.kt`).
2. The callback resolves `WidgetCompleteTaskSubmitter` via `WidgetEntryPoint`,
   looks the record up in the offline cache, and calls the same
   `TodoRepository.completeTodo/completeFloater` the in-app checkbox uses —
   optimistic cache write, queued COMPLETE_* mutation, sync in Server Mode.
3. The repository's own widget refresh pushes the new state, so the row
   disappears immediately. Mis-taps are reversed from the app's Completed
   screen (no transient in-widget undo yet).

### iOS

The widget extension runs in its own process with no cache or SwiftData
access, so completion is a two-phase handoff through the app group:

1. Each row's leading ring is a `Button(intent: CompleteWidgetTaskIntent(...))`
   (defined inside `TdayWidget/TodayTasksWidget.swift`, the only file the
   widget target compiles). `perform()` appends a `{kind, id}` descriptor to
   the `tday.widget.pendingCompletions` app-group key
   (`WidgetPendingCompletionStore`) and reloads that widget's timeline.
2. Both timeline providers filter out snapshot rows whose id is queued, so
   the row disappears immediately even though nothing has really completed.
3. When the app activates (cold launch via the bootstrap `onChange`, warm
   foreground via the scenePhase `.active` block in `AppRootView`),
   `TodoRepository.drainWidgetCompletions()` empties the queue
   (`WidgetPendingCompletionQueue` — key and entry shape must stay in
   lockstep with the widget-side store), resolves each id against the
   offline cache, and rides the normal `completeTodo/completeFloater` path —
   optimistic cache write, queued COMPLETE_* mutation, sync in Server Mode,
   fresh widget snapshot. Mis-taps are reversed from the app's Completed
   screen, same as Android.

## How the refresh cascade works (after pressing +)

```
User taps + → fills form → taps Save
        │
        ▼
ViewModel.addTask()
        │
        ├─[Android]──▶  repo.insert(todo)
        │               widgetUpdateManager.scheduleImmediateUpdate()
        │                 └─ GlanceAppWidgetManager.update(id) ──▶ widget recomposes ~instantly
        │               WidgetSyncWorker.runOnce()
        │                 └─ WorkManager OneTime job (fallback if process dies)
        │
        └─[iOS]──────▶  repository.insert(task)
                        WidgetReloadHelper.shared.reloadTodayWidget()
                          ├─ TaskSnapshotLoader → UserDefaults(App Group)
                          └─ WidgetCenter.reloadTimelines(ofKind: "TdayTodayWidget")
                               └─ WidgetKit calls getTimeline → new entries → widget updates
```

```
User closes app (home button / swipe away)
        │
        ├─[Android]──▶  ProcessLifecycleOwner.onStop()
        │               widgetUpdateManager.scheduleImmediateUpdate()
        │
        └─[iOS]──────▶  scenePhase == .background
                        WidgetReloadHelper.shared.reloadAfterTaskChange()
```

## Background refresh cadence

- Android WorkManager fires every **15 minutes** (platform minimum for PeriodicWork)
- iOS WidgetKit timeline has entries every **15 minutes**, policy `.atEnd`
  (so getTimeline is called fresh every ~1 hour at the latest, but mutations
  skip straight to a reload via `reloadAllTimelines`)
- Both platforms also refresh **immediately** on every task mutation and on
  app background — this is the most important path for perceived freshness
