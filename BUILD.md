# Building Wishy Launcher

**Nothing in this project has been compiled, built or run by its author.** The source was written without an Android SDK, a compiler or a device. The first build will probably show some compile errors. Treat them as normal work on a fresh project, and fix them in the file named by the error. Section 5 lists the places most likely to need attention.

## 1. What you need

| Tool | Version | Notes |
|---|---|---|
| JDK | 17 | Android Studio's bundled JDK 17 is fine. |
| Android Studio | Koala (2024.1) or newer | It brings the Android SDK manager. |
| Android SDK | Platform 34 (compileSdk 34) and Build-Tools 34 | Install through Android Studio. |
| Gradle | 8.9 | Downloaded by the included wrapper (`./gradlew`). |
| Android Gradle Plugin | 8.5.2 | Set in `build.gradle.kts`. |
| Kotlin | 2.0.20 | Set in `build.gradle.kts`. |

An internet connection is needed for the first build to download Gradle and the libraries from Google's Maven and Maven Central. After that Gradle can work offline.

## 2. Open and build

1. Open this folder in Android Studio (File, Open). Let Gradle sync finish. If it asks for an SDK location, accept the default or point it at your installed SDK. Android Studio then writes `local.properties`, which is git-ignored.
2. Run the plain JVM tests first. They do not need an emulator:
   ```
   ./gradlew :themecore:test
   ```
   They cover safe unzipping, path rules, merge patch, the installer, the repository index rules and the loading of all four bundled themes in both colour schemes at 720p, 1080p and 2160p. If `everyBundledThemeLoadsInBothSchemesAndSizes` fails, the message names the theme and the reason.
3. Build a debug APK:
   ```
   ./gradlew :app:assembleDebug
   ```
   The file is `app/build/outputs/apk/debug/app-debug.apk`.
4. Optional checks: `./gradlew :app:lintDebug`.

## 3. Release builds

`./gradlew :app:assembleRelease` produces an **unsigned** APK, with minification and resource shrinking on. Signing is deliberately not set up in this repository:

- Add `-PlocalSign` to sign with the debug key, for testing only.
- For a real release, create your own key store and either sign the APK with `apksigner`, or add a signing configuration of your own. Never commit a key store or its passwords.

Because release builds use R8, test a release build on a device at least once. The keep rules for `kotlinx.serialization` are in `app/proguard-rules.pro` and cover both `org.wishy.launcher` and `org.wishy.theme`. A theme that loads in a debug build but is rejected in a release build points at a missing keep rule.

## 4. Project layout

```
app/                 Android application (Kotlin, Jetpack Compose for TV)
themecore/           Plain Kotlin/JVM library. No Android classes. Theme model, loader, checker, installer, repository rules.
themes/              The four bundled themes. Packed into the APK as assets (see app/build.gradle.kts).
schema/              JSON Schema for themes
```

The launcher and the theme repository are separate projects. The theme repository (`WishyLauncher-Themes`) is built with plain Python and has its own README.

## 5. Places most likely to need fixing on the first build

1. **Experimental Compose APIs** used in `theme/ThemedHome.kt`: `LocalBringIntoViewSpec`, `BringIntoViewSpec` and `focusRestorer`. They are opted in, but their exact signatures differ a little between Compose versions. The BOM pinned here is 2024.09.03.
2. **`theme/ThemedCard.kt`**: long-press detection from key repeats, the `graphicsLayer` State reads and the focus indicator drawing.
3. **`theme/ThemeShapes.kt`**: the pixel, chamfer and bevel shapes, and the tiled overlay shader built with `ImageShader`.
4. **`ui/LauncherScreen.kt`**: brace and `when` structure around the new themed branch, and the move-mode helper functions that now route differently when a theme is active.
5. **Imports**: several new files list imports by hand. An unused import is only a warning; a missing one is an error.
6. **Lint**: new strings live in `res/values/strings_themes.xml`, marked to ignore missing translations. Other languages fall back to English.

## 6. First run checklist on a device or emulator

After it builds, in this order:

1. Install the APK on an Android TV or Google TV device (see `INSTALL.md`). Open it from the app list.
2. Home screen shows the Wishy theme with your apps, a clock and section tabs. Move with the D-pad. OK launches an app. Long-press OK opens the app menu. Menu opens Settings.
3. Settings, Themes: switch colour mode between Light and Dark.
4. Settings, Themes: set Animations to Off, then Reduced. Set theme size to 70% and 130%.
5. Move an app (long-press, Move) in each theme. Check that the focus stays on the moved app and that Back or OK ends the move.
6. Switch to Classic layout and back. Use "Reset to the Wishy theme".
7. With Offline mode on, confirm that the launcher works normally. Turn it off, open Get more themes. The official repository does not exist until you create it, so a "did not work" message is expected until then.
8. App banners: choose a picture for one app. Confirm it appears, then remove it.
9. Try a 720p and a 4K output if you can, and a low-memory device if you have one. Note any slow scrolling.

Please keep notes of anything that fails, with the theme name and the screen. That is what makes the next round of fixes quick.

## 7. Making your own build recognisable

Change `applicationId`, `versionCode` and `versionName` in `app/build.gradle.kts` if you publish your own build. Replace `app_icon.xml`, `app_banner.xml` and `ic_wishy_mark.xml` with your own artwork. The licence (GPL-3.0) requires that you offer the source of any build you distribute.
