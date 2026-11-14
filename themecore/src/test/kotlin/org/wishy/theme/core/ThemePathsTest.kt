package org.wishy.theme.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePathsTest {
    @Test fun acceptsPlainPaths() {
        assertTrue(ThemePaths.isAllowedFile("theme.json"))
        assertTrue(ThemePaths.isAllowedFile("CREDITS.md"))
        assertTrue(ThemePaths.isAllowedFile("assets/wallpaper.jpg"))
        assertTrue(ThemePaths.isAllowedFile("assets/fonts/pixel.TTF"))
    }

    @Test fun rejectsTraversalAndOddPaths() {
        listOf(
            "../a.png", "a/../b.png", "/etc/a.png", "a\\b.png", "a//b.png", ".hidden.png",
            "a/.hidden/b.png", "a b.png", "a..b.png", "", "assets/", "a\u0000.png",
        ).forEach { assertFalse("should reject: $it", ThemePaths.isAllowedFile(it)) }
    }

    @Test fun rejectsOtherFileTypes() {
        listOf("a.svg", "a.js", "a.kt", "a.so", "a.dex", "a.json", "a.html", "a", "run.sh", "a.png.exe")
            .forEach { assertFalse("should reject: $it", ThemePaths.isAllowedFile(it)) }
    }
}
