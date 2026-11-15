package org.wishy.launcher.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import org.wishy.launcher.net.HttpsFetcher
import org.wishy.launcher.net.Repos
import java.io.File
import java.security.MessageDigest

/**
 * One picture from a community GitHub repository that is nothing more than folders of images
 * (`<Palette>/<Category>/<file>`), such as SleepyCatHey/CozyPixels. There is no index, so [palette], [category] and [name]
 * are read from the path, and [gitSha] is the file's git blob id from the repository tree, which is what we verify against.
 */
class CommunityWallpaper(
    val id: String,
    val path: String,
    val palette: String,
    val category: String,
    val name: String,
    val bytes: Long,
    val gitSha: String,
) {
    val rawUrl: String get() = Repos.COZY_RAW_BASE + path.split('/').joinToString("/") { Uri.encode(it) }
}

/**
 * Lists, filters and downloads wallpapers from the community repository.
 *
 * Safety rules, matching [WallpaperStore]:
 *  - the list comes from one https call to api.github.com, is capped in size, and is cached on disk so we stay well inside
 *    GitHub's anonymous rate limit and keep working (with the old list) when GitHub is unreachable;
 *  - a path from the list is only used if every segment is plain (no `..`, no backslashes, no control characters);
 *  - every picture is checked against its git blob SHA-1 before it is used, then decoded and re-encoded by us as a
 *    screen-sized WebP, so what ends up on disk is never the downloaded bytes;
 *  - sizes are capped while reading, and large pictures are decoded at reduced size so a 1-2 GB TV never runs out of memory.
 * Installed pictures are stored exactly like store downloads (same files, same ids rules) so the rest of the app, and
 * [WallpaperStore.remove], need no special case.
 */
object CommunityWallpapers {
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp")
    private val NAME_PREFIX = Regex("^(ign|od|ctp|cat)_", RegexOption.IGNORE_CASE)
    private const val MAX_TREE_BYTES = 4 * 1024 * 1024
    private const val MAX_ENTRIES = 5000
    private const val MAX_SOURCE_BYTES = 30L * 1024 * 1024
    private const val MAX_EDGE = 3840
    private const val MIN_EDGE = 1280
    private const val THUMB_WIDTH = 480
    private const val INDEX_FRESH_MS = 6L * 60 * 60 * 1000

    /** Two downloads at once is plenty on TV hardware and keeps a fast scroll from starting dozens of them. */
    private val thumbGate = Semaphore(2)

    private fun thumbDir(context: Context) = File(context.cacheDir, "community_thumbs").apply { mkdirs() }
    private fun indexCache(context: Context) = File(context.cacheDir, "community_tree.json")

    // ---- list -------------------------------------------------------------------------------------------------------

