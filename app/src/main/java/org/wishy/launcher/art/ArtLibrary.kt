package org.wishy.launcher.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.wishy.theme.core.ArtOrigin
import org.wishy.theme.core.ArtPack
import org.wishy.theme.core.BannerSpec
import org.wishy.theme.core.PackEntry
import org.wishy.theme.core.PackFile
import org.wishy.theme.core.RepoUrls
import org.wishy.theme.core.SvgSanitizer
import org.wishy.theme.core.ThemeLimits
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** What is remembered about a banner the person made, so it can be edited again and credited in packs. */
@Serializable
data class ArtMeta(
    val spec: BannerSpec = BannerSpec(),
    val logoFile: String? = null,
    val origin: ArtOrigin = ArtOrigin.USER,
    val source: String = "",
    val author: String = "",
    val license: String = "",
    val sourceUrl: String? = null,
    val title: String = "",
    val createdAt: Long = 0L,
)

/** An original logo kept on disk: [ext] is "svg" or "png". */
class StoredLogo(val bytes: ByteArray, val ext: String)

class PackImportResult(val added: Int, val kept: Int, val skipped: Int)

/**
 * The person's own artwork: the finished banner (through [BannerManager], `art/user/<pkg>.img`) plus the originals
 * it was made from (`art/originals/<pkg>/`: logo, background picture, meta.json). Everything is keyed by package
 * name only, so an app update never touches it, and nothing here is removed by "clear downloaded artwork".
 * Never call on the main thread.
 */
class ArtLibrary private constructor(context: Context) {
    private val app = context.applicationContext
    private val manager = BannerManager.get(app)
    private val origDir = File(app.filesDir, "art/originals")
    private val tmpDir = File(app.cacheDir, "art-staging")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun meta(pkg: String): ArtMeta? {
        if (!RepoUrls.isPackageName(pkg)) return null
        val f = File(origDir, "$pkg/meta.json")
        if (!f.isFile || f.length() > 256 * 1024) return null
        return runCatching { json.decodeFromString(ArtMeta.serializer(), f.readText()) }.getOrNull()
    }

    fun logo(pkg: String): StoredLogo? {
        if (!RepoUrls.isPackageName(pkg)) return null
        for (ext in listOf("svg", "png")) {
            val f = File(origDir, "$pkg/logo.$ext")
            if (f.isFile && f.length() <= SvgSanitizer.MAX_BYTES.toLong().coerceAtLeast(ArtPack.MAX_LOGO_PNG_BYTES)) return StoredLogo(f.readBytes(), ext)
        }
        return null
    }

    fun backgroundBytes(pkg: String): ByteArray? {
        if (!RepoUrls.isPackageName(pkg)) return null
        val f = File(origDir, "$pkg/background.img")
        return if (f.isFile && f.length() <= ThemeLimits.MAX_IMAGE_BYTES) f.readBytes() else null
    }

    /**
     * Saves [banner] as the banner for [pkg] and keeps the originals next to it. If saving the banner fails the
     * old banner and originals stay as they were.
     */
    fun save(pkg: String, banner: Bitmap, meta: ArtMeta, logoBytes: ByteArray?, logoExt: String?, bgBytes: ByteArray?) {
        if (!RepoUrls.isPackageName(pkg)) throw BannerManager.ArtException("Not a valid package name.")
        tmpDir.mkdirs()
        val tmp = File(tmpDir, "$pkg.save.part")
        try {
            tmp.outputStream().use { encode(banner, it) }
            if (tmp.length() > ThemeLimits.MAX_IMAGE_BYTES) {
                tmp.outputStream().use { lossy(banner, it) }
            }
            manager.installUserFile(pkg, tmp)
            storeOriginals(pkg, meta, logoBytes, logoExt, bgBytes)
        } finally {
            tmp.delete()
        }
    }

