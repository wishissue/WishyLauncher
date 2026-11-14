package org.wishy.theme.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SafeUnzipTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun dest(): File = tmp.newFolder()

    private fun assertRejected(zip: File) {
        val d = dest()
        try {
            SafeUnzip.extract(zip, d)
            fail("expected the theme to be rejected")
        } catch (e: ThemeRejectedException) {
            // expected
        }
    }

    @Test fun extractsAValidTheme() {
        val zip = TestZips.make(tmp.root, "ok.zip", mapOf(
            "theme.json" to TestZips.themeJson("mini", "1.0.0"),
            "assets/wallpaper.png" to ByteArray(1000) { it.toByte() },
        ))
        val d = dest()
        SafeUnzip.extract(zip, d)
        assertTrue(File(d, "theme.json").isFile)
        assertEquals(1000L, File(d, "assets/wallpaper.png").length())
    }

    @Test fun rejectsTraversalEntry() {
        assertRejected(TestZips.make(tmp.root, "t.zip", mapOf(
            "theme.json" to TestZips.themeJson("mini", "1.0.0"),
            "../evil.png" to ByteArray(10),
        )))
    }

    @Test fun rejectsAbsoluteEntry() {
        assertRejected(TestZips.make(tmp.root, "a.zip", mapOf(
            "theme.json" to TestZips.themeJson("mini", "1.0.0"),
            "/evil.png" to ByteArray(10),
        )))
    }

    @Test fun rejectsDisallowedTypes() {
        assertRejected(TestZips.make(tmp.root, "s.zip", mapOf(
            "theme.json" to TestZips.themeJson("mini", "1.0.0"),
            "run.js" to "alert(1)".toByteArray(),
        )))
        assertRejected(TestZips.make(tmp.root, "v.zip", mapOf(
            "theme.json" to TestZips.themeJson("mini", "1.0.0"),
            "icon.svg" to "<svg/>".toByteArray(),
        )))
    }

    @Test fun rejectsMissingThemeJson() {
        assertRejected(TestZips.make(tmp.root, "n.zip", mapOf("a.png" to ByteArray(10))))
    }

    @Test fun rejectsOversizedFile() {
        // Random-looking data so the compression ratio check is not what triggers.
        val rnd = java.util.Random(1)
        val big = ByteArray((ThemeLimits.MAX_FONT_BYTES + 1).toInt()).also { rnd.nextBytes(it) }
        assertRejected(TestZips.make(tmp.root, "f.zip", mapOf(
            "theme.json" to TestZips.themeJson("mini", "1.0.0"),
            "font.ttf" to big,
        )))
    }

    @Test fun rejectsZipBombByRatio() {
        // 4 MB of zeros compresses to a few KB: well under the per-file limit, far over the ratio limit.
        assertRejected(TestZips.make(tmp.root, "b.zip", mapOf(
            "theme.json" to TestZips.themeJson("mini", "1.0.0"),
            "assets/zeros.png" to ByteArray(4 * 1024 * 1024),
        )))
    }

    @Test fun rejectsTooManyFiles() {
        val entries = LinkedHashMap<String, ByteArray>()
        entries["theme.json"] = TestZips.themeJson("mini", "1.0.0")
        for (i in 0..ThemeLimits.MAX_FILES) entries["a/f$i.png"] = ByteArray(8) { (it + i).toByte() }
        assertRejected(TestZips.make(tmp.root, "m.zip", entries))
    }

    @Test fun rejectsDuplicateNames() {
        val f = File(tmp.root, "d.zip")
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("theme.json"))
            z.write(TestZips.themeJson("mini", "1.0.0"))
            z.closeEntry()

            z.putNextEntry(java.util.zip.ZipEntry("theme.jso2"))
            z.write(TestZips.themeJson("mini", "1.0.0"))
            z.closeEntry()
        }
        val bytes = baos.toByteArray()
        val targetBytes = "theme.jso2".toByteArray(Charsets.US_ASCII)
        val replacementBytes = "theme.json".toByteArray(Charsets.US_ASCII)
        for (i in 0..bytes.size - targetBytes.size) {
            if (bytes.copyOfRange(i, i + targetBytes.size).contentEquals(targetBytes)) {
                System.arraycopy(replacementBytes, 0, bytes, i, replacementBytes.size)
            }
        }
        f.writeBytes(bytes)
        assertRejected(f)
    }
}
