# Making your own Wishy Launcher theme

A theme is a folder: one `theme.json` plus optional fonts and pictures. You do not write any code. This guide takes you from nothing to a theme that anyone can install, including how to make a different clock for every theme. The complete field-by-field reference is [`THEME_SCHEMA.md`](../THEME_SCHEMA.md); this guide is the friendly route.

## 1. Where your theme goes

Pick the route that matches what you want.

| You want | Put the folder here | How people get it |
|---|---|---|
| Your theme in the official launcher | `themes/<your-id>/` in a fork of this repository, then open a pull request | It ships inside the app, and after the GitHub Action runs it is also listed under **Settings > Themes > Get more themes** |
| Your own theme store | A repository of your own with the same layout (`themes/` plus the `repo/` output, see section 9) | People add its address under **Settings > Themes > Get more themes > Advanced** |
| Just to try it on your TV | `themes/<your-id>/` in a copy of this repository, then build the app | It is bundled into your own build |

Installing a theme from a local file or folder on the device is not supported. That is deliberate: every theme goes through the same safety checks, whether it comes bundled or downloaded.

## 2. Folder layout

```
themes/mytheme/
  theme.json          required, the whole design
  CREDITS.md          required if you use any font or picture
  preview.png         optional, shown in the theme list
  assets/
    fonts/            .ttf or .otf files
    ui/               backgrounds and decorations
    art/              cover art (see section 6)
```

The folder name must equal `"id"` inside `theme.json`. Use lowercase letters, digits and dashes.

**File rules** (checked when the theme is built and again on the device):

- Only `png`, `jpg`, `jpeg`, `webp`, `ttf`, `otf`, `theme.json` and `CREDITS.md` are allowed. No scripts, no other file types.
- Pictures up to 5 MB and 4096 px, fonts up to 2 MB, at most 200 files, 40 MB unpacked, 20 MB zipped.
- Paths may only use letters, digits, `_`, `-`, `.` and `/`. No spaces, no `..`.

## 3. Your first theme in ten minutes

1. Copy a built-in theme folder next to it, for example `themes/wishy` to `themes/mytheme`.
2. Open `theme.json` and change `"id"`, `"name"`, `"author"`.
3. Change the colours in `colorSchemes`. Every colour is `#RRGGBB` or `#AARRGGBB`.
4. Run `python3 tools/build_repo.py` from the repository root. It checks your theme against the schema, checks every file you refer to exists, and builds the zip. A mistake is reported with the exact place.
5. Build and install the app (see `BUILD.md`), open **Settings > Themes**, and pick your theme.
6. Change something small, rebuild, look again. Repeat.

Tip: install `pip install jsonschema pillow` once so the script can check the schema and picture sizes.

### The smallest complete theme

```json
{
  "$schema": "../../schema/wishy-theme.schema.v1.json",
  "schemaVersion": 1,
  "id": "mytheme",
  "name": "My Theme",
  "version": "1.0.0",
  "author": "Your Name",
  "description": "My first theme.",
  "license": "GPL-3.0-only",
  "defaultColorScheme": "dark",
  "colorSchemes": {
    "dark": {
      "background": "#101820",
      "surface": "#1D2A38",
      "onSurface": "#FFFFFF",
      "primary": "#FFB000",
      "onPrimary": "#101820"
    }
  },
  "textStyles": {
    "clock": {
      "size": 80,
      "color": "@onSurface"
    }
  },
  "cardStyles": {
    "tile": {
      "aspect": "16:9",
      "shape": {
        "kind": "rounded",
        "radius": 16
      },
      "fill": "@surface",
      "artwork": {
        "source": "auto",
        "fit": "crop"
      },
      "label": {
        "position": "none"
      },
      "idle": {
        "scale": 1.0,
        "alpha": 0.9
      },
      "focused": {
        "scale": 1.08,
        "alpha": 1.0,
        "indicator": {
          "kind": "ring",
          "color": "@primary",
          "width": 4,
          "gap": 4
        }
      }
    }
  },
  "motion": {
    "focusMillis": 60,
    "scrollMillis": 100,
    "easing": "decelerate"
  },
  "screens": {
    "home": {
      "root": {
        "type": "stack",
        "children": [
          {
            "type": "clock",
            "style": "clock",
            "frame": {
              "x": "5%",
              "y": "6%"
            }
          },
          {
            "type": "sections",
            "arrangement": "tabs",
            "gap": 30,
            "frame": {
              "x": "5%",
              "y": "30%",
              "w": "90%"
            }
          },
          {
            "type": "apps",
            "arrangement": "rail",
            "card": "tile",
            "itemWidth": 360,
            "gap": 24,
            "frame": {
              "x": 0,
              "y": "45%",
              "w": "100%",
              "h": "40%"
            },
            "padding": {
              "left": "5%",
              "right": "5%"
            }
          }
        ]
      }
    }
  }
}
```

