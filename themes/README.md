# Built-in themes

These four folders are packed into the launcher as assets and copied to the app's storage on first run (and again whenever the app version changes). They load through exactly the same loader as downloaded themes, so they are also the reference examples for [`THEME_SCHEMA.md`](../THEME_SCHEMA.md).

| Folder | Idea | Layout | Navigation feel |
|---|---|---|---|
| `wishy` | Clean, blue and white by day, dark navy at night. | Clock and date top left, section tabs, one row of wide 16:9 banners. | Smooth: scale and ring on focus, eased scrolling. |
| `8bit` | Limited palette, hard pixel edges, dither overlay. | A framed title panel on the left with a vertical section list; a 4-column grid of square cartridges on the right. | Stepped (`steps:4`): cards hop up and blink, corner brackets flash, an arrow marks the section. |
| `psx` | Console-style cross menu with a scanline overlay. | Sections along the top, one centred bar of icons with the focused one enlarged, its name below. | Slow, soft: the bar slides to keep the focus centred, the focused icon pulses and glows. |
| `experimental` | Bold and graphic. Split screen with heavy borders and cut corners. | A very large clock and the focused name on the left, a vertical stack of wide banner strips on the right. | Quick and blunt: hard offset shadows, a thick bar marks the focus. |

None of them contains third-party artwork, fonts or logos; see each folder's `CREDITS.md`. Previews are not included yet: they must be real screenshots of the running launcher, so they will be added after you have built and run it.

All four use only features the v1 renderer draws. They check against `schema/wishy-theme.schema.v1.json` and are also loaded by `themecore`'s `everyBundledThemeLoadsInBothSchemesAndSizes` test in both colour schemes at 720p, 1080p and 2160p.
