package org.wishy.launcher.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the latest GitHub release says. */
data class UpdateInfo(
    val version: String,
    val name: String,
    val notes: String,
    val pageUrl: String,
    /** Direct link to the release's APK, or empty when the release has none (then only the page can be opened). */
    val apkUrl: String = "",
    val apkBytes: Long = 0L,
)

/** The outcome of one check. */
sealed class UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult()
    data object UpToDate : UpdateResult()
    data class Failed(val reason: String) : UpdateResult()
}

@Serializable
private data class ReleaseJson(
    val tag_name: String = "",
    val name: String? = null,
    val body: String? = null,
    val html_url: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<AssetJson> = emptyList(),
)

@Serializable
private data class AssetJson(
    val name: String = "",
    val size: Long = 0L,
    val browser_download_url: String = "",
)

/**
 * Asks GitHub for the latest release and compares it with the installed version. Only the release list is read here;
 * downloading and installing the APK is [UpdateInstaller]'s job and only happens when the person asks for it.
 * No identifying data is sent. Call from a background thread.
 */
object UpdateChecker {
    private const val MAX_BYTES = 256 * 1024
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val PREFS = "update_check"

    fun check(installedVersion: String): UpdateResult = try {
        val text = HttpsFetcher.getText(Repos.LATEST_RELEASE_API, MAX_BYTES)
        val r = json.decodeFromString(ReleaseJson.serializer(), text)
        val latest = r.tag_name.trim().removePrefix("v").removePrefix("V")
        when {
            r.draft || r.prerelease || latest.isEmpty() -> UpdateResult.UpToDate
            isNewer(latest, installedVersion) -> {
                // Only a file hosted by this project's own releases is ever offered for installing.
                val apk = r.assets.firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true) &&
                        it.browser_download_url.startsWith(Repos.RELEASE_DOWNLOAD_PREFIX)
                }
                UpdateResult.Available(
                    UpdateInfo(
                        version = latest,
                        name = r.name?.takeIf { it.isNotBlank() } ?: "Wishy Launcher $latest",
                        notes = r.body.orEmpty().take(600),
                        pageUrl = r.html_url?.takeIf { it.startsWith("https://github.com/") } ?: Repos.RELEASES_PAGE,
                        apkUrl = apk?.browser_download_url.orEmpty(),
                        apkBytes = apk?.size ?: 0L,
                    ),
                )
            }
            else -> UpdateResult.UpToDate
        }
    } catch (e: Exception) {
        // A repository with no release yet answers 404; that is "nothing new", not an error worth showing.
        if (e.message?.contains("404") == true) UpdateResult.UpToDate
        else UpdateResult.Failed(e.message ?: "No connection")
    }

    /** True when [candidate] is a newer MAJOR.MINOR.PATCH than [installed]; ignores suffixes like "-beta". */
    fun isNewer(candidate: String, installed: String): Boolean {
        fun parts(v: String): List<Int>? {
            val out = ArrayList<Int>()
            for (s in v.substringBefore('-').split('.')) out += (s.toIntOrNull() ?: return null)
            return out
        }
        val a = parts(candidate) ?: return false
        val b = parts(installed) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** The version the person already dismissed, so the launch check does not nag about the same release. */
    fun dismissedVersion(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("dismissed", "") ?: ""

    fun dismiss(context: Context, version: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("dismissed", version).apply()
    }

    /** Opens [url] in a browser. Returns false when the device has none (common on Android TV). */
    fun openPage(context: Context, url: String): Boolean = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