It has a clock, section tabs and one row of banners. Everything else in this guide is about changing and extending it.

## 4. How a theme is built

- **`colorSchemes`**: a `dark` and/or `light` set. Everything else refers to colours by name, like `@primary` or `@surface`. Add `/60` for transparency, like `@surface/60`.
- **`textStyles`**: named text looks (font, size, colour, spacing, shadow). Nodes refer to them by name.
- **`cardStyles`**: how one app tile looks: shape, fill, border, how it looks idle, focused, or while being moved.
- **`screens.home.root`**: a tree of nodes that places everything on the screen. Exactly one `apps` node is required.

Node types: `stack` (layers), `row`, `column`, `shape`, `image`, `text`, `clock`, `date`, `focusedAppName`, `sections`, `apps`, `spacer`.

**Position anything with `frame`.** `x`, `y`, `w`, `h` are plain numbers (design units) or percentages of the parent, and `anchor` chooses which corner or edge `x` and `y` refer to: `topStart`, `top`, `topEnd`, `start`, `center`, `end`, `bottomStart`, `bottom`, `bottomEnd`. Right-aligning something is just `"x": "95%", "anchor": "topEnd"`.

**Different layouts** come from `apps.arrangement`:

| Value | Looks like |
|---|---|
| `rail` | One scrolling row of tiles |
| `grid` | Rows and columns (`columns` sets how many) |
| `list` | One vertical stack of strips |
| `wheel` | A ring of round cards where only the middle one is selected and up/down spins the next app into the middle |
| `bento` | A grid of wide and small tiles (`card` for small, `bigCard` for wide) |

Three bundled themes show these off: `wishy-wheel` (the ring), `wishy-cozy` (bento) and `wishy-bit` (a text-only menu with `artwork.source: "none"` and a pixel font). Copy whichever is closest to what you want.

and `sections.arrangement`: `tabs` (a row), `dots` or `list` (a column).

Use `designHeight` (default 1080) to design on a different canvas. Handheld uses 720, so its numbers read like a 720p screen.

## 5. Different clocks for every theme

There is no separate "clock mode". A clock is a `clock` node, and the look is entirely yours:

- **Font, size, colour, spacing, shadow** come from its text style.
- **Position and size** come from `frame`.
- **Seconds**: `"showSeconds": true` on the node.
- **Shapes behind it**: draw a `shape` node first, then the clock on top.
- **12 or 24 hour** follows the device setting. A theme cannot override it.
- **On or off**: `"showWhen": "clockEnabled"` hides the clock when the person turns it off in Settings. Use it on every clock you add.
- **A date to go with it**: a `date` node with its own style.
- **Several clocks** are allowed, for example a big one and a small decorative one. Only use `showSeconds` on one; each ticking clock redraws every second.
- An analogue (hands) clock is not supported yet.

Pair the clock with the theme's character. A pixel theme wants a pixel font and no shadow; a console theme wants thin type; a poster theme wants a huge condensed clock. Six starting points, all tested against the schema (they use the fonts of the Wishy theme, so change the `font` names to ones your theme defines):

### 1. Big and minimal

One huge, tight clock at the top left.

Add to `textStyles`:

```json
{
  "clock": {
    "font": "semibold",
    "size": 140,
    "color": "@onSurface",
    "letterSpacing": -4
  }
}
```

