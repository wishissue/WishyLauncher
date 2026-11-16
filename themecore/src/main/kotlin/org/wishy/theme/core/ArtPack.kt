package org.wishy.theme.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Where a picture came from. Shown to the user and written to the pack manifest. */
@Serializable
enum class ArtOrigin { OFFICIAL, COMMUNITY, GENERATED, USER, APP }

@Serializable
data class PackEntry(
    val `package`: String,
    /** Path inside the zip of the finished banner, e.g. "banners/com.example.app.webp". */
    val banner: String,
    val width: Int,
    val height: Int,
    /** Path of the original logo (SVG or PNG), kept so the banner can be edited again. Optional. */
    val logo: String? = null,
    val spec: BannerSpec? = null,
    val origin: ArtOrigin = ArtOrigin.USER,
    /** Provider id such as "simple-icons", or "import". */
    val source: String = "",
    val author: String = "",
    val license: String = "",
    val sourceUrl: String? = null,
    val title: String = "",
)

/** `manifest.json` of an artwork pack (a plain zip). */
@Serializable
data class PackManifest(
    val format: String = ArtPack.FORMAT,
    val version: Int = ArtPack.VERSION,
    val createdAt: String = "",
    val launcher: String = "Wishy Launcher",
    val entries: List<PackEntry> = emptyList(),
)

/** One entry plus the bytes to write. */
class PackFile(val entry: PackEntry, val bannerBytes: ByteArray, val logoBytes: ByteArray?)

/**
 * The artwork pack format: a zip with `manifest.json`, `banners/<package>.<webp|png|jpg>` and optionally
 * `logos/<package>.<svg|png>`. Reading is as strict as theme unzipping: only these names, size caps while
 * writing to disk, a compression-ratio limit and no overwriting.
 */
object ArtPack {
    const val FORMAT = "wishy-artwork-pack"
    const val VERSION = 1
    const val MAX_ENTRIES = 500
    const val MAX_ZIP_BYTES = 150L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 400L * 1024 * 1024
    const val MAX_BANNER_BYTES = 5L * 1024 * 1024
    const val MAX_LOGO_PNG_BYTES = 2L * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 2L * 1024 * 1024
    private const val MAX_RATIO = 200L

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val BANNER_NAME = Regex("^banners/([A-Za-z][A-Za-z0-9_.]*)\\.(webp|png|jpg|jpeg)$")
    private val LOGO_NAME = Regex("^logos/([A-Za-z][A-Za-z0-9_.]*)\\.(svg|png)$")

    fun bannerPath(pkg: String, ext: String) = "banners/$pkg.$ext"
    fun logoPath(pkg: String, ext: String) = "logos/$pkg.$ext"

    fun encodeManifest(m: PackManifest): String = json.encodeToString(PackManifest.serializer(), m)

    fun decodeManifest(text: String): PackManifest {
        if (text.length > MAX_MANIFEST_BYTES) throw ThemeRejectedException("The pack manifest is too large.")
        val m = try { json.decodeFromString(PackManifest.serializer(), text) } catch (e: Exception) {
            throw ThemeRejectedException("This is not a valid artwork pack.")
        }
        if (m.format != FORMAT) throw ThemeRejectedException("This is not a Wishy artwork pack.")
        if (m.version != VERSION) throw ThemeRejectedException("This artwork pack version is not supported.")
        if (m.entries.size > MAX_ENTRIES) throw ThemeRejectedException("The pack has too many entries.")
        val seen = HashSet<String>()
        for (e in m.entries) {
            if (!RepoUrls.isPackageName(e.`package`)) throw ThemeRejectedException("The pack names an invalid app.")
            if (!seen.add(e.`package`)) throw ThemeRejectedException("The pack lists an app twice.")
            if (!BANNER_NAME.matches(e.banner) || BANNER_NAME.matchEntire(e.banner)!!.groupValues[1] != e.`package`) {
                throw ThemeRejectedException("The pack has an unsafe banner name.")
            }
            if (e.logo != null && (!LOGO_NAME.matches(e.logo) || LOGO_NAME.matchEntire(e.logo)!!.groupValues[1] != e.`package`)) {
                throw ThemeRejectedException("The pack has an unsafe logo name.")
            }
            if (e.width !in 1..ThemeLimits.MAX_IMAGE_DIMENSION || e.height !in 1..ThemeLimits.MAX_IMAGE_DIMENSION) {
                throw ThemeRejectedException("The pack has a banner with a bad size.")
            }
        }
        return m
    }