    /**
     * Fetches the file list. A cached copy younger than six hours is used as it is; if the network call fails an older
     * copy is used rather than showing nothing. Throws only if there is neither. Call off the main thread.
     */
    fun fetchIndex(context: Context, forceRefresh: Boolean = false): List<CommunityWallpaper> {
        val cache = indexCache(context)
        val fresh = cache.isFile && System.currentTimeMillis() - cache.lastModified() < INDEX_FRESH_MS
        if (fresh && !forceRefresh) {
            runCatching { parseTree(cache.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return try {
            val json = HttpsFetcher.getText(Repos.COZY_TREE_API, MAX_TREE_BYTES)
            val parsed = parseTree(json)
            if (parsed.isNotEmpty()) cache.writeText(json) // only keep a copy that actually parses
            parsed
        } catch (t: Throwable) {
            if (cache.isFile) runCatching { parseTree(cache.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
            throw t
        }
    }

    /** Parses the GitHub "git tree" answer. Public for tests. Entries that don't look right are skipped, not fatal. */
    fun parseTree(json: String): List<CommunityWallpaper> {
        val tree = JSONObject(json).getJSONArray("tree")
        val out = ArrayList<CommunityWallpaper>()
        for (i in 0 until tree.length()) {
            if (out.size >= MAX_ENTRIES) break
            val o = tree.optJSONObject(i) ?: continue
            if (o.optString("type") != "blob") continue
            val path = o.optString("path")
            val size = o.optLong("size", -1L)
            val sha = o.optString("sha")
            if (size <= 0 || size > MAX_SOURCE_BYTES || !SHA1.matches(sha)) continue
            val segments = path.split('/')
            if (segments.size < 2 || path.length > 300 || !segments.all { plainSegment(it) }) continue
            val file = segments.last()
            val ext = file.substringAfterLast('.', "").lowercase()
            if (ext !in IMAGE_EXT) continue
            out += CommunityWallpaper(
                id = "cp_" + sha1Hex(path).take(20),
                path = path,
                palette = segments.first(),
                category = if (segments.size >= 3) segments[1] else "General",
                name = prettyName(file.substringBeforeLast('.')),
                bytes = size,
                gitSha = sha.lowercase(),
            )
        }
        return out.sortedWith(compareBy({ it.palette.lowercase() }, { it.category.lowercase() }, { it.name.lowercase() }))
    }

    private val SHA1 = Regex("^[0-9a-fA-F]{40}$")

    private fun plainSegment(s: String) =
        s.isNotEmpty() && s.length <= 120 && s != "." && s != ".." && s.none { it == '\\' || it.code < 0x20 || it.code == 0x7f }

    private fun prettyName(base: String): String {
        val cleaned = base.replace(NAME_PREFIX, "").replace(Regex("[-_]+"), " ").trim()
        return cleaned.split(' ').filter { it.isNotEmpty() }
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
            .ifEmpty { base }
    }

    fun palettes(all: List<CommunityWallpaper>): List<String> = all.map { it.palette }.distinct().sortedBy { it.lowercase() }

    /** Categories of [palette], or of everything when [palette] is null. */
    fun categories(all: List<CommunityWallpaper>, palette: String?): List<String> =
        all.filter { palette == null || it.palette == palette }.map { it.category }.distinct().sortedBy { it.lowercase() }

    fun filter(all: List<CommunityWallpaper>, palette: String?, category: String?): List<CommunityWallpaper> =
        all.filter { (palette == null || it.palette == palette) && (category == null || it.category == category) }

    // ---- download ---------------------------------------------------------------------------------------------------

    /** Downloads, verifies and converts [w], leaving it installed in both tones. Throws, and leaves nothing behind, on any failure. */
    fun download(context: Context, w: CommunityWallpaper) {
        val d = File(context.filesDir, "wallpapers").apply { mkdirs() }
        val src = File(d, ".${w.id}.src.part")
        val darkTmp = File(d, ".${w.id}-dark.webp.part")
        val lightTmp = File(d, ".${w.id}-light.webp.part")
        try {
            fetchVerified(w, src)
            val bmp = decode(src, MAX_EDGE, requireMin = MIN_EDGE) ?: error("not a picture, or smaller than $MIN_EDGE px")
            try {
                val scaled = limitEdge(bmp, MAX_EDGE)
                darkTmp.outputStream().use { scaled.compress(webp(), 90, it) }
                if (scaled !== bmp) scaled.recycle()
            } finally {
                bmp.recycle()
            }
            // One picture serves both colour modes, like a single-tone store wallpaper.
            darkTmp.copyTo(lightTmp, overwrite = true)
            val darkDest = File(d, "${w.id}-dark.webp"); val lightDest = File(d, "${w.id}-light.webp")
            darkDest.delete(); lightDest.delete()
            require(darkTmp.renameTo(darkDest) && lightTmp.renameTo(lightDest)) { "could not save" }
            File(d, "${w.id}.json").writeText(
                JSONObject().put("name", w.name).put("author", "CozyPixels community").put("license", "MIT")
                    .put("source", Repos.COZY_HOME).toString()
            )
        } catch (t: Throwable) {
            File(d, "${w.id}-dark.webp").delete(); File(d, "${w.id}-light.webp").delete(); File(d, "${w.id}.json").delete()
            throw t
        } finally {
            src.delete(); darkTmp.delete(); lightTmp.delete()
        }
    }

    /** A small picture for the list, cached. Never blocks more than two at a time and gives up quietly if scrolled away. */
    suspend fun thumbnail(context: Context, w: CommunityWallpaper): Bitmap? {
        val f = File(thumbDir(context), "${w.id}-${w.gitSha.take(12)}.webp")
        if (!f.isFile) {
            thumbGate.withPermit {
                currentCoroutineContext().ensureActive() // scrolled past while waiting: don't download
                if (!f.isFile) runCatching { buildThumb(context, w, f) }
            }
        }
        return if (f.isFile) runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull() else null
    }

    private fun buildThumb(context: Context, w: CommunityWallpaper, dest: File) {
        val src = File(thumbDir(context), "${dest.name}.src.part")
        val tmp = File(thumbDir(context), "${dest.name}.part")
        try {
            fetchVerified(w, src)
            val bmp = decode(src, THUMB_WIDTH, requireMin = 0) ?: return
            try {
                tmp.outputStream().use { bmp.compress(webp(), 80, it) }
            } finally {
                bmp.recycle()
            }
            dest.delete(); tmp.renameTo(dest)
        } finally {
            src.delete(); tmp.delete()
        }
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    private fun fetchVerified(w: CommunityWallpaper, dest: File) {
        HttpsFetcher.download(w.rawUrl, dest, MAX_SOURCE_BYTES, w.bytes)
        require(dest.length() == w.bytes && gitBlobSha1(dest) == w.gitSha) { "downloaded file does not match the repository" }
    }

    /** Git names a file by SHA-1 of "blob <size>\0<bytes>", so we can check a download against the tree without trusting the host twice. */
    private fun gitBlobSha1(f: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update("blob ${f.length()}\u0000".toByteArray(Charsets.US_ASCII))
        f.inputStream().use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha1Hex(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /**
     * Decodes [file] at a reduced size: the smallest reduction that still leaves the long edge at or above [targetEdge]
     * (for thumbnails pass the thumbnail width, which is compared with the long edge too). Null if it isn't a picture
     * or is smaller than [requireMin] on its long edge.
     */
    private fun decode(file: File, targetEdge: Int, requireMin: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0 || longEdge > 16384 || longEdge < requireMin) return null
        var sample = 1
        while (longEdge / (sample * 2) >= targetEdge) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }

    private fun limitEdge(b: Bitmap, edge: Int): Bitmap {
        val longEdge = maxOf(b.width, b.height)
        if (longEdge <= edge) return b
        val k = edge.toFloat() / longEdge
        return Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
    }

    @Suppress("DEPRECATION")
    private fun webp(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
}