Put in the `children` list of the home screen:

```json
[
  {
    "type": "clock",
    "style": "clock",
    "frame": {
      "x": "5%",
      "y": "6%"
    }
  }
]
```

### 2. Digital, with seconds

Seconds on, wide letter spacing, accent colour, top right.

Add to `textStyles`:

```json
{
  "clock": {
    "font": "medium",
    "size": 64,
    "color": "@primary",
    "letterSpacing": 4
  }
}
```

Put in the `children` list of the home screen:

```json
[
  {
    "type": "clock",
    "style": "clock",
    "showSeconds": true,
    "frame": {
      "x": "95%",
      "y": "5%",
      "anchor": "topEnd"
    }
  }
]
```

### 3. Pill chip

A rounded pill drawn behind a small clock. Tune the numbers until the text sits centred.

Add to `textStyles`:

```json
{
  "clock": {
    "font": "medium",
    "size": 34,
    "color": "@onPrimary"
  }
}
```

Put in the `children` list of the home screen:

```json
[
  {
    "type": "shape",
    "shape": {
      "kind": "rounded",
      "radius": 60
    },
    "fill": "@primary",
    "frame": {
      "x": "95%",
      "y": "4%",
      "w": "11%",
      "h": "7%",
      "anchor": "topEnd"
    }
  },
  {
    "type": "clock",
    "style": "clock",
    "frame": {
      "x": "93.2%",
      "y": "5.3%",
      "anchor": "topEnd"
    }
  }
]
```

### 4. Clock over date

A big clock with a spaced-out capital date underneath.

Add to `textStyles`:

```json
{
  "clock": {
    "font": "semibold",
    "size": 96,
    "color": "@onSurface"
  },
  "date": {
    "font": "regular",
    "size": 30,
    "color": "@onSurfaceMuted",
    "uppercase": true,
    "letterSpacing": 6
  }
}
```

Put in the `children` list of the home screen:

```json
[
  {
    "type": "clock",
    "style": "clock",
    "frame": {
      "x": "5%",
      "y": "6%"
    }
  },
  {
    "type": "date",
    "style": "date",
    "frame": {
      "x": "5.2%",
      "y": "21%"
    }
  }
]
```

### 5. Neon glow

A soft coloured glow behind the digits, centred at the top.

Add to `textStyles`:

```json
{
  "clock": {
    "font": "semibold",
    "size": 110,
    "color": "@primary",
    "shadow": {
      "color": "#CC66A3FF",
      "dx": 0,
      "dy": 0,
      "blur": 40
    }
  }
}
```

Put in the `children` list of the home screen:

```json
[
  {
    "type": "clock",
    "style": "clock",
    "frame": {
      "x": "50%",
      "y": "6%",
      "anchor": "top"
    }
  }
]
```

### 6. Quiet corner clock

Small and muted, and it disappears when the person turns the clock off in Settings.

Add to `textStyles`:

```json
{
  "clock": {
    "font": "regular",
    "size": 26,
    "color": "@onSurfaceMuted"
  }
}
```

Put in the `children` list of the home screen:

```json
[
  {
    "type": "clock",
    "style": "clock",
    "showWhen": "clockEnabled",
    "frame": {
      "x": "96%",
      "y": "3.5%",
      "anchor": "topEnd"
    }
  }
]
```

### Make the clock responsive

Use `overrides` so the clock still fits on narrow or low-motion setups:

```json
"overrides": [
  { "when": { "maxAspect": 1.5 }, "merge": { "textStyles": { "clock": { "size": 70 } } } }
]
```

## 6. Fonts, icons and cover art

**Fonts.** Put a `.ttf` or `.otf` file in `assets/fonts/`, declare it, then use it in a text style:

```json
"fonts": { "display": { "file": "assets/fonts/MyFont-Regular.ttf" } },
"textStyles": { "title": { "font": "display", "size": 48, "color": "@onSurface" } }
```

