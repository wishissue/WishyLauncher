package org.wishy.launcher.net

/** Addresses of the official repository (the separate WishyLauncher-Themes project). */
object Repos {
    const val OFFICIAL_BASE = "https://raw.githubusercontent.com/wishissue/WishyLauncher-Themes/main/"
    const val OFFICIAL_THEMES_INDEX = OFFICIAL_BASE + "index.json"
    const val OFFICIAL_ART_INDEX = OFFICIAL_BASE + "artwork/index.json"

    fun isOfficial(indexUrl: String) = indexUrl == OFFICIAL_THEMES_INDEX
}
