package org.wishy.theme.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SurpriseDesignTest {
    private fun contrast(a: Int, b: Int): Float {
        val la = ArtColor.luminance(a); val lb = ArtColor.luminance(b)
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    @Test fun monoLogosAlwaysGetAReadableTint() {
        val rnd = Random(1)
        repeat(300) {
            val s = SurpriseDesign.generate(rnd, mono = true, brandHex = if (it % 2 == 0) "#E50914" else null, dominantArgb = null)
            val tint = ArtColor.parseHex(s.tint)
            assertNotNull(tint)
            val a = ArtColor.parseHex(s.background.a)!!
            assertTrue(contrast(tint!!, a) >= 2.0f)
        }
    }

    @Test fun colouredLogosReadAgainstTheirBackground() {
        val rnd = Random(2)
        val logo = 0xFF1E40AF.toInt()
        repeat(300) {
            val s = SurpriseDesign.generate(rnd, mono = false, brandHex = null, dominantArgb = logo)
            assertNull(s.tint)
            assertTrue(contrast(logo, ArtColor.parseHex(s.background.a)!!) >= SurpriseDesign.MIN_CONTRAST - 0.01f)
            if (s.background.kind == BgKind.GRADIENT) assertTrue(contrast(logo, ArtColor.parseHex(s.background.b)!!) >= SurpriseDesign.MIN_CONTRAST - 0.01f)
        }
    }

    @Test fun layoutKeepsTheLogoOnTheCanvas() {
        val rnd = Random(3)
        repeat(300) {
            val aspect = 0.5f + rnd.nextFloat() * 4f
            val s = SurpriseDesign.generate(rnd, true, null, null, aspect)
            assertTrue(s.scale >= SurpriseDesign.MIN_SCALE_PICK && s.scale <= 1f)
            val r = BannerLayout.logoRect(1280f, 720f, aspect, s.mode, s.scale, s.dx, s.dy)
            assertTrue(r.left >= -0.5f && r.top >= -0.5f && r.right <= 1280.5f && r.bottom <= 720.5f)
        }
    }

    @Test fun variationsDifferAndCountMatches() {
        val v = SurpriseDesign.variations(6, Random(4), true, null, null)
        assertEquals(6, v.size)
        assertTrue(v.map { it.background to it.decor }.toSet().size > 1)
    }

    @Test fun differentSeedsGiveDifferentDesigns() {
        val a = SurpriseDesign.generate(Random(10), true, null, null)
        val b = SurpriseDesign.generate(Random(11), true, null, null)
        assertTrue(a != b)
    }
}
