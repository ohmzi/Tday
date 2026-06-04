// WidgetLifecycleIntegration.swift
// Tday — ios-swiftUI/Tday/Widget/
//
// Shows the exact integration points needed in the main Tday app.
// Nothing here is standalone — it shows WHERE to add the reload calls.

import SwiftUI
import WidgetKit

// ===========================================================================
// 1.  SCENE PHASE OBSERVER  (add to your root App or main ContentView)
// ===========================================================================
//
// When the user presses the home button or switches apps, write the latest
// task snapshot and reload the widget timeline so the home screen shows
// fresh data.
//
//   @main
//   struct TdayApp: App {
//
//       @Environment(\.scenePhase) private var scenePhase
//
//       var body: some Scene {
//           WindowGroup { RootView() }
//               .onChange(of: scenePhase) { _, newPhase in
//                   if newPhase == .background {
//                       // App is about to go to background — push fresh data now
//                       WidgetReloadHelper.shared.reloadAfterTaskChange()
//                   }
//               }
//       }
//   }

// ===========================================================================
// 2.  TASK CREATION / MUTATION  (inside your ViewModel or repository)
// ===========================================================================
//
// In your ScheduledTaskViewModel (or equivalent):
//
//   func addTask(_ task: Todo) async throws {
//       try await repository.insert(task)
//       // ↓ Tell the widget to refresh immediately
//       await WidgetReloadHelper.shared.reloadTodayWidget()
//   }
//
//   func deleteTask(id: String) async throws {
//       try await repository.delete(id: id)
//       await WidgetReloadHelper.shared.reloadTodayWidget()
//   }
//
//   func completeTask(id: String) async throws {
//       try await repository.markDone(id: id)
//       await WidgetReloadHelper.shared.reloadTodayWidget()
//   }
//
// In your FloaterViewModel:
//
//   func addFloater(_ floater: FloaterTask) async throws {
//       try await repository.insert(floater)
//       await WidgetReloadHelper.shared.reloadFloaterWidget()
//   }

// ===========================================================================
// 3.  + BUTTON  (inside whatever view presents the Add Task sheet)
// ===========================================================================
//
// If your add-task flow calls ViewModel methods, the ViewModel hook above is
// sufficient.  But if you dismiss the sheet without going through the ViewModel,
// add a direct call in the .onDisappear of the sheet:
//
//   .sheet(isPresented: $showAddTask) {
//       AddTaskView(...)
//           .onDisappear {
//               // Reload whether or not a task was actually saved.
//               // It's a no-op if the snapshot hasn't changed.
//               WidgetReloadHelper.shared.reloadAfterTaskChange()
//           }
//   }
//
// Or in your "Save" button action:
//
//   Button("Save") {
//       Task {
//           try? await viewModel.addTask(draftTask)
//           dismiss()
//           // reloadTodayWidget() is already called inside addTask above.
//       }
//   }

// ===========================================================================
// 4.  APP INTENTS  (if using App Intents for interactive widget buttons)
// ===========================================================================
//
// Any AppIntent that mutates tasks must also reload:
//
//   struct CompleteTaskIntent: AppIntent {
//       static var title: LocalizedStringResource = "Complete Task"
//
//       @Parameter(title: "Task ID") var taskID: String
//
//       func perform() async throws -> some IntentResult {
//           try await TaskRepository.shared.markDone(id: taskID)
//           // Widget intent runs in the extension process; WidgetCenter works there too
//           WidgetCenter.shared.reloadAllTimelines()
//           return .result()
//       }
//   }

// ===========================================================================
// 5.  App Group setup checklist
// ===========================================================================
//
// Without App Group sharing the extension cannot read the data written by the
// main app, so the widget always shows stale or empty data.
//
// In Xcode:
//   a. Select the main app target → Signing & Capabilities → + Capability → App Groups
//      Add: group.com.ohmz.tday  (or your bundle prefix)
//   b. Select the TdayWidget extension target → same steps, same identifier
//   c. Verify kTdayAppGroupID in WidgetReloadHelper.swift matches exactly.
//
// You can verify it works in the widget extension with:
//   let defaults = UserDefaults(suiteName: "group.com.ohmz.tday")
//   print(defaults?.string(forKey: "tday_widget_last_updated") ?? "nil")

// This file is documentation-only; it compiles but nothing is exported.
private enum _WidgetLifecycleIntegrationDocs {}
