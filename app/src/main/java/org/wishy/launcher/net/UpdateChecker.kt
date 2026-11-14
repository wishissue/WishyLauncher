package org.wishy.launcher.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the latest GitHub release says. */
data class UpdateInfo(val version: String, val name: String, val notes: String, val pageUrl: String)

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
)

/**
 * Asks GitHub for the latest release and compares it with the installed version.
 * Nothing is downloaded or installed: the launcher only tells the person a newer version exists and where
 * to get it. No identifying data is sent. Call from a background thread.
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
            isNewer(latest, installedVersion) -> UpdateResult.Available(
                UpdateInfo(
                    version = latest,
                    name = r.name?.takeIf { it.isNotBlank() } ?: "Wishy Launcher $latest",
                    notes = r.body.orEmpty().take(600),
                    pageUrl = r.html_url?.takeIf { it.startsWith("https://github.com/") } ?: Repos.RELEASES_PAGE,
                ),
            )
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