    /** Writes `meta.json`, the logo and the background picture for [pkg], replacing older originals in one step. */
    private fun storeOriginals(pkg: String, meta: ArtMeta, logoBytes: ByteArray?, logoExt: String?, bgBytes: ByteArray?) {
        runCatching {
            tmpDir.mkdirs()
            val stage = File(tmpDir, "$pkg.orig.new")
            stage.deleteRecursively(); stage.mkdirs()
            val logoName = if (logoBytes != null && (logoExt == "svg" || logoExt == "png")) "logo.$logoExt" else null
            if (logoName != null) File(stage, logoName).writeBytes(logoBytes!!)
            if (bgBytes != null) File(stage, "background.img").writeBytes(bgBytes)
            val m = meta.copy(logoFile = logoName, createdAt = if (meta.createdAt == 0L) System.currentTimeMillis() else meta.createdAt)
            File(stage, "meta.json").writeText(json.encodeToString(ArtMeta.serializer(), m))
            origDir.mkdirs()
            val dest = File(origDir, pkg)
            dest.deleteRecursively()
            if (!stage.renameTo(dest)) { stage.copyRecursively(dest, true); stage.deleteRecursively() }
        }
    }

    /** Official banner from the Wishy repository, already downloaded and checked, becomes the person's choice for [pkg]. */
    fun saveOfficial(pkg: String, file: File, e: org.wishy.theme.core.ArtEntry) {
        tmpDir.mkdirs()
        val part = File(tmpDir, "$pkg.official.part")
        try {
            file.copyTo(part, overwrite = true)
            manager.installUserFile(pkg, part)
        } finally { part.delete() }
        File(origDir, pkg).deleteRecursively()
        storeOriginals(
            pkg,
            ArtMeta(origin = ArtOrigin.OFFICIAL, source = "wishy-repository", author = e.author, license = e.license, sourceUrl = e.source, title = e.title ?: ""),
            null, null, null,
        )
    }

    /** Person picked a ready-made picture: kept as it is, with a small note so it can be exported. */
    fun setPicture(pkg: String, uri: Uri) {
        manager.setUserArt(pkg, uri)
        File(origDir, pkg).deleteRecursively()
        storeOriginals(pkg, ArtMeta(origin = ArtOrigin.USER, source = "file", title = "Picture from a file"), null, null, null)
    }

    /** Back to the app's own banner (or icon): removes the person's banner and its originals, and skips downloaded art. */
    fun restoreOriginal(pkg: String) {
        if (!RepoUrls.isPackageName(pkg)) return
        File(origDir, pkg).deleteRecursively()
        manager.pickAppOwn(pkg) // also removes the person's banner and skips downloaded versions
    }

    // ----- packs -----

    /** Writes every banner of the person's own, with logos and notes, as an artwork pack. Returns how many. */
    fun exportPack(out: OutputStream): Int {
        val files = ArrayList<PackFile>()
        for (pkg in manager.userPackages()) {
            if (files.size >= ArtPack.MAX_ENTRIES) break
            val bf = manager.userFile(pkg) ?: continue
            val bytes = runCatching { bf.readBytes() }.getOrNull() ?: continue
            val ext = sniff(bytes) ?: continue
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            if (o.outWidth <= 0 || o.outHeight <= 0) continue
            val m = meta(pkg)
            val lg = logo(pkg)
            val entry = PackEntry(
                `package` = pkg,
                banner = ArtPack.bannerPath(pkg, ext),
                width = o.outWidth, height = o.outHeight,
                logo = lg?.let { ArtPack.logoPath(pkg, it.ext) },
                spec = m?.spec,
                origin = m?.origin ?: ArtOrigin.USER,
                source = m?.source ?: "file",
                author = m?.author ?: "",
                license = m?.license ?: "",
                sourceUrl = m?.sourceUrl,
                title = m?.title ?: "",
            )
            files += PackFile(entry, bytes, lg?.bytes)
        }
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        ArtPack.write(out, files, fmt.format(java.util.Date()))
        return files.size
    }

