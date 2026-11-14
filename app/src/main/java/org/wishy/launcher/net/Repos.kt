package org.wishy.launcher.net

/**
 * Everything the launcher fetches comes from this one repository (wishissue/WishyLauncher):
 *  - `repo/index.json`            the theme list
 *  - `repo/themes/<id>.zip`       theme packages
 *  - `repo/artwork/index.json`    per-app banner list
 *  - `repo/artwork/<file>`        banner pictures
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

    /** Latest published release, as JSON. */
    const val LATEST_RELEASE_API = "https://api.github.com/repos/$OWNER_REPO/releases/latest"
    /** Human-readable page for the latest release (downloads live here). */
    const val RELEASES_PAGE = "https://github.com/$OWNER_REPO/releases/latest"

    fun isOfficial(indexUrl: String) = indexUrl == OFFICIAL_THEMES_INDEX
}
