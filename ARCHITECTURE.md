# Wishy Launcher architecture

Status of this document: design and current state of the source architecture. Where something is not written yet, it says **planned**.

## 1. Goals that drive the design

1. A theme can change the whole layout, not just colours, and new themes install without a new APK.
2. A downloaded theme can never run code, read files outside itself, fill the disk, or leave the launcher unusable.
3. The home screen starts fast and stays light on a 1 to 2 GB TV.
4. Everything works offline once installed. The network is used only for the theme repository and artwork, only over HTTPS, and only when the user (or a visible setting) allows it.
5. Settings and recovery are never themed, so a bad theme cannot lock anyone out.

## 2. Foundation

Wishy Launcher is a fork of Couchy Launcher (GPL-3.0, commit `d3dcd70d3dd3b261108d25d72569671aee23b2d6`). See [`docs/FOUNDATION_AUDIT.md`](docs/FOUNDATION_AUDIT.md) for why. Changes made so far:

| Change | Reason |
|---|---|
| Package renamed to `org.wishy.launcher`, app id `org.wishy.launcher` | Wishy naming. |
| Removed built-in aerial videos, the local-video wallpaper, Media3/ExoPlayer, OkHttp, the network security config and `usesCleartextTraffic` | Not needed, large dependency, and the video client used a **trust-all TLS certificate manager that disabled certificate and hostname checks**. That was removed, and the new network client (`HttpsFetcher`, written) uses normal platform certificate validation. |
| Removed the "Buy me a coffee" QR, Cookie font and the Twemoji couch artwork | Not part of Wishy. New original placeholder mark: `ic_wishy_mark.xml`. |
| Kept `INTERNET` permission | Needed later for the theme and artwork client. Nothing uses it yet. |
| Kept `KILL_BACKGROUND_PROCESSES`, `REQUEST_DELETE_PACKAGES`, `QUERY_ALL_PACKAGES` | `Actions.kt` uses the first two for "close app" and "uninstall" menu items, and a launcher needs the third to list every app. |

