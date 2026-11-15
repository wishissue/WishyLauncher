package org.wishy.launcher.net

/**
 * Almost everything the launcher fetches comes from this one repository (wishissue/WishyLauncher). The one exception is
 * the community wallpaper source below (SleepyCatHey/CozyPixels), which is listed through GitHub's API and downloaded
 * from raw.githubusercontent.com, and only when the person opens that tab in the wallpaper store.
 *
 * Official repository (wishissue/WishyLauncher):
 *  - `repo/index.json`            the theme list
 *  - `repo/themes/<id>.zip`       theme packages
 *  - `repo/artwork/index.json`    per-app banner list
 *  - `repo/artwork/<file>`        banner pictures
 *  - `repo/wallpapers/index.json` the wallpaper store list, `repo/wallpapers/<file>.webp` the pictures
 *  - GitHub Releases              new launcher versions
 *
 * `repo/` is built by `tools/build_repo.py` (a GitHub Action runs it on every push), so the hashes
 * in the indexes always match the files.
 */
object Repos {
    private const val OWNER_REPO = "wishissue/WishyLauncher"

    const val OFFICIAL_BASE = "https://raw.githubusercontent.com/$OWNER_REPO/main/repo/"
    const val OFFICIAL_THEMES_INDEX = OFFICIAL_BASE + "index.json"
    const val OFFICIAL_ART_INDEX = OFFICIAL_BASE + "artwork/index.json"
    const val OFFICIAL_WALLPAPERS_BASE = OFFICIAL_BASE + "wallpapers/"
    const val OFFICIAL_WALLPAPERS_INDEX = OFFICIAL_WALLPAPERS_BASE + "index.json"

    /**
     * Community wallpaper repository: plain folders of pictures, `<Palette>/<Category>/<file>`, no index. `HEAD` means
     * "the default branch", so this keeps working if the repository's branch is renamed.
     */
    private const val COZY_REPO = "SleepyCatHey/CozyPixels"
    const val COZY_TREE_API = "https://api.github.com/repos/$COZY_REPO/git/trees/HEAD?recursive=1"
    const val COZY_RAW_BASE = "https://raw.githubusercontent.com/$COZY_REPO/HEAD/"
    const val COZY_HOME = "https://github.com/$COZY_REPO"

    /** Latest published release, as JSON. */
    const val LATEST_RELEASE_API = "https://api.github.com/repos/$OWNER_REPO/releases/latest"
    /** Human-readable page for the latest release (downloads live here). */
    const val RELEASES_PAGE = "https://github.com/$OWNER_REPO/releases/latest"

    fun isOfficial(indexUrl: String) = indexUrl == OFFICIAL_THEMES_INDEX
}
