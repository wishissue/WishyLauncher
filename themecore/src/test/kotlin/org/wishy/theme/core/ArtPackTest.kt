package org.wishy.theme.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArtPackTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun entry(pkg: String, logo: String? = null) = PackEntry(
        `package` = pkg, banner = ArtPack.bannerPath(pkg, "webp"), width = 1280, height = 720,
        logo = logo, spec = BannerSpec(), origin = ArtOrigin.COMMUNITY, source = "simple-icons", author = "A", license = "CC0-1.0",
    )

    private fun zipOf(vararg files: Pair<String, ByteArray>): File {
        val f = tmp.newFile()
        ZipOutputStream(f.outputStream()).use { z -> for ((n, b) in files) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return f
    }

    private fun manifestJson(vararg e: PackEntry) = ArtPack.encodeManifest(PackManifest(createdAt = "2026-01-01", entries = e.toList()))

    private fun rejects(f: File) {
        try { ArtPack.extract(f, tmp.newFolder()); fail("expected rejection") } catch (e: ThemeRejectedException) { /* ok */ }
    }

    @Test fun roundTrip() {
        val out = ByteArrayOutputStream()
        val e1 = entry("com.a.b", ArtPack.logoPath("com.a.b", "svg"))
        ArtPack.write(out, listOf(PackFile(e1, byteArrayOf(1, 2, 3), "<svg/>".toByteArray()), PackFile(entry("com.c.d"), byteArrayOf(9), null)), "2026-01-01")
        val zip = tmp.newFile(); zip.writeBytes(out.toByteArray())
        val dest = tmp.newFolder()
        val m = ArtPack.extract(zip, dest)
        assertEquals(2, m.entries.size)
        assertEquals("simple-icons", m.entries[0].source)
        assertEquals("CC0-1.0", m.entries[0].license)
        assertTrue(File(dest, "banners/com.a.b.webp").isFile)
        assertTrue(File(dest, "logos/com.a.b.svg").isFile)
    }

    @Test fun rejectsPathTraversalAndStrangeNames() {
        val m = manifestJson(entry("com.a.b")).toByteArray()
        rejects(zipOf("manifest.json" to m, "banners/com.a.b.webp" to byteArrayOf(1), "../evil.txt" to byteArrayOf(1)))
        rejects(zipOf("manifest.json" to m, "banners/com.a.b.webp" to byteArrayOf(1), "banners/../../x.webp" to byteArrayOf(1)))
        rejects(zipOf("manifest.json" to m, "banners/com.a.b.webp" to byteArrayOf(1), "other/readme.txt" to byteArrayOf(1)))
        rejects(zipOf("manifest.json" to m, "banners/com.a.b.webp" to byteArrayOf(1), "/abs.webp" to byteArrayOf(1)))
    }

    @Test fun rejectsMissingFileAndMissingManifest() {
        rejects(zipOf("manifest.json" to manifestJson(entry("com.a.b")).toByteArray()))
        rejects(zipOf("banners/com.a.b.webp" to byteArrayOf(1)))
    }

    @Test fun rejectsBadManifests() {
        fun bad(json: String) = rejects(zipOf("manifest.json" to json.toByteArray()))
        bad("not json")
        bad("""{"format":"other","version":1,"entries":[]}""")
        bad("""{"format":"wishy-artwork-pack","version":99,"entries":[]}""")
        // banner name that does not match the package
        bad(ArtPack.encodeManifest(PackManifest(entries = listOf(entry("com.a.b").copy(banner = "banners/com.x.y.webp")))))
        // invalid package name
        bad(ArtPack.encodeManifest(PackManifest(entries = listOf(entry("../x").copy(banner = "banners/x.webp")))))
    }

    @Test fun rejectsDuplicatePackages() {
        val m = manifestJson(entry("com.a.b"), entry("com.a.b")).toByteArray()
        rejects(zipOf("manifest.json" to m, "banners/com.a.b.webp" to byteArrayOf(1)))
    }

    @Test fun rejectsOversizedBanner() {
        val m = manifestJson(entry("com.a.b")).toByteArray()
        rejects(zipOf("manifest.json" to m, "banners/com.a.b.webp" to ByteArray((ArtPack.MAX_BANNER_BYTES + 1).toInt())))
    }

    @Test fun rejectsZipBomb() {
        val m = manifestJson(entry("com.a.b")).toByteArray()
        // 5 MB of zeros compresses to a few KB: ratio far above the limit.
        rejects(zipOf("manifest.json" to m, "banners/com.a.b.webp" to ByteArray(4 * 1024 * 1024)))
    }
}
