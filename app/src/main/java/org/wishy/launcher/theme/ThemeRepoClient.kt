package org.wishy.launcher.theme

import android.content.Context
import org.wishy.launcher.net.HttpsFetcher
import org.wishy.theme.core.ColorSchemeName
import org.wishy.theme.core.FileHash
import org.wishy.theme.core.LoadContext
import org.wishy.theme.core.RepoChecker
import org.wishy.theme.core.RepoIndex
import org.wishy.theme.core.RepoJson
import org.wishy.theme.core.RepoLimits
import org.wishy.theme.core.RepoTheme
import org.wishy.theme.core.RepoUrls
import org.wishy.theme.core.ThemeLimits
import org.wishy.theme.core.ThemeLoader
import org.wishy.theme.core.ThemeRejectedException
import java.io.File

/** A repository's index together with the address it came from. */
data class RepoListing(val indexUrl: String, val index: RepoIndex)

/**
 * Lists and installs themes from a repository. Every download goes through the same steps whether the repository
 * is the official one or one the user added: https and same-host checks, size cap, SHA-256 against the index, safe
 * unzip, full theme validation on the unpacked folder, atomic swap. Call from a background thread.
 */
class ThemeRepoClient(context: Context, private val storage: ThemeStorage) {
    private val staging = File(context.applicationContext.cacheDir, "theme-download")

    /** Fetches and checks an index. Throws on a network error or a rejected index. */
    fun list(indexUrl: String): RepoListing {
        val text = HttpsFetcher.getText(indexUrl, RepoLimits.MAX_INDEX_BYTES)
        val index = RepoJson.parseIndex(text)
        val problems = RepoChecker.problems(index, indexUrl)
        if (problems.isNotEmpty()) throw ThemeRejectedException("The repository index has problems: ${problems.first()}")
        return RepoListing(indexUrl, index)
    }

    /** Downloads and installs [entry]. Returns the installed theme id. An installed copy is kept if anything fails. */
    fun install(listing: RepoListing, entry: RepoTheme, minLauncherVersion: Int): String {
        if (entry.minLauncherVersion > minLauncherVersion) {
            throw ThemeRejectedException("This theme needs a newer launcher (version code ${entry.minLauncherVersion}).")
        }
        val url = RepoUrls.resolve(listing.indexUrl, entry.downloadUrl)
        staging.mkdirs()
        val zip = File(staging, "${entry.id}.zip.part")
        try {
            HttpsFetcher.download(url, zip, ThemeLimits.MAX_ZIP_BYTES, entry.sizeBytes)
            FileHash.verifySha256(zip, entry.sha256)
            storage.installer.install(zip, entry.id, entry.version) { dir -> validate(dir) }
        } finally {
            zip.delete()
        }
        storage.rememberSource(entry.id, listing.indexUrl)
        return entry.id
    }

    /** Both colour schemes at a small and a large screen, so a theme that only breaks on some screens is caught. */
    private fun validate(dir: File) {
        for (scheme in ColorSchemeName.values()) for (h in intArrayOf(720, 2160)) {
            ThemeLoader.load(dir, LoadContext(16f / 9f, h, scheme, false))
        }
    }
}
