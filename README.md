# Tday Widget Sync Improvements

## What was wrong / what this fixes

| Symptom | Root cause | Fix |
|---|---|---|
| Widget stays stale for 30+ min after adding a task | Relying on `updatePeriodMillis` (Android min 30 min) or WidgetKit's passive refresh | Trigger explicit refresh immediately after every task mutation |
| Widget doesn't update when app is closed | No lifecycle hook on app background | `onStop` (Android) / `.scenePhase == .background` (iOS) now push a reload |
| Pressing + and adding a task doesn't update widget | No call to `GlanceAppWidgetManager.update` / `WidgetCenter.reloadTimelines` from the save path | Added to ViewModels and sheet `.onDisappear` |
| Widget updates are irregular / unreliable | No background worker as fallback | WorkManager `PeriodicWorkRequest` (Android) / WidgetKit timeline entries every 15 min (iOS) |

---

## Files — where they go

### Android (`android-compose/`)

```
app/src/main/java/com/ohmz/tday/
├── widget/
│   ├── WidgetUpdateManager.kt        ← NEW: central refresh trigger (Hilt Singleton)
│   ├── WidgetStateKeys.kt            ← NEW: shared DataStore Preferences keys
│   ├── TodayGlanceWidget.kt          ← REPLACE: improved Glance widget + receiver
│   ├── FloaterGlanceWidget.kt        ← CREATE: mirror of TodayGlanceWidget for floaters
│   └── WidgetIntegrationGuide.kt     ← REFERENCE: integration comments + manifest XML
└── sync/
    └── WidgetSyncWorker.kt           ← NEW: WorkManager periodic + on-demand worker
```

**res/xml/today_widget_info.xml** — set `updatePeriodMillis="0"` (we own all updates).

### iOS (`ios-swiftUI/`)

```
Tday/Widget/
├── WidgetReloadHelper.swift          ← NEW: main-app side, writes App Group + calls reloadAllTimelines
└── WidgetLifecycleIntegration.swift  ← REFERENCE: shows where to add calls in App/ViewModels

TdayWidget/
└── TdayWidgetProvider.swift          ← REPLACE: smart timeline with 15-min entries + .atEnd policy
```

---

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
- [ ] Add `.onChange(of: scenePhase)` in your `@main` App struct (see `WidgetLifecycleIntegration.swift`)
- [ ] Implement `TaskSnapshotLoader.loadTodayTasks()` and `loadFloaterTasks()` to
  actually query SwiftData
- [ ] Verify `WidgetKind.today` / `WidgetKind.floater` strings match the `kind:` parameter
  in your `Widget` struct declarations in the TdayWidget extension
- [ ] For App Intents (interactive widget buttons), add
  `WidgetCenter.shared.reloadAllTimelines()` at the end of `perform()`

---

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

---

## Background refresh cadence

- Android WorkManager fires every **15 minutes** (platform minimum for PeriodicWork)
- iOS WidgetKit timeline has entries every **15 minutes**, policy `.atEnd`
  (so getTimeline is called fresh every ~1 hour at the latest, but mutations
  skip straight to a reload via `reloadAllTimelines`)
- Both platforms also refresh **immediately** on every task mutation and on
  app background — this is the most important path for perceived freshness
