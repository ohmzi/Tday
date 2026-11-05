# Floater List Main-Screen Visibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a per-floater-list "Show on main screen" setting (default on) that hides the list's tasks from the Floater main feed, Floater widget and Floater summary on web, Android and iOS.

**Architecture:** One boolean `showOnMainScreen` on the floater list, stored in Postgres and every client cache, synced like `reusable`. One rule lives in shared Kotlin, called by backend and Android and mirrored on web and iOS under a parity guardrail. The API keeps returning every floater, and each surface filters on its own.

**Tech Stack:** Kotlin Multiplatform, Ktor, Exposed, Flyway, React, TypeScript, Vitest, Compose, Hilt, Room, SwiftUI, SwiftData.

**Spec:** `docs/superpowers/specs/2026-11-04-floater-list-main-screen-visibility-design.md`

## Global Constraints

- Branch: `feature/floater-list-main-screen-visibility`, based on `origin/develop`. PR targets `develop`.
- Field name everywhere: `showOnMainScreen`. Absent or null reads as `true`, never `false`.
- Filter only: feed rows, the all-floaters widget, the no-list Floater summary.
- Never filter: search, list tile counts, list screens, per-list widgets, a list's own summary, Completed, `pendingFloaterCount` and the native empty-state answers.
- No new empty-state copy, no tile marker, no new MCP parameter, no new telemetry.
- Only the list owner can change the value.
- Toggle label is "Show on main screen". Hint is "Off keeps its tasks inside this list only."
- Commits use `ohmzi <6551272+ohmzi@users.noreply.github.com>`, have short human messages, and carry no AI attribution trailers (user memory overrides the default).
- iOS can't be built on this Linux host. Parse-check here, real verification is macOS CI.

## Review Focus

1. Upgrade with existing cached lists and no stored value: every list stays visible on all clients. Pinned by the Task 3, 4 and 6 default tests.
2. Toggle off while offline, then sync: the pending change replays, and a pull before replay doesn't flip it back. Pinned by the Task 4 and 6 mutation tests.
3. Tapping a hidden task in main-screen search: it opens the list and focuses the task, rather than doing nothing. Pinned by the Task 5 and 7 tests.
4. Member of a shared list hidden by its owner: the member's Floater summary leaves those tasks out too. Pinned by the Task 2 test.
5. Toggling with a widget on the home screen: the widget changes without waiting for a task edit. Pinned by the Task 5 and 7 tests.

---

### Task 1: Shared contract and rule

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ohmz/tday/shared/floater/FloaterMainScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/ohmz/tday/shared/model/ListModels.kt:106-156`
- Test: `shared/src/commonTest/kotlin/com/ohmz/tday/shared/floater/FloaterMainScreenTest.kt`

**Interfaces:**
- Produces: `object FloaterMainScreen { fun isShown(listId: String?, hiddenListIds: Set<String>): Boolean }`
- Produces: `FloaterListDto.showOnMainScreen: Boolean = true`, `CreateFloaterListRequest.showOnMainScreen: Boolean = true`, `UpdateFloaterListRequest.showOnMainScreen: Boolean? = null`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun noListIsShown() = assertTrue(FloaterMainScreen.isShown(null, setOf("a")))
@Test fun unknownListIsShown() = assertTrue(FloaterMainScreen.isShown("b", setOf("a")))
@Test fun hiddenListIsNotShown() = assertFalse(FloaterMainScreen.isShown("a", setOf("a")))
@Test fun dtoWithoutFieldDecodesAsShown() {
    val dto = Json { ignoreUnknownKeys = true }.decodeFromString<FloaterListDto>("""{"id":"1","name":"x"}""")
    assertTrue(dto.showOnMainScreen)
}
```

- [ ] **Step 2: Run** `./gradlew :shared:jvmTest --tests '*FloaterMainScreenTest*'`. Expected: FAIL, unresolved `FloaterMainScreen`.
- [ ] **Step 3: Implement the rule and add the three DTO fields.** Check that `explicitNulls`/`encodeDefaults` settings on the API `Json` send `showOnMainScreen` on create.
- [ ] **Step 4: Run the same command.** Expected: PASS.
- [ ] **Step 5: Commit** `Add floater list main-screen visibility contract`.

