# Wishy Launcher

A fast, private, open-source home launcher for Android TV and Google TV, with a theme system that can change the whole layout and install new themes without updating the app.

> **Status:** the theme engine has been compiled and validated by the maintainer. The latest changes (artwork system, `wishy-home` theme, navigation, wallpaper and layout fixes) are source-reviewed only and still need a build and a real-device test. Minimum Android version: 5.0 (API 21). See [`BUILD.md`](BUILD.md) section 5 and [`INSTALL.md`](INSTALL.md).

## What it does

- Remote-friendly home screen: D-pad focus, app launch, long-press menu, hide/organise apps, reorder apps with ▲ ▼ in Settings › Apps (works with a remote, every theme), sections, clock and date, status bar, and wallpapers (eight bundled ones in a light and a dark tone, or your own photo).
- **Built-in theme engine**: **Wishy** (blue and white, navy in dark mode) default theme, plus the original Couchy layouts available as **Classic**.
- **Themes are data**: versioned JSON plus images, strictly validated, no scripts. New themes install without an APK update, and a broken theme falls back to Wishy, then to Classic.
- **Artwork**: find logos and banners for your apps, build a banner in a simple editor (logo, background, adjust), keep your banners safe, and export or import them as a ZIP pack. Falls back to the app's own banner, then its icon. See `ARCHITECTURE.md` section 6a.
- Offline by default, no accounts, ads or analytics, and no system settings changed.

## Where things are

| Path | What |
|---|---|
| `app/` | The Android app (Kotlin, Jetpack Compose for TV). A fork of Couchy Launcher. |
| `themecore/` | Android-free library: theme model, loader, checker, safe unzip, installer, repository rules. JUnit tests (not yet run). |
| `themes/` | The four built-in themes. [`themes/README.md`](themes/README.md) |
| `schema/` | JSON Schema for themes. |
| [`THEME_SCHEMA.md`](THEME_SCHEMA.md) | The theme format and how to make a theme. |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Design and current state. |
| [`docs/FOUNDATION_AUDIT.md`](docs/FOUNDATION_AUDIT.md) | Why this foundation was chosen. |
| [`INSTALL.md`](INSTALL.md), [`BUILD.md`](BUILD.md) | Installing and building. |
| [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md), [`ARTWORK_ATTRIBUTION.md`](ARTWORK_ATTRIBUTION.md) | Licences and credits. |

The theme and artwork repository is a separate project, `WishyLauncher-Themes`.

## Known gaps

- Theme fields that validate but are not drawn yet: font `pixel` flag, `apps.wrap`, `apps.rows`, `screenTransition`.
- No installing a theme from a local folder or file, no automatic update checks, and no first-run question about going online (it stays offline until you change the setting).
- No theme previews and no artwork yet. Both must be real, so they come after the first working build.
- Themes are checked by hash, not by signature, so the official repository's maintainers must be trusted. See `ARCHITECTURE.md` section 7.
- Only English strings for the new screens.

## Licence

GPL-3.0-only. See `LICENSE`. This project is a modified version of Couchy Launcher; see `THIRD_PARTY_LICENSES.md`.

## Make your own theme

See [`docs/MAKING_THEMES.md`](docs/MAKING_THEMES.md): where to put a theme, how to build it, and how to make a different clock for every theme.

## Recent changes

- **Default launcher** (Settings › Default launcher): shows whether Android really treats Wishy as the home app, opens Android's own Home app page, offers "Remove default / change launcher", refreshes when you come back, and records whether the Home button actually reaches Wishy. Certified Google TV devices keep Google's launcher on the Home button even when Wishy is selected; the page explains this and links to the ADB/remap setup guide.
- **Wallpapers** are decoded at the real screen size (up to 4096 px on the long edge, 8-bit colour, no rescaling at decode) instead of a fixed 1280 px 16-bit copy, so photos and bundled wallpapers stay sharp on 1080p and 4K TVs. Only the blurred background layer still uses a tiny copy.
- **Artwork search** now also searches Material Symbols, Simple Icons and other Iconify sets, understands synonyms ("movies" also finds "film"), removes duplicates, shows SVG icons only after they pass the SVG safety check, and has a **Fetch more** button. SVG Repo is not used: it has no public API and scraping it is not allowed.
- **Surprise me** (banner editor, replaces "Auto design"): shows six random banners made from the logo (colours, gradients, layouts and faint patterns). Pick one, generate again, then Done to save. The manual editor is unchanged.
- **Moving apps**: the long-press Move mode was removed. Use Settings › Apps: ▲ ▼ move an app one place (hold to jump to the top or bottom); the others shift and the order is saved.
- **Home button override** (Settings › Default launcher): for TVs that ignore the Home app choice. Turn on "Wishy Home button" in Android's Accessibility settings, no ADB or PC needed. It opens Wishy when Home is pressed, right after the TV boots, and also when the TV's own launcher comes to the front (wake from standby, boot). It reads no screen content.
- **Banners** on app tiles are no longer chopped: a banner is filled to the tile only when less than 10% would be cut off, otherwise it is shown whole.