    /**
     * Reads a pack. Banners the person already has are kept, never replaced. A bad banner is skipped on its own;
     * a bad logo only loses the logo. Throws for a pack that is not valid at all.
     */
    fun importPack(uri: Uri): PackImportResult {
        tmpDir.mkdirs()
        val zip = File(tmpDir, "import.zip")
        val dir = File(tmpDir, "import-x")
        var added = 0; var kept = 0; var skipped = 0
        try {
            app.contentResolver.openInputStream(uri)?.use { input ->
                zip.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024); var total = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        total += n
                        if (total > ArtPack.MAX_ZIP_BYTES) throw BannerManager.ArtException("That pack is too large.")
                        out.write(buf, 0, n)
                    }
                }
            } ?: throw BannerManager.ArtException("Could not open that file.")
            dir.deleteRecursively(); dir.mkdirs()
            val manifest = ArtPack.extract(zip, dir)
            for (e in manifest.entries) {
                if (manager.hasUserArt(e.`package`)) { kept++; continue }
                val ok = runCatching {
                    var logoBytes: ByteArray? = null
                    var logoExt: String? = null
                    if (e.logo != null) {
                        val lf = File(dir, e.logo!!)
                        val ext = lf.extension.lowercase()
                        val b = lf.readBytes()
                        val valid = runCatching {
                            if (ext == "svg") SvgSanitizer.inspect(b) else manager.validateImage(lf)
                            true
                        }.getOrDefault(false)
                        if (valid) { logoBytes = b; logoExt = ext }
                    }
                    val part = File(tmpDir, "${e.`package`}.import.part")
                    try {
                        File(dir, e.banner).copyTo(part, overwrite = true)
                        manager.installUserFile(e.`package`, part)
                    } finally { part.delete() }
                    val meta = ArtMeta(
                        spec = e.spec ?: BannerSpec(), origin = e.origin, source = e.source, author = e.author,
                        license = e.license, sourceUrl = e.sourceUrl, title = e.title,
                    )
                    storeOriginals(e.`package`, meta, logoBytes, logoExt, null)
                }.isSuccess
                if (ok) added++ else skipped++
            }
        } finally {
            zip.delete(); dir.deleteRecursively()
        }
        return PackImportResult(added, kept, skipped)
    }

    // ----- helpers -----

    private fun encode(b: Bitmap, out: OutputStream) {
        if (Build.VERSION.SDK_INT >= 30) b.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, out)
        else @Suppress("DEPRECATION") b.compress(Bitmap.CompressFormat.WEBP, 95, out)
    }

    private fun lossy(b: Bitmap, out: OutputStream) {
        if (Build.VERSION.SDK_INT >= 30) b.compress(Bitmap.CompressFormat.WEBP_LOSSY, 88, out)
        else @Suppress("DEPRECATION") b.compress(Bitmap.CompressFormat.WEBP, 88, out)
    }

    private fun sniff(b: ByteArray): String? = when {
        b.size > 12 && b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() && b[8] == 'W'.code.toByte() && b[9] == 'E'.code.toByte() -> "webp"
        b.size > 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() -> "png"
        b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "jpg"
        else -> null
    }

    companion object {
        @Volatile private var instance: ArtLibrary? = null
        fun get(context: Context): ArtLibrary =
            instance ?: synchronized(this) { instance ?: ArtLibrary(context).also { instance = it } }
    }
}

/** Pictures the Android system already has for an app. Local only, nothing is downloaded. */
object AppArt {
    fun icon(context: Context, pkg: String): Bitmap? =
        runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(256, 256, Bitmap.Config.ARGB_8888) }.getOrNull()

    fun banner(context: Context, pkg: String): Bitmap? =
        runCatching { context.packageManager.getApplicationBanner(pkg)?.toBitmap(640, 360, Bitmap.Config.ARGB_8888) }.getOrNull()

    fun hasOwnBanner(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getApplicationBanner(pkg) != null }.getOrDefault(false)
}
