package org.wishy.theme.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ThemeInstallerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun zip(name: String, id: String, version: String, extra: Map<String, ByteArray> = emptyMap()): File =
        TestZips.make(tmp.root, name, mapOf("theme.json" to TestZips.themeJson(id, version)) + extra)

    private fun installer() = ThemeInstaller(File(tmp.root, "data"))

    @Test fun installsAndUpdatesAtomically() {
        val inst = installer()
        val v1 = inst.install(zip("v1.zip", "mini", "1.0.0"), "mini", "1.0.0")
        assertTrue(File(v1, "theme.json").isFile)
        assertTrue(File(v1, "theme.json").readText().contains("1.0.0"))

        val v2 = inst.install(zip("v2.zip", "mini", "1.1.0"), "mini", "1.1.0")
        assertTrue(File(v2, "theme.json").readText().contains("1.1.0"))
        assertFalse(File(inst.themesDir, "mini.old").exists())
    }

    @Test fun failedValidationKeepsTheOldTheme() {
        val inst = installer()
        inst.install(zip("v1.zip", "mini", "1.0.0"), "mini", "1.0.0")
        try {
            inst.install(zip("v2.zip", "mini", "2.0.0"), "mini", "2.0.0") { throw ThemeRejectedException("nope") }
            fail("expected rejection")
        } catch (e: ThemeRejectedException) {
            assertEquals("nope", e.message)
        }
        assertTrue(File(inst.installedDir("mini")!!, "theme.json").readText().contains("1.0.0"))
        assertFalse(File(inst.themesDir, "mini.old").exists())
    }

    @Test fun badZipKeepsTheOldTheme() {
        val inst = installer()
        inst.install(zip("v1.zip", "mini", "1.0.0"), "mini", "1.0.0")
        val bad = zip("bad.zip", "mini", "2.0.0", mapOf("../x.png" to ByteArray(4)))
        try {
            inst.install(bad, "mini", "2.0.0")
            fail("expected rejection")
        } catch (e: ThemeRejectedException) {
            // expected
        }
        assertTrue(File(inst.installedDir("mini")!!, "theme.json").readText().contains("1.0.0"))
    }

    @Test fun idOrVersionMismatchIsRejected() {
        val inst = installer()
        try {
            inst.install(zip("a.zip", "other", "1.0.0"), "mini", "1.0.0")
            fail("expected rejection")
        } catch (e: ThemeRejectedException) { /* expected */ }
        try {
            inst.install(zip("b.zip", "mini", "1.0.1"), "mini", "1.0.0")
            fail("expected rejection")
        } catch (e: ThemeRejectedException) { /* expected */ }
        assertNull(inst.installedDir("mini"))
    }

    @Test fun invalidIdsNeverBecomePaths() {
        val inst = installer()
        listOf("../x", "a/b", "A", "", "x").forEach {
            try {
                inst.installedDir(it)
                fail("expected rejection for '$it'")
            } catch (e: ThemeRejectedException) { /* expected */ }
        }
    }

    @Test fun removeDeletesTheTheme() {
        val inst = installer()
        inst.install(zip("v1.zip", "mini", "1.0.0"), "mini", "1.0.0")
        inst.remove("mini")
        assertNull(inst.installedDir("mini"))
    }

    @Test fun recoverRestoresAnInterruptedSwap() {
        val inst = installer()
        inst.install(zip("v1.zip", "mini", "1.0.0"), "mini", "1.0.0")
        // Simulate a crash after the old copy was moved aside but before the new one arrived.
        File(inst.themesDir, "mini").renameTo(File(inst.themesDir, "mini.old"))
        inst.recoverLeftovers()
        assertTrue(File(inst.installedDir("mini")!!, "theme.json").isFile)
        assertFalse(File(inst.themesDir, "mini.old").exists())
    }
}
