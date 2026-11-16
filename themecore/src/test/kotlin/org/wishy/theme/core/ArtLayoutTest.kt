package org.wishy.theme.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtLayoutTest {
    private val cw = 1280f
    private val ch = 720f

    private fun aspectOf(r: FRect) = r.width / r.height

    @Test fun fitKeepsAspectAndStaysInside() {
        for (a in listOf(0.2f, 0.5f, 1f, 1.5f, 3f, 8f)) {
            val r = BannerLayout.logoRect(cw, ch, a, FitMode.FIT)
            assertEquals("aspect $a", a, aspectOf(r), a * 0.001f)
            assertTrue(r.left >= 0f && r.top >= 0f && r.right <= cw + 0.01f && r.bottom <= ch + 0.01f)
        }
    }

    @Test fun fitIsCentred() {
        val r = BannerLayout.logoRect(cw, ch, 2f, FitMode.FIT)
        assertEquals(cw / 2, (r.left + r.right) / 2, 0.01f)
        assertEquals(ch / 2, (r.top + r.bottom) / 2, 0.01f)
    }

    @Test fun squareLogoIsNotHuge() {
        val r = BannerLayout.logoRect(cw, ch, 1f, FitMode.FIT)
        assertTrue(r.height <= ch * 0.6f + 0.01f)
    }

    @Test fun wideLogoHasSideMargins() {
        val r = BannerLayout.logoRect(cw, ch, 5f, FitMode.FIT)
        assertTrue(r.left >= cw * 0.1f)
    }

    @Test fun scaleAndMoveAreClampedInsideCanvas() {
        val r = BannerLayout.logoRect(cw, ch, 2f, FitMode.FIT, scale = 100f, dx = 5f, dy = -5f)
        assertTrue(r.left >= 0f && r.top >= 0f && r.right <= cw + 0.01f && r.bottom <= ch + 0.01f)
        assertEquals(2f, aspectOf(r), 0.002f)
    }

    @Test fun smallestScaleIsLimited() {
        val big = BannerLayout.logoRect(cw, ch, 2f, FitMode.FIT, scale = 1f)
        val small = BannerLayout.logoRect(cw, ch, 2f, FitMode.FIT, scale = 0.0001f)
        assertEquals(big.width * BannerLayout.MIN_SCALE, small.width, 0.5f)
    }

    @Test fun coverCoversWholeCanvasForAnyAspect() {
        for (a in listOf(0.5f, 1f, 16f / 9f, 3f)) {
            val r = BannerLayout.logoRect(cw, ch, a, FitMode.COVER, dx = 0.4f, dy = -0.4f)
            assertTrue("aspect $a", r.left <= 0.01f && r.top <= 0.01f && r.right >= cw - 0.01f && r.bottom >= ch - 0.01f)
            assertEquals(a, aspectOf(r), a * 0.001f)
        }
    }

    @Test fun exactSixteenNineCoverMatchesCanvas() {
        val r = BannerLayout.logoRect(cw, ch, 16f / 9f, FitMode.COVER)
        assertEquals(0f, r.left, 0.01f); assertEquals(cw, r.right, 0.01f)
    }

    @Test fun gradientPointsRunAcrossTheCanvas() {
        val p = BannerLayout.gradientPoints(100f, 50f, 0)
        assertEquals(0f, p[0], 0.01f); assertEquals(100f, p[2], 0.01f); assertEquals(25f, p[1], 0.01f)
        val v = BannerLayout.gradientPoints(100f, 50f, 90)
        assertEquals(0f, v[1], 0.01f); assertEquals(50f, v[3], 0.01f)
    }

    @Test fun parseHex() {
        assertEquals(0xFFFF0000.toInt(), ArtColor.parseHex("#f00"))
        assertEquals(0xFF123456.toInt(), ArtColor.parseHex("123456"))
        assertNull(ArtColor.parseHex("#12345"))
        assertNull(ArtColor.parseHex("zzz"))
        assertNull(ArtColor.parseHex(null))
        assertEquals("#123456", ArtColor.toHex(0xFF123456.toInt()))
    }

    @Test fun contrastPicksReadableColour() {
        assertEquals(0xFFFFFFFF.toInt(), ArtColor.contrastOn(0xFF000000.toInt()))
        assertEquals(0xFF111827.toInt(), ArtColor.contrastOn(0xFFFFFFFF.toInt()))
    }

    @Test fun dominantIgnoresTransparentAndPrefersColour() {
        val px = IntArray(100) { 0x00000000 } // transparent
        assertNull(ArtColor.dominant(px))
        val mixed = IntArray(100) { if (it < 80) 0xFFFFFFFF.toInt() else 0xFFFF0000.toInt() }
        val d = ArtColor.dominant(mixed)!!
        assertTrue(((d shr 16) and 0xFF) > ((d shr 8) and 0xFF)) // red beats the white background
    }

    @Test fun autoDesignIsDeterministicAndReadable() {
        val a = AutoDesign.forLogo("com.example.app", true, null, null)
        val b = AutoDesign.forLogo("com.example.app", true, null, null)
        assertEquals(a, b)
        val branded = AutoDesign.forLogo("x.y", true, "#E50914", null)
        assertEquals(BgKind.GRADIENT, branded.background.kind)
        assertNotNull(branded.tint)
        val coloured = AutoDesign.forLogo("x.y", false, null, 0xFF2563EB.toInt())
        assertNull(coloured.tint)
    }

    @Test fun presetsAreValid() {
        for (p in ArtPresets.all) {
            assertNotNull(ArtColor.parseHex(p.background.a)); assertNotNull(ArtColor.parseHex(p.background.b))
            p.tint?.let { assertNotNull(ArtColor.parseHex(it)) }
        }
        for (c in ArtPresets.solidColors) assertNotNull(ArtColor.parseHex(c))
    }
}
