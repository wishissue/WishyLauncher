# Wishy Launcher

A fast, private, open-source home launcher for Android TV and Google TV, with a theme system that can change the whole layout and install new themes without updating the app.

> **Status:** the theme engine has been compiled and validated by the maintainer. The latest changes (artwork system, `wishy-home` theme, navigation, wallpaper and layout fixes) are source-reviewed only and still need a build and a real-device test. Minimum Android version: 5.0 (API 21). See [`BUILD.md`](BUILD.md) section 5 and [`INSTALL.md`](INSTALL.md).

## What it does

- Remote-friendly home screen: D-pad focus, app launch, long-press menu, move/hide/organise apps, sections, clock and date, status bar, and wallpapers (eight bundled ones in a light and a dark tone, or your own photo).
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
