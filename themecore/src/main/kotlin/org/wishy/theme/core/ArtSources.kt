package org.wishy.theme.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.abs

/** One logo a provider offers. [url] is downloaded only when the user opens the app's result page. */
data class CatalogLogo(
    val provider: String,
    val title: String,
    /** Lower-case, letters and digits only. Used for matching. */
    val key: String,
    val url: String,
    val isSvg: Boolean,
    val brandHex: String? = null,
    val license: String,
    val author: String,
    val sourcePage: String? = null,
    /** Single-colour line icon: draw it tinted on the banner instead of as it is. */
    val monochrome: Boolean = false,
    /** Packages this logo is known to belong to (Arcticons ships such a list). */
    val packages: Set<String> = emptySet(),
)

/** A provider of logos. Everything is described here so it can be tested without the network. */
object ArtProviders {
    const val SIMPLE_ICONS = "simple-icons"
    const val SVGL = "svgl"
    const val ARCTICONS = "arcticons"
    const val ICONIFY = "iconify"

    class Info(val id: String, val name: String, val license: String, val homepage: String, val catalogUrl: String?, val maxCatalogBytes: Int)

    val all: List<Info> = listOf(
        Info(SIMPLE_ICONS, "Simple Icons", "CC0-1.0 (logos are trademarks of their owners)", "https://simpleicons.org",
            "https://raw.githubusercontent.com/simple-icons/simple-icons/develop/data/simple-icons.json", 3 * 1024 * 1024),
        Info(SVGL, "SVGL", "Per logo, see svgl.app (logos are trademarks of their owners)", "https://svgl.app",
            "https://api.svgl.app", 2 * 1024 * 1024),
        Info(ARCTICONS, "Arcticons", "CC BY-SA 4.0", "https://github.com/Arcticons-Team/Arcticons",
            "https://raw.githubusercontent.com/Arcticons-Team/Arcticons/main/app/assets/appfilter.xml", 6 * 1024 * 1024),
        Info(ICONIFY, "Iconify (logos set)", "Per icon set, see iconify.design", "https://iconify.design", null, 512 * 1024),
    )

    fun info(id: String): Info? = all.firstOrNull { it.id == id }

    /** Only these hosts are ever contacted for pictures, so an index cannot send the launcher elsewhere. */
    val ALLOWED_HOSTS: Set<String> = setOf("raw.githubusercontent.com", "api.svgl.app", "svgl.app", "api.iconify.design")

    fun isAllowedUrl(url: String): Boolean {
        val m = Regex("^https://([^/:?#@]+)(?::443)?(/[^?#]*)?(\\?[^#]*)?$", RegexOption.IGNORE_CASE).matchEntire(url) ?: return false
        return m.groupValues[1].lowercase() in ALLOWED_HOSTS && !url.contains("..")
    }
}

/** Parsers for the catalogs. Each returns nothing (never throws) for a file it does not understand. */
object ArtCatalogs {
    private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val SI_RAW = "https://raw.githubusercontent.com/simple-icons/simple-icons/develop/icons/"
    private const val ARCTICONS_RAW = "https://raw.githubusercontent.com/Arcticons-Team/Arcticons/main/icons/white/"
    const val MAX_LOGOS = 8000

    /** Letters and digits only, with "+" and "&" spelled out so "Disney+" and "disneyplus" agree. */
    fun key(s: String): String =
        s.lowercase().replace("+", "plus").replace("&", "and").filter { it in 'a'..'z' || it in '0'..'9' }

    /** Simple Icons' own rule for turning a title into a file name. */
    fun simpleIconsSlug(title: String): String {
        var t = title.lowercase()
        t = t.replace("+", "plus").replace(".", "dot").replace("&", "and")
        t = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return t.filter { it in 'a'..'z' || it in '0'..'9' }
    }

