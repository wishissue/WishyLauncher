# Wishy Launcher theme format, version 1

A theme is a folder (or a `.zip` of that folder) holding one `theme.json` and some image and font files. It contains data only. The launcher never runs code from a theme: there are no scripts, no expressions, no plugins, and no shader code.

The machine-readable definition is [`schema/wishy-theme.schema.v1.json`](schema/wishy-theme.schema.v1.json) (JSON Schema 2020-12). This page explains it. If the two ever disagree, the launcher's validator and the JSON Schema win.

> Status: the schema is checked against its meta-schema and against a few accept/reject examples. The Kotlin loader and renderer are written (Milestone 3) but have not been compiled or run by me, so details may change before the first release. `schemaVersion` will be bumped on any incompatible change.

## 1. Folder layout

```
my-theme/
  theme.json          required
  preview.png         optional, shown in the theme picker (max 1280x720)
  CREDITS.md          required if you use any artwork or fonts you did not make yourself
  assets/
    wallpaper.jpg
    fonts/pixel.ttf
```

Rules for every file:

- Allowed extensions: `png`, `jpg`, `jpeg`, `webp`, `ttf`, `otf`, plus `theme.json` and `CREDITS.md`. SVG is not allowed.
- Paths are relative, use `/`, contain only `A-Z a-z 0-9 _ . -`, and may not contain `..`, a leading `/`, a backslash, or a symlink.
- Limits (the launcher enforces these; a theme that exceeds them is rejected whole):

| Limit | Value |
|---|---|
| `theme.json` size | 256 KB |
| Nodes in a screen tree | 400 |
| Tree depth | 12 |
| Files in a theme | 200 |
| One image | 5 MB, at most 4096 x 4096 px |
| One font | 2 MB |
| Whole theme, unpacked | 40 MB |
| Zip compression ratio | 100:1 at most (zip-bomb guard) |

## 2. Top-level fields

| Field | Required | Meaning |
|---|---|---|
| `schemaVersion` | yes | Always `1` for this document. |
| `id` | yes | Lowercase letters, digits and hyphens, 2 to 40 characters. Must match the repository manifest entry. |
| `name`, `version`, `author` | yes | Shown in the picker. `version` is `MAJOR.MINOR.PATCH`. |
| `description` | no | Up to 400 characters. |
| `license` | yes | SPDX identifier for the theme's own files, for example `GPL-3.0-only`, `CC0-1.0`. |
| `minLauncherVersion` | no | Launcher `versionCode` needed. |
| `preview` | no | Path to a preview image. |
| `credits` | no | One entry per third-party file: `file`, `author`, `license`, optional `title`, `source`, `notes`. Shown in Settings under "Theme credits and licenses". |
| `designHeight` | no | The canvas height your numbers are written for. Default `1080`. |
| `colorSchemes` | yes | `light` and/or `dark` colour tables. |
| `defaultColorScheme` | no | `light`, `dark` or `system` (default). A theme with only one scheme always uses it. |
| `fonts`, `textStyles` | no | Font files and named text styles. |
| `background` | no | Colour, image, dimming and a built-in overlay effect. |
| `cardStyles` | no | Named styles for app cards. |
| `motion` | no | Animation timing and easing. |
| `screens.home` | yes | The layout tree for the home screen. |
| `overrides` | no | Changes applied on certain screen shapes, colour schemes or when reduced motion is on. |

Settings, dialogs and the recovery screen are not themeable. They use the launcher's own layout, with your colour scheme and fonts applied. This is deliberate: a broken theme must never lock someone out of Settings.

## 3. Units

