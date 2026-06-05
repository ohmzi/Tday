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
| Home (root dock) | `house` | `Home` | `ic_lucide_house` | `NavHouse` |
| Task edit | `square-pen` | `SquarePen` | `ic_lucide_square_pen` | `ActionEdit` |
| Task delete | `trash` | `Trash` | `ic_lucide_trash` | `ActionDelete` |

These tile/screen icons are also reused as the faint full-screen background watermark on each corresponding screen, and list icons are resolved per list from the shared icon registry (`lib/listIcons.ts` on web, `TdayListIcons.kt` on Android, `todoListSymbolName` on iOS) — keep those registries Lucide-based too.