    fun parseSimpleIcons(text: String): List<CatalogLogo> = runCatching {
        val root = lenient.parseToJsonElement(text)
        val arr: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["icons"] as? JsonArray) ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        val info = ArtProviders.info(ArtProviders.SIMPLE_ICONS)!!
        val out = ArrayList<CatalogLogo>()
        for (el in arr) {
            val o = el as? JsonObject ?: continue
            val title = o.str("title") ?: continue
            val slug = (o.str("slug") ?: simpleIconsSlug(title)).takeIf { Regex("^[a-z0-9_-]{1,80}$").matches(it) } ?: continue
            val hex = o.str("hex")?.takeIf { Regex("^[0-9A-Fa-f]{6}$").matches(it) }?.let { "#" + it.uppercase() }
            out += CatalogLogo(info.id, title, key(title), "$SI_RAW$slug.svg", true, hex, "CC0-1.0", "Simple Icons contributors",
                "https://simpleicons.org/?q=${slug}", monochrome = true)
            if (out.size >= MAX_LOGOS) break
        }
        out
    }.getOrDefault(emptyList())

    fun parseSvgl(text: String): List<CatalogLogo> = runCatching {
        val root = lenient.parseToJsonElement(text)
        val arr = (root as? JsonArray) ?: return@runCatching emptyList()
        val out = ArrayList<CatalogLogo>()
        for (el in arr) {
            val o = el as? JsonObject ?: continue
            val title = o.str("title") ?: continue
            val route = o["route"]
            val url = when (route) {
                is JsonPrimitive -> route.contentOrNull
                is JsonObject -> route.str("dark") ?: route.str("light")
                else -> null
            } ?: continue
            if (!url.lowercase().endsWith(".svg") || !ArtProviders.isAllowedUrl(url)) continue
            out += CatalogLogo(ArtProviders.SVGL, title, key(title), url, true, null, "See SVGL (logo belongs to its owner)", "SVGL",
                o.str("url")?.takeIf { it.startsWith("https://") })
            if (out.size >= MAX_LOGOS) break
        }
        out
    }.getOrDefault(emptyList())

    /** Arcticons' appfilter.xml maps `ComponentInfo{package/activity}` to a drawable name. */
    fun parseArcticons(text: String): List<CatalogLogo> = runCatching {
        val rx = Regex("component=\"ComponentInfo\\{([A-Za-z][A-Za-z0-9_.]*)/[^}\"]*\\}\"\\s+drawable=\"([a-z0-9_]{1,80})\"")
        val byDrawable = LinkedHashMap<String, MutableSet<String>>()
        for (m in rx.findAll(text)) byDrawable.getOrPut(m.groupValues[2]) { LinkedHashSet() } += m.groupValues[1]
        val out = ArrayList<CatalogLogo>()
        for ((drawable, pkgs) in byDrawable) {
            val title = drawable.replace('_', ' ')
            out += CatalogLogo(ArtProviders.ARCTICONS, title, key(title), "$ARCTICONS_RAW$drawable.svg", true, null,
                "CC BY-SA 4.0", "Arcticons contributors", "https://github.com/Arcticons-Team/Arcticons", monochrome = true, packages = pkgs)
            if (out.size >= MAX_LOGOS) break
        }
        out
    }.getOrDefault(emptyList())

    /** Iconify search answer: {"icons": ["logos:netflix", ...]}. Only used when the user types a search. */
    fun parseIconifySearch(text: String): List<CatalogLogo> = runCatching {
        val root = lenient.parseToJsonElement(text) as? JsonObject ?: return@runCatching emptyList()
        val icons = root["icons"] as? JsonArray ?: return@runCatching emptyList()
        val out = ArrayList<CatalogLogo>()
        for (el in icons) {
            val id = (el as? JsonPrimitive)?.contentOrNull ?: continue
            val m = Regex("^([a-z0-9-]{1,40}):([a-z0-9-]{1,80})$").matchEntire(id) ?: continue
            val (prefix, name) = m.destructured
            out += CatalogLogo(ArtProviders.ICONIFY, name.replace('-', ' '), key(name), "https://api.iconify.design/$prefix/$name.svg", true,
                null, "See the \"$prefix\" icon set on iconify.design", "Iconify: $prefix", "https://icon-sets.iconify.design/$prefix/$name/")
            if (out.size >= 24) break
        }
        out
    }.getOrDefault(emptyList())

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}

/** One installed app, as far as matching is concerned. */
data class MatchApp(val pkg: String, val label: String, val category: String = "")

