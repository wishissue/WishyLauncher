# Artwork attribution

## In this project (the launcher)

| Item | Author | Licence |
|---|---|---|
| `app_icon.xml`, `app_banner.xml`, `ic_wishy_mark.xml` (placeholder launcher icon, banner and mark) | Wishy Launcher contributors | GPL-3.0-only |
| Built-in theme `wishy` (colours, shapes, text styles, its wallpapers and placeholder cover) | Wishy Launcher contributors | GPL-3.0-only |
| Bundled wallpapers `wp_*_dark.webp` and `wp_*_light.webp` in `app/src/main/res/drawable-nodpi/` (eight wallpapers, each in a dark and a light tone) | Wishy Launcher contributors | GPL-3.0-only |

All the wallpapers and the Wishy theme's placeholder cover are drawn from scratch by `tools/gen-wallpapers.py` (colour gradients, soft blooms and film grain). None is based on a photograph or on anyone else's artwork, and the script reproduces them exactly. The only third-party files bundled are the Plus Jakarta Sans fonts, see `THIRD_PARTY_LICENSES.md`. No photographs or per-app banners are bundled with the launcher.

## App banners

The launcher can show a banner that a user picked, or one downloaded from the official `WishyLauncher-Themes` repository. Downloaded banners are **not** part of this project's source or APK. Each entry in the repository's `artwork/index.json` must name the author and licence of the picture, and the Settings screen for theme credits and the repository's `CREDITS.md` show them. At the time of writing that index is empty.

Rules for artwork in the repository:

- Only pictures the contributor made, or that are licensed for redistribution (for example CC0, CC-BY, or the app owner's written permission), are accepted. The licence and source are recorded per file.
- Logos and trademarks belong to their owners. Their appearance in an app's own banner, which the launcher shows from the app itself, is the app developer's decision. The launcher does not copy those banners into this repository.
- A picture can be withdrawn at any time by deleting its entry. Launchers drop it on the next cache clear or refresh.

## Third-party themes

Themes from other repositories are not covered by this file. Each such theme lists its own credits, shown in Settings, Themes, Theme credits and licences.
