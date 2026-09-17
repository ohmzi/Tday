# Icons — One Lucide Source Across Platforms

T'Day uses **[Lucide](https://lucide.dev)** as the single icon language for web, Android, and iOS. The same glyph must look the same on every platform. Do **not** reach for a platform's built-in icon set (Material Icons / `Icons.*`, SF Symbols / `Image(systemName:)`) for any icon a user sees in a shared product surface — mirror the Lucide glyph instead.

Lucide is ISC-licensed (free for commercial use). Glyph geometry: `viewBox="0 0 24 24"`, `fill="none"`, `stroke="currentColor"`, `stroke-width="2"`, round line caps and joins.

## Per-platform mechanics

### Web (`tday-web/`)
Use the `lucide-react` component directly — this is the reference implementation other platforms match.

```tsx
import { SquarePen } from "lucide-react";
<SquarePen className="h-4 w-4" strokeWidth={1.8} />
```

### Android (`android-compose/`)
Embed each Lucide glyph as a **vector drawable** under `app/src/main/res/drawable/`, named `ic_lucide_<glyph>.xml` (e.g. `ic_lucide_square_pen.xml`). Convert every SVG element to `<path>` (Android vector drawables only support paths — turn `<circle>`/`<rect>`/`<line>`/`<polyline>` into path data). Stroke each path with `strokeWidth="2"`, `strokeLineCap="round"`, `strokeLineJoin="round"`, and a placeholder `strokeColor` (the render-site tint overrides it).

```kotlin
Icon(
    painter = painterResource(R.drawable.ic_lucide_square_pen),
    contentDescription = stringResource(R.string.action_edit_task),
    tint = Color.White,
    modifier = Modifier.size(22.dp),
)
```

