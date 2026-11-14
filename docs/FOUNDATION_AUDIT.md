# Wishy Launcher — Foundation Audit (Milestone 1)

Audit date: 2026-11-13. Nothing in this audit was compiled, built or run on a device. All findings come from reading source and documentation.

## 1. What was inspected, and how

| Project | Link | Commit inspected | Access |
|---|---|---|---|
| Couchy Launcher | https://github.com/conreo/couchy-launcher | `d3dcd70d3dd3b261108d25d72569671aee23b2d6` (2026-08-01) | Shallow clone, full source read |
| LTvLauncher | https://github.com/leanbitlab-org/LtvLauncher | `9c1af45fa3aaf2431b1972db0994b35d57967c68` (2026-11-12) | Shallow clone, manifest, pubspec, structure and key files read |
| CleanYourTV | https://github.com/Gathin23/CleanYourTV | `b95380483c9716b0bce6c151fa8de6b1f5000265` (2026-11-11) | Shallow clone, launcher module, theme system and docs read |
| Wishy Browser | https://github.com/wishissue/WishyBrowser | `5be0153de788d792f357feb9577393669e5e937b` (2026-11-05) | Shallow clone, branding, NOTICE and credits read |
| ES-DE source | https://gitlab.com/es-de/emulationstation-de | **Not inspected from GitLab** (see below) | Blocked |
| ES-DE themes list | https://gitlab.com/es-de/themes/themes-list | **Not inspected** (see below) | Blocked |
| ES-DE (alternate) | https://github.com/RetroDECK/ES-DE | `15e1b236eafd2864bd99ffcc9853d2efa2a3916c` (2026-09-26) | Shallow clone, theme docs, parser and bundled themes read |

### GitLab was inaccessible

`git clone` of both `gitlab.com/es-de/...` URLs failed with `CONNECT tunnel failed, response 403`. The session's proxy status endpoint records these as policy denials for `gitlab.com:443`. I did not try to bypass this. WebFetch of the GitLab URL was also refused (not on the allowlist).

What I used instead: the RetroDECK project's GitHub fork of ES-DE. Its `update_from_upstream.sh` fetches from the official GitLab repository, and it contains the full tree: C++ source, `THEMES.md`, `THEMES-DEV.md`, `capabilities.xml` examples and the bundled themes. Caveats:

- It is a fork, not the official repo. The commit hash above is the fork's, and I cannot verify it equals any official ES-DE commit. Details may differ from official ES-DE.
- The `themes-list` repository (the catalogue of community themes) was not available at all. I have **not** reviewed community ES-DE themes beyond the ones bundled in the fork (`linear-es-de`, `modern-es-de`, `slate-es-de`).
- A search surfaced a RetroDECK-hosted Forgejo mirror, but its git data was unreadable, so it contributed nothing.

### Not available

GitHub API access was not enabled for this session, so I have no star counts, commit counts, issue activity or release cadence for any repo. Maturity judgements below rely on version numbers, tests present and code structure only.

## 2. Findings per project

### 2.1 Couchy Launcher — GPL-3.0

