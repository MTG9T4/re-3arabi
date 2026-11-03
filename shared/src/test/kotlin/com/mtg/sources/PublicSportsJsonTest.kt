package com.mtg.sources

import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class PublicSportsJsonTest {
    @Test fun `decodes the actual sports schedule served by the public client`() {
        val payload = javaClass.getResource("/shooot-envelope.txt")!!.readText()
        val root = PublicSportsJson.decode(payload).jsonObject
        val matches = root["matches"]!!.jsonArray
        assertTrue(matches.isNotEmpty())
        assertEquals(root["count"]!!.jsonPrimitive.int, matches.size)
        assertTrue(matches.all { it.jsonObject["home"] is JsonObject && it.jsonObject["away"] is JsonObject })
    }
    @Test fun `authenticated envelopes reject tampering and accept plain JSON`() {
        assertEquals("ok", PublicSportsJson.decode("{\"status\":\"ok\"}").jsonObject["status"]!!.jsonPrimitive.content)
        val payload = javaClass.getResource("/shooot-envelope.txt")!!.readText()
        assertThrows(Exception::class.java) { PublicSportsJson.decode(payload.dropLast(20)) }
    }
    @Test fun `decodes match navigation exactly as the website does`() {
        val doc = Jsoup.parse(javaClass.getResource("/onlinesport-match.html")!!.readText())
        val url = PublicSportsJson.matchUrl(doc.selectFirst("[data-enc]")!!.attr("data-enc"))
        assertTrue(url?.startsWith("https://onlinesportlive.com/matches/") == true)
        assertNull(PublicSportsJson.matchUrl("not-a-link"))
    }
}
