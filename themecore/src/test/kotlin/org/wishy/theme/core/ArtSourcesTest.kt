package org.wishy.theme.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtSourcesTest {
    private val simpleIcons = """[
        {"title":"Netflix","hex":"E50914"},
        {"title":"YouTube","hex":"FF0000"},
        {"title":"Disney+","hex":"113CCF","slug":"disneyplus"},
        {"title":"Spotify","hex":"1ED760"},
        {"title":"Plex","hex":"EBAF00"},
        {"title":"Bad Slug","hex":"000000","slug":"../x"},
        {"hex":"000000"}
    ]"""

    @Test fun parsesSimpleIcons() {
        val l = ArtCatalogs.parseSimpleIcons(simpleIcons)
        assertEquals(5, l.size)
        val n = l.first { it.key == "netflix" }
        assertEquals("https://raw.githubusercontent.com/simple-icons/simple-icons/develop/icons/netflix.svg", n.url)
        assertEquals("#E50914", n.brandHex)
        assertTrue(n.monochrome)
        assertTrue(l.first { it.title == "Disney+" }.url.endsWith("/disneyplus.svg"))
    }

    @Test fun simpleIconsSlugRules() {
        assertEquals("disneyplus", ArtCatalogs.simpleIconsSlug("Disney+"))
        assertEquals("nodedotjs", ArtCatalogs.simpleIconsSlug("Node.js"))
        assertEquals("handm", ArtCatalogs.simpleIconsSlug("H&M"))
        assertEquals("disneyplus", ArtCatalogs.key("Disney+"))
    }

    @Test fun simpleIconsAcceptsObjectWrapperAndGarbage() {
        assertEquals(1, ArtCatalogs.parseSimpleIcons("""{"icons":[{"title":"Plex","hex":"EBAF00"}]}""").size)
        assertTrue(ArtCatalogs.parseSimpleIcons("garbage").isEmpty())
        assertTrue(ArtCatalogs.parseSimpleIcons("42").isEmpty())
    }

    @Test fun parsesSvglAndRejectsOtherHosts() {
        val json = """[
          {"title":"Netflix","category":"Entertainment","route":"https://svgl.app/library/netflix.svg","url":"https://netflix.com"},
          {"title":"Plex","route":{"light":"https://svgl.app/library/plex.svg","dark":"https://svgl.app/library/plex-dark.svg"},"url":"https://plex.tv"},
          {"title":"Evil","route":"https://evil.example/x.svg"},
          {"title":"Png","route":"https://svgl.app/library/x.png"}
        ]"""
        val l = ArtCatalogs.parseSvgl(json)
        assertEquals(2, l.size)
        assertTrue(l.first { it.key == "plex" }.url.endsWith("plex-dark.svg"))
    }

    @Test fun parsesArcticonsMapping() {
        val xml = """<resources>
          <item component="ComponentInfo{com.netflix.ninja/com.netflix.ninja.MainActivity}" drawable="netflix" />
          <item component="ComponentInfo{com.netflix.mediaclient/com.netflix.Main}" drawable="netflix" />
          <item component="ComponentInfo{org.xbmc.kodi/org.xbmc.kodi.Splash}" drawable="kodi" />
        </resources>"""
        val l = ArtCatalogs.parseArcticons(xml)
        assertEquals(2, l.size)
        assertEquals(setOf("com.netflix.ninja", "com.netflix.mediaclient"), l.first { it.key == "netflix" }.packages)
    }

    @Test fun parsesIconifySearchOnlyValidIds() {
        val l = ArtCatalogs.parseIconifySearch("""{"icons":["logos:netflix","bad id","logos:disney-plus","../x:y"]}""")
        assertEquals(2, l.size)
        assertEquals("https://api.iconify.design/logos/netflix.svg", l[0].url)
        assertTrue(l.all { ArtProviders.isAllowedUrl(it.url) })
    }

    @Test fun onlyAllowedHostsAreContacted() {
        assertTrue(ArtProviders.isAllowedUrl("https://raw.githubusercontent.com/a/b.svg"))
        assertFalse(ArtProviders.isAllowedUrl("http://raw.githubusercontent.com/a/b.svg"))
        assertFalse(ArtProviders.isAllowedUrl("https://evil.example/a.svg"))
        assertFalse(ArtProviders.isAllowedUrl("https://raw.githubusercontent.com.evil.example/a.svg"))
        assertFalse(ArtProviders.isAllowedUrl("https://user@svgl.app/a.svg"))
    }

    private val catalog get() = ArtCatalogs.parseSimpleIcons(simpleIcons) + ArtCatalogs.parseArcticons(
        """<item component="ComponentInfo{org.xbmc.kodi/x.Y}" drawable="kodi" />"""
    )

    @Test fun matchesByBrandTableLabelAndPackage() {
        val apps = listOf(
            MatchApp("com.netflix.ninja", "Netflix"),
            MatchApp("com.google.android.youtube.tv", "YouTube"),
            MatchApp("com.disney.disneyplus", "Disney+"),
            MatchApp("com.spotify.tv.android", "Spotify TV"),
            MatchApp("org.xbmc.kodi", "Kodi"),
            MatchApp("com.unknown.thing", "Totally Unknown"),
        )
        val m = ArtMatcher.match(apps, catalog)
        assertEquals("netflix", m["com.netflix.ninja"]!!.first().logo.key)
        assertEquals("youtube", m["com.google.android.youtube.tv"]!!.first().logo.key)
        assertEquals("disneyplus", m["com.disney.disneyplus"]!!.first().logo.key)
        assertEquals("spotify", m["com.spotify.tv.android"]!!.first().logo.key)
        assertEquals("kodi", m["org.xbmc.kodi"]!!.first().logo.key)
        assertNull(m["com.unknown.thing"])
    }

    @Test fun doesNotMatchUnrelatedShortNames() {
        val l = listOf(CatalogLogo("p", "Pl", "pl", "https://svgl.app/a.svg", true, null, "x", "y"))
        val m = ArtMatcher.match(listOf(MatchApp("com.example.plex", "Plex Client")), l)
        assertTrue(m.isEmpty())
    }

    @Test fun emptyCatalogGivesNothing() {
        assertTrue(ArtMatcher.match(listOf(MatchApp("a.b", "A")), emptyList()).isEmpty())
    }

    @Test fun genericIconByCategory() {
        assertEquals("play", GenericIcons.forCategory("streaming").id)
        assertEquals("apps", GenericIcons.forCategory("whatever").id)
        assertNotNull(GenericIcons.byId("star"))
    }
}
