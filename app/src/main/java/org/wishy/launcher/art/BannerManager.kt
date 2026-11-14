package org.wishy.launcher.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.wishy.launcher.data.AppEntry
import org.wishy.launcher.net.HttpsFetcher
import org.wishy.launcher.net.Repos
import org.wishy.theme.core.FileHash
import org.wishy.theme.core.RepoChecker
import org.wishy.theme.core.RepoJson
import org.wishy.theme.core.RepoLimits
import org.wishy.theme.core.RepoUrls
import org.wishy.theme.core.ThemeLimits
import java.io.File

/**
 * Chooses and stores artwork for installed apps. Lookup order (first hit wins):
 *   1. a picture the user chose for the app        filesDir/art/user/<pkg>.img
 *   2. artwork from the official repository         filesDir/art/repo/<pkg>.img  (+ <pkg>.sha256)
 *   3. the app's own banner, then its icon          (done by AppRepository, unchanged)
 *
 * Nothing here runs on the main thread. A file is only ever replaced after the new one has been fully
 * downloaded, hash-checked and decoded as an image, so a failure leaves the old artwork in place.
 */
class BannerManager private constructor(context: Context) {
    private val app = context.applicationContext
    private val userDir = File(app.filesDir, "art/user")
    private val repoDir = File(app.filesDir, "art/repo")
    private val tmpDir = File(app.cacheDir, "art-staging")
    private val prefs = app.getSharedPreferences("art_status", Context.MODE_PRIVATE)

    class ArtException(message: String) : Exception(message)

    data class Sizes(val userBytes: Long, val repoBytes: Long, val userCount: Int, val repoCount: Int)

    fun userFile(pkg: String): File? = fileIfValid(userDir, pkg)
    fun repoFile(pkg: String): File? = fileIfValid(repoDir, pkg)
    fun hasUserArt(pkg: String) = userFile(pkg) != null

    private fun fileIfValid(dir: File, pkg: String): File? {
        if (!RepoUrls.isPackageName(pkg)) return null
        return File(dir, "$pkg.img").takeIf { it.isFile }
    }

    /** Returns [apps] with the banner replaced where the user or the repository supplied artwork. */
    fun withArtwork(apps: List<AppEntry>, maxWidth: Int = 720): List<AppEntry> =
        apps.map { a ->
            val f = userFile(a.pkg) ?: repoFile(a.pkg)
            val bmp = f?.let { decode(it, maxWidth) }
            if (bmp != null && f != null) a.copy(banner = bmp, stamp = a.stamp xor f.lastModified()) else a
        }

