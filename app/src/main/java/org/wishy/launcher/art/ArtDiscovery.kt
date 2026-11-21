package org.wishy.launcher.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.wishy.launcher.net.HttpsFetcher
import org.wishy.launcher.net.Repos
import org.wishy.theme.core.ArtCatalogs
import org.wishy.theme.core.ArtEntry
import org.wishy.theme.core.ArtIndex
import org.wishy.theme.core.ArtProviders
import org.wishy.theme.core.ArtSynonyms
import org.wishy.theme.core.CatalogLogo
import org.wishy.theme.core.FileHash
import org.wishy.theme.core.RepoChecker
import org.wishy.theme.core.RepoJson
import org.wishy.theme.core.RepoLimits
import org.wishy.theme.core.RepoUrls
import org.wishy.theme.core.SvgInfo
import org.wishy.theme.core.SvgSanitizer
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest

/** A logo that was downloaded (or found in the cache) and passed the SVG checks. */
class FetchedLogo(val logo: CatalogLogo, val bytes: ByteArray, val info: SvgInfo)

/**
 * Finds artwork. Privacy rules, all enforced here:
 *  - Catalogs (Simple Icons, SVGL, Arcticons, and the official Wishy index) are downloaded as WHOLE files.
 *    Matching them against installed apps happens on the device, so no app name or package is sent anywhere.
 *  - A single picture is requested only after the person opens that app's results or editor.
 *  - The only text ever sent is a search the person typed (Iconify) and the file name of a picture they opened.
 *  - Hosts are limited to [ArtProviders.ALLOWED_HOSTS]; everything goes through [HttpsFetcher] (https only, size caps).
 *  - Files are cached, so nothing is requested twice within a week (catalogs) or two weeks (logos).
 * Never call on the main thread.
 */