### Task 2: Backend storage, API and summary

**Files:**
- Create: `tday-backend/src/main/resources/db/migration/V33__floater_list_show_on_main_screen.sql`
- Modify: `tday-backend/src/main/kotlin/com/ohmz/tday/db/tables/FloaterLists.kt:15`
- Modify: `.../services/FloaterListService.kt` (`create` :154, `update` :185, row mapper :300)
- Modify: `.../models/request/FloaterListRequests.kt`, `.../models/response/FloaterListResponses.kt`
- Modify: `.../routes/FloaterListRoutes.kt:46,59`
- Modify: `.../routes/TodoRoutes.kt:354,525-540`
- Test: `tday-backend/src/test/kotlin/com/ohmz/tday/routes/FloaterListVisibilityTest.kt`

**Interfaces:**
- Consumes: `FloaterMainScreen.isShown`, DTO fields from Task 1.
- Produces: `buildSummaryTasks(..., hiddenFloaterListIds: Set<String>, ...)`.

- [ ] **Step 1: Write failing tests:** `newListDefaultsToShown`, `updateFalsePersists`, `updateWithoutFieldLeavesValue`, `nonOwnerUpdateIsForbidden`, `floaterSummarySkipsHiddenList`, `listSummaryKeepsHiddenList`, `sharedMemberSummarySkipsHiddenList`, `allFloatersRouteStillReturnsHiddenTasks`.
- [ ] **Step 2: Run** `./gradlew :tday-backend:test --tests '*FloaterListVisibilityTest*'`. Expected: FAIL.
- [ ] **Step 3: Write V33.** Follow V31 quoting and V19 guarding:

```sql
ALTER TABLE IF EXISTS floaterproject
    ADD COLUMN IF NOT EXISTS "showOnMainScreen" BOOLEAN NOT NULL DEFAULT TRUE;
```

- [ ] **Step 4: Add the Exposed column** `bool("showOnMainScreen").default(true)`, then thread the value through service, routes and mapper the same way as `reusable`. Leave `FloaterService.kt:325` on the default.
- [ ] **Step 5: In the summary route,** when the scope is FLOATER and `listId` is blank, derive `hiddenFloaterListIds` from `floaterListService.getAll(user.id)`, which covers shared lists. Apply `FloaterMainScreen.isShown` in the `SummaryScope.FLOATER` branch only when `normalizedListId == null`.
- [ ] **Step 6: Run** `./gradlew :tday-backend:test`. Expected: all PASS, including the clean-database migration path.
- [ ] **Step 7: Commit** `Store floater list main-screen visibility on the backend`.

### Task 3: Web

**Files:**
- Create: `tday-web/src/lib/floater/mainScreen.ts`
- Create: `tday-web/tests/guardrails/floater-main-screen-parity.test.ts`
- Create: `tday-web/tests/unit/floater-main-screen.test.tsx`
- Modify: `tday-web/src/schema.ts`, `src/types/index.ts`
- Modify: `src/features/floaterList/query/create-floater-list.ts`, `query/floater-list-utils.ts`
- Modify: `src/features/floaterList/component/FloaterListFormSheet.tsx`
- Modify: `src/features/floater/component/NativeFloaterTaskHomeDashboard.tsx`
- Modify: `src/lib/local/localDb.ts:99`, `localLists.ts:237,267,290`, `localFloaters.ts:256`, `localTransfer.ts:95,209`, `localSummary.ts:200`
- Modify: `tday-web/messages/*.json` (10 files)

**Interfaces:**
- Consumes: `showOnMainScreen` on list responses.
- Produces: `isFloaterShownOnMainScreen(listId: string | null | undefined, hiddenListIds: ReadonlySet<string>): boolean` and `hiddenFloaterListIds(lists: Iterable<{ id: string; showOnMainScreen?: boolean }>): Set<string>`.

