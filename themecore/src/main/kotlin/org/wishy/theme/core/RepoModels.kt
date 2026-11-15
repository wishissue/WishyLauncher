package org.wishy.theme.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Limits for repository files. Indexes are small on purpose. */
object RepoLimits {
    const val MAX_INDEX_BYTES = 512 * 1024
    const val MAX_THEMES = 500
    const val MAX_ART_ENTRIES = 5000
    /** Most banner versions one app can offer. The picker downloads them all when opened, so keep it small. */
    const val MAX_ART_VARIANTS = 12
    const val MAX_ART_BYTES = 2L * 1024 * 1024
    const val MIN_ART_W = 320
    const val MIN_ART_H = 180
    const val MAX_ART_W = 1920
    const val MAX_ART_H = 1080
    /** Width/height of a banner must be within this fraction of 16:9. */
    const val ASPECT_TOLERANCE = 0.03f
}

/** `index.json` at the root of a theme repository. */
@Serializable
data class RepoIndex(
    val schemaVersion: Int,
    val name: String,
    val themes: List<RepoTheme> = emptyList(),
)

@Serializable
data class RepoTheme(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String = "",
    val license: String,
    val schemaVersion: Int,
    val minLauncherVersion: Int = 1,
    /** Relative to the index URL. */
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val previewUrl: String? = null,
    val creditsUrl: String? = null,
    val updatedAt: String? = null,
)

/** `artwork/index.json` in the official repository: package name -> one banner. */
@Serializable
data class ArtIndex(
    val schemaVersion: Int,
    val apps: Map<String, ArtEntry> = emptyMap(),
)

@Serializable
data class ArtEntry(
    /** Relative to the artwork index URL. */
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val author: String,
    val license: String,
    val source: String? = null,
    val title: String? = null,
)

/**
 * `artwork/variants.json`: package name -> several banners the person can choose between. It sits next to the
 * single-banner `index.json` (which older launchers keep reading unchanged), and anyone can host one.
 */
@Serializable
data class ArtVariantIndex(
    val schemaVersion: Int,
    val apps: Map<String, List<ArtVariant>> = emptyMap(),
)

@Serializable
data class ArtVariant(
    /** Unique within the app: lowercase letters, digits and underscores. */
    val id: String,
    /** Shown in the picker, for example "Dark logo" or "Key art". */
    val name: String,
    /** Relative to the variants index URL. */
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val author: String,
    val license: String,
    val source: String? = null,
)

object RepoJson {
    val json = Json { ignoreUnknownKeys = false; isLenient = false }

    fun parseIndex(text: String): RepoIndex = decode(text) { json.decodeFromString(RepoIndex.serializer(), it) }
    fun parseArt(text: String): ArtIndex = decode(text) { json.decodeFromString(ArtIndex.serializer(), it) }
    fun parseVariants(text: String): ArtVariantIndex = decode(text) { json.decodeFromString(ArtVariantIndex.serializer(), it) }

    private fun <T> decode(text: String, f: (String) -> T): T {
        if (text.length > RepoLimits.MAX_INDEX_BYTES) throw ThemeRejectedException("Repository index is too large.")
        return try { f(text) } catch (e: Exception) {
            throw ThemeRejectedException("Repository index is not valid: ${e.message?.take(160)}")
        }
    }
}

/** Resolves relative download locations and refuses anything that could send the launcher elsewhere. */
object RepoUrls {
    private val SHA = Regex("^[0-9a-f]{64}$")
    private val PKG = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    fun isHexSha256(s: String) = SHA.matches(s)
    fun isPackageName(s: String) = s.length <= 255 && PKG.matches(s)

    private val VARIANT_ID = Regex("^[a-z0-9_]{1,32}$")
    fun isVariantId(s: String) = VARIANT_ID.matches(s)

    /**
     * [base] is the URL the index was fetched from, [rel] a location from inside it. The result must be
     * https and on the same host and port as [base]. Throws ThemeRejectedException otherwise.
     */
    fun resolve(base: String, rel: String): String {
        val b = parse(base) ?: throw ThemeRejectedException("Bad repository address.")
        if (b.scheme != "https") throw ThemeRejectedException("Repositories must use https.")
        if (rel.contains('\\') || rel.any { it.code < 0x20 } || rel.contains("..")) throw ThemeRejectedException("Unsafe location: $rel")
        val absolute = rel.startsWith("http://") || rel.startsWith("https://")
        val url = if (absolute) rel else b.dir + rel.trimStart('/')
        val u = parse(url) ?: throw ThemeRejectedException("Bad location: $rel")
        if (u.scheme != "https") throw ThemeRejectedException("Only https locations are allowed.")
        if (!u.host.equals(b.host, ignoreCase = true) || u.port != b.port) throw ThemeRejectedException("Location is on another host: ${u.host}")
        return url
    }

    private class P(val scheme: String, val host: String, val port: Int, val dir: String)

    private fun parse(url: String): P? {
        val m = Regex("^(https?)://([^/:?#@]+)(?::(\\d{1,5}))?(/[^?#]*)?$", RegexOption.IGNORE_CASE).matchEntire(url) ?: return null
        val scheme = m.groupValues[1].lowercase()
        val port = m.groupValues[3].toIntOrNull() ?: if (scheme == "https") 443 else 80
        val path = m.groupValues[4].ifEmpty { "/" }
        val dir = path.substring(0, path.lastIndexOf('/') + 1)
        return P(scheme, m.groupValues[2], port, "$scheme://${m.groupValues[2]}${m.groupValues[3].let { if (it.isEmpty()) "" else ":$it" }}$dir")
    }
}

