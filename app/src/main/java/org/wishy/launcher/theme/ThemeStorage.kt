package org.wishy.launcher.theme

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import org.wishy.theme.core.LoadContext
import org.wishy.theme.core.LoadedTheme
import org.wishy.theme.core.ThemeInstaller
import org.wishy.theme.core.ThemeLimits
import org.wishy.theme.core.ThemeLoader
import org.wishy.theme.core.ThemeSummary
import java.io.File

/** One theme in the picker. */
data class ThemeEntry(val summary: ThemeSummary, val bundled: Boolean, val installed: Boolean)

/** [theme] is null only if even the bundled default failed; the caller then shows the classic layout. */
data class ThemeResult(val theme: LoadedTheme?, val requestedId: String, val error: String?) {
    val usedFallback: Boolean get() = theme != null && theme.id != requestedId
}

/**
 * Where themes live on disk, and how one is chosen.
 *
 *   filesDir/themedata/bundled/<id>/   copies of the themes shipped in the APK (re-copied when the app updates)
 *   filesDir/themedata/themes/<id>/    themes installed by the user (managed by ThemeInstaller)
 *
 * Bundled themes are copied out of assets so every theme, bundled or downloaded, goes through the same
 * file-based loader and the same validation.
 */
class ThemeStorage private constructor(private val context: Context) {
    private val root = File(context.filesDir, "themedata")
    private val bundledRoot = File(root, "bundled")
    val installer = ThemeInstaller(root)
    private val prefs = context.getSharedPreferences("theme_status", Context.MODE_PRIVATE)
    private var prepared = false

    /** Last reason a theme was rejected, for Settings. Empty if none. */
    var lastError: String
        get() = prefs.getString(KEY_ERROR, "") ?: ""
        private set(value) { prefs.edit().putString(KEY_ERROR, value).apply() }

    fun clearError() { lastError = "" }

    private val sources = context.getSharedPreferences("theme_sources", Context.MODE_PRIVATE)

    /** Remembers which repository an installed theme came from, so Settings can show it. */
    fun rememberSource(id: String, indexUrl: String) { sources.edit().putString(id, indexUrl).apply() }
    fun sourceOf(id: String): String? = sources.getString(id, null)

    /** Removes an installed (not bundled) theme. The caller switches away from it first if it is active. */
    fun remove(id: String) {
        installer.remove(id)
        sources.edit().remove(id).apply()
    }

    @Synchronized
    fun prepare() {
        if (prepared) return
        runCatching { installer.recoverLeftovers() }
        runCatching { syncBundled() }
        prepared = true
    }

    /** The folder for [id]: an installed copy wins over the bundled one. Null if neither exists. */
    fun dirFor(id: String): File? {
        if (!ThemeLimits.ID_REGEX.matches(id)) return null
        val installed = runCatching { installer.installedDir(id) }.getOrNull()
        if (installed != null) return installed
        return File(bundledRoot, id).takeIf { File(it, "theme.json").isFile }
    }

    fun isInstalled(id: String): Boolean = runCatching { installer.installedDir(id) }.getOrNull() != null

    fun list(): List<ThemeEntry> {
        prepare()
        val byId = LinkedHashMap<String, ThemeEntry>()
        bundledRoot.listFiles()?.sortedBy { it.name }?.forEach { dir ->
            ThemeLoader.readSummary(dir)?.takeIf { it.id == dir.name }?.let {
                byId[it.id] = ThemeEntry(it, bundled = true, installed = false)
            }
        }
        installer.themesDir.listFiles()?.sortedBy { it.name }?.forEach { dir ->
            if (!dir.isDirectory || dir.name.endsWith(".old") || dir.name.endsWith(".new")) return@forEach
            ThemeLoader.readSummary(dir)?.takeIf { it.id == dir.name }?.let {
                byId[it.id] = ThemeEntry(it, bundled = byId[it.id]?.bundled == true, installed = true)
            }
        }
        return byId.values.toList()
    }

    /**
     * Loads theme [id]. Any failure falls back to the bundled default theme and records the reason.
     * Never throws. Call off the main thread.
     */
    fun resolve(id: String, ctx: LoadContext): ThemeResult {
        prepare()
        var error: String? = null
        if (id.isNotEmpty() && id != CLASSIC_ID) {
            val dir = dirFor(id)
            if (dir == null) {
                error = "Theme '$id' is not installed."
            } else {
                try {
                    return ThemeResult(ThemeLoader.load(dir, ctx), id, null)
                } catch (e: Exception) {
                    error = "Theme '$id' was not used: ${e.message ?: "unknown error"}"
                }
            }
        }
        if (id == CLASSIC_ID) return ThemeResult(null, id, null)
        try {
            val fallback = ThemeLoader.load(File(bundledRoot, DEFAULT_ID), ctx)
            if (error != null) lastError = error
            return ThemeResult(fallback, id, error)
        } catch (e: Exception) {
            val msg = (error?.let { "$it " } ?: "") + "The bundled theme also failed: ${e.message ?: "unknown error"}"
            lastError = msg
            return ThemeResult(null, id, msg)
        }
    }

    private fun syncBundled() {
        val am = context.assets
        val version = runCatching {
            PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
        }.getOrDefault(0L).toString()
        val names = am.list("") ?: return
        for (name in names) {
            if (!ThemeLimits.ID_REGEX.matches(name)) continue
            if ((am.list(name) ?: emptyArray()).none { it == "theme.json" }) continue
            val target = File(bundledRoot, name)
            val marker = File(target, MARKER)
            if (File(target, "theme.json").isFile && marker.isFile && marker.readText().trim() == version) continue
            target.deleteRecursively()
            copyAsset(name, target)
            File(target, MARKER).writeText(version)
        }
    }

    private fun copyAsset(path: String, dest: File) {
        val am = context.assets
        dest.mkdirs()
        for (child in am.list(path).orEmpty()) {
            val childPath = "$path/$child"
            if (am.list(childPath).orEmpty().isNotEmpty()) {
                copyAsset(childPath, File(dest, child))
            } else {
                am.open(childPath).use { input -> File(dest, child).outputStream().use { input.copyTo(it) } }
            }
        }
    }

    companion object {
        const val DEFAULT_ID = "wishy"
        /** Special id: use the original Couchy-style layouts instead of a theme. */
        const val CLASSIC_ID = "classic"
        private const val MARKER = ".app-version"
        private const val KEY_ERROR = "last_error"

        @Volatile private var instance: ThemeStorage? = null

        fun get(context: Context): ThemeStorage =
            instance ?: synchronized(this) {
                instance ?: ThemeStorage(context.applicationContext).also { instance = it }
            }
    }
}
