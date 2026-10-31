# Illustrated states

Flat-scene illustrations for empty, error and status screens on web, Android and iOS.
They should all look like the same artist drew them: one palette, light from the right, soft contact shadows,
the recurring tabby cat, and a small cast of people built the same way.

## Source of truth

| File | What it holds |
|------|---------------|
| `scenes/<id>.svg` | One scene on a 400×300 board. Colours are written only as `var(--token)`. Every `id` inside starts with `<id>-`. |
| `palette.json` | The tokens, with a `light` and a `dark` value each. |
| `motion.css` | Web-only ambient loops. Scenes use bare class names (`sway`, `breathe`, `tail`, `steam`, `twinkle`…), and the generator prefixes them `il-`. |

After any change, run:

```bash
node scripts/illustrations.mjs          # regenerate every client
node scripts/illustrations.mjs --check  # CI drift gate (also run by the web test suite)
```

It writes these files, which you should never hand-edit:

- **Web:** `tday-web/src/components/illustrations/scenes.generated.ts` and
  `illustrations.generated.css`, rendered by `<Illustration id=… />`. Tokens become `--il-*`. `.dark .tday-illus`
  switches the palette.
- **Android:** `res/drawable/illus_<id>_{light,dark}.xml` (VectorDrawable), plus the `TdayIllustration` enum
  in `core/ui/TdayIllustration.kt`. `TdayIllustrationImage` picks the light or dark version from the page colour,
  because the in-app theme can differ from the system theme, so `drawable-night` can't be used.
- **iOS:** `Assets.xcassets/Illus<Id>.imageset` (light and dark SVG appearances), plus the `TdayIllustration` enum
  in `Core/UI/TdayIllustration.swift`.

Native art is static. Classes, `<text>` and stroke dashes are dropped, and the whole scene floats instead
(`TdayEmptyState`). Allowed SVG: `g`, `path`, `rect`, `circle`, `ellipse`, `line`, `polyline`, `polygon` and `use`,
plus `clipPath` from `defs`; transforms limited to `translate`, `scale` and `rotate`; and `opacity`, `fill-opacity` and
`stroke-opacity`. No gradients, no patterns, no filters. The generator fails on anything else.

## Where each scene is used

| Scene | Screen |
|-------|--------|
| `cal` | Today and Scheduled empty, calendar day with nothing due |
| `done` | Today finished (Day Done) |
| `overdue` | Overdue empty |
| `box` | All, Priority, a list, or an Anytime list with nothing in it |
| `anytime` | Anytime (floater) home empty |
| `history` | Completed empty (tasks and floaters) |
| `search` | Any search with no results |
| `lost` | Web 404 |
| `oops` | Web error boundary |
| `update` | App update required (all clients; web stale-chunk error) |
| `srv` | Server update required on mobile |
| `offline`, `sync`, `signout` | Drawn, waiting for a screen (today, offline is a toast) |

Copy stays in each platform's own strings. The art carries no text.
