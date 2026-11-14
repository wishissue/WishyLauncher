# Installing Wishy Launcher

Wishy Launcher is not on any store. You build it (see `BUILD.md`) or receive an APK from someone you trust, and install it yourself. It never disables your TV's own launcher.

## 1. Before you start

- An Android TV or Google TV device, Android 5.0 (API 21) or newer. Newer is better: older and low-memory devices may feel slower.
- Turn on **Developer options** (Settings, Device Preferences, About, press Build several times), then **USB debugging**, or allow **Install unknown apps** for the app you will use to open the APK.

## 2. Install the APK

Pick one way.

**A. From a computer, with adb (most reliable)**

1. Find the TV's IP address (Settings, Network) and enable network debugging in Developer options.
2. On the computer: `adb connect <tv-ip>:5555`
3. `adb install -r app-debug.apk`

**B. From a USB stick**

1. Copy the APK to a USB stick and plug it into the TV.
2. Open a file manager app on the TV, select the APK and allow installation when asked.

**C. From a download**

Use a "downloader" style app on the TV to fetch the APK from an address you control, then open it.

## 3. Make it your home screen (optional)

Android decides which launcher is "home". Wishy Launcher does not change system settings.

1. Open Wishy Launcher from the app list. A short setup wizard appears the first time.
2. When asked, choose Wishy Launcher as the home app in Android's own chooser. Some TVs hide this chooser. If yours does, look under Settings, Apps, Default apps, Home app, or use the HOME button after the first launch and choose "Always".
3. To go back to the original launcher, use the same setting, or uninstall Wishy Launcher.

On some Google TV devices the system keeps launching its own home screen. That is a limit of the device, and it is not something this app can override. You can still open Wishy Launcher from the app list.

## 4. Using it

- **D-pad** moves focus. **OK** opens an app. **Long-press OK** opens the app menu (open, move, hide, app info, close, uninstall).
- **Menu** opens Settings from anywhere.
- **Channel up/down or Page up/down** switch sections.
- Settings, **Themes** chooses the look, colour mode, size, animations, clock and date. **Reset to the Wishy theme** is always there if something looks wrong. **Classic layout** gives the original grid, carousel or dock.

## 5. Privacy

- There are no accounts, ads or analytics.
- **Offline mode is on by default.** The launcher makes no network requests at all until you turn it off in Settings, Themes, or from Get more themes.
- When it is off, the launcher contacts only the theme repositories you use, and (if you turn on artwork lookup) the official repository's artwork list. Your list of installed apps is never sent anywhere: the launcher downloads one list and compares it on the device.
- Themes are data only. They cannot run code.

## 6. Updating and removing

- To update, install the new APK over the old one with the same signing key (`adb install -r`). If you switch between a debug and a release key, Android asks you to uninstall first, which clears settings.
- To remove: uninstall it in Android Settings, Apps. Its settings, themes and cached artwork are deleted with it. `allowBackup` is off, so nothing is stored in a cloud backup.

## 7. If something goes wrong

- **A theme looks broken or the screen is empty:** press Menu, open Themes, choose **Reset to the Wishy theme**, or choose **Classic layout**.
- **Settings does not open:** press Menu. The Menu key always opens Settings, even if a theme has no settings button.
- **Still stuck:** uninstall and reinstall.
- **Reporting a problem:** note the theme, the screen resolution and what you pressed.