- [ ] **Step 1: Write failing tests:**
  - `isFloaterShownOnMainScreen` matches the three Task 1 cases.
  - `hiddenFloaterListIds` treats a missing field as shown.
  - Feed: a hidden task has no row, and search still returns it.
  - Feed: with only hidden tasks left, no rows and no empty scene render.
  - Toggle: defaults on, posts `false` when turned off, posts on save like `reusable`.
  - Local: an old row without the field reads as `true`, `localSummary` skips hidden lists with no `listId` and keeps them with one, and an import without the field gives `true`.
  - Guardrail: reads `FloaterMainScreen.kt` and the Swift mirror and fails if `isShown` or `hiddenListIds` membership is missing. Add the Swift path in Task 7 if it doesn't exist yet.
- [ ] **Step 2: Run** `cd tday-web && npx vitest run tests/unit/floater-main-screen.test.tsx`. Expected: FAIL.
- [ ] **Step 3: Implement.** In the dashboard, keep `filteredFloaters` for search and add `feedFloaters`, sorted separately for the `FloaterGroup` rows, so `sortedFloaters` no longer feeds both. Leave `listCounts` and `pendingFloaterCount` on `floaters`. Render the rows block from `feedFloaters`, and leave the search empty-state check on the search results.
- [ ] **Step 4: Add locale keys** `showOnMainScreen` and `showOnMainScreenHint` beside `reusableList` in all 10 files.
- [ ] **Step 5: Run** `cd tday-web && npm run lint && npm run test && npm run build`. Expected: PASS, including i18n parity.
- [ ] **Step 6: Commit** `Hide floater list tasks from the web main screen`.

### Task 4: Android storage and settings

**Files:**
- Modify: `android-compose/app/src/main/java/com/ohmz/tday/compose/core/data/db/Entities.kt:79,143`, `TdayDatabase.kt:17`, `DatabaseModule.kt`, `EntityMappers.kt`
- Modify: `.../core/data/OfflineSyncModels.kt:84,145`, `core/data/cache/CacheMappers.kt:247,268,455`
- Modify: `.../core/data/list/FloaterListRepository.kt`, `core/data/sync/SyncManager.kt:722,751,1942,2002`
- Modify: `.../core/model/DomainModels.kt:152`
- Modify: `.../feature/todos/TodoListScreen.kt` (sheet params :5098, row :5290, state :2357-2364, call sites :3710,3762), `TodoListViewModel.kt:1280,1404`, `TdayApp.kt:2676`
- Modify: `android-compose/app/src/main/res/values*/strings.xml`
- Test: `android-compose/app/src/test/.../core/data/list/FloaterListVisibilitySyncTest.kt`, plus a migration test beside `StagedMutationPersistenceTest.kt`

**Interfaces:**
- Consumes: Task 1 DTO fields.
- Produces: `CachedFloaterListRecord.showOnMainScreen: Boolean = true`, and `ListSummary.showOnMainScreen: Boolean = true` on the domain model. Use the actual record and model names at the `reusable` lines above.

