package com.carateen

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CarateenJsonTest {
    @Test
    fun `plain catalog arrays retain every title and Arabic text`() {
        val payload = (1..85).joinToString(prefix = "[", postfix = "]") {
            """{"id":$it,"title":"غرندايزر $it"}"""
        }
        val items = CarateenJson.decode(payload) as JsonArray
        assertEquals(85, items.size)
        assertEquals(JsonPrimitive("غرندايزر 85"), (items.last() as JsonObject)["title"])
        assertEquals(0, (CarateenJson.decode("[]") as JsonArray).size)
    }

    @Test
    fun `decrypts a real public episode API response`() {
        val fixture = javaClass.getResource("/carateen-episode.json")!!.readText()
        val episode = CarateenJson.decode(fixture) as JsonObject
        assertTrue((episode["streamUrl"] as JsonPrimitive).content.endsWith("/api/hls/5470/playlist.m3u8"))
        assertEquals(JsonPrimitive(true), episode["hls_supported"])
    }

    @Test
    fun `plain objects remain available for playback and live channels`() {
        val payload = """{"channels":[{"id":"spacetoon"}],"success":true}"""
        val live = CarateenJson.decode(payload) as JsonObject
        assertEquals(1, (live["channels"] as JsonArray).size)
        assertEquals(JsonPrimitive(true), live["success"])
    }

    @Test
    fun `broken responses fail rather than masquerading as an empty catalog`() {
        assertThrows(Exception::class.java) { CarateenJson.decode("<html>blocked</html>") }
        assertThrows(Exception::class.java) { CarateenJson.decode("""{"iv":"00"}""") }
        assertThrows(Exception::class.java) {
            CarateenJson.decode("""{"iv":"00000000000000000000000000000000","encryptedData":"abc"}""")
        }
    }
}