    /** Writes [files] and the manifest to [out]. */
    fun write(out: java.io.OutputStream, files: List<PackFile>, createdAt: String) {
        require(files.size <= MAX_ENTRIES) { "Too many entries." }
        val manifest = PackManifest(createdAt = createdAt, entries = files.map { it.entry })
        ZipOutputStream(out.buffered()).use { z ->
            z.putNextEntry(ZipEntry("manifest.json"))
            z.write(encodeManifest(manifest).toByteArray(Charsets.UTF_8))
            z.closeEntry()
            for (f in files) {
                z.putNextEntry(ZipEntry(f.entry.banner)); z.write(f.bannerBytes); z.closeEntry()
                val lp = f.entry.logo
                if (lp != null && f.logoBytes != null) { z.putNextEntry(ZipEntry(lp)); z.write(f.logoBytes); z.closeEntry() }
            }
        }
    }

    /**
     * Extracts a pack into the empty folder [destDir] and returns its manifest. Only `manifest.json`, banners and
     * logos with the exact names above are accepted; every file named in the manifest must exist.
     */
    fun extract(zip: File, destDir: File): PackManifest {
        if (!destDir.isDirectory || (destDir.list()?.isNotEmpty() != false)) throw IllegalArgumentException("Destination must be an existing, empty folder.")
        val zipSize = zip.length()
        if (zipSize <= 0 || zipSize > MAX_ZIP_BYTES) throw ThemeRejectedException("The pack file is empty or too large.")
        val root = destDir.canonicalFile
        val prefix = root.path + File.separator
        var total = 0L
        var files = 0
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                if (e.isDirectory) { zin.closeEntry(); continue }
                val name = e.name
                val limit = when {
                    name == "manifest.json" -> MAX_MANIFEST_BYTES
                    BANNER_NAME.matches(name) -> MAX_BANNER_BYTES
                    LOGO_NAME.matches(name) -> if (name.endsWith(".svg")) SvgSanitizer.MAX_BYTES.toLong() else MAX_LOGO_PNG_BYTES
                    else -> throw ThemeRejectedException("The pack contains a file that is not allowed: $name")
                }
                files++
                if (files > MAX_ENTRIES * 2 + 1) throw ThemeRejectedException("The pack has too many files.")
                val target = File(root, name).canonicalFile
                if (!target.path.startsWith(prefix)) throw ThemeRejectedException("The pack has an unsafe path.")
                if (target.exists()) throw ThemeRejectedException("The pack contains a file twice: $name")
                target.parentFile?.mkdirs()
                var written = 0L
                FileOutputStream(target).use { o ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = zin.read(buf); if (n < 0) break
                        written += n; total += n
                        if (written > limit) throw ThemeRejectedException("A file in the pack is too large: $name")
                        if (total > MAX_TOTAL_BYTES) throw ThemeRejectedException("The pack is too large when unpacked.")
                        if (total > zipSize * MAX_RATIO) throw ThemeRejectedException("The pack is compressed suspiciously well.")
                        o.write(buf, 0, n)
                    }
                }
                zin.closeEntry()
            }
        }
        val mf = File(root, "manifest.json")
        if (!mf.isFile) throw ThemeRejectedException("The pack has no manifest.")
        val manifest = decodeManifest(mf.readText(Charsets.UTF_8))
        for (e in manifest.entries) {
            if (!File(root, e.banner).isFile) throw ThemeRejectedException("The pack is missing ${e.banner}.")
            if (e.logo != null && !File(root, e.logo).isFile) throw ThemeRejectedException("The pack is missing ${e.logo}.")
        }
        return manifest
    }
}
