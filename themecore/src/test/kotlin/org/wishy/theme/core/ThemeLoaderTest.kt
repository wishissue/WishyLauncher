package org.wishy.theme.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ThemeLoaderTest {
    @get:Rule val tmp = TemporaryFolder()

    private val ctx = LoadContext(aspect = 16f / 9f, heightPx = 1080, colorScheme = ColorSchemeName.DARK, reducedMotion = false)

    private val scheme = """{"background":"#080B12","surface":"#111725","onSurface":"#FFFFFF","primary":"#66A3FF","onPrimary":"#001B3E","custom":{"sky":"#00AAFF"}}"""

    private fun theme(
        screens: String = """{"home":{"root":{"type":"apps","arrangement":"rail","card":"c","itemWidth":300}}}""",
        extra: String = "",
        cardStyles: String = """{"c":{"focused":{"scale":1.1,"indicator":{"kind":"ring","color":"@primary/60"}}}}""",
    ): File {
        val dir = tmp.newFolder()
        File(dir, "theme.json").writeText(
            """{"schemaVersion":1,"id":"t1","name":"T","version":"1.0.0","author":"a","license":"CC0-1.0",
               "colorSchemes":{"dark":$scheme},"cardStyles":$cardStyles,"screens":$screens $extra}"""
        )
        return dir
    }

    private fun assertRejected(dir: File, contains: String? = null, context: LoadContext = ctx) {
        try {
            ThemeLoader.load(dir, context)
            fail("expected the theme to be rejected")
        } catch (e: ThemeRejectedException) {
            if (contains != null) assertTrue("message was: ${e.message}", e.message!!.contains(contains))
        }
    }

    @Test fun loadsAMinimalTheme() {
        val loaded = ThemeLoader.load(theme(), ctx)
        assertEquals("t1", loaded.id)
        assertEquals(300f, loaded.doc.appsNode.itemWidth!!.value)
        assertEquals(false, loaded.doc.hasSections)
    }

    @Test fun acceptsWheelBentoAndTextOnlyCards() {
        ThemeLoader.load(
            theme(screens = """{"home":{"root":{"type":"apps","arrangement":"wheel","card":"c","itemWidth":220,
                "ringColor":"@primary/20","ringEdgeColor":"@primary/40","ringWidth":300}}}"""), ctx,
        )
        ThemeLoader.load(
            theme(screens = """{"home":{"root":{"type":"apps","arrangement":"bento","card":"c","bigCard":"c","columns":5}}}"""), ctx,
        )
        ThemeLoader.load(
            theme(
                cardStyles = """{"c":{"artwork":{"source":"none"},"label":{"position":"overlayStart"}}}""",
                screens = """{"home":{"root":{"type":"apps","arrangement":"list","card":"c","gap":8}}}""",
            ),
            ctx,
        )
    }

    @Test fun rejectsBadBentoAndRingSettings() {
        fun apps(extra: String, arrangement: String = "bento") =
            theme(screens = """{"home":{"root":{"type":"apps","arrangement":"$arrangement","card":"c",$extra "itemWidth":200}}}""")
        assertRejected(apps(""" "columns":2,"""), "bento must be 3 to 8")
        assertRejected(apps(""" "columns":9,"""), "bento must be 3 to 8")
        assertRejected(apps(""" "bigCard":"nope","""), "nope")
        assertRejected(apps(""" "bigCard":"c",""", arrangement = "rail"), "only applies to the bento")
        assertRejected(apps(""" "ringWidth":900,""", arrangement = "wheel"), "ringWidth")
        assertRejected(apps(""" "ringWidth":100,""", arrangement = "rail"), "only apply to the wheel")
        assertRejected(apps(""" "ringColor":"@custom.missing",""", arrangement = "wheel"), "not defined")
    }

    @Test fun rejectsUnknownTopLevelKey() = assertRejected(theme(extra = ""","script":"alert(1)""""))

    @Test fun rejectsUnknownNodeType() =
        assertRejected(theme(screens = """{"home":{"root":{"type":"script","code":"x"}}}"""))

    @Test fun rejectsUnknownNodeKey() = assertRejected(
        theme(screens = """{"home":{"root":{"type":"apps","arrangement":"rail","card":"c","itemWidth":300,"onClick":"x"}}}""")
    )

    @Test fun rejectsMissingAppsNode() =
        assertRejected(theme(screens = """{"home":{"root":{"type":"clock"}}}"""), "exactly one apps node")

    @Test fun rejectsTwoAppsNodes() = assertRejected(
        theme(screens = """{"home":{"root":{"type":"stack","children":[
            {"type":"apps","arrangement":"rail","card":"c","itemWidth":300},
            {"type":"apps","arrangement":"rail","card":"c","itemWidth":300}]}}}"""),
        "exactly one apps node",
    )

    @Test fun rejectsUnknownCardReference() = assertRejected(
        theme(screens = """{"home":{"root":{"type":"apps","arrangement":"rail","card":"nope","itemWidth":300}}}"""), "nope"
    )

    @Test fun rejectsBadColours() {
        assertRejected(theme(cardStyles = """{"c":{"fill":"red"}}"""), "colour")
        assertRejected(theme(cardStyles = """{"c":{"fill":"@custom.missing"}}"""), "not defined")
        assertRejected(theme(cardStyles = """{"c":{"fill":"@primary/150"}}"""), "above 100")
    }

    @Test fun acceptsCustomColourTokens() {
        ThemeLoader.load(theme(cardStyles = """{"c":{"fill":"@custom.sky/50"}}"""), ctx)
    }

    @Test fun rejectsOutOfRangeValues() {
        assertRejected(theme(cardStyles = """{"c":{"focused":{"scale":9}}}"""), "scale")
        assertRejected(theme(cardStyles = """{"c":{"aspect":"0:9"}}"""), "aspect")
    }

    @Test fun rejectsMissingAndTraversalFiles() {
        assertRejected(theme(extra = ""","background":{"image":"assets/missing.png"}"""), "does not exist")
        assertRejected(theme(extra = ""","background":{"image":"../x.png"}"""), "safe path")
        assertRejected(theme(extra = ""","background":{"image":"a.svg"}"""), "image")
    }

    @Test fun acceptsExistingImage() {
        val dir = theme(extra = ""","background":{"image":"assets/bg.png"}""")
        File(dir, "assets").mkdirs()
        File(dir, "assets/bg.png").writeBytes(ByteArray(10))
        ThemeLoader.load(dir, ctx)
    }

    @Test fun rejectsDeepTrees() {
        var node = """{"type":"apps","arrangement":"rail","card":"c","itemWidth":300}"""
        repeat(ThemeLimits.MAX_DEPTH + 2) { node = """{"type":"stack","children":[$node]}""" }
        assertRejected(theme(screens = """{"home":{"root":$node}}"""), "deeper")
    }

    @Test fun overridesApplyOnlyWhenTheyMatch() {
        val extra = ""","overrides":[
            {"when":{"reducedMotion":true},"merge":{"motion":{"focusMillis":0}}},
            {"when":{"maxHeightPx":720},"merge":{"motion":{"scrollMillis":10}}}]"""
        val dir = theme(extra = extra)
        assertEquals(180, ThemeLoader.load(dir, ctx).doc.motion.focusMillis)
        assertEquals(0, ThemeLoader.load(dir, ctx.copy(reducedMotion = true)).doc.motion.focusMillis)
        assertEquals(10, ThemeLoader.load(dir, ctx.copy(heightPx = 720)).doc.motion.scrollMillis)
    }

    @Test fun overridesCannotChangeProtectedKeysOrUseUnknownConditions() {
        assertRejected(theme(extra = ""","overrides":[{"when":{"reducedMotion":false},"merge":{"id":"evil"}}]"""))
        assertRejected(theme(extra = ""","overrides":[{"when":{"battery":1},"merge":{}}]"""))
    }

    @Test fun badJsonAndOversizedFilesAreRejected() {
        val dir = tmp.newFolder()
        File(dir, "theme.json").writeText("{not json")
        assertRejected(dir, "JSON")
        val big = tmp.newFolder()
        File(big, "theme.json").writeBytes(ByteArray((ThemeLimits.MAX_THEME_JSON_BYTES + 1).toInt()) { ' '.code.toByte() })
        assertRejected(big, "too large")
        assertRejected(tmp.newFolder(), "missing")
    }

    @Test fun summaryIsReadable() {
        val s = ThemeLoader.readSummary(theme())!!
        assertEquals("t1", s.id)
        assertEquals("1.0.0", s.version)
    }

    @Test fun everyBundledThemeLoadsInBothSchemesAndSizes() {
        val themesRoot = File("../themes")
        val dirs = themesRoot.listFiles { f -> File(f, "theme.json").isFile }.orEmpty()
        assertTrue("no bundled themes found at ${themesRoot.absolutePath}", dirs.isNotEmpty())
        for (dir in dirs) {
            for (scheme in ColorSchemeName.values()) for (h in listOf(720, 1080, 2160)) for (rm in listOf(false, true)) {
                val c = LoadContext(16f / 9f, h, scheme, rm)
                try {
                    ThemeLoader.load(dir, c)
                } catch (e: ThemeRejectedException) {
                    fail("${dir.name} failed to load ($scheme, ${h}p, reducedMotion=$rm): ${e.message}")
                }
            }
        }
    }
}
