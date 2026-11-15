package org.wishy.theme.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RepoModelsTest {
    private val base = "https://raw.example.org/Repo/main/index.json"
    private val sha = "a".repeat(64)

    private fun rejects(f: () -> Unit) {
        try { f(); fail("expected ThemeRejectedException") } catch (_: ThemeRejectedException) {}
    }

    @Test fun relativeLocationsResolveNextToTheIndex() {
        assertEquals("https://raw.example.org/Repo/main/themes/x/1.0.0.zip", RepoUrls.resolve(base, "themes/x/1.0.0.zip"))
    }

    @Test fun otherHostsAndPlainHttpAreRefused() {
        rejects { RepoUrls.resolve(base, "https://evil.example.net/x.zip") }
        rejects { RepoUrls.resolve(base, "http://raw.example.org/Repo/main/x.zip") }
        rejects { RepoUrls.resolve("http://raw.example.org/index.json", "x.zip") }
    }

    @Test fun traversalAndOddCharactersAreRefused() {
        rejects { RepoUrls.resolve(base, "../secret.zip") }
        rejects { RepoUrls.resolve(base, "a\\b.zip") }
        rejects { RepoUrls.resolve(base, "a\nb.zip") }
    }

    @Test fun absoluteUrlOnTheSameHostIsAccepted() {
        assertEquals("https://raw.example.org/other/x.zip", RepoUrls.resolve(base, "https://raw.example.org/other/x.zip"))
    }

    private fun theme(id: String = "demo", version: String = "1.0.0", url: String = "themes/demo/1.0.0.zip", hash: String = sha) =
        """{"id":"$id","name":"Demo","version":"$version","author":"A","license":"CC0-1.0","schemaVersion":1,
            "downloadUrl":"$url","sha256":"$hash","sizeBytes":1000}"""

    @Test fun goodIndexHasNoProblems() {
        val idx = RepoJson.parseIndex("""{"schemaVersion":1,"name":"T","themes":[${theme()}]}""")
        assertTrue(RepoChecker.problems(idx, base).isEmpty())
    }

    @Test fun badEntriesAreReported() {
        val idx = RepoJson.parseIndex("""{"schemaVersion":1,"name":"T","themes":[${theme(id = "Bad_ID")},${theme(hash = "xyz")},${theme(url = "https://evil.test/a.zip")}]}""")
        val p = RepoChecker.problems(idx, base)
        assertTrue(p.any { it.contains("bad id") })
        assertTrue(p.any { it.contains("sha256") })
        assertTrue(p.any { it.contains("another host") })
    }

    @Test fun unknownKeysFailParsing() {
        rejects { RepoJson.parseIndex("""{"schemaVersion":1,"name":"T","themes":[],"surprise":1}""") }
        rejects { RepoJson.parseIndex("not json") }
    }

    @Test fun artworkChecksAspectSizeAndPackage() {
        fun art(pkg: String, w: Int, h: Int) =
            """{"schemaVersion":1,"apps":{"$pkg":{"url":"a/x.png","sha256":"$sha","sizeBytes":5000,"width":$w,"height":$h,"author":"A","license":"CC0-1.0"}}}"""
        assertTrue(RepoChecker.problems(RepoJson.parseArt(art("com.example.app", 640, 360)), base).isEmpty())
        assertTrue(RepoChecker.problems(RepoJson.parseArt(art("com.example.app", 640, 480)), base).any { it.contains("16:9") })
        assertTrue(RepoChecker.problems(RepoJson.parseArt(art("com.example.app", 100, 56)), base).any { it.contains("out of range") })
        assertTrue(RepoChecker.problems(RepoJson.parseArt(art("not a package", 640, 360)), base).any { it.contains("package") })
    }

    private fun variant(id: String = "dark", w: Int = 640, h: Int = 360, url: String = "x/dark.png") =
        """{"id":"$id","name":"Dark","url":"$url","sha256":"$sha","sizeBytes":5000,"width":$w,"height":$h,"author":"A","license":"CC0-1.0"}"""

    private fun variants(pkg: String, vararg v: String) = """{"schemaVersion":1,"apps":{"$pkg":[${v.joinToString(",")}]}}"""

    @Test fun artworkVariantsAcceptSeveralVersionsOfOneApp() {
        val idx = RepoJson.parseVariants(variants("com.example.app", variant("dark"), variant("light"), variant("key_art")))
        assertEquals(3, idx.apps.getValue("com.example.app").size)
        assertTrue(RepoChecker.problems(idx, base).isEmpty())
    }

    @Test fun artworkVariantsReportBadVersions() {
        fun problems(vararg v: String, pkg: String = "com.example.app") = RepoChecker.problems(RepoJson.parseVariants(variants(pkg, *v)), base)
        assertTrue(problems(variant("dark"), variant("dark")).any { it.contains("duplicate") })
        assertTrue(problems(variant("Bad Id")).any { it.contains("bad version id") })
        assertTrue(problems(variant(w = 640, h = 480)).any { it.contains("16:9") })
        assertTrue(problems(variant(w = 100, h = 56)).any { it.contains("out of range") })
        assertTrue(problems(variant(url = "../x.png")).any { it.contains("Unsafe") })
        assertTrue(problems(variant(url = "https://evil.example.net/x.png")).any { it.contains("another host") })
        assertTrue(problems(variant(), pkg = "not a package").any { it.contains("package") })
        assertTrue(problems(*Array(RepoLimits.MAX_ART_VARIANTS + 1) { variant("v$it") }).any { it.contains("more than") })
    }

    @Test fun artworkVariantsUnknownKeysFailParsing() {
        rejects { RepoJson.parseVariants("""{"schemaVersion":1,"apps":{},"extra":1}""") }
    }

    @Test fun versionComparison() {
        assertTrue(RepoChecker.isNewer("1.0.1", "1.0.0"))
        assertTrue(RepoChecker.isNewer("2.0.0", "1.9.9"))
        assertFalse(RepoChecker.isNewer("1.0.0", "1.0.0"))
        assertFalse(RepoChecker.isNewer("1.0.0", "1.2.0"))
        assertFalse(RepoChecker.isNewer("x", "1.0.0"))
    }
}
