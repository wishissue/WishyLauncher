package org.wishy.launcher.art

import android.content.Context
import android.graphics.BitmapFactory
import org.json.JSONObject
import org.wishy.launcher.net.HttpsFetcher
import org.wishy.launcher.net.Repos
import org.wishy.theme.core.FileHash
import java.io.File

class StoreFile(val file: String, val sha256: String, val bytes: Long)

/** One entry of the store. [light] is null for a picture that serves both colour modes. */
class StoreWallpaper(
    val id: String, val name: String, val author: String, val license: String, val source: String,
    val dark: StoreFile, val light: StoreFile?, val thumb: StoreFile,
) {
    val totalBytes: Long get() = dark.bytes + (light?.bytes ?: 0L)
}

/**
 * The wallpaper store: a list on the official repository (repo/wallpapers/index.json, built by tools/build_repo.py).
 * Every file is checked against its SHA-256 before it is kept, sizes are capped, and file names are matched against a
 * strict pattern so a bad index can't write outside our folder. Pictures live in filesDir/wallpapers/.
 */
object WallpaperStore {
    private val ID = Regex("^[a-z0-9_]{1,40}$")
    private val NAME = Regex("^[a-z0-9_]{1,40}(-dark|-light|-thumb)?\\.webp$")
    private const val MAX_PICTURE = 2_000_000L
    private const val MAX_THUMB = 200_000L

    private fun dir(context: Context) = File(context.filesDir, "wallpapers").apply { mkdirs() }
    private fun thumbDir(context: Context) = File(context.cacheDir, "wallpaper_thumbs").apply { mkdirs() }

    private fun file(o: JSONObject): StoreFile {
        val f = StoreFile(o.getString("file"), o.getString("sha256"), o.getLong("bytes"))
        require(NAME.matches(f.file)) { "bad file name" }
        return f
    }

    /** Fetches the list. Throws on network or format trouble; entries that don't parse are skipped. */
    fun fetchIndex(): List<StoreWallpaper> {
        val root = JSONObject(HttpsFetcher.getText(Repos.OFFICIAL_WALLPAPERS_INDEX, 512 * 1024))
        val arr = root.getJSONArray("wallpapers")
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                require(ID.matches(id))
                StoreWallpaper(
                    id = id, name = o.getString("name"), author = o.getString("author"),
                    license = o.getString("license"), source = o.optString("source"),
                    dark = file(o.getJSONObject("dark")),
                    light = o.optJSONObject("light")?.let { file(it) },
                    thumb = file(o.getJSONObject("thumb")),
                )
            }.getOrNull()
        }
    }

    fun isInstalled(context: Context, id: String) = ID.matches(id) && File(dir(context), "$id.json").isFile

    /** Downloads both tones into place. Throws (and leaves nothing behind) if anything fails to download or verify. */
    fun download(context: Context, w: StoreWallpaper) {
        require(ID.matches(w.id))
        val d = dir(context)
        val darkTmp = File(d, ".${w.id}-dark.webp.part")
        val lightTmp = File(d, ".${w.id}-light.webp.part")
        fun fetch(sf: StoreFile, tmp: File) {
            HttpsFetcher.download(Repos.OFFICIAL_WALLPAPERS_BASE + sf.file, tmp, MAX_PICTURE, sf.bytes)
            FileHash.verifySha256(tmp, sf.sha256)
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.path, opts)
            require(maxOf(opts.outWidth, opts.outHeight) in 1280..3840) { "not a wallpaper-sized picture" }
        }
        try {
            fetch(w.dark, darkTmp)
            // A single-tone picture serves both colour modes: keep one copy per tone so the rest of the app needs no special case.
            if (w.light != null) fetch(w.light, lightTmp) else darkTmp.copyTo(lightTmp, overwrite = true)
            val darkDest = File(d, "${w.id}-dark.webp"); val lightDest = File(d, "${w.id}-light.webp")
            darkDest.delete(); lightDest.delete()
            require(darkTmp.renameTo(darkDest) && lightTmp.renameTo(lightDest)) { "could not save" }
            File(d, "${w.id}.json").writeText(JSONObject().put("name", w.name).put("author", w.author).put("license", w.license).toString())
        } catch (t: Throwable) {
            darkTmp.delete(); lightTmp.delete()
            File(d, "${w.id}-dark.webp").delete(); File(d, "${w.id}-light.webp").delete()
            throw t
        }
    }

    fun remove(context: Context, id: String) {
        if (!ID.matches(id)) return
        val d = dir(context)
        listOf("$id.json", "$id-dark.webp", "$id-light.webp").forEach { File(d, it).delete() }
    }

    /** Downloaded wallpapers as ready-to-use entries. */
    fun installed(context: Context): List<BundledWallpaper> {
        val d = dir(context)
        return (d.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray()).sortedBy { it.name }.mapNotNull { meta ->
            runCatching {
                val id = meta.name.removeSuffix(".json")
                val dark = File(d, "$id-dark.webp"); val light = File(d, "$id-light.webp")
                if (!ID.matches(id) || !dark.isFile || !light.isFile) return@runCatching null
                BundledWallpaper(id, 0, 0, 0, title = JSONObject(meta.readText()).getString("name"), darkFile = dark, lightFile = light)
            }.getOrNull()
        }
    }

    /** A small picture for the list, cached. Call off the main thread. Null if it can't be had. */
    fun thumbnail(context: Context, w: StoreWallpaper): android.graphics.Bitmap? = runCatching {
        val f = File(thumbDir(context), "${w.id}-${w.thumb.sha256.take(12)}.webp")
        if (!f.isFile) {
            val tmp = File(thumbDir(context), "${f.name}.part")
            HttpsFetcher.download(Repos.OFFICIAL_WALLPAPERS_BASE + w.thumb.file, tmp, MAX_THUMB, w.thumb.bytes)
            FileHash.verifySha256(tmp, w.thumb.sha256)
            f.delete(); tmp.renameTo(f)
        }
        BitmapFactory.decodeFile(f.path)
    }.getOrNull()
}