- [ ] **Step 1: Write failing tests:** `migrationKeepsExistingListsShown` (v14 to v15 reads `1`), `oldCacheJsonDecodesAsShown`, `offlineToggleQueuesMutationAndSurvivesPull`, `changeDetectionSeesVisibilityChange`.
- [ ] **Step 2: Run** `cd android-compose && ./gradlew :app:testDebugUnitTest --tests '*FloaterListVisibility*'`. Expected: FAIL.
- [ ] **Step 3: Add the Room column** with `@ColumnInfo(defaultValue = "1")` and a nullable mutation column, bump the version to 15, add the migration, and export the schema JSON.
- [ ] **Step 4: Thread the value** through every `reusable` site listed above. Use `?: true` for create replay, not `?: false`.
- [ ] **Step 5: Add the toggle.** Make the Reusable row composable generic, or add a sibling that shares its layout. Add strings for every locale. Show it only for floater lists, in the create and settings sheets.
- [ ] **Step 6: Run** `cd android-compose && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 7: Commit** `Store floater list main-screen visibility on Android`.

### Task 5: Android surfaces

**Files:**
- Modify: `.../feature/todos/TodoListScreen.kt` (`buildFloaterSections` call site, `openFloaterTaskHomeSearchResult`)
- Modify: `.../feature/widget/snapshot/WidgetSnapshotBuilders.kt:289-290`
- Modify: `.../core/data/todo/TodoRepository.kt:1277` (`summarizeLocally`)
- Modify: `.../core/data/list/FloaterListRepository.kt` (refresh widgets on change)
- Test: `android-compose/app/src/test/.../feature/widget/snapshot/FloaterWidgetSnapshotBuilderTest.kt`, `ListWidgetSnapshotBuilderTest.kt`, `.../feature/todos/FloaterMainScreenFeedTest.kt`

**Interfaces:**
- Consumes: `FloaterMainScreen.isShown`, `showOnMainScreen` from Task 4.

- [ ] **Step 1: Write failing tests:**
  - `floaterWidgetSkipsHiddenList` (count and rows)
  - `perListWidgetKeepsHiddenList`
  - `localFloaterSummarySkipsHiddenListWithoutListId`
  - `localFloaterSummaryKeepsHiddenListWithListId`
  - `feedSectionsSkipHiddenList`
  - `emptyAnswerStaysBasedOnAllItems`
  - `searchResultForHiddenTaskOpensList`, pulled out as a small pure function that decides between scroll and open list
  - `visibilityChangeRefreshesWidgets`
- [ ] **Step 2: Run** `cd android-compose && ./gradlew :app:testDebugUnitTest --tests '*Widget*' --tests '*FloaterMainScreen*'`. Expected: FAIL.
- [ ] **Step 3: Filter the feed** only when `isFloaterTaskHomeScreen`. Pass filtered items to `buildFloaterSections`, and leave `uiState.items`, the tile counts, search and `FeedAnswer` as they are.
- [ ] **Step 4: Filter `buildFloaterWidgetSnapshot` and `summarizeLocally`** (when `mode == FLOATER` and `listId` is blank).
- [ ] **Step 5: Route hidden search results** through `onOpenFloaterList`, with the task focused if that route supports it.
- [ ] **Step 6: Run** `cd android-compose && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`, then `cd tday-web && npm run test`, because web guardrails parse Android source. Expected: PASS.
- [ ] **Step 7: Commit** `Hide floater list tasks from Android main-screen surfaces`.

### Task 6: iOS storage and settings

**Files:**
- Modify: `ios-swiftUI/Tday/Core/Model/ApiModels.swift:469,499,599`, `DomainModels.swift:352`, `OfflineSyncModels.swift`
- Modify: `Core/Data/Database/SwiftDataModels.swift:111,126,215,237`, `Core/Data/Cache/OfflineCacheManager.swift:204,290`
- Modify: `Core/Data/Sync/CacheMappers.swift:281,316,379`, `Core/Data/Sync/SyncManager.swift:663,825,931,947,1498`
- Modify: `Core/Data/List/FloaterListRepository.swift`
- Modify: `Feature/Todos/TodoListScreen.swift:2076,2854,5681,5885,5971,5997`, `Feature/ScheduledTaskHome/ScheduledTaskHomeScreen.swift:1722`
- Modify: `Tday/Resources/Localizable.xcstrings`
- Test: `ios-swiftUI/Tests/TdayCoreTests/OfflineCacheManagerPersistenceTests.swift`, `CacheMappersDateParsingTests.swift` or a new `FloaterListVisibilityTests.swift`

**Interfaces:**
- Consumes: Task 1 JSON field.
- Produces: `CachedListRecord`-equivalent `showOnMainScreen: Bool = true` and `ListSummary.showOnMainScreen: Bool = true`. Use the real type names at the `reusable` lines.

- [ ] **Step 1: Write failing tests:** `nilStoredValueReadsAsShown`, `oldJSONDecodesAsShown`, `queuedVisibilityMutationSurvivesPull`.
- [ ] **Step 2: Add fields.** SwiftData uses `var showOnMainScreen: Bool?`, and every read uses `?? true`. Custom `init(from:)` decoders use `decodeIfPresent(...) ?? true`. Search for any remaining `reusable ?? false` copy before finishing.
- [ ] **Step 3: Add the toggle** beside the Reusable row in the shared sheet, shown where `showsReusable` is true, and add `xcstrings` entries for every locale.
- [ ] **Step 4: Parse-check** with `swiftc -parse` on each changed file. Expected: no errors. Tests run in macOS CI, so report them as not run here.
- [ ] **Step 5: Commit** `Store floater list main-screen visibility on iOS`.

### Task 7: iOS surfaces

**Files:**
- Create: `ios-swiftUI/Tday/Core/Model/FloaterMainScreen.swift`, then register it in `TdayApp.xcodeproj/project.pbxproj` for the app and widget targets if the widget compiles it
- Modify: `Feature/Todos/TodoListScreen.swift` (`.floater` section at :2709, `openFloaterTaskHomeSearchResult` :3158)
- Modify: `Core/Widget/TodayTasksWidgetSnapshotStore.swift:925`
- Test: `ios-swiftUI/Tests/TdayCoreTests/TodayTasksWidgetSnapshotStoreTests.swift`, `FloaterListVisibilityTests.swift`

**Interfaces:**
- Produces: `enum FloaterMainScreen { static func isShown(listId: String?, hiddenListIds: Set<String>) -> Bool }`, and the Swift path the Task 3 guardrail reads.

- [ ] **Step 1: Write failing tests:** the three rule cases, `floaterWidgetSkipsHiddenList`, `perListSnapshotKeepsHiddenList`, `searchResultForHiddenTaskOpensList`, `visibilityChangeWritesWidgetSnapshot`.
- [ ] **Step 2: Implement the mirror and filter the feed section** only on the floater home screen. Leave `viewModel.items`, `floaterTaskHomeAnswer`, list counts and search as they are.
- [ ] **Step 3: In `makeSnapshot`,** keep `floaterTasks` unfiltered for `perList` and add `shownFloaterTasks` for `tasks`, `taskCount` and `status`. Include the new value in the change check so a toggle isn't skipped by `hasSameContent`.
- [ ] **Step 4: Route hidden search results** to the list, using the same route `FloaterTaskHomeListCard` opens.
- [ ] **Step 5: Parse-check and run** `cd tday-web && npm run test`. Expected: PASS, including the parity guardrail. iOS is summary-server-only, so Task 2 covers it.
- [ ] **Step 6: Commit** `Hide floater list tasks from iOS main-screen surfaces`.

### Task 8: Guide and docs

**Files:**
- Modify: `shared/.../guide/GuideTopicIds.kt:32`, `GuideCatalog.kt` (after `REUSABLE_LISTS`)
- Modify: `tday-web/messages/*.json` (10 files), then the regenerated artifacts
- Modify: `docs/DATA_MODEL.md`, `docs/API_GUIDELINES.md`, `docs/ARCHITECTURE.md`, plus platform READMEs if they list list settings

**Interfaces:**
- Produces: `GuideTopicIds.HIDDEN_FLOATER_LISTS = "hidden-floater-lists"`.

- [ ] **Step 1: Add the topic:** section `ORGANIZING`, icon `eye-off`, `setOf(WEB, ANDROID, IOS)`, `sinceVersion = "0.8.15"`, `body = listOf(para(...), steps(..., 3))`. The Android and iOS glyphs already exist. Confirm web and the `guide-icons` test accept it.
- [ ] **Step 2: Add strings to all 10 locales.** The English body is:

> Keep a list out of your Floater main screen. Turn off "Show on main screen" in a list's settings and its tasks stay inside that list, and leave the main feed, the Floater widget and the Floater summary. They still appear in search, the list itself and Completed. Open tasks in a hidden list keep the "all done" scene from showing.

- [ ] **Step 3: Run** `./gradlew :shared:exportGuideContent`, then `./gradlew :shared:verifyGuideContent`. Expected: PASS with regenerated artifacts staged.
- [ ] **Step 4: Update the docs listed above.**
- [ ] **Step 5: Full check:** `./gradlew :shared:jvmTest :tday-backend:test`, `cd tday-web && npm run lint && npm run test && npm run build`, `cd android-compose && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`. Expected: all PASS.
- [ ] **Step 6: Commit** `Add the hidden floater lists guide topic and docs`.
