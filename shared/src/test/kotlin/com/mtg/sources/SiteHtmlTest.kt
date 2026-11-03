package com.mtg.sources

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class SiteHtmlTest {
    private fun fixture(name: String, url: String) = Jsoup.parse(javaClass.getResource("/$name")!!.readText(), url)

    @Test fun `movie and cartoon catalogs retain real title links and artwork`() {
        for ((file, base, site) in listOf(
            Triple("myseed-catalog.html", "https://myseed.cam/films", Site.MYSEED),
            Triple("spacepower-catalog.html", "https://spacepowerfan.com/anime/", Site.SPACEPOWER),
            Triple("arabiccartoon-catalog.html", "https://arabiccartoon.org/home/", Site.ARABICCARTOON),
            Triple("cimaclub-catalog.html", "https://w.cimacub.com/", Site.CIMACLUB),
        )) {
            val cards = SiteHtml.cards(fixture(file, base), site)
            assertEquals(file, 3, cards.size)
            assertTrue(file, cards.all { it.title.isNotBlank() && it.url.startsWith("https://") && it.poster?.startsWith("https://") == true })
            assertEquals(cards.size, cards.distinctBy { it.url }.size)
        }
    }

    @Test fun `series episode lists do not absorb navigation or related shows`() {
        val myseed = SiteHtml.episodes(fixture("myseed-episodes.html", "https://myseed.cam/series/test"), Site.MYSEED)
        assertEquals(listOf(1, 2, 3, 4), myseed.map { it.number })
        val space = SiteHtml.episodes(fixture("spacepower-episodes.html", "https://spacepowerfan.com/anime/test"), Site.SPACEPOWER)
        assertEquals(1, space.size)
        assertEquals(1, space.first().number)
        val arabic = SiteHtml.episodes(fixture("arabiccartoon-episodes.html", "https://arabiccartoon.org/tvshows/test"), Site.ARABICCARTOON)
        assertEquals(listOf(1, 2, 3), arabic.map { it.number })
    }

    @Test fun `published server links are parsed without returning poster images`() {
        val space = SiteHtml.playerTargets(fixture("spacepower-player.html", "https://spacepowerfan.com/watch/test"))
        assertEquals(6, space.size)
        assertTrue(space.any { it.contains("4shared.com/web/embed/file/") })
        val myseed = SiteHtml.playerTargets(fixture("myseed-player.html", "https://myseed.cam/watch/test/servers"))
        assertEquals(3, myseed.size)
        assertTrue(myseed.all { it.contains("/e/") })
    }

    @Test fun `subscription restrictions never produce media links`() {
        val doc = fixture("arabiccartoon-restricted.html", "https://arabiccartoon.org/episodes/test")
        assertTrue(SiteHtml.restricted(doc))
        assertTrue(SiteHtml.playerTargets(doc).isEmpty())
    }

    @Test fun `pagination advances through the published next page`() {
        val doc = Jsoup.parse("<nav><a href='/films/page/2'>2</a></nav>", "https://myseed.cam/films")
        assertTrue(SiteHtml.hasNext(doc, 1))
        assertFalse(SiteHtml.hasNext(doc, 2))
        assertFalse(SiteHtml.hasNext(Jsoup.parse("<a href='/films/page/23'>23</a>"), 1))
    }

    @Test fun `Arabic search matches decorated cartoon names`() {
        assertEquals(SiteHtml.searchKey("فلونة"), SiteHtml.searchKey("فـلُونة"))
    }
}