class ArtDiscovery private constructor(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.cacheDir, "art-discovery")

    class Catalogs(val logos: List<CatalogLogo>, val official: ArtIndex?, val unavailable: List<String>)

    private fun cachedText(name: String, url: String, maxBytes: Int, maxAgeMs: Long, allowNetwork: Boolean): String? {
        val f = File(dir, name)
        val fresh = f.isFile && System.currentTimeMillis() - f.lastModified() < maxAgeMs
        if (fresh) return runCatching { f.readText() }.getOrNull()
        if (allowNetwork) {
            val t = runCatching { HttpsFetcher.getText(url, maxBytes) }.getOrNull()
            if (t != null) { runCatching { dir.mkdirs(); f.writeText(t) }; return t }
        }
        return if (f.isFile) runCatching { f.readText() }.getOrNull() else null // an old copy beats nothing
    }

    /** Loads every catalog it can. With [allowNetwork] false only cached copies are used. */
    fun loadCatalogs(allowNetwork: Boolean): Catalogs {
        val week = 7L * 24 * 3600 * 1000
        val logos = ArrayList<CatalogLogo>()
        val missing = ArrayList<String>()
        for (info in ArtProviders.all) {
            val url = info.catalogUrl ?: continue
            val text = cachedText("catalog-${info.id}.txt", url, info.maxCatalogBytes, week, allowNetwork)
            val parsed = when {
                text == null -> emptyList()
                info.id == ArtProviders.SIMPLE_ICONS -> ArtCatalogs.parseSimpleIcons(text)
                info.id == ArtProviders.SVGL -> ArtCatalogs.parseSvgl(text)
                info.id == ArtProviders.ARCTICONS -> ArtCatalogs.parseArcticons(text)
                else -> emptyList()
            }
            if (parsed.isEmpty()) missing += info.name else logos += parsed
        }
        val official = runCatching {
            cachedText("official-index.json", Repos.OFFICIAL_ART_INDEX, RepoLimits.MAX_INDEX_BYTES, 24L * 3600 * 1000, allowNetwork)
                ?.let { RepoJson.parseArt(it) }?.takeIf { it.schemaVersion == 1 }
        }.getOrNull()
        return Catalogs(logos, official, missing)
    }

    /** The official entry for [pkg] if the index lists it and the entry passes the repository checks. */
    fun officialEntry(index: ArtIndex?, pkg: String): ArtEntry? {
        val e = index?.apps?.get(pkg) ?: return null
        return e.takeIf { RepoChecker.problems(ArtIndex(1, mapOf(pkg to e)), Repos.OFFICIAL_ART_INDEX).isEmpty() }
    }

    /** Downloads (or finds in the cache) one logo and checks it. Null when it cannot be had or is not a safe SVG. */
    fun fetchLogo(logo: CatalogLogo, allowNetwork: Boolean): FetchedLogo? {
        if (!logo.isSvg || !ArtProviders.isAllowedUrl(logo.url)) return null
        val f = File(dir, "logo-" + sha1(logo.url) + ".svg")
        val fresh = f.isFile && System.currentTimeMillis() - f.lastModified() < 14L * 24 * 3600 * 1000
        var bytes: ByteArray? = if (fresh) runCatching { f.readBytes() }.getOrNull() else null
        var downloaded = false
        if (bytes == null && allowNetwork) {
            dir.mkdirs()
            val part = File(dir, f.name + ".part")
            bytes = runCatching { HttpsFetcher.download(logo.url, part, SvgSanitizer.MAX_BYTES.toLong()); part.readBytes() }.getOrNull()
            part.delete()
            downloaded = bytes != null
        }
        if (bytes == null && f.isFile) bytes = runCatching { f.readBytes() }.getOrNull()
        val b = bytes ?: return null
        val info = runCatching { SvgSanitizer.inspect(b) }.getOrNull()
        if (info == null) { f.delete(); return null }
        if (downloaded) runCatching { f.writeBytes(b) }
        return FetchedLogo(logo, b, info)
    }

    /** Downloads the official banner for [pkg] into the cache and checks it. Null if it cannot be had. */
    fun officialFile(pkg: String, e: ArtEntry, allowNetwork: Boolean): File? {
        if (!RepoUrls.isPackageName(pkg) || !RepoUrls.isHexSha256(e.sha256)) return null
        val f = File(dir, "official-$pkg-${e.sha256.take(16)}.img")
        if (f.isFile) return f
        if (!allowNetwork) return null
        dir.mkdirs()
        // Old versions of this banner are no longer needed.
        dir.listFiles { x -> x.name.startsWith("official-$pkg-") }?.forEach { it.delete() }
        val part = File(dir, f.name + ".part")
        return try {
            HttpsFetcher.download(RepoUrls.resolve(Repos.OFFICIAL_ART_INDEX, e.url), part, RepoLimits.MAX_ART_BYTES, e.sizeBytes)
            FileHash.verifySha256(part, e.sha256)
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(part.path, o)
            if (o.outWidth != e.width || o.outHeight != e.height) throw BannerManager.ArtException("Size does not match the index.")
            if (!part.renameTo(f)) { part.copyTo(f, overwrite = true) }
            f
        } catch (x: Exception) {
            null
        } finally {
            part.delete()
        }
    }

    /** A small bitmap of a picture file for previews. */
    fun previewOf(file: File, maxWidth: Int = 640): Bitmap? = runCatching {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, b)
        if (b.outWidth <= 0) return null
        var sample = 1
        while (b.outWidth / (sample * 2) >= maxWidth) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /**
     * A search by a word the person typed, read one page at a time ("Fetch more"). The catalogs are searched on the
     * device. Iconify (Simple Icons, Material Symbols, logos and general icon sets) is asked only when network is
     * allowed, and then receives just the typed word and up to two of its synonyms (see [ArtSynonyms]).
     * Duplicates (same provider and name, whether from a catalog or from Iconify) are never returned twice.
     */
    inner class SearchSession(val query: String, private val catalogs: Catalogs?) {
        private val terms = ArtSynonyms.expand(query)
        private val seen = HashSet<String>()
        private var pool: List<CatalogLogo>? = null
        private var poolPos = 0
        private var netPage = 0
        private val netDone = BooleanArray(terms.size)

        /** More results may exist. True until every source ran dry. */
        var hasMore: Boolean = terms.isNotEmpty()
            private set

        private fun buildPool(): List<CatalogLogo> {
            val logos = catalogs?.logos ?: return emptyList()
            val rank = HashMap<CatalogLogo, Int>()
            for ((ti, t) in terms.withIndex()) {
                val q = ArtCatalogs.key(t)
                if (q.length < 2) continue
                for (l in logos) if (l.key.contains(q)) {
                    // exact name first, then names that start with the word, then the rest; earlier terms before synonyms
                    val r = ti * 1000 + (if (l.key == q) 0 else if (l.key.startsWith(q)) 200 else 500) + minOf(l.key.length, 99)
                    val old = rank[l]
                    if (old == null || r < old) rank[l] = r
                }
            }
            return rank.entries.sortedWith(compareBy({ it.value }, { !it.key.isSvg })).map { it.key }
        }

        /** The next page of results (empty when there is nothing more). Never call on the main thread. */
        fun next(allowNetwork: Boolean): List<CatalogLogo> {
            val out = ArrayList<CatalogLogo>()
            var guard = 0
            while (out.isEmpty() && hasMore && guard++ < 4) {
                val p = pool ?: buildPool().also { pool = it }
                while (poolPos < p.size && out.size < PAGE_LOCAL) {
                    val l = p[poolPos++]
                    if (seen.add(ArtCatalogs.dedupeKey(l))) out += l
                }
                var netMore = false
                if (allowNetwork) {
                    val prefixes = ArtProviders.ICONIFY_PREFIXES.joinToString(",")
                    for ((ti, t) in terms.take(MAX_NET_TERMS).withIndex()) {
                        if (netDone[ti]) continue
                        val start = netPage * PAGE_NET
                        val text = runCatching {
                            val enc = URLEncoder.encode(t.take(40), "UTF-8")
                            HttpsFetcher.getText("https://api.iconify.design/search?query=$enc&prefixes=$prefixes&limit=$PAGE_NET&start=$start", 256 * 1024)
                        }.getOrNull()
                        if (text == null) { netDone[ti] = true; continue }
                        val got = ArtCatalogs.parseIconifySearch(text)
                        val total = ArtCatalogs.parseIconifyTotal(text)
                        for (l in got) if (ArtProviders.isAllowedUrl(l.url) && seen.add(ArtCatalogs.dedupeKey(l))) out += l
                        if (got.isEmpty() || start + PAGE_NET >= total) netDone[ti] = true else netMore = true
                    }
                    netPage++
                } else {
                    netMore = false
                }
                val localMore = poolPos < p.size
                hasMore = localMore || netMore
            }
            if (out.isEmpty()) hasMore = false
            return out
        }
    }

    fun startSearch(query: String, catalogs: Catalogs?): SearchSession = SearchSession(query.trim(), catalogs)

    /** The first page of a search. Kept for callers that do not page. */
    fun search(query: String, catalogs: Catalogs?, allowNetwork: Boolean): List<CatalogLogo> =
        startSearch(query, catalogs).next(allowNetwork)

    /** Removes everything this class cached. User artwork is elsewhere and is not touched. */
    fun clearCache() { dir.deleteRecursively() }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)

    companion object {
        private const val PAGE_LOCAL = 24
        private const val PAGE_NET = 24
        private const val MAX_NET_TERMS = 3
        @Volatile private var instance: ArtDiscovery? = null
        fun get(context: Context): ArtDiscovery =
            instance ?: synchronized(this) { instance ?: ArtDiscovery(context).also { instance = it } }
    }
}