### iOS (`ios-swiftUI/`)
Add each glyph as a **template SVG imageset** in `Tday/Assets.xcassets/<Name>.imageset/` with the raw Lucide SVG (path-only markup is most reliable with Xcode's importer) and a `Contents.json` that sets `"preserves-vector-representation": true` and `"template-rendering-intent": "template"`. Render resizable and tint with `foregroundStyle`.

```swift
Image("ActionEdit")
    .renderingMode(.template)
    .resizable()
    .scaledToFit()
    .frame(width: 22, height: 22)
    .foregroundStyle(.white)
```

## Adding a new icon

1. Find the glyph on lucide.dev and copy its SVG (or read `tday-web/node_modules/lucide-react/dist/esm/icons/<glyph>.js` for the exact path nodes).
2. Web: import the `lucide-react` component.
3. Android: add `ic_lucide_<glyph>.xml` (paths only), render via `painterResource`.
4. iOS: add a `<Name>.imageset` template SVG, render via `Image("<Name>")` + `.renderingMode(.template)`.
5. Keep the three in sync — same glyph everywhere.

## Current shared glyphs

| Purpose | Lucide glyph | Web | Android drawable | iOS imageset |
|---|---|---|---|---|
| Scheduled tile | `calendar-clock` | `CalendarClock` | `ic_lucide_calendar_clock` | `TileScheduled` |
| Priority tile | `flag` | `Flag` | `ic_lucide_flag` | `TilePriority` |
| Overdue tile | `clock-3` | `Clock3` | `ic_lucide_clock_3` | `TileOverdue` |
| All tile | `layers` | `Layers` | `ic_lucide_layers` | `TileAll` |
| Complete tile | `circle-check-big` | `CheckCircle` | `ic_lucide_circle_check_big` | `TileComplete` |
| Calendar tile | `calendar-1` | `Calendar1` | `ic_lucide_calendar_1` | `TileCalendar` |
| Search | `search` | `Search` | `ic_lucide_search` | `NavSearch` |
| New list | `list-plus` | `ListPlus` | `ic_lucide_list_plus` | `NavListPlus` |
| Settings / more | `ellipsis` | `Ellipsis` | `ic_lucide_ellipsis` | `NavEllipsis` |
| Close / clear | `x` | `X` | `ic_lucide_x` | `NavClose` |
| Undo (toast action) | `undo-2` | `Undo2` | `ic_lucide_undo_2` | `ActionUndo` |
| Scheduled (root dock) | `house` | `Home` | `ic_lucide_house` | `NavHouse` |
| Task edit | `square-pen` | `SquarePen` | `ic_lucide_square_pen` | `ActionEdit` |
| Task copy | `copy` | `Copy` | `ic_lucide_copy` | `ActionCopy` |
| Task delete | `trash` | `Trash` | `ic_lucide_trash` | `ActionDelete` |
| Notes: bold | `bold` | `Bold` | `ic_lucide_bold` | `LucideBold` |
| Notes: italic | `italic` | `Italic` | `ic_lucide_italic` | `LucideItalic` |
| Notes: underline | `underline` | `Underline` | `ic_lucide_underline` | `LucideUnderline` |
| Notes: strikethrough | `strikethrough` | `Strikethrough` | `ic_lucide_strikethrough` | `LucideStrikethrough` |
| Notes: bulleted list | `list` | `List` | `ic_lucide_list` | `LucideList` |
| Notes: numbered list | `list-ordered` | `ListOrdered` | `ic_lucide_list_ordered` | `LucideListOrdered` |

These tile/screen icons are also reused as the faint full-screen background watermark on each corresponding screen, and list icons are resolved per list from the shared icon registry (`lib/listIcons.ts` on web, `TdayListIcons.kt` on Android, `todoListSymbolName` on iOS) — keep those registries Lucide-based too.

### List icons inferred from a list's name

A list whose owner never picked an icon takes one from its name — "Groceries" a cart, "Gym" a dumbbell. The keyword table is `ListIconInference` in `shared/src/commonMain/kotlin/com/ohmz/tday/shared/listicon/`, and four rules keep it honest:

- **It only fills an unset icon.** A chosen `iconKey` always wins, including when the choice is the default `inbox` — which is why the create sheets post `null` for an untouched picker instead of the glyph they were previewing, and why the *edit* sheets, which seeded from the list and posted the seed straight back, now send nothing unless the picker was tapped: renaming a list must not write a default over an icon nobody ever chose. Never infer on `iconKey == "inbox"`; **after the v0.7.28 cutover below** that value means "the user chose inbox". An edit sheet's "nothing changed" check must compare against the STORED value, not the normalised one, or a deliberate tap on Inbox for a list that had no icon reads as no change and is thrown away.
- **It is display-only.** The inferred key is never written back through a list repository or the device-local icon shadow. A guess that persists stops being a guess, and the next rename would inherit the old one.
- **Unsure returns `null`, and `null` is not `inbox`.** Every client's resolver falls back to the inbox glyph for null, blank and unknown keys alike, silently — so a matcher that answered "inbox" would erase the only distinction the rules above rest on. Two different keys matching in one title is also unsure.
- **English only, deliberately.** Stated in the KDoc because no gate can catch it: the i18n parity guardrail compares key sets and would pass nine locales of untranslated English. The path to ten locales is a `listIcons` namespace in `tday-web/messages/<locale>.json` exported into `shared` the way `SummaryStringBundlesGenerated.kt` already is.

#### The v0.7.28 cutover: why every existing list had to be cleared

The rule above — `iconKey` set means chosen — describes a distinction the database did not contain when the feature was written. Until v0.7.28 **nothing ever wrote `NULL`**. Every create sheet on all three clients seeded its picker with the default and posted whatever it held, so a list made by someone who never looked at the icon row stored `'inbox'`, and so did a list made by someone who deliberately tapped Inbox. The two are byte-identical in that column; the difference was never recorded, because before v0.7.28 nothing depended on it.

Shipping the resolvers alone would therefore have shipped the feature switched **off for every account that already existed**. Every list says `'inbox'`, every resolver reads that as a choice and stops, and the user opens their "Groceries" list after updating to find the same inbox as before — only lists created *after* the update could ever be guessed for. So there is a one-time backfill, in two halves, because the seeded default was stored in two places:

- **`V29__clear_seeded_list_icons.sql`** sets `"iconKey" = NULL WHERE "iconKey" = 'inbox'` on `project` and `floaterproject`. No `createdAt` cutoff: Flyway runs inside `DatabaseConfig.init()` before the server binds a port, so every row present at that moment is by definition pre-cutover, and a timestamp comparison could not identify one extra row while adding a clock question. `floaterproject` is Exposed-owned and may not exist on a first-ever boot, so its `UPDATE` sits behind a `to_regclass` guard (`UPDATE` has no `IF EXISTS`), the same discipline V27 uses.
- **`SecureConfigStore.pruneSeededListIconShadow()`** on Android, once per install, clearing the same value out of the device-local `list_icon_map`. This half is easy to miss and fatal to omit: `SyncManager` passes that shadow to `mapListDto` as `iconFallback`, so `dto.iconKey ?: iconFallback` resurrects the device's remembered `'inbox'` over the column the migration just cleared — the SQL would report rows updated and Android alone would still show every list wearing an inbox, for a reason nothing on the server could explain. iOS has the same store (`SecureStore.saveListIcon`) but has never wired it as a fallback — `mapListDTO`'s `iconFallback` is nil at every call site — and web has no such shadow, so the migration is the whole story on those two. The decision is a pure function (`prunedSeededListIconShadow`) with a test, because `SecureConfigStore` is `EncryptedSharedPreferences` over a `Context` and this module has no Robolectric.

**What the cutover costs, stated rather than buried.** Clearing `'inbox'` wholesale also clears the minority who chose Inbox on purpose, and some of their lists will now take a name-derived glyph. That is the wrong answer for them, and it is the cheaper wrong answer: which rows they are is *unknowable*, not merely expensive — the information was never written down, and any rule that tried to guess would be a second detective system applied to the one decision this feature promises never to override. It is also recoverable in two taps that then stick forever, because a post-cutover tap on Inbox stores `'inbox'` against clients that no longer post the default. Not doing it is recoverable by nobody: there is no "unset my icon" control anywhere — `ListService.update` and `FloaterListService.update` both write `iconKey?.let { … }`, so `null` in a PATCH means "leave it alone" — and a list frozen on the seeded default would stay frozen for the life of the account.

**What the cutover does not cover.** An *old* client still installed after the server updates keeps posting `'inbox'` on create, and those lists stay frozen until it updates. The server cannot fix that by rewriting an incoming `'inbox'` to `NULL`, because that would destroy a *new* client's deliberate Inbox. Lists created through MCP were never affected either way: `tday_create_list` has never sent an `iconKey`, so they have always been `NULL` and have always been inferable.

Every key the table can emit must exist in all three icon registries — and must not resolve to the inbox glyph in any of them, which would paint the picture the inference was written to replace. `ListIconInferenceKeyParityTest` (Android unit tests) asserts both halves — emittable keys ⊆ Android's set, and Android's set == web's == iOS's — because a divergence would otherwise ship as a list quietly wearing an inbox on one platform, with no error anywhere.

**Android** consumes the Kotlin table directly; `:shared` is on its classpath.

**iOS** reads a committed artifact, `Tday/UI/Theme/TdayListIconInferenceGenerated.swift`, written by `./gradlew :shared:exportListIconTable` with `verifyListIconTable` (`--check`) as the CI drift gate — the third codegen in `shared/build.gradle.kts`, the same shape as the guide and motion pairs beside it. It does **not** call the Kotlin through `TdayShared`: that framework is declared for the iOS targets but has never been linked (`TdayApp.xcodeproj/project.pbxproj` names no Kotlin and `Package.swift` depends on none), so linking it would be a build-system change verified by a toolchain this repo does not have, to deliver one dictionary. The table is generated; the matching *rule* is a hand-written twin in `TdayListIconInference.swift`, the way `floaterRestingTier` twins `FloaterResting.tierFor` — fifteen lines that want the platform's own string handling against two hundred words that do not. `tday-web/tests/guardrails/list-icon-inference-parity.test.ts` asserts the generated table still says what the Kotlin one says, that every emittable key is in **both** iOS icon lists (`tdayLucideListAssetTable` *and* `todoListSettingsIconKeys`, which the settings sheet normalises against), and that no second keyword list has appeared in the Swift sources.

**Web** reads a committed artifact too — `src/generated/list-icon-table.ts`, from the same exporter — and for a plainer reason than iOS's: `tday-web` runs no Kotlin at all and never could, so this is the only route the table has, exactly as `src/generated/motion-tokens.ts` beside it is. Its rule twin is `src/lib/listIconInference.ts`, and `src/lib/listIcons.ts` holds `resolveListIconKey`, the one place the chosen-beats-inferred precedence is written; every display site calls `getListIconForList` so no screen can accidentally opt out of it. The web half of `tday-web/tests/guardrails/list-icon-inference-parity.test.ts` asks the same three questions of the generated file, plus one only web can ask cheaply: that no file under `src` other than the generated table spells a table keyword.

### Settings rows

Every tappable Settings row leads with a glyph in a 22px slot (20px glyph, 14px gap), tinted with the accent blue (`text-accent` on web, `colorScheme.secondary` on Android, `colors.secondary` on iOS) — never the heavier `primary`. Destructive rows inherit the row's own error colour instead. The icons are decorative (`aria-hidden` / `contentDescription = null` / `.accessibilityHidden(true)`) because the row label carries the meaning. Non-tappable fact rows sitting inside an iconed card (Role, Server version) reserve an equal-size empty slot so their labels stay aligned. A dash means the row does not exist on that platform.

| Purpose | Lucide glyph | Web | Android drawable | iOS imageset |
|---|---|---|---|---|
| Name | `user` | `User` | `ic_lucide_user` | `LucideUser` |
| Username | `at-sign` | `AtSign` | `ic_lucide_at_sign` | `LucideAtSign` |
| Password | `lock` | `Lock` | `ic_lucide_lock` | `LucideLock` |
| Security questions | `shield-question` | `ShieldQuestion` | `ic_lucide_shield_question` | `LucideShieldQuestion` |
| Role | — (empty slot) | — | (empty slot) | (empty slot) |
| Reduce motion | `activity` | — | `ic_lucide_activity` | `LucideActivity` |
| Default reminder | `bell` | — | `ic_lucide_bell` | `LucideBell` |
| Day Ahead digest | `bell-ring` | — | `ic_lucide_bell_ring` | `LucideBellRing` |
| Quiet hours | `moon` | — | `ic_lucide_moon` | `LucideMoon` |
| Push delivery app | `cloud` | — | `ic_lucide_cloud` | — |
| App language | `languages` | `Languages` | `ic_lucide_languages` | `LucideLanguages` |
| AI task summary | `sparkles` | `Sparkles` | `ic_lucide_sparkles` | `LucideSparkles` |
| Resting floaters | `waves` | `Waves` | `ic_lucide_waves` | `LucideWaves` |
| Push notifications | `bell-ring` | `BellRing` | — | — |
| Sound | `volume-2` | `Volume2` | — | — |
| Vibration | `vibrate` | `Vibrate` | — | — |
| Device calendar sync | `calendar` | — | `ic_lucide_calendar` | `LucideCalendar` |
| Screenshot protection | `eye-off` | — | `ic_lucide_eye_off` | — |
| App lock / Face ID | `shield` | — | `ic_lucide_shield` | `LucideShield` |
| Download my data | `download` | — | — | `LucideDownload` |
| Import | `upload` | — | — | `LucideUpload` |
| Encrypt this workspace | `key-round` | `KeyRound` | — | — |
| API key | `key-round` | `KeyRound` | — | — |
| App version / Release | `info` | — | `ic_lucide_info` | `LucideInfo` |
| Server version | — (empty slot) | — | (empty slot) | (empty slot) |
| How-To & Tips | `circle-help` | `CircleHelp` | `ic_lucide_circle_help` | `LucideCircleHelp` |
| Admin | `users-round` | `UsersRound` | — | — |
| Reset cached app data | `refresh-cw` | `RefreshCw` | — | — |
| Leave local workspace | `log-out` | `LogOut` | — | — |
| Delete local data | `trash-2` | `Trash2` | — | — |
| Sign out | `log-out` | `LogOut` | `ic_lucide_log_out` | `LucideLogOut` |

Section headings, the theme segmented control, sync-status blocks, the web Calendar-feed and Webhooks form cards, and filled buttons that already carry their own icon stay bare. The native glyphs above are listed in `tday-web/tests/fixtures/settings-icons.json`; the `settings-icons` coverage test fails if an Android drawable or iOS imageset is missing, so update the fixture when a row's glyph changes.
