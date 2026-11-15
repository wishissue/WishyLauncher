package org.wishy.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import org.wishy.launcher.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.compose.runtime.Immutable

@Serializable
data class CategoryCfg(
    val id: String,
    val name: String,
)

@Immutable
@Serializable
data class LauncherConfig(
    val categories: List<CategoryCfg> = listOf(
        CategoryCfg(AppRepository.ALL_APPS_ID, "All Apps"),
        CategoryCfg("streaming", "Streaming"),
        CategoryCfg("games", "Games"),
        CategoryCfg("music", "Music"),
        CategoryCfg("apps", "Apps"),
    ),
    /** package -> section ids the user assigned (an app may be in several) */
    val sections: Map<String, Set<String>> = emptyMap(),
    /** category id -> explicit package order */
    val order: Map<String, List<String>> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    /** packages seen on the last scan — new installs are auto-added to the first section */
    val knownApps: Set<String> = emptySet(),
    val wallpaper: Int = 1, // Aurora
    val useCustomWallpaper: Boolean = false,
    /**
     * A wallpaper that ships with the launcher (see art/Wallpapers.kt). Empty = none chosen: a theme then shows its
     * own wallpaper and the classic layouts show the colour gradient above. A custom photo wins over this.
     */
    val wallpaperId: String = "",
    /** Extra darkening of the wallpaper under a theme, in steps of 10 percent (0 = none, 5 = 50 percent). */
    val wallpaperDim: Int = 0,
    val accent: Int = 0,
    val h24: Boolean = true,
    val showHidden: Boolean = false,
    val setupDone: Boolean = false,
    // ----- status bar -----
    val showStatusBar: Boolean = true,
    /** package of the app the VPN icon opens; empty = system VPN settings */
    val vpnApp: String = "",
    val showVpnButton: Boolean = true,
    /** wrap the status-bar icons in the same glass panel as dock mode */
    val statusBarGlass: Boolean = true,
    /** index into DATE_FORMATS; 0 = no date shown */
    val dateFormat: Int = 0,
    // ----- display options -----
    /** wallpaper dimming: 0 = top & bottom, 1 = top, 2 = bottom, 3 = full, 4 = off */
    val scrimMode: Int = 1, // Top
    val showCategoryNames: Boolean = true,
    val showAppLabels: Boolean = true,
    /** spacing step 0..4 (see GAP_SIZES) */
    val spacing: Int = 1, // Small
    /** icon size step 0..4 (see ICON_SIZES) */
    val iconScale: Int = 1, // Small
    /** icon/panel corner roundness step 0..4 (see CORNER_RADII) */
    val cornerRadius: Int = 3, // Large
    /** UI scale: 0 = Auto (compact high-DPI TVs), 1..5 = fixed (see UI_SCALES) */
    val uiScale: Int = 0, // Auto
    /** language code (e.g. "fr", "de"); empty = system default */
    val language: String = "",
    /** 0 = carousel (fixed selection), 1 = grid, 2 = dock */
    val layout: Int = 2, // Dock
    // ----- themes -----
    /** Installed theme id, or "classic" for the original layouts. */
    val themeId: String = "wishy",
    /** 0 = follow the theme's default, 1 = light, 2 = dark */
    val themeMode: Int = 0,
    /** Show the theme's clock (and the status-bar clock in classic mode). */
    val showClock: Boolean = true,
    /** 0 = full, 1 = reduced, 2 = off */
    val animations: Int = 0,
    /** Manual theme scale in percent (50..150) on top of the automatic screen scaling. */
    val themeScalePct: Int = 100,
    /** No network use at all. Default on: the launcher works fully offline. */
    /** Themes: split apps into the sections (Streaming, Games...) with a tab row. Off = one simple row of everything. */
    val themeSections: Boolean = false,
    val offlineMode: Boolean = false,
    /** Look up banner artwork in the official repository for installed apps. Needs offlineMode = false. */
    val artworkOnline: Boolean = true,
    /** Extra THEME repositories (index.json addresses). Never used for artwork. */
    val extraRepos: List<String> = emptyList(),
)

const val LAYOUT_CAROUSEL = 0
const val LAYOUT_GRID = 1
const val LAYOUT_DOCK = 2

/** Status bar date formats (SimpleDateFormat patterns); index 0 = off. */
val DATE_FORMATS = listOf("", "EEE d", "EEE d MMM", "d MMM yyyy", "dd/MM", "MM/dd", "yyyy-MM-dd")

/** Sorted by user base — the display name is localized in the UI via string resource. */
val LANGUAGES = listOf(
    "",     // system default
    "zh", "es", "ja", "de", "fr",   // tier 1
    "pt", "ru", "ko", "ar", "it",   // tier 2
    "tr", "pl", "nl", "hi", "th", "in", "vi", // tier 3
)

private val Context.dataStore by preferencesDataStore(name = "launcher")
private val KEY_CONFIG = stringPreferencesKey("config")
private val json = Json { ignoreUnknownKeys = true }

class ConfigStore(private val context: Context) {

    val flow: Flow<LauncherConfig> = context.dataStore.data.map { prefs ->
        val cfg = prefs[KEY_CONFIG]?.let {
            runCatching { json.decodeFromString<LauncherConfig>(it) }.getOrNull()
        } ?: LauncherConfig(categories = defaultCategories(context))
        ensureAllAppsFirst(cfg, context)
    }

    private fun defaultCategories(ctx: Context) = listOf(
        CategoryCfg(AppRepository.ALL_APPS_ID, ctx.getString(R.string.cat_all_apps)),
        CategoryCfg("streaming", ctx.getString(R.string.cat_streaming)),
        CategoryCfg("games", ctx.getString(R.string.cat_games)),
        CategoryCfg("music", ctx.getString(R.string.cat_music)),
        CategoryCfg("apps", ctx.getString(R.string.cat_apps)),
    )

    private fun ensureAllAppsFirst(cfg: LauncherConfig, ctx: Context): LauncherConfig {
        val allAppsCat = CategoryCfg(AppRepository.ALL_APPS_ID, ctx.getString(R.string.cat_all_apps))
        val currentCats = cfg.categories
        if (currentCats.firstOrNull()?.id == AppRepository.ALL_APPS_ID) return cfg
        val rest = currentCats.filter { it.id != AppRepository.ALL_APPS_ID }
        return cfg.copy(categories = listOf(allAppsCat) + rest)
    }

    suspend fun update(transform: (LauncherConfig) -> LauncherConfig) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_CONFIG]?.let {
                runCatching { json.decodeFromString<LauncherConfig>(it) }.getOrNull()
            } ?: LauncherConfig()
            prefs[KEY_CONFIG] = json.encodeToString(transform(current))
        }
    }
}