`minSdk` stays at 21 (Couchy's value), `targetSdk` 34.

## 3. Modules

```
WishyLauncher/
  app/                     Android application (Compose TV). Everything that needs Android.
  themecore/               Pure Kotlin/JVM library. No Android classes. Planned contents below.
  themes/                  The four built-in themes, copied into the APK's assets at build time. (Milestone 4)
  schema/                  Public JSON Schema for themes and the repository manifest.
  docs/
```

Why a separate `themecore`: the safety-critical code (path rules, unzip limits, merge patch, atomic install, manifest checks) has no need for Android, so it can be unit-tested on a plain JVM with Gradle `test`, and it can be reused by a command-line theme checker.

### `themecore` (Android-free)

| Part | State | Job |
|---|---|---|
| `ThemeLimits` | written | One place for every size and count limit. |
| `ThemePaths` | written | Decides whether a relative path or zip entry name is acceptable. |
| `SafeUnzip` | written | Extracts a zip into a new empty folder under byte, count and ratio limits, checking every entry name. |
| `MergePatch` | written | RFC 7396 merge patch used by `overrides`. |
| `ThemeInstaller` | written | Stage, verify, then atomically switch an installed theme; keep the old one on any failure. |
| `ThemeModel` (typed, strict `kotlinx.serialization`) | written | The single typed definition of `theme.json`. Unknown keys and wrong types fail decoding. |
| `ThemeChecker` | written | Range and reference checks the type system cannot express: colour syntax, token names, style references, exactly one `apps` node, asset existence and type, node and depth limits. |
| `RepoModels` (index models, URL rules, checker) | written | Reads `index.json` from the theme repository. |

The JSON Schema in `schema/` is the public description for theme authors and tools. The Kotlin model is the real gatekeeper. A launcher unit test loads every bundled theme, and the repository's `validate_repo.py` checks every published theme against the JSON Schema, so they cannot drift apart.

### `app` packages

| Package | State | Job |
|---|---|---|
| `org.wishy.launcher.data` | from Couchy | `AppRepository` (app discovery and cache), `LauncherConfig` and `ConfigStore` (DataStore, one JSON blob), `Status` (network and clock state). |
| `org.wishy.launcher.ui` | from Couchy | Home screen, settings sheet, wizard, cards. Will be split. |
| `org.wishy.launcher.theme` | written (storage, environment, shapes, card, home renderer); not compiled | Loads installed themes, resolves colour tokens, and renders a theme tree as Compose. |
| `org.wishy.launcher.art` | written | Banner manager. |
| `org.wishy.launcher.net` | written | The one HTTPS client. |

## 4. Application discovery (existing, kept)

- `AppRepository.scan` asks `PackageManager` for activities matching `CATEGORY_LEANBACK_LAUNCHER` and `CATEGORY_LAUNCHER`, skips the launcher itself, and keeps one entry per package.
- Each entry carries package name, label, banner (if the app ships a leanback banner), icon fallback, install and update times.
- Entries whose `lastUpdateTime` is unchanged are reused between scans, so a rescan only decodes new or updated apps. Scan work is capped to `cores - 1` threads so the render thread keeps a core.
- Bitmaps are cached as lossless WebP in `filesDir/iconcache`, named with package, update stamp and size. Cache files of uninstalled or updated apps are deleted after each scan.
- `MainActivity` registers a receiver for package added, removed and changed events, so the list refreshes without a restart.
- Sections (user-named groups), per-section order, hidden apps, and a "known apps" set live in `LauncherConfig`. New installs go to the first section.

Planned change: the scan will also look up the banner manager (section 6) for each package, so a user-chosen or downloaded banner takes priority over the app's own, and the app's own over the icon.

## 5. Theme engine

### Data flow

```
 theme.json + assets            LauncherConfig (user settings)         installed apps
        |                              |                                    |
   ThemeModel (strict)                 |                                    |
        |                              v                                    v
   ThemeChecker --> ResolvedTheme ----> ThemeRenderer (Compose) <---- AppRepository
        |           (tokens resolved,           |
   fails: fall back  overrides merged)          v
   to bundled Wishy                      home screen composables
```

### Load steps (written)

1. Read `theme.json` with a size cap. Decode into the strict model. On error, stop.
2. Apply `overrides` whose `when` matches the current screen shape, colour scheme and reduced-motion setting, using merge patch, then decode again.
3. Run `ThemeChecker`.
4. Resolve colour tokens against the chosen colour scheme. Resolve lengths: numbers scale by `screenHeightPx / designHeight` times the user's UI scale; percentages resolve against the parent at layout time.
5. Load fonts and images lazily on a background thread, decoding images to their drawn size (`inSampleSize`), never larger than 4096 px.
6. Any failure in steps 1 to 3 selects the bundled Wishy theme and stores the reason. Failures in step 5 degrade only that element.

### Rendering

The theme tree is interpreted by one generic composable that switches on node type. There is no per-theme Kotlin code. `row` and `column` use Compose `Row` and `Column`. `stack` uses a custom `Layout` that places children from `frame` values. `apps` maps to `LazyRow`, `LazyVerticalGrid` or `LazyColumn`, and `cross` to a custom row where the focused item is enlarged. `bento` is a `LazyVerticalGrid` with two-column spans for the wide tiles. `wheel` is a custom layout: one focus target for the whole ring handles the keys, and the cards are drawn passively on a circle that an `Animatable` turns.

Focus and keys are owned by the launcher, not the theme: D-pad movement, long-press OK for the app menu, Menu key for Settings and Back behaviour are fixed. A theme chooses only how focus *looks* (card state, indicator, motion).

Reduced motion (a Settings toggle) forces zero-duration focus and scroll animation regardless of the theme.

### Performance rules for the renderer (written, to verify on device)

- Card composables take stable, immutable inputs so unchanged cards skip recomposition (Couchy already does this with `@Immutable AppEntry`).
- Focus effects use graphics-layer properties (scale, alpha, translation) so they do not re-layout.
- No blur, no runtime shaders. The `effect` overlays are drawn once into a cached bitmap.
- Only visible cards decode artwork.

None of this has been measured. Startup and memory targets need testing on a low-end TV, which only you can do.

## 6. Application banner manager (written, not compiled)

Code: `art/BannerManager.kt`. The home screen takes the user's picture or downloaded artwork over the app's own banner by decoding it off the main thread (`withArtwork`), then redraws when it is ready. Settings, App banners lets the user pick a picture per app, refresh, and clear downloaded artwork. Online lookup is off by default, runs at most once a day, five seconds after start, and asks for a picture only when the package is installed and listed in the index.

Lookup order for one installed package, first hit wins:

1. **User choice**: an image the user picked for this package, stored in `filesDir/art/user/`.
2. **Downloaded artwork**: validated and cached in `filesDir/art/repo/`, from the Wishy artwork index.
3. **The app's own banner**: the leanback banner from its resources (what Couchy does now).
4. **The app's icon**, drawn on the card style's `placeholderFill`.

Rules:

- **Only installed apps.** The launcher downloads one small index file for the whole repository (`artwork/index.json`) and matches package names *locally*. It never sends the list of installed apps anywhere, and it requests an image only for a package that is installed and listed in the index.
- **No invented URLs.** An image URL comes only from the index. If an app is not listed, there is no download attempt.
- **Never block the UI.** A single background worker fetches artwork one image at a time; cards show the fallback until the file is ready, then update.
- **Validate before keeping.** Download to a temp file under a size cap (2 MB). Check the SHA-256 against the index. Decode the bounds only (`inJustDecodeBounds`) and accept PNG, JPEG or WebP at 16:9 within tolerance and between 320x180 and 1920x1080. Only then rename over the old file. A failed download or validation leaves the old file untouched.
- **Offline.** Cached files are used with no network. A failed index fetch is retried later with backoff and never shown as an error on the home screen.
- **Manual choice** uses the system document picker. The chosen image goes through the same validation (but may be any aspect, cropped on display), is copied into the user folder, and the permission grant is not kept.
- **Cache management** in Settings shows sizes and can clear downloaded artwork without touching user choices.

## 7. Theme repository and installer (client written, not compiled)

Code: `net/HttpsFetcher.kt` (the only network code: https only, same-host redirects, size caps enforced while reading), `theme/ThemeRepoClient.kt`, `themecore` `RepoModels.kt` (index models, URL rules, checker) with `RepoModelsTest`. Settings, Get more themes lists the official repository and any repositories the user added (each add needs a confirmation dialog whose default button is Cancel). The separate repository project lives next to this one in `WishyLauncher-Themes/`.

Not done yet in this section: automatic update checks (updates are offered by hand in the list), and the first-run question about going online (the switch defaults to offline and is changed in Settings).

Repository layout (the separate `WishyLauncher-Themes` project, Milestone 5):

```
index.json                  themes manifest
artwork/index.json          package -> artwork manifest
themes/<id>/<version>.zip   packaged themes
themes/<id>/preview.png
```

`index.json` entries carry: `id`, `name`, `version`, `author`, `description`, `license`, `schemaVersion`, `minLauncherVersion`, `previewUrl`, `downloadUrl`, `sha256`, `sizeBytes`, `updatedAt`, `creditsUrl`. Download locations are relative to the manifest URL, and the client refuses any URL whose scheme is not HTTPS or whose host differs from the manifest's host (so a manifest cannot send the launcher elsewhere).

**Install pipeline** (`ThemeInstaller`):

1. Check declared `sizeBytes` against the limit before downloading, and stop the download if it exceeds it.
2. Download to `cache/theme-staging/<id>.zip.part`. Compare SHA-256 with the manifest. Mismatch: delete, abort.
3. `SafeUnzip` into a **new empty** `staging/<id>-<random>/` folder, checking every entry name, count, per-file and total size, and compression ratio.
4. Validate (`ThemeLoader`/`ThemeChecker`, both colour schemes at 720p and 2160p) the unpacked theme: `id` and `version` equal the manifest entry, all files allowed, all references resolve.
5. Move the staged folder to `themes/<id>.new`, then replace `themes/<id>` by renaming (old to `themes/<id>.old`, new to `themes/<id>`, delete old). If any step fails, restore the old folder. The active-theme pointer is a separate small file, updated only after the move succeeds.
6. On launcher start, any leftover `*.new`, `*.old` and staging folders are cleaned up, and an `.old` folder with no matching live folder is restored.

**Removal** deletes the folder and, if it was active, switches to the bundled Wishy theme first. **Updates** are the same pipeline, triggered when the manifest `version` is higher than the installed one.

**Trust model, honestly stated.** Hashes in the manifest protect against corrupted or truncated downloads and against a zip being swapped for another one while the manifest is untouched. They do **not** protect against someone who can edit the repository, because they could edit both the zip and its hash. Version 1 has no signatures (platform Ed25519 support is missing on older Android releases, and adding a crypto library goes against the "minimal dependencies" aim). The real protection is that themes are data only and strictly validated: the worst a malicious theme can do is look bad, hide the apps it was supposed to show (rejected by the one-`apps`-node rule and the recovery paths), or use up to 40 MB of storage. Signing can be added in a later schema version.

The default repository URL is `https://raw.githubusercontent.com/wishissue/WishyLauncher-Themes/main/` (your repository, to be created). Users can add other **theme** repositories in Settings, under these rules (implemented except where noted):

- HTTPS only; the host is shown in a confirmation dialog that says third-party themes are not reviewed.
- A third-party repository can supply themes only. **Artwork is fetched from the official repository only**, because per-app artwork requests to an unknown host would reveal which apps are installed.
- Third-party themes get the exact same validation, size limits and atomic install as official ones. They never update automatically: an update is offered and the user must accept it.
- Each theme records the repository it came from, and Settings shows it. Removing a repository does not delete its installed themes, but stops updates.
- Redirects to a different host are refused.

**Network is opt-in.** Nothing contacts a server until the user turns on online themes and/or artwork lookup (the first-run wizard asks once, default off). A single "Offline mode" switch in Settings blocks all launcher network use.

## 8. Settings (Themes, Get more themes, App banners written)

Existing from Couchy: wallpaper (gradient or photo), display (spacing, icon size, corner radius, UI scale), status bar, apps (hide, move), sections, language, save and load configuration, about.

To add: Themes (pick, install, update, remove, repositories, credits and licences), clock and date options, banner management (per-app artwork choose, reset, refresh), animation settings (full, reduced, off), cache management, reset and recovery.

All settings are reachable with the D-pad only. Text entry (a repository URL) happens inside a dialog.

**Recovery**: "Use default theme", "Clear theme data", "Reset launcher settings" and "Clear artwork cache" are always available from Settings, which is never themed. Settings opens from the Menu key even if the theme tree has no way to reach it.

## 9. Data and privacy

- Settings: one JSON blob in DataStore (`LauncherConfig`), unknown keys ignored, decode failure falls back to defaults.
- Files: `filesDir/themes/`, `filesDir/art/`, `filesDir/iconcache/`, `cache/theme-staging/`.
- No analytics, no accounts, no advertising, no Google services. The only network traffic is to the repository URLs the user has configured.
- The launcher does not change system settings or disable the system launcher. The setup wizard (from Couchy) only points the user to Android's own default-home chooser.
- `allowBackup` is now `false`, so settings, user artwork and installed themes are not uploaded to a cloud backup. Users who want a copy use the existing manual "Save configuration" export.

## 10. Build and tests

- Gradle 8.9, AGP 8.5.2, Kotlin 2.0.20 (Couchy's versions, unchanged so far). A JVM `themecore` module needs the Kotlin JVM plugin added to the root build, which is done in the source but not yet built.
- JUnit tests for `themecore` are written alongside the code and have **not** been run.
- No Android instrumentation tests exist yet. Couchy had none.

See `BUILD.md` and `INSTALL.md`.

## 11. Decisions taken

1. `allowBackup` is off.
2. Themes may omit the `sections` node. To stay safe, if a theme has no `sections` node, every `apps` node behaves as `focusScope: "all"` (one flat list of all visible apps), so no app is unreachable. Section switching with the remote's channel or page keys keeps working either way.
3. Third-party theme repositories are allowed with the rules in section 7. Artwork comes from the official repository only.
4. Network use is opt-in, with a global offline switch.

## 12. Open items

1. Final Wishy mark and banner artwork (current files are placeholders).
2. `minSdk 21` with Compose: verify on an old device.
3. Whether to add theme signing in schema version 2.
4. Everything on the device side is unverified: compile errors, Compose API differences, startup time and memory on low-end TVs, and the long-press and move-mode behaviour under each theme. `BUILD.md` section 6 is the checklist.
5. Theme fields accepted but not drawn: font `pixel` flag, `apps.wrap`, `apps.rows`, `screenTransition`; `cross` is a centred rail.
6. Not implemented: installing from a local folder, automatic theme update checks, the first-run question about going online, translations of the new screens.