data class LogoMatch(val logo: CatalogLogo, val score: Int)

/**
 * Matches installed apps to catalog logos on the device. The catalogs are downloaded as whole files, so no app
 * name is ever sent anywhere to find a match.
 */
object ArtMatcher {
    const val MIN_SCORE = 70
    const val MAX_PER_APP = 4

    /** Known TV packages whose name is not the brand name. Brand keys are plain letters and digits. */
    private val PACKAGE_BRANDS: Map<String, List<String>> = mapOf(
        "com.google.android.youtube.tv" to listOf("youtube"),
        "com.google.android.youtube.tvunplugged" to listOf("youtubetv", "youtube"),
        "com.google.android.youtube.tvmusic" to listOf("youtubemusic"),
        "com.amazon.amazonvideo.livingroom" to listOf("primevideo", "amazonprimevideo"),
        "com.amazon.firetv.youtube" to listOf("youtube"),
        "com.netflix.ninja" to listOf("netflix"),
        "com.disney.disneyplus" to listOf("disney", "disneyplus"),
        "com.wbd.stream" to listOf("max", "hbomax"),
        "com.hbo.hbonow" to listOf("hbo"),
        "com.plexapp.android" to listOf("plex"),
        "tv.twitch.android.viewer" to listOf("twitch"),
        "org.jellyfin.androidtv" to listOf("jellyfin"),
        "org.xbmc.kodi" to listOf("kodi"),
        "com.spotify.tv.android" to listOf("spotify"),
        "com.aspiro.tidal" to listOf("tidal"),
        "com.valvesoftware.steamlink" to listOf("steam", "steamlink"),
        "com.limelight" to listOf("moonlight"),
        "com.nvidia.geforcenow" to listOf("geforcenow", "nvidia"),
        "com.retroarch" to listOf("retroarch"),
        "com.apple.atve.androidtv.appletv" to listOf("appletv", "apple"),
        "com.cbs.ott" to listOf("paramountplus", "paramount"),
        "com.hulu.plus" to listOf("hulu"),
        "com.crunchyroll.crunchyrollmobile" to listOf("crunchyroll"),
        "com.stremio.one" to listOf("stremio"),
        "org.videolan.vlc" to listOf("vlc", "vlcmediaplayer"),
        "com.mxtech.videoplayer.ad" to listOf("mxplayer"),
        "com.teamsmart.videomanager.tv" to listOf("smartyoutubetv"),
    )

    private val NOISE = setOf(
        "com", "org", "net", "io", "tv", "app", "apps", "android", "androidtv", "google", "mobile", "tvapp", "ninja",
        "livingroom", "viewer", "client", "release", "main", "ui", "lite", "free", "pro", "player", "the", "for",
    )

    /** Keys that describe this app, strongest first: brand table, label, then package segments. */
    fun appKeys(app: MatchApp): List<String> {
        val keys = LinkedHashSet<String>()
        PACKAGE_BRANDS[app.pkg]?.forEach { keys += it }
        val label = ArtCatalogs.key(app.label)
        if (label.length >= 2) keys += label
        val segs = app.pkg.split('.').map { ArtCatalogs.key(it) }.filter { it.length >= 3 && it !in NOISE }
        // The middle segment (company or product) is usually the brand: com.netflix.ninja -> netflix.
        segs.forEach { keys += it }
        return keys.toList()
    }

    fun score(keys: List<String>, logo: CatalogLogo, pkg: String): Int {
        if (pkg in logo.packages) return 100
        var best = 0
        for ((i, k) in keys.withIndex()) {
            val s = when {
                k == logo.key -> 95
                // "disneyplus" vs "disney": one contains the other and the shorter one is a real share of the longer.
                k.length >= 4 && logo.key.length >= 4 && (k.startsWith(logo.key) || logo.key.startsWith(k)) &&
                    minOf(k.length, logo.key.length).toFloat() / maxOf(k.length, logo.key.length) >= 0.6f -> 78
                else -> 0
            }
            // The first keys come from the brand table or the label, so they count a little more.
            val adj = if (s > 0 && i == 0) s + 3 else s
            if (adj > best) best = adj
        }
        return best.coerceAtMost(100)
    }