- **Lengths** are a number (design units) or a string like `"12%"`. Design units are scaled by `actual screen height / designHeight`, so one theme looks the same at 720p, 1080p and 4K. The user's UI scale setting multiplies on top. A percentage is a fraction of the parent's matching dimension (`x` and `w` use the parent's width, `y` and `h` its height).
- **Colours** are `#RRGGBB` or `#AARRGGBB`, or a token: `@primary`, `@background`, `@surface`, `@surfaceVariant`, `@onSurface`, `@onSurfaceMuted`, `@onPrimary`, `@accent`, `@outline`, `@scrim`, `@custom.yourName`. A token may end with `/NN` to set alpha as a percentage, for example `@primary/40`.
- **Easing**: `linear`, `standard`, `decelerate`, `accelerate`, or `steps:N` (N from 1 to 16) for stepped, retro-style movement.

## 4. Colour schemes

```json
"colorSchemes": {
  "light": { "background": "#E6F1FF", "surface": "#FFFFFF", "onSurface": "#0B1B33",
             "primary": "#0166FF", "onPrimary": "#FFFFFF" },
  "dark":  { "background": "#080B12", "surface": "#111725", "onSurface": "#F2F6FF",
             "primary": "#66A3FF", "onPrimary": "#001B3E" }
}
```

`background`, `surface`, `onSurface`, `primary`, `onPrimary` are required in every scheme. The rest are optional, and `custom` holds up to 16 extra named colours.

## 5. Fonts and text styles

```json
"fonts": { "pixel": { "file": "assets/fonts/pixel.ttf", "pixel": true } },
"textStyles": {
  "title": { "font": "pixel", "size": 36, "color": "@onSurface", "uppercase": true, "letterSpacing": 2 },
  "caption": { "size": 22, "color": "@onSurfaceMuted", "maxLines": 1 }
}
```

If a style names no `font`, the launcher's bundled default is used. A font file that fails to load falls back to the default for that style only.

## 6. Background

```json
"background": { "color": "@background", "image": "assets/wallpaper.jpg", "fit": "crop",
                "dim": 0.25, "userWallpaper": "allowed", "effect": "scanlines", "effectOpacity": 0.12 }
```

- `userWallpaper`: `allowed` lets the user's own picture from Settings replace the theme image, `ignored` always uses the theme image, `required` hides the theme image until the user picks one.
- `effect`: one of `none`, `scanlines`, `grid`, `dither`. These are fixed, cheap overlays built into the launcher. You cannot supply shader code.

## 7. Card styles

A card style says what one app tile looks like in three states: `idle`, `focused` and `moving` (while the user is rearranging).

```json
"cardStyles": {
  "banner": {
    "aspect": "16:9",
    "shape": { "kind": "rounded", "radius": 20 },
    "fill": "@surface",
    "artwork": { "source": "auto", "fit": "crop", "iconSize": 96 },
    "label": { "position": "below", "style": "caption", "gap": 10 },
    "idle":    { "scale": 1.0 },
    "focused": { "scale": 1.08, "shadow": { "color": "#66000000", "dy": 8, "blur": 24 },
                 "indicator": { "kind": "ring", "color": "@primary", "width": 3, "gap": 4 } }
  }
}
```

- **Artwork source**: `auto` uses a banner if one exists and the app icon otherwise. `banner` or `icon` force one. `sampling: "nearest"` with `pixelBlock` gives a pixelated look (downscale by that block size, then upscale without smoothing).
- **Shapes**: `rect`, `rounded`, `pixel` (stepped corners of `pixelSize`), `chamfer` (cut corners), `bevel`.
- **Label position**: `none`, `below`, `overlayBottom`, `overlayCenter`, `focusedOnly`.
- **Focus indicators**: `none`, `ring`, `corners` (four corner brackets), `arrow` (a pointer beside the card), `underline`, `fill`, `bar`. `blink: true` toggles it on and off every `blinkMillis`.
- **Card animation** (per state): `none`, `bob`, `pulse`, `blink`.

## 8. Screen trees

`screens.home.root` is one node. Container nodes hold children. Every node accepts the common fields: `id`, `frame`, `weight`, `padding`, `visible`, `showWhen`.

| `type` | Purpose | Notable fields |
|---|---|---|
| `stack` | Layers children; each child is placed by its `frame`. | `children` |
| `row`, `column` | Flow layout. Children share leftover space by `weight`. | `children`, `gap`, `align` |
| `shape` | A filled, bordered shape. | `shape`, `fill`, `border`, `shadow` |
| `image` | A theme image. | `source`, `fit`, `sampling`, `tint`, `alpha` |
| `text` | Literal text, or one fixed piece of live data. No expressions. | `text`, `style`, `bind` |
| `clock`, `date` | The system time and date, shown only if the user has enabled them. | `style`, `showSeconds` |
| `focusedAppName` | The name of the app that currently has focus. | `style` |
| `sections` | The section switcher (Streaming, Games and so on). | `arrangement` (`tabs`, `dots`, `list`), `style`, `selectedIndicator` |
| `apps` | The app collection. | `arrangement`, `card`, `columns`, `rows`, `itemWidth`, `itemHeight`, `gap`, `scroll`, `wrap`, `focusScope` |
| `spacer` | Empty space. | `size` |
| `statusIcons` | The VPN, network and settings buttons, drawn your way. | `items`, `iconSize`, `gap`, `color`, `inactiveColor`, `focusFill`, `shape` |

**`frame`** places a node inside a `stack`: `x`, `y`, `w`, `h` (lengths) and an `anchor` (`topStart`, `top`, `topEnd`, `start`, `center`, `end`, `bottomStart`, `bottom`, `bottomEnd`) saying which point of the node `x` and `y` refer to, plus `zIndex`.

**`apps` arrangements:**

| Value | What the user sees |
|---|---|
| `rail` | One horizontal row that scrolls sideways. |
| `grid` | `columns` by `rows`, scrolling vertically. |
| `list` | One vertical column. |
| `cross` | A horizontal bar of icons with the focused one enlarged, like a console main menu. |

Every theme must contain exactly one `apps` node on the home screen; otherwise the theme is rejected, because the launcher would have no way to open apps.

The `sections` node is optional. If a theme leaves it out, the launcher treats the `apps` node as `focusScope: "all"` and shows every visible app in one list, so nothing becomes unreachable. Section switching with the remote's channel or page keys keeps working either way.

**`showWhen`** is how a theme reacts to user settings without code: `always`, `clockEnabled`, `dateEnabled`, `hasFocusedApp`, `statusBarEnabled`, `hasSections`, `online`.

**`text.bind`** swaps the literal text for live data from a closed list: `sectionName`, `appCount`, `focusedPackage`, `focusedPosition` ("3 / 12"), `networkName`.

**`chrome.statusBar`** (top level): `launcher` (default) keeps the launcher's own status bar above the theme; `theme` hides it so the theme places a `statusIcons` node wherever it likes, or none. Menu always opens Settings, so nothing is ever unreachable.

**`sections.always`**: `true` for a theme built around sections. They stay on, and the "Group apps into sections" switch is hidden.

## 8b. Settings that apply to every theme

These Display settings are applied by the launcher on top of any theme, as factors of what the theme designed (the default setting leaves the theme exactly as drawn):

| Setting | Effect on a theme |
|---|---|
| Corner roundness | Scales the radius of every `rounded`, `pixel`, `chamfer` and `bevel` shape: cards, shape nodes, status-icon buttons, focus rings. `rect` stays square. |
| Icon size | Scales app tiles. |
| Spacing | Scales every gap in rows, columns, tab lists and the app list. |
| Show app names | Hides every card label. |
| Accent colour | Replaces `@primary` and `@accent`. The first choice, "Default", keeps the theme's own colours. |
| UI scale, animations, light/dark, wallpaper and dimming, clock, 24-hour, date | Already apply to every theme. |

A setting the active theme cannot use is **hidden** instead of shown dead: no `clock` node hides Show clock and 24-hour; no `date` node hides Date format; no `sections` node hides the sections switch and the Sections page; no rounded shape hides Corner roundness; no label hides Show app names; `userWallpaper: "ignored"` hides Wallpaper; a theme with `chrome.statusBar: "theme"` and no `statusIcons` hides the status-bar page's bar, glass and VPN options. The classic layout-only choices (view mode, section names) only appear in Classic mode.

## 9. Responsive overrides

`overrides` is a list. Each entry has a `when` and a `merge`. When every condition in `when` is true, the `merge` object is applied to the theme using JSON Merge Patch (RFC 7396) before the screen tree is validated.

```json
"overrides": [
  { "when": { "maxAspect": 1.5 },
    "merge": { "screens": { "home": { "root": { "children": [] } } } } },
  { "when": { "reducedMotion": true },
    "merge": { "motion": { "focusMillis": 0, "scrollMillis": 0 } } }
]
```

Conditions: `minAspect`, `maxAspect` (width divided by height), `minHeightPx`, `maxHeightPx`, `colorScheme`, `reducedMotion`. A merge cannot change `id`, `version`, `schemaVersion`, `credits` or `license`. Note that merge patch replaces arrays wholesale, so to change one child you must restate the array.

### Fields that are accepted but not drawn yet (v1 renderer)

These validate, but the first renderer ignores them: the font `pixel` flag, `apps.wrap`, `apps.rows`, and `screenTransition`. The `cross` arrangement is drawn as a horizontal rail whose focused card is kept centred. Themes may include them; a later launcher version will honour them without a schema change.

## 10. What happens when a theme is wrong

1. **At install time** the whole theme is validated (JSON Schema rules, limits, path rules, every referenced file exists and has the right type, exactly one `apps` node). A theme that fails is not installed. An existing installed copy is left alone.
2. **At load time** the same checks run again on the installed copy.
3. If loading fails, the launcher shows the bundled **Wishy** theme and a small notice, and records the reason under Settings, Reset and recovery.
4. A single bad image or font does not reject the theme once it is installed: that element falls back (flat colour, default font) and the problem is logged.
5. Settings, Themes, "Reset to the Wishy theme" always returns to the bundled Wishy theme. Menu always opens Settings, even when a theme draws no settings button. Choosing "Classic layout" bypasses themes entirely.

## 11. Credits and licences

Every file you did not create yourself needs a line in `credits` and a matching entry in `CREDITS.md`. The theme repository's CI check (`tools/validate_repo.py`) fails a theme whose images lack credits. Do not include artwork, logos, fonts or wallpapers unless you have checked that you may redistribute them. A theme in the official repository must use only files under licences that allow redistribution, and must state them.

## 12. Making your own theme

See the step-by-step guide: [`docs/MAKING_THEMES.md`](docs/MAKING_THEMES.md). In short: copy a folder from `themes/`, change `id` and the content, run `python3 tools/build_repo.py` to validate and build, then publish through a pull request or your own repository. Installing from a local folder or file on the device is not implemented. If a theme is rejected, Settings shows the reason and the launcher keeps working with the previous theme.
