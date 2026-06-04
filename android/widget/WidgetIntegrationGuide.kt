package com.ohmz.tday.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Mixin / helper that any ViewModel managing tasks should call after every
 * mutation (add, edit, complete, delete, reorder).
 *
 * Inject WidgetUpdateManager directly into your ViewModels:
 *
 *   @HiltViewModel
 *   class TodayViewModel @Inject constructor(
 *       private val repo: TodoRepository,
 *       private val widgetSync: WidgetUpdateManager,
 *       @ApplicationContext private val context: Context,
 *   ) : ViewModel() {
 *
 *       fun addTask(todo: Todo) {
 *           viewModelScope.launch {
 *               repo.insert(todo)
 *               widgetSync.scheduleImmediateUpdate()      // ← instant widget refresh
 *               WidgetSyncWorker.runOnce(context)          // ← also kicks WorkManager path
 *           }
 *       }
 *
 *       fun deleteTask(id: String) {
 *           viewModelScope.launch {
 *               repo.delete(id)
 *               widgetSync.scheduleImmediateUpdate()
 *           }
 *       }
 *   }
 *
 * The two-call pattern (scheduleImmediateUpdate + WidgetSyncWorker.runOnce) is
 * intentional:
 *   • scheduleImmediateUpdate() hits Glance directly — near-instant if the process
 *     is alive.
 *   • WidgetSyncWorker.runOnce() queues a WorkManager job so the widget still
 *     refreshes if the process is killed before Glance finishes.
 */

// -----------------------------------------------------------------------
// App lifecycle — refresh widget when the app moves to background
// -----------------------------------------------------------------------
//
// In your MainActivity (or a ProcessLifecycleOwner observer registered in
// Application.onCreate):
//
//   @AndroidEntryPoint
//   class MainActivity : ComponentActivity() {
//
//       @Inject lateinit var widgetUpdateManager: WidgetUpdateManager
//
//       override fun onCreate(savedInstanceState: Bundle?) {
//           super.onCreate(savedInstanceState)
//
//           // Observe process lifecycle so the widget is fresh when the user
//           // swipes back to the home screen.
//           ProcessLifecycleOwner.get().lifecycle.addObserver(
//               object : DefaultLifecycleObserver {
//                   override fun onStop(owner: LifecycleOwner) {
//                       // App went to background — push a widget update
//                       widgetUpdateManager.scheduleImmediateUpdate()
//                   }
//               }
//           )
//
//           setContent { TdayApp() }
//       }
//   }

// -----------------------------------------------------------------------
// FloaterGlanceWidget — skeleton (mirrors TodayGlanceWidget structure)
// -----------------------------------------------------------------------
//
// Create FloaterGlanceWidget.kt alongside TodayGlanceWidget.kt using the
// same pattern. The only differences are:
//   • class FloaterWidgetReceiver : GlanceAppWidgetReceiver() with FloaterGlanceWidget
//   • loadAndPersistState reads from floaterRepository instead
//   • WidgetStateKeys.FLOATERS_JSON is used instead of TASKS_JSON
//
// Register both receivers in AndroidManifest.xml:
//
//   <receiver
//       android:name=".widget.TodayWidgetReceiver"
//       android:exported="true">
//       <intent-filter>
//           <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
//       </intent-filter>
//       <meta-data
//           android:name="android.appwidget.provider"
//           android:resource="@xml/today_widget_info" />
//   </receiver>
//
//   <receiver
//       android:name=".widget.FloaterWidgetReceiver"
//       android:exported="true">
//       <intent-filter>
//           <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
//       </intent-filter>
//       <meta-data
//           android:name="android.appwidget.provider"
//           android:resource="@xml/floater_widget_info" />
//   </receiver>

// -----------------------------------------------------------------------
// res/xml/today_widget_info.xml
// -----------------------------------------------------------------------
//
// <?xml version="1.0" encoding="utf-8"?>
// <appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
//     android:initialLayout="@layout/glance_default_loading_layout"
//     android:minWidth="180dp"
//     android:minHeight="110dp"
//     android:resizeMode="horizontal|vertical"
//     android:updatePeriodMillis="0"
//     android:widgetCategory="home_screen" />
//
// IMPORTANT: updatePeriodMillis="0" — we manage all updates ourselves via
// WorkManager + direct Glance calls. The system minimum is 30 minutes anyway,
// so relying on it produces stale widgets. Setting 0 disables the system timer
// and gives us full control.

@Suppress("unused")
private object WidgetIntegrationGuide  // documentation-only file