    /** Best matches for each app across [catalogs] (already merged), highest score first. Apps without a match are absent. */
    fun match(apps: List<MatchApp>, catalogs: List<CatalogLogo>): Map<String, List<LogoMatch>> {
        if (catalogs.isEmpty()) return emptyMap()
        val byKey = HashMap<String, MutableList<CatalogLogo>>()
        val byPackage = HashMap<String, MutableList<CatalogLogo>>()
        for (l in catalogs) {
            byKey.getOrPut(l.key) { ArrayList() } += l
            for (p in l.packages) byPackage.getOrPut(p) { ArrayList() } += l
        }
        val result = LinkedHashMap<String, List<LogoMatch>>()
        for (app in apps) {
            val keys = appKeys(app)
            val found = LinkedHashMap<String, LogoMatch>()
            fun add(l: CatalogLogo) {
                val s = score(keys, l, app.pkg)
                if (s >= MIN_SCORE) found.putIfAbsent(l.provider + "|" + l.url, LogoMatch(l, s))
            }
            byPackage[app.pkg]?.forEach { add(it) }
            for (k in keys) byKey[k]?.forEach { add(it) }
            // Prefix matches ("disney" for "disneyplus") need a scan; keep it to short key lists.
            for (k in keys.take(3)) if (k.length >= 4) {
                for ((ck, list) in byKey) {
                    if (ck.length >= 4 && (ck.startsWith(k) || k.startsWith(ck)) && ck != k) list.forEach { add(it) }
                }
            }
            if (found.isNotEmpty()) {
                result[app.pkg] = found.values
                    .sortedWith(compareByDescending<LogoMatch> { it.score }.thenBy { providerRank(it.logo.provider) })
                    .take(MAX_PER_APP)
            }
        }
        return result
    }

    /** Prefer coloured brand logos, then Simple Icons (CC0), then Arcticons. */
    private fun providerRank(p: String) = when (p) {
        ArtProviders.SVGL -> 0
        ArtProviders.SIMPLE_ICONS -> 1
        ArtProviders.ARCTICONS -> 2
        else -> 3
    }

    /** True when [a] and [b] are about the same size to within a tolerance. Small helper for tests. */
    internal fun near(a: Float, b: Float, tol: Float = 0.001f) = abs(a - b) <= tol
}

/** Small built-in line icons for apps that have no logo anywhere. They are SVG, so they go through the same pipeline. */
object GenericIcons {
    class Icon(val id: String, val name: String, val svg: String)

    private fun svg(path: String) =
        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><path fill=\"#000000\" d=\"$path\"/></svg>"

    val all: List<Icon> = listOf(
        Icon("play", "Play", svg("M8 5v14l11-7z")),
        Icon("music", "Music", svg("M12 3v10.55A4 4 0 1 0 14 17V7h4V3h-6z")),
        Icon("game", "Games", svg("M21 6H3a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h18a1 1 0 0 0 1-1V7a1 1 0 0 0-1-1zM11 13H9v2H7v-2H5v-2h2V9h2v2h2v2zm4.5 2a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm3-3a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3z")),
        Icon("apps", "Apps", svg("M4 4h6v6H4V4zm10 0h6v6h-6V4zM4 14h6v6H4v-6zm10 0h6v6h-6v-6z")),
        Icon("tv", "TV", svg("M21 3H3a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h5v2h8v-2h5a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2zm0 14H3V5h18v12z")),
        Icon("star", "Star", svg("M12 17.27 18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z")),
        Icon("heart", "Heart", svg("M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z")),
        Icon("bolt", "Bolt", svg("M11 21h-1l1-7H7.5c-.58 0-.57-.32-.38-.66.19-.34.05-.08.07-.12C8.48 10.94 10.42 7.54 13 3h1l-1 7h3.5c.49 0 .56.33.47.51l-.07.15C12.96 17.55 11 21 11 21z")),
    )

    fun forCategory(category: String): Icon = when (category) {
        "streaming" -> all[0]
        "music" -> all[1]
        "games" -> all[2]
        else -> all[3]
    }

    fun byId(id: String): Icon? = all.firstOrNull { it.id == id }
}
