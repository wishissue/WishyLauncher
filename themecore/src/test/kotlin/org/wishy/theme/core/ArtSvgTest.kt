package org.wishy.theme.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ArtSvgTest {
    private fun inspect(s: String) = SvgSanitizer.inspect(s.toByteArray())
    private fun rejected(s: String) {
        try { inspect(s); fail("expected rejection for: $s") } catch (e: ThemeRejectedException) { /* ok */ }
    }

    @Test fun acceptsSimpleMonochromeSvg() {
        val i = inspect("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 12"><path d="M0 0h24v12z"/></svg>""")
        assertEquals(2f, i.aspect, 0.001f)
        assertTrue(i.monochrome)
    }

    @Test fun twoColoursIsNotMonochrome() {
        val i = inspect("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><path fill="#f00" d="M0 0h5v5z"/><path fill="#00f" d="M5 5h5v5z"/></svg>""")
        assertFalse(i.monochrome)
    }

    @Test fun gradientIsNotMonochrome() {
        val i = inspect("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><defs><linearGradient id="g"/></defs><path fill="url(#g)" d="M0 0h5v5z"/></svg>""")
        assertFalse(i.monochrome)
    }

    @Test fun usesWidthAndHeightWithoutViewBox() {
        val i = inspect("""<svg xmlns="http://www.w3.org/2000/svg" width="200" height="50"><rect width="200" height="50"/></svg>""")
        assertEquals(4f, i.aspect, 0.001f)
    }

    @Test fun rejectsScripts() {
        rejected("""<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>""")
    }

    @Test fun rejectsEventHandlers() {
        rejected("""<svg xmlns="http://www.w3.org/2000/svg" onload="x()"><rect width="1" height="1"/></svg>""")
    }

    @Test fun rejectsForeignObject() {
        rejected("""<svg xmlns="http://www.w3.org/2000/svg"><foreignObject><div/></foreignObject></svg>""")
    }

    @Test fun rejectsExternalHref() {
        rejected("""<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"><image xlink:href="https://evil.example/x.png"/></svg>""")
        rejected("""<svg xmlns="http://www.w3.org/2000/svg"><use href="other.svg#a"/></svg>""")
    }

    @Test fun allowsInternalReferences() {
        inspect("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1 1"><defs><path id="a" d="M0 0h1v1z"/></defs><use href="#a"/></svg>""")
    }

    @Test fun rejectsExternalUrlInStyle() {
        rejected("""<svg xmlns="http://www.w3.org/2000/svg"><rect style="fill:url(http://evil.example/a)" width="1" height="1"/></svg>""")
        rejected("""<svg xmlns="http://www.w3.org/2000/svg"><style>@import url(x.css);</style></svg>""")
    }

    @Test fun rejectsEntitiesAndDoctypeSubset() {
        rejected("""<?xml version="1.0"?><!DOCTYPE svg [<!ENTITY a "b">]><svg xmlns="http://www.w3.org/2000/svg">&a;</svg>""")
    }

    @Test fun acceptsPlainDoctype() {
        val doc = """<?xml version="1.0"?><!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1 1"><rect width="1" height="1"/></svg>"""
        val i = inspect(doc)
        assertFalse(i.text.contains("DOCTYPE"))
    }

    @Test fun rejectsAnimationAndJavascriptUrls() {
        rejected("""<svg xmlns="http://www.w3.org/2000/svg"><rect width="1" height="1"><animate attributeName="x" to="2"/></rect></svg>""")
        rejected("""<svg xmlns="http://www.w3.org/2000/svg"><a href="javascript:alert(1)"><rect width="1" height="1"/></a></svg>""")
    }

    @Test fun rejectsMalformedAndNonSvg() {
        rejected("")
        rejected("not xml at all")
        rejected("""<svg xmlns="http://www.w3.org/2000/svg"><rect></svg>""")
        rejected("""<html><body><svg/></body></html>""")
    }

    @Test fun rejectsTooLarge() {
        val big = "<svg xmlns=\"http://www.w3.org/2000/svg\">" + " ".repeat(SvgSanitizer.MAX_BYTES) + "</svg>"
        rejected(big)
    }

    @Test fun rejectsDeepNesting() {
        val s = "<svg xmlns=\"http://www.w3.org/2000/svg\">" + "<g>".repeat(100) + "</g>".repeat(100) + "</svg>"
        rejected(s)
    }

    @Test fun genericIconsAreAllValidMonochromeSvg() {
        for (g in GenericIcons.all) {
            val i = inspect(g.svg)
            assertTrue(g.id, i.monochrome)
            assertEquals(g.id, 1f, i.aspect, 0.001f)
        }
    }
}
