# Building Wishy Launcher

**Status: Verified build.** The project compiles cleanly and passes all unit tests and lint checks.

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

## 3. Release builds and local signing

Release builds use minification and resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`).
Because release builds use R8, test a release build on a device at least once. Keep rules for `kotlinx.serialization` are in `app/proguard-rules.pro`.

### Local release signing (offline key)

To protect release security, release signing keys NEVER leave your local machine and are NEVER stored in GitHub Secrets or CI workflows.

1. Store your release keystore and configuration outside the repository, by default at `~/.wishy-keys/`:
   - Keystore: `~/.wishy-keys/wishy-release.jks`
   - Properties file: `~/.wishy-keys/keystore.properties` (or set `WISHY_KEYSTORE_PROPS` env var to point to your file).

   `keystore.properties` format:
   ```properties
   storeFile=/home/<username>/.wishy-keys/wishy-release.jks
   storePassword=<your-store-password>
   keyAlias=<your-key-alias>
   keyPassword=<your-key-password>
   ```

2. **CRITICAL KEY SECURITY RULES**:
   - Back up your `.jks` file offline (USB drive or password manager).
   - NEVER commit key files, keystore properties, or passwords to git.
   - **If the `.jks` key is lost, existing users CANNOT update in place** (Android strictly enforces signature matching and will refuse installation of an APK signed with a different key). Users would have to uninstall the app first.

3. If `keystore.properties` does not exist:
   - `./gradlew :app:assembleRelease` produces an **unsigned** APK.
   - `./gradlew :app:assembleRelease -PlocalSign` signs with the debug key for local testing only.

### How to publish a release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts` and commit the change.
2. Run `scripts/release-local.sh` on your machine. This script:
   - Checks that the keystore properties file exists.
   - Runs `./gradlew :app:assembleRelease`.
   - Copies the APK to `WishyLauncher-v<versionName>.apk`.
   - Runs `apksigner verify --print-certs` on it and prints the SHA-256 of the signing certificate.
3. Upload the signed APK by hand:
   - On the GitHub Releases page, create a release tagged `v<versionName>` (e.g. `v1.1.1`) and attach `WishyLauncher-v<versionName>.apk`.
   - Or run from your machine:
     ```bash
     gh release create v<versionName> WishyLauncher-v<versionName>.apk \
       --title "Wishy Launcher <versionName>" --generate-notes
     ```
   - Note: The app's in-app updater only installs an `.apk` attached to the latest release whose tag is newer than the installed `versionName` (release tags must use the plain version format, e.g. `v1.1.1`). Only your locally signed APK should ever be attached to a release.

### Debug builds warning

> [!WARNING]
> The debug APK built in CI or via `./gradlew :app:assembleDebug` is signed with a different, throwaway debug key.
> It **cannot be installed over a release build** (or vice versa). Testers must uninstall the existing app first.
> **NEVER attach debug APKs to releases.**

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
