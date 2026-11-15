# Third-party licences and credits

This file lists every third-party component in the source tree and in the built APK, and where each came from. It was completed in Milestone 6 from the Gradle files and the source; it is not generated, so check it again whenever a dependency is added.

## Base project

| Component | Licence | Notes |
|---|---|---|
| **Couchy Launcher** by the Couchy Launcher contributors, https://github.com/conreo/couchy-launcher, commit `d3dcd70d3dd3b261108d25d72569671aee23b2d6` | GPL-3.0 | Wishy Launcher is a modified version. Copyright (C) 2026 the Couchy Launcher contributors for their portions. Changes: package rename, removal of aerial video wallpapers, video playback, the trust-all TLS client, donation UI and couch artwork; added `themecore` and documentation. |

## Libraries (used as unmodified binaries from Maven)

| Library | Licence |
|---|---|
| AndroidX: `core-ktx`, `annotation`, `activity-compose`, `lifecycle-runtime-compose`, `datastore-preferences`, `profileinstaller` | Apache-2.0 |
| Jetpack Compose (BOM 2024.09.03: `ui`, `foundation`), `androidx.tv:tv-material` 1.0.0, `androidx.graphics:graphics-shapes` 1.0.1 | Apache-2.0 |
| `kotlinx-serialization-json` 1.7.1 (app and themecore) | Apache-2.0 |
| Kotlin standard library (pulled in by the above) | Apache-2.0 |

Build and test tools, **not** shipped in the APK: Android Gradle Plugin, Kotlin Gradle plugin and Gradle (Apache-2.0), JUnit 4.13.2 (EPL-1.0, tests only). In the theme repository's CI only: `jsonschema` (MIT) and Pillow (MIT-CMU), installed from PyPI and not distributed.

Apache-2.0 requires that the licence text accompanies binary distributions. When you distribute an APK, include the Apache-2.0 text (https://www.apache.org/licenses/LICENSE-2.0) alongside this file.

## Embedded data

| Item | Licence | Where |
|---|---|---|
| Material Design icon path data (Google) | Apache-2.0 | `app/src/main/java/org/wishy/launcher/ui/Icons.kt`, per Couchy's own licensing notes; not independently re-verified |

## Artwork and fonts

- `ic_wishy_mark.xml`, `app_icon.xml`, `app_banner.xml`: original placeholder artwork for this project, GPL-3.0. Final branding is not done.
- **The only third-party files bundled are the Plus Jakarta Sans fonts** (SIL OFL 1.1, the same files Wishy Browser ships), listed in the table below with their licence text. No third-party images, wallpapers or sounds are bundled: the wallpapers and the placeholder cover are drawn from scratch by `tools/gen-wallpapers.py` and are GPL-3.0 like the rest of the project. Every font or file added later must be listed here with its licence text.
- Artwork attribution is covered separately in `ARTWORK_ATTRIBUTION.md`.

## Colour palettes and brand references

- The Wishy theme uses the Wishy Browser brand colours (blue `#0166FF`, navy `#080B12`). The brand is the project owner's own.

## Ideas, not code

The theme engine's design borrows concepts from ES-DE (MIT, Northwestern Software AB and contributors): normalised layout, declared variants, aspect-ratio overrides. No ES-DE code or theme assets are included. The CC-BY-NC-SA ES-DE themes (`modern-es-de`, `slate-es-de`) are not used in any form.

## Fonts bundled in the built-in themes

All are licensed under the SIL Open Font License 1.1. The full licence text is in the `licenses/` folder.

| Font | Used by | Authors | Licence text |
|---|---|---|---|
| Plus Jakarta Sans (Regular, Medium, SemiBold, Bold) | Wishy theme | The Plus Jakarta Sans Project Authors | `licenses/OFL-PlusJakartaSans.txt` |

## Added with the UI redesign

| Item | Author | Licence |
|---|---|---|
| Material Symbols Rounded icons in `ui/SymbolIcons.kt` | Google, https://github.com/google/material-design-icons | Apache-2.0 |
| Bagel Fat One (`res/font/bagel_fat_one.ttf`), brand wordmark face | The Bagel Fat Project Authors, https://github.com/JAMO-TYPEFACE/BagelFat | SIL OFL 1.1 |
| Plus Jakarta Sans, also copied to `res/font/` for screens outside the theme engine | The Plus Jakarta Sans Project Authors | SIL OFL 1.1 |