Add `"pixel": true` for bitmap fonts. Use fonts with the SIL Open Font License or similar, for example from [Google Fonts](https://github.com/google/fonts/tree/main/ofl). Put one font style per file; variable fonts with `[` in the file name are not allowed, so download the static files.

**Cover art for apps.** Turn it on in the card style with `"themeArt": true` under `artwork`. Then:

- `assets/art/<package>.png` is the cover for one app, for example `com.netflix.ninja.png`
- `assets/art/default.png` is used when an app has neither a cover of yours nor its own banner

The order is: your cover for that app, then the app's own banner, then your `default.png`, then the app icon on a plain tile. Make covers the shape your cards use (`aspect` in the card style): `16:9` banners, `1:1` squares, and so on.

**Icon looks.** `artwork` has three switches for how pictures are drawn:

- `"mono": true`: hard black and white, like a 1-bit screen. Used by the 8-bit theme.
- `"pixelBlock": 4` with `"sampling": "nearest"`: chunky pixel art, any picture.
- `"source": "icon"`: always use the app icon instead of its banner.

**Backgrounds.** `background.image` takes a picture, `background.effect` takes `scanlines`, `grid` and others, with `effectOpacity` to tone it down. `userWallpaper` decides whether the person's own wallpaper may show.

## 7. Make it feel fast

The launcher caps animations so nothing can feel slow: focus changes at most 80 ms, scrolling at most 120 ms, whatever you write. Good practice:

- `"focusMillis": 50` to `60`, `"scrollMillis": 90` to `100`, `"easing": "decelerate"`.
- For a retro snap with no animation: `0` for both.
- Never use an `accelerate` easing; it starts slowly and feels laggy.
- Add the reduced-motion override so people who turned animations down get none:

```json
"overrides": [{ "when": { "reducedMotion": true }, "merge": { "motion": { "focusMillis": 0, "scrollMillis": 0 } } }]
```

## 8. Credits and licences

Every font and picture you did not draw yourself needs:

1. an entry in `theme.json` under `credits` (file, title, author, licence, source), and
2. a line in `CREDITS.md`.

Only include files you may redistribute. Do not include logos, console artwork, screenshots of other products, or fonts you only have a desktop licence for. Make themes that borrow an idea (a shelf of tiles, a wave backdrop), not a copy of someone's trademarked design.

## 9. Publish

**Into this repository:** push your folder in a pull request. When it is merged, the GitHub Action (`.github/workflows/repo.yml`) rebuilds `repo/` automatically: your theme zip, its SHA-256 and the index. The launcher refuses a download whose hash does not match, so never edit files inside `repo/` by hand.

**As your own store:** copy `tools/build_repo.py` into your own repository, put your themes under `themes/`, run it, and commit the `repo/` folder. Your index address is:

```
https://raw.githubusercontent.com/<you>/<repository>/main/repo/index.json
```

Share that address. To add it on the TV: **Settings > Themes > Get more themes > Advanced > Add repository**. Third-party stores show a warning first, because anything they offer is code-free but still not reviewed by this project.

## 10. When something goes wrong

| Symptom | Likely cause |
|---|---|
| `build_repo.py` says "breaks the schema at ..." | A typo or a value out of range at the place it names |
| "refers to missing file" | The path in `theme.json` does not match the file name exactly (case matters) |
| "has a file type themes may not contain" | A stray file in the folder: remove it |
| Theme does not appear in the list | The folder name differs from `"id"`, or `theme.json` is not at the top of the folder |
| Text is cut off | Pixel fonts are wide; lower `size` or give the frame a larger `w` |
| Tiles are clipped when focused | Lower the focused `scale` or shadow `blur`; the row only leaves a little room around the tiles |
| Clock sits off-centre in a pill | Tune the clock frame's `x` and `y`; text width depends on the font |

## Checklist before you share

- [ ] `python3 tools/build_repo.py` passes
- [ ] Works with the `dark` and `light` colour schemes (or you only define one on purpose)
- [ ] Focus changes feel instant
- [ ] Every font and picture has a credit and a licence that allows sharing
- [ ] No logos or copied artwork
- [ ] `description` says in one sentence what it looks like