    private fun decode(f: File, maxWidth: Int): ImageBitmap? = runCatching {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, b)
        if (b.outWidth <= 0) return null
        var sample = 1
        while (b.outWidth / (sample * 2) >= maxWidth) sample *= 2
        val o = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        BitmapFactory.decodeFile(f.path, o)?.asImageBitmap()
    }.getOrNull()

    // ----- user choice -----

    /** Copies the picture at [uri] after checking it. [pkg] must be an installed package the caller checked. */
    fun setUserArt(pkg: String, uri: Uri) {
        requirePkg(pkg)
        tmpDir.mkdirs()
        val tmp = File(tmpDir, "$pkg.user.part")
        try {
            app.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024); var total = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        total += n
                        if (total > ThemeLimits.MAX_IMAGE_BYTES) throw ArtException("That picture is larger than 5 MB.")
                        out.write(buf, 0, n)
                    }
                }
            } ?: throw ArtException("Could not open that file.")
            checkImage(tmp, requireBannerShape = false)
            replace(tmp, File(userDir, "$pkg.img"))
        } finally {
            tmp.delete()
        }
    }

    fun clearUserArt(pkg: String) { if (RepoUrls.isPackageName(pkg)) File(userDir, "$pkg.img").delete() }

    // ----- repository artwork -----

    /**
     * Fetches the official artwork index and downloads artwork for [installed] packages that are listed and
     * missing or changed. Only packages that are installed AND present in the index are ever requested; the
     * installed-app list never leaves the device. Returns how many files changed. Failures for one app do not
     * stop the others; an index failure throws.
     */
    fun syncOfficial(installed: Set<String>): Int {
        val text = HttpsFetcher.getText(Repos.OFFICIAL_ART_INDEX, RepoLimits.MAX_INDEX_BYTES)
        val index = RepoJson.parseArt(text)
        if (index.schemaVersion != 1) throw ArtException("Artwork index version not supported.")
        var changed = 0
        for (pkg in installed) {
            val e = index.apps[pkg] ?: continue
            if (!RepoUrls.isPackageName(pkg)) continue
            // Judge the entry on its own, so one bad entry cannot block the rest.
            val single = org.wishy.theme.core.ArtIndex(1, mapOf(pkg to e))
            if (RepoChecker.problems(single, Repos.OFFICIAL_ART_INDEX).isNotEmpty()) continue
            val dest = File(repoDir, "$pkg.img")
            val sidecar = File(repoDir, "$pkg.sha256")
            if (dest.isFile && sidecar.isFile && sidecar.readText().trim() == e.sha256) continue
            runCatching {
                val url = RepoUrls.resolve(Repos.OFFICIAL_ART_INDEX, e.url)
                tmpDir.mkdirs()
                val tmp = File(tmpDir, "$pkg.repo.part")
                try {
                    HttpsFetcher.download(url, tmp, RepoLimits.MAX_ART_BYTES, e.sizeBytes)
                    FileHash.verifySha256(tmp, e.sha256)
                    val (w, h) = checkImage(tmp, requireBannerShape = true)
                    if (w != e.width || h != e.height) throw ArtException("Size does not match the index.")
                    replace(tmp, dest)
                    sidecar.parentFile?.mkdirs()
                    sidecar.writeText(e.sha256)
                    changed++
                } finally { tmp.delete() }
            }
        }
        prefs.edit().putLong("last_sync", System.currentTimeMillis()).apply()
        return changed
    }

    fun lastSync(): Long = prefs.getLong("last_sync", 0L)

    fun syncDue(minIntervalMs: Long = 24L * 60 * 60 * 1000) = System.currentTimeMillis() - lastSync() > minIntervalMs

    // ----- cache management -----

    fun sizes(): Sizes {
        fun scan(d: File): Pair<Long, Int> {
            val fs = d.listFiles { f -> f.name.endsWith(".img") }.orEmpty()
            return fs.sumOf { it.length() } to fs.size
        }
        val (ub, uc) = scan(userDir); val (rb, rc) = scan(repoDir)
        return Sizes(ub, rb, uc, rc)
    }

    /** Removes downloaded artwork only. The user's own choices are not touched. */
    fun clearDownloaded() {
        repoDir.deleteRecursively()
        tmpDir.deleteRecursively()
        prefs.edit().remove("last_sync").apply()
    }

    // ----- helpers -----

    private fun requirePkg(pkg: String) { if (!RepoUrls.isPackageName(pkg)) throw ArtException("Not a valid package name.") }

    /** Accepts PNG, JPEG and WebP only. Reads the header, not the pixels. Returns width to height. */
    private fun checkImage(f: File, requireBannerShape: Boolean): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        val w = o.outWidth; val h = o.outHeight
        if (w <= 0 || h <= 0) throw ArtException("That is not a readable image.")
        if (o.outMimeType !in setOf("image/png", "image/jpeg", "image/webp")) throw ArtException("Use a PNG, JPEG or WebP picture.")
        if (w > ThemeLimits.MAX_IMAGE_DIMENSION || h > ThemeLimits.MAX_IMAGE_DIMENSION) throw ArtException("Picture is too large in pixels.")
        if (requireBannerShape) {
            if (w !in RepoLimits.MIN_ART_W..RepoLimits.MAX_ART_W || h !in RepoLimits.MIN_ART_H..RepoLimits.MAX_ART_H) throw ArtException("Artwork size is out of range.")
            if (!RepoChecker.isBannerAspect(w, h)) throw ArtException("Artwork is not 16:9.")
        }
        return w to h
    }

    private fun replace(from: File, to: File) {
        to.parentFile?.mkdirs()
        if (!from.renameTo(to)) {
            // Different volume or an existing target: copy to a sibling, then rename over the target.
            val sib = File(to.parentFile, to.name + ".new")
            from.copyTo(sib, overwrite = true)
            if (!sib.renameTo(to)) { sib.delete(); throw ArtException("Could not save the artwork.") }
        }
    }

    companion object {
        @Volatile private var instance: BannerManager? = null
        fun get(context: Context): BannerManager =
            instance ?: synchronized(this) { instance ?: BannerManager(context).also { instance = it } }
    }
}
