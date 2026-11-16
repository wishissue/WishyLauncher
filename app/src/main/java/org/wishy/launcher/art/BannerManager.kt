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
import org.wishy.theme.core.ArtVariant
import org.wishy.theme.core.ArtVariantIndex
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
 *   2. the banner version the user picked           filesDir/art/variants/<pkg>/<key>.img  (+ <key>.sha256)
 *      or, when nothing is picked, artwork from the official repository
 *                                                   filesDir/art/repo/<pkg>.img  (+ <pkg>.sha256)
 *   3. the app's own banner, then its icon          (done by AppRepository, unchanged)
 * Picking "the app's own" skips step 2 for that app. Versions come from `variants.json` files: the official one and any
 * the user added. They are only downloaded when the person opens that app's picker, and checked like every other banner.
 *
 * Nothing here runs on the main thread. A file is only ever replaced after the new one has been fully
 * downloaded, hash-checked and decoded as an image, so a failure leaves the old artwork in place.
 */
class BannerManager private constructor(context: Context) {
    private val app = context.applicationContext
    private val userDir = File(app.filesDir, "art/user")
    private val repoDir = File(app.filesDir, "art/repo")
    private val variantDir = File(app.filesDir, "art/variants")
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
            val f = userFile(a.pkg) ?: chosenRepoFile(a.pkg)
            val bmp = f?.let { decode(it, maxWidth) }
            if (bmp != null && f != null) a.copy(banner = bmp, stamp = a.stamp xor f.lastModified()) else a
        }

    /** The downloaded banner that applies to [pkg] given the person's pick. Null means "use the app's own". */
    private fun chosenRepoFile(pkg: String): File? = when (val p = pick(pkg)) {
        PICK_DEFAULT -> repoFile(pkg)
        PICK_APP -> null
        else -> versionFile(pkg, p) ?: repoFile(pkg)
    }

    private fun decode(f: File, maxWidth: Int): ImageBitmap? = runCatching {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, b)
        if (b.outWidth <= 0) return null
        var sample = 1
        while (b.outWidth / (sample * 2) >= maxWidth) sample *= 2
        val o = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = BitmapFactory.decodeFile(f.path, o) ?: return null
        // A 1280 px banner is far more than a TV card needs: keep memory low by scaling to the size that is drawn.
        val out = if (bmp.width > maxWidth * 5 / 4) {
            Bitmap.createScaledBitmap(bmp, maxWidth, (bmp.height.toLong() * maxWidth / bmp.width).toInt().coerceAtLeast(1), true)
                .also { if (it !== bmp) bmp.recycle() }
        } else bmp
        out.asImageBitmap()
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


    /** Packages that have a banner of the user's own, for export and listing. */
    fun userPackages(): List<String> =
        userDir.listFiles { f -> f.name.endsWith(".img") }.orEmpty().map { it.name.removeSuffix(".img") }.filter { RepoUrls.isPackageName(it) }.sorted()

    /** Checks the picture in [file] (PNG, JPEG or WebP, readable, not huge) and returns its width and height. */
    fun validateImage(file: File): Pair<Int, Int> = checkImage(file, requireBannerShape = false)

    /**
     * Makes the picture in [file] the user's banner for [pkg]. The file is moved, not copied. The old banner stays
     * in place if the picture is not acceptable. Also ends "use the app's original banner" for this app.
     */
    fun installUserFile(pkg: String, file: File) {
        requirePkg(pkg)
        checkImage(file, requireBannerShape = false)
        if (file.length() > ThemeLimits.MAX_IMAGE_BYTES) throw ArtException("That picture is larger than 5 MB.")
        replace(file, File(userDir, "$pkg.img"))
        clearOwnPick(pkg)
    }

    /** True when the person chose "the app's own banner" for [pkg] (the same pick the versions screen offers). */
    fun prefersOriginal(pkg: String): Boolean = RepoUrls.isPackageName(pkg) && pick(pkg) == PICK_APP

    /** Ends "the app's own banner" for [pkg] (called when the person puts a banner of their own in place). */
    fun clearOwnPick(pkg: String) {
        if (RepoUrls.isPackageName(pkg) && pick(pkg) == PICK_APP) prefs.edit().remove("pick_$pkg").apply()
    }

    fun userStamp(pkg: String): Long = userFile(pkg)?.lastModified() ?: 0L

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

    // ----- banner versions -----

    /** One selectable version of an app's banner. [key] is unique across sources and safe to use as a file name. */
    class Version(val key: String, val sourceUrl: String, val info: ArtVariant)

    /** What the person picked for [pkg]: [PICK_DEFAULT], [PICK_APP], or a version key. A picture of their own wins over all of these. */
    fun pick(pkg: String): String = prefs.getString("pick_$pkg", PICK_DEFAULT) ?: PICK_DEFAULT

    /** Automatic again: the official banner when there is one, otherwise the app's own. Also drops the person's own picture. */
    fun pickDefault(pkg: String) { requirePkg(pkg); prefs.edit().remove("pick_$pkg").apply(); clearUserArt(pkg) }

    /** Use the banner the app ships, even if the repository has one. Also drops the person's own picture. */
    fun pickAppOwn(pkg: String) { requirePkg(pkg); prefs.edit().putString("pick_$pkg", PICK_APP).apply(); clearUserArt(pkg) }

    /** Use a downloaded version. Throws if it has not been downloaded (and checked) yet. */
    fun pickVersion(pkg: String, key: String) {
        requirePkg(pkg)
        if (versionFile(pkg, key) == null) throw ArtException("That banner is not downloaded yet.")
        prefs.edit().putString("pick_$pkg", key).apply(); clearUserArt(pkg)
    }

    fun versionFile(pkg: String, key: String): File? {
        if (!RepoUrls.isPackageName(pkg) || !VERSION_KEY.matches(key)) return null
        return File(File(variantDir, pkg), "$key.img").takeIf { it.isFile }
    }

    /** A small decoded picture of a downloaded version, for the picker. Call off the main thread. */
    fun versionPreview(pkg: String, key: String, maxWidth: Int = 480): ImageBitmap? = versionFile(pkg, key)?.let { decode(it, maxWidth) }
    fun userPreview(pkg: String, maxWidth: Int = 480): ImageBitmap? = userFile(pkg)?.let { decode(it, maxWidth) }
    fun defaultPreview(pkg: String, maxWidth: Int = 480): ImageBitmap? = repoFile(pkg)?.let { decode(it, maxWidth) }

    /** The banner the app itself ships, if any (not the icon). Call off the main thread. */
    fun appOwnPreview(pkg: String, maxWidth: Int = 480): ImageBitmap? = runCatching {
        if (!RepoUrls.isPackageName(pkg)) return null
        val pm = app.packageManager
        val d = pm.getApplicationBanner(pkg)
            ?: pm.getLeanbackLaunchIntentForPackage(pkg)?.let { pm.getActivityBanner(it) }
            ?: return null
        val w = d.intrinsicWidth.takeIf { it > 0 } ?: 320
        val h = d.intrinsicHeight.takeIf { it > 0 } ?: 180
        val k = (maxWidth.toFloat() / w).coerceAtMost(1f)
        val bmp = Bitmap.createBitmap((w * k).toInt().coerceAtLeast(1), (h * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, bmp.width, bmp.height)
        d.draw(android.graphics.Canvas(bmp))
        bmp.asImageBitmap()
    }.getOrNull()

    /**
     * Lists the versions offered for [pkg] by the official `variants.json` and by [extraSources] (https addresses the
     * person added). Only the one package's entries are looked at, and each entry is judged on its own so a bad one can't
     * hide the good ones. Throws only if no source answered and nothing was found. Call off the main thread.
     */
    fun fetchVersions(pkg: String, extraSources: List<String>): List<Version> {
        requirePkg(pkg)
        val out = ArrayList<Version>()
        var firstError: Throwable? = null
        for (src in listOf(Repos.OFFICIAL_VARIANTS_INDEX) + extraSources) {
            runCatching {
                val idx = RepoJson.parseVariants(HttpsFetcher.getText(src, RepoLimits.MAX_INDEX_BYTES))
                if (idx.schemaVersion != 1) return@runCatching
                val list = idx.apps[pkg] ?: return@runCatching
                val tag = sourceTag(src)
                for (v in list.take(RepoLimits.MAX_ART_VARIANTS)) {
                    if (RepoChecker.problems(ArtVariantIndex(1, mapOf(pkg to listOf(v))), src).isNotEmpty()) continue
                    out += Version("${tag}_${v.id}", src, v)
                }
            }.onFailure { if (firstError == null) firstError = it }
        }
        val err = firstError
        if (out.isEmpty() && err != null) throw err
        return out
    }

    /** Downloads and checks one version. Does nothing if the same picture is already stored. Throws on any failure. */
    fun downloadVersion(pkg: String, v: Version) {
        requirePkg(pkg)
        require(VERSION_KEY.matches(v.key)) { "bad key" }
        val dir = File(variantDir, pkg)
        val dest = File(dir, "${v.key}.img")
        val sidecar = File(dir, "${v.key}.sha256")
        if (dest.isFile && sidecar.isFile && sidecar.readText().trim() == v.info.sha256) return
        tmpDir.mkdirs()
        val tmp = File(tmpDir, "$pkg.${v.key}.part")
        try {
            HttpsFetcher.download(RepoUrls.resolve(v.sourceUrl, v.info.url), tmp, RepoLimits.MAX_ART_BYTES, v.info.sizeBytes)
            FileHash.verifySha256(tmp, v.info.sha256)
            val (w, h) = checkImage(tmp, requireBannerShape = true)
            if (w != v.info.width || h != v.info.height) throw ArtException("Size does not match the index.")
            replace(tmp, dest)
            sidecar.writeText(v.info.sha256)
        } finally {
            tmp.delete()
        }
    }

    /** First 8 hex digits of the source address, so two sources can both offer a version called "dark". */
    private fun sourceTag(src: String): String =
        java.security.MessageDigest.getInstance("SHA-1").digest(src.toByteArray()).joinToString("") { "%02x".format(it) }.take(8)

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
        variantDir.deleteRecursively()
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
        const val PICK_DEFAULT = ""
        const val PICK_APP = "app"
        private val VERSION_KEY = Regex("^[0-9a-f]{8}_[a-z0-9_]{1,32}$")
        @Volatile private var instance: BannerManager? = null
        fun get(context: Context): BannerManager =
            instance ?: synchronized(this) { instance ?: BannerManager(context).also { instance = it } }
    }
}
