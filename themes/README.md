# Built-in themes

This folder is packed into the launcher as assets and copied to the app's storage on first run (and again whenever the app version changes). It loads through exactly the same loader as downloaded themes, so it is also the reference example for [`THEME_SCHEMA.md`](../THEME_SCHEMA.md).

| Folder | Idea | Layout | Navigation feel |
|---|---|---|---|
| `wishy` | The Wishy Browser look. Plus Jakarta Sans, brand blue, a glass shelf under the banners, a blue glow on the focused one, light and dark wallpapers. | Clock and date top left, section tabs, one row of wide 16:9 banners on a shelf. | Instant focus, 100 ms eased scroll. |

It contains no third-party artwork or logos. Its wallpapers are drawn by `tools/gen-wallpapers.py`. Fonts are open-licensed (SIL OFL 1.1); see `CREDITS.md`. No theme may animate focus or scrolling for more than 80 ms and 120 ms respectively; the launcher caps longer values.

It checks against `schema/wishy-theme.schema.v1.json` and is also loaded by `themecore`'s tests in both colour schemes at 720p, 1080p and 2160p.

## Cover art per theme

The built-in theme sets `artwork.themeArt` and ships its own covers in `assets/art/`:

- `assets/art/<package>.png` is the cover for one app (for example `com.netflix.ninja.png`).
- `assets/art/default.png` is the placeholder used when an app has neither its own banner nor a cover of its own.

Order of choice: the theme's cover for that app, then the app's own banner, then `default.png`, then the app icon on a plain tile.
