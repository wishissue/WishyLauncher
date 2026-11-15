# Artwork attribution

## In this project (the launcher)

| Item | Author | Licence |
|---|---|---|
| `app_icon.xml`, `app_banner.xml`, `ic_wishy_mark.xml` (placeholder launcher icon, banner and mark) | Wishy Launcher contributors | GPL-3.0-only |
| Built-in theme `wishy` (colours, shapes, text styles, its wallpapers and placeholder cover) | Wishy Launcher contributors | GPL-3.0-only |
| Bundled wallpaper `wp_wishy_*.webp` (the Wishy Blue wallpaper, dark and light) | Wishy Launcher contributors | GPL-3.0-only |
| Bundled wallpapers `wp_<name>_dark.webp` / `wp_<name>_light.webp` for sheet, fold, amber, glass_chip, adwaita, curvy, curvaturingster, dithered_sun, pills, symbolic | GNOME Backgrounds contributors, https://gitlab.gnome.org/GNOME/gnome-backgrounds (mirror: https://github.com/GNOME/gnome-backgrounds). Individual author credits are embedded in the original images. | CC BY-SA 3.0 US |
| Bundled wallpapers `wp_flow.webp`, `wp_volna.webp`, `wp_milkyway.webp`, `wp_altai.webp` | Sandra Smukaste (Flow), Nikita Babin (Volna), ruvkr (Milky Way), Alesya Khoteeva (Altai); from KDE Plasma wallpapers, https://github.com/KDE/plasma-workspace-wallpapers | CC BY-SA 4.0 |
| Store wallpapers in `wallpapers/` (served from `repo/wallpapers/`): Mountain (Andy Betts, CC BY-SA 4.0), Shell (Lucas Andrade, CC BY-SA 4.0), Ice Cold (Santiago Cézar, CC BY-SA 4.0), Honeywave (Ken Vermette, CC BY-SA 4.0), Cascade and Opal (Ken Vermette, LGPL-3.0) from KDE; Spheres, Blend pills, Glass stripes, Pixel icons, Pixel pusher, Progress, Tarka, Candy pills and Tubes from GNOME Backgrounds (CC BY-SA 3.0) | As listed; full details per picture in `wallpapers/credits.json` | See left |

The Wishy Blue wallpaper and the Wishy theme's placeholder cover are drawn from scratch by `tools/gen-wallpapers.py`. The ten GNOME wallpapers are the originals from GNOME Backgrounds, centre-cropped to 16:9, scaled to 1920 x 1080 and saved as WebP. Each has the dark and light version that GNOME publishes, so it matches the launcher's two colour modes. They are not changed in any other way. CC BY-SA 3.0 asks for credit and for the same licence on adapted copies of the pictures; the pictures are separate files, they are not part of the GPL-licensed code. The only other third-party files bundled are the fonts and icons listed in `THIRD_PARTY_LICENSES.md`.

## Community wallpaper sources (downloaded by the user, not bundled)

The wallpaper store can list pictures from public GitHub repositories. These pictures are **not** part of this project's source or APK; they are fetched only when a user opens that source and chooses a picture, and each one is credited in the store and in the picture's saved details.

| Source | Owner | Licence |
|---|---|---|
| CozyPixels, https://github.com/SleepyCatHey/CozyPixels (built-in source) | SleepyCatHey and the repository's contributors | MIT for the repository. Individual pictures can have their own original authors or rights, so check the repository before redistributing them. |

Repositories a user adds themselves are shown with their `owner/repo` name and "See repository" as the licence, because Wishy cannot know their terms.

## App banners

The launcher can show a banner that a user picked, or one downloaded from the official `WishyLauncher-Themes` repository. Downloaded banners are **not** part of this project's source or APK. Each entry in the repository's `artwork/index.json` must name the author and licence of the picture, and the Settings screen for theme credits and the repository's `CREDITS.md` show them. At the time of writing that index is empty.

Rules for artwork in the repository:

- Only pictures the contributor made, or that are licensed for redistribution (for example CC0, CC-BY, or the app owner's written permission), are accepted. The licence and source are recorded per file.
- Logos and trademarks belong to their owners. Their appearance in an app's own banner, which the launcher shows from the app itself, is the app developer's decision. The launcher does not copy those banners into this repository.
- A picture can be withdrawn at any time by deleting its entry. Launchers drop it on the next cache clear or refresh.

## Third-party themes

Themes from other repositories are not covered by this file. Each such theme lists its own credits, shown in Settings, Themes, Theme credits and licences.