- **Stack:** Kotlin, Jetpack Compose, `androidx.tv:tv-material`, DataStore, kotlinx.serialization, Media3 ExoPlayer (for video wallpaper). AGP 8.5.2, Kotlin 2.0.20, Gradle 8.9. minSdk 21, targetSdk 34, version 1.0.6.
- **Size:** 15 Kotlin files, about 4,700 lines. Two files hold most of it: `SettingsSheet.kt` (1,497 lines) and `LauncherScreen.kt` (1,562 lines).
- **Features that overlap our requirements:** three layouts (carousel, grid, dock); sections you rename and reorder; hide apps; wallpapers (gradient, photo, video); clock/date; UI scale, spacing, icon size and corner radius settings; D-pad-only design; first-run wizard; JSON import/export of configuration; 18 languages; baseline profile for startup.
- **App discovery** (`AppRepository.kt`): queries both `LEANBACK_LAUNCHER` and `LAUNCHER` intents, reuses entries across rescans, loads the app's leanback banner or falls back to its icon, caches WebP bitmaps on disk, and limits scan parallelism to protect weak TV CPUs. This is solid and directly reusable.
- **Config model** (`Config.kt`): one `@Serializable` data class persisted as a JSON string in DataStore, with `ignoreUnknownKeys` and a decode fallback to defaults.
- **Focus handling** (`AppCard.kt`): handles long-press OK on a TV remote, including suppressing the click that follows a long press. Real-world detail worth keeping.
- **Theme system:** none. "Theme.kt" is a colour scheme and accent index. Layouts are three hard-coded constants.
- **Permissions:** `QUERY_ALL_PACKAGES`, `INTERNET`, `ACCESS_NETWORK_STATE`, `KILL_BACKGROUND_PROCESSES`, `REQUEST_DELETE_PACKAGES`. `usesCleartextTraffic="true"`. `allowBackup="true"`.
- **Problems for us:**
  - `INTERNET` and cleartext traffic exist only for built-in aerial videos hosted on Apple and Amazon CDNs. Our requirements say no unnecessary network use, so this would be removed and rebuilt around a narrow, HTTPS-only theme and artwork client.
  - Aerial manifest, `BuiltinAerials.kt` and the Media3 dependency would be removed (the project's own `LICENSING.md` notes that footage belongs to third parties).
  - No unit or instrumentation tests exist.
  - The two large files need splitting before they can host a theme renderer.
  - A "Buy Me a Coffee" button and Cookie font heading are in the app UI and README; both would be dropped.
- **Licensing:** GPLv3 for the code. Bundled third-party: Twemoji couch artwork (MIT, would be removed or replaced), Material icon path data (Apache-2.0), Cookie font (SIL OFL 1.1). Our fork would have to remain GPLv3 and keep the copyright notice and attribution.

### 2.2 LTvLauncher — GPL-3.0 (fork of FLauncher)

- **Stack:** Flutter 3.24.5 / Dart, plus a Java Android layer. Drift (SQLite), provider, `sqlite3_flutter_libs`. About 18,800 lines of Dart (including generated localization), 23 Java files.
- **Lineage:** forked from FLauncher (osrosal/flauncher, originally etienn01 on GitLab). The Java sources still exist under two package names (`com.leanbitlab.ltvL` and `me.efesser.flauncher`), which suggests incomplete cleanup.
- **Features:** categories, continue-watching rows, weather, TV input switching, notification overlays, OLED screensaver, backup/restore.
- **Permissions:** by far the heaviest of the four: `SYSTEM_ALERT_WINDOW`, `WRITE_SETTINGS`, `PACKAGE_USAGE_STATS`, `READ_TV_LISTINGS`, EPG and watch-next read/write, `RECEIVE_BOOT_COMPLETED`, an accessibility service and a notification listener service.
- **Theme system:** none beyond a few visual style options.
- **Suitability:** poor fit. Flutter ships its own engine (a large baseline footprint) which works against "fast startup and low memory usage", the permission surface conflicts with "do not unnecessarily modify Android system settings", and the Dart/Flutter toolchain adds a build dependency for a Kotlin-centric developer. Some ideas (watch-next rows) are out of scope.
- **Reusable:** nothing I would copy. GPL-3.0, so compatible if needed.

### 2.3 CleanYourTV — MIT

- **What it really is:** primarily a debloat toolkit (Python scripts, 468 TV model lists, add-ons, per-device docs). The launcher is one part, in `launcher/`.
- **Launcher stack:** plain Java with Views, `recyclerview` and `dynamicanimation` only. minSdk 26. Documented as about 572 KB with no network permission and no background services. Strong evidence of memory discipline (`MemoryTrimPolicyTest`, reduced-motion switch).
- **Size:** 15,300 lines of Java in the main source set. `MainActivity.java` alone is 4,104 lines; `Config.java` 947; `AppAdapter.java` 1,005; `Cards.java` 864. The package contains many features unrelated to our brief (lunar and Shamsi calendars, race-weekend and game-day cards, seasonal skins, petal showers, bat flights).
- **Tests:** about 45 JVM unit test files. This is the best-tested of the four.
- **Theme system:** a `themes/<name>/config.json` overlay. It is a flat key/value config (columns, dock apps, wallpaper rotation, opacity, glow) over built-in XML defaults, not a layout format. Unknown keys are ignored, and an unparsable file is ignored wholesale, so a theme cannot break Home. That fail-safe behaviour is worth copying as a principle. There are about 30 community themes, all of which are wallpaper and spacing variants of one layout. `ThemeFiles.java` is a strict filename allowlist for the theme directory: good practice.
- **Permissions:** `WRITE_SECURE_SETTINGS` (granted via ADB, used for its Home-key service), location, Wi-Fi state, and vendor-specific TV source permissions. It also supports an accessibility-style Home key service. This conflicts with our "do not modify system settings" requirement.
- **Strategy mismatch:** the project's own contributing guide tells people that a "whole launcher, a restyle, a port or a big feature" belongs in a separate repo, and wants `launcher/` kept small. Large changes upstream are not welcome, though forking is allowed by MIT.
- **Licensing:** MIT for code. Wallpapers inside themes carry *different* licences (NASA public domain, CC0, CC BY, CC BY-SA, Unsplash) documented per theme in `CREDITS.md`. This is a good model for how Wishy should record artwork credits. Outfit font: SIL OFL 1.1.
- **Suitability:** excellent performance and vendor-hardening ideas, and a permissive licence, but the UI is imperative View code coupled to many features. Turning it into a declarative multi-layout renderer would mean rewriting most of `MainActivity` and `AppAdapter`.

### 2.4 ES-DE (via the RetroDECK fork) — MIT

- **What it is:** a C++/OpenGL desktop and Android frontend for emulators. It cannot be reused as code in an Android launcher. It is relevant for its **theme architecture**.
- **Theme format:** XML. A theme folder contains `capabilities.xml` (mandatory), `theme.xml`, includes, fonts, and assets. Core ideas:
  - **Normalised layout:** `pos` and `size` are fractions of the parent (0 to 1), which is how one theme scales across resolutions.
  - **Views and elements:** named elements (`image`, `text`, `carousel`, `grid`, `textlist`, `video`, `animation`, `badges`, `datetime`, `clock`, `helpsystem` and others) with properties; multiple files can contribute to the same named element.
  - **Variants:** theme-declared profiles (for example a carousel layout versus a text list), selectable by the user. They are declared in `capabilities.xml` first.
  - **Aspect ratios:** per-aspect-ratio overrides from a fixed list.
  - **Colour schemes, font sizes, languages, transitions:** declared and switchable.
  - **zIndex, includes, variables, triggers:** composition and conditional behaviour.
- **Safety:** ES-DE themes are declarative, but I saw no evidence of path sandboxing or size limits in `ThemeData.cpp` (it checks that files exist). I would not assume ES-DE's loader is a safe model for downloaded content. Wishy's loader must add its own path confinement, file-type allowlists and size limits.
- **Licensing:**
  - ES-DE code: MIT (Northwestern Software AB, Leon Styhre, Alec Lofquist). Reusing ideas is unrestricted; copying code would need attribution, though none is Android-reusable anyway.
  - **Bundled themes differ.** `linear-es-de` is MIT. `modern-es-de` and `slate-es-de` carry a CC-BY-NC-SA licence file, which **prohibits commercial distribution** and requires share-alike. `modern-es-de` also uses third-party fonts and logotypes with their own terms. **No ES-DE theme assets or artwork will be copied into Wishy.** We will adapt *concepts* only.
- **Fidelity of findings:** based on the RetroDECK fork's `THEMES-DEV.md` (3,836 lines) and parser source, not the official repo.

### 2.5 Wishy Browser (branding reference) — GPL-3.0

- **Stack:** Kotlin, Jetpack Compose, Material 3. GeckoView (MPL-2.0, unmodified binary). `NOTICE.md` explains the project deliberately avoided copying GPL code from TV Bro.
- **Brand tokens** (`ui/theme/WishyBrand.kt`): signature blue `#0166FF` (dark-mode primary `#66A3FF`); light background `#E6F1FF`, dark background `#080B12`; surfaces `#FFFFFF` and `#111725`; primary container `#D6E4FF`; on-container `#001B3E`.
- **Typography:** Plus Jakarta Sans (UI) and Bagel Fat One (app name), both SIL OFL 1.1, with the OFL text included.
- **Credits practice:** `NOTICE.md` and `CURSOR_CREDITS.md` already give a per-asset licence table, a good template for our credits file.
- **Licensing note for planning:** the repo's `LICENSE` is GPL-3.0. Wishy Launcher can be GPL-3.0 as well, which makes a Couchy fork (GPL-3.0) licence-compatible. Please confirm this is the licence you want for the launcher.

## 3. Comparison

| | Couchy | LTvLauncher | CleanYourTV launcher | ES-DE (fork) |
|---|---|---|---|---|
| Language / UI | Kotlin, Compose TV | Dart, Flutter + Java | Java, Views | C++, OpenGL |
| Licence | GPL-3.0 | GPL-3.0 | MIT | MIT (some bundled themes CC-BY-NC-SA) |
| Layout variety | 3 fixed layouts | few | 1 grid and dock, key-tweaked | fully data-driven |
| Real theme system | No | No | Flat config overlay | Yes (XML) |
| Network permission | Yes (aerials only) | Not required for core features | None | n/a |
| System-level permissions | Few | Many | `WRITE_SECURE_SETTINGS` | n/a |
| Tests | None | Some (Dart `test/`) | About 45 JVM tests | n/a |
| Footprint risk | Compose (mitigated by baseline profile) | Flutter engine | Very small | n/a |
| D-pad quality | Good, long-press handled | Good | Excellent, extensive vendor handling | n/a |
| Reusable for Wishy | Scanner, config, focus, settings patterns | Little | Principles, remote-key ideas, MIT code if attributed | Concepts only |

## 4. The theme engine must be new regardless

No launcher inspected has a theme system that changes layout structure. Couchy has fixed layouts, CleanYourTV overrides config keys, and ES-DE has the right ideas but lives in C++. Whichever foundation is chosen, the declarative JSON theme engine, schema, installer and artwork manager are new work.

Ideas to adapt from ES-DE (concepts, not code): normalised screen-fraction positions; named element types; declared capabilities and variants; aspect-ratio overrides; z-order; includes; declared transitions; mandatory capabilities declaration before load. Ideas to add that ES-DE does not give us: JSON Schema validation, path confinement, hashes, size limits, atomic install.

## 5. Recommendation

**Fork Couchy Launcher as the foundation, keep its app-scanning, config-persistence and focus-handling code, strip its network and video features, and build the theme engine as a new, separate module.**

Why Couchy:

1. **Language and UI toolkit match the existing Wishy ecosystem.** Wishy Browser is Kotlin and Compose with M3 branding, so the project stays in one stack you already maintain.
2. **Compose suits a renderer.** A JSON theme engine that maps declarative nodes to composables, with layouts expressed as `Row`, `Column`, `LazyRow`, `LazyVerticalGrid` and `Box` with fractional placement, is natural in Compose and awkward in hand-written Views.
3. **The reusable code is high quality.** `AppRepository` (discovery, banner extraction, caching, rescans) and the long-press D-pad handling are the hardest launcher-specific parts and already work the way we want.
4. **Licence fit.** GPL-3.0 matches Wishy Browser's licence, with attribution preserved.
5. **Small enough to take over.** About 4,700 lines is easy to restructure.

What we take: `AppRepository` scanning and cache, `ConfigStore` pattern, `AppCard` focus and long-press logic, settings-sheet navigation patterns, baseline-profile setup, language list.

What we remove: aerial videos and Media3, the Buy Me a Coffee UI, Twemoji couch artwork and Cookie font, `INTERNET` as a blanket permission (replaced by a narrow, HTTPS-only client for theme and artwork repositories), `usesCleartextTraffic`, `KILL_BACKGROUND_PROCESSES`.

What we add: tests (none exist), file splitting, the theme engine, banner manager, theme installer and TV-friendly settings.

What we borrow in spirit from CleanYourTV (MIT, with attribution if code is ever copied): fail-safe parsing (a bad theme can never break Home), reduced-motion switch, memory-trim policy, per-theme `CREDITS.md` practice.

### Alternatives considered

- **CleanYourTV launcher as the base:** best performance record and test coverage, but its UI code is a 4,100-line activity tied to features we do not want and to `WRITE_SECURE_SETTINGS`. Reaching a multi-layout theme renderer would mean replacing most of its UI.
- **Fresh Compose project borrowing from Couchy:** lowest legacy, highest effort. Couchy at 4,700 lines does not justify a from-scratch start, and your brief prefers adapting.
- **LTvLauncher:** rejected for Flutter weight, permission surface and toolchain mismatch.

### Risks of the recommended path

- Couchy is young (1.0.6), untested and has large files. We would be taking over its quality, not inheriting it. I could not check its issue history or maintenance activity.
- Compose has a larger cold-start and memory cost than Views. The baseline profile helps, but the "fast startup, low memory" goal needs measurement on a low-end device, which only you can do. CleanYourTV's footprint shows how much headroom a Views approach has.
- `QUERY_ALL_PACKAGES` is required for a launcher but is restricted on Google Play. This project is sideloaded or F-Droid, so it is fine, but you should know.
- Couchy's `minSdk 21` is lower than we may want. Compose TV works there, but some behaviour differs on old Android TV boxes.
- ES-DE community themes were not reviewed (themes-list blocked), so "creative ES-DE theme" inspiration for the Experimental theme is based only on three bundled themes and general knowledge, not a survey.

## 6. Decisions needed from you

1. Approve (or change) the foundation: **Couchy fork, new theme engine module**.
2. Confirm the launcher licence: **GPL-3.0** (matches Wishy Browser and Couchy).
3. Confirm the application ID and package name (for example `org.wishy.launcher`) and that Wishy Browser's `org.wishy.browser` naming style is right.
4. Should the launcher keep `minSdk 21`, or raise it (CleanYourTV uses 26)? Raising simplifies code and security handling; lowering supports older boxes.
5. If you can, grant access to the ES-DE GitLab repos (or paste the `themes-list` content) so I can review community themes. Otherwise I will proceed with what is documented here.

I have not started Milestone 2 and will not until you approve.

## 7. Raw evidence

Everything above was read from clones in `/tmp/research/` during this session. Commit hashes are in section 1. Licence files inspected: `LICENSE` and `LICENSING.md` (Couchy), `LICENSE` (LTvLauncher, CleanYourTV, Wishy Browser, RetroDECK/ES-DE), `themes/*/LICENSE` and `CREDITS.md` (ES-DE bundled themes), `themes/*/CREDITS.md` (CleanYourTV), `NOTICE.md` and `CURSOR_CREDITS.md` (Wishy Browser).