/** Checks a parsed index against the rules the client relies on. Returns problems, empty when fine. */
object RepoChecker {
    private val VERSION = Regex("^\\d+\\.\\d+\\.\\d+$")

    fun problems(index: RepoIndex, indexUrl: String): List<String> {
        val out = ArrayList<String>()
        if (index.schemaVersion != 1) out += "Unsupported repository schemaVersion ${index.schemaVersion}."
        if (index.themes.size > RepoLimits.MAX_THEMES) out += "Too many themes."
        val seen = HashSet<String>()
        for (t in index.themes) {
            val tag = "theme '${t.id}'"
            if (!ThemeLimits.ID_REGEX.matches(t.id)) out += "$tag: bad id."
            if (!seen.add(t.id)) out += "$tag: duplicate id."
            if (!VERSION.matches(t.version)) out += "$tag: version must be MAJOR.MINOR.PATCH."
            if (t.schemaVersion != 1) out += "$tag: schemaVersion ${t.schemaVersion} is not supported."
            if (!RepoUrls.isHexSha256(t.sha256)) out += "$tag: sha256 must be 64 lowercase hex characters."
            if (t.sizeBytes <= 0 || t.sizeBytes > ThemeLimits.MAX_ZIP_BYTES) out += "$tag: sizeBytes out of range."
            if (t.license.isBlank()) out += "$tag: license is required."
            for ((what, u) in listOfNotNull("downloadUrl" to t.downloadUrl, t.previewUrl?.let { "previewUrl" to it }, t.creditsUrl?.let { "creditsUrl" to it })) {
                try { RepoUrls.resolve(indexUrl, u) } catch (e: ThemeRejectedException) { out += "$tag: $what: ${e.message}" }
            }
        }
        return out
    }

    fun problems(index: ArtIndex, indexUrl: String): List<String> {
        val out = ArrayList<String>()
        if (index.schemaVersion != 1) out += "Unsupported artwork schemaVersion ${index.schemaVersion}."
        if (index.apps.size > RepoLimits.MAX_ART_ENTRIES) out += "Too many artwork entries."
        for ((pkg, e) in index.apps) {
            val tag = "artwork '$pkg'"
            if (!RepoUrls.isPackageName(pkg)) out += "$tag: not a package name."
            if (!RepoUrls.isHexSha256(e.sha256)) out += "$tag: bad sha256."
            if (e.sizeBytes <= 0 || e.sizeBytes > RepoLimits.MAX_ART_BYTES) out += "$tag: sizeBytes out of range."
            if (e.width !in RepoLimits.MIN_ART_W..RepoLimits.MAX_ART_W || e.height !in RepoLimits.MIN_ART_H..RepoLimits.MAX_ART_H) out += "$tag: size ${e.width}x${e.height} out of range."
            else if (!isBannerAspect(e.width, e.height)) out += "$tag: not 16:9."
            if (e.author.isBlank() || e.license.isBlank()) out += "$tag: author and license are required."
            try { RepoUrls.resolve(indexUrl, e.url) } catch (x: ThemeRejectedException) { out += "$tag: ${x.message}" }
        }
        return out
    }

    /** Same rules as a single banner, applied to every version of every app. Problems are reported per version. */
    fun problems(index: ArtVariantIndex, indexUrl: String): List<String> {
        val out = ArrayList<String>()
        if (index.schemaVersion != 1) out += "Unsupported artwork variants schemaVersion ${index.schemaVersion}."
        if (index.apps.values.sumOf { it.size } > RepoLimits.MAX_ART_ENTRIES) out += "Too many artwork entries."
        for ((pkg, list) in index.apps) {
            if (!RepoUrls.isPackageName(pkg)) out += "artwork '$pkg': not a package name."
            if (list.size > RepoLimits.MAX_ART_VARIANTS) out += "artwork '$pkg': more than ${RepoLimits.MAX_ART_VARIANTS} versions."
            val seen = HashSet<String>()
            for (v in list) {
                val tag = "artwork '$pkg/${v.id}'"
                if (!RepoUrls.isVariantId(v.id)) out += "$tag: bad version id."
                if (!seen.add(v.id)) out += "$tag: duplicate version id."
                if (v.name.isBlank() || v.name.length > 40) out += "$tag: name must be 1 to 40 characters."
                if (!RepoUrls.isHexSha256(v.sha256)) out += "$tag: bad sha256."
                if (v.sizeBytes <= 0 || v.sizeBytes > RepoLimits.MAX_ART_BYTES) out += "$tag: sizeBytes out of range."
                if (v.width !in RepoLimits.MIN_ART_W..RepoLimits.MAX_ART_W || v.height !in RepoLimits.MIN_ART_H..RepoLimits.MAX_ART_H) out += "$tag: size ${v.width}x${v.height} out of range."
                else if (!isBannerAspect(v.width, v.height)) out += "$tag: not 16:9."
                if (v.author.isBlank() || v.license.isBlank()) out += "$tag: author and license are required."
                try { RepoUrls.resolve(indexUrl, v.url) } catch (x: ThemeRejectedException) { out += "$tag: ${x.message}" }
            }
        }
        return out
    }

    fun isBannerAspect(w: Int, h: Int): Boolean {
        if (w <= 0 || h <= 0) return false
        val r = w.toFloat() / h
        return kotlin.math.abs(r - 16f / 9f) / (16f / 9f) <= RepoLimits.ASPECT_TOLERANCE
    }

    /** True when [candidate] is a newer MAJOR.MINOR.PATCH than [installed]. */
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = candidate.split(".").map { it.toIntOrNull() ?: return false }
        val b = installed.split(".").map { it.toIntOrNull() ?: return false }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
