package org.wishy.theme.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The settings screens hide switches a theme cannot use. These checks pin down what each bundled theme reports. */
class ThemeCapsTest {
    private fun load(id: String): ThemeDoc =
        ThemeLoader.load(File("../themes/$id"), LoadContext(16f / 9f, 1080, ColorSchemeName.DARK, false)).doc

    @Test fun wishyDrawsStatusIconsAndOptionalSections() {
        val d = load("wishy")
        assertTrue(d.hasClock && d.hasDate && d.hasSections && d.hasStatusIcons && d.hasCorners)
        assertTrue(d.ownsStatusBar)
        assertFalse(d.sectionsAlways)
    }

    @Test fun gridThemeKeepsSectionsOn() {
        val d = load("wishy-grid")
        assertTrue(d.hasSections && d.sectionsAlways && d.hasStatusIcons)
        assertEquals(AppsArrangement.GRID, d.appsNode.arrangement)
    }

    @Test fun textBindingAndStatusIconsParse() {
        val d = load("wishy")
        assertTrue(d.anyNode { it is TextNode && it.bind == TextBind.FOCUSED_POSITION })
    }
}
